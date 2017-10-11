package com.chromaway.postchain.ebft

import junitparams.JUnitParamsRunner
import junitparams.Parameters
import mu.KLogging
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class FullEbftTest : EbftIntegrationTest() {
    companion object : KLogging()

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
        var txId = 0
        for (i in 0 until blockCount) {
            for (tx in 0 until txPerBlock) {
                ebftNodes[i % nodeCount].dataLayer.txEnqueuer.enqueue(TestTransaction(txId++))
            }
            listeners.forEach({it.releaseBlock()})
            listeners.forEach({it.awaitCommitted()})
        }

        // Blocks may be built after last block because updater thread keeps running
        // If so, we should not block those blocks from executing or we will
        // block the next test from
        listeners.forEach({it.releaseBlock()})

        val queries0 = ebftNodes[0].dataLayer.blockQueries
        val referenceHeight = queries0.getBestHeight().get()
        ebftNodes.forEach { node ->
            val queries = node.dataLayer.blockQueries
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
                    assertArrayEquals(expectedTx.getRID(), actualTx?.getRID())
                    assertArrayEquals(expectedTx.getRawData(), actualTx!!.getRawData())
                }
            }
        }
    }
}
