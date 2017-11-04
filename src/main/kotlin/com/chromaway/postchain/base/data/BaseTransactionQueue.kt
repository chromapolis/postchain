package com.chromaway.postchain.base.data

import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TransactionEnqueuer
import com.chromaway.postchain.core.TransactionQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class ComparableTransaction(val tx: Transaction) {
    override fun equals(other: Any?): Boolean {
        if (other is ComparableTransaction) {
            return tx.getRID().contentEquals(other.tx.getRID())
        }
        return false
    }
}

class BaseTransactionQueue(): TransactionQueue, TransactionEnqueuer {
    val queue = LinkedBlockingQueue<ComparableTransaction>()
    val acceptTxs = AtomicBoolean(true)
    override fun dequeueTransactions(): Array<Transaction> {
        val result = mutableListOf<ComparableTransaction>()
        queue.drainTo(result)
        return result.map({ it.tx }).toTypedArray()
    }

    override fun peekTransactions(): List<Transaction> {
        return queue.toList().map { it.tx }
    }

    override fun enqueue(tx: Transaction) {
        val comparableTx = ComparableTransaction(tx)
        if (!queue.contains(comparableTx)) {
            queue.offer(comparableTx)
        }
    }

    override fun hasTx(txHash: ByteArray): Boolean {
        if (queue.find({it.tx.getRID().contentEquals(txHash)}) != null) {
            return true
        }
        return false
    }

    override fun removeAll(transactionsToRemove: Collection<Transaction>) {
        queue.removeAll(transactionsToRemove.map{ ComparableTransaction(it) })
    }
}