// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import net.postchain.base.BaseBlockBuildingStrategy
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockQueries
import net.postchain.base.BaseBlockWitness
import net.postchain.base.CryptoSystem
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.Signer
import net.postchain.base.Storage
import net.postchain.core.*
import org.apache.commons.configuration2.Configuration
import net.postchain.base.BaseBlockchainConfigurationData

open class BaseBlockchainConfiguration(val configData: BaseBlockchainConfigurationData) :
        BlockchainConfiguration {
    override val traits = setOf<String>()
    val cryptoSystem = SECP256K1CryptoSystem()
    val blockStore = BaseBlockStore()
    override val chainID = configData.chainID
    val blockchainRID = configData.blockchainRID

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
        val signerPubKeys = configData.getSigners()
        return createBlockBuilderInstance(cryptoSystem,
                ctx, blockStore, getTransactionFactory(),
                signerPubKeys.toTypedArray(),
                configData.blockSigner)
    }

    open fun createBlockBuilderInstance(cryptoSystem: CryptoSystem, ctx: EContext,
                                        blockStore: BlockStore, transactionFactory: TransactionFactory,
                                        signers: Array<ByteArray>, blockSigner: Signer): BlockBuilder {
        return BaseBlockBuilder(cryptoSystem, ctx, blockStore,
                getTransactionFactory(), signers, blockSigner)
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries {
        return BaseBlockQueries(
                this, storage, blockStore, chainID,
                configData.subjectID)
    }

    override fun initializeDB(ctx: EContext) {
        blockStore.initialize(ctx, blockchainRID)
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, transactionQueue: TransactionQueue): BlockBuildingStrategy {
        val strategyClassName = configData.getBlockBuildingStrategyName()
        if (strategyClassName == "") {
            return BaseBlockBuildingStrategy(configData, this, blockQueries, transactionQueue)
        }
        val strategyClass = Class.forName(strategyClassName)

        val ctor = strategyClass.getConstructor(
                BaseBlockchainConfigurationData::class.java,
                BlockchainConfiguration::class.java,
                BlockQueries::class.java,
                TransactionQueue::class.java)
        return ctor.newInstance(configData, this, blockQueries, transactionQueue) as BlockBuildingStrategy
    }
}

