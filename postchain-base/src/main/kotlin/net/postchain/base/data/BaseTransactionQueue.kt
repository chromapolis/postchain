// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import mu.KLogging
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

class BaseTransactionQueue(queueCapacity: Int = 2500): TransactionQueue {

    companion object : KLogging()

    val queue = LinkedBlockingQueue<ComparableTransaction>(queueCapacity)
    val queueSet = HashSet<ByteArrayKey>()
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
            queueSet.remove(ByteArrayKey(tx.tx.getRID()))
            return tx.tx
        }
        else return null
    }

    override fun getTransactionQueueSize(): Int {
        return queue.size
    }

    override fun enqueue(tx: Transaction): Boolean {
        val rid = ByteArrayKey(tx.getRID())
        synchronized(this) {
            if (queueSet.contains(rid)) return false
        }

        val comparableTx = ComparableTransaction(tx)
        try {
            if (tx.isCorrect())
                synchronized(this) {
                    if (queueSet.contains(rid)) return false
                    if (queue.offer(comparableTx)) {
                        logger.debug("Enqueued tx ${rid}")
                        queueSet.add(rid)
                        return true
                    } else {
                        logger.debug("Skipping tx ${rid}, overloaded")
                        return false
                    }
                } else {
                logger.debug("Tx ${rid} didn't pass the check")
                rejectTransaction(tx, null)
            }
        } catch (e: UserMistake) {
            logger.debug("Tx ${rid} didn't pass the check")
            rejectTransaction(tx, e)
        }
        return false
    }

    @Synchronized
    override fun getTransactionStatus(txHash: ByteArray): TransactionStatus {
        val rid = ByteArrayKey(txHash)
        if (queueSet.contains(rid)) {
            return TransactionStatus.WAITING
        } else if (taken.find({it.tx.getRID().contentEquals(txHash)}) != null) {
            return TransactionStatus.WAITING
        } else if (rid in rejects) {
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
        queueSet.removeAll(transactionsToRemove.map { ByteArrayKey(it.getRID()) })
        taken.removeAll(transactionsToRemove.map{ ComparableTransaction(it) })
    }
}