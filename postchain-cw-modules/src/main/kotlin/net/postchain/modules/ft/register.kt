// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXValue
import net.postchain.gtx.decodeGTXValue

/**
 * Collection of information relevant to the register operation
 */
class FTRegisterData(val opData: ExtOpData,
                     val accountID: ByteArray,
                     val accountRawDesc: ByteArray,
                     val accountDesc: GTXValue,
                     val extra: ExtraData)

typealias StaticRegisterRule = (FTRegisterData)->Boolean
typealias DbRegisterRule = (OpEContext, FTDBOps, FTRegisterData)->Boolean

typealias FTRegisterRules = FTRules<FTRegisterData>

/**
 * Register operation used to register an account with a blockchain
 *
 * @property config configuration for the FT module
 * @property data data relating to the operation including account identifier and account descriptor
 */
class FT_register_op (val config: FTConfig, data: ExtOpData): GTXOperation(data) {
    val registerData = FTRegisterData(
            data,
            config.cryptoSystem.digest(data.args[0].asByteArray()),
            data.args[0].asByteArray(),
            decodeGTXValue(data.args[0].asByteArray()),
            getExtraData(data, 1)
    )

    /**
     * Checks if operation is correct. Checks include if account is compatible with the particular blockchainRID and
     * that specific rules are correct
     *
     * @return status of blockchain compatability
     */
    override fun isCorrect(): Boolean {
        val inputAcc = config.accountFactory.makeInputAccount(registerData.accountID, registerData.accountDesc)
        if (!inputAcc.isCompatibleWithblockchainRID(config.blockchainRID)) return false
        val outputAcc = config.accountFactory.makeOutputAccount(registerData.accountID, registerData.accountDesc)
        if (!outputAcc.isCompatibleWithblockchainRID(config.blockchainRID)) return false

        return config.registerRules.applyStaticRules(registerData)
    }

    /**
     * Operation is applied to the database, given that certain rules are followed
     *
     * @param ctx contextual information
     * @return if register operation application was successful or not
     */
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