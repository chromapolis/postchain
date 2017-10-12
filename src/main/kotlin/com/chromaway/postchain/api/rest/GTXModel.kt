package com.chromaway.postchain.api.rest

import com.chromaway.postchain.base.TransactionEnqueuer
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.gtx.GTXBlockchainConfiguration
import com.chromaway.postchain.gtx.GTXModule
import com.chromaway.postchain.gtx.GTXValue

class GTXModel(txEnqueuer: TransactionEnqueuer,
               transactionFactory: TransactionFactory,
               blockQueries: BlockQueries,
               blockchainConfiguration: BlockchainConfiguration
) : PostchainModel(txEnqueuer, transactionFactory, blockQueries) {
    val module: GTXModule
    private val gson = make_gtx_gson()

    init {
        val conf = blockchainConfiguration as GTXBlockchainConfiguration
        module = conf.module
    }

    override fun query(query: Query): QueryResult {
        val gtxQuery = gson.fromJson<GTXValue>(query.json, GTXValue::class.java)
        val gtxResult = blockQueries.runQuery { ctxt ->
            module.query(ctxt, "wow", gtxQuery)
        }
        val jsonResult = gson.toJson(gtxResult.get())
        return QueryResult(jsonResult)
    }

}