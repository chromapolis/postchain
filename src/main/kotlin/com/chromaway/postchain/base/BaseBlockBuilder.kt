package com.chromaway.postchain.base

import com.chromaway.postchain.core.*
import java.util.*

class BaseBlockBuilder(val cryptoSystem: CryptoSystem, eContext: EContext, store: BlockStore,
                       txFactory: TransactionFactory)
    : AbstractBlockBuilder(eContext, store, txFactory ) {


    fun computeRootHash(): ByteArray {
        val digests = transactions.map { cryptoSystem.digest(it) }
        return computeMerkleRootHash(cryptoSystem, digests.toTypedArray())
    }

    override fun makeBlockHeader(): BlockHeader {
        // TODO("timestamp")
        return BaseBlockHeader.make(cryptoSystem, iBlockData, computeRootHash(),0);
    }

    override fun validateBlockHeader(bh: BlockHeader): Boolean {
        val bbh = bh as BaseBlockHeader;
        if (!Arrays.equals(bbh.prevBlockRID, iBlockData.prevBlockRID)) return false
        if (bbh.blockHeaderRec.height != iBlockData.height) return false
        if (!Arrays.equals(bbh.blockHeaderRec.rootHash, computeRootHash())) return false

        return true
    }

    override fun validateWitness(w: BlockWitness): Boolean {
        return Arrays.equals( w.blockRID, _blockData!!.header.blockRID )
    }
}