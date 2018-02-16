// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.test.gtx

import net.postchain.configurations.GTXTestModule
import net.postchain.configurations.SingleNodeGtxBlockchainConfigurationFactory
import net.postchain.test.EbftIntegrationTest
import net.postchain.test.IntegrationTest
import org.junit.Test

class GTXClientTestBackendManual: EbftIntegrationTest() {

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("api.port", 7741)
        configOverrides.setProperty("blockchain.1.configurationfactory", SingleNodeGtxBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules", GTXTestModule::class.qualifiedName)
        createEbftNodes(1)
        Thread.sleep(600000)
    }
}