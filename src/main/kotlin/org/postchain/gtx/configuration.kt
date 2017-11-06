// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.gtx

import org.postchain.base.BaseBlockQueries
import org.postchain.base.Storage
import org.postchain.base.data.BaseBlockchainConfiguration
import org.postchain.base.hexStringToByteArray
import org.postchain.base.secp256k1_derivePubKey
import org.postchain.core.*
import nl.komponents.kovenant.Promise
import org.apache.commons.configuration2.Configuration

open class GTXBlockchainConfiguration(chainID: Long, config: Configuration, val module: GTXModule)
    : BaseBlockchainConfiguration(chainID, config) {
    val txFactory = GTXTransactionFactory(blockchainRID, module, cryptoSystem)

    override fun getTransactionFactory(): TransactionFactory {
        return txFactory
    }

    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        GTXSchemaManager.initializeDB(ctx)
        module.initializeDB(ctx)
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries {
        val blockSigningPrivateKey = config.getString("blocksigningprivkey").hexStringToByteArray()
        val blockSigningPublicKey = secp256k1_derivePubKey(blockSigningPrivateKey)

        return object : BaseBlockQueries(this@GTXBlockchainConfiguration, storage, blockStore,
                chainID, blockSigningPublicKey) {
            private val gson = make_gtx_gson()

            override fun query(query: String): Promise<String, Exception> {
                val gtxQuery = gson.fromJson<GTXValue>(query, GTXValue::class.java)
                return runOp {
                    val type = gtxQuery.asDict().get("type") ?: throw UserMistake("Missing query type")
                    val queryResult = module.query(it, type.asString(), gtxQuery)
                    gtxToJSON(queryResult, gson)
                }
            }
        }
    }
}

open class GTXBlockchainConfigurationFactory() : BlockchainConfigurationFactory {
    override fun makeBlockchainConfiguration(chainID: Long, config: Configuration): BlockchainConfiguration {
        return GTXBlockchainConfiguration(chainID, config, createGtxModule(config))
    }

    open fun createGtxModule(config: Configuration): GTXModule {
        val gtxConfig = config.subset("gtx")
        val list = gtxConfig.getStringArray("modules")
        if (list == null || list.isEmpty()) {
            throw UserMistake("Missing GTX module in config. expected property 'blockchain.<chainId>.gtx.modules'")
        }

        fun makeModule(name: String): GTXModule {
            val moduleClass = Class.forName(name)
            val instance = moduleClass.newInstance()
            if (instance is GTXModule) {
                return instance
            } else if (instance is GTXModuleFactory) {
                return instance.makeModule(config)
            } else throw UserMistake("Module class not recognized")
        }

        return if (list.size == 1) {
            makeModule(list[0])
        } else {
            val moduleList = list.map(::makeModule)
            val allowOverrides = gtxConfig.getBoolean("allowoverrides", false)
            CompositeGTXModule(moduleList.toTypedArray(), allowOverrides)
        }
    }
}