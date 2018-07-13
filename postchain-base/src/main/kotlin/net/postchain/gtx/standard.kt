// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx

import net.postchain.base.data.SQLDatabaseAccess
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import org.apache.commons.dbutils.handlers.ScalarHandler

/**
 * nop operation can be useful as nonce or identifier which has no meaning on consensus level
 */
class GTX_nop(u: Unit, opData: ExtOpData) : GTXOperation(opData) {
    override fun apply(ctx: TxEContext): Boolean {
        return true
    }

    override fun isCorrect(): Boolean {
        return true;
    }
}

class GTX_timeb(u: Unit, opData: ExtOpData) : GTXOperation(opData) {
    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        val from = data.args[0].asInteger()
        if (!data.args[1].isNull()) {
            if (data.args[1].asInteger() < from) return false
        }
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        val from = data.args[0].asInteger()
        if (ctx.timestamp < from) return false
        if (!data.args[1].isNull()) {
            val until = data.args[1].asInteger()
            if (until < ctx.timestamp) return false
        }
        return true
    }

}

fun lastBlockInfoQuery(config: Unit, ctx: EContext, args: GTXValue): GTXValue {
    val dba = SQLDatabaseAccess()
    val prevHeight = dba.getLastBlockHeight(ctx)
    val prevTimestamp = dba.getLastBlockTimestamp(ctx)
    val prevBlockRID: ByteArray?
    if (prevHeight != -1L) {
        val prevBlockRIDs = dba.getBlockRIDs(ctx, prevHeight)
        prevBlockRID = prevBlockRIDs[0]
    } else
        prevBlockRID = null
    return gtx(
            "height" to gtx(prevHeight),
            "timestamp" to gtx(prevTimestamp),
            "blockRID" to (if (prevBlockRID != null) gtx(prevBlockRID) else GTXNull)
    )
}

fun txConfirmationTime(config: Unit, ctx: EContext, args: GTXValue): GTXValue {
    val dba = SQLDatabaseAccess()
    val txRID = args["txRID"]!!.asByteArray(true)
    val info = dba.getBlockInfo(ctx, txRID)
    val timestamp = dba.r.query(ctx.conn,"SELECT timestamp FROM blocks WHERE block_iid = ?",
            ScalarHandler<Long>(), info.blockIid)
    val blockRID = dba.r.query(ctx.conn,"SELECT block_rid FROM blocks WHERE block_iid = ?",
            ScalarHandler<ByteArray>(), info.blockIid)
    val blockHeight = dba.r.query(ctx.conn,"SELECT block_height FROM blocks WHERE block_iid = ?",
            ScalarHandler<Long>(), info.blockIid)
    return gtx(
            "timestamp" to gtx(timestamp),
            "blockRID" to gtx(blockRID),
            "blockHeight" to gtx(blockHeight)
    )
}


class StandardOpsGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(
        "nop" to ::GTX_nop,
        "timeb" to ::GTX_timeb
), mapOf(
        "last_block_info" to ::lastBlockInfoQuery,
        "tx_confirmation_time" to ::txConfirmationTime
        )
) {
    override fun initializeDB(ctx: EContext) {}
}