// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import com.sun.java.swing.plaf.gtk.GTKColorType
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.core.ByteArrayKey
import net.postchain.gtx.GTXValue
import org.apache.commons.configuration2.Configuration

/**
 * Create rules to be checked when verifying and applying [FT_issue_op]. Retrieve assets and allowed issuers from the
 * configuration prior to each rule check.
 *
 * @param ac account utility methods
 * @param config configuration options
 * @return rules for the issue operation
 */
fun makeFTIssueRules(ac: AccountUtil, config: GTXValue): FTIssueRules {
    val assetIssuerMap: MutableMap<String, Map<ByteArrayKey, ByteArray>> = mutableMapOf()
    val issuerAccountDescriptors: MutableMap<ByteArrayKey, ByteArray> = mutableMapOf()
    val assets = config["assets"]!!
    for (asset in assets.asArray()) {
        val issuerMap = mutableMapOf<ByteArrayKey, ByteArray>()
        for (issuer in asset["issuers"]!!.asArray()) {
            val pubKey = issuer.asByteArray()
            val descriptor = ac.issuerAccountDesc(pubKey)
            val issuerID = ac.makeAccountID(descriptor)
            val key = ByteArrayKey(issuerID)
            issuerMap[key] = pubKey
            issuerAccountDescriptors[key] = descriptor
        }
        assetIssuerMap[asset.asString()] = issuerMap.toMap()
    }

    /**
     * Check that the asset is specified in the configuration. If so, check that the issuer is authorized to issue for
     * the particular assetID.
     *
     * @param data data for the issuing operation
     * @return boolean whether issuer is authorized or not
     */
    fun checkIssuer(data: FTIssueData): Boolean {
        if (data.assetID !in assetIssuerMap) return false
        val issuer = assetIssuerMap[data.assetID]!![ByteArrayKey(data.issuerID)]
        if (issuer == null) {
            return false
        } else {
            return data.opData.signers.any { it.contentEquals(issuer) }
        }
    }

    /**
     * Prepare to apply issue operation. Register issuers account if it doesn't already exist.
     *
     * @param ctx contextual information for the operation
     * @param dbOps database operations
     * @param data data for the issuing operation
     * @return boolean whether the check passes or not (Always passes?)
     */
    fun prepare(ctx: OpEContext, dbOps: FTDBOps, data: FTIssueData): Boolean {
        val maybeDesc = dbOps.getDescriptor(ctx.txCtx, data.issuerID)
        if (maybeDesc == null) {
            dbOps.registerAccount(ctx,
                    data.issuerID,
                    0,
                    issuerAccountDescriptors[ByteArrayKey(data.issuerID)]!!
            )
        }
        dbOps.registerAsset(ctx, data.assetID)
        return true
    }

    return FTIssueRules(arrayOf(::checkIssuer), arrayOf(::prepare))
}

/**
 * Create rules to check when verifying and applying [FT_register_op]. If configuration specified 'openRegistration'
 * anyone may register an account on the blockchain. Otherwise only those listed in the 'registrators' option may do it.
 *
 * @param config configuration options
 * @return rules for the register operation
 */
fun makeFTRegisterRules(config: GTXValue): FTRegisterRules {
//    if (config.getBoolean("openRegistration")) { // TODO //FIX //TOFIX
//        return FTRegisterRules(arrayOf(), arrayOf())
//    } else {
//        val registrators = config.getStringArray("registrators").map { it.hexStringToByteArray() }
//        fun checkRegistration(data: FTRegisterData): Boolean {
//            return data.opData.signers.any { signer ->
//                registrators.any { it.contentEquals(signer) }
//            }
//        }
//        return FTRegisterRules(arrayOf(::checkRegistration), arrayOf())
//    }
    return FTRegisterRules( arrayOf(), arrayOf())
}

/**
 * Create rules to check when verifying and applying [FT_transfer_op].
 *
 * @param config configuration options
 */
fun makeFTTransferRules(config: GTXValue): FTTransferRules {
    return FTTransferRules(arrayOf(), arrayOf(), false)
}

/**
 * Create an account factory which provides methods to create new input and output accounts.
 *
 * @param config configuration options
 * @return account factory
 */
fun makeFTAccountFactory(config: GTXValue, blockchainRID: ByteArray): AccountFactory {

    return BaseAccountFactory(
            mapOf(
                    NullAccount.entry,
                    BasicAccount.entry
            )
    )
}

/**
 * Create configuration for the FT module based on the base [config]
 *
 * @param config the base configuration options
 * @return the FT module configuration
 */
fun makeBaseFTConfig(config: GTXValue, blockchainRID: ByteArray): FTConfig {
    val blockchainRID = blockchainRID
    val ftConfig = config["gtx"]!!["ft"]?: throw Exception("No ft module") // MARK


    val cs = SECP256K1CryptoSystem()
    val ac = AccountUtil(blockchainRID, cs)
    val accFactory = makeFTAccountFactory(config, blockchainRID)
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
