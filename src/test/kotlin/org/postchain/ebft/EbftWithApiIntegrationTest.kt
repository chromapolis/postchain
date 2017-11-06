// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.ebft

import org.postchain.api.rest.PostchainModel
import org.postchain.api.rest.RestApi
import org.junit.After

open class EbftWithApiIntegrationTest: EbftIntegrationTest() {

    fun createSystem(count: Int) {
        createEbftNodes(count)
    }
}