package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.ManagedBlockBuilder
import com.chromaway.postchain.base.Storage
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.*
import mu.KLogging

class BaseManagedBlockBuilder(
        val ctxt: EContext,
        val s: Storage,
        val bb: BlockBuilder
) : ManagedBlockBuilder {
    companion object : KLogging()

    var closed: Boolean = false

    fun <RT> runOp(fn: () -> RT): RT {
        if (closed)
            throw ProgrammerMistake("Already closed")
        try {
            return fn()
        } catch (e: Exception) {
            rollback()
            throw e
        }
    }

    override fun begin() {
        runOp({ bb.begin() })
    }

    override fun appendTransaction(tx: Transaction) {
        throw ProgrammerMistake("appendTransaction is not allowed on a ManagedBlockBuilder")
    }

    override fun appendTransaction(txData: ByteArray) {
        runOp({bb.appendTransaction(txData)})
    }

    override fun maybeAppendTransaction(tx: Transaction): Boolean {
        try {
            s.withSavepoint(ctxt) {
                try {
                    bb.appendTransaction(tx)
                } catch (userMistake: UserMistake) {
                    logger.info("Failed to append transaction ${tx.getRID().toHex()}", userMistake)
                    throw userMistake
                }
            }
        } catch (userMistake: UserMistake) {
            return false
        }
        return true
    }

    override fun maybeAppendTransaction(txData: ByteArray): Boolean {
        s.withSavepoint(ctxt) {
            bb.appendTransaction(txData)
        }
        return true
    }

    override fun finalizeBlock() {
        runOp { bb.finalizeBlock() }
    }

    override fun finalizeAndValidate(bh: BlockHeader) {
        runOp { bb.finalizeAndValidate(bh) }
    }

    override fun getBlockData(): BlockData {
        return bb.getBlockData()
    }

    override fun getBlockWitnessBuilder(): BlockWitnessBuilder? {
        if (closed) throw ProgrammerMistake("Already closed")
        return bb.getBlockWitnessBuilder()
    }

    override fun commit(w: BlockWitness?) {
        runOp { bb.commit(w) }
        closed = true
        s.closeWriteConnection(ctxt, true)
    }

    override fun rollback() {
        logger.debug("${ctxt.nodeID} BaseManagedBlockBuilder.rollback()")
        if (closed) throw ProgrammerMistake("Already closed")
        closed = true
        s.closeWriteConnection(ctxt, false)
    }
}