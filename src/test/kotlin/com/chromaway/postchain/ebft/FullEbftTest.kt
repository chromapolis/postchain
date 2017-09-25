package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.IntegrationTest.DataLayer
import com.chromaway.postchain.core.BlockLifecycleListener
import com.chromaway.postchain.core.BlockWitness
import mu.KLogging
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class FullEbftTest : IntegrationTest() {
    companion object : KLogging()
    lateinit var nodes: Array<EbftNode>
    fun createEbftNodes(count: Int): Array<EbftNode> {
        return Array(count, { createEBFTNode(count, it) })
    }

    fun createEBFTNode(nodeCount: Int, myIndex: Int): EbftNode {
        val dataLayer = createDataLayer(myIndex, nodeCount)
        val ectxt = TestErrorContext()
        val statusManager = BaseStatusManager(ectxt, nodeCount, myIndex)
        val blockDatabase = BaseBlockDatabase(dataLayer.engine, dataLayer.blockQueries)
        val blockManager = BaseBlockManager(blockDatabase, statusManager, ectxt)

        val commConfiguration = createBasePeerCommConfiguration(nodeCount, myIndex)
        val commManager = makeCommManager(commConfiguration)

        val syncManager = SyncManager(statusManager, blockManager, blockDatabase, commManager, dataLayer.blockchainConfiguration)
        return EbftNode(syncManager, dataLayer, statusManager, blockManager)
    }

    @After
    fun tearDownEbftNode() {
        nodes.forEach { it.close() }
    }

    @Test
    fun setupThreeNodesAndStartUpdating() {
        nodes = createEbftNodes(3)

        val listener = object : BlockLifecycleListener() {
            val queue = LinkedBlockingQueue<BlockWitness?>(1)
            var height = 0;
            override fun commitDone(witness: BlockWitness?) {
                logger.info("Commit height ${height++/nodes.size} done")
                queue.add(witness)
            }

            fun awaitCommitted() {
                try {
                    logger.info("Awaiting commit")
                    for (node in nodes) {
                        queue.take()
                    }
                } finally {
                    logger.info("Finished Awaiting commit")
                }
            }
        }

        nodes.forEach { it.dataLayer.engine.addBlockLifecycleListener(listener) }
        thread(name = "updateLoop") {
            while (true) {
                nodes.forEach { it.syncManager.update() }
                Thread.sleep(100)
            }
        }
        nodes[0].statusManager.setBlockIntent(BuildBlockIntent)
        for (i in 0 until 10) {
            listener.awaitCommitted()
        }

        val queries0 = nodes[0].dataLayer.blockQueries;
        val referenceHeight = queries0.getBestHeight().get()
        nodes.forEach { node ->
            val queries = node.dataLayer.blockQueries;
            assertEquals(referenceHeight, queries.getBestHeight().get())
        }
        assertEquals(1, 1)

    }

}

class EbftNode(val syncManager: SyncManager, val dataLayer: DataLayer, val statusManager: BaseStatusManager, val blockManager: BlockManager) {
    fun close() {
        syncManager.commManager.stop()
    }
}


class TestErrorContext : ErrContext {
    companion object : KLogging()

    override fun fatal(msg: String) {
        logger.error(msg)
    }

    override fun warn(msg: String) {
        logger.warn(msg)
    }

    override fun log(msg: String) {
        logger.info { msg }
    }

}
