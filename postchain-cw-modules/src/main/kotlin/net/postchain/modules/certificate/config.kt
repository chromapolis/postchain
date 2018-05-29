package net.postchain.modules.certificate

import net.postchain.base.CryptoSystem
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.gtx.GTXValue

class CertificateConfig (
        val cryptoSystem: CryptoSystem,
        val blockchainRID: ByteArray
)

fun makeBaseCertificateConfig(data: GTXValue, blockchainRID: ByteArray): CertificateConfig {
    val cs = SECP256K1CryptoSystem()
    return CertificateConfig(
        cs,
        blockchainRID
    )
}