// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.modules.ft

import org.postchain.base.CryptoSystem
import org.postchain.core.UserMistake
import org.postchain.gtx.GTXNull
import org.postchain.gtx.GTXValue
import org.postchain.gtx.encodeGTXValue
import org.postchain.gtx.gtx

interface FTAccount {
    val accountID: ByteArray
    fun isCompatibleWithblockchainRID(blockchainRID: ByteArray): Boolean
}

interface FTInputAccount : FTAccount {
    val descriptor: GTXValue
    val skipUpdate: Boolean
    fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
    fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
}

interface FTOutputAccount : FTAccount {
    val descriptor: GTXValue?
    val skipUpdate: Boolean
    fun verifyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
    fun applyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
}


typealias InputAccountConstructor = (ByteArray, GTXValue)->FTInputAccount
typealias OutputAccountConstructor = (ByteArray, GTXValue)->FTOutputAccount

typealias AccountConstructors = Pair<InputAccountConstructor, OutputAccountConstructor>

class SimpleOutputAccount(override val accountID: ByteArray): FTOutputAccount {
    override val descriptor = null
    override val skipUpdate = false

    override fun isCompatibleWithblockchainRID(blockchainRID: ByteArray): Boolean {
        return true
    }

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


class BaseAccountFactory(val accountConstructors: Map<Int, AccountConstructors>) : AccountFactory {
    override fun makeInputAccount(accountID: ByteArray, descriptor: GTXValue): FTInputAccount {
        val accType = descriptor[0].asInteger().toInt()
        if (accType in accountConstructors) {
            return accountConstructors[accType]!!.first(accountID, descriptor)
        } else throw UserMistake("Account type not found")
    }

    override fun makeOutputAccount(accountID: ByteArray, descriptor: GTXValue): FTOutputAccount {
        val accType = descriptor[0].asInteger().toInt()
        if (accType in accountConstructors) {
            return accountConstructors[accType]!!.second(accountID, descriptor)
        } else throw UserMistake("Account type not found")
    }
}

class BaseAccountResolver(val factory: AccountFactory) : AccountResolver {
    override fun resolveInputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTInputAccount {
        val descriptor = dbops.getDescriptor(ctx.txCtx, accountID)
        if (descriptor == null)
            throw UserMistake("Account descriptor not found")
        else
            return factory.makeInputAccount(accountID, descriptor)
    }

    override fun resolveOutputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTOutputAccount {
        val descriptor = dbops.getDescriptor(ctx.txCtx, accountID)
        if (descriptor == null)
            throw UserMistake("Account descriptor not found")
        else
            return factory.makeOutputAccount(accountID, descriptor)
    }
}

class BasicAccountInput(override val accountID: ByteArray, override val descriptor: GTXValue) : FTInputAccount {
    val blockchainRID = descriptor[1]
    val pubkey = descriptor[2].asByteArray()

    override val skipUpdate = false

    override fun isCompatibleWithblockchainRID(blockchainRID: ByteArray): Boolean {
        if (this.blockchainRID.isNull()) return true
        else return this.blockchainRID.asByteArray().contentEquals(blockchainRID)
    }

    override fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return data.opData.signers.any { it.contentEquals(pubkey) }
    }

    override fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return true
    }
}

open class AccountType(val code: Int,
                       val inputConstructor: InputAccountConstructor,
                       val outputConstructor: OutputAccountConstructor) {
    val entry = Pair(code, Pair(inputConstructor, outputConstructor))
}

object BasicAccount : AccountType(1, ::BasicAccountInput, simpleOutputAccount) {
    fun makeDescriptor(blockchainRID: ByteArray, pubKey: ByteArray): ByteArray {
        return encodeGTXValue(
                gtx(gtx(code.toLong()), gtx(blockchainRID), gtx(pubKey))
        )
    }
}

class NullAccountInput(override val accountID: ByteArray, override val descriptor: GTXValue) : FTInputAccount {
    override val skipUpdate = false
    val blockchainRID = descriptor[1]

    override fun isCompatibleWithblockchainRID(blockchainRID: ByteArray): Boolean {
        if (this.blockchainRID.isNull()) return true
        else return this.blockchainRID.asByteArray().contentEquals(blockchainRID)
    }

    override fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return false
    }

    override fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return false
    }
}

object NullAccount : AccountType(0, ::NullAccountInput, simpleOutputAccount) {
    fun makeDescriptor(blockchainRID: ByteArray?, pubKey: ByteArray): ByteArray {
        val gtxBlockchainRID = if (blockchainRID == null) GTXNull else gtx(blockchainRID)
        return encodeGTXValue(
                gtx(gtx(code.toLong()), gtxBlockchainRID, gtx(pubKey))
        )
    }
}

class AccountUtil(val blockchainRID: ByteArray, val cs: CryptoSystem) {

    fun makeAccountID(descriptor: ByteArray): ByteArray {
        return cs.digest(descriptor)
    }

    fun issuerAccountDesc(issuerPubKey: ByteArray): ByteArray {
        return NullAccount.makeDescriptor(blockchainRID, issuerPubKey)
    }
}
