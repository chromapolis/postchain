package com.chromaway.postchain.base.data

import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TransactionEnqueuer
import com.chromaway.postchain.core.TransactionQueue
import java.util.concurrent.LinkedBlockingQueue

class BaseTransactionQueue: TransactionQueue, TransactionEnqueuer {
    val queue = LinkedBlockingQueue<Transaction>()

    override fun dequeueTransactions(): Array<Transaction> {
        val result = mutableListOf<Transaction>()
        queue.drainTo(result)
        return result.toTypedArray()
    }

    override fun peekTransactions(): List<Transaction> {
        return queue.toList()
    }

    override fun enqueue(tx: Transaction) {
        queue.offer(tx)
    }

    override fun hasTx(txHash: ByteArray): Boolean {
        if (queue.find({it.getRID().contentEquals(txHash)}) != null) {
            return true
        }
        return false
    }
}