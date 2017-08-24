package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.MultiSigBlockWitnessBuilder
import com.chromaway.postchain.core.MultiSigBlockWitness
import com.chromaway.postchain.core.Signature

class BaseBlockWitness(val _rawData: ByteArray, override val blockRID: ByteArray, val _signatures: Array<Signature>)
    : MultiSigBlockWitness {

    override fun getSignatures(): Array<Signature> {
        return _signatures
    }

    override fun getRawData(): ByteArray {
        return _rawData
    }

    companion object Factory {
        @JvmStatic val make: Int = 0
    }

}

class BaseBlockWitnessBuilder(val subjects: Array<ByteArray>, val threshold: Int) : MultiSigBlockWitnessBuilder {
    val signatures: Array<Signature?> = arrayOfNulls(subjects.size)

    override fun isComplete(): Boolean {
        return signatures.count({ it != null }) >= threshold
    }
    override fun getWitness(): BlockWitness {
        val nnsigs = signatures.filter({ it != null })
        if (nnsigs.size < threshold) throw Error("Not complete yet")
        TODO("Not implemented yet")
        //return BaseBlockWitness();
    }

    override fun getMySignature(): Signature = TODO("Not implemented yet")
    override fun applySignature(s: Signature) = TODO("Not implemented yet")

}