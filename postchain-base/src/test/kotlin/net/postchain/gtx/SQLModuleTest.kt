package net.postchain.gtx

import net.postchain.base.withWriteConnection
import net.postchain.baseStorage
import org.apache.commons.configuration2.MapConfiguration
import org.apache.commons.dbutils.QueryRunner
import org.junit.Assert
import org.junit.Test

class SQLModuleTest {
    @Test
    fun testModule() {
        val config = MapConfiguration(mapOf(
                "gtx.sqlmodules" to javaClass.getResource("sqlmodule1.sql").file
        ))

        val mf = SQLGTXModuleFactory()
        val module = mf.makeModule(config)

        val dataConf = MapConfiguration(mapOf(
                "database.driverclass" to "org.postgresql.Driver",
                "database.url" to "jdbc:postgresql://localhost/postchain",
                "database.username" to "postchain",
                "database.password" to "postchain",
                "database.schema" to "testschema",
                "database.wipe" to "true"
        ))

        val r = QueryRunner()
        val storage = baseStorage(dataConf, 0)
        withWriteConnection(storage, 1) {
            GTXSchemaManager.initializeDB(it)
            module.initializeDB(it)
            Assert.assertTrue(module.getOperations().size == 1)
            false
        }
    }



}