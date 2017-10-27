package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.ProgrammerMistake
import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.core.UserMistake
import com.chromaway.postchain.gtx.ExtOpData
import com.chromaway.postchain.gtx.GTXOperation
import com.chromaway.postchain.gtx.GTXValue

class TransferElement<AccountT>(val account: AccountT,
                                val assetID: String,
                                val amount: Long,
                                val extra: ExtraData)

class TransferData<InputAccountT, OutputAccountT>
(
        val inputs: Array<TransferElement<InputAccountT>>,
        val outputs: Array<TransferElement<OutputAccountT>>,
        val signers: Array<ByteArray>,
        val extra: ExtraData
)

typealias StaticTransferElement = TransferElement<ByteArray>
typealias StaticTransferData = TransferData<ByteArray, ByteArray>

fun parseTransferData(args: Array<GTXValue>, signers: Array<ByteArray>): StaticTransferData {
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
    return TransferData(inputs, outputs, signers, extra)
}

typealias StaticTransferRule = (StaticTransferData)->Boolean

interface FTAccount {
    val accountID: ByteArray
}

interface FTInputAccount : FTAccount {
    val descriptor : GTXValue
    val skipUpdate: Boolean
    fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
    fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
}

interface FTOutputAccount : FTAccount {
    val descriptor : GTXValue?
    val skipUpdate: Boolean
    fun verifyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
    fun applyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
}



typealias CompleteTransferData = TransferData<FTInputAccount, FTOutputAccount>
typealias CompleteTransferRule = (OpEContext, FTDBOps, CompleteTransferData)->Boolean

open class FTTransferRules(staticRules: Array<StaticTransferRule>, val completeRules: Array<CompleteTransferRule>,
                           val allowLoss: Boolean)
    : FTRules<StaticTransferData>(staticRules, arrayOf())
{
    open fun checkAssetConservation(td: StaticTransferData): Boolean {
        val assetAmounts = mutableMapOf<String, Long>()
        for (input in td.inputs) {
            val sumSoFar = assetAmounts[input.assetID] ?: 0L
            assetAmounts[input.assetID] = sumSoFar + input.amount
        }
        for (output in td.outputs) {
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

class FT_transfer_op (val config: FTConfig, data: ExtOpData): GTXOperation(data) {
    val transferData = parseTransferData(data.args, data.signers)

    override fun isCorrect(): Boolean {
        return config.transferRules.applyStaticRules(transferData)
    }

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
        val competeTransferData = CompleteTransferData(inputs, outputs, data.signers, transferData.extra)

        // 2. verify inputs and outputs (custom)

        for ((idx, input) in inputs.withIndex())
            if (!input.account.verifyInput(opCtx, dbOps, idx, competeTransferData))
                return false;
        for ((idx, output) in outputs.withIndex())
            if (!output.account.verifyOutput(opCtx, dbOps, idx, competeTransferData))
                return false;

        // 3. apply deltas

        for (input in inputs) {
            if (!input.account.skipUpdate)
                dbOps.update(opCtx, input.account.accountID, input.assetID, -input.amount)
        }
        for (output in outputs) {
            if (!output.account.skipUpdate)
                dbOps.update(opCtx, output.account.accountID, output.assetID, output.amount)
        }

        // 4. apply custom rules (global)

        if (!config.transferRules.applyCompleteRules(opCtx, config.dbOps, competeTransferData)) return false

        // 5. apply custom rules per account

        for ((idx, input) in inputs.withIndex())
            if (!input.account.applyInput(opCtx, dbOps, idx, competeTransferData))
                return false;
        for ((idx, output) in outputs.withIndex())
            if (!output.account.applyOutput(opCtx, dbOps, idx, competeTransferData))
                return false;
        return true
    }
}