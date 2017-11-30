package net.postchain.modules.ft

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.base.secp256k1_derivePubKey
import net.postchain.gtx.*
import net.postchain.test.modules.ft.FTIntegrationTest
import org.junit.Assert
import org.junit.Test
import kotlin.system.measureNanoTime

val testBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3".hexStringToByteArray()
val myCS = SECP256K1CryptoSystem()

class FTPerfTestNightly : FTIntegrationTest() {

    fun make1000Transactions(): List<ByteArray> {
        val accUtil = AccountUtil(testBlockchainRID, myCS)
        val senderPriv = privKey(0)
        val senderPub = pubKey(0)
        val senderID = accUtil.makeAccountID(
                BasicAccount.makeDescriptor(testBlockchainRID, senderPub)
        )
        val receiverID = accUtil.makeAccountID(
            BasicAccount.makeDescriptor(testBlockchainRID, pubKey(1))
        )
        return (0..999).map {
            makeTransferTx(
                    senderPub, senderPriv, senderID, "USD",
                    it.toLong(), receiverID)
        }
    }

    val accFactory = BaseAccountFactory(
            mapOf(
                    NullAccount.entry,
                    BasicAccount.entry
            )
    )
    val module = FTModule(FTConfig(
            FTIssueRules(arrayOf(), arrayOf()),
            FTTransferRules(arrayOf(), arrayOf(), false),
            FTRegisterRules(arrayOf(), arrayOf()),
            accFactory,
            BaseAccountResolver(accFactory),
            BaseDBOps(),
            myCS,
            testBlockchainRID
    ))
    val txFactory = GTXTransactionFactory(testBlockchainRID, module, myCS)

    @Test
    fun parseTx() {
        val transactions = make1000Transactions()
        var total = 0


        val nanoDelta = measureNanoTime {
            for (tx in transactions) {
                val ttx = txFactory.decodeTransaction(tx)
                total += (ttx as GTXTransaction).ops.size
            }
        }
        Assert.assertTrue(total == 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }

    @Test
    fun parseTxVerify() {
        val transactions = make1000Transactions()
        var total = 0

        val nanoDelta = measureNanoTime {
            for (tx in transactions) {
                val ttx = txFactory.decodeTransaction(tx)
                total += (ttx as GTXTransaction).ops.size
                Assert.assertTrue(ttx.isCorrect())
            }
        }
        Assert.assertTrue(total == 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }



}

