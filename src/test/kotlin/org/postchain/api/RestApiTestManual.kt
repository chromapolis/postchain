// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.api

import org.postchain.base.SECP256K1CryptoSystem
import org.postchain.base.toHex
import org.postchain.configurations.GTXTestModule
import org.postchain.gtx.*
import org.junit.Test
import org.junit.Assert.*
import java.util.Random

class RestApiTestManual {
    val port = 58373
    val restTools = RestTools()
    val cryptoSystem = SECP256K1CryptoSystem()

    fun makeTestTx(id: Long, value: String): ByteArray {
        val b = GTXDataBuilder(EMPTY_SIGNATURE, arrayOf(pubKey(0)), cryptoSystem)
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

        val transaction = GTXTransactionFactory(EMPTY_SIGNATURE, GTXTestModule(), cryptoSystem).decodeTransaction(txBytes)
        restTools.awaitConfirmed(port, transaction)
    }
}