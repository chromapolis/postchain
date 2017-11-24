package net.postchain.modules.esplix;

import net.postchain.core.EContext
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

open class BaseDBOps: EsplixDBOps {

    private val r = QueryRunner()
    private val mapListHandler = MapListHandler()
    private val longHandler = ScalarHandler<Long>()
    private val unitHandler = ScalarHandler<Unit>()

    override fun postMessage(ctx: EContext, txiid: Long, messageID: ByteArray, prevID: ByteArray,
                             callIndex: Int, payload: ByteArray) {

        r.query(ctx.conn, "SELECT mcs_r2_postMessage(?, ?, ?, ?, ?)", unitHandler,
                txiid, callIndex, messageID, prevID, payload)
    }

    override fun certificate(ctx: EContext, signers: Array<ByteArray>, id: String, name: String, pubkey: ByteArray,
                             expires: Long, authority: ByteArray, reason: ByteArray) {
        r.query(ctx.conn, "INSERT INTO certificate (id, name, pubkey, expires, authority, reason) values" +
                "(?, ?, ?, ?, ?, ?)", unitHandler, id, name, pubkey, expires, authority, reason)

    }

    override fun createChain(ctx: EContext, chainID: ByteArray, nonce: ByteArray, txiid: Long, callIndex: Int, payload: ByteArray): Long {
        val res = r.query(ctx.conn, "SELECT mcs_r2_createChain (?, ?, ?, ?, ?)", longHandler,
                nonce, chainID, txiid, callIndex, payload)
        return res
    }

    override fun getCertificates(ctx: EContext, id: ByteArray, authority: ByteArray?): List<CertificateEntry> {
        val now = System.currentTimeMillis()
        val result: MutableList<MutableMap<String,Any>>
        if (authority != null) {
            result = r.query(ctx.conn,
                    "SELECT * FROM certificate WHERE id = ? and expires > ? and authority = ?", mapListHandler,
                    id, now, authority)
        } else {
            result = r.query(ctx.conn,
                    "SELECT * FROM certificate WHERE id = ? and expires > ?", mapListHandler,
                    id, now)

        }
        val ret = MutableList<CertificateEntry>(result.size,{ index ->
            CertificateEntry(
                result[index]["id"] as String,
                result[index]["name"] as String,
                result[index]["pubkey"] as ByteArray,
                result[index]["expires"] as Long,
                result[index]["authority"] as ByteArray,
                result[index]["reason"] as ByteArray
                )


            })
        return ret.toList()
    }

    override fun getMessages(ctx: EContext, chainID: ByteArray, sinceMessageID: ByteArray?, maxHits: Long): List<MessageEntry> {
        val res = r.query(ctx.conn, "SELECT * FROM findRatatoskMessages(?, ?, ?)", mapListHandler,
                chainID,
                sinceMessageID,
                maxHits)
        return List<MessageEntry>(res.size, { index ->
            MessageEntry(
                    res[index]["gtx"] as ByteArray,
                    res[index]["gtx_id"] as ByteArray,
                    res[index]["call_index"] as Array<Int>

            )
        })
    }


}
