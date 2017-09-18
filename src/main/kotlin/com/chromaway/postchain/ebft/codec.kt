package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.Signer
import com.chromaway.postchain.base.Verifier
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.UserError
import com.chromaway.postchain.ebft.messages.Message
import com.chromaway.postchain.ebft.messages.SignedMessage
import java.io.ByteArrayOutputStream
import java.util.Arrays

fun encodeAndSign(m: Message, sign: Signer): ByteArray {
    val signingBytesOut = ByteArrayOutputStream()
    m.der_encode(signingBytesOut)
    val signingBytes = signingBytesOut.toByteArray()

    val signature = sign(signingBytes)
    val sm = SignedMessage()
    sm.message = signingBytes
    sm.pubkey = signature.subjectID
    sm.signature = signature.data
    val out = ByteArrayOutputStream()
    sm.der_encode(out)
    return out.toByteArray()
}

fun decodeSignedMessage(bytes: ByteArray): SignedMessage {
    try {
        return  SignedMessage.der_decode(bytes.inputStream())
    } catch (e: Exception) {
        throw UserError("bytes ${bytes.toHex()} cannot be decoded", e)
    }
}

fun decodeWithoutVerification(bytes: ByteArray): Message {
    try {
        val sm = SignedMessage.TYPE.decode(bytes, SignedMessage.CONV) as SignedMessage
        return Message.TYPE.decode(sm.message, Message.CONV) as Message
    } catch (e: Exception) {
        throw UserError("bytes cannot be decoded", e)
    }
}

fun decodeAndVerify(bytes: ByteArray, pubkey: ByteArray, verify: Verifier): Message {
    val sm = SignedMessage.der_decode(bytes.inputStream()) as SignedMessage
    if (Arrays.equals(sm.pubkey, pubkey)
            && verify(sm.message, Signature(sm.pubkey, sm.signature))) {
        return Message.der_decode(sm.message.inputStream())
    } else {
        throw UserError("Verification failed")
    }
}
