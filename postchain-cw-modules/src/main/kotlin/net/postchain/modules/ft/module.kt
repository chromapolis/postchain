// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.EContext
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.configuration2.Configuration

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

    override fun initializeDB(ctx: EContext) {
        GTXSchemaManager.autoUpdateSQLSchema(
                ctx, 0, javaClass, "/net/postchain/modules/ft/schema.sql", "chromaway.ft"
        )
    }
}

class BaseFTModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Configuration): GTXModule {
        return FTModule(makeBaseFTConfig(config))
    }
}
