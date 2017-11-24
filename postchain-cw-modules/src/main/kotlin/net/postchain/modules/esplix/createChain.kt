package net.postchain.modules.esplix

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.modules.ft.ExtraData
import net.postchain.modules.ft.getExtraData

class EsplixCreateChainData(val chainID: ByteArray, val nonce: ByteArray, val callIndex: Int, val payload: ByteArray, extra: ExtraData)

class create_chain_op (val config: EsplixConfig, data: ExtOpData): GTXOperation(data) {
    val chainData = EsplixCreateChainData(
            data.args[0].asByteArray(),
            data.args[1].asByteArray(),
            data.args[2].asInteger().toInt(),
            data.args[3].asByteArray(),
            getExtraData(data,4)
    )

    override fun isCorrect(): Boolean {
        if (data.args.size < 4)
            return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        config.dbOps.createChain(ctx,
                chainData.chainID,
                chainData.nonce,
                ctx.txIID,
                chainData.callIndex,
                chainData.payload)
        return true
    }
}
