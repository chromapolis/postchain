package com.chromaway.postchain.api.rest

import com.chromaway.postchain.base.BaseBlockQueries
import com.chromaway.postchain.base.ConfirmationProof
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.TransactionEnqueuer
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.core.TransactionStatus.UNKNOWN
import com.chromaway.postchain.core.TransactionStatus.WAITING
import com.chromaway.postchain.core.UserMistake

open class PostchainModel(
        val txEnqueuer: TransactionEnqueuer,
        val transactionFactory: TransactionFactory,
        val blockQueries: BaseBlockQueries
) : Model {
    override fun postTransaction(tx: ApiTx) {
        val decodedTransaction = transactionFactory.decodeTransaction(tx.bytes)
        if (!decodedTransaction.isCorrect()) {
            throw UserMistake("Transaction ${decodedTransaction.getRID()} is not correct")
        }
        txEnqueuer.enqueue(decodedTransaction)
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
        if (txEnqueuer.hasTx(txRID.bytes)) return ApiStatus(WAITING)
        val dbStatus = blockQueries.getTxStatus(txRID.bytes).get()
        if (dbStatus == null) return ApiStatus(UNKNOWN)
        return ApiStatus(dbStatus)
    }

    override fun query(query: Query): QueryResult {
        return QueryResult(blockQueries.query(query.json).get())
    }
}