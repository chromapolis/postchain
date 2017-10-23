package com.chromaway.postchain.api

import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.configurations.GTXTestModule
import com.chromaway.postchain.gtx.GTXDataBuilder
import com.chromaway.postchain.gtx.GTXTransactionFactory
import com.chromaway.postchain.gtx.gtx
import com.chromaway.postchain.gtx.myCS
import com.chromaway.postchain.gtx.privKey
import com.chromaway.postchain.gtx.pubKey
import org.junit.Test
import org.junit.Assert.*
import java.util.Random

class RestApiTestManual {
    val port = 58373
    val restTools = RestTools()
    val cryptoSystem = SECP256K1CryptoSystem()

    fun makeTestTx(id: Long, value: String): ByteArray {
        val b = GTXDataBuilder(arrayOf(pubKey(0)), cryptoSystem)
        b.addOperation("gtx_test", arrayOf(gtx(id), gtx(value)))
        b.finish()
        b.sign(cryptoSystem.makeSigner(pubKey(0), privKey(0)))
        return b.serialize()
    }

//    @Test
    fun testGtxTestModuleBackend() {
        val restTools = RestTools()
        val query = """{"type"="gtx_test_get_value", "txRID"="abcd"}"""
        val response = restTools.post(port, "/query", query)
        assertEquals(200, response!!.code)
        assertEquals("null", response.body)


        val txBytes = makeTestTx(1L, "hello${Random().nextLong()}")
        val response2 = restTools.post(port, "/tx", """{"tx"="${txBytes.toHex()}"}""")
        assertEquals(200, response2!!.code)

        val transaction = GTXTransactionFactory(GTXTestModule(), cryptoSystem).decodeTransaction(txBytes)
        restTools.awaitConfirmed(port, transaction)
    }
}