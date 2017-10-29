package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.core.UserMistake
import com.chromaway.postchain.gtx.GTXValue
import com.chromaway.postchain.gtx.encodeGTXValue
import com.chromaway.postchain.gtx.gtx

interface FTAccount {
    val accountID: ByteArray
    fun isCompatibleWithBlockchainID(blockchainID: ByteArray): Boolean
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

    override fun isCompatibleWithBlockchainID(blockchainID: ByteArray): Boolean {
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
        return factory.makeInputAccount(accountID, dbops.getDescriptor(ctx, accountID))
    }

    override fun resolveOutputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTOutputAccount {
        return factory.makeOutputAccount(accountID, dbops.getDescriptor(ctx, accountID))
    }
}

class BasicAccountInput(override val accountID: ByteArray, override val descriptor: GTXValue) : FTInputAccount {
    val blockchainID = descriptor[1]
    val pubkey = descriptor[2].asByteArray()

    override val skipUpdate = false

    override fun isCompatibleWithBlockchainID(blockchainID: ByteArray): Boolean {
        if (this.blockchainID.isNull()) return false
        else return this.blockchainID.asByteArray().contentEquals(blockchainID)
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
    fun makeDescriptor(blockchainID: ByteArray, pubKey: ByteArray): ByteArray {
        return encodeGTXValue(
                gtx(gtx(code.toLong()), gtx(blockchainID), gtx(pubKey))
        )
    }
}

class NullAccountInput(override val accountID: ByteArray, override val descriptor: GTXValue) : FTInputAccount {
    override val skipUpdate = false
    val blockchainID = descriptor[1]

    override fun isCompatibleWithBlockchainID(blockchainID: ByteArray): Boolean {
        if (this.blockchainID.isNull()) return false
        else return this.blockchainID.asByteArray().contentEquals(blockchainID)
    }

    override fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return false
    }

    override fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean {
        return false
    }
}

object NullAccount : AccountType(0, ::NullAccountInput, simpleOutputAccount) {
    fun makeDescriptor(blockchainID: ByteArray, pubKey: ByteArray): ByteArray {
        return encodeGTXValue(
                gtx(gtx(code.toLong()), gtx(blockchainID), gtx(pubKey))
        )
    }
}

class AccountUtil(val blockchainID: ByteArray, val cs: CryptoSystem) {

    fun makeAccountID(descriptor: ByteArray): ByteArray {
        return cs.digest(descriptor)
    }

    fun issuerAccountID(issuerPubKey: ByteArray): ByteArray {
        return makeAccountID(
                NullAccount.makeDescriptor(blockchainID, issuerPubKey)
        )
    }
}
