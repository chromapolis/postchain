package net.postchain.test

import net.postchain.core.BlockBuilder
import net.postchain.core.BlockBuildingStrategy
import net.postchain.core.BlockData
import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.TransactionQueue
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

    fun awaitCommitted(blockHeight: Int) {
        while (committedHeight < blockHeight) {
            blocks.take()
            committedHeight++
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        return false
    }
}