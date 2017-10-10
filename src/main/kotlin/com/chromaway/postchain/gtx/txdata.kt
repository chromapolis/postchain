package com.chromaway.postchain.gtx

import org.asnlab.asndt.runtime.type.ByteOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import com.chromaway.postchain.gtx.messages.GTXOperation as RawGTXOperation
import com.chromaway.postchain.gtx.messages.GTXTransaction as RawGTXTransaction
import com.chromaway.postchain.gtx.messages.GTXValue as RawGTXValue


class OpData(val opName: String, val args: Array<GTXValue>)
class ExtOpData(val opName: String, val opIndex: Int, val signers: Array<ByteArray>, val args: Array<GTXValue>)

class GTXData (val signers: Array<ByteArray>,
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

}


fun parseGTXData(_rawData: ByteArray): GTXData {
    val rawGTX = RawGTXTransaction.der_decode(ByteArrayInputStream(_rawData))
    val signers: Array<ByteArray> = rawGTX.signers.toArray(arrayOf())
    val ops = rawGTX.operations.map({
        OpData(
                it.opName,
                it.args.map(::wrapValue).toTypedArray())
    }).toTypedArray()

    return GTXData(
            signers,
            rawGTX.signatures.toArray(arrayOf()),
            ops)
}

class GTXDataBuilder {
    val signers = mutableListOf<ByteArray>()
    val signatures = mutableListOf<ByteArray>()
    val operations = mutableListOf<OpData>()

    fun serialize(): ByteArray {
        return GTXData(
                signers.toTypedArray(),
                signatures.toTypedArray(),
                operations.toTypedArray()
        ).serialize()
    }


}