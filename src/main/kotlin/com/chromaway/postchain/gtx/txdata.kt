package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.Signer
import com.chromaway.postchain.core.ProgrammerMistake
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.UserMistake
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import com.chromaway.postchain.gtx.messages.GTXOperation as RawGTXOperation
import com.chromaway.postchain.gtx.messages.GTXTransaction as RawGTXTransaction
import com.chromaway.postchain.gtx.messages.GTXValue as RawGTXValue

class OpData(val opName: String, val args: Array<GTXValue>)
class ExtOpData(val opName: String, val opIndex: Int, val signers: Array<ByteArray>, val args: Array<GTXValue>)

val EMPTY_SIGNATURE: ByteArray = ByteArray(0)

class GTXData(val blockchainRID: ByteArray,
              val signers: Array<ByteArray>,
              val signatures: Array<ByteArray>,
              val operations: Array<OpData>) {

    fun getExtOpData(): Array<ExtOpData> {
        return operations.mapIndexed({
            idx, op ->
            ExtOpData(op.opName, idx, signers, op.args)
        }).toTypedArray()
    }

    fun serialize(): ByteArray {
        val rtx = RawGTXTransaction()
        rtx.blockchainRID = blockchainRID
        rtx.operations = Vector<RawGTXOperation>(operations.map({
            val rop = RawGTXOperation()
            rop.opName = it.opName
            rop.args = Vector<RawGTXValue>(it.args.map({ it.getRawGTXValue() }))
            rop
        }))
        rtx.signatures = Vector<ByteArray>(signatures.toMutableList())
        rtx.signers = Vector<ByteArray>(signers.toMutableList())
        val outs = ByteArrayOutputStream()
        rtx.der_encode(outs)
        return outs.toByteArray()
    }

    fun serializeWithoutSignatures(): ByteArray {
        return GTXData(
                blockchainRID,
                signers,
                arrayOf(),
                operations).serialize()
    }

    fun getDigestForSigning(crypto: CryptoSystem): ByteArray {
        return crypto.digest(serializeWithoutSignatures())
    }

}


fun decodeGTXData(_rawData: ByteArray): GTXData {
    val rawGTX = RawGTXTransaction.der_decode(ByteArrayInputStream(_rawData))
    val signers: Array<ByteArray> = rawGTX.signers.toArray(arrayOf())
    val ops = rawGTX.operations.map({
        OpData(
                it.opName,
                it.args.map(::wrapValue).toTypedArray())
    }).toTypedArray()

    return GTXData(
            rawGTX.blockchainRID,
            signers,
            rawGTX.signatures.toArray(arrayOf()),
            ops)
}

// TODO: cache data for signing and digest

class GTXDataBuilder(val blockchainRID: ByteArray,
                     val signers: Array<ByteArray>,
                     val crypto: CryptoSystem,
                     val signatures: Array<ByteArray>,
                     val operations: MutableList<OpData>,
                     private var finished: Boolean) {

    // construct empty builder
    constructor(blockchainRID: ByteArray,
                signers: Array<ByteArray>,
                crypto: CryptoSystem) :
            this(
                    blockchainRID,
                    signers,
                    crypto,
                    Array(signers.size, { EMPTY_SIGNATURE }),
                    mutableListOf<OpData>(),
                    false)
    {
    }

    companion object {
        fun decode(bytes: ByteArray, crypto: CryptoSystem, finished: Boolean = true): GTXDataBuilder {
            val data = decodeGTXData(bytes)
            return GTXDataBuilder(
                    data.blockchainRID,
                    data.signers, crypto, data.signatures,
                    data.operations.toMutableList(), finished)
        }
    }

    fun finish() {
        finished = true
    }

    fun isFullySigned(): Boolean {
        return signatures.all { !it.contentEquals(EMPTY_SIGNATURE) }
    }

    fun addOperation(opName: String, args: Array<GTXValue>) {
        if (finished) throw ProgrammerMistake("Already finished")
        operations.add(OpData(opName, args))
    }

    fun verifySignature(s: Signature): Boolean {
        return crypto.verifyDigest(getDigestForSigning(), s)
    }

    fun addSignature(s: Signature, check: Boolean = true) {
        if (!finished) throw ProgrammerMistake("Must be finished before signing")

        if (check) {
            if (!verifySignature(s)) {
                throw UserMistake("Signature is not valid")
            }
        }

        val idx = signers.indexOfFirst { it.contentEquals(s.subjectID) }
        if (idx != -1) {
            if (signatures[idx].contentEquals(EMPTY_SIGNATURE)) {
                signatures[idx] = s.data
            } else throw UserMistake("Signature already exists")
        } else throw UserMistake("Singer not found")
    }

    fun getDataForSigning(): ByteArray {
        if (!finished) throw ProgrammerMistake("Must be finished before signing")

        return getGTXData().serializeWithoutSignatures()
    }

    fun getDigestForSigning(): ByteArray {
        if (!finished) throw ProgrammerMistake("Must be finished before signing")

        return getGTXData().getDigestForSigning(crypto)
    }

    fun sign(signer: Signer) {
        addSignature(signer(getDataForSigning()), false)
    }

    fun getGTXData(): GTXData {
        return GTXData(
                blockchainRID,
                signers,
                signatures,
                operations.toTypedArray()
        )
    }

    fun serialize(): ByteArray {
        return getGTXData().serialize()
    }

}
