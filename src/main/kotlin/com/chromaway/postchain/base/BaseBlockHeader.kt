package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.InitialBlockData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

class BaseBlockHeader(override val rawData: ByteArray, val cryptoSystem: CryptoSystem) : BlockHeader {
    override val prevBlockRID: ByteArray
    override val blockRID: ByteArray
    val blockHeaderRec: com.chromaway.postchain.base.messages.BlockHeader

    init {
        blockHeaderRec = com.chromaway.postchain.base.messages.BlockHeader.der_decode(
                ByteArrayInputStream(rawData)
        )
        prevBlockRID = blockHeaderRec.prevBlockHash
        blockRID = cryptoSystem.digest(rawData)
    }

    companion object Factory {
        @JvmStatic fun make(cryptoSystem: CryptoSystem, iBlockData: InitialBlockData, rootHash: ByteArray, timestamp: Long): BaseBlockHeader {
            val bh = com.chromaway.postchain.base.messages.BlockHeader()
            bh.prevBlockHash = iBlockData.prevBlockRID
            bh.rootHash = rootHash
            bh.height = iBlockData.height
            bh.timestamp = timestamp
            bh.extra = Vector()
            val outs = ByteArrayOutputStream()
            bh.der_encode(outs)
            return BaseBlockHeader(outs.toByteArray(), cryptoSystem)
        }
    }
}
