package net.postchain.modules.certificate

import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.core.UserMistake
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler

class CertificateEntry(val id: String, val name: String, val pubkey: ByteArray,
                       val expires: Long, val authority: ByteArray, val reason: ByteArray)

fun getCertificatesQ(config: CertificateConfig, ctx: EContext, args: GTXValue): GTXValue {
    val id = args["id"]?.asString()?.hexStringToByteArray()
    if (id == null) throw UserMistake("Missing id")

    val authority = args["authority"]?.asByteArray()

    val r = QueryRunner()
    val mapListHandler = MapListHandler()
    val result: MutableList<MutableMap<String,Any>>
    val now = System.currentTimeMillis()

    if (authority != null) {
        result = r.query(ctx.conn,
                "SELECT * FROM certificate WHERE id = ? and expires > ? and authority = ?", mapListHandler,
                id, now, authority)
    } else {
        result = r.query(ctx.conn,
                "SELECT * FROM certificate WHERE id = ? and expires > ?", mapListHandler,
                id, now) }
    val list = MutableList<CertificateEntry>(result.size,{ index ->
        CertificateEntry(
                result[index]["id"] as String,
                result[index]["name"] as String,
                result[index]["pubkey"] as ByteArray,
                result[index]["expires"] as Long,
                result[index]["authority"] as ByteArray,
                result[index]["reason"] as ByteArray
        )
    }).toList()

    val ret = list.map {
        gtx("id" to gtx(it.id),
                "name" to gtx(it.name),
                "pubkey" to gtx(it.pubkey),
                "expires" to gtx(it.expires),
                "authority" to gtx(it.authority),
                "reason" to gtx(it.reason))
    }.toTypedArray()
    return gtx(*ret)
}
