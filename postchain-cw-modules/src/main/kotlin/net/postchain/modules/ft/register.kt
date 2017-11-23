// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXValue
import net.postchain.gtx.decodeGTXValue

class FTRegisterData(val opData: ExtOpData,
                     val accountID: ByteArray,
                     val accountRawDesc: ByteArray,
                     val accountDesc: GTXValue,
                     val extra: ExtraData)

typealias StaticRegisterRule = (FTRegisterData)->Boolean
typealias DbRegisterRule = (OpEContext, FTDBOps, FTRegisterData)->Boolean

typealias FTRegisterRules = FTRules<FTRegisterData>

class FT_register_op (val config: FTConfig, data: ExtOpData): GTXOperation(data) {
    val registerData = FTRegisterData(
            data,
            config.cryptoSystem.digest(data.args[0].asByteArray()),
            data.args[0].asByteArray(),
            decodeGTXValue(data.args[0].asByteArray()),
            getExtraData(data, 1)
    )

    override fun isCorrect(): Boolean {
        val inputAcc = config.accountFactory.makeInputAccount(registerData.accountID, registerData.accountDesc)
        if (!inputAcc.isCompatibleWithblockchainRID(config.blockchainRID)) return false
        val outputAcc = config.accountFactory.makeOutputAccount(registerData.accountID, registerData.accountDesc)
        if (!outputAcc.isCompatibleWithblockchainRID(config.blockchainRID)) return false

        return config.registerRules.applyStaticRules(registerData)
    }

    override fun apply(ctx: TxEContext): Boolean {
        val opCtx = OpEContext(ctx, data.opIndex)

        if (!config.registerRules.applyDbRules(opCtx, config.dbOps, registerData)) return false
        config.dbOps.registerAccount(
                opCtx,
                registerData.accountID,
                registerData.accountDesc[0].asInteger().toInt(),
                registerData.accountRawDesc
        )
        return true
    }
}