// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.api.rest

import net.postchain.base.BaseBlockQueries
import net.postchain.base.ConfirmationProof
import net.postchain.base.toHex
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.TransactionStatus.UNKNOWN
import net.postchain.core.UserMistake

open class PostchainModel(
        val txQueue: TransactionQueue,
        val transactionFactory: TransactionFactory,
        val blockQueries: BaseBlockQueries
) : Model {
    override fun postTransaction(tx: ApiTx) {
        val decodedTransaction = transactionFactory.decodeTransaction(tx.bytes)
        if (!decodedTransaction.isCorrect()) {
            throw UserMistake("Transaction ${decodedTransaction.getRID()} is not correct")
        }
        if (!txQueue.enqueue(decodedTransaction))
            throw OverloadedException("Transaction queue is full")
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
            val dbStatus = blockQueries.getTxStatus(txRID.bytes).get()
            if (dbStatus == null) return ApiStatus(UNKNOWN)
            return ApiStatus(dbStatus)
        }
    }

    override fun query(query: Query): QueryResult {
        return QueryResult(blockQueries.query(query.json).get())
    }
}