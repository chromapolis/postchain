package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockEContext
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.ProgrammerMistake
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TransactionStatus
import com.chromaway.postchain.core.UserMistake
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ArrayListHandler
import org.apache.commons.dbutils.handlers.BeanHandler
import org.apache.commons.dbutils.handlers.BeanListHandler
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import java.sql.Connection
import java.util.stream.Stream

interface DatabaseAccess {
    fun getBlockchainRID(ctx: EContext): ByteArray?
    fun insertBlock(ctx: EContext, height: Long): Long
    fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long
    fun finalizeBlock(ctx: BlockEContext, header: BlockHeader)

    fun commitBlock(bctx: BlockEContext, w: BlockWitness)
    fun getBlockHeight(ctx: EContext, blockRID: ByteArray): Long?
    fun getBlockRIDs(ctx: EContext, height: Long): List<ByteArray>
    fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): Stream<ByteArray>
    fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getLastBlockHeight(ctx: EContext): Long
    fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray>
    fun getBlockInfo(ctx: EContext, txRID: ByteArray): SQLDatabaseAccess.BlockInfo
    fun getBlockTransactions(ctx: EContext, blockIid: Long): List<ByteArray>
    fun getTxBytes(ctx: EContext, rid: ByteArray): ByteArray?
    fun getTxStatus(ctx: EContext, txHash: ByteArray): TransactionStatus?
    fun initialize(ctx: EContext, blockchainRID: ByteArray, expectedDbVersion: Int)
}

class SQLDatabaseAccess(): DatabaseAccess {
    var r = QueryRunner()
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

    override fun insertBlock(ctx: EContext, height: Long): Long {
        return r.insert(ctx.conn,
                "INSERT INTO blocks (chain_id, block_height) VALUES (?, ?) RETURNING block_iid",
                longRes, ctx.chainID, height)
    }

    override fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long {
        return r.insert(ctx.conn,
                "INSERT INTO transactions (chain_id, tx_rid, tx_data, block_iid)" +
                        "VALUES (?, ?, ?, ?) RETURNING tx_iid",
                longRes,
                ctx.chainID, tx.getRID(), tx.getRawData(), ctx.blockIID)
    }

    override fun finalizeBlock(ctx: BlockEContext, header: BlockHeader) {
        r.update(ctx.conn,
                "UPDATE blocks SET block_rid = ?, block_header_data = ? WHERE chain_id = ? AND block_iid = ?",
                header.blockRID, header.rawData, ctx.chainID, ctx.blockIID
        )
    }

    override fun commitBlock(bctx: BlockEContext, w: BlockWitness) {
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

    class BlockInfo(val blockIid: Long, val blockHeader: ByteArray, val witness: ByteArray)
    override fun getBlockInfo(ctx: EContext, txRID: ByteArray): BlockInfo {
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
        return BlockInfo(blockIid, blockHeader, witness)
    }

    override fun getBlockTransactions(ctx: EContext, blockIid: Long): List<ByteArray> {
        return r.query(ctx.conn,
                "SELECT tx_rid FROM " +
                        "transactions t " +
                        "where t.block_iid=? order by tx_iid",
                ColumnListHandler<ByteArray>(), blockIid)!!
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

    override fun getBlockchainRID(ctx: EContext): ByteArray? {
        return r.query(ctx.conn, "SELECT blockchain_rid FROM blockchains WHERE chain_id = ?",
                nullableByteArrayRes, ctx.chainID)
    }

    override fun initialize(ctx: EContext, blockchainRID: ByteArray, expectedDbVersion: Int) {
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

        val metaExists = r.query(ctx.conn, checkExists, ColumnListHandler<Int>())
        if (metaExists.size == 1) {
            // meta table already exists. Check the version
            val versionString = r.query(ctx.conn, "SELECT value FROM meta WHERE key='version'", ScalarHandler<String>())
            val version = versionString.toInt()
            if (version != expectedDbVersion) {
                throw UserMistake("Unexpected version '$version' in database. Expected '$expectedDbVersion'")
            }

            // Check that the blockchainRID is present for block_id
            val dbRid = r.query(ctx.conn, "SELECT blockchain_rid from blockchains where chain_id=?",
                    nullableByteArrayRes, ctx.chainID)
            if (dbRid == null) {
                r.insert(ctx.conn, "INSERT INTO blockchains (chain_id, blockchain_rid) values (?, ?)",
                        ScalarHandler<Unit>(), ctx.chainID, blockchainRID)
            } else if (!dbRid.contentEquals(blockchainRID)) {
                throw UserMistake("The blockchainRID in db for chainId ${ctx.chainID} " +
                        "is ${dbRid.toHex()}, but the expected rid is ${blockchainRID.toHex()}")
            }
            return
        }

        // meta table does not exist! Assume database does not exist.

        r.update(ctx.conn, """
            CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)
            """)
        r.update(ctx.conn, "INSERT INTO meta (key, value) values ('version', ?)", expectedDbVersion)

        // Don't use "CREATE TABLE IF NOT EXISTS" because if they do exist
        // we must throw an error. If these tables exists but meta did not exist,
        // there is some serious problem that needs manual work
        r.update(ctx.conn, "CREATE TABLE blockchains " +
                "(chain_id BIGINT PRIMARY KEY, blockchain_rid BYTEA NOT NULL)")
        r.insert(ctx.conn, "INSERT INTO blockchains (chain_id, blockchain_rid) values (?, ?)",
                ScalarHandler<Unit>(), ctx.chainID, blockchainRID)

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
}
