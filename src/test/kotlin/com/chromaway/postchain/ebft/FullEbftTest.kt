package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.IntegrationTest.DataLayer
import com.chromaway.postchain.core.BlockLifecycleListener
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.ProgrammerError
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import mu.KLogging
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(JUnitParamsRunner::class)
class FullEbftTest : IntegrationTest() {
    companion object : KLogging()
    private var ebftNodes: Array<EbftNode> = arrayOf()
    fun createEbftNodes(count: Int): Array<EbftNode> {
        return Array(count, { createEBFTNode(count, it) })
    }

    fun createEBFTNode(nodeCount: Int, myIndex: Int): EbftNode {
        val dataLayer = createDataLayer(myIndex, nodeCount)
        val ectxt = TestErrorContext()
        val statusManager = BaseStatusManager(ectxt, nodeCount, myIndex)
        val blockDatabase = BaseBlockDatabase(dataLayer.engine, dataLayer.blockQueries, myIndex)
        val blockManager = BaseBlockManager(blockDatabase, statusManager, ectxt)

        val commConfiguration = createBasePeerCommConfiguration(nodeCount, myIndex)
        val commManager = makeCommManager(commConfiguration)

        val syncManager = SyncManager(statusManager, blockManager, blockDatabase, commManager, dataLayer.blockchainConfiguration)
        return EbftNode(syncManager, blockDatabase, dataLayer, statusManager, blockManager)
    }

    var updateLoop: Thread? = null
    @Before
    fun setupEbftNodes() {
    }

    val stopMe = AtomicBoolean(false)
    private fun startUpdateLoop() {
        updateLoop = thread(name = "updateLoop") {
            while (true) {
                if (stopMe.get()) {
                    break
                }
                ebftNodes.forEach { it.syncManager.update() }
                if (stopMe.get()) {
                    break
                }
                Thread.sleep(100)
            }
        }
    }

    @After
    fun tearDownEbftNodes() {
        stopMe.set(true)
        ebftNodes.forEach {
            it.close()
        }
        ebftNodes = arrayOf()
        updateLoop?.join()
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

        val allowBeginBlock = LinkedBlockingQueue<Boolean>()
        override fun beginBlockDone() {
            allowBeginBlock.take()
        }
        fun releaseBlock() {
            allowBeginBlock.add(true)
        }
    }

    @Test
    @Parameters("3, 1, 0",  "3, 2, 0",  "3, 10, 0",
                "3, 1, 10", "3, 2, 10", "3, 10, 10",
                "4, 1, 0",  "4, 2, 0",  "4, 10, 0",
                "4, 1, 10", "4, 2, 10", "4, 10, 10",
                "8, 1, 0",  "8, 2, 0",  "8, 10, 0",
                "8, 1, 10", "8, 2, 10", "8, 10, 10"//"25, 100, 0"
                )
    fun runXNodesWithYTxPerBlock(nodeCount: Int, blockCount: Int, txPerBlock: Int) {
        ebftNodes = createEbftNodes(nodeCount)
        startUpdateLoop()

        val listeners = ebftNodes.map { CommitListener() }

        ebftNodes.forEachIndexed { index, ebftNode -> ebftNode.dataLayer.engine.addBlockLifecycleListener(listeners[index]) }

        ebftNodes[0].statusManager.setBlockIntent(BuildBlockIntent)
        var txId = 0;
        for (i in 0 until blockCount) {
            for (tx in 0 until txPerBlock) {
                ebftNodes[i % nodeCount].dataLayer.txQueue.add(TestTransaction(txId++))
            }
            listeners.forEach({it.releaseBlock()})
            listeners.forEach({it.awaitCommitted()})
        }

        // Blocks may be built after last block because updater thread keeps running
        // If so, we should not block those blocks from executing or we will
        // block the next test from
        listeners.forEach({it.releaseBlock()})

        val queries0 = ebftNodes[0].dataLayer.blockQueries;
        val referenceHeight = queries0.getBestHeight().get()
        ebftNodes.forEach { node ->
            val queries = node.dataLayer.blockQueries;
            assertEquals(referenceHeight, queries.getBestHeight().get())
            for (height in 0..referenceHeight) {
                val rids = queries.getBlockRids(height).get()
                assertEquals(1, rids.size)
                val txs = queries.getBlockTransactionRids(rids[0]).get()
                assertEquals(txPerBlock, txs.size)
                for (tx in 0 until txPerBlock) {
                    val expectedTx = TestTransaction((height * txPerBlock + tx).toInt())
                    assertArrayEquals(expectedTx.getRID(), txs[tx])
                    val actualTx = queries.getTransaction(txs[tx]).get()
                    assertArrayEquals(expectedTx.getRID(), actualTx.getRID())
                    assertArrayEquals(expectedTx.getRawData(), actualTx.getRawData())
                }
            }
        }
    }
}

class EbftNode(val syncManager: SyncManager, val blockDatabase: BaseBlockDatabase, val dataLayer: DataLayer, val statusManager: BaseStatusManager, val blockManager: BlockManager) {
    companion object : KLogging()
    fun close() {
        try {
            // Ordering is important.
            // 1. Close the data sources so that new blocks cant be started
            dataLayer.close()
            // 2. Close the listening port and all TCP connections
            syncManager.commManager.stop()
            // 3. Stop any in-progress blocks
            blockDatabase.stop()
        } catch (e: Throwable) {
            logger.error("Failed closing EbftNode", e);
        }
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
