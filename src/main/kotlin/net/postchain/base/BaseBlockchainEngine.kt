// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseManagedBlockBuilder
import net.postchain.core.*
import net.postchain.ebft.BlockchainEngine

open class BaseBlockchainEngine(private val bc: BlockchainConfiguration,
                                val s: Storage,
                                private val chainID: Long,
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
        return BaseManagedBlockBuilder(ctxt, s, bb, { _bb ->
            val aBB = _bb as AbstractBlockBuilder
            tq.removeAll(aBB.transactions)
        })
    }

    override fun addBlock(block: BlockDataWithWitness) {
        val blockBuilder = loadUnfinishedBlock(block)
        blockBuilder.commit(block.witness)
    }

    override  fun loadUnfinishedBlock(block: BlockData): ManagedBlockBuilder {
        val blockBuilder = makeBlockBuilder()
        val factory = bc.getTransactionFactory()
        blockBuilder.begin()
        for (txData in block.transactions) {
            blockBuilder.appendTransaction(factory.decodeTransaction(txData))
        }
        blockBuilder.finalizeAndValidate(block.header)
        return blockBuilder
    }

    override fun buildBlock(): ManagedBlockBuilder {
        val blockBuilder = makeBlockBuilder()
        blockBuilder.begin()

        // TODO Potential problem: if the block fails for some reason,
        // the transaction queue is gone. This could potentially happen
        // during a revolt. We might need a "transactional" tx queue...

        while (true) {
            val tx = tq.takeTransaction()
            if (tx != null) {
                val exception = blockBuilder.maybeAppendTransaction(tx)
                if (exception != null) {
                    tq.rejectTransaction(tx, exception)
                }
            } else {
                break
            }
        }
        blockBuilder.finalizeBlock()
        return blockBuilder
    }
}