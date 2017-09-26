package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.MultiSigBlockWitness
import com.chromaway.postchain.core.MultiSigBlockWitnessBuilder
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.UserError
import java.nio.ByteBuffer

class BaseBlockWitness(val _rawData: ByteArray, val _signatures: Array<Signature>)
    : MultiSigBlockWitness {

    override fun getSignatures(): Array<Signature> {
        return _signatures
    }

    override fun getRawData(): ByteArray {
        return _rawData
    }

    companion object Factory {
        @JvmStatic val fromBytes = {rawWitness: ByteArray ->
            val buffer = ByteBuffer.wrap(rawWitness)
            val sigCount = buffer.int
            val signatures = Array<Signature>(sigCount, {
                val subjectIdSize = buffer.int
                val subjectId = ByteArray(subjectIdSize)
                buffer.get(subjectId)
                val signatureSize = buffer.int
                val signature = ByteArray(signatureSize)
                buffer.get(signature)
                Signature(subjectId, signature)
            })
            BaseBlockWitness(rawWitness, signatures)
        }

        @JvmStatic val fromSignatures = {signatures: Array<Signature> ->
            var size = 4 // space for sig count
            signatures.forEach { size += 8 + it.subjectID.size + it.data.size }
            val bytes = ByteBuffer.allocate(size)
            bytes.putInt(signatures.size)
            for (signature in signatures) {
                bytes.putInt(signature.subjectID.size)
                bytes.put(signature.subjectID)
                bytes.putInt(signature.data.size)
                bytes.put(signature.data)
            }
            BaseBlockWitness(bytes.array(), signatures)
        }
    }
}

class BaseBlockWitnessBuilder(val cryptoSystem: CryptoSystem, val blockHeader: BlockHeader,
                              val subjects: Array<ByteArray>,
                              val threshold: Int) : MultiSigBlockWitnessBuilder {
    val signatures = mutableListOf<Signature>()

    override fun isComplete(): Boolean {
        return signatures.size >= threshold
    }

    override fun getWitness(): BlockWitness {
        if (signatures.size < threshold) throw Error("Not complete yet")
        return BaseBlockWitness.fromSignatures(signatures.toTypedArray())
    }

    override fun getMySignature(): Signature {
        return signatures[0]
    }

    override fun applySignature(s: Signature) {
        if (!subjects.any( { s.subjectID.contentEquals(it) } )) {
            throw UserError("Unexpected subject ${s.subjectID.toHex()} of signature")
        }
        if (signatures.any({it.subjectID.contentEquals(s.subjectID)})) {
            return
        }
        if (!cryptoSystem.makeVerifier()(blockHeader.rawData, s)) {
            throw UserError("Invalid signature from subject ${s.subjectID.toHex()}")
        }
        signatures.add(s)
    }

}