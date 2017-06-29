package com.chromaway.postchain.ebft

import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.storeBlock
import com.chromaway.postchain.ebft.messages.BlockData
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.sql.DriverManager
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.thread

class BlockDatabaseImpl(val bc: BlockchainConfiguration) {

    fun addBlock(block: BlockDataWithWitness) {
        val conn = DriverManager.getConnection("jdbc:h2:mem:test")
        storeBlock(conn, bc, block)
        conn.close()
    }
}

typealias Operation = () -> Unit;


class BlockDatabaseWrapper(val bc: BlockchainConfiguration) : BlockDatabase {
    val impl = BlockDatabaseImpl(bc)
    val queue = SynchronousQueue<Operation>()
    @Volatile private var ready = true
    @Volatile private var keepGoing = true

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

    override fun addBlock(block: BlockDataWithWitness): Promise<Unit, Exception> {
        return runOp { impl.addBlock(block) }
    }


/*    fun loadUnfinishedBlock(block: BlockData): Promise<Signature, Exception>; // returns block signature if successful
    fun commitBlock(signatures: Array<Signature?>): Promise<Void, Exception>;
    fun buildBlock(): Promise<com.chromaway.postchain.core.BlockData, Exception>;

    fun verifyBlockSignature(s: Signature): Boolean;
    fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception>;
    fun getBlockAtHeight(height: Long): Promise<com.chromaway.postchain.core.BlockData, Exception>;*/
}