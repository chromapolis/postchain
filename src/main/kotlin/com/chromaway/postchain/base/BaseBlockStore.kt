package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockEContext
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.InitialBlockData
import com.chromaway.postchain.core.ProgrammerError
import com.chromaway.postchain.core.Signature
import org.apache.commons.dbutils.BaseResultSetHandler
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.ResultSetHandler
import org.apache.commons.dbutils.handlers.BeanListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import java.sql.ResultSet

class BaseBlockStore : BlockStore {
    val r = QueryRunner()
    val intRes = ScalarHandler<Integer>();
    val longRes = ScalarHandler<Long>();
    val signatureRes = BeanListHandler<Signature>(Signature::class.java);
    val byteArrayRes = ScalarHandler<ByteArray?>();

    override fun commitBlock(bctx: BlockEContext, w: BlockWitness?) {
        TODO("not implemented")
    }

    override fun getBlockHeight(ctx: EContext, blockRID: ByteArray): Long? {
        TODO("not implemented")
    }

    override fun getBlockRID(ctx: EContext, height: Long): ByteArray? {
        return r.query(ctx.conn,
                "SELECT block_rid FROM blocks WHERE chain_id = ? AND block_height = ?",
                byteArrayRes, ctx.chainID, height)
    }

    override fun getBlockData(ctx: EContext, height: Long): BlockData {
        TODO("not implemented")
    }

    override fun getWitnessData(ctx: EContext, height: Long): ByteArray {
        TODO("not implemented")
    }

    override fun getLastBlockHeight(ctx: EContext): Long {
        return r.query(ctx.conn,
                "SELECT block_height FROM blocks WHERE chain_id = ? ORDER BY block_height DESC LIMIT 1",
                longRes, ctx.chainID) ?: -1L;
    }

    override fun beginBlock(ctx: EContext): InitialBlockData {
        val prevHeight = getLastBlockHeight(ctx)
        val prevBlockRID : ByteArray?;
        if (prevHeight == -1L) {
            prevBlockRID = kotlin.ByteArray(32);
        } else {
            prevBlockRID = getBlockRID(ctx, prevHeight);
            if (prevBlockRID == null) {
                throw ProgrammerError("Previous block had no RID. Check your block writing code!");
            }
        }

        val blockIid = r.query(ctx.conn,
                "INSERT INTO blocks (chain_id, block_height) VALUES (?, ?) RETURNING block_iid",
                longRes, ctx.chainID, prevHeight+1)
        val blockData = InitialBlockData(blockIid, prevBlockRID, prevHeight+1)
        return blockData;
    }

    override fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader) {
        r.update(bctx.conn,
                "UPDATE blocks SET block_rid = ?, block_header_data = ? WHERE chain_id = ? AND block_iid = ?",
                bh.blockRID, bh.rawData, bctx.chainID, bctx.blockIID
        )
    }

    fun addSignature(bctx: BlockEContext, s: Signature) {
        TODO("FIX");
//        r.update(bctx.conn,
//                "INSERT INTO block_signaturs (block_iid, signature_pos, public_key, signature) VALUES (?, ?, ?, ?)",
//                bctx.blockIID, 0, s.pubKey, s.rawSignature
//        )
    }

    fun getSignatures(bctx: BlockEContext): Array<Signature> {
        return r.query(bctx.conn,
                "SELECT public_key, signature FROM block_signatures WHERE block_iid = ?",
                signatureRes, bctx.blockIID).toTypedArray()
    }


    fun initialize(ctx: EContext) {
        r.update(ctx.conn,
                "CREATE TABLE IF NOT EXISTS blocks" +
                        " (block_iid BIGSERIAL PRIMARY KEY," +
                        "  block_height BIGINT NOT NULL, " +
                        "  block_rid BYTEA," +
                        "  chain_id BIGINT NOT NULL," +
                        "  block_header_data BYTEA," +
                        "  UNIQUE (chain_id, block_rid)," +
                        "  UNIQUE (chain_id, block_height))")
    }
}
