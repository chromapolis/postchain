package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.*
import com.chromaway.postchain.parseInt
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.ResultSetHandler
import org.apache.commons.dbutils.ResultSetIterator
import org.apache.commons.dbutils.handlers.*
import java.sql.Connection
import java.sql.ResultSet
import java.util.stream.Stream

class BaseBlockStore : BlockStore {
    private val r = QueryRunner()
    private val intRes = ScalarHandler<Int>()
    private val longRes = ScalarHandler<Long>()
    private val signatureRes = BeanListHandler<Signature>(Signature::class.java)
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val nullableLongRes = ScalarHandler<Long?>()
    private val byteArrayRes = ScalarHandler<ByteArray>()
    private val blockDataRes = BeanHandler<BlockData>(BlockData::class.java)
    private val byteArrayListRes = ColumnListHandler<ByteArray>()
    private val mapListHandler = MapListHandler()
    private val stringRes = ScalarHandler<String>()
    private val dbVersion = 1

    override fun beginBlock(ctx: EContext): InitialBlockData {
        val prevHeight = getLastBlockHeight(ctx)
        val prevBlockRID: ByteArray?
        if (prevHeight == -1L) {
            prevBlockRID = kotlin.ByteArray(32)
        } else {
            val prevBlockRIDs = getBlockRIDs(ctx, prevHeight)
            if (prevBlockRIDs.isEmpty()) {
                throw ProgrammerMistake("Previous block had no RID. Check your block writing code!")
            }
            prevBlockRID = prevBlockRIDs[0]
        }

        val blockIid = r.insert(ctx.conn,
                "INSERT INTO blocks (chain_id, block_height) VALUES (?, ?) RETURNING block_iid",
                longRes, ctx.chainID, prevHeight + 1)
        val blockData = InitialBlockData(blockIid, prevBlockRID, prevHeight + 1)
        return blockData
    }

    override fun addTransaction(bctx: BlockEContext, tx: Transaction): TxEContext {
        val txIid = r.insert(bctx.conn,
                "INSERT INTO transactions (chain_id, tx_rid, tx_data, block_iid)" +
                        "VALUES (?, ?, ?, ?) RETURNING tx_iid",
                longRes,
                bctx.chainID, tx.getRID(), tx.getRawData(), bctx.blockIID)
        return TxEContext(bctx.conn, bctx.chainID, bctx.nodeID, bctx.blockIID, txIid)
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

    override fun getBlockRIDs(ctx: EContext, height: Long): List<ByteArray> {
        return r.query(ctx.conn,
                "SELECT block_rid FROM blocks WHERE chain_id = ? AND block_height = ?",
                byteArrayListRes, ctx.chainID, height)
    }

//    override fun getBlockData(ctx: EContext, blockRID: ByteArray): BlockData {
//
//
//
//        val map = r.query(ctx.conn,
//                "SELECT block_iid, block_rid, block_header FROM blocks WHERE chain_id = ? AND block_height = ? ",
//                MapHandler(), ctx.chainID, height)
//        if (map == null) {
//            throw ProgrammerMistake("no block data at height ${height}")
//        }
//        val blockIid = map.get("block_iid") as Long
//        val transactions = r.query(ctx.conn,
//                "SELECT tx_data FROM transactions where block_iid=?",
//                byteArrayListRes, blockIid)
//        val result = BlockData(
//        TODO("Implement")
//    }

    override fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray {
        return r.query(ctx.conn, "SELECT block_header_data FROM blocks where chain_id = ? and block_rid = ?",
                byteArrayRes, ctx.chainID, blockRID)
    }

    // This implementation does not actually *stream* data from the database connection.
    // It is buffered in an ArrayList by ArrayListHandler() which is unfortunate.
    // Eventually, we may change this implementation to actually deliver a true
    // stream so that we don't have to store all transaction data in memory.
    override fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): Stream<ByteArray> {
        val sql = """
            SELECT tx_data
            FROM transactions t
            JOIN blocks b ON t.block_iid=b.block_iid
            WHERE b.block_rid=? AND b.chain_id=?
            ORDER BY tx_iid"""
        return r.query(ctx.conn, sql, ArrayListHandler(), blockRID, ctx.chainID).
                stream().map { array -> array[0] as ByteArray}
    }

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        return r.query(ctx.conn,
                "SELECT block_witness FROM blocks WHERE chain_id = ? AND block_rid = ?",
                byteArrayRes, ctx.chainID, blockRID)
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

