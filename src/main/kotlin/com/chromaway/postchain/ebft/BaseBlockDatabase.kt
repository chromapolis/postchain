package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.ManagedBlockBuilder
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.MultiSigBlockWitnessBuilder
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.TransactionStatus
import mu.KLogging
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

typealias Operation = () -> Unit

class BaseBlockDatabase(private val engine: BlockchainEngine, private val blockQueries: BlockQueries, val nodeIndex: Int) : BlockDatabase {
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            SynchronousQueue<Runnable>(), ThreadFactory {
        val t = Thread(it, "${nodeIndex}-BaseBlockDatabaseWorker")
        t.isDaemon = true // So it can't block the JVM from exiting if still running
        t
    })
    private var blockBuilder: ManagedBlockBuilder? = null
    private var witnessBuilder: MultiSigBlockWitnessBuilder? = null

    companion object : KLogging()

    fun stop() {
        logger.debug("BaseBlockDatabase $nodeIndex stopping")
        executor.shutdownNow()
        maybeRollback()
    }

    private fun <RT> runOp(op: () -> RT): Promise<RT, Exception> {
        val deferred = deferred<RT, Exception>()
        logger.trace("BaseBlockDatabase $nodeIndex putting a job")
        executor.execute({
            try {
                val res = op()
                deferred.resolve(res)
            } catch (e: Exception) {
                deferred.reject(e)
            }
        })
        return deferred.promise
    }

    private fun maybeRollback() {
        logger.trace("BaseBlockDatabase $nodeIndex maybeRollback.")
        if (blockBuilder != null) {
            logger.debug("BaseBlockDatabase $nodeIndex blockBuilder is not null.")
        }
        blockBuilder?.rollback()
        blockBuilder = null
        witnessBuilder = null
    }

    override fun addBlock(block: BlockDataWithWitness): Promise<Unit, Exception> {
        return runOp {
            engine.addBlock(block)
        }
    }


    override fun loadUnfinishedBlock(block: BlockData): Promise<Signature, Exception> {
        return runOp {
            maybeRollback()
            blockBuilder = engine.loadUnfinishedBlock(block)
            witnessBuilder = blockBuilder!!.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
            witnessBuilder!!.getMySignature()
        }
    }

    override fun commitBlock(signatures: Array<Signature?>): Promise<Unit, Exception> {
        return runOp {

            // TODO: process signatures
            blockBuilder!!.commit(witnessBuilder!!.getWitness())
            blockBuilder = null
            witnessBuilder = null
        }
    }

    override fun buildBlock(): Promise<Pair<BlockData, Signature>, Exception> {
        return runOp {
            maybeRollback()
            blockBuilder = engine.buildBlock()
            witnessBuilder = blockBuilder!!.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
            Pair(blockBuilder!!.getBlockData(), witnessBuilder!!.getMySignature())
        }
    }

    override fun verifyBlockSignature(s: Signature): Boolean {
        if (witnessBuilder == null) {
            return false
        }
        return try {
            witnessBuilder!!.applySignature(s)
            true
        } catch (e: Exception) {
            logger.debug("Signature invalid", e)
            false
        }
    }

    override fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception> {
        return blockQueries.getBlockSignature(blockRID)
    }

    override fun getBlockAtHeight(height: Long): Promise<BlockDataWithWitness, Exception> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}