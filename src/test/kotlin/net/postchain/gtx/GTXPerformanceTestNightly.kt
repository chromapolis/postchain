// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx

import junitparams.JUnitParamsRunner
import junitparams.Parameters
import mu.KLogging
import net.postchain.PostchainNode
import net.postchain.configurations.GTXTestModule
import net.postchain.ebft.EbftIntegrationTest
import net.postchain.ebft.OnDemandBlockBuildingStrategy
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(JUnitParamsRunner::class)
class GTXPerformanceTestNightly : EbftIntegrationTest() {
    companion object : KLogging()

    fun strat(node: PostchainNode): OnDemandBlockBuildingStrategy {
        return node.blockStrategy as OnDemandBlockBuildingStrategy
    }



    @Test
    @Parameters(
            "3, 100", "4, 100", "10, 100",
            "4, 1000", "10, 1000",
            "4, 10", "10, 10", "16, 10"
    )
    fun runXNodesWithYTxPerBlock(nodeCount: Int, txPerBlock: Int) {
        val blockCount = 2
        configOverrides.setProperty("blockchain.1.blockstrategy", OnDemandBlockBuildingStrategy::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.configurationfactory", GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                listOf(GTXTestModule::class.qualifiedName, StandardOpsGTXModule::class.qualifiedName))

        createEbftNodes(nodeCount)

        var txId = 0
        val statusManager = ebftNodes[0].statusManager
        for (i in 0 until blockCount) {
            for (tx in 0 until txPerBlock) {
                val txf = ebftNodes[statusManager.primaryIndex()].blockchainConfiguration.getTransactionFactory()
                ebftNodes[statusManager.primaryIndex()].txQueue.enqueue(
                        txf.decodeTransaction(makeTestTx(1, (txId++).toString()))
                )
            }
            val nanoDelta = measureNanoTime {
                strat(ebftNodes[statusManager.primaryIndex()]).triggerBlock()
                ebftNodes.forEach { strat(it).awaitCommitted(i) }
            }
            println("Time elapsed: ${nanoDelta / 1000000} ms")
        }
    }

}