// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.base.data.BaseManagedBlockBuilder
import net.postchain.core.*
import net.postchain.ebft.BlockchainEngine
import nl.komponents.kovenant.task

open class BaseBlockchainEngine(private val bc: BlockchainConfiguration,
                                val s: Storage,
                                private val chainID: Long,
                                private val tq: TransactionQueue,
                                private val strategy: BlockBuildingStrategy,
                                private val useParallelDecoding: Boolean = false
) : BlockchainEngine
{
    companion object : KLogging()

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

    fun parLoadUnfinishedBlock(block: BlockData): ManagedBlockBuilder {
        val factory = bc.getTransactionFactory()
        val transactions = block.transactions.map { txData ->
            task {
                val tx = factory.decodeTransaction(txData)
                if (!tx.isCorrect()) throw UserMistake("Transaction is not correct")
                tx
            }
        }
        val blockBuilder = makeBlockBuilder()
        blockBuilder.begin()
        for (tx in transactions) {
            blockBuilder.appendTransaction(tx.get())
        }
        blockBuilder.finalizeAndValidate(block.header)
        return blockBuilder
    }

    fun seqLoadUnfinishedBlock(block: BlockData): ManagedBlockBuilder {
        val blockBuilder = makeBlockBuilder()
        val factory = bc.getTransactionFactory()
        blockBuilder.begin()
        for (txData in block.transactions) {
            blockBuilder.appendTransaction(factory.decodeTransaction(txData))
        }
        blockBuilder.finalizeAndValidate(block.header)
        return blockBuilder
    }

    override fun loadUnfinishedBlock(block: BlockData): ManagedBlockBuilder {
        return if (useParallelDecoding)
            parLoadUnfinishedBlock(block)
        else
            seqLoadUnfinishedBlock(block)
    }

    override fun buildBlock(): ManagedBlockBuilder {
        val blockBuilder = makeBlockBuilder()
        val abstractBlockBuilder = ((blockBuilder as BaseManagedBlockBuilder).bb as AbstractBlockBuilder)
        blockBuilder.begin()

        // TODO Potential problem: if the block fails for some reason,
        // the transaction queue is gone. This could potentially happen
        // during a revolt. We might need a "transactional" tx queue...

        while (true) {
            logger.debug("Checking transaction queue")
            val tx = tq.takeTransaction()
            if (tx != null) {
                logger.info("Appending transaction ${tx.getRID().toHex()}")
                val exception = blockBuilder.maybeAppendTransaction(tx)
                if (exception != null) {
                    tq.rejectTransaction(tx, exception)
                } else {
                    // tx is fine, consider stopping
                    if (strategy.shouldStopBuildingBlock(abstractBlockBuilder)) {
                        logger.info("Block size limit is reached")
                        break
                    }
                }
            } else { // tx == null
                break
            }
        }
        blockBuilder.finalizeBlock()
        logger.info("Block is finalized")
        return blockBuilder
    }
}