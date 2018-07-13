// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation

/**
 * Collection of information relevant to the issuing operation
 */
class FTIssueData(val opData: ExtOpData, val issuerID: ByteArray, val assetID: String, val amount: Long, val recipientID: ByteArray, val extra: ExtraData)
typealias StaticIssueRule = (FTIssueData)->Boolean
typealias DbIssueRule = (OpEContext, FTDBOps, FTIssueData)->Boolean

typealias FTIssueRules = FTRules<FTIssueData>

// Do we benefit from having a class?
//open class FTIssueRules(staticRules: Array<StaticIssueRule>, dbRules: Array<DbIssueRule>)
//    : FTRules<FTIssueData>(staticRules, dbRules)

/**
 * Issuing operation used when creating new tokens of a certain asset
 *
 * @property config module configuration settings
 * @property data data relating to the operation, including issuerID, assetID, amount and recipientID
 */
class FT_issue_op (val config: FTConfig, data: ExtOpData): GTXOperation(data) {
    val issueData = FTIssueData(
            data,
            data.args[0].asByteArray(),
            data.args[1].asString(),
            data.args[2].asInteger(),
            data.args[3].asByteArray(),
            getExtraData(data, 4)
    )

    /**
     * Check to see that the operation is correctly formed. Operation is deemed incorrect if the amount is negative
     * or if any of the static rules fail on the [issueData]
     *
     * @return return boolean whether the operation is correctly formed or not
     */
    override fun isCorrect(): Boolean {
        if (issueData.amount < 0) return false
        return config.issueRules.applyStaticRules(issueData)
    }

    /**
     * Apply the operation, updating relevant database entries
     *
     * @param ctx contextual information
     * @return return boolean whether operation was applied correctly or not
     */
    override fun apply(ctx: TxEContext): Boolean {
        val opCtx = OpEContext(ctx, data.opIndex)
        if (!config.issueRules.applyDbRules(opCtx, config.dbOps, issueData)) return false
        val memo = issueData.extra["memo"]?.asString()
        config.dbOps.update(opCtx, issueData.issuerID, issueData.assetID, -issueData.amount, memo, true)
        config.dbOps.update(opCtx, issueData.recipientID, issueData.assetID, issueData.amount, memo, false)
        return true
    }
}