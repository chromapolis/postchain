package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.BaseManagedBlockBuilder
import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.ManagedBlockBuilder
import com.chromaway.postchain.base.PeerCommConfiguration
import com.chromaway.postchain.base.Storage
import com.chromaway.postchain.base.TransactionQueue
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockchainConfiguration

open class BaseBlockchainEngine(val bc: BlockchainConfiguration,
                                override val peerCommConfiguration: PeerCommConfiguration,
                                val s: Storage,
                                val chainID: Int,
                                override val cryptoSystem: CryptoSystem,
                                val tq: TransactionQueue
) : BlockchainEngine
{
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
        val transactions = tq.getTransactions()
        if (transactions.isEmpty()) throw Error("No transactions to build a block")
        val blockBuilder = makeBlockBuilder()
        blockBuilder.begin()
        for (tx in transactions) {
            blockBuilder.maybeAppendTransaction(tx)
        }
        // TODO handle a case with 0 transactions
        // TODO what if more transactions arrive?
        // TODO block size policy goes here
        blockBuilder.finalize()
        return blockBuilder
    }

}