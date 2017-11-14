// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockQueries
import net.postchain.base.BaseBlockWitness
import net.postchain.base.CryptoSystem
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.Signer
import net.postchain.base.Storage
import net.postchain.base.hexStringToByteArray
import net.postchain.base.secp256k1_derivePubKey
import net.postchain.core.*
import org.apache.commons.configuration2.Configuration

open class BaseBlockchainConfiguration(final override val chainID: Long,
                                       val config: Configuration) :
        BlockchainConfiguration {
    override val traits = setOf<String>()
    val cryptoSystem = SECP256K1CryptoSystem()
    val blockStore = BaseBlockStore()
    val blockchainRID: ByteArray

    init {
        val blockchainRidHex = config.getString("blockchainrid")
        if (blockchainRidHex == null) {
            throw UserMistake("Missing property blockchain.$chainID.blochchainrid")
        }
        if (!blockchainRidHex.matches(Regex("[0-9a-f]{64}"))) {
            throw UserMistake("Invalid property blockchain.$chainID.blochchainrid expected 64 " +
                    "lower case hex digits. Got $blockchainRidHex")
        }
        blockchainRID = blockchainRidHex.hexStringToByteArray()
    }

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
        blockStore.initialize(ctx, blockchainRID)
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, transactionQueue: TransactionQueue): BlockBuildingStrategy {
        val strategyClassName = config.getString("blockstrategy", "")
        if (strategyClassName == "") {
            return BaseBlockBuildingStrategy(config, this, blockQueries, transactionQueue)
        }
        val strategyClass = Class.forName(strategyClassName)
        val ctor = strategyClass.getConstructor(Configuration::class.java,
                BlockchainConfiguration::class.java, BlockQueries::class.java, TransactionQueue::class.java)
        return ctor.newInstance(config, this, blockQueries, transactionQueue) as BlockBuildingStrategy
    }
}

class BaseBlockBuildingStrategy(val config: Configuration,
                                val blockchainConfiguration: BlockchainConfiguration,
                                blockQueries: BlockQueries,
                                private val txQueue: TransactionQueue): BlockBuildingStrategy {
    private var lastBlockTime: Long
    private var lastTxTime = System.currentTimeMillis()
    private var lastTxSize = 0
    private val maxBlockTime = config.getLong("basestrategy.maxblocktime", 30000)
    private val blockDelay = config.getLong("basestrategy.blockdelay", 1000)
    private val enqueueLocally = false
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
        val transactionQueueSize = txQueue.getTransactionQueueSize()
        if (transactionQueueSize > 0) {
            if (transactionQueueSize == lastTxSize && lastTxTime + blockDelay < System.currentTimeMillis()) {
                lastTxSize = 0
                lastTxTime = System.currentTimeMillis()
                return true
            }
            if (transactionQueueSize > lastTxSize) {
                lastTxTime = System.currentTimeMillis()
                lastTxSize = transactionQueueSize
            }
            return false
        }
        return false
    }

}