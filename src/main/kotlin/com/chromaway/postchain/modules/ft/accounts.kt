package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.UserMistake
import com.chromaway.postchain.gtx.GTXValue

typealias InputAccountConstructor = (ByteArray, GTXValue)->FTInputAccount
typealias OutputAccountConstructor = (ByteArray, GTXValue)->FTOutputAccount

typealias AccountConstructors = Pair<InputAccountConstructor, OutputAccountConstructor>

class SimpleOutputAccount(override val accountID: ByteArray): FTOutputAccount {
    override val descriptor = null
    override val skipUpdate = false
    override fun verifyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }

    override fun applyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }
}

val simpleOutputAccount = {
    accid: ByteArray, _: GTXValue ->
    SimpleOutputAccount(accid)
}

class SimpleAccountResolver(
        val accountConstructors: Map<Int, AccountConstructors>
): AccountResolver {

    override fun resolveInputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTInputAccount {
        val descriptor = dbops.getDescriptor(ctx, accountID)
        val accType = descriptor[0].asInteger().toInt()
        if (accType in accountConstructors) {
            return accountConstructors[accType]!!.first(accountID, descriptor)
        } else throw UserMistake("Account type not found")
    }

    override fun resolveOutputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTOutputAccount {
        val descriptor = dbops.getDescriptor(ctx, accountID)
        val accType = descriptor[0].asInteger().toInt()
        if (accType in accountConstructors) {
            return accountConstructors[accType]!!.second(accountID, descriptor)
        } else throw UserMistake("Account type not found")
    }
}

class BasicAccount(override val accountID: ByteArray, override val descriptor: GTXValue): FTInputAccount {
    val blockchainID = descriptor[1].asByteArray()
    val pubkey = descriptor[2].asByteArray()

    override val skipUpdate = false

    override fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return data.signers.any { it.contentEquals(pubkey) }
    }

    override fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }
}