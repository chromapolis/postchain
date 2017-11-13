// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.core

import net.postchain.base.toHex

abstract class AbstractBlockBuilder (
        val ectx: EContext,
        val store: BlockStore,
        val txFactory: TransactionFactory
) : BlockBuilder {

    // functions which need to be implemented in a concrete BlockBuilder:
    abstract fun makeBlockHeader(): BlockHeader
    abstract fun validateBlockHeader(bh: BlockHeader): Boolean
    abstract fun validateWitness(w: BlockWitness): Boolean
    // fun getBlockWitnessBuilder(): BlockWitnessBuilder?;

    var finalized: Boolean = false
    val rawTransactions = mutableListOf<ByteArray>()
    val transactions = mutableListOf<Transaction>()
    lateinit var bctx: BlockEContext
    lateinit var iBlockData: InitialBlockData
    var _blockData: BlockData? = null

    override fun begin() {
        iBlockData = store.beginBlock(ectx)
        bctx = BlockEContext(ectx.conn, ectx.chainID, ectx.nodeID, iBlockData.blockIID, iBlockData.timestamp)
    }

    override fun appendTransaction(tx: Transaction) {
        if (finalized) throw ProgrammerMistake("Block is already finalized")
        // tx.isCorrect may also throw UserMistake to provide
        // a meaningful error message to log.
        if (!tx.isCorrect()) {
            throw UserMistake("Transaction ${tx.getRID().toHex()} is not correct")
        }
        val txctx: TxEContext
        try {
            txctx = store.addTransaction(bctx, tx)
        } catch (e: Exception) {
            throw UserMistake("Failed to save tx to database", e)
        }
        // In case of errors, tx.apply may either return false or throw UserMistake
        if (tx.apply(txctx)) {
            transactions.add(tx)
            rawTransactions.add(tx.getRawData())
        } else {
            throw UserMistake("Transaction ${tx.getRID().toHex()} failed")
        }
    }

    override fun finalizeBlock() {
        val bh = makeBlockHeader()
        store.finalizeBlock(bctx, bh)
        _blockData = BlockData(bh, rawTransactions)
        finalized = true
    }

    override fun finalizeAndValidate(bh: BlockHeader) {
        if (validateBlockHeader(bh)) {
            store.finalizeBlock(bctx, bh)
            _blockData = BlockData(bh, rawTransactions)
            finalized = true
        } else {
            throw UserMistake("Invalid block header")
        }
    }

    override  fun getBlockData(): BlockData {
        return _blockData ?: throw ProgrammerMistake("Block is not finalized yet")
    }

    override fun commit(w: BlockWitness?) {
        if (w != null && !validateWitness(w)) {
            throw ProgrammerMistake("Invalid witness")
        }
        store.commitBlock(bctx, w)
    }
}
