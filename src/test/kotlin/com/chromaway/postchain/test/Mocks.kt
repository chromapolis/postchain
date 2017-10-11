package com.chromaway.postchain.test

import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.Signer
import com.chromaway.postchain.base.Verifier
import com.chromaway.postchain.base.secp256k1_verify
import com.chromaway.postchain.core.Signature
import java.security.MessageDigest
import kotlin.experimental.xor

class MockCryptoSystem : CryptoSystem {

    override fun digest(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes)
    }

    override fun makeSigner(pubKey: ByteArray, privKey: ByteArray): Signer {
        return { data ->
            val signature = ByteArray(32)
            val digest = digest(data)
            digest.forEachIndexed { index, byte ->  byte xor pubKey[index]}
            Signature(pubKey, digest)
        }
    }

    override fun verifyDigest(ddigest: ByteArray, s: Signature): Boolean {
        return secp256k1_verify(ddigest, s.subjectID, s.data)
    }

    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            secp256k1_verify(digest(data), signature.subjectID, signature.data)
        }
    }
}