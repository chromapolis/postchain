package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.MultiSigBlockWitnessBuilder
import com.chromaway.postchain.core.UserError
import com.chromaway.postchain.ebft.BlockchainEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BlockchainEngineTest : IntegrationTest() {

    @Test
    fun testBuildBlock() {
        val node = createDataLayer(0)
        node.txQueue.add(TestTransaction(0))
        buildBlockAndCommit(node.engine)
        assertEquals(0, blockStore.getLastBlockHeight(node.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node.readCtx, 0)
        assertEquals(1, riDsAtHeight0.size)
        assertArrayEquals(TestTransaction(id = 0).getRID(), riDsAtHeight0[0])

        node.txQueue.add(TestTransaction(1))
        node.txQueue.add(TestTransaction(2))
        buildBlockAndCommit(node.engine)
        assertEquals(1, blockStore.getLastBlockHeight(node.readCtx))
        assertTrue(riDsAtHeight0.contentDeepEquals(blockStore.getTxRIDsAtHeight(node.readCtx, 0)))
        val riDsAtHeight1 = blockStore.getTxRIDsAtHeight(node.readCtx, 1)
        assertTrue(riDsAtHeight1.contentDeepEquals(Array(2, { TestTransaction(it + 1).getRID() })))

        // Empty block. All tx will be failing
        node.txQueue.add(TestTransaction(3, good = true, correct = false))
        node.txQueue.add(TestTransaction(4, good = false, correct = true))
        node.txQueue.add(TestTransaction(5, good = false, correct = false))
        node.txQueue.add(ErrorTransaction(6, true, true))
        node.txQueue.add(ErrorTransaction(7, false, true))
        node.txQueue.add(ErrorTransaction(8, true, false))

        buildBlockAndCommit(node.engine)
        assertEquals(2, blockStore.getLastBlockHeight(node.readCtx))
        assertTrue(riDsAtHeight1.contentDeepEquals(blockStore.getTxRIDsAtHeight(node.readCtx, 1)))
        val txRIDsAtHeight2 = blockStore.getTxRIDsAtHeight(node.readCtx, 2)
        assertEquals(0, txRIDsAtHeight2.size)
    }

    @Test
    fun testLoadUnfinishedEmptyBlock() {
        val (node0, node1) = createEngines(2)

        val blockData = createBlockWithTxAndCommit(node0, 0)

        loadUnfinishedAndCommit(node1, blockData)
        assertEquals(0, blockStore.getLastBlockHeight(node1.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node1.readCtx, 0)
        assertEquals(0, riDsAtHeight0.size)
    }

    @Test
    fun testLoadUnfinishedBlock2tx() {
        val (node0, node1) = createEngines(2)

        val blockData = createBlockWithTxAndCommit(node0, 2)
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, blockStore.getLastBlockHeight(node1.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node1.readCtx, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array(2, { TestTransaction(it).getRID() })))
    }

    @Test
    fun testMultipleLoadUnfinishedBlocks() {
        val (node0, node1) = createEngines(2)
        for (i in 0..10) {
            val blockData = createBlockWithTxAndCommit(node0, 2, i * 2)

            loadUnfinishedAndCommit(node1, blockData)

            assertEquals(i.toLong(), blockStore.getLastBlockHeight(node1.readCtx))
            val riDsAtHeighti = blockStore.getTxRIDsAtHeight(node1.readCtx, i.toLong())
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
        } catch (userError: UserError) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, blockStore.getLastBlockHeight(node1.readCtx))

        bc.transactionFactory.specialTxs.clear()
        // And we can create a new valid block afterwards.
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, blockStore.getLastBlockHeight(node1.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node1.readCtx, 0)
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
        } catch (userError: UserError) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, blockStore.getLastBlockHeight(node1.readCtx))
    }

    @Test
    fun testAddBlock() {
        val (node0, node1) = createEngines(2)
        val blockBuilder = createBlockWithTx(node0, 2)
        val witness = commitBlock(blockBuilder)
        val blockData = blockBuilder.getBlockData()
        val blockWithWitness = BlockDataWithWitness(blockData.header, blockData.transactions, witness)

        node1.engine.addBlock(blockWithWitness)

        assertEquals(0, blockStore.getLastBlockHeight(node1.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node1.readCtx, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array(2, { TestTransaction(it).getRID() })))
    }

    private fun createBlockWithTxAndCommit(dataLayer: DataLayer, txCount: Int, startId: Int = 0): BlockData {
        val blockBuilder = createBlockWithTx(dataLayer, txCount, startId)
        commitBlock(blockBuilder)
        return blockBuilder.getBlockData()
    }

    private fun createBlockWithTx(dataLayer: DataLayer, txCount: Int, startId: Int = 0): BlockBuilder {
        for (i in startId until startId + txCount) {
            dataLayer.txQueue.add(TestTransaction(i))
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
        blockBuilder.finalize()
        val blockData = blockBuilder.getBlockData()
        // Simulate other peers sign the block
        val blockHeader = blockData.header
        var i = 0;
        while (!witnessBuilder.isComplete()) {
            witnessBuilder.applySignature(cryptoSystem.makeSigner(pubKey(i), privKey(i))(blockHeader.rawData))
        }
        val witness = witnessBuilder.getWitness()
        blockBuilder.commit(witness)
        return witness
    }

}