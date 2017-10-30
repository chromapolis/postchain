package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.hexStringToByteArray
import org.apache.commons.configuration2.Configuration

fun makeFTIssueRules(ac: AccountUtil, config: Configuration): FTIssueRules {
    val assetIssuerMap: MutableMap<String, Map<ByteArray, ByteArray>> = mutableMapOf()
    val issuerAccountDescriptors: MutableMap<ByteArray, ByteArray> = mutableMapOf()
    val assets = config.getStringArray("assets")
    for (assetName in assets) {
        val issuerMap = mutableMapOf<ByteArray, ByteArray>()
        for (issuer in config.getStringArray("asset.${assetName}.issuers")) {
            val pubKey = issuer.hexStringToByteArray()
            val descriptor = ac.issuerAccountDesc(pubKey)
            val issuerID = ac.makeAccountID(descriptor)
            issuerMap[issuerID] = pubKey
            issuerAccountDescriptors[issuerID] = descriptor
        }
        assetIssuerMap[assetName] = issuerMap.toMap()
    }

    fun checkIssuer(data: FTIssueData): Boolean {
        if (data.assetID !in assetIssuerMap) return false
        val issuer = assetIssuerMap[data.assetID]!![data.issuerID]
        if (issuer == null) {
            return false
        } else {
            return data.opData.signers.any { it.contentEquals(issuer) }
        }
    }

    fun registerIssuerAccount(ctx: OpEContext, dbOps: FTDBOps, data: FTIssueData): Boolean {
        val maybeDesc = dbOps.getDescriptor(ctx, data.issuerID)
        if (maybeDesc == null) {
            dbOps.registerAccount(ctx,
                    data.issuerID,
                    0,
                    issuerAccountDescriptors[data.issuerID]!!
            )
        }
        return true
    }

    return FTIssueRules(arrayOf(::checkIssuer), arrayOf(::registerIssuerAccount))
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
