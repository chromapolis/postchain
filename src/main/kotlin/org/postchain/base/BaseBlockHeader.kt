// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.base

import org.postchain.core.BlockHeader
import org.postchain.core.InitialBlockData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

class BaseBlockHeader(override val rawData: ByteArray, private val cryptoSystem: CryptoSystem) : BlockHeader {
    override val prevBlockRID: ByteArray
    override val blockRID: ByteArray
    val timestamp: Long get() = blockHeaderRec.timestamp
    val blockHeaderRec: org.postchain.base.messages.BlockHeader

    init {
        blockHeaderRec = org.postchain.base.messages.BlockHeader.der_decode(
                ByteArrayInputStream(rawData)
        )
        prevBlockRID = blockHeaderRec.prevBlockHash
        blockRID = cryptoSystem.digest(rawData)
    }

    companion object Factory {
        @JvmStatic fun make(cryptoSystem: CryptoSystem, iBlockData: InitialBlockData, rootHash: ByteArray, timestamp: Long): BaseBlockHeader {
            val bh = org.postchain.base.messages.BlockHeader()
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

    fun merklePath(txHash: ByteArray, txHashes: Array<ByteArray>): MerklePath {
        return merklePath(cryptoSystem, txHashes, txHash)
    }

    fun validateMerklePath(merklePath: MerklePath, targetTxHash: ByteArray): Boolean {
        return validateMerklePath(cryptoSystem, merklePath, targetTxHash, blockHeaderRec.rootHash)
    }
}
