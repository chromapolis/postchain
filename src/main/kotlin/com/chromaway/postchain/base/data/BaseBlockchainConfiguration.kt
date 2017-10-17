package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.BaseBlockHeader
import com.chromaway.postchain.base.BaseBlockQueries
import com.chromaway.postchain.base.BaseBlockWitness
import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.Signer
import com.chromaway.postchain.base.Storage
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.secp256k1_derivePubKey
import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.TransactionFactory
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.SubsetConfiguration
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler

open class BaseBlockchainConfiguration(override val chainID: Long, val config: Configuration) :
        BlockchainConfiguration {
    override val traits = setOf<String>()
    val cryptoSystem = SECP256K1CryptoSystem()
    val blockStore = BaseBlockStore()

    override fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader {
        return BaseBlockHeader(rawBlockHeader, cryptoSystem)
    }

    override fun decodeWitness(rawWitness: ByteArray): BlockWitness {
        return BaseBlockWitness.fromBytes(rawWitness)
    }

    override fun getTransactionFactory(): TransactionFactory {
        return BaseTransactionFactory()
    }

    override fun makeBlockBuilder(ctx: EContext): BlockBuilder {
        val signersStrings = config.getStringArray("signers")
        val signerPubKeys = signersStrings.map { hexString -> hexString.hexStringToByteArray() }

        val blockSigningPrivateKey = config.getString("blocksigningprivkey").hexStringToByteArray()
        val blockSigningPublicKey = secp256k1_derivePubKey(blockSigningPrivateKey)
        val blockSigner = cryptoSystem.makeSigner(blockSigningPublicKey, blockSigningPrivateKey)

        return createBlockBuilderInstance(cryptoSystem, ctx, blockStore, getTransactionFactory(),
                signerPubKeys.toTypedArray(), blockSigner)
    }

    open fun createBlockBuilderInstance(cryptoSystem: CryptoSystem, ctx: EContext,
                                        blockStore: BlockStore, transactionFactory: TransactionFactory,
                                        signers: Array<ByteArray>, blockSigner: Signer): BlockBuilder {
        return BaseBlockBuilder(cryptoSystem, ctx, blockStore,
                getTransactionFactory(), signers, blockSigner)
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries {
        val blockSigningPrivateKey = config.getString("blocksigningprivkey").hexStringToByteArray()
        val blockSigningPublicKey = secp256k1_derivePubKey(blockSigningPrivateKey)
        return BaseBlockQueries(this, storage, blockStore, chainID.toInt(), blockSigningPublicKey)
    }

    override fun initializeDB(ctx: EContext) {
        blockStore.initialize(ctx)
    }

}

