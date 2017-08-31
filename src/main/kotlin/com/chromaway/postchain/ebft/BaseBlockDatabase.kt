package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.BaseBlockWitnessBuilder
import com.chromaway.postchain.core.*
import com.chromaway.postchain.base.ManagedBlockBuilder
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.thread

typealias Operation = () -> Unit;

class BaseBlockDatabase(val engine: BlockchainEngine, val privKey: ByteArray, val pubKey: ByteArray) : BlockDatabase {

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
            createWitnessBuilderAndSign(block)
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
            val blockData = blockBuilder!!.getBlockData()
            val signature = createWitnessBuilderAndSign(blockData)
            Pair(blockData, signature)
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


    private fun createWitnessBuilderAndSign(blockData: BlockData): Signature {
        val peerInfo = engine.peerCommConfiguration.peerInfo;
        val subjects = Array(peerInfo.size, { i -> peerInfo[i].pubKey });

        var requiredSigs: Int
        if (subjects.size == 3) {
            requiredSigs = 3;
        } else {
            val maxFailedNodes = Math.floor(((subjects.size - 1) / 3).toDouble());
            //return signers.signers.length - maxFailedNodes;
            requiredSigs = 2 * maxFailedNodes.toInt() + 1;
        }

        witnessBuilder = BaseBlockWitnessBuilder(subjects, requiredSigs)
        val signature = signBlock(blockData)
        witnessBuilder!!.applySignature(signature)

        return signature;
    }

    private fun signBlock(blockData: BlockData): Signature {
        val signer = engine.cryptoSystem.makeSigner(pubKey, privKey)
        return signer.invoke(blockData.header.rawData);
    }
}