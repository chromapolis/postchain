// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.core

import net.postchain.common.TimeLog
import net.postchain.common.toHex

/**
 * This class includes the bare minimum functionality required by a real block builder
 *
 * @property ectx Connection context including blockchain and node identifiers
 * @property store For database access
 * @property txFactory Used for serializing transaction data
 * @property finalized Boolean signalling if further updates to block is permitted
 * @property rawTransactions list of encoded transactions
 * @property transactions list of decoded transactions
 * @property _blockData complete set of data for the block including header and [rawTransactions]
 * @property iBlockData
 */
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

    /**
     * Retrieve initial block data and set block context
     */
    override fun begin() {
        iBlockData = store.beginBlock(ectx)
        bctx = BlockEContext(ectx.conn, ectx.chainID, ectx.nodeID, iBlockData.blockIID, iBlockData.timestamp)
    }

    /**
     * Apply transaction to current working block
     *
     * @param tx transaction to be added to block
     * @throws ProgrammerMistake if block is finalized
     * @throws UserMistake transaction is not correct
     * @throws UserMistake failed to save transaction to database
     * @throws UserMistake failed to apply transaction and update database state
     */
    override fun appendTransaction(tx: Transaction) {
        if (finalized) throw ProgrammerMistake("Block is already finalized")
        // tx.isCorrect may also throw UserMistake to provide
        // a meaningful error message to log.
        TimeLog.startSum("AbstractBlockBuilder.appendTransaction().isCorrect")
        if (!tx.isCorrect()) {
            throw UserMistake("Transaction ${tx.getRID().toHex()} is not correct")
        }
        TimeLog.end("AbstractBlockBuilder.appendTransaction().isCorrect")
        val txctx: TxEContext
        try {
            TimeLog.startSum("AbstractBlockBuilder.appendTransaction().addTransaction")
            txctx = store.addTransaction(bctx, tx)
            TimeLog.end("AbstractBlockBuilder.appendTransaction().addTransaction")
        } catch (e: Exception) {
            throw UserMistake("Failed to save tx to database", e)
        }
        // In case of errors, tx.apply may either return false or throw UserMistake
        TimeLog.startSum("AbstractBlockBuilder.appendTransaction().apply")
        if (tx.apply(txctx)) {
            transactions.add(tx)
            rawTransactions.add(tx.getRawData())
        } else {
            throw UserMistake("Transaction ${tx.getRID().toHex()} failed")
        }
        TimeLog.end("AbstractBlockBuilder.appendTransaction().apply")
    }

    /**
     * By finalizing the block we won't allow any more transactions to be added, and the block RID and timestamp are set
     */
    override fun finalizeBlock() {
        val bh = makeBlockHeader()
        store.finalizeBlock(bctx, bh)
        _blockData = BlockData(bh, rawTransactions)
        finalized = true
    }

    /**
     * Apart from finalizing the block, validate the header
     *
     * @param bh Block header to finalize and validate
     * @throws UserMistake Happens if validation of the block header fails
     */
    override fun finalizeAndValidate(bh: BlockHeader) {
        if (validateBlockHeader(bh)) {
            store.finalizeBlock(bctx, bh)
            _blockData = BlockData(bh, rawTransactions)
            finalized = true
        } else {
            throw UserMistake("Invalid block header")
        }
    }

    /**
     * Return block data if block is finalized.
     *
     * @throws ProgrammerMistake When block is not finalized
     */
    override  fun getBlockData(): BlockData {
        return _blockData ?: throw ProgrammerMistake("Block is not finalized yet")
    }

    /**
     * By commiting to the block we update the database to include the witness for that block
     *
     * @param w The witness for the block
     * @throws ProgrammerMistake If the witness is invalid
     */
    override fun commit(w: BlockWitness?) {
        if (w != null && !validateWitness(w)) {
            throw ProgrammerMistake("Invalid witness")
        }
        store.commitBlock(bctx, w)
    }
}
