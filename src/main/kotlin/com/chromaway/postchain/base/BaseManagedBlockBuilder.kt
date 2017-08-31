package com.chromaway.postchain.base

import com.chromaway.postchain.core.*

class BaseManagedBlockBuilder(
        val ctxt: EContext,
        val s: Storage,
        val bb: BlockBuilder
)
    : ManagedBlockBuilder {
    var closed: Boolean = false

    fun<RT> runOp(fn: ()->RT): RT {
        if (closed) throw Error("Already closed")
        try {
            return fn();
        } catch (e: Exception) {
            rollback()
            throw e
        }
    }

    override fun begin() { runOp( { bb.begin()} ) }

    override fun appendTransaction(tx: Transaction) { runOp { bb.appendTransaction(tx) } }
    override fun appendTransaction(txData: ByteArray) { runOp { bb.appendTransaction(txData)}}

    override fun maybeAppendTransaction(tx: Transaction): Boolean {
        s.withSavepoint(ctxt) {
            bb.appendTransaction(tx)
        }
        return true
    }

    override fun maybeAppendTransaction(txData: ByteArray): Boolean {
        s.withSavepoint(ctxt) {
            bb.appendTransaction(txData)
        }
        return true
    }


    override fun finalize() { runOp { bb.finalize() }}
    override fun finalizeAndValidate(bh: BlockHeader) { runOp { bb.finalizeAndValidate(bh)} }

    override fun getBlockData(): BlockData {
        return bb.getBlockData()
    }

    override fun commit(w: BlockWitness?) {
        runOp { bb.commit(w) }
        closed = true
        s.closeWriteConnection(ctxt, true)
    }

    override fun rollback() {
        if (closed) throw Error("Already closed")
        closed = true
        s.closeWriteConnection(ctxt, false)
    }
}