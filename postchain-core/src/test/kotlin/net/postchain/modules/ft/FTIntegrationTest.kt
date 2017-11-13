// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.test.IntegrationTest
import net.postchain.base.toHex
import net.postchain.core.Transaction
import net.postchain.gtx.*
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

fun makeTransferTx(senderIdx: Int,
                   senderID: ByteArray,
                   assetID: String,
                   amout: Long,
                   recipientID: ByteArray,
                   memo1: String? = null, memo2: String? = null): ByteArray {
    val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(senderIdx)), myCS)

    val args = mutableListOf<GTXValue>()
    args.add(gtx(gtx(gtx(senderID), gtx(assetID), gtx(amout)))) // inputs

    val output = mutableListOf<GTXValue>(gtx(recipientID), gtx(assetID), gtx(amout))
    if (memo2 != null) {
        output.add(gtx("memo" to gtx(memo2)))
    }
    args.add(gtx(gtx(*output.toTypedArray()))) // outputs
    if (memo1 != null) {
        args.add(gtx("memo" to gtx(memo1)))
    }
    b.addOperation("ft_transfer", args.toTypedArray())
    b.finish()
    b.sign(myCS.makeSigner(pubKey(senderIdx), privKey(senderIdx)))
    return b.serialize()
}

class FTIntegrationTest : IntegrationTest() {

    @Test
    fun testEverything() {
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
        var currentBlockHeight = -1L

        fun makeSureBlockIsBuiltCorrectly() {
            currentBlockHeight += 1
            buildBlockAndCommit(node.engine)
            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
            val ridsAtHeight = getTxRidsAtHeight(node, currentBlockHeight)
            for (vtx in validTxs) {
                val vtxRID = vtx.getRID()
                Assert.assertTrue(ridsAtHeight.any { it.contentEquals(vtxRID)})
            }
            Assert.assertEquals(validTxs.size, ridsAtHeight.size)
            validTxs.clear()
        }

        validTxs.add(enqueueTx(
                makeRegisterTx(arrayOf(aliceAccountDesc, bobAccountDesc), 0)
        )!!)

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(
                makeIssueTx(0, issuerID, aliceAccountID, "USD", 1000)
        )!!)

        // invalid issuance:
        enqueueTx(makeIssueTx(0, issuerID, aliceAccountID, "XDX", 1000))
        enqueueTx(makeIssueTx(0, issuerID, aliceAccountID, "USD", -1000))
        enqueueTx(makeIssueTx(0, aliceAccountID, aliceAccountID, "USD", 1000))
        enqueueTx(makeIssueTx(1, issuerID, aliceAccountID, "USD", 1000))
        enqueueTx(makeIssueTx(0, issuerID, invalidAccountID, "USD", 1000))

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(
                makeTransferTx(1, aliceAccountID, "USD", 100, bobAccountID)
        )!!)

        enqueueTx(makeTransferTx(1, aliceAccountID, "USD", 10000, bobAccountID))
        enqueueTx(makeTransferTx(1, aliceAccountID, "USD", -100, bobAccountID))
        enqueueTx(makeTransferTx(2, aliceAccountID, "USD", 100, bobAccountID))

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(
                makeTransferTx(1, aliceAccountID, "USD", 1, bobAccountID, "hi")
        )!!)
        validTxs.add(enqueueTx(
                makeTransferTx(1, aliceAccountID, "USD", 1, bobAccountID, null, "there")
        )!!)
        validTxs.add(enqueueTx(
                makeTransferTx(1, aliceAccountID, "USD", 1, bobAccountID, "hi", "there")
        )!!)

        makeSureBlockIsBuiltCorrectly()

        val balance = node.blockQueries.query(
                """{"type"="ft_get_balance",
                    "account_id"="${aliceAccountID.toHex()}",
                    "asset_id"="USD"
                   }""")
        Assert.assertEquals("""{"balance":897}""", balance.get())
        val existence = node.blockQueries.query(
                """{"type"="ft_account_exists",
                    "account_id"="${invalidAccountID.toHex()}"
                   }""")
        Assert.assertEquals("0", existence.get())
        val history = node.blockQueries.query(
                """{"type"="ft_get_history",
                    "account_id"="${aliceAccountID.toHex()}",
                    "asset_id"="USD"
                   }""").get()
        println(history);
        val gson = make_gtx_gson()
        val historyGTX = gson.fromJson<GTXValue>(history, GTXValue::class.java)
        Assert.assertEquals(5, historyGTX.asArray().size)

        val history2 = node.blockQueries.query(
                """{"type"="ft_get_history",
                    "account_id"="${bobAccountID.toHex()}",
                    "asset_id"="USD"
                   }""").get()
        println(history2);
        val history2GTX = gson.fromJson<GTXValue>(history2, GTXValue::class.java)
        Assert.assertEquals(4, history2GTX.asArray().size)

    }
}