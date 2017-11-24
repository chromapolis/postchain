package net.postchain.modules.esplix

import net.postchain.core.EContext

class CertificateEntry(val id: String, val name: String, val pubkey: ByteArray,
                       val expires: Long, val authority: ByteArray, val reason: ByteArray)

class MessageEntry(val gtx: ByteArray, val gtx_id: ByteArray, val callIndex: Array<Int>)

interface EsplixDBOps {
    fun getCertificates(ctx: EContext, id: ByteArray, authority: ByteArray?): List<CertificateEntry>
    fun getMessages(ctx: EContext, chainID: ByteArray, sinceMessageID: ByteArray?, maxHits: Long): List<MessageEntry>

    fun createChain(ctx: EContext, chainID: ByteArray, nonce: ByteArray, txiid: Long, callIndex: Int, payload: ByteArray): Long
    fun postMessage(ctx: EContext, txiid: Long, messageID: ByteArray, prevID: ByteArray, callIndex: Int, payload: ByteArray)
    fun certificate(ctx: EContext, signers: Array<ByteArray>, id: String, name: String, pubkey: ByteArray,
                    expires: Long, authority: ByteArray, reason: ByteArray)
}
