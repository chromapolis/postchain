package net.postchain.modules.esplix

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.modules.ft.ExtraData
import net.postchain.modules.ft.getExtraData

class EsplixCertificateData(val id: String, val name: String, val pubkey: ByteArray,
                            val expires: Long, val authority: ByteArray, val reason: ByteArray, extra: ExtraData)

class certificate_op (val config: EsplixConfig, data: ExtOpData): GTXOperation(data) {
    val certData = EsplixCertificateData(
            data.args[0].asString(),
            data.args[1].asString(),
            data.args[2].asByteArray(),
            data.args[3].asInteger(),
            data.args[4].asByteArray(),
            data.args[5].asByteArray(),
            getExtraData(data, 6)
    )
    override fun isCorrect(): Boolean {
        if (!data.signers.any { signer ->
            signer.contentEquals(certData.authority)
        })
            return false

        certData.apply {
            if (id == null) return false
            if (name == null) return false
            if (pubkey == null || pubkey.size != 33) return false
            if (expires < 0) return false
            if (authority == null || authority.size != 33) return false
            if (authority.size != 33) return false
        }
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        config.dbOps.certificate(ctx, data.signers, certData.id, certData.name,
                certData.pubkey,certData.expires,certData.authority,certData.reason)
        return true
    }
}
