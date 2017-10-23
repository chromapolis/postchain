package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.gtx.ExtOpData
import com.chromaway.postchain.gtx.GTXValue

class OpEContext (val txCtx: TxEContext, val opIndex: Int)

typealias ExtraData = Map<String, GTXValue>

val NoExtraData = mapOf<String, GTXValue>()

fun getExtraData(data: ExtOpData, idx: Int): ExtraData {
    if (data.args.size > idx) {
        return data.args[idx].asDict()
    } else {
        return NoExtraData
    }
}

open class FTRules<DataT>(val staticRules: Array<(DataT)->Boolean>, val dbRules: Array<(OpEContext, FTDBOps, DataT)->Boolean>) {
    open fun applyStaticRules(data: DataT): Boolean {
        return staticRules.all { it(data) }
    }

    open fun applyDbRules(ctx: OpEContext, dbops: FTDBOps, data: DataT): Boolean {
        return dbRules.all { it(ctx, dbops, data) }
    }
}

interface AccountResolver {
    fun resolveInputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTInputAccount
    fun resolveOutputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTOutputAccount
}

open class FTConfig (
    val issueRules : FTIssueRules,
    val transferRules : FTTransferRules,
    val registerRules: FTRegisterRules,
    val accountResolver: AccountResolver,
    val dbOps : FTDBOps,
    val cryptoSystem: CryptoSystem
)

interface FTDBOps {
    fun update(ctx: OpEContext, accountID: ByteArray, assetID: String, amount: Long, allowNeg: Boolean = false)
    fun getDescriptor(ctx: OpEContext, accountID: ByteArray): GTXValue
    fun registerAccount(ctx: OpEContext, accountID: ByteArray, accountType: Int, accountDesc: ByteArray)
}

