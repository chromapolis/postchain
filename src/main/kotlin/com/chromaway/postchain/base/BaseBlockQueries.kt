package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.MultiSigBlockWitness
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.UserError
import mu.KLogging
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

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
            val height = blockStore.getBlockHeight(ctx, blockRID)
            if (height == null) {
                throw UserError("Trying go get signature of non-existing block")
            }
            val witnessData = blockStore.getWitnessData(ctx, height)
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
}