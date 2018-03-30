// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.base.CryptoSystem
import net.postchain.core.UserMistake
import net.postchain.gtx.GTXNull
import net.postchain.gtx.GTXValue
import net.postchain.gtx.encodeGTXValue
import net.postchain.gtx.gtx

/**
 * FT works through accounts. Accounts has their own balance, history and what authorization is necessary to initiate
 * transfer of tokens.
 *
 * @property accountID is the hash of the accounts descriptor and is used to reference the account
 */
interface FTAccount {
    val accountID: ByteArray
    fun isCompatibleWithblockchainRID(blockchainRID: ByteArray): Boolean
}
/**
 * An input account is the source account of a token transfer event
 *
 * @property descriptor describes the account such as account type and associated public keys
 * @property skipUpdate signals if deltas should be applied to the database in a token transfer operation
 */
interface FTInputAccount : FTAccount {
    val descriptor: GTXValue
    val skipUpdate: Boolean
    fun verifyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
    fun applyInput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
}

/**
 * An output account is the destination account of a token transfer event
 *
 * @property descriptor describes the account such as account type and associated public keys
 * @property skipUpdate signals if deltas should be applied to the database of a token transfer operation
 */
interface FTOutputAccount : FTAccount {
    val descriptor: GTXValue?
    val skipUpdate: Boolean
    fun verifyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
    fun applyOutput(ctx: OpEContext, dbops: FTDBOps, index: Int, data: CompleteTransferData): Boolean
}

/**
 * Constructor for input accounts, taking accountID and account descriptor as input values
 */
typealias InputAccountConstructor = (ByteArray, GTXValue)->FTInputAccount

/**
 * Constructor for output accounts, taking accountID and account descriptor as input values
 */
typealias OutputAccountConstructor = (ByteArray, GTXValue)->FTOutputAccount

/**
 * A pair of account constructors, one input and one output
 */
typealias AccountConstructors = Pair<InputAccountConstructor, OutputAccountConstructor>

/**
 * Simple output account that is compatible with all blockchains and always verifies
 *
 * @property accountID the account identifier
 */
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

/**
 * Constructor for a [SimpleOutputAccount]
 */
val simpleOutputAccount = {
    accid: ByteArray, _: GTXValue ->
    SimpleOutputAccount(accid)
}

/**
 * Account Factory with methods to create input and output accounts given their constructors
 *
 * @property accountConstructors a map of available account constructors
 */
class BaseAccountFactory(val accountConstructors: Map<Int, AccountConstructors>) : AccountFactory {
    /**
     * Create an input account, given that its constructor specified in [descriptor] exists in [accountConstructors]
     *
     * @param accountID the account ID
     * @param descriptor the account descriptor
     * @return the input account
     */
    override fun makeInputAccount(accountID: ByteArray, descriptor: GTXValue): FTInputAccount {
        val accType = descriptor[0].asInteger().toInt()
        if (accType in accountConstructors) {
            return accountConstructors[accType]!!.first(accountID, descriptor)
        } else throw UserMistake("Account type not found")
    }

    /**
     * Create an output account, given that its constructor specified in [descriptor] exists in [accountConstructors]
     *
     * @param accountID the accound ID
     * @param descriptor the account descriptor
     * @return the output account
     */
    override fun makeOutputAccount(accountID: ByteArray, descriptor: GTXValue): FTOutputAccount {
        val accType = descriptor[0].asInteger().toInt()
        if (accType in accountConstructors) {
            return accountConstructors[accType]!!.second(accountID, descriptor)
        } else throw UserMistake("Account type not found")
    }
}

/**
 * Account resolver returning input and output accounts given their account descriptors.
 *
 * @property factory used to make the relevant account
 */
class BaseAccountResolver(val factory: AccountFactory) : AccountResolver {
    /**
     * Resolve input account given the account ID and its descriptor
     *
     * @param ctx contextual information regarding the operation
     * @param dbops DB operations, including call for retrieving account descriptor given the [accountID]
     * @param accountID the account identifier
     * @return the input account instance
     */
    override fun resolveInputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTInputAccount {
        val descriptor = dbops.getDescriptor(ctx.txCtx, accountID)
        if (descriptor == null)
            throw UserMistake("Account descriptor not found")
        else
            return factory.makeInputAccount(accountID, descriptor)
    }

