package net.postchain.modules.esplix

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.modules.ft.ExtraData
import net.postchain.modules.ft.getExtraData

class EsplixPostMessageData(val callIndex: Int, val messageID: ByteArray, val prevID: ByteArray, val payload: ByteArray, extra: ExtraData)

class post_message_op (val config: EsplixConfig, data: ExtOpData): GTXOperation(data) {
    val messageData = EsplixPostMessageData(
            data.args[0].asInteger().toInt(), //CallIndex
            config.cryptoSystem.digest(
                    data.args[1].asByteArray()+
                            data.args[2].asByteArray()+
                            data.signers.reduce{it, acc -> it + acc}),
            data.args[1].asByteArray(), //prevID
            data.args[2].asByteArray(), //payload
            getExtraData(data,3))

    override fun isCorrect(): Boolean {
        if (data.args.size < 3)
            return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        config.dbOps.postMessage(ctx,
                ctx.txIID,
                messageData.messageID,
                messageData.prevID,
                messageData.callIndex,
                messageData.payload)
        return true
    }
}
