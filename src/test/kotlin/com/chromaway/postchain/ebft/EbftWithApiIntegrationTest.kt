package com.chromaway.postchain.ebft

import com.chromaway.postchain.api.rest.PostchainModel
import com.chromaway.postchain.api.rest.RestApi
import org.junit.After

open class EbftWithApiIntegrationTest: EbftIntegrationTest() {
    lateinit var apis: List<RestApi>

    fun createSystem(count: Int) {
        createEbftNodes(count)
        apis = ebftNodes.map { ebftNode ->
            val model = PostchainModel(ebftNode.dataLayer.txEnqueuer,
                    ebftNode.dataLayer.blockchainConfiguration.getTransactionFactory(),
                    ebftNode.dataLayer.blockQueries)
            RestApi(model, configOverrides.getInt("api.port", 0), "")
        }
    }

    @After
    fun tearDownApi() {
        apis.forEach { it.stop() }
    }

}