// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.test.ebft

import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.core.BlockBuildingStrategy
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.BlockManager
import net.postchain.ebft.SyncManager
import net.postchain.test.IntegrationTest
import org.junit.After
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
