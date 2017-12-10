// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.core.BlockHeader
import net.postchain.core.InitialBlockData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * BaseBlockHeader implements elements and functionality that are necessary to describe and operate on a block header
 *
 * @property rawData DER encoded data including the previous blocks RID ([prevBlockRID]) and [timestamp]
 * @property cryptoSystem An implementation of the various cryptographic primitives to use
 * @property timestamp  Specifies the time that a block was created as the number
 *                      of milliseconds since midnight Januray 1st 1970 UTC
 */
class BaseBlockHeader(override val rawData: ByteArray, private val cryptoSystem: CryptoSystem) : BlockHeader {
    override val prevBlockRID: ByteArray
    override val blockRID: ByteArray
    val timestamp: Long get() = blockHeaderRec.timestamp
    val blockHeaderRec: net.postchain.base.messages.BlockHeader

    init {
        blockHeaderRec = net.postchain.base.messages.BlockHeader.der_decode(
                ByteArrayInputStream(rawData)
        )
        prevBlockRID = blockHeaderRec.prevBlockHash
        blockRID = cryptoSystem.digest(rawData)
    }

    companion object Factory {
        /**
         * Utility to simplify creating an instance of BaseBlockHeader
         *
         * @param cryptoSystem Cryptographic utilities
         * @param iBlockData Initial block data including previous block identifier, timestamp and height
         * @param rootHash Merkle tree root hash
         * @param timestamp timestamp
         * @return Serialized block header
         */
        @JvmStatic fun make(cryptoSystem: CryptoSystem, iBlockData: InitialBlockData, rootHash: ByteArray, timestamp: Long): BaseBlockHeader {
            val bh = net.postchain.base.messages.BlockHeader()
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

    /**
     * Return a Merkle path of a hash in a Merkle tree
     *
     * @param txHash Target hash for which the Merkle path is wanted
     * @param txHashes All hashes are the leaves part of this Merkle tree
     * @return The Merkle path for [txHash]
     */
    fun merklePath(txHash: ByteArray, txHashes: Array<ByteArray>): MerklePath {
        return merklePath(cryptoSystem, txHashes, txHash)
    }

    /**
     * Validate that a Merkle path connects the target hash to the root hash in the block header
     *
     * @param merklePath The Merkle path
     * @param targetTxHash Target hash to validate path for
     * @return Boolean for if hash is part of the Merkle path
     */
    fun validateMerklePath(merklePath: MerklePath, targetTxHash: ByteArray): Boolean {
        return validateMerklePath(cryptoSystem, merklePath, targetTxHash, blockHeaderRec.rootHash)
    }
}
