package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.InitialBlockData
import com.chromaway.postchain.core.MerklePath
import com.chromaway.postchain.core.UserMistake
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.*

class BaseBlockHeader(override val rawData: ByteArray, private val cryptoSystem: CryptoSystem) : BlockHeader {
    override val prevBlockRID: ByteArray
    override val blockRID: ByteArray
    val timestamp: Long get() = blockHeaderRec.timestamp
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
            if (bh.height == 0L) {
                // The first block in a blockchain must encode the blockchainId in
                // the prevBlockHash. BlockchainId is an 8 byte signed integer, of which
                // we only use the non-negative numbers. It is encoded big-endian in the
                // last 8 bytes of the prevBlockHash. To enable future changes, we don't say anything
                // about the other 24 bytes of prevBlockHash of block 0.
                val slice = bh.prevBlockHash.sliceArray(24 until 32)
                val chainId = DataInputStream(slice.inputStream()).readLong()
                if (chainId != iBlockData.chainID.toLong()) {
                    throw UserMistake("Unexpected chainId in prevBlockRID at height 0. Expected ${iBlockData.chainID} got ${chainId}")
                }
            }
            bh.timestamp = timestamp
            bh.extra = Vector()
            val outs = ByteArrayOutputStream()
            bh.der_encode(outs)
            return BaseBlockHeader(outs.toByteArray(), cryptoSystem)
        }
    }

    override fun merklePath(txRID: ByteArray, txRIDs: Array<ByteArray>): MerklePath {
        return com.chromaway.postchain.base.merklePath(cryptoSystem, txRIDs, txRID)
    }

    override fun validateMerklePath(merklePath: MerklePath, targetTxRid: ByteArray): Boolean {
        return validateMerklePath(cryptoSystem, merklePath, targetTxRid, blockHeaderRec.rootHash)
    }
}
