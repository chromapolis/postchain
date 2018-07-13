// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.EContext
import net.postchain.gtx.GTXNull
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx

/**
 * Query that checks if an account exists given the account identifier
 *
 * @param config configuration for the FT module
 * @param ctx contextual information
 * @param args arguments including account identifier and asset identifier
 * @return GTXValue of 1L for true and 0L for false
 */
fun ftAccountExistsQ(config: FTConfig, ctx: EContext, args: GTXValue): GTXValue {
    val accountID = args["account_id"]!!.asByteArray(true)
    val exists = if (config.dbOps.getDescriptor(ctx, accountID) != null) 1L else 0L
    return gtx("exists" to gtx(exists))
}

/**
 * Query that returns the balance of a given account for a given asset
 *
 * @param config configuration for the FT module
 * @param ctx contextual information
 * @param args arguments including account identifier and asset identifier
 * @return the balance of the account
 */
fun ftBalanceQ(config: FTConfig, ctx: EContext, args: GTXValue): GTXValue {
    val accountID = args["account_id"]!!.asByteArray(true)
    val assetID = args["asset_id"]!!.asString()
    return gtx(
            "balance" to
                    gtx(config.dbOps.getBalance(ctx, accountID, assetID)))
}

/**
 * Query that return the historic events a given account have been involved in
 *
 * @param config configuration for the FT module
 * @param ctx contextual information
 * @param args arguments including account identifier and asset identifier
 * @return history entries relating to the given account and asset
 */
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
