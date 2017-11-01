package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.core.BlockBuildingStrategy
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.TransactionQueue
import mu.KLogging
import org.apache.commons.configuration2.Configuration
import org.junit.After
import org.junit.Before
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

open class EbftIntegrationTest : IntegrationTest() {
    protected var ebftNodes: Array<EbftNode> = arrayOf()

    open fun createEbftNodes(count: Int) {
        ebftNodes = Array(count, { createEBFTNode(count, it) })
    }

    protected fun createEBFTNode(nodeCount: Int, myIndex: Int): EbftNode {
        val dataLayer = createDataLayer(myIndex, nodeCount)
        val statusManager = BaseStatusManager(nodeCount, myIndex, 0)
        val blockDatabase = BaseBlockDatabase(dataLayer.engine, dataLayer.blockQueries, myIndex)

        val blockStrategy =
                dataLayer.blockchainConfiguration.getBlockBuildingStrategy(
                        dataLayer.blockQueries, dataLayer.txQueue)
        val blockManager = BaseBlockManager(blockDatabase, statusManager, blockStrategy)

        val commConfiguration = createBasePeerCommConfiguration(nodeCount, myIndex)
        val commManager = makeCommManager(commConfiguration)

        val syncManager = SyncManager(statusManager, blockManager, blockDatabase, commManager,
                dataLayer.blockchainConfiguration)
        statusManager.recomputeStatus()
        return EbftNode(syncManager, blockDatabase, dataLayer, statusManager, blockManager,
                blockStrategy)
    }

    @Before
    fun setupEbftNodes() {
    }

    @After
    fun tearDownEbftNodes() {
        ebftNodes.forEach {
            it.close()
        }
        ebftNodes = arrayOf()
    }
}

@Suppress("UNUSED_PARAMETER")
class OnDemandBlockBuildingStrategy(config: Configuration, blockQueries: BlockQueries, txQueue: TransactionQueue) : BlockBuildingStrategy {
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
}

class EbftNode(val syncManager: SyncManager, val blockDatabase: BaseBlockDatabase,
               val dataLayer: IntegrationTest.DataLayer, val statusManager: BaseStatusManager,
               val blockManager: BlockManager, val blockStrategy: BlockBuildingStrategy) {
    companion object : KLogging()

    val stopUpdateLoop = AtomicBoolean(false)

    init {
        startUpdateLoop()
    }

    fun close() {
        try {
            stopUpdateLoop.set(true)
            // Ordering is important.
            // 1. Close the data sources so that new blocks cant be started
            dataLayer.close()
            // 2. Close the listening port and all TCP connections
            syncManager.commManager.stop()
            // 3. Stop any in-progress blocks
            blockDatabase.stop()
        } catch (e: Throwable) {
            logger.error("Failed closing EbftNode", e)
        }
    }

    fun startUpdateLoop() {
        thread(name = "${statusManager.myIndex}-updateLoop") {
            while (true) {
                if (stopUpdateLoop.get()) {
                    break
                }
                syncManager.update()
                Thread.sleep(100)
            }
        }
    }
}
