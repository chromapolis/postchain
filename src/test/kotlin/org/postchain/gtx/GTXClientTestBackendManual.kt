// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.gtx

import org.postchain.api.rest.RestApi
import org.postchain.base.IntegrationTest
import org.postchain.configurations.GTXTestModule
import org.postchain.configurations.SingleNodeGtxBlockchainConfigurationFactory
import org.postchain.core.Transaction
import org.postchain.ebft.EbftIntegrationTest
import org.postchain.ebft.EbftWithApiIntegrationTest
import org.junit.Assert
import org.junit.Test

class GTXClientTestBackendManual: EbftWithApiIntegrationTest() {

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("api.port", 7741)
        configOverrides.setProperty("blockchain.1.configurationfactory", SingleNodeGtxBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules", GTXTestModule::class.qualifiedName)
        createSystem(1)
        Thread.sleep(600000)
    }
}