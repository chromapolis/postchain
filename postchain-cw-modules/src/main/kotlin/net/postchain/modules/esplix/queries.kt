package net.postchain.modules.esplix

import net.postchain.base.hexStringToByteArray
import net.postchain.base.toHex
import net.postchain.core.EContext
import net.postchain.core.UserMistake
import net.postchain.gtx.GTXNull
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx
import org.apache.commons.dbutils.QueryRunner
import java.nio.ByteBuffer

fun getNonceQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    return gtx(config.cryptoSystem.getRandomBytes(32).toHex())
}

fun getCertificatesQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    val id = args["id"]?.asString()?.hexStringToByteArray()
    if (id == null) throw UserMistake("Missing id")
    val authority = args["authority"]?.asByteArray()
    val certs = config.dbOps.getCertificates(ctx, id, authority)
    val result = certs.map {
        gtx("id" to gtx(it.id),
                "name" to gtx(it.name),
                "pubkey" to gtx(it.pubkey),
                "expires" to gtx(it.expires),
                "authority" to gtx(it.authority),
                "reason" to gtx(it.reason))
    }.toTypedArray()
    return gtx(*result)
}

fun getMessagesQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    val chainID = args["chainId"]?.asString()?.hexStringToByteArray()
    if (chainID == null)
        throw UserMistake("Invalid ChainID")

    val sinceMessageID = args["sinceMessageId"]?.asString()?.hexStringToByteArray()
    val maxHits = args["maxHits"]?.asInteger() ?: 100
    if (maxHits < 1 || maxHits > 1000) throw UserMistake("Invalid maxHits")
    val messages = config.dbOps.getMessages(ctx, chainID!!, sinceMessageID, maxHits)
    val result = messages.map {
        gtx("gtx" to gtx(it.gtx),
                "gtx_id" to gtx(it.gtx_id),
                "call_index" to gtx(*it.callIndex.map {
                    gtx(it as Long)
                }.toTypedArray()))
    }.toTypedArray()

    return gtx(*result)
}