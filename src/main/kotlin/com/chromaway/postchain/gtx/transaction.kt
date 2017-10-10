package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.core.*

class GTXTransaction (val _rawData: ByteArray, module: GTXModule, val cs: CryptoSystem): Transaction {

    val myRID: ByteArray = cs.digest(_rawData)
    val data: GTXData
    val signers: Array<ByteArray>
    val signatures: Array<ByteArray>
    val ops: Array<Transactor>
    var isChecked: Boolean = false
    val digestForSigning: ByteArray

    init {
        data = decodeGTXData(_rawData)

        digestForSigning = data.getDigestForSigning(cs)

        signers = data.signers
        signatures = data.signatures

        ops = data.getExtOpData().map({ module.makeTransactor(it) }).toTypedArray()
    }

    override fun isCorrect(): Boolean {
        if (isChecked) return true

        if (signatures.size != signers.size) return false

        for ((idx, signer) in signers.withIndex()) {
            val signature = signatures[idx]
            if (!cs.verifyDigest(digestForSigning, Signature(signer, signature))) {
                return false
            }
        }

        for (op in ops) {
            if (!op.isCorrect()) return false
        }

        isChecked = true
        return true
    }

    override fun getRawData(): ByteArray {
        return _rawData
    }

    override fun getRID(): ByteArray {
         return myRID
    }

    override fun apply(ctx: TxEContext): Boolean {
        if (!isCorrect()) throw Error("Transaction is not correct")
        for (op in ops) {
            if (!op.apply(ctx))
                throw Error("Operation failed")
        }
        return true
    }

}

class GTXTransactionFactory(val module: GTXModule, val cs: CryptoSystem): TransactionFactory {
    override fun decodeTransaction(data: ByteArray): Transaction {
        return GTXTransaction(data, module, cs)
    }
}
