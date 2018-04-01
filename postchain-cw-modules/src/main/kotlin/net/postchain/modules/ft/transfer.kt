// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.ProgrammerMistake
import net.postchain.core.TxEContext
import net.postchain.core.UserMistake
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXValue

class TransferElement<AccountT>(val account: AccountT,
                                val assetID: String,
                                val amount: Long,
                                val extra: ExtraData) {
    fun getMemo(default: String?): String? {
        return extra["memo"]?.asString() ?: default
    }
}

class TransferData<InputAccountT, OutputAccountT>
(
        val opData: ExtOpData,
        val inputs: Array<TransferElement<InputAccountT>>,
        val outputs: Array<TransferElement<OutputAccountT>>,
        val extra: ExtraData
)

typealias StaticTransferElement = TransferElement<ByteArray>
typealias StaticTransferData = TransferData<ByteArray, ByteArray>

fun parseTransferData(opData: ExtOpData): StaticTransferData {
    val args = opData.args
    if (args.size < 2) throw UserMistake("Not enough arguments to transfer")
    fun parseElement(it: GTXValue): StaticTransferElement {
        val extra = if (it.getSize() >= 4) it[3].asDict() else NoExtraData
        return TransferElement(
                it[0].asByteArray(),
                it[1].asString(),
                it[2].asInteger(),
                extra)
    }


    val inputs = args[0].asArray().map(::parseElement).toTypedArray()
    val outputs = args[1].asArray().map(::parseElement).toTypedArray()
    val extra = if (args.size >= 3) args[2].asDict() else NoExtraData
    return TransferData(opData, inputs, outputs, extra)
}

typealias StaticTransferRule = (StaticTransferData)->Boolean

typealias CompleteTransferData = TransferData<FTInputAccount, FTOutputAccount>
typealias CompleteTransferRule = (OpEContext, FTDBOps, CompleteTransferData)->Boolean

open class FTTransferRules(staticRules: Array<StaticTransferRule>, val completeRules: Array<CompleteTransferRule>,
                           val allowLoss: Boolean)
    : FTRules<StaticTransferData>(staticRules, arrayOf())
{
    open fun checkAssetConservation(td: StaticTransferData): Boolean {
        val assetAmounts = mutableMapOf<String, Long>()
        for (input in td.inputs) {
            if (input.amount < 0) return false
            val sumSoFar = assetAmounts[input.assetID] ?: 0L
            assetAmounts[input.assetID] = sumSoFar + input.amount
        }
        for (output in td.outputs) {
            if (output.amount < 0) return false
            val sumSoFar = assetAmounts[output.assetID] ?: return false
            val newSum = sumSoFar - output.amount
            if (newSum < 0)
                return false
            else if (newSum == 0L)
                assetAmounts.remove(output.assetID)
            else
                assetAmounts[output.assetID] = newSum
        }
        if (!allowLoss && assetAmounts.isNotEmpty())
            return false
        return true
    }

    override fun applyStaticRules(data: StaticTransferData): Boolean {
        // conservation check is essentially a static rule which is hard to disable,
        // because in most cases you want it
        if (!checkAssetConservation(data)) return false
        return super.applyStaticRules(data)
    }

    override fun applyDbRules(ctx: OpEContext, dbops: FTDBOps, data: StaticTransferData): Boolean {
        throw ProgrammerMistake("Call applyCompleteRules instead")
    }

    open fun applyCompleteRules(ctx: OpEContext, dbops: FTDBOps, data: CompleteTransferData): Boolean {
        return completeRules.all { it(ctx, dbops, data) }
    }

}

/**
 * Transfer operation used when transferring tokens between accounts.
 *
 * @param config configuration options for the FT module
 * @param data information relating to the operation
 * @property transferData static data relating to the transfer operation
 */
class FT_transfer_op (val config: FTConfig, data: ExtOpData): GTXOperation(data) {
    val transferData = parseTransferData(data)

    /**
     * Checks if operation is correct
     *
     * @return status of blockchain compatability
     */
    override fun isCorrect(): Boolean {
        return config.transferRules.applyStaticRules(transferData)
    }

    /**
     * Operation is applied to the database, given that certain rules are followed
     *
     * @param ctx contextual information
     * @return if operation was properly applied or not
     */
    override fun apply(ctx: TxEContext): Boolean {
        val opCtx = OpEContext(ctx, data.opIndex)
        val dbOps = config.dbOps

        // 1. resolve accounts

        val inputs = transferData.inputs.map({
            TransferElement(
                    config.accountResolver.resolveInputAccount(opCtx, config.dbOps, it.account),
                    it.assetID,
                    it.amount,
                    it.extra)
        }).toTypedArray()
        val outputs = transferData.outputs.map({
            TransferElement(
                    config.accountResolver.resolveOutputAccount(opCtx, config.dbOps, it.account),
                    it.assetID,
                    it.amount,
                    it.extra)
        }).toTypedArray()
        val completeTransferData = CompleteTransferData(data, inputs, outputs, transferData.extra)

        // 2. verify inputs and outputs (custom)

        for ((idx, input) in inputs.withIndex())
            if (!input.account.verifyInput(opCtx, dbOps, idx, completeTransferData))
                return false;
        for ((idx, output) in outputs.withIndex())
            if (!output.account.verifyOutput(opCtx, dbOps, idx, completeTransferData))
                return false;

        // 3. apply deltas

        val xferMemo = transferData.extra["memo"]?.asString()

        for (input in inputs) {
            if (!input.account.skipUpdate)
                dbOps.update(opCtx, input.account.accountID, input.assetID, -input.amount, input.getMemo(xferMemo))
        }
        for (output in outputs) {
            if (!output.account.skipUpdate)
                dbOps.update(opCtx, output.account.accountID, output.assetID, output.amount, output.getMemo(xferMemo))
        }

        // 4. apply custom rules (global)

        if (!config.transferRules.applyCompleteRules(opCtx, config.dbOps, completeTransferData)) return false

        // 5. apply custom rules per account

        for ((idx, input) in inputs.withIndex())
            if (!input.account.applyInput(opCtx, dbOps, idx, completeTransferData))
                return false;
        for ((idx, output) in outputs.withIndex())
            if (!output.account.applyOutput(opCtx, dbOps, idx, completeTransferData))
                return false;
        return true
    }
}