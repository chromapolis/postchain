package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.Signer
import com.chromaway.postchain.base.Verifier
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.UserError
import com.chromaway.postchain.ebft.message.Messaged
import com.chromaway.postchain.ebft.message.SignedMessage
import java.util.Arrays

fun encodeAndSign(m: Messaged, sign: Signer): ByteArray {
    val signingBytes = m.encode()
    val signature = sign(signingBytes)
    val sm = SignedMessage(signingBytes, signature.subjectID, signature.data)
    return sm.encode()
}

fun decodeSignedMessage(bytes: ByteArray): SignedMessage {
    try {
        return  SignedMessage.decode(bytes)
    } catch (e: Exception) {
        throw UserError("bytes ${bytes.toHex()} cannot be decoded", e)
    }
}

fun decodeWithoutVerification(bytes: ByteArray): SignedMessage {
    try {
        val sm = SignedMessage.decode(bytes)
        return sm
    } catch (e: Exception) {
        throw UserError("bytes cannot be decoded", e)
    }
}

fun decodeAndVerify(bytes: ByteArray, pubkey: ByteArray, verify: Verifier): Messaged {
    val sm = SignedMessage.decode(bytes)
    if (Arrays.equals(sm.pubKey, pubkey)
            && verify(sm.message, Signature(sm.pubKey, sm.signature))) {
        return Messaged.decode(sm.message)
    } else {
        throw UserError("Verification failed")
    }
}
