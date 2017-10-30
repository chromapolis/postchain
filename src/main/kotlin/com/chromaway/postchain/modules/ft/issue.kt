package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.gtx.ExtOpData
import com.chromaway.postchain.gtx.GTXOperation

class FTIssueData(val opData: ExtOpData, val issuerID: ByteArray, val assetID: String, val amount: Long, val recipientID: ByteArray, val extra: ExtraData)
typealias StaticIssueRule = (FTIssueData)->Boolean
typealias DbIssueRule = (OpEContext, FTDBOps, FTIssueData)->Boolean

typealias FTIssueRules = FTRules<FTIssueData>

// Do we benefit from having a class?
//open class FTIssueRules(staticRules: Array<StaticIssueRule>, dbRules: Array<DbIssueRule>)
//    : FTRules<FTIssueData>(staticRules, dbRules)

class FT_issue_op (val config: FTConfig, data: ExtOpData): GTXOperation(data) {
    val issueData = FTIssueData(
            data,
            data.args[0].asByteArray(),
            data.args[1].asString(),
            data.args[2].asInteger(),
            data.args[3].asByteArray(),
            getExtraData(data, 4)
    )

    override fun isCorrect(): Boolean {
        if (issueData.amount < 0) return false
        return config.issueRules.applyStaticRules(issueData)
    }

    override fun apply(ctx: TxEContext): Boolean {
        val opCtx = OpEContext(ctx, data.opIndex)
        if (!config.issueRules.applyDbRules(opCtx, config.dbOps, issueData)) return false
        config.dbOps.update(opCtx, issueData.issuerID, issueData.assetID, -issueData.amount, true)
        config.dbOps.update(opCtx, issueData.recipientID, issueData.assetID, issueData.amount, false)
        return true
    }
}