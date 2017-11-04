package com.chromaway.postchain.ebft

import com.chromaway.postchain.api.rest.PostchainModel
import com.chromaway.postchain.api.rest.RestApi
import org.junit.After

open class EbftWithApiIntegrationTest: EbftIntegrationTest() {

    fun createSystem(count: Int) {
        createEbftNodes(count)
    }
}