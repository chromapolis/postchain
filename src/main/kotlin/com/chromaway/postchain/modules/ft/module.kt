package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.gtx.GTXSchemaManager
import com.chromaway.postchain.gtx.SimpleGTXModule
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
            r.batch(ctx.conn, schemaSQL, arrayOf())
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}