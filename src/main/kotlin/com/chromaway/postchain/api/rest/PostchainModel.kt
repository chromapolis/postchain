package com.chromaway.postchain.api.rest

import com.chromaway.postchain.base.ConfirmationProof
import com.chromaway.postchain.base.TransactionEnqueuer
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.core.TransactionStatus.UNKNOWN
import com.chromaway.postchain.core.TransactionStatus.WAITING

class PostchainModel(private val txEnqueuer: TransactionEnqueuer,
                     private val transactionFactory: TransactionFactory,
                     private val blockQueries: BlockQueries): Model {
    override fun postTransaction(tx: ApiTx) {
        txEnqueuer.enqueue(transactionFactory.decodeTransaction(tx.bytes))
    }

    override fun getTransaction(txHash: TxHash): ApiTx? {
        val promise = blockQueries.getTransaction(txHash.bytes)
        val tx = promise.get() ?: return null
        return ApiTx(tx.getRawData().toHex())
    }

    override fun getConfirmationProof(txHash: TxHash): ConfirmationProof? {
        return blockQueries.getConfirmationProof(txHash.bytes).get() ?: return null
    }

    override fun getStatus(txHash: TxHash): ApiStatus {
        if (txEnqueuer.hasTx(txHash.bytes)) return ApiStatus(WAITING)
        val dbStatus = blockQueries.getTxStatus(txHash.bytes).get()
        if (dbStatus == null) return ApiStatus(UNKNOWN)
        return ApiStatus(dbStatus)
    }

    override fun query(query: Query): QueryResult {
        return QueryResult(blockQueries.query(query.json).get())
    }
}