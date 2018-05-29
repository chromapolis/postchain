package net.postchain.modules.esplix_r4

import net.postchain.base.CryptoSystem
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.gtx.GTXValue

open class EsplixConfig (
        val cryptoSystem: CryptoSystem,
        val blockchainRID: ByteArray
)

fun makeBaseEsplixConfig(data: GTXValue, blockchainRID: ByteArray): EsplixConfig {
    val blockchainRID = blockchainRID

    val cs = SECP256K1CryptoSystem()
    return EsplixConfig(
            cs,
            blockchainRID
    )
}
