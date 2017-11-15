package net.postchain.modules.esplix

import net.postchain.base.CryptoSystem
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.hexStringToByteArray
import org.apache.commons.configuration2.Configuration

open class EsplixConfig (
        val cryptoSystem: CryptoSystem,
        val blockchainRID: ByteArray
)

fun makeBaseEsplixConfig(config: Configuration): EsplixConfig {
    val blockchainRID = config.getString("blockchainrid").hexStringToByteArray()
    val esplixConfig = config.subset("gtx.esplix")

    val cs = SECP256K1CryptoSystem()
    return EsplixConfig(
            cs,
            blockchainRID
    )
}
