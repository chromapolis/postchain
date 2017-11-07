// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.base.test.IntegrationTest
import net.postchain.core.*
import org.apache.commons.configuration2.Configuration
import org.junit.After
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

open class EbftIntegrationTest : IntegrationTest() {
    protected var ebftNodes: Array<PostchainNode> = arrayOf()

    open fun createEbftNodes(count: Int) {
        ebftNodes = Array(count, { createEBFTNode(count, it) })
    }

    protected fun createEBFTNode(nodeCount: Int, myIndex: Int): PostchainNode {
        configOverrides.setProperty("messaging.privkey", privKeyHex(myIndex))
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val pn = PostchainNode()
        pn.start(createConfig(myIndex, nodeCount), myIndex)
        return pn;
    }

    @After
    fun tearDownEbftNodes() {
        ebftNodes.forEach {
            it.stop()
        }
        ebftNodes = arrayOf()
    }
}

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
        val txFactory = blockchainConfiguration.getTransactionFactory()
        txQueue.removeAll(blockData.transactions.map {txFactory.decodeTransaction(it)})
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
