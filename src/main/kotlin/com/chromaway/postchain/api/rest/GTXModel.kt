package com.chromaway.postchain.api.rest

import com.chromaway.postchain.base.TransactionEnqueuer
import com.chromaway.postchain.core.BlockQueries
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.gtx.GTXBlockchainConfiguration
import com.chromaway.postchain.gtx.GTXModule
import com.chromaway.postchain.gtx.GTXValue

class GTXModel(txEnqueuer: TransactionEnqueuer,
               transactionFactory: TransactionFactory,
               blockQueries: BlockQueries,
               blockchainConfiguration: GTXBlockchainConfiguration
) : PostchainModel(txEnqueuer, transactionFactory, blockQueries) {
    val module: GTXModule = blockchainConfiguration.module
    private val gson = make_gtx_gson()

    override fun query(query: Query): QueryResult {
        val gtxQuery = gson.fromJson<GTXValue>(query.json, GTXValue::class.java)
        val gtxResult = blockQueries.runQuery { ctxt ->
            module.query(ctxt, "wow", gtxQuery)
        }
        return QueryResult(gtxToJSON(gtxResult.get(), gson))
    }

}