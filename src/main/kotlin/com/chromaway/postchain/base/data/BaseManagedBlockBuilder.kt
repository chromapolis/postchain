package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.ManagedBlockBuilder
import com.chromaway.postchain.base.Storage
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.*
import mu.KLogging

class BaseManagedBlockBuilder(
        val ctxt: EContext,
        val s: Storage,
        val bb: BlockBuilder,
        private val lifecycleListeners: List<BlockLifecycleListener>
) : ManagedBlockBuilder {
    companion object : KLogging()

    var closed: Boolean = false

    fun <RT> runOp(fn: () -> RT): RT {
        if (closed)
            throw Error("Already closed")
        try {
            return fn()
        } catch (e: Exception) {
            rollback()
            throw e
        }
    }

    override fun begin() {
        runOp({ bb.begin() })
        lifecycleListeners.forEach({it.beginBlockDone()})
    }

    override fun appendTransaction(tx: Transaction) {
        throw ProgrammerError("appendTransaction is not allowed on a ManagedBlockBuilder")
    }

    override fun appendTransaction(txData: ByteArray) {
        runOp({bb.appendTransaction(txData)})
        lifecycleListeners.forEach({it.appendTxDone(txData)})
    }

    override fun maybeAppendTransaction(tx: Transaction): Boolean {
        try {
            s.withSavepoint(ctxt) {
                try {
                    bb.appendTransaction(tx)
                } catch (userError: UserError) {
                    logger.info("Failed to append transaction ${tx.getRID().toHex()}", userError)
                    throw userError
                }
            }
        } catch (userError: UserError) {
            return false
        }
        lifecycleListeners.forEach({it.appendTxDone(tx)})
        return true
    }

    override fun maybeAppendTransaction(txData: ByteArray): Boolean {
        s.withSavepoint(ctxt) {
            bb.appendTransaction(txData)
        }
        lifecycleListeners.forEach({it.appendTxDone(txData)})
        return true
    }

    override fun finalizeBlock() {
        runOp { bb.finalizeBlock() }
        lifecycleListeners.forEach({it.finalizeBlockDone(bb.getBlockData().header)})
    }

    override fun finalizeAndValidate(bh: BlockHeader) {
        runOp { bb.finalizeAndValidate(bh) }
        lifecycleListeners.forEach({it.finalizeBlockDone(bb.getBlockData().header)})
    }

    override fun getBlockData(): BlockData {
        return bb.getBlockData()
    }

    override fun getBlockWitnessBuilder(): BlockWitnessBuilder? {
        if (closed) throw Error("Already closed")
        return bb.getBlockWitnessBuilder()
    }

    override fun commit(w: BlockWitness?) {
        runOp { bb.commit(w) }
        closed = true
        s.closeWriteConnection(ctxt, true)
        lifecycleListeners.forEach({ it.commitDone(w) })
    }

    override fun rollback() {
        if (closed) throw Error("Already closed")
        closed = true
        s.closeWriteConnection(ctxt, false)
    }
}