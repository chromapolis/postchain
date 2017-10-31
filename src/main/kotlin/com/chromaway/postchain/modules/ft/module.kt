package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.gtx.GTXModule
import com.chromaway.postchain.gtx.GTXModuleFactory
import com.chromaway.postchain.gtx.GTXSchemaManager
import com.chromaway.postchain.gtx.SimpleGTXModule
import org.apache.commons.configuration2.Configuration
import org.apache.commons.dbutils.QueryRunner
import spark.utils.IOUtils

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
        val moduleName = "chromaway.ft"
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            val r = QueryRunner()
            val schemaSQL = IOUtils.toString(javaClass.getResourceAsStream("schema.sql"))
            r.update(ctx.conn, schemaSQL)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}

class BaseFTModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Configuration): GTXModule {
        return FTModule(makeBaseFTConfig(config))
    }
}
