package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.ManagedBlockBuilder
import com.chromaway.postchain.base.Storage
import com.chromaway.postchain.base.data.BaseManagedBlockBuilder
import com.chromaway.postchain.base.withWriteConnection
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockLifecycleListener
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.TransactionQueue

open class BaseBlockchainEngine(private val bc: BlockchainConfiguration,
                                val s: Storage,
                                private val chainID: Int,
                                private val tq: TransactionQueue
) : BlockchainEngine
{
    override fun initializeDB() {
        withWriteConnection(s, chainID) { ctx ->
            bc.initializeDB(ctx)
            true
        }
    }

    private fun makeBlockBuilder(): ManagedBlockBuilder {
        val ctxt = s.openWriteConnection(chainID)
        val bb = bc.makeBlockBuilder(ctxt)
        return BaseManagedBlockBuilder(ctxt, s, bb)
    }

    override fun addBlock(block: BlockDataWithWitness) {
        val blockBuilder = loadUnfinishedBlock(block)
        blockBuilder.commit(block.witness)
    }

    override  fun loadUnfinishedBlock(block: BlockData): ManagedBlockBuilder {
        val blockBuilder = makeBlockBuilder()
        blockBuilder.begin()
        for (txData in block.transactions) {
            blockBuilder.appendTransaction(txData)
        }
        blockBuilder.finalizeAndValidate(block.header)
        return blockBuilder
    }

    override fun buildBlock(): ManagedBlockBuilder {
//        if (transactions.isEmpty()) throw Error("No transactions to build a block")
        val blockBuilder = makeBlockBuilder()
        blockBuilder.begin()

        // TODO Potential problem: if the block fails for some reason,
        // the transaction queue is gone. This could potentially happen
        // during a revolt. We might need a "transactional" tx queue...
        val transactions = tq.dequeueTransactions()
        for (tx in transactions) {
            blockBuilder.maybeAppendTransaction(tx)
        }
        // TODO handle a case with 0 transactions - Done
        // TODO what if more transactions arrive? - They will wait until next block
        // TODO block size policy goes here - Uhm, ok.
        blockBuilder.finalizeBlock()
        return blockBuilder
    }
}