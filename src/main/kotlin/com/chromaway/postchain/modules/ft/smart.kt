package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.gtx.GTXValue
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler

// assurance contract

class AssuranceContract(
        override val accountID: ByteArray,
        override val descriptor: GTXValue
) : FTInputAccount, FTOutputAccount {
    override val skipUpdate = false
    val assetID = descriptor[2].asString()
    val targetAccountID = descriptor[3].asByteArray()
    val thresholdAmount = descriptor[4].asInteger()
    val deadline = descriptor[5].asInteger()

    override fun isCompatibleWithBlockchainID(blockchainID: ByteArray): Boolean {
        return true
    }

    fun getContributions(ctx: TxEContext): List<Pair<ByteArray, Long>> {
        val r = QueryRunner()
        return r.query(ctx.conn,
                """SELECT h1.delta as amount, acc.account_id as account_id
        FROM ft_history h1
        INNER JOIN ft_history h2
        ON (h1.tx_iid = h2.tx_iid) AND (h1.op_index = h2.op_index) AND (h2.delta < 0)
        INNER JOIN ft_accounts acc
        ON acc.account_iid = h2.account_iid
        WHERE h1.account_iid = ft_find_account(?, ?)
             AND h1.asset_iid = ft_find_asset(?, ?)
        ORDER BY h1.tx_iid""",
                MapListHandler(),
                ctx.chainID,
                accountID,
                ctx.chainID,
                assetID).map {
            Pair(it["account_id"] as ByteArray, it["amount"] as Long)
        }
    }

    override fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        val myBalance = dbops.getBalance(ctx, accountID, assetID)
        if (System.currentTimeMillis() < deadline) return false // TODO: get timestamp from database!

        if (myBalance >= thresholdAmount) {
            // minimum is met -- withdraw complete amount to target
            return (data.inputs.size == 1
                    && data.inputs[0].assetID == assetID
                    && data.inputs[0].amount == myBalance
                    && data.outputs.size == 1
                    && data.outputs[0].assetID == assetID
                    && data.outputs[0].amount == myBalance
                    && data.outputs[0].account.accountID.contentEquals(targetAccountID))
        } else {
            // minimum is not met -- withdraw to backers
            val contributions = getContributions(ctx.txCtx)
            if (data.outputs.size != contributions.size) return false
            for ((idx, c) in contributions.withIndex()) {
                val output = data.outputs[idx]
                if (!c.first.contentEquals(output.account.accountID)) return false
                if (c.second != output.amount) return false
                if (output.assetID != assetID) return false
            }
            return (data.inputs.size == 1
                    && data.inputs[0].assetID == assetID
                    && data.inputs[0].amount == myBalance)
        }
    }

    override fun verifyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        if (System.currentTimeMillis() > deadline) return false // TODO: get timestamp from database!
        return (data.inputs.size == 1
                && data.inputs[0].assetID.contentEquals(assetID)
                && data.outputs.size == 1
                && data.outputs[0].assetID == assetID
                && data.outputs[0].account.accountID.contentEquals(accountID))
    }


    override fun applyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }


    override fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }
}