// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import net.postchain.base.*
import net.postchain.core.*
import java.util.*

open class BaseBlockBuilder(val cryptoSystem: CryptoSystem, eContext: EContext, store: BlockStore,
                            txFactory: TransactionFactory, val subjects: Array<ByteArray>, val blockSigner: Signer)
    : AbstractBlockBuilder(eContext, store, txFactory ) {


    fun computeRootHash(): ByteArray {

        val digests = transactions.map { txFactory.decodeTransaction(it).getHash() }
        return computeMerkleRootHash(cryptoSystem, digests.toTypedArray())
    }

    override fun makeBlockHeader(): BlockHeader {
        return BaseBlockHeader.make(cryptoSystem, iBlockData, computeRootHash(), System.currentTimeMillis())
    }

    override fun validateBlockHeader(bh: BlockHeader): Boolean {
        val bbh = bh as BaseBlockHeader
        if (!Arrays.equals(bbh.prevBlockRID, iBlockData.prevBlockRID)) return false
        if (bbh.blockHeaderRec.height != iBlockData.height) return false
        if (!Arrays.equals(bbh.blockHeaderRec.rootHash, computeRootHash())) return false
        if (bctx.timestamp >= bbh.timestamp) return false

        return true
    }

    override fun validateWitness(w: BlockWitness): Boolean {
        if (!(w is MultiSigBlockWitness)) {
            throw ProgrammerMistake("Invalid BlockWitness impelmentation.")
        }
        val witnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, _blockData!!.header, subjects, getRequiredSigCount())
        for (signature in w.getSignatures()) {
            witnessBuilder.applySignature(signature)
        }
        return witnessBuilder.isComplete()
    }

    override fun getBlockWitnessBuilder(): BlockWitnessBuilder? {
        if (!finalized) {
            throw ProgrammerMistake("Block is not finalized yet.")
        }

        val witnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, _blockData!!.header, subjects, getRequiredSigCount())
        witnessBuilder.applySignature(blockSigner(_blockData!!.header.rawData))
        return witnessBuilder
    }

    protected open fun getRequiredSigCount(): Int {
        val requiredSigs: Int
        if (subjects.size == 1)
            requiredSigs = 1
        else if (subjects.size == 3) {
            requiredSigs = 3
        } else {
            val maxFailedNodes = Math.floor(((subjects.size - 1) / 3).toDouble())
            //return signers.signers.length - maxFailedNodes;
            requiredSigs = 2 * maxFailedNodes.toInt() + 1
        }
        return requiredSigs
    }

}