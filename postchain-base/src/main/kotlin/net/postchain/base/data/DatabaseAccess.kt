// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import net.postchain.base.BaseBlockHeader
import net.postchain.base.toHex
import net.postchain.core.*
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.*
import java.util.stream.Stream

interface DatabaseAccess {
    class BlockInfo(val blockIid: Long, val blockHeader: ByteArray, val witness: ByteArray)
    fun getBlockchainRID(ctx: EContext): ByteArray?
    fun insertBlock(ctx: EContext, height: Long): Long
    fun insertTransaction(ctx: BlockEContext, tx: Transaction): Long
    fun finalizeBlock(ctx: BlockEContext, header: BlockHeader)

    fun commitBlock(bctx: BlockEContext, w: BlockWitness)
    fun getBlockHeight(ctx: EContext, blockRID: ByteArray): Long?
    fun getBlockRIDs(ctx: EContext, height: Long): List<ByteArray>
    fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): List<ByteArray>
    fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getLastBlockHeight(ctx: EContext): Long
    fun getLastBlockTimestamp(ctx: EContext): Long
    fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray>
    fun getBlockInfo(ctx: EContext, txRID: ByteArray): BlockInfo
    fun getTxHash(ctx: EContext, txRID: ByteArray): ByteArray
    fun getBlockTxRIDs(ctx: EContext, blockIid: Long): List<ByteArray>
    fun getBlockTxHashes(ctx: EContext, blokcIid: Long): List<ByteArray>
    fun getTxBytes(ctx: EContext, rid: ByteArray): ByteArray?
    fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean
    fun initialize(ctx: EContext, blockchainRID: ByteArray, expectedDbVersion: Int)
}

class SQLDatabaseAccess(): DatabaseAccess {
    var r = QueryRunner()
    private val intRes = ScalarHandler<Int>()
    private val longRes = ScalarHandler<Long>()
    private val signatureRes = BeanListHandler<Signature>(Signature::class.java)
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val nullableIntRes = ScalarHandler<Int?>()
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
                "INSERT INTO transactions (chain_id, tx_rid, tx_data, tx_hash, block_iid)" +
                        "VALUES (?, ?, ?, ?, ?) RETURNING tx_iid",
                longRes,
                ctx.chainID, tx.getRID(), tx.getRawData(), tx.getHash(), ctx.blockIID)
    }

    override fun finalizeBlock(ctx: BlockEContext, header: BlockHeader) {
        r.update(ctx.conn,
                "UPDATE blocks SET block_rid = ?, block_header_data = ?, timestamp = ? WHERE chain_id = ? AND block_iid = ?",
                header.blockRID, header.rawData, (header as BaseBlockHeader).timestamp, ctx.chainID, ctx.blockIID
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

    override fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): List<ByteArray> {
        val sql = """
            SELECT tx_data
            FROM transactions t
            JOIN blocks b ON t.block_iid=b.block_iid
            WHERE b.block_rid=? AND b.chain_id=?
            ORDER BY tx_iid"""
        return r.query(ctx.conn, sql, byteArrayListRes, blockRID, ctx.chainID)
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

    override fun getLastBlockTimestamp(ctx: EContext): Long {
        return r.query(ctx.conn,
                "SELECT timestamp FROM blocks WHERE chain_id = ? ORDER BY timestamp DESC LIMIT 1",
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

    override fun getBlockInfo(ctx: EContext, txRID: ByteArray): DatabaseAccess.BlockInfo {
        val block = r.query(ctx.conn,
                """
                    SELECT b.block_iid, b.block_header_data, b.block_witness
                    FROM blocks b JOIN transactions t ON b.block_iid=t.block_iid
                    WHERE t.chain_id=? and t.tx_rid=?
                    """, mapListHandler, ctx.chainID, txRID)!!
        if (block.size < 1) throw UserMistake("Can't get confirmation proof for nonexistent tx")
        if (block.size > 1) throw ProgrammerMistake("Expected at most one hit")
        val blockIid = block[0].get("block_iid") as Long
        val blockHeader = block[0].get("block_header_data") as ByteArray
        val witness = block[0].get("block_witness") as ByteArray
        return DatabaseAccess.BlockInfo(blockIid, blockHeader, witness)
    }

    override fun getTxHash(ctx: EContext, txRID: ByteArray): ByteArray {
        return r.query(ctx.conn,
                "SELECT tx_hash FROM transactions WHERE tx_rid = ? and chain_id =?",
                byteArrayRes, txRID, ctx.chainID)
    }

    override fun getBlockTxRIDs(ctx: EContext, blockIid: Long): List<ByteArray> {
        return r.query(ctx.conn,
                "SELECT tx_rid FROM " +
                        "transactions t " +
                        "where t.block_iid=? order by tx_iid",
                ColumnListHandler<ByteArray>(), blockIid)!!
    }

    override fun getBlockTxHashes(ctx: EContext, blockIid: Long): List<ByteArray> {
        return r.query(ctx.conn,
                "SELECT tx_hash FROM " +
                        "transactions t " +
                        "where t.block_iid=? order by tx_iid",
                ColumnListHandler<ByteArray>(), blockIid)!!
    }


    override fun getTxBytes(ctx: EContext, rid: ByteArray): ByteArray? {
        return r.query(ctx.conn, "SELECT tx_data FROM " +
                "transactions WHERE chain_id=? AND tx_rid=?",
                nullableByteArrayRes, ctx.chainID, rid)
    }

    override fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean {
        val res = r.query(ctx.conn,
                """
                        SELECT 1 FROM transactions t
                        WHERE t.chain_id=? AND t.tx_rid=?
                        """, nullableIntRes, ctx.chainID, txRID)
        return (res != null)
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
                        "  timestamp BIGINT," +
                        "  UNIQUE (chain_id, block_rid)," +
                        "  UNIQUE (chain_id, block_height))")

        val createTxTable = "CREATE TABLE transactions (" +
                "    tx_iid BIGSERIAL PRIMARY KEY, " +
                "    chain_id bigint NOT NULL," +
                "    tx_rid bytea NOT NULL," +
                "    tx_data bytea NOT NULL," +
                "    tx_hash bytea NOT NULL," +
                "    block_iid bigint NOT NULL REFERENCES blocks(block_iid)," +
                "    UNIQUE (chain_id, tx_rid))"

        r.update(ctx.conn, createTxTable)

        val createTransactionsBlockIidIndex = """CREATE INDEX transactions_block_iid_idx ON transactions(block_iid)"""

        // This is for eg getBlockTransactions()
        r.update(ctx.conn, createTransactionsBlockIidIndex)
    }
}
