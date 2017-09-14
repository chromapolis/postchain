package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.BaseBlockHeader
import com.chromaway.postchain.base.BaseBlockWitnessBuilder
import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.computeMerkleRootHash
import com.chromaway.postchain.core.*
import java.util.*

class BaseBlockBuilder(val cryptoSystem: CryptoSystem, eContext: EContext, store: BlockStore,
                       txFactory: TransactionFactory, val subjects: Array<ByteArray>)
    : AbstractBlockBuilder(eContext, store, txFactory ) {


    fun computeRootHash(): ByteArray {
        val digests = transactions.map { cryptoSystem.digest(it) }
        return computeMerkleRootHash(cryptoSystem, digests.toTypedArray())
    }

    override fun makeBlockHeader(): BlockHeader {
        // TODO("timestamp")
        return BaseBlockHeader.make(cryptoSystem, iBlockData, computeRootHash(), 0)
    }

    override fun validateBlockHeader(bh: BlockHeader): Boolean {
        val bbh = bh as BaseBlockHeader
        if (!Arrays.equals(bbh.prevBlockRID, iBlockData.prevBlockRID)) return false
        if (bbh.blockHeaderRec.height != iBlockData.height) return false
        if (!Arrays.equals(bbh.blockHeaderRec.rootHash, computeRootHash())) return false

        return true
    }

    override fun validateWitness(w: BlockWitness): Boolean {
        if (!(w is MultiSigBlockWitness)) {
            throw ProgrammerError("Invalid BlockWitness impelmentation.")
        }
        val witnessBuilder = getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        for (signature in w.getSignatures()) {
            witnessBuilder.applySignature(signature)
        }
        return witnessBuilder.isComplete()
    }

    override fun getBlockWitnessBuilder(): BlockWitnessBuilder? {
        if (!finalized) {
            throw ProgrammerError("Block is not finalized yet.")
        }
        val requiredSigs: Int
        if (subjects.size == 3) {
            requiredSigs = 3
        } else {
            val maxFailedNodes = Math.floor(((subjects.size - 1) / 3).toDouble())
            //return signers.signers.length - maxFailedNodes;
            requiredSigs = 2 * maxFailedNodes.toInt() + 1
        }

        return BaseBlockWitnessBuilder(cryptoSystem, _blockData!!.header, subjects, requiredSigs)
    }

}