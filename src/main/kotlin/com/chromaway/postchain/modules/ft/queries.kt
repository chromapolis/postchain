package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.gtx.GTXNull
import com.chromaway.postchain.gtx.GTXValue
import com.chromaway.postchain.gtx.gtx

fun ftAccountExistsQ(config: FTConfig, ctx: EContext, args: GTXValue): GTXValue {
    val accountID = args["account_id"]!!.asByteArray(true)
    return gtx(
            if (config.dbOps.getDescriptor(ctx, accountID) != null) 1L else 0L
    )
}

fun ftBalanceQ(config: FTConfig, ctx: EContext, args: GTXValue): GTXValue {
    val accountID = args["account_id"]!!.asByteArray(true)
    val assetID = args["asset_id"]!!.asString()
    return gtx(
            "balance" to
                    gtx(config.dbOps.getBalance(ctx, accountID, assetID)))
}

fun ftHistoryQ(config: FTConfig, ctx: EContext, args: GTXValue): GTXValue {
    val accountID = args["account_id"]!!.asByteArray(true)
    val assetID = args["asset_id"]!!.asString()
    val history = config.dbOps.getHistory(ctx, accountID, assetID)
    val entries = history.map({
        gtx(
                "delta" to gtx(it.delta),
                "tx_rid" to gtx(it.txRID),
                "op_index" to gtx(it.opIndex.toLong()),
                "memo" to (if (it.memo != null) gtx(it.memo) else GTXNull)
        )
    }).toTypedArray()
    return gtx(*entries)
}
