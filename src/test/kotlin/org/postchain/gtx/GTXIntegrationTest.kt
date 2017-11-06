// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.gtx

import org.postchain.base.IntegrationTest
import org.postchain.base.toHex
import org.postchain.configurations.GTXTestModule
import org.postchain.core.Transaction
import org.junit.Assert
import org.junit.Test


fun makeTestTx(id: Long, value: String): ByteArray {
    val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(0)), myCS)
    b.addOperation("gtx_test", arrayOf(gtx(id), gtx(value)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

fun makeTimeBTx(from: Long, to: Long?): ByteArray {
    val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(0)), myCS)
    b.addOperation("timeb", arrayOf(
            gtx(from),
            if (to != null) gtx(to) else GTXNull
    ))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}


class GTXIntegrationTest: IntegrationTest() {

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("blockchain.1.configurationfactory", GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                listOf(GTXTestModule::class.qualifiedName, StandardOpsGTXModule::class.qualifiedName))
        val node = createDataLayer(0)

        fun enqueueTx(data: ByteArray): Transaction? {
            try {
                val tx = node.blockchainConfiguration.getTransactionFactory().decodeTransaction(data)
                node.txEnqueuer.enqueue(tx)
                return tx
            } catch (e: Exception) {
                println(e)
            }
            return null
        }

        val validTxs = mutableListOf<Transaction>()
        var currentBlockHeight = -1L

        fun makeSureBlockIsBuiltCorrectly() {
            currentBlockHeight += 1
            buildBlockAndCommit(node.engine)
            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
            val ridsAtHeight = getTxRidsAtHeight(node, currentBlockHeight)
            for (vtx in validTxs) {
                val vtxRID = vtx.getRID()
                Assert.assertTrue(ridsAtHeight.any { it.contentEquals(vtxRID) })
            }
            Assert.assertEquals(validTxs.size, ridsAtHeight.size)
            validTxs.clear()
        }

        val validTx1 = enqueueTx(makeTestTx(1, "true"))!!
        validTxs.add(validTx1)
        enqueueTx(makeTestTx(2, "false"))
        validTxs.add(enqueueTx(makeNOPGTX())!!)

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(makeTimeBTx(0, null))!!)
        validTxs.add(enqueueTx(makeTimeBTx(0, System.currentTimeMillis()))!!)

        enqueueTx(makeTimeBTx(100, 0))
        enqueueTx(makeTimeBTx(System.currentTimeMillis() + 100, null))

        makeSureBlockIsBuiltCorrectly()

        val value = node.blockQueries.query(
                """{"type"="gtx_test_get_value", "txRID"="${validTx1.getRID().toHex()}"}""")
        Assert.assertEquals("\"true\"", value.get())
    }
}