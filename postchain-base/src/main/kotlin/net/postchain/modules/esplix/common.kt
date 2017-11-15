package net.postchain.modules.esplix

import net.postchain.core.EContext
import net.postchain.gtx.GTXValue
import net.postchain.modules.ft.OpEContext

interface EsplixDBOps {
    fun getCertificates(ctx: OpEContext, id: ByteArray, authority: ByteArray?)
    fun getMessages(ctx: OpEContext, chainID: ByteArray, messageID: ByteArray, maxHits: Long)
}
