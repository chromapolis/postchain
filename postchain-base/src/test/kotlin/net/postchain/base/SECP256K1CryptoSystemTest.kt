// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.common.toHex
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class SECP256K1CryptoSystemTest {
    @Test
    fun testSignVerify() {
        val SUT = SECP256K1CryptoSystem()
        val random = Random()
        var privKey = ByteArray(32)
        for (i in 0..39) {
            random.nextBytes(privKey)
            val pubKey = secp256k1_derivePubKey(privKey)
            val signer = SUT.makeSigner(pubKey, privKey)
            val data = "Hello".toByteArray()
            val signature = signer(data)
            val verifier = SUT.makeVerifier()
            assertTrue("Positive test failed for privkey ${privKey.toHex()}", verifier(data, signature))
            assertFalse("Negative test failed for privkey ${privKey.toHex()}", verifier("Hell0".toByteArray(), signature))
        }
    }
}