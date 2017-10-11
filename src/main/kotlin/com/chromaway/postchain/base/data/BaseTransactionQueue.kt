package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.TransactionEnqueuer
import com.chromaway.postchain.base.TransactionQueue
import com.chromaway.postchain.core.Transaction
import java.util.concurrent.LinkedBlockingQueue

class BaseTransactionQueue: TransactionQueue, TransactionEnqueuer {
    val queue = LinkedBlockingQueue<Transaction>()

    override fun getTransactions(): Array<Transaction> {
        val result = mutableListOf<Transaction>()
        queue.drainTo(result)
        return result.toTypedArray()
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