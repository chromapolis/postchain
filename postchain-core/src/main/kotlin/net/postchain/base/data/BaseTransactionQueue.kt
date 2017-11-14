// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import net.postchain.core.*
import java.util.concurrent.LinkedBlockingQueue

class ComparableTransaction(val tx: Transaction) {
    override fun equals(other: Any?): Boolean {
        if (other is ComparableTransaction) {
            return tx.getRID().contentEquals(other.tx.getRID())
        }
        return false
    }

    override fun hashCode(): Int {
        return tx.getRID().hashCode()
    }
}

val MAX_REJECTED = 1000

class BaseTransactionQueue(queueCapacity: Int = 1000): TransactionQueue {

    val queue = LinkedBlockingQueue<ComparableTransaction>(queueCapacity)
    val taken = mutableListOf<ComparableTransaction>()
    val rejects = object: LinkedHashMap<ByteArrayKey, Exception?>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ByteArrayKey, java.lang.Exception?>?): Boolean {
            return size > MAX_REJECTED
        }
    }

    @Synchronized
    override fun takeTransaction(): Transaction? {
        val tx = queue.poll()
        if (tx != null) {
            taken.add(tx)
            return tx.tx
        }
        else return null
    }

    override fun getTransactionQueueSize(): Int {
        return queue.size
    }

    @Synchronized
    override fun enqueue(tx: Transaction): Boolean {
        val comparableTx = ComparableTransaction(tx)
        if (!queue.contains(comparableTx)) {
            try {
                if (tx.isCorrect()) {
                    return queue.offer(comparableTx)
                } else {
                    rejectTransaction(tx, null)
                }
            } catch (e: UserMistake) {
                rejectTransaction(tx, e)
            }
        }
        return false
    }

    @Synchronized
    override fun getTransactionStatus(txHash: ByteArray): TransactionStatus {
        if (queue.find({it.tx.getRID().contentEquals(txHash)}) != null) {
            return TransactionStatus.WAITING
        } else if (taken.find({it.tx.getRID().contentEquals(txHash)}) != null) {
            return TransactionStatus.WAITING
        } else if (ByteArrayKey(txHash) in rejects) {
            return TransactionStatus.REJECTED
        } else
            return TransactionStatus.UNKNOWN
    }

    @Synchronized
    override fun rejectTransaction(tx: Transaction, reason: Exception?) {
        taken.remove(ComparableTransaction(tx))
        rejects.put(ByteArrayKey(tx.getRID()), reason)
    }

    @Synchronized
    override fun removeAll(transactionsToRemove: Collection<Transaction>) {
        queue.removeAll(transactionsToRemove.map{ ComparableTransaction(it) })
        taken.removeAll(transactionsToRemove.map{ ComparableTransaction(it) })
    }
}