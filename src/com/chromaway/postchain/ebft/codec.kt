package com.chromaway.postchain.ebft

import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.ebft.messages.Message;
import com.chromaway.postchain.ebft.messages.SignedMessage
import org.asnlab.asndt.runtime.type.Buffer
import java.util.*

import com.chromaway.postchain.engine.*

fun encodeAndSign(m: Message, sign: Signer): ByteArray {
    val buffer = Buffer.allocate(1024, Buffer.DISTINGUISHED_ENCODING_RULES).autoExpand();
    Message.TYPE.encode(m, buffer, Message.CONV);
    val bytes = buffer.array()
    buffer.clear()
    val signature = sign(bytes)
    val sm = SignedMessage();
    sm.message = bytes
    sm.pubkey = signature.subjectID
    sm.signature = signature.data
    SignedMessage.TYPE.encode(sm, buffer, SignedMessage.CONV)
    return buffer.array()
}

fun decodeAndVerify(bytes: ByteArray, pubkey: ByteArray, verify: Verifier): Message {
    val sm = SignedMessage.TYPE.decode(bytes, SignedMessage.CONV) as SignedMessage
    if (Arrays.equals(sm.pubkey, pubkey)
            && verify(sm.message, Signature(sm.pubkey, sm.signature))) {
        return Message.TYPE.decode(sm.message, Message.CONV) as Message
    } else {
        throw Error("Verification failed")
    }
}
