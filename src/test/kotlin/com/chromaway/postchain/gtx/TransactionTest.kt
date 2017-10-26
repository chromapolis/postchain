package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.core.TxEContext
import org.junit.Test
import org.junit.Assert.*

val myCS = SECP256K1CryptoSystem()

fun makeNOPGTX(): ByteArray {
    val b = GTXDataBuilder(EMPTY_SIGNATURE, arrayOf(pubKey(0)), myCS)
    b.addOperation("nop", arrayOf(gtx(42)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

class GTXTransactionTest {

    val module = GTX_NOP_Module()
    val gtxData = makeNOPGTX()

    @Test
    fun runtx() {
        val factory = GTXTransactionFactory(EMPTY_SIGNATURE, module, myCS)
        val tx = factory.decodeTransaction(gtxData)
        assertTrue(tx.getRID().size > 1)
        assertTrue(tx.getRawData().size > 1)
        assertTrue((tx as GTXTransaction).ops.size == 1)
        assertTrue(tx.isCorrect())
        // we should test apply() here but it's a bit problematic to get conn, so...
    }
}