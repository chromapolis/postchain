package com.chromaway.postchain

import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.BlockchainConfigurationFactory
import org.apache.commons.configuration2.Configuration


fun getBlockchainConfiguration(config: Configuration, chainId: Long): BlockchainConfiguration {
    val bcfClass = Class.forName(config.getString("configurationfactory"))
    val factory = (bcfClass.newInstance() as BlockchainConfigurationFactory)

    return factory.makeBlockchainConfiguration(chainId, config)
}