    override fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): Map<String, Any> {
        val block = r.query(ctx.conn,
                """
                    SELECT b.block_iid, b.block_header_data, b.block_witness
                    FROM blocks b JOIN transactions t ON b.block_iid=t.block_iid
                    WHERE b.chain_id=? and t.tx_rid=?
                    """, mapListHandler, ctx.chainID, txRID)!!
        if (block.size < 1) throw UserMistake("Can't get confirmation proof for nonexistent tx")
        if (block.size > 1) throw ProgrammerMistake("Expected at most one hit")
        val blockIid = block[0].get("block_iid") as Long
        val blockHeader = block[0].get("block_header_data") as ByteArray
        val witness = block[0].get("block_witness") as ByteArray

        val txs = r.query(ctx.conn,
                "SELECT tx_rid FROM " +
                        "transactions t " +
                        "where t.block_iid=? order by tx_iid",
                ColumnListHandler<ByteArray>(), blockIid)!!
        return mapOf<String, Any>("header" to blockHeader,
                "witness" to witness,
                "txs" to txs)
    }

    override fun getTxBytes(ctx: EContext, rid: ByteArray): ByteArray? {
        return r.query(ctx.conn, "SELECT tx_data FROM " +
                "transactions WHERE chain_id=? AND tx_rid=?",
                nullableByteArrayRes, ctx.chainID, rid)
    }

    override fun getTxStatus(ctx: EContext, txHash: ByteArray): TransactionStatus? {
        try {
            // Note that PostgreSQL does not support READ UNCOMMITTED, so setting
            // it is useless. It will be run as READ COMMITTED.
            // I leave this here to mark my intention.
            // Currently, due to the above, transactions will look like they are unknown if they are in the
            // middle of block-building.
            ctx.conn.transactionIsolation = Connection.TRANSACTION_READ_UNCOMMITTED
            val list = r.query(ctx.conn,
                    """
                        SELECT tx_iid, block_witness
                        FROM transactions t JOIN blocks b ON t.block_iid=b.block_iid
                        WHERE b.chain_id=? AND t.tx_rid=?
                        """, mapListHandler, ctx.chainID, txHash)
            if (list.isEmpty()) return null
            if (list.size != 1) throw ProgrammerMistake("Expected at most one result for ${txHash.toHex()}")
            if (list[0].get("block_witness") == null) return TransactionStatus.WAITING
            return TransactionStatus.CONFIRMED
        } finally {
            ctx.conn.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        }
    }

    fun initialize(ctx: EContext) {
        // "CREATE TABLE IF NOT EXISTS" is not good enough for the meta table
        // We need to know whether it exists or not in order to
        // make desicions on upgrade
        val checkExists = """
            SELECT 1
            FROM   pg_catalog.pg_class c
            JOIN   pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE  n.nspname = ANY(current_schemas(FALSE))
                    AND    n.nspname NOT LIKE 'pg_%'
                    AND    c.relname = 'meta'
                    AND    c.relkind = 'r'
        """

        val exists = r.query(ctx.conn, checkExists, ColumnListHandler<Int>())
        if (exists.size == 1) {
            // meta table already exists. Check the version
            val versionString = r.query(ctx.conn, "SELECT value FROM meta WHERE key='version'", ScalarHandler<String>())
            val version = versionString.toInt()
            if (version != this.dbVersion) {
                throw UserMistake("Unexpected version '$version' in database. Expected '$dbVersion'")
            }
            return
        }
        // meta table does not exist! Assume database does not exist.

        r.update(ctx.conn, """
            CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)
            """)
        r.update(ctx.conn, "INSERT INTO meta (key, value) values ('version', ?)", dbVersion)

        // Don't use "CREATE TABLE IF NOT EXISTS" because if they do exist
        // we must throw an error. If these tables exists but meta did not exist,
        // there is some serious problem that needs manual work
        r.update(ctx.conn,
                "CREATE TABLE blocks" +
                        " (block_iid BIGSERIAL PRIMARY KEY," +
                        "  block_height BIGINT NOT NULL, " +
                        "  block_rid BYTEA," +
                        "  chain_id BIGINT NOT NULL," +
                        "  block_header_data BYTEA," +
                        "  block_witness BYTEA," +
                        "  UNIQUE (chain_id, block_rid)," +
                        "  UNIQUE (chain_id, block_height))")

        val createTxTable = "CREATE TABLE transactions (" +
                "    tx_iid BIGSERIAL PRIMARY KEY, " +
                "    chain_id bigint NOT NULL," +
                "    tx_rid bytea NOT NULL," +
                "    tx_data bytea NOT NULL," +
                "    block_iid bigint NOT NULL REFERENCES blocks(block_iid)," +
                "    UNIQUE (chain_id, tx_rid))"
        r.update(ctx.conn, createTxTable)
    }

    class IterableByteArrays(val columnName: String) : ResultSetHandler<Iterator<ByteArray>> {
        override fun handle(rs: ResultSet?): Iterator<ByteArray> {
            return object : Iterator<ByteArray> {
                override fun hasNext(): Boolean {
                    if (rs == null) {
                        throw ProgrammerMistake("ResultSet was null")
                    }
                    return !rs.isLast()
                }

                override fun next(): ByteArray {
                    if (rs == null) {
                        throw ProgrammerMistake("ResultSet was null")
                    }
                    if (!rs.next()) {
                        throw NoSuchElementException("No more ByteArrays")
                    }
                    return rs.getBytes(columnName)
                }
            }
        }
    }
}
