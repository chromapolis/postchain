package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.BaseBlockWitnessBuilder
import com.chromaway.postchain.core.*
import com.chromaway.postchain.base.ManagedBlockBuilder
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.thread

typealias Operation = () -> Unit

class BaseBlockDatabase(val engine: BlockchainEngine) : BlockDatabase {

    private val queue = SynchronousQueue<Operation>()
    @Volatile private var ready = true
    @Volatile private var keepGoing = true
    private var blockBuilder: ManagedBlockBuilder? = null
    private var witnessBuilder: MultiSigBlockWitnessBuilder? = null

    @Synchronized fun stop () {
        keepGoing = false
        queue.put({})
    }

    @Synchronized
    private fun <RT>runOp(op: ()->RT): Promise<RT, Exception> {
        val deferred = deferred<RT, Exception>()
        if (!ready) {
            deferred.reject(Exception("Not ready"))
        } else {
            ready = false
            queue.put({
                try {
                    val res = op()
                    ready = true
                    deferred.resolve(res)
                } catch (e: Exception) {
                    ready = true
                    deferred.reject(e)
                }
            })
        }
        return deferred.promise
    }

    init {
        thread {
            while (keepGoing) {
                val op = queue.take()
                op()
            }
        }
    }

    private fun maybeRollback() {
        if (blockBuilder != null) {
            blockBuilder?.rollback()
            blockBuilder = null
            witnessBuilder = null
        }
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
        if (witnessBuilder != null) {
            try {
                witnessBuilder!!.applySignature(s)
            } catch (e: Exception) {
                return false
            }
            return true
        }
        return false
    }

    override fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getBlockAtHeight(height: Long): Promise<BlockData, Exception> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}