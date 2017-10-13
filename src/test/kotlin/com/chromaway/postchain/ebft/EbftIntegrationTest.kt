package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.core.BlockLifecycleListener
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.ProgrammerError
import com.chromaway.postchain.integrationtest.FullEbftTestNightly
import mu.KLogging
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

open class EbftIntegrationTest : IntegrationTest() {
    protected var ebftNodes: Array<EbftNode> = arrayOf()
    var updateLoop: Thread? = null
    val stopMe = AtomicBoolean(false)
    open fun createEbftNodes(count: Int): Array<EbftNode> {
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

    @Before
    fun setupEbftNodes() {
    }

    protected fun startUpdateLoop() {
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
        var height = 0
        override fun commitDone(witness: BlockWitness?) {
            FullEbftTestNightly.logger.info("Commit height ${height++} done")
            if (queue.size > 0) {
                FullEbftTestNightly.logger.error("Committed multiple times", ProgrammerError(""))
                fail()
            }
            queue.add(witness)
        }

        fun awaitCommitted() {
            try {
                FullEbftTestNightly.logger.info("Awaiting commit")
                queue.take()
            } finally {
                FullEbftTestNightly.logger.info("Finished Awaiting commit")
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
}

class EbftNode(val syncManager: SyncManager, val blockDatabase: BaseBlockDatabase, val dataLayer: IntegrationTest.DataLayer, val statusManager: BaseStatusManager, val blockManager: BlockManager) {
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
            logger.error("Failed closing EbftNode", e)
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
