package com.chromaway.postchain.base.data

import com.chromaway.postchain.core.BlockEContext
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.InitialBlockData
import com.chromaway.postchain.core.ProgrammerMistake
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TransactionStatus
import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.core.UserMistake
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.stream.Stream

class BaseBlockStore : BlockStore {
    var db: DatabaseAccess = SQLDatabaseAccess()
    private val dbVersion = 1

    override fun beginBlock(ctx: EContext): InitialBlockData {
        if (ctx.chainID < 0) {
            throw UserMistake("ChainId must be >=0, got ${ctx.chainID}")
        }
        val prevHeight = getLastBlockHeight(ctx)
        val prevBlockRID: ByteArray?
        if (prevHeight == -1L) {
            // get the blockchainRId from db
            val blockchainRID = db.getBlockchainRID(ctx)
            if (blockchainRID == null) {
                throw UserMistake("Blockchain RID not found for chainId ${ctx.chainID}")
            }
            prevBlockRID = blockchainRID
        } else {
            val prevBlockRIDs = getBlockRIDs(ctx, prevHeight)
            if (prevBlockRIDs.isEmpty()) {
                throw ProgrammerMistake("Previous block had no RID. Check your block writing code!")
            }
            prevBlockRID = prevBlockRIDs[0]
        }

        val blockIid = db.insertBlock(ctx, prevHeight + 1)
        val blockData = InitialBlockData(blockIid, ctx.chainID, prevBlockRID, prevHeight + 1)
        return blockData
    }

    override fun addTransaction(bctx: BlockEContext, tx: Transaction): TxEContext {
        val txIid = db.insertTransaction(bctx, tx)
        return TxEContext(bctx.conn, bctx.chainID, bctx.nodeID, bctx.blockIID, txIid)
    }

    override fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader) {
        db.finalizeBlock(bctx, bh)
    }


    override fun commitBlock(bctx: BlockEContext, w: BlockWitness?) {
        if (w == null) return
        db.commitBlock(bctx, w)
    }

    override fun getBlockHeight(ctx: EContext, blockRID: ByteArray): Long? {
        return db.getBlockHeight(ctx, blockRID)
    }

    override fun getBlockRIDs(ctx: EContext, height: Long): List<ByteArray> {
        return db.getBlockRIDs(ctx, height)
    }

    override fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray {
        return db.getBlockHeader(ctx, blockRID)
    }

    // This implementation does not actually *stream* data from the database connection.
    // It is buffered in an ArrayList by ArrayListHandler() which is unfortunate.
    // Eventually, we may change this implementation to actually deliver a true
    // stream so that we don't have to store all transaction data in memory.
    override fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): Stream<ByteArray> {
        return db.getBlockTransactions(ctx, blockRID)
    }

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        return db.getWitnessData(ctx, blockRID)
    }

    override fun getLastBlockHeight(ctx: EContext): Long {
        return db.getLastBlockHeight(ctx)
    }

    override fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray> {
        return db.getTxRIDsAtHeight(ctx, height)
    }

    override fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): Map<String, Any> {
        val block = db.getBlockInfo(ctx, txRID)

        val txs = db.getBlockTransactions(ctx, block.blockIid)
        return mapOf<String, Any>("header" to block.blockHeader,
                "witness" to block.witness,
                "txs" to txs)
    }

    override fun getTxBytes(ctx: EContext, rid: ByteArray): ByteArray? {
        return db.getTxBytes(ctx, rid)
    }

    override fun getTxStatus(ctx: EContext, txHash: ByteArray): TransactionStatus? {
        return db.getTxStatus(ctx, txHash)
    }

    fun initialize(ctx: EContext, blockchainRID: ByteArray) {
        db.initialize(ctx, blockchainRID, this.dbVersion)
    }
}
