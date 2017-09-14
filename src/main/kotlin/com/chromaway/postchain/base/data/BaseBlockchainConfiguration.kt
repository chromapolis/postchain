package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.BaseBlockWitness
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.TransactionFactory
import org.apache.commons.configuration2.Configuration

open class BaseBlockchainConfiguration(override val chainID: Long, val config: Configuration) :
        BlockchainConfiguration {
    override val traits = setOf<String>()

    override fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader {
        TODO("Not implemented yet")
        //return BaseBlockHeader(rawBlockHeader)
    }

    override fun decodeWitness(rawWitness: ByteArray): BlockWitness {
        return BaseBlockWitness.fromBytes(rawWitness)
    }

    override fun getTransactionFactory(): TransactionFactory {
        return BaseTransactionFactory()
    }

    override fun makeBlockBuilder(ctx: EContext): BlockBuilder {
        val blockStore = BaseBlockStore()

        val signersStrings = config.getStringArray("signers")
        val signers = signersStrings.map { hexString -> hexString.hexStringToByteArray() }

        return BaseBlockBuilder(SECP256K1CryptoSystem(), ctx, blockStore,
                getTransactionFactory(), signers.toTypedArray())
    }



}

