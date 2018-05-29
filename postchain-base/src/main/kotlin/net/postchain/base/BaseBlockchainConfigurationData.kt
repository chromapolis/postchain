package net.postchain.base

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
        fun readFromCommonsConfiguration(config: Configuration): BaseBlockchainConfigurationData {

            val gConfig = buildConfigurationDataStructure(config)
            val cryptoSystem = SECP256K1CryptoSystem()
            val signer = cryptoSystem.makeSigner(ByteArray(0), ByteArray(0))
            return BaseBlockchainConfigurationData(
                    gtx(1),
                    ByteArray(0),
                    0,
                    0,
                    signer,
                    ByteArray(0)
            )
        }

        fun buildConfigurationDataStructure(config: Configuration): GTXValue { // TODO

            val gConfig = mutableMapOf<String, GTXValue>()
            // Get all elements in the configuration
            for (key in config.getKeys()) {
                println(key)
                val elements = key.split(".")
//                println(elements)
                // Per each element, insert it into a dictionary
                var currentVal = mutableMapOf<String, GTXValue>()
                for (i in elements.size-1..0) {
                    println(i) // TODO
                    if(elements.size-1 == i) currentVal.put(elements[i], gtx(config.getString(key)))
                    else currentVal.put(elements[i], gtx(currentVal))
                }
                println(currentVal)
                println("--")

            }
            println(gConfig)
            return gtx("")
        }
    }
}

val DummyBaseBlockchainConfigurationData =
        BaseBlockchainConfigurationData.readFromCommonsConfiguration(MapConfiguration(Properties()))