package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.gtx.*
import org.apache.commons.configuration2.Configuration
import org.apache.commons.dbutils.QueryRunner
import java.nio.file.Files
import java.nio.file.Paths

class FTModule(config: FTConfig): SimpleGTXModule<FTConfig>(
        config,
        mapOf(
                "ft_issue" to ::FT_issue_op,
                "ft_transfer" to ::FT_transfer_op,
                "ft_register" to ::FT_register_op
        ),
        mapOf()
) {

    override fun initializeDB(ctx: EContext) {
        val moduleName = "chromaway.ft"
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            val r = QueryRunner()
            val schemaURI = javaClass.getResource("schema.sql").toURI()
            val schemaSQL = String(Files.readAllBytes(Paths.get(schemaURI)))
            r.update(ctx.conn, schemaSQL)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}

/**
 * Convenience class which loads rules from configuration.
 */
class FTBlockchainConfigurationFactory : GTXBlockchainConfigurationFactory() {
    override fun createGtxModule(config: Configuration): GTXModule {
        return FTModule(makeFTConfig(EMPTY_SIGNATURE, config))
    }
}