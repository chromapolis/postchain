package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.configurations.GTXTestModule
import com.chromaway.postchain.core.Transaction
import org.junit.Assert
import org.junit.Test


fun makeTestTx(id: Long, value: String): ByteArray {
    val b = GTXDataBuilder(EMPTY_SIGNATURE, arrayOf(pubKey(0)), myCS)
    b.addOperation("gtx_test", arrayOf(gtx(id), gtx(value)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

class GTXIntegrationTest: IntegrationTest() {

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("blockchain.1.configurationfactory", GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules", GTXTestModule::class.qualifiedName)
        val node = createDataLayer(0)

        fun enqueueTx(data: ByteArray): Transaction? {
            try {
                val tx = node.blockchainConfiguration.getTransactionFactory().decodeTransaction(data)
                node.txEnqueuer.enqueue(tx)
                return tx
            } catch (e: Error) {
                println(e)
            }
            return null
        }

        val validTx = enqueueTx(makeTestTx(1, "true"))!!
        enqueueTx(makeTestTx(2, "false"))
        enqueueTx(makeNOPGTX())

        buildBlockAndCommit(node.engine)
        Assert.assertEquals(0, getBestHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        Assert.assertEquals(1, riDsAtHeight0.size)
        Assert.assertArrayEquals(validTx.getRID(), riDsAtHeight0[0])

        val value = node.blockQueries.query(
                """{"type"="gtx_test_get_value", "txRID"="${validTx.getRID().toHex()}"}""")
        Assert.assertEquals("\"true\"", value.get())
    }
}