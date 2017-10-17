package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.MultiSigBlockWitnessBuilder
import com.chromaway.postchain.core.UserMistake
import com.chromaway.postchain.ebft.BlockchainEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BlockchainEngineTest : IntegrationTest() {

    private fun getTxRidsAtHeight(node: DataLayer, height: Int): Array<ByteArray> {
        val list = node.blockQueries.getBlockRids(height.toLong()).get()
        return node.blockQueries.getBlockTransactionRids(list[0]).get().toTypedArray()
    }

    private fun getBestHeight(node: DataLayer): Long {
        return node.blockQueries.getBestHeight().get()
    }

    @Test
    fun testBuildBlock() {
        val node = createDataLayer(0)
        node.txEnqueuer.enqueue(TestTransaction(0))
        buildBlockAndCommit(node.engine)
        assertEquals(0, getBestHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        assertEquals(1, riDsAtHeight0.size)
        assertArrayEquals(TestTransaction(id = 0).getRID(), riDsAtHeight0[0])

        node.txEnqueuer.enqueue(TestTransaction(1))
        node.txEnqueuer.enqueue(TestTransaction(2))
        buildBlockAndCommit(node.engine)
        assertEquals(1, getBestHeight(node))
        assertTrue(riDsAtHeight0.contentDeepEquals(getTxRidsAtHeight(node, 0)))
        val riDsAtHeight1 = getTxRidsAtHeight(node, 1)
        assertTrue(riDsAtHeight1.contentDeepEquals(Array(2, { TestTransaction(it + 1).getRID() })))

        // Empty block. All tx will be failing
        node.txEnqueuer.enqueue(TestTransaction(3, good = true, correct = false))
        node.txEnqueuer.enqueue(TestTransaction(4, good = false, correct = true))
        node.txEnqueuer.enqueue(TestTransaction(5, good = false, correct = false))
        node.txEnqueuer.enqueue(ErrorTransaction(6, true, true))
        node.txEnqueuer.enqueue(ErrorTransaction(7, false, true))
        node.txEnqueuer.enqueue(ErrorTransaction(8, true, false))

        buildBlockAndCommit(node.engine)
        assertEquals(2, getBestHeight(node))
        assertTrue(riDsAtHeight1.contentDeepEquals(getTxRidsAtHeight(node, 1)))
        val txRIDsAtHeight2 = getTxRidsAtHeight(node, 2)
        assertEquals(0, txRIDsAtHeight2.size)
    }

    @Test
    fun testLoadUnfinishedEmptyBlock() {
        val (node0, node1) = createEngines(2)

        val blockData = createBlockWithTxAndCommit(node0, 0)

        loadUnfinishedAndCommit(node1, blockData)
        assertEquals(0, getBestHeight(node1))
        val riDsAtHeight0 = getTxRidsAtHeight(node1, 0)
        assertEquals(0, riDsAtHeight0.size)
    }

    @Test
    fun testLoadUnfinishedBlock2tx() {
        val (node0, node1) = createEngines(2)

        val blockData = createBlockWithTxAndCommit(node0, 2)
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, getBestHeight(node1))
        val riDsAtHeight0 = getTxRidsAtHeight(node1, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array(2, { TestTransaction(it).getRID() })))
    }

    @Test
    fun testMultipleLoadUnfinishedBlocks() {
        val (node0, node1) = createEngines(2)
        for (i in 0..10) {
            val blockData = createBlockWithTxAndCommit(node0, 2, i * 2)

            loadUnfinishedAndCommit(node1, blockData)

            assertEquals(i.toLong(), getBestHeight(node1))
            val riDsAtHeighti = getTxRidsAtHeight(node1, i)
            assertTrue(riDsAtHeighti.contentDeepEquals(Array(2, { TestTransaction(i * 2 + it).getRID() })))
        }
    }

    @Test
    fun testLoadUnfinishedBlockTxFail() {
        val (node0, node1) = createEngines(2)

        val blockData = createBlockWithTxAndCommit(node0, 2)

        val bc = node1.blockchainConfiguration as TestBlockchainConfiguration
        // Make the tx invalid on follower. Should discard whole block
        bc.transactionFactory.specialTxs.put(0, ErrorTransaction(0, true, false))
        try {
            loadUnfinishedAndCommit(node1, blockData)
            fail()
        } catch (userMistake: UserMistake) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, getBestHeight(node1))

        bc.transactionFactory.specialTxs.clear()
        // And we can create a new valid block afterwards.
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, getBestHeight(node1))
        val riDsAtHeight0 = getTxRidsAtHeight(node1, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array(2, { TestTransaction(it).getRID() })))
    }

    @Test
    fun testLoadUnfinishedBlockInvalidHeader() {
        val (node0, node1) = createEngines(2)

        val blockData = createBlockWithTxAndCommit(node0, 2)
        blockData.header.prevBlockRID[0]++
        try {
            loadUnfinishedAndCommit(node1, blockData)
            fail()
        } catch (userMistake: UserMistake) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, getBestHeight(node1))
    }

    @Test
    fun testAddBlock() {
        val (node0, node1) = createEngines(2)
        val blockBuilder = createBlockWithTx(node0, 2)
        val witness = commitBlock(blockBuilder)
        val blockData = blockBuilder.getBlockData()
        val blockWithWitness = BlockDataWithWitness(blockData.header, blockData.transactions, witness)

        node1.engine.addBlock(blockWithWitness)

        assertEquals(0, getBestHeight(node1))
        val riDsAtHeight0 = getTxRidsAtHeight(node1, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array(2, { TestTransaction(it).getRID() })))
    }

    private fun createBlockWithTxAndCommit(dataLayer: DataLayer, txCount: Int, startId: Int = 0): BlockData {
        val blockBuilder = createBlockWithTx(dataLayer, txCount, startId)
        commitBlock(blockBuilder)
        return blockBuilder.getBlockData()
    }

    private fun createBlockWithTx(dataLayer: DataLayer, txCount: Int, startId: Int = 0): BlockBuilder {
        for (i in startId until startId + txCount) {
            dataLayer.txEnqueuer.enqueue(TestTransaction(i))
        }
        return dataLayer.engine.buildBlock()
    }

    private fun loadUnfinishedAndCommit(dataLayer: DataLayer, blockData: BlockData) {
        val blockBuilder = dataLayer.engine.loadUnfinishedBlock(blockData)
        commitBlock(blockBuilder)
    }

    private fun buildBlockAndCommit(engine: BlockchainEngine) {
        val blockBuilder = engine.buildBlock()
        commitBlock(blockBuilder)
    }

    private fun commitBlock(blockBuilder: BlockBuilder): BlockWitness {
        val witnessBuilder = blockBuilder.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        assertNotNull(witnessBuilder)
        val blockData = blockBuilder.getBlockData()
        // Simulate other peers sign the block
        val blockHeader = blockData.header
        var i = 0
        while (!witnessBuilder.isComplete()) {
            witnessBuilder.applySignature(cryptoSystem.makeSigner(pubKey(i), privKey(i))(blockHeader.rawData))
            i++
        }
        val witness = witnessBuilder.getWitness()
        blockBuilder.commit(witness)
        return witness
    }

}