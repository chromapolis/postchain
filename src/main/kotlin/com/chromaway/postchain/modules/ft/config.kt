package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.hexStringToByteArray
import org.apache.commons.configuration2.Configuration

fun issuerAccountID(issuerPubKey: ByteArray): ByteArray {
    return issuerPubKey // TODO
}

fun makeFTIssueRules(config: Configuration): FTIssueRules {
    val assetIssuerMap: MutableMap<String, Map<ByteArray, ByteArray>> = mutableMapOf()
    val assets = config.getStringArray("assets")
    for (assetName in assets) {
        val issuerMap = mutableMapOf<ByteArray, ByteArray>()
        for (issuer in config.getStringArray("asset.${assetName}.issuers")) {
            val pubKey = issuer.hexStringToByteArray()
            issuerMap[issuerAccountID(pubKey)] = pubKey
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
    return FTIssueRules(arrayOf(::checkIssuer), arrayOf())
}

/*
val issueRules : FTIssueRules,
val transferRules : FTTransferRules,
val registerRules: FTRegisterRules,
val accountResolver: AccountResolver,
val dbOps : FTDBOps,
val cryptoSystem: CryptoSystem


class BaseFTConfig(config: Configuration):
        FTConfig(

        )*/