// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.core.*
import org.apache.commons.configuration2.Configuration

class BaseBlockBuildingStrategy(val config: Configuration,
                                val blockchainConfiguration: BlockchainConfiguration,
                                blockQueries: BlockQueries,
                                private val txQueue: TransactionQueue): BlockBuildingStrategy {
    private var lastBlockTime: Long
    private var lastTxTime = System.currentTimeMillis()
    private var lastTxSize = 0
    private val maxBlockTime = config.getLong("basestrategy.maxblocktime", 30000)
    private val blockDelay = config.getLong("basestrategy.blockdelay", 100)
    private val maxBlockTransactions = config.getLong("basestrategy.maxblocktransactions", 100)

    init {
        val height = blockQueries.getBestHeight().get()
        if (height == -1L) {
            lastBlockTime = System.currentTimeMillis()
        } else {
            val blockRID = blockQueries.getBlockRids(height).get()[0]
            lastBlockTime = (blockQueries.getBlockHeader(blockRID).get() as BaseBlockHeader).timestamp
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        val abb = bb as AbstractBlockBuilder
        if (abb.transactions.size >= maxBlockTransactions)
            return true
        else
            return false
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