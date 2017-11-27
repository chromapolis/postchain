package net.postchain.modules.esplix

import net.postchain.base.hexStringToByteArray
import net.postchain.base.toHex
import net.postchain.core.EContext
import net.postchain.core.UserMistake
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler

class MessageEntry(val gtx: ByteArray, val gtx_id: ByteArray, val callIndex: Array<Int>)

fun getNonceQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    return gtx(config.cryptoSystem.getRandomBytes(32).toHex())
}

fun getMessagesQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    val r = QueryRunner()
    val mapListHandler = MapListHandler()

    val chainID = args["chainId"]?.asString()?.hexStringToByteArray()
    if (chainID == null)
        throw UserMistake("Invalid ChainID")

    val sinceMessageID = args["sinceMessageId"]?.asString()?.hexStringToByteArray()
    val maxHits = args["maxHits"]?.asInteger() ?: 100
    if (maxHits < 1 || maxHits > 1000) throw UserMistake("Invalid maxHits")

    fun getMessages(ctx: EContext, chainID: ByteArray, sinceMessageID: ByteArray?, maxHits: Long): List<MessageEntry> {
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

    val messages = getMessages(ctx, chainID!!, sinceMessageID, maxHits)
    val result = messages.map {
        gtx("gtx" to gtx(it.gtx),
                "gtx_id" to gtx(it.gtx_id),
                "call_index" to gtx(*it.callIndex.map {
                    gtx(it as Long)
                }.toTypedArray()))
    }.toTypedArray()

    return gtx(*result)
}