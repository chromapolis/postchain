package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.gtx.*
import org.junit.Assert
import org.junit.Test


fun makeRegisterTx(accountDescs: Array<ByteArray>, registrator: Int): ByteArray {
    val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(registrator)), myCS)
    for (desc in accountDescs) {
        b.addOperation("ft_register", arrayOf(gtx(desc)))
    }
    b.finish()
    b.sign(myCS.makeSigner(pubKey(registrator), privKey(registrator)))
    return b.serialize()
}

fun makeIssueTx(issuerIdx: Int, issuerID: ByteArray, recipientID: ByteArray, assetID: String, amout: Long): ByteArray {
    val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(issuerIdx)), myCS)
    b.addOperation("ft_issue", arrayOf(
            gtx(issuerID), gtx(assetID), gtx(amout), gtx(recipientID)
    ))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(issuerIdx), privKey(issuerIdx)))
    return b.serialize()
}

class FTIntegrationTest : IntegrationTest() {

    @Test
    fun testBuildBlock() {
        val issuerPubKey = pubKey(0)
        val accUtil = AccountUtil(testBlockchainRID, SECP256K1CryptoSystem())
        val issuerID = accUtil.makeAccountID(accUtil.issuerAccountDesc(issuerPubKey))
        val aliceAccountDesc = BasicAccount.makeDescriptor(testBlockchainRID, pubKey(1))
        val aliceAccountID = accUtil.makeAccountID(aliceAccountDesc)
        val bobAccountDesc = BasicAccount.makeDescriptor(testBlockchainRID, pubKey(2))
        val bobAccountID = accUtil.makeAccountID(bobAccountDesc)
        val invalidAccountID = accUtil.makeAccountID("hello".toByteArray())

        configOverrides.setProperty("blockchain.1.configurationfactory",
                GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                BaseFTModuleFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.ft.assets", "USD")
        configOverrides.setProperty("blockchain.1.gtx.ft.asset.USD.issuers",
                issuerPubKey.toHex())
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

        val validTxs = mutableListOf<Transaction>()

        validTxs.add(enqueueTx(
                makeRegisterTx(arrayOf(aliceAccountDesc, bobAccountDesc), 0)
        )!!)
        /*validTxs.add(enqueueTx(
                makeIssueTx(0, issuerID, aliceAccountID, "USD", 1000)
        )!!)

        // invalid issuance:
        enqueueTx(makeIssueTx(0, issuerID, aliceAccountID, "XDX", 1000))
        enqueueTx(makeIssueTx(0, issuerID, aliceAccountID, "USD", -1000))
        enqueueTx(makeIssueTx(0, aliceAccountID, aliceAccountID, "USD", 1000))
        enqueueTx(makeIssueTx(1, issuerID, aliceAccountID, "USD", 1000))
        makeIssueTx(0, issuerID, invalidAccountID, "USD", 1000)
        */

        buildBlockAndCommit(node.engine)
        Assert.assertEquals(0, getBestHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        Assert.assertEquals(validTxs.size, riDsAtHeight0.size)
        //Assert.assertArrayEquals(validTx.getRID(), riDsAtHeight0[0])

        /*val myConf = node.blockchainConfiguration as GTXBlockchainConfiguration
        val value = node.blockQueries.query(
                """{"type"="gtx_test_get_value", "txRID"="${validTx.getRID().toHex()}"}""")
        Assert.assertEquals("\"true\"", value.get())*/
    }
}