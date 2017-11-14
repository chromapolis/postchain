// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.integrationtest

import junitparams.JUnitParamsRunner
import junitparams.Parameters
import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.test.OnDemandBlockBuildingStrategy
import net.postchain.test.EbftIntegrationTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class FullEbftTestNightly : EbftIntegrationTest() {
    companion object : KLogging()

    fun strat(node: PostchainNode): OnDemandBlockBuildingStrategy {
        return node.blockStrategy as OnDemandBlockBuildingStrategy
    }

    @Test
    @Parameters("3, 1, 0",  "3, 2, 0", "3, 10, 0",
                "3, 1, 10", "3, 2, 10", "3, 10, 10",
                "4, 1, 0",  "4, 2, 0",  "4, 10, 0",
                "4, 1, 10", "4, 2, 10", "4, 10, 10",
                "8, 1, 0",  "8, 2, 0",  "8, 10, 0",
                "8, 1, 10", "8, 2, 10", "8, 10, 10" //"25, 100, 0"
                )
    fun runXNodesWithYTxPerBlock(nodeCount: Int, blockCount: Int, txPerBlock: Int) {
        configOverrides.setProperty("blockchain.1.blockstrategy", OnDemandBlockBuildingStrategy::class.qualifiedName)
        createEbftNodes(nodeCount)

        var txId = 0
        var statusManager = ebftNodes[0].statusManager
        for (i in 0 until blockCount) {
            for (tx in 0 until txPerBlock) {
                ebftNodes[statusManager.primaryIndex()].txQueue.enqueue(TestTransaction(txId++))
            }
            strat(ebftNodes[statusManager.primaryIndex()]).triggerBlock()
            ebftNodes.forEach { strat(it).awaitCommitted(i) }
        }

        val queries0 = ebftNodes[0].blockQueries
        val referenceHeight = queries0.getBestHeight().get()
        ebftNodes.forEach { node ->
            val queries = node.blockQueries
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
