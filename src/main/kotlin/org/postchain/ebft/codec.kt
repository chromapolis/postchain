// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.ebft

import org.postchain.base.Signer
import org.postchain.base.Verifier
import org.postchain.base.toHex
import org.postchain.core.Signature
import org.postchain.core.UserMistake
import org.postchain.ebft.message.EbftMessage
import org.postchain.ebft.message.SignedMessage
import java.util.Arrays

fun encodeAndSign(m: EbftMessage, sign: Signer): ByteArray {
    val signingBytes = m.encode()
    val signature = sign(signingBytes)
    val sm = SignedMessage(signingBytes, signature.subjectID, signature.data)
    return sm.encode()
}

fun decodeSignedMessage(bytes: ByteArray): SignedMessage {
    try {
        return  SignedMessage.decode(bytes)
    } catch (e: Exception) {
        throw UserMistake("bytes ${bytes.toHex()} cannot be decoded", e)
    }
}

fun decodeWithoutVerification(bytes: ByteArray): SignedMessage {
    try {
        val sm = SignedMessage.decode(bytes)
        return sm
    } catch (e: Exception) {
        throw UserMistake("bytes cannot be decoded", e)
    }
}

fun decodeAndVerify(bytes: ByteArray, pubkey: ByteArray, verify: Verifier): EbftMessage {
    val sm = SignedMessage.decode(bytes)
    if (Arrays.equals(sm.pubKey, pubkey)
            && verify(sm.message, Signature(sm.pubKey, sm.signature))) {
        return EbftMessage.decode(sm.message)
    } else {
        throw UserMistake("Verification failed")
    }
}
