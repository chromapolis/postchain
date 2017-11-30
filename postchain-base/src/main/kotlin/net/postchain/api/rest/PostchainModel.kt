// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.api.rest

import mu.KLogging
import net.postchain.base.BaseBlockQueries
import net.postchain.base.ConfirmationProof
import net.postchain.common.TimeLog
import net.postchain.common.toHex
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.TransactionStatus.CONFIRMED
import net.postchain.core.TransactionStatus.UNKNOWN
import net.postchain.core.UserMistake

open class PostchainModel(
        val txQueue: TransactionQueue,
        val transactionFactory: TransactionFactory,
        val blockQueries: BaseBlockQueries
) : Model {
    companion object: KLogging()

    override fun postTransaction(tx: ApiTx) {
        var n = TimeLog.startSumConc("PostchainModel.postTransaction().decodeTransaction")
        val decodedTransaction = transactionFactory.decodeTransaction(tx.bytes)
        TimeLog.end("PostchainModel.postTransaction().decodeTransaction", n)

        n = TimeLog.startSumConc("PostchainModel.postTransaction().isCorrect")
        if (!decodedTransaction.isCorrect()) {
            throw UserMistake("Transaction ${decodedTransaction.getRID()} is not correct")
        }
        TimeLog.end("PostchainModel.postTransaction().isCorrect", n)
        n = TimeLog.startSumConc("PostchainModel.postTransaction().enqueue")
        if (!txQueue.enqueue(decodedTransaction))
            throw OverloadedException("Transaction queue is full")
        TimeLog.end("PostchainModel.postTransaction().enqueue", n)
    }

    override fun getTransaction(txRID: TxRID): ApiTx? {
        val promise = blockQueries.getTransaction(txRID.bytes)
        val tx = promise.get() ?: return null
        return ApiTx(tx.getRawData().toHex())
    }

    override fun getConfirmationProof(txRID: TxRID): ConfirmationProof? {
        return blockQueries.getConfirmationProof(txRID.bytes).get() ?: return null
    }

    override fun getStatus(txRID: TxRID): ApiStatus {
        val status = txQueue.getTransactionStatus(txRID.bytes)
        if (status != UNKNOWN)
            return ApiStatus(status)
        else {
            val confirmed = blockQueries.isTransactionConfirmed(txRID.bytes).get()
            return ApiStatus(
                    if (confirmed) CONFIRMED else UNKNOWN
            )
        }
    }

    override fun query(query: Query): QueryResult {
        return QueryResult(blockQueries.query(query.json).get())
    }
}