package com.chromaway.postchain.ebft;

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.IntegrationTest.DataLayer
import org.junit.Test

class FullEbftTest: IntegrationTest() {
    fun createEbftNodes(count: Int): Array<EbftNode> {
        return Array(count, {createEBFTNode(count, it)})
    }

    fun createEBFTNode(nodeCount: Int, myIndex: Int): EbftNode {
        val dataLayer = createDataLayer(myIndex)
        val ectxt = TestErrorContext()
        val statusManager = BaseStatusManager(ectxt, nodeCount, myIndex)
        val blockDatabase = BaseBlockDatabase(dataLayer.engine)
        val blockManager = BaseBlockManager(blockDatabase, statusManager, ectxt)

        val commConfiguration = createBasePeerCommConfiguration(nodeCount, myIndex)
        val commManager = makeCommManager(commConfiguration)

        val syncManager = SyncManager(statusManager, blockManager, blockDatabase, commManager, dataLayer.blockchainConfiguration)
        return EbftNode(syncManager, dataLayer, statusManager, blockManager)
    }

    @Test
    fun setupThreeNodes() {
        val nodes = createEbftNodes(3)
        for (i in 0 until 1000) {
            nodes.forEach { it.syncManager.update() }
            Thread.sleep(10)
        }
    }

}

class EbftNode(val syncManager: SyncManager, val dataLayer: DataLayer, val statusManager: StatusManager, val blockManager: BlockManager)


class TestErrorContext: ErrContext {
    override fun fatal(msg: String) {
        TODO("not implemented")
    }

    override fun warn(msg: String) {
        TODO("not implemented")
    }

    override fun log(msg: String) {
        TODO("not implemented")
    }

}
