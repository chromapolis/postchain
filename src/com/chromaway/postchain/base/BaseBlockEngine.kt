package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockchainConfiguration

open class BaseBlockEngine (val bc: BlockchainConfiguration,
                            override val peerCommConfiguration: PeerCommConfiguration,
                            val s: Storage,
                            val chainID: Int,
                            override val cryptoSystem: CryptoSystem,
                            val tq: TransactionQueue
) : BlockchainEngine
{
    private fun makeBlockBuilder(): ManagedBlockBuilder {
        val ctxt = s.openWriteConnection(chainID)
        val bb = bc.makeBlockBuilder(ctxt.conn)
        return BaseManagedBlockBuilder(ctxt, s, bb)
    }


    override fun addBlock(block: BlockDataWithWitness) {
        val mbb = loadUnfinishedBlock(block)
        mbb.commit(block.witness)
    }

    override  fun loadUnfinishedBlock(block: BlockData): ManagedBlockBuilder {
        val mbb = makeBlockBuilder()
        mbb.begin();
        for (txData in block.transactions) {
            mbb.appendTransaction(txData)
        }
        mbb.finalizeAndValidate(block.header)
        return mbb
    }

    override fun buildBlock(): ManagedBlockBuilder {
        val transactions = tq.getTransactions()
        if (transactions.isEmpty()) throw Error("No transactions to build a block")
        val mbb = makeBlockBuilder()
        mbb.begin()
        for (tx in transactions) {
            mbb.maybeAppendTransaction(tx)
        }
        // TODO handle a case with 0 transactions
        // TODO what if more transactions arrive?
        // TODO block size policy goes here
        mbb.finalize()
        return mbb
    }

}