package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.gtx.GTXValue
/*
class SmartContract(
        override val accountID: ByteArray,
        override val descriptor: GTXValue
) : FTInputAccount, FTOutputAccount {
    override val skipUpdate = false
    val assetID = descriptor[2].asString()
    val amount = descriptor[3].asInteger()

    override fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        if (dbops.getBalance())
        return true
    }

    override fun verifyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }


    override fun applyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }


    override fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }
}*/