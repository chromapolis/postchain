// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.gtx

import org.postchain.base.SECP256K1CryptoSystem
import org.postchain.base.hexStringToByteArray
import org.postchain.base.secp256k1_derivePubKey
import org.postchain.core.Signature
import org.junit.Assert.*
import org.junit.Test

// TODO: figure out why we get different results
// val testBlockchainRID = SECP256K1CryptoSystem().digest("Test blockchainRID".toByteArray())
val testBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3".hexStringToByteArray()

fun mustThrowError(msg: String, code: ()->Unit) {
    try {
        code()
        fail(msg)
    } catch (e: Exception) {
    }
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


class GTXDataTest {

    @Test
    fun testGTXData() {
        val signerPub = (0..3).map(::pubKey).toTypedArray()
        val signerPriv = (0..3).map(::privKey)
        val crypto = SECP256K1CryptoSystem()

        val b = GTXDataBuilder(testBlockchainRID, signerPub.slice(0..2).toTypedArray(), crypto)
        // primitives
        b.addOperation("hello", arrayOf(GTXNull, gtx(42), gtx("Wow"), gtx(signerPub[0])))
        // array of primitives
        b.addOperation("bro", arrayOf(gtx(GTXNull, gtx(2), gtx("Nope"))))
        // dict
        b.addOperation("dictator", arrayOf(gtx(mapOf("two" to gtx(2), "five" to GTXNull))))
        // complex structure
        b.addOperation("soup", arrayOf(
                // map with array
                gtx(mapOf("array" to gtx(gtx(1), gtx(2), gtx(3)))),
                // array with map
                gtx(gtx(mapOf("inner" to gtx("space"))), GTXNull)
        ))
        b.finish()
        b.sign(crypto.makeSigner(signerPub[0], signerPriv[0]))

        // try recreating from a serialized copy
        val b2 = GTXDataBuilder.decode(b.serialize(), crypto)
        val dataForSigning = b2.getDataForSigning()
        val signature = crypto.makeSigner(signerPub[1], signerPriv[1])(dataForSigning)
        b2.addSignature(signature)

        mustThrowError("Allows duplicate signature") {
            b2.addSignature(signature, true)
        }
        mustThrowError("Allows invalid signature") {
            b2.addSignature(Signature(signerPub[2], signerPub[2]), true)
        }
        mustThrowError("Allows signature from wrong participant") {
            val wrongSignature = crypto.makeSigner(signerPub[3], signerPriv[3])(dataForSigning)
            b2.addSignature(wrongSignature, true)
        }

        b2.sign(crypto.makeSigner(signerPub[2], signerPriv[2]))

        assertTrue(b2.isFullySigned())

        val d = decodeGTXData(b2.serialize())

        assertTrue(d.signers.contentDeepEquals(
                signerPub.slice(0..2).toTypedArray()
        ))
        assertEquals(3, d.signatures.size)
        assertEquals(4, d.operations.size)
        assertEquals("bro", d.operations[1].opName)
        val op0 = d.operations[0]
        assertTrue(op0.args[0].isNull())
        assertEquals(42, op0.args[1].asInteger())
        assertEquals("Wow", op0.args[2].asString())
        assertTrue(op0.args[3].asByteArray().contentEquals(signerPub[0]))
        val op1 = d.operations[1]
        assertEquals("Nope", op1.args[0][2].asString())
        val dict2 = d.operations[2].args[0]
        assertEquals(2, dict2["two"]!!.asInteger())
        assertNull(dict2["six"])
        val mapWithArray = d.operations[3].args[0]
        assertEquals(2, mapWithArray["array"]!![1].asInteger())
        val arrayWithMap = d.operations[3].args[1]
        assertEquals("space", arrayWithMap[0]["inner"]!!.asString())
    }
}