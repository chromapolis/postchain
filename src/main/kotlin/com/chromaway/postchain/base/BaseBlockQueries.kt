package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockWitness
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.MerklePath
import com.chromaway.postchain.core.MultiSigBlockWitness
import com.chromaway.postchain.core.ProgrammerError
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TransactionStatus
import com.chromaway.postchain.core.UserError
import mu.KLogging
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ConfirmationProof(val txRID: ByteArray, val header: ByteArray, val witness: BlockWitness, val merklePath: MerklePath)

class BaseBlockQueries(private val blockchainConfiguration: BlockchainConfiguration,
                       private val storage: Storage, private val blockStore: BlockStore,
                       private val chainId: Int, private val mySubjectId: ByteArray) : BlockQueries {
    companion object : KLogging()

    private fun <T> runOp(operation: (EContext) -> T): Promise<T, Exception> {
        return task {
            val ctx = storage.openReadConnection(chainId)
            try {
                operation(ctx)
            } catch (e: Exception) {
                logger.error("An error occurred", e)
                throw e
            } finally {
                storage.closeReadConnection(ctx)
            }
        }
    }

    override fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception> {
        return runOp({ ctx ->
            val witnessData = blockStore.getWitnessData(ctx, blockRID)
            val witness = blockchainConfiguration.decodeWitness(witnessData) as MultiSigBlockWitness
            val signature = witness.getSignatures().find { it.subjectID.contentEquals(mySubjectId) }
            if (signature == null) {
                throw UserError("Trying to get a signature from a node that doesn't have one")
            }
            signature!!
        })
    }

    override fun getBestHeight(): Promise<Long, Exception> {
        return runOp {
            blockStore.getLastBlockHeight(it)
        }
    }

    override fun getBlockTransactionRids(blockRID: ByteArray): Promise<List<ByteArray>, Exception> {
        return runOp {
            val height = blockStore.getBlockHeight(it, blockRID)
            if (height == null) {
                throw ProgrammerError("BlockRID does not exist")
            }
            blockStore.getTxRIDsAtHeight(it, height).toList()
        }
    }

    override fun getTransaction(txRID: ByteArray): Promise<Transaction?, Exception> {
        return runOp {
            val txBytes = blockStore.getTxBytes(it, txRID)
            if (txBytes == null)
                null
            else
                blockchainConfiguration.getTransactionFactory().decodeTransaction(txBytes)
        }
    }

    override fun getBlockRids(height: Long): Promise<List<ByteArray>, Exception> {
        return runOp {
            blockStore.getBlockRIDs(it, height).toList()
        }
    }

    override fun getTxStatus(txRID: ByteArray): Promise<TransactionStatus?, Exception> {
        return runOp {
            blockStore.getTxStatus(it, txRID)
        }
    }

    override fun query(json: String): Promise<String, Exception> {
        return runOp { blockStore.query(it, json) }
    }

    override fun getConfirmationProof(txRID: ByteArray): Promise<ConfirmationProof?, Exception> {
        return runOp {
            val material = blockStore.getConfirmationProofMaterial(it, txRID)
            val txIds = material.get("txs") as List<ByteArray>
            val header = material.get("header") as ByteArray
            val witness: ByteArray = material.get("witness") as ByteArray
            val decodedWitness = blockchainConfiguration.decodeWitness(witness)
            val decodedBlockHeader = blockchainConfiguration.decodeBlockHeader(header)

            val merklePath = decodedBlockHeader.merklePath(txRID, txIds.toTypedArray())
            ConfirmationProof(txRID, header, decodedWitness, merklePath)
        }
    }
}