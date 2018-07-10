package net.postchain.base

import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockchainConfigurationData
import net.postchain.gtx.DictGTXValue
import net.postchain.gtx.GTXNull
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx
import net.postchain.gtx.messages.DictPair
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.MapConfiguration
import java.util.*

/*

        val blockSigningPrivateKey = config.getString("blocksigningprivkey").hexStringToByteArray()
        val blockSigningPublicKey = secp256k1_derivePubKey(blockSigningPrivateKey)
        val blockSigner = cryptoSystem.makeSigner(blockSigningPublicKey, blockSigningPrivateKey)

 */

class BaseBlockchainConfigurationData(
        override val data: GTXValue,
        override val blockchainRID: ByteArray,
        override val chainID: Long,
        override val nodeID: Int,
        val blockSigner: Signer,
        val subjectID: ByteArray
) : BlockchainConfigurationData {

    fun getSigners(): List<ByteArray> {
        return data["signers"]!!.asArray().map { it.asByteArray() }
    }

    fun getBlockBuildingStrategyName(): String {
        return data["blockstrategy"]?.get("name")?.asString() ?: ""
    }

    fun getBlockBuildingStrategy() : GTXValue? {
        return data["blockstrategy"]
    }

    companion object {
        fun readFromCommonsConfiguration(config: Configuration, chainID: Long, nodeID: Int): BaseBlockchainConfigurationData {
            val gConfig = convertConfigToGTXValue(config)
            val cryptoSystem = SECP256K1CryptoSystem()
            val privKey = gConfig["blocksigningprivkey"]!!.asByteArray()
            val pubKey = secp256k1_derivePubKey(privKey)
            val signer = cryptoSystem.makeSigner(
                pubKey, privKey // TODO: maybe take it from somewhere?
            )
            return BaseBlockchainConfigurationData(
                    gConfig,
                    gConfig["blockchainRID"]!!.asByteArray(),
                    chainID,
                    nodeID,
                    signer,
                    pubKey
            )
        }

        private fun convertGTXConfigToGTXValue(config: Configuration): GTXValue {
            val properties = mutableListOf(
                    "modules" to gtx(
                            config.getStringArray("gtx.modules").map { gtx(it) }
                    )
            )

            if (config.containsKey("gtx.ft.assets")) {
                val ftProps = mutableListOf<Pair<String, GTXValue>>()
                val assets = config.getStringArray("gtx.ft.assets")

                ftProps.add("assets" to gtx(*assets.map {
                    assetName ->
                    val issuers = gtx(
                            *config.getStringArray("gtx.ft.asset.${assetName}.issuers").map(
                                    { gtx(it.hexStringToByteArray()) }
                            ).toTypedArray())

                    gtx(
                            "name" to gtx(assetName),
                            "issuers" to issuers
                    )
                }.toTypedArray()))
                properties.add("ft" to gtx(*ftProps.toTypedArray()))
            }

            if (config.containsKey("gtx.sqlmodules"))
                properties.add("sqlmodules" to gtx(*
                        config.getStringArray("gtx.sqlmodules").map {gtx(it)}.toTypedArray()
                ))

            return gtx(*properties.toTypedArray())
        }



        private fun convertConfigToGTXValue(config: Configuration): GTXValue {

            fun blockStrategy(config: Configuration): GTXValue {
                return gtx(
                        "name" to gtx(config.getString("blockstrategy"))
                )
            }

            val properties = mutableListOf(
                    "blockstrategy" to blockStrategy(config),
                    "blockchainRID" to gtx(config.getString("blockchainrid").hexStringToByteArray()),
                    "configurationfactory" to gtx(config.getString("configurationfactory")),
                    "signers" to gtx(config.getStringArray("signers").map { gtx(it.hexStringToByteArray())}),
                    "blocksigningprivkey" to gtx(config.getString("blocksigningprivkey").hexStringToByteArray())
            )

            if (config.containsKey("gtx.modules")) {
                properties.add(Pair("gtx", convertGTXConfigToGTXValue(config)))
            }

            return gtx(*properties.toTypedArray())
        }
    }
}
