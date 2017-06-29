import com.chromaway.postchain.core.*
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.BeanListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import java.sql.Connection

class BaseBlockStore : BlockStore {
    val r = QueryRunner()
    val intRes = ScalarHandler<Long>()
    val signatureRes = BeanListHandler<Signature>(Signature::class.java);

    fun getLastBlockHeight(conn: Connection): Long {
        return r.query(conn,
                "SELECT block_height FROM blocks WHERE chain_id = $1 ORDER BY block_height DESC LIMIT 1",
                intRes)
    }

    override fun beginBlock(ctx: EContext): Long {
        val blockHeight = getLastBlockHeight(ctx.conn)
        return r.query(ctx.conn,
                "INSERT INTO blocks (chain_id, block_height) VALUES (?, ?) RETURNING block_iid",
                intRes, ctx.chainID, blockHeight)
    }

    override fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader) {
        r.update(bctx.conn,
                "UPDATE blocks SET block_rid = ?, block_header_data = ? WHERE chain_id = ? AND block_iid = ?",
                bh.blockRID, bh.rawData, bctx.chainID, bctx.blockIID
        )
    }
    override fun addSignature(bctx: BlockEContext, s: Signature) {
        r.update(bctx.conn,
                "INSERT INTO block_signaturs (block_iid, signature_pos, public_key, signature) VALUES (?, ?, ?, ?)",
                bctx.blockIID, 0, s.pubKey, s.rawSignature
        )
    }
    override fun getSignatures(bctx: BlockEContext): Array<Signature> {
        return r.query(bctx.conn,
                "SELECT public_key, signature FROM block_signatures WHERE block_iid = ?",
                signatureRes, bctx.blockIID).toTypedArray()
    }

}