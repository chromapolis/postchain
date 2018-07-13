package net.postchain.modules.ft

import net.postchain.DataLayer
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.toHex
import net.postchain.core.Transaction
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXValue
import net.postchain.gtx.make_gtx_gson
import net.postchain.test.modules.ft.FTIntegrationTest
import org.junit.Assert
import org.junit.Test

class FTBasicIntegrationTest: FTIntegrationTest() {

    @Test
    fun testEverything() {

        configOverrides.setProperty("blockchain.1.configurationfactory",
                GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                BaseFTModuleFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.ft.assets", "USD")
        configOverrides.setProperty("blockchain.1.gtx.ft.asset.USD.issuers",
                issuerPubKeys[0].toHex())
        configOverrides.setProperty("blockchain.1.gtx.ft.openRegistration", true)

        val node = createDataLayer(0)



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

        validTxs.add(enqueueTx(node,
                makeRegisterTx(arrayOf(aliceAccountDesc, bobAccountDesc), 0)
        )!!)

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(node,
                makeIssueTx(0, issuerID, aliceAccountID, "USD", 1000)
        )!!)

        // invalid issuance:
        enqueueTx(node, makeIssueTx(0, issuerID, aliceAccountID, "XDX", 1000))
        enqueueTx(node, makeIssueTx(0, issuerID, aliceAccountID, "USD", -1000))
        enqueueTx(node, makeIssueTx(0, aliceAccountID, aliceAccountID, "USD", 1000))
        enqueueTx(node, makeIssueTx(1, issuerID, aliceAccountID, "USD", 1000))
        enqueueTx(node, makeIssueTx(0, issuerID, invalidAccountID, "USD", 1000))

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(node,
                makeTransferTx(1, aliceAccountID, "USD", 100, bobAccountID)
        )!!)

        enqueueTx(node, makeTransferTx(1, aliceAccountID, "USD", 10000, bobAccountID))
        enqueueTx(node, makeTransferTx(1, aliceAccountID, "USD", -100, bobAccountID))
        enqueueTx(node, makeTransferTx(2, aliceAccountID, "USD", 100, bobAccountID))

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(node,
                makeTransferTx(1, aliceAccountID, "USD", 1, bobAccountID, "hi")
        )!!)
        validTxs.add(enqueueTx(node,
                makeTransferTx(1, aliceAccountID, "USD", 1, bobAccountID, null, "there")
        )!!)
        validTxs.add(enqueueTx(node,
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
        Assert.assertEquals("""{"exists":0}""", existence.get())
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