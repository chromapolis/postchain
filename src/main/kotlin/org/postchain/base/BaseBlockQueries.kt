// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.base

import org.postchain.core.*
import mu.KLogging
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class ConfirmationProof(val txHash: ByteArray, val header: ByteArray, val witness: BlockWitness, val merklePath: MerklePath)

open class BaseBlockQueries(private val blockchainConfiguration: BlockchainConfiguration,
                       private val storage: Storage, private val blockStore: BlockStore,
                       private val chainId: Long, private val mySubjectId: ByteArray) : BlockQueries {
    companion object : KLogging()

    protected fun <T> runOp(operation: (EContext) -> T): Promise<T, Exception> {
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
                throw UserMistake("Trying to get a signature from a node that doesn't have one")
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
                throw ProgrammerMistake("BlockRID does not exist")
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

    override fun query(query: String): Promise<String, Exception> {
        return Promise.ofFail(UserMistake("Queries are not supported"))
    }

    fun getConfirmationProof(txRID: ByteArray): Promise<ConfirmationProof?, Exception> {
        return runOp {
            val material = blockStore.getConfirmationProofMaterial(it, txRID) as ConfirmationProofMaterial
            val decodedWitness = blockchainConfiguration.decodeWitness(material.witness)
            val decodedBlockHeader = blockchainConfiguration.decodeBlockHeader(material.header) as BaseBlockHeader

            val merklePath = decodedBlockHeader.merklePath(material.txHash, material.txHashes)
            ConfirmationProof(material.txHash, material.header, decodedWitness, merklePath)
        }
    }

    override fun getBlockHeader(blockRID: ByteArray): Promise<BlockHeader, Exception> {
        return runOp {
            val headerBytes = blockStore.getBlockHeader(it, blockRID)
            blockchainConfiguration.decodeBlockHeader(headerBytes)
        }
    }

    override fun getBlockAtHeight(height: Long): Promise<BlockDataWithWitness, Exception> {
        return runOp {
            val blockRIDs = blockStore.getBlockRIDs(it, height)
            if (blockRIDs.size == 0) {
                throw UserMistake("No block at height $height")
            }
            if (blockRIDs.size > 1) {
                throw ProgrammerMistake("Multiple blocks at height $height found")
            }
            val blockRID = blockRIDs[0]
            val headerBytes = blockStore.getBlockHeader(it, blockRID)
            val witnessBytes = blockStore.getWitnessData(it, blockRID)
            val txBytes = blockStore.getBlockTransactions(it, blockRID)
            val header = blockchainConfiguration.decodeBlockHeader(headerBytes)
            val witness = blockchainConfiguration.decodeWitness(witnessBytes)

            // txBytes is a stream, and it would be nice to be able to stream all transaction data
            // all the way from database connection to the peer's socket.
            // For now we store it in an ArrayList below.
            // Using this streaming stuff actually makes it worse than using
            // ArrayList, because the data is not really streamed from the database connection
            // but from an intermediary ArrayList in BaseBlockStore. So now we store all
            // the txdata both in the blockStore and in the following list. Not cool.
            val list = ArrayList<ByteArray>()
            txBytes.forEach { list.add(it) }

            BlockDataWithWitness(header, list, witness)
        }
    }
}