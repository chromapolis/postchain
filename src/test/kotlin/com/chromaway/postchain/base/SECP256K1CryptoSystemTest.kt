package com.chromaway.postchain.base

import org.junit.Assert.*
import org.junit.Test

class SECP256K1CryptoSystemTest {
     @Test
    fun testSignVerify() {
         val SUT = SECP256K1CryptoSystem()
         val privKey = ByteArray(32, {1.toByte()})
         val pubKey = secp256k1_derivePubKey(privKey)
         val signer = SUT.makeSigner(pubKey, privKey)
         val data = "Hello".toByteArray()
         val signature = signer(data)
         val verifier = SUT.makeVerifier()
         assertTrue(verifier(data, signature))
         assertFalse(verifier("Hell0".toByteArray(), signature))
     }
}