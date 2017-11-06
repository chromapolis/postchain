// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.modules.ft

import org.postchain.base.SECP256K1CryptoSystem
import org.postchain.base.hexStringToByteArray
import org.apache.commons.configuration2.Configuration

class BAKey(val byteArray: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (super.equals(other)) return true
        if (other is BAKey) {
            return other.byteArray.contentEquals(byteArray)
        } else return false
    }

    override fun hashCode(): Int {
        return byteArray.contentHashCode()
    }
}

fun makeFTIssueRules(ac: AccountUtil, config: Configuration): FTIssueRules {
    val assetIssuerMap: MutableMap<String, Map<BAKey, ByteArray>> = mutableMapOf()
    val issuerAccountDescriptors: MutableMap<BAKey, ByteArray> = mutableMapOf()
    val assets = config.getStringArray("assets")
    for (assetName in assets) {
        val issuerMap = mutableMapOf<BAKey, ByteArray>()
        for (issuer in config.getStringArray("asset.${assetName}.issuers")) {
            val pubKey = issuer.hexStringToByteArray()
            val descriptor = ac.issuerAccountDesc(pubKey)
            val issuerID = ac.makeAccountID(descriptor)
            val key = BAKey(issuerID)
            issuerMap[key] = pubKey
            issuerAccountDescriptors[key] = descriptor
        }
        assetIssuerMap[assetName] = issuerMap.toMap()
    }

    fun checkIssuer(data: FTIssueData): Boolean {
        if (data.assetID !in assetIssuerMap) return false
        val issuer = assetIssuerMap[data.assetID]!![BAKey(data.issuerID)]
        if (issuer == null) {
            return false
        } else {
            return data.opData.signers.any { it.contentEquals(issuer) }
        }
    }

    fun prepare(ctx: OpEContext, dbOps: FTDBOps, data: FTIssueData): Boolean {
        val maybeDesc = dbOps.getDescriptor(ctx.txCtx, data.issuerID)
        if (maybeDesc == null) {
            dbOps.registerAccount(ctx,
                    data.issuerID,
                    0,
                    issuerAccountDescriptors[BAKey(data.issuerID)]!!
            )
        }
        dbOps.registerAsset(ctx, data.assetID)
        return true
    }

    return FTIssueRules(arrayOf(::checkIssuer), arrayOf(::prepare))
}

fun makeFTRegisterRules(config: Configuration): FTRegisterRules {
    if (config.getBoolean("openRegistration")) {
        return FTRegisterRules(arrayOf(), arrayOf())
    } else {
        val registrators = config.getStringArray("registrators").map { it.hexStringToByteArray() }
        fun checkRegistration(data: FTRegisterData): Boolean {
            return data.opData.signers.any { signer ->
                registrators.any { it.contentEquals(signer) }
            }
        }
        return FTRegisterRules(arrayOf(::checkRegistration), arrayOf())
    }
}

fun makeFTTransferRules(config: Configuration): FTTransferRules {
    return FTTransferRules(arrayOf(), arrayOf(), false)
}

fun makeFTAccountFactory(config: Configuration): AccountFactory {

    return BaseAccountFactory(
            mapOf(
                    NullAccount.entry,
                    BasicAccount.entry
            )
    )
}

fun makeBaseFTConfig(config: Configuration): FTConfig {
    val blockchainRID = config.getString("blockchainrid").hexStringToByteArray()
    val ftConfig = config.subset("gtx.ft")

    val cs = SECP256K1CryptoSystem()
    val ac = AccountUtil(blockchainRID, cs)
    val accFactory = makeFTAccountFactory(config)
    return FTConfig(
            makeFTIssueRules(ac, ftConfig),
            makeFTTransferRules(ftConfig),
            makeFTRegisterRules(ftConfig),
            accFactory,
            BaseAccountResolver(accFactory),
            BaseDBOps(),
            cs,
            blockchainRID
    )
}
