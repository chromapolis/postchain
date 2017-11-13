// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft

import net.postchain.api.rest.PostchainModel
import net.postchain.api.rest.RestApi
import org.junit.After

open class EbftWithApiIntegrationTest: EbftIntegrationTest() {

    fun createSystem(count: Int) {
        createEbftNodes(count)
    }
}