package com.chromaway.postchain.integrationtest

import com.chromaway.postchain.PostchainNode
import com.chromaway.postchain.api.rest.ApiTx
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.BlockBuildingStrategy
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.TransactionQueue
import com.chromaway.postchain.ebft.EbftNode
import com.chromaway.postchain.ebft.EbftWithApiIntegrationTest
import com.chromaway.postchain.ebft.OnDemandBlockBuildingStrategy
import org.apache.commons.configuration2.Configuration
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue

class TxForwardingTest: EbftWithApiIntegrationTest() {

    fun strat(node: PostchainNode): ThreeTxStrategy {
        return node.blockStrategy as ThreeTxStrategy
    }

    fun tx(id: Int): ApiTx {
        return ApiTx(TestTransaction(id).getRawData().toHex())
    }

    var nodeIndex = 0
    class ThreeTxStrategy(val config: Configuration,
                          val blockchainConfiguration: BlockchainConfiguration,
                          blockQueries: BlockQueries, private val txQueue: TransactionQueue): BlockBuildingStrategy {
        val blocks = LinkedBlockingQueue<BlockData>()
        var committedHeight = -1
        val index = config.getInteger("testmyindex", -1)
        override fun shouldBuildBlock(): Boolean {
            logger.debug { "Node $index shouldBuildBlock? ${txQueue.peekTransactions().size}" }
            return txQueue.peekTransactions().size >= 3
        }

        override fun blockCommitted(blockData: BlockData) {
            blocks.add(blockData)
            val txFactory = blockchainConfiguration.getTransactionFactory()
            txQueue.removeAll(blockData.transactions.map {txFactory.decodeTransaction(it)})
            logger.debug { "Node $index committed height ${blocks.size}" }
        }

        fun awaitCommitted(blockHeight: Int) {
            logger.debug { "Node $index awaiting committed $blockHeight" }
            while (committedHeight < blockHeight) {
                blocks.take()
                committedHeight++
            }
        }
    }

    @Test
    fun testTxNotForwardedIfPrimary() {
        configOverrides.setProperty("blockchain.1.blockstrategy", ThreeTxStrategy::class.java.name)
        createEbftNodes(3)

        ebftNodes[0].model.postTransaction(tx(0))
        ebftNodes[1].model.postTransaction(tx(1))
        ebftNodes[2].model.postTransaction(tx(2))
        strat(ebftNodes[2]).awaitCommitted(0)

        ebftNodes[0].model.postTransaction(tx(3))
        ebftNodes[1].model.postTransaction(tx(4))
        ebftNodes[2].model.postTransaction(tx(5))
        strat(ebftNodes[2]).awaitCommitted(1)

        ebftNodes[0].model.postTransaction(tx(6))
        ebftNodes[1].model.postTransaction(tx(7))
        ebftNodes[2].model.postTransaction(tx(8))
        strat(ebftNodes[2]).awaitCommitted(2)

        val q0 = ebftNodes[2].blockQueries
        for (i in 0..2) {
            val b0 = q0.getBlockAtHeight(i.toLong()).get()
            Assert.assertEquals(3, b0.transactions.size)
        }
    }
}