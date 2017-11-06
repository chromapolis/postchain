// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx

import net.postchain.core.EContext
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

object GTXSchemaManager {
    private val r = QueryRunner()
    private val nullableLongRes = ScalarHandler<Long?>()

    fun initializeDB(ctx: EContext) {
        r.update(ctx.conn,
                """CREATE TABLE IF NOT EXISTS gtx_module_version
                        (module_name TEXT PRIMARY KEY,
                         version BIGINT NOT NULL)""")
    }

    fun getModuleVersion(ctx: EContext, name: String): Long? {
        return r.query(ctx.conn,
                "SELECT version FROM gtx_module_version WHERE module_name = ?",
                nullableLongRes, name)
    }

    fun setModuleVersion(ctx: EContext, name: String, version: Long) {
        val oldversion = getModuleVersion(ctx, name)

        if (oldversion != null) {
            if (oldversion != version) {
                r.update(ctx.conn,
                        """UPDATE gtx_module_version SET version = ? WHERE module_name = ?""",
                        name, version)
            }
        } else {
            r.update(ctx.conn,
                    """INSERT INTO gtx_module_version (module_name, version)
                    VALUES (?, ?)""",
                    name, version)
        }
    }


}