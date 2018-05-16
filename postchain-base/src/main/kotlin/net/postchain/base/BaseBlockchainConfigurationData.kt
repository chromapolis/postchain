package net.postchain.base

import net.postchain.core.BlockchainConfigurationData
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx
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
    }
}

val DummyBaseBlockchainConfigurationData =
        BaseBlockchainConfigurationData.readFromCommonsConfiguration(MapConfiguration(Properties()))