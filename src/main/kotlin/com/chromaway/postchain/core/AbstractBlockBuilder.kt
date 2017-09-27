package com.chromaway.postchain.core

import com.chromaway.postchain.base.toHex

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
    val transactions = mutableListOf<ByteArray>()
    lateinit var bctx: BlockEContext
    lateinit var iBlockData: InitialBlockData
    var _blockData: BlockData? = null

    override fun begin() {
        iBlockData = store.beginBlock(ectx)
        bctx = BlockEContext(ectx.conn, ectx.chainID, ectx.nodeID, iBlockData.blockIID)
    }

    override fun appendTransaction(tx: Transaction) {
        if (finalized) throw ProgrammerError("Block is already finalized")
        // tx.isCorrect may also throw UserError to provide
        // a meaningful error message to log.
        if (!tx.isCorrect()) {
            throw UserError("Transaction ${tx.getRID().toHex()} is not correct")
        }
        val txctx = store.addTransaction(bctx, tx)
        // In case of errors, tx.apply may either return false or throw UserError
        if (tx.apply(txctx)) {
            transactions.add(tx.getRawData())
        } else {
            throw UserError("Transaction ${tx.getRID().toHex()} failed")
        }
    }

    override fun appendTransaction(txData: ByteArray) {
        appendTransaction(txFactory.decodeTransaction(txData))
    }

    override fun finalizeBlock() {
        val bh = makeBlockHeader()
        store.finalizeBlock(bctx, bh)
        _blockData = BlockData(bh, transactions)
        finalized = true
    }

    override fun finalizeAndValidate(bh: BlockHeader) {
        if (validateBlockHeader(bh)) {
            store.finalizeBlock(bctx, bh)
            _blockData = BlockData(bh, transactions)
            finalized = true
        } else {
            throw UserError("Invalid block header")
        }
    }

    override  fun getBlockData(): BlockData {
        return _blockData ?: throw Error("Block is not finalized yet")
    }

    override fun commit(w: BlockWitness?) {
        if (w != null && !validateWitness(w)) {
            throw Error("Invalid witness")
        }
        store.commitBlock(bctx, w)
    }
}
