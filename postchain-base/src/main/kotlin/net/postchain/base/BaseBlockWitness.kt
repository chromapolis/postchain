// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.core.*
import mu.KLogging
import net.postchain.common.toHex
import java.nio.ByteBuffer

/**
 * Contains data and utilities for operating on block witnesses
 *
 * @param _rawData Deserialized signatures
 * @param _signatures Serialized signatures
 */
class BaseBlockWitness(val _rawData: ByteArray, val _signatures: Array<Signature>)
    : MultiSigBlockWitness {

    override fun getSignatures(): Array<Signature> {
        return _signatures
    }

    override fun getRawData(): ByteArray {
        return _rawData
    }

    companion object Factory {
        /**
         * Create BlockWitness given the deserialized signatures
         */
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

        /**
         * Create BlockWitness given the serialized signatures
         */
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

/**
 * Class containing the necessary data and functionality necessary for building a block witness.
 * Will collect signatures and release a BlockWitness instance when a threshold number is reached.
 *
 * @property blockHeader The header of the block to which [signatures] applies
 * @property subjects Public keys elligable for signing a block
 * @property threshold Minimum amount of signatures necessary for witness to be valid
 */
class BaseBlockWitnessBuilder(val cryptoSystem: CryptoSystem, val blockHeader: BlockHeader,
                              val subjects: Array<ByteArray>,
                              val threshold: Int) : MultiSigBlockWitnessBuilder {

    /**
     * Signatures from [subjects] that have signed the [blockHeader]
     */
    val signatures = mutableListOf<Signature>()
    companion object: KLogging()

    /**
     * Check if we have enough [signatures] for witness to be valid
     *
     * @return Boolean whether witness is complete or not
     */
    override fun isComplete(): Boolean {
        return signatures.size >= threshold
    }

    /**
     * Return the witness if it is complete
     *
     * @return The witness for the block
     */
    override fun getWitness(): BlockWitness {
        if (!isComplete()) throw ProgrammerMistake("Witness not complete yet")
        return BaseBlockWitness.fromSignatures(signatures.toTypedArray())
    }

    override fun getMySignature(): Signature {
        return signatures[0]
    }

    /**
     * Add signature [s] to [signatures] if one of the public keys found in [subjects] verifies [s] for [blockHeader]
     *
     * @throws UserMistake If subject is not authorized to sign blocks
     * @throws UserMistake If subject attempts to add more than one signature
     */
    override fun applySignature(s: Signature) {
        if (!subjects.any( { s.subjectID.contentEquals(it) } )) {
            throw UserMistake("Unexpected subject ${s.subjectID.toHex()} of signature")
        }

        // Do not add two signatures from the same subject!
        if (signatures.any({it.subjectID.contentEquals(s.subjectID)})) {
            return
        }
//        logger.debug("BlockHeader: ${blockHeader.rawData.toHex()}")
        if (!cryptoSystem.makeVerifier()(blockHeader.rawData, s)) {
            throw UserMistake("Invalid signature from subject ${s.subjectID.toHex()}")
        }
        signatures.add(s)
    }

}