package net.postchain.modules.certificate

import net.postchain.base.CryptoSystem
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import org.apache.commons.configuration2.Configuration

class CertificateConfig (
        val cryptoSystem: CryptoSystem,
        val blockchainRID: ByteArray
)

fun makeBaseCertificateConfig(config: Configuration): CertificateConfig {
    val blockchainRID = config.getString("blockchainrid").hexStringToByteArray()
    val esplixConfig = config.subset("gtx.certificate")

    val cs = SECP256K1CryptoSystem()
    return CertificateConfig(
            cs,
            blockchainRID
    )
}
