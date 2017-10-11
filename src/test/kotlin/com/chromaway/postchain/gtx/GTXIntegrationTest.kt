package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.cryptoSystem
import com.chromaway.postchain.core.*
import com.chromaway.postchain.ebft.BlockchainEngine
import org.apache.commons.configuration2.Configuration
import org.junit.Assert
import org.junit.Test

class GTXIntegrationTest: IntegrationTest() {

    @Test
    fun testBuildBlock() {
        val node = createDataLayer(0)
        val data = makeNOPGTX()
        val tx = node.blockchainConfiguration.getTransactionFactory().decodeTransaction(data)
        node.txQueue.add(tx)
        buildBlockAndCommit(node.engine)
        Assert.assertEquals(0, getBestHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        Assert.assertEquals(1, riDsAtHeight0.size)
        Assert.assertArrayEquals(tx.getRID(), riDsAtHeight0[0])
    }

    override fun makeTestBlockchainConfigurationFactory(): BlockchainConfigurationFactory {
        return GTXBlockchainConfigurationFactory(GTX_NOP_Module())
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun buildBlockAndCommit(engine: BlockchainEngine) {
        val blockBuilder = engine.buildBlock()
        commitBlock(blockBuilder)
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun commitBlock(blockBuilder: BlockBuilder): BlockWitness {
        val witnessBuilder = blockBuilder.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        Assert.assertNotNull(witnessBuilder)
        blockBuilder.finalizeBlock()
        val blockData = blockBuilder.getBlockData()
        // Simulate other peers sign the block
        val blockHeader = blockData.header
        var i = 0;
        while (!witnessBuilder.isComplete()) {
            witnessBuilder.applySignature(cryptoSystem.makeSigner(pubKey(i), privKey(i))(blockHeader.rawData))
            i++
        }
        val witness = witnessBuilder.getWitness()
        blockBuilder.commit(witness)
        return witness
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun getTxRidsAtHeight(node: DataLayer, height: Int): Array<ByteArray> {
        val list = node.blockQueries.getBlockRids(height.toLong()).get()
        return node.blockQueries.getBlockTransactionRids(list[0]).get().toTypedArray()
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun getBestHeight(node: DataLayer): Long {
        return node.blockQueries.getBestHeight().get()
    }


}