// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.EContext
import net.postchain.gtx.*
import org.apache.commons.configuration2.Configuration

/**
 * FT Module including valid operations and queries
 *
 * @property config configuration for the module
 */
class FTModule(val config: FTConfig) : SimpleGTXModule<FTConfig>(
        config,
        mapOf(
                "ft_issue" to ::FT_issue_op,
                "ft_transfer" to ::FT_transfer_op,
                "ft_register" to ::FT_register_op
        ),
        mapOf(
                "ft_account_exists" to ::ftAccountExistsQ,
                "ft_get_balance" to ::ftBalanceQ,
                "ft_get_history" to ::ftHistoryQ
        )
) {

    /**
     * Initialize database
     *
     * @param ctx contextual information
     */
    override fun initializeDB(ctx: EContext) {
        GTXSchemaManager.autoUpdateSQLSchema(
                ctx, 0, javaClass, "/net/postchain/modules/ft/schema.sql", "chromaway.ft"
        )
    }
}

/**
 * Factory to create module
 *
 * @return the module factory
 */
class BaseFTModuleFactory : GTXModuleFactory {
    /**
     * Create FT module with configuration in [config]
     *
     * @param config base configuration for the FT module
     * @return an instance of the module
     */
    override fun makeModule(config: GTXValue, blockchainRID: ByteArray): GTXModule{
        return FTModule(makeBaseFTConfig(config, blockchainRID))
    }
}
