// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.test

import net.postchain.core.*
import org.apache.commons.configuration2.Configuration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("UNUSED_PARAMETER")
class OnDemandBlockBuildingStrategy(config: Configuration,
                                    val blockchainConfiguration: BlockchainConfiguration,
                                    blockQueries: BlockQueries, val txQueue: TransactionQueue)
    : BlockBuildingStrategy {
    val triggerBlock = AtomicBoolean(false)
    val blocks = LinkedBlockingQueue<BlockData>()
    var committedHeight = -1
    override fun shouldBuildBlock(): Boolean {
        return triggerBlock.getAndSet(false)
    }

    fun triggerBlock() {
        triggerBlock.set(true)
    }

    override fun blockCommitted(blockData: BlockData) {
        blocks.add(blockData)
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        return false
    }

    fun awaitCommitted(blockHeight: Int) {
        while (committedHeight < blockHeight) {
            blocks.take()
            committedHeight++
        }
    }
}