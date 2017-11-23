package net.postchain.modules.ft

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.hexStringToByteArray
import net.postchain.base.secp256k1_derivePubKey
import net.postchain.gtx.*
import org.junit.Assert
import org.junit.Test
import kotlin.system.measureNanoTime

val testBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3".hexStringToByteArray()
val myCS = SECP256K1CryptoSystem()

class FTPerfTestNightly {


    fun makeTransferTx(senderPriv: ByteArray,
                       senderPub: ByteArray,
                       senderID: ByteArray,
                       assetID: String,
                       amout: Long,
                       recipientID: ByteArray,
                       memo1: String? = null, memo2: String? = null): ByteArray {
        val b = GTXDataBuilder(testBlockchainRID, arrayOf(senderPub), myCS)

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
        b.sign(myCS.makeSigner(senderPub, senderPriv))
        return b.serialize()
    }

    fun privKey(index: Int): ByteArray {
        // private key index 0 is all zeroes except byte 16 which is 1
        // private key index 12 is all 12:s except byte 16 which is 1
        // reason for byte16=1 is that private key cannot be all zeroes
        return ByteArray(32, { if (it == 16) 1.toByte() else index.toByte() })
    }

    fun pubKey(index: Int): ByteArray {
        return secp256k1_derivePubKey(privKey(index))
    }


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
                    senderPriv, senderPub, senderID, "USD",
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

