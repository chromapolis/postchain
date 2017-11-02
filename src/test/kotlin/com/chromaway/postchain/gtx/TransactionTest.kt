package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.SECP256K1CryptoSystem
import org.junit.Assert.assertTrue
import org.junit.Test

val myCS = SECP256K1CryptoSystem()

fun makeNOPGTX(): ByteArray {
    val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(0)), myCS)
    b.addOperation("nop", arrayOf(gtx(42)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

class GTXTransactionTest {

    val module = StandardOpsGTXModule()
    val gtxData = makeNOPGTX()

    @Test
    fun runtx() {
        val factory = GTXTransactionFactory(testBlockchainRID, module, myCS)
        val tx = factory.decodeTransaction(gtxData)
        assertTrue(tx.getRID().size > 1)
        assertTrue(tx.getRawData().size > 1)
        assertTrue((tx as GTXTransaction).ops.size == 1)
        assertTrue(tx.isCorrect())
    }
}