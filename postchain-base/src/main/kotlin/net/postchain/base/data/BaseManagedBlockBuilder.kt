// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.ManagedBlockBuilder
import net.postchain.base.Storage
import net.postchain.common.TimeLog
import net.postchain.common.toHex
import net.postchain.core.*

/**
 * Wrapper around BlockBuilder providing more control over the process of building blocks,
 * with checks to see if current working block has been commited or not, and rolling back
 * database state in case some operation fails
 *
 * @property ctxt Connection context including blockchain and node identifiers
 * @property s For database access
 * @property bb The base block builder
 * @property onCommit Clean-up function to be called when block has been commited
 * @property closed Boolean for if block is open to further modifications and queries. It is closed if
 * an operation fails to execute in full or if a witness is created and the block commited.
 */
class BaseManagedBlockBuilder(
        val ctxt: EContext,
        val s: Storage,
        val bb: BlockBuilder,
        val onCommit: (BlockBuilder)->Unit
) : ManagedBlockBuilder {
    companion object : KLogging()

    var closed: Boolean = false

    /**
     * Wrapper for blockbuilder operations. Will close current working block for further modifications
     * if an operation fails to execute in full.
     *
     * @param RT type of returned object from called operation (Currently all Unit)
     * @param fn operation to be executed
     * @return whatever [fn] returns
     */
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
        runOp { bb.appendTransaction(tx) }
    }

    /**
     * Append transaction as long as everything is OK. withSavepoint will roll back any potential changes
     * to the database state if appendTransaction fails to complete
     *
     * @param tx Transaction to be added to the current working block
     * @return exception if error occurs
     */
    override fun maybeAppendTransaction(tx: Transaction): Exception? {
        TimeLog.startSum("BaseManagedBlockBuilder.maybeAppendTransaction().withSavepoint")
        val exception = s.withSavepoint(ctxt) {
                TimeLog.startSum("BaseManagedBlockBuilder.maybeAppendTransaction().insideSavepoint")
                try {
                    bb.appendTransaction(tx)
                } finally {
                    TimeLog.end("BaseManagedBlockBuilder.maybeAppendTransaction().insideSavepoint")
                }
            }
        TimeLog.end("BaseManagedBlockBuilder.maybeAppendTransaction().withSavepoint")
        if (exception != null) {
            logger.info("Failed to append transaction ${tx.getRID().toHex()}", exception)
        }
        return exception
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
        onCommit(bb)
    }

    override fun rollback() {
        logger.debug("${ctxt.nodeID} BaseManagedBlockBuilder.rollback()")
        if (closed) throw ProgrammerMistake("Already closed")
        closed = true
        s.closeWriteConnection(ctxt, false)
    }
}