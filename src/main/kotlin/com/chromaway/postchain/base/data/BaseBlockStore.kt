package com.chromaway.postchain.base.data

import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockEContext
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.InitialBlockData
import com.chromaway.postchain.core.ProgrammerError
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.parseInt
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.BeanHandler
import org.apache.commons.dbutils.handlers.BeanListHandler
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.apache.commons.dbutils.handlers.MapHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

class BaseBlockStore : BlockStore {
    private val r = QueryRunner()
    val intRes = ScalarHandler<Int>()
    private val longRes = ScalarHandler<Long>()
    val signatureRes = BeanListHandler<Signature>(Signature::class.java)
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val nullableLongRes = ScalarHandler<Long?>()
    private val byteArrayRes = ScalarHandler<ByteArray>()
    private val blockDataRes = BeanHandler<BlockData>(BlockData::class.java)
    private val byteArrayListRes = ColumnListHandler<ByteArray>()

    override fun beginBlock(ctx: EContext): InitialBlockData {
        val prevHeight = getLastBlockHeight(ctx)
        val prevBlockRID : ByteArray?
        if (prevHeight == -1L) {
            prevBlockRID = kotlin.ByteArray(32)
        } else {
            prevBlockRID = getBlockRID(ctx, prevHeight)
            if (prevBlockRID == null) {
                throw ProgrammerError("Previous block had no RID. Check your block writing code!")
            }
        }

        val blockIid = r.insert(ctx.conn,
                "INSERT INTO blocks (chain_id, block_height) VALUES (?, ?) RETURNING block_iid",
                longRes, ctx.chainID, prevHeight+1)
        val blockData = InitialBlockData(blockIid, prevBlockRID, prevHeight+1)
        return blockData
    }

    override fun addTransaction(bctx: BlockEContext, tx: Transaction): TxEContext {
        val txIid = r.insert(bctx.conn,
                "INSERT INTO transactions (chain_id, tx_rid, tx_data, block_iid)" +
                        "VALUES (?, ?, ?, ?) RETURNING tx_iid",
                longRes,
                bctx.chainID, tx.getRID(), tx.getRawData(), bctx.blockIID)
        return TxEContext(bctx.conn, bctx.chainID, bctx.blockIID, txIid)
    }

    override fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader) {
        r.update(bctx.conn,
                "UPDATE blocks SET block_rid = ?, block_header_data = ? WHERE chain_id = ? AND block_iid = ?",
                bh.blockRID, bh.rawData, bctx.chainID, bctx.blockIID
        )
    }


    override fun commitBlock(bctx: BlockEContext, w: BlockWitness?) {
        if (w == null) return
        r.update(bctx.conn,
                "UPDATE blocks SET block_witness = ? WHERE block_iid=?",
                w.getRawData(), bctx.blockIID)
    }

    override fun getBlockHeight(ctx: EContext, blockRID: ByteArray): Long? {
        return r.query(ctx.conn, "SELECT block_height FROM blocks where chain_id = ? and block_rid = ?",
                nullableLongRes, ctx.chainID, blockRID)
    }

    override fun getBlockRID(ctx: EContext, height: Long): ByteArray? {
        return r.query(ctx.conn,
                "SELECT block_rid FROM blocks WHERE chain_id = ? AND block_height = ?",
                nullableByteArrayRes, ctx.chainID, height)
    }

    override fun getBlockData(ctx: EContext, height: Long): BlockData {
//        val map = r.query(ctx.conn,
//                "SELECT block_iid, block_rid, block_header FROM blocks WHERE chain_id = ? AND block_height = ? ",
//                MapHandler(), ctx.chainID, height)
//        if (map == null) {
//            throw ProgrammerError("no block data at height ${height}")
//        }
//        val blockIid = map.get("block_iid") as Long
//        val transactions = r.query(ctx.conn,
//                "SELECT tx_data FROM transactions where block_iid=?",
//                byteArrayListRes, blockIid)
//        val result = BlockData(
        TODO("Implement")
    }

    override fun getWitnessData(ctx: EContext, height: Long): ByteArray {
        return r.query(ctx.conn,
                "SELECT block_witness FROM blocks WHERE chain_id = ? AND block_height = ?",
                byteArrayRes, ctx.chainID, height)
    }

    override fun getLastBlockHeight(ctx: EContext): Long {
        return r.query(ctx.conn,
                "SELECT block_height FROM blocks WHERE chain_id = ? ORDER BY block_height DESC LIMIT 1",
                longRes, ctx.chainID) ?: -1L
    }

    override fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray> {
        return r.query(ctx.conn,
                "SELECT tx_rid FROM " +
                        "transactions t " +
                        "INNER JOIN blocks b ON t.block_iid=b.block_iid " +
                        "where b.block_height=? and b.chain_id=?",
                ColumnListHandler<ByteArray>(), height, ctx.chainID).toTypedArray()
    }

    override fun getTxBytes(ctx: EContext, rid: ByteArray): ByteArray {
        return r.query(ctx.conn, "SELECT tx_rid FROM " +
                "transactions WHERE chain_id=? AND tx_rid=?",
                byteArrayRes, ctx.chainID, rid)
    }

    fun initialize(ctx: EContext) {
        r.update(ctx.conn,
                "CREATE TABLE IF NOT EXISTS blocks" +
                        " (block_iid BIGSERIAL PRIMARY KEY," +
                        "  block_height BIGINT NOT NULL, " +
                        "  block_rid BYTEA," +
                        "  chain_id BIGINT NOT NULL," +
                        "  block_header_data BYTEA," +
                        "  block_witness BYTEA," +
                        "  UNIQUE (chain_id, block_rid)," +
                        "  UNIQUE (chain_id, block_height))")

        val createTxTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                "    tx_iid BIGSERIAL PRIMARY KEY, " +
                "    chain_id bigint NOT NULL," +
                "    tx_rid bytea NOT NULL," +
                "    tx_data bytea NOT NULL," +
                "    block_iid bigint NOT NULL REFERENCES blocks(block_iid)," +
                "    UNIQUE (chain_id, tx_rid))"
        r.update(ctx.conn, createTxTable)
    }
}
