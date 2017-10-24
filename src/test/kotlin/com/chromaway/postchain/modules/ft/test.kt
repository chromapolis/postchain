package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.SECP256K1CryptoSystem
import org.junit.Test
import org.junit.Assert
import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.data.BaseStorage
import com.chromaway.postchain.base.withWriteConnection
import com.chromaway.postchain.gtx.GTXSchemaManager
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.File

class ModuleTest: IntegrationTest() {

    @Test
    fun testModule() {
        val config = FTConfig(
                FTIssueRules(arrayOf(), arrayOf()),
                FTTransferRules(arrayOf(), arrayOf(), false),
                FTRegisterRules(arrayOf(), arrayOf()),
                SimpleAccountResolver(
                        mapOf(1 to Pair(::BasicAccount, simpleOutputAccount))
                ),
                BaseDBOps(),
                SECP256K1CryptoSystem()
        )
        val module = FTModule(config)

        val storage = makeBaseStorage()

        withWriteConnection(storage, 0L) {
            GTXSchemaManager.initializeDB(it)
            module.initializeDB(it)
            true
        }
    }

    // TODO: factor out
    private fun makeBaseStorage(): BaseStorage {
        val configs = Configurations()
        val config = configs.properties(File("config.properties"))
        config.listDelimiterHandler = DefaultListDelimiterHandler(',')

        val writeDataSource = createBasicDataSource(config, true)
        writeDataSource.maxTotal = 1

        val readDataSource = createBasicDataSource(config)
        readDataSource.defaultAutoCommit = true
        readDataSource.maxTotal = 2
        readDataSource.defaultReadOnly = true

        return  BaseStorage(writeDataSource, readDataSource, 0)
    }

}