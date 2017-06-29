package com.chromaway.postchain.core

import java.sql.Connection

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
    var _blockData: BlockData? = null

    override fun begin() {
        val blockIID = store.beginBlock(ectx)
        bctx = BlockEContext(ectx.conn, ectx.chainID, blockIID)
    }

    override fun appendTransaction(tx: Transaction) {
        if (finalized) throw Error("Block is already finalized")
        tx.apply(bctx)
        transactions.add(tx.getRawData())
    }

    override fun appendTransaction(txData: ByteArray) {
        appendTransaction(txFactory.decodeTransaction(txData))
    }

    override fun finalize() {
        val bh = makeBlockHeader()
        store.finalizeBlock(bctx, bh)
        _blockData = BlockData(bh, transactions.toTypedArray())
        finalized = true
    }

    override fun finalizeAndValidate(bh: BlockHeader) {
        if (validateBlockHeader(bh)) {
            store.finalizeBlock(bctx, bh)
            _blockData = BlockData(bh, transactions.toTypedArray())
            finalized = true
        } else {
            throw Error("Invalid block header")
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

fun storeBlock(conn: Connection, bf: BlockchainConfiguration, blockData: BlockDataWithWitness) {
    val blockBuilder = bf.makeBlockBuilder(conn)
    blockBuilder.begin();
    for (txData in blockData.transactions) {
        blockBuilder.appendTransaction(txData)
    }
    blockBuilder.finalizeAndValidate(blockData.header)
    blockBuilder.commit(blockData.witness)
}