package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.IntegrationTest.DataLayer
import com.chromaway.postchain.core.BlockLifecycleListener
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.ProgrammerError
import mu.KLogging
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
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

    class CommitListener(): BlockLifecycleListener() {
        val queue = LinkedBlockingQueue<BlockWitness?>()
        var height = 0;
        override fun commitDone(witness: BlockWitness?) {
            logger.info("Commit height ${height++} done")
            if (queue.size > 0) {
                logger.error("Committed multiple times", ProgrammerError(""))
                fail()
            }
            queue.add(witness)
        }

        fun awaitCommitted() {
            try {
                logger.info("Awaiting commit")
                queue.take()
            } finally {
                logger.info("Finished Awaiting commit")
            }
        }
    }

    @Test
    fun setupThreeNodesAndStartUpdating() {
        nodes = createEbftNodes(3)

        val listeners = nodes.map { CommitListener() }

        val stopMe = AtomicBoolean(false)
        nodes.forEachIndexed { index, ebftNode -> ebftNode.dataLayer.engine.addBlockLifecycleListener(listeners[index]) }
        thread(name = "updateLoop") {
            while (true) {
                nodes.forEach { it.syncManager.update() }
                if (stopMe.get()) {
                    break
                }
                Thread.sleep(100)
            }
        }
        nodes[0].statusManager.setBlockIntent(BuildBlockIntent)
        for (i in 0 until 10) {
            listeners.forEach({it.awaitCommitted()})
        }
        stopMe.set(true)
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
