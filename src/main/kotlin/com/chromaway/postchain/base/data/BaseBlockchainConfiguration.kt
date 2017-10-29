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
import com.chromaway.postchain.core.BlockBuildingStrategy
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.core.TransactionQueue
import com.chromaway.postchain.core.UserMistake
import org.apache.commons.configuration2.Configuration

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
        return BaseBlockQueries(this, storage, blockStore, chainID, blockSigningPublicKey)
    }

    override fun initializeDB(ctx: EContext) {
        val blockchainRidHex = config.getString("blockchainrid")
        if (blockchainRidHex == null) {
            throw UserMistake("Missing property blockchain.$chainID.blochchainrid")
        }
        if (!blockchainRidHex.matches(Regex("[0-9a-f]{64}"))) {
            throw UserMistake("Invalid property blockchain.$chainID.blochchainrid expected 64 " +
                    "lower case hex digits. Got $blockchainRidHex")
        }
        blockStore.initialize(ctx, blockchainRidHex!!.hexStringToByteArray())
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, transactionQueue: TransactionQueue): BlockBuildingStrategy {
        val strategyClassName = config.getString("blockstrategy", "")
        if (strategyClassName == "") {
            return BaseBlockBuildingStrategy(config, blockQueries, transactionQueue)
        }
        val strategyClass = Class.forName(strategyClassName)
        val ctor = strategyClass.getConstructor(Configuration::class.java, BlockQueries::class.java, TransactionQueue::class.java)
        return ctor.newInstance(config, blockQueries, transactionQueue) as BlockBuildingStrategy
    }
}

class BaseBlockBuildingStrategy(val config: Configuration, blockQueries: BlockQueries, private val txQueue: TransactionQueue): BlockBuildingStrategy {
    private var lastBlockTime: Long
    private var lastTxTime = System.currentTimeMillis()
    private var lastTxSize = 0
    private val maxBlockTime = config.getLong("basestrategy.maxblocktime", 30000)
    private val blockDelay = config.getLong("basestrategy.blockdelay", 1000)
    init {
        val height = blockQueries.getBestHeight().get()
        if (height == -1L) {
            lastBlockTime = System.currentTimeMillis()
        } else {
            val blockRID = blockQueries.getBlockRids(height).get()[0]
            lastBlockTime = (blockQueries.getBlockHeader(blockRID).get() as BaseBlockHeader).timestamp
        }
    }

    override fun blockCommitted(blockData: BlockData) {
        lastBlockTime = (blockData.header as BaseBlockHeader).timestamp
    }

    override fun shouldBuildBlock(): Boolean {
        if (System.currentTimeMillis() - lastBlockTime > maxBlockTime) {
            lastTxSize = 0
            lastTxTime = System.currentTimeMillis()
            return true
        }
        val peekTransactions = txQueue.peekTransactions()
        if (peekTransactions.size > 0) {
            if (peekTransactions.size == lastTxSize && lastTxTime + blockDelay < System.currentTimeMillis()) {
                lastTxSize = 0
                lastTxTime = System.currentTimeMillis()
                return true
            }
            if (peekTransactions.size > lastTxSize) {
                lastTxTime = System.currentTimeMillis()
                lastTxSize = peekTransactions.size
            }
            return false
        }
        return false
    }

}