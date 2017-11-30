package net.postchain.modules.perftest

import net.postchain.common.RestTools
import net.postchain.common.toHex
import net.postchain.gtx.GTXDataBuilder
import net.postchain.gtx.gtx
import net.postchain.modules.ft.testBlockchainRID
import net.postchain.test.modules.ft.FTIntegrationTest
import org.junit.Test
import java.util.LinkedList

/**
 * The test methods of this class should be started before the node starts, so that the
 * node doesn't spend time doing nothing.
 *
 * Not that for testBombGtxTest() you need to wait until the transactions are prepared until you
 * start the node
 */
class ClientManual : FTIntegrationTest() {
    val assetID = "TST"

    val restTools = RestTools()

    fun postTx(bytes: ByteArray) {
        restTools.post(8383, "/tx", "{tx: ${bytes.toHex()}}")
    }

    fun makeTestTx(id: Long, value: String): ByteArray {
        val b = GTXDataBuilder(testBlockchainRID, arrayOf(alicePubKey), cryptoSystem)
        b.addOperation("gtx_test", arrayOf(gtx(id), gtx(value)))
        b.finish()
        b.sign(cryptoSystem.makeSigner(alicePubKey, alicePrivKey))
        return b.serialize()
    }

    /**
     * This one bombs a FT node with transactions moving 10 TST tokens back and forth
     */
    @Test
    fun testBombFT() {
        // Setup Alice's and Bob's accounts. Fun Alice with 100 TST
        var nodeStarted = false;
        while (!nodeStarted) {
            try {
                postTx(makeRegisterTx(arrayOf(aliceAccountDesc, bobAccountDesc), 1))
                postTx(makeIssueTx(0, issuerID, aliceAccountID, assetID, 100))
                nodeStarted = true;
            } catch (e: Exception) {
                Thread.sleep(10)
            }
        }

        val endTime = System.currentTimeMillis() + 600000
        var i = 0;
        while (endTime > System.currentTimeMillis()) {
            postTx(makeTransferTx(alicePubKey, alicePrivKey, aliceAccountID, assetID, 10, bobAccountID, "A->B ${i}"))
            postTx(makeTransferTx(bobPubKey, bobPrivKey, bobAccountID, assetID, 10, aliceAccountID, memo1 = "B->A ${i++}"))
            println("$i")
        }
    }

    /**
     * This bombs the GTXTestModule node with 100000 transactions that are precreated
     * for maximum speed once the test starts
     */
    @Test
    fun testBombGtxTest() {
        val list = LinkedList<ByteArray>()
        // We cant create the transactions fast enough, so we need to pre-create them
        for (i in 0..99999) {
            list.add(makeTestTx(1, "${i}"))
        }
        println("Precreated transactions")
        val endTime = System.currentTimeMillis() + 600000
        var i = 0;
        while (endTime > System.currentTimeMillis()) {
            val tx = list.first
            try {
                postTx(tx)
                list.removeFirst()
            } catch (e: Exception) {
                Thread.sleep(10)
            }
        }
    }

}