package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.gtx.*
import org.junit.Assert
import org.junit.Test


fun makeTestTx(): ByteArray {

    val accountDesc = NullAccount.makeDescriptor(null, pubKey(0))

    val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(0)), myCS)

    b.addOperation("ft_register", arrayOf(gtx(accountDesc)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

class FTIntegrationTest : IntegrationTest() {

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("blockchain.1.configurationfactory",
                GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                BaseFTModuleFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.ft.assets", "USD")
        configOverrides.setProperty("blockchain.1.gtx.ft.asset.USD.issuers", pubKey(0).toHex())
        configOverrides.setProperty("blockchain.1.gtx.ft.openRegistration", true)

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

        val validTx = enqueueTx(makeTestTx())!!

        buildBlockAndCommit(node.engine)
        Assert.assertEquals(0, getBestHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        Assert.assertEquals(1, riDsAtHeight0.size)
        Assert.assertArrayEquals(validTx.getRID(), riDsAtHeight0[0])

        /*val myConf = node.blockchainConfiguration as GTXBlockchainConfiguration
        val value = node.blockQueries.query(
                """{"type"="gtx_test_get_value", "txRID"="${validTx.getRID().toHex()}"}""")
        Assert.assertEquals("\"true\"", value.get())*/
    }
}