    /**
     * Resolve output account given the account ID and its descriptor
     *
     * @param ctx contextual information regarding the operation
     * @param dbops DB operations, including call for retrieving account descriptor given the [accountID]
     * @param accountID the account identifier
     * @return the output account instance
     */
    override fun resolveOutputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTOutputAccount {
        val descriptor = dbops.getDescriptor(ctx.txCtx, accountID)
        if (descriptor == null)
            throw UserMistake("Account descriptor not found")
        else
            return factory.makeOutputAccount(accountID, descriptor)
    }
}

/**
 * Basic input account
 *
 * @property accountID the account identifier
 * @property descriptor account descriptor including [blockchainRID] and [pubkey]
 * @property blockchainRID reference to blockchain account is tied to
 * @property pubkey the cryptographic public key associated with the account
 */
class BasicAccountInput(override val accountID: ByteArray, override val descriptor: GTXValue) : FTInputAccount {
    val blockchainRID = descriptor[1]
    val pubkey = descriptor[2].asByteArray()

    override val skipUpdate = false

    /**
     * Verify that this account is compatible with the blockchain with RID [blockchainRID]
     *
     * @blockchainRID reference to the blockchain
     * @return compatability status between the [blockchainRID] and the account
     */
    override fun isCompatibleWithblockchainRID(blockchainRID: ByteArray): Boolean {
        if (this.blockchainRID.isNull()) return true
        else return this.blockchainRID.asByteArray().contentEquals(blockchainRID)
    }

    /**
     * Verifies that [pubkey] exists in the signers array in the operation data structure
     *
     * @param ctx contextual information
     * @param dbops database operations
     * @param index input index of the operation
     * @param data all data related to the transfer
     * @return status of verification on input
     */
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

/**
 * A basic account object with account type equal to 1 consisting of a [BasicAccountInput] and a [SimpleOutputAccount]
 */
object BasicAccount : AccountType(1, ::BasicAccountInput, simpleOutputAccount) {
    /**
     * Create a descriptor for this account
     *
     * @param blockchainRID reference to the blockchain
     * @param pubkey associated cryptographic public key
     * @return the account descriptor
     */
    fun makeDescriptor(blockchainRID: ByteArray, pubKey: ByteArray): ByteArray {
        return encodeGTXValue(
                gtx(gtx(code.toLong()), gtx(blockchainRID), gtx(pubKey))
        )
    }
}

/**
 * Null input account, similar to [BasicAccountInput] but will always fail calls to verify and apply
 *
 * @property accountID the account identifier
 * @property descriptor account descriptor including [blockchainRID]
 * @property blockchainRID reference to blockchain account is tied to
 */
class NullAccountInput(override val accountID: ByteArray, override val descriptor: GTXValue) : FTInputAccount {
    override val skipUpdate = false
    val blockchainRID = descriptor[1]

    /**
     * Verify that this account is compatible with the blockchain with RID [blockchainRID]
     *
     * @blockchainRID reference to the blockchain
     * @return compatability status between the [blockchainRID] and the account
     */
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

/**
 * A null account object with account type equal to 0 consisting of a [NullAccountInput] and a [SimpleOutputAccount]
 */
object NullAccount : AccountType(0, ::NullAccountInput, simpleOutputAccount) {
    /**
     * Create a descriptor for this account. Used for issuer accounts.
     *
     * @param blockchainRID reference to the blockchain
     * @param pubkey associated cryptographic public key for the issuer
     * @return the account descriptor
     */
    fun makeDescriptor(blockchainRID: ByteArray?, pubKey: ByteArray): ByteArray {
        val gtxBlockchainRID = if (blockchainRID == null) GTXNull else gtx(blockchainRID)
        return encodeGTXValue(
                gtx(gtx(code.toLong()), gtxBlockchainRID, gtx(pubKey))
        )
    }
}

/**
 * Account utility functions
 *
 * @param blockchainRID for referencing the blockchain
 * @param cs the cryptosystem to use
 */
class AccountUtil(val blockchainRID: ByteArray, val cs: CryptoSystem) {

    /**
     * Create the account ID which is the digest of the account descriptor
     *
     * @param descriptor is the account descriptor
     * @return The accountID
     */
    fun makeAccountID(descriptor: ByteArray): ByteArray {
        return cs.digest(descriptor)
    }

    /**
     * Create an account descriptor for an issuer
     *
     * @param issuerPubKey the public key of the issuer
     * @return The account descriptor
     */
    fun issuerAccountDesc(issuerPubKey: ByteArray): ByteArray {
        return NullAccount.makeDescriptor(blockchainRID, issuerPubKey)
    }
}
