package com.chromaway.postchain.gtx

import com.chromaway.postchain.api.rest.RestApi
import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.configurations.GTXTestModule
import com.chromaway.postchain.configurations.SingleNodeGtxBlockchainConfigurationFactory
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.ebft.EbftIntegrationTest
import com.chromaway.postchain.ebft.EbftWithApiIntegrationTest
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