// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.base.data.BaseManagedBlockBuilder
import net.postchain.core.*
import net.postchain.ebft.BlockchainEngine

open class BaseBlockchainEngine(private val bc: BlockchainConfiguration,
                                val s: Storage,
                                private val chainID: Long,
                                private val tq: TransactionQueue,
                                private val strategy: BlockBuildingStrategy
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
        logger.info("Starting to build block")
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
                    if (strategy.shouldStopBuildingBlock(abstractBlockBuilder)) {
                        logger.info("Block size limit is reached")
                        break
                    }
                }
            } else {
                break
            }
        }
        blockBuilder.finalizeBlock()
        logger.info("Block is finalized")
        return blockBuilder
    }
}