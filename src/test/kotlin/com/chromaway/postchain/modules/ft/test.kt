package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.withWriteConnection
import com.chromaway.postchain.baseStorage
import com.chromaway.postchain.gtx.GTXSchemaManager
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.junit.Test
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
        val configs = Configurations()
        val conf = configs.properties(File("config.properties"))
        val storage = baseStorage(conf, 0, true)

        withWriteConnection(storage, 0L) {
            GTXSchemaManager.initializeDB(it)
            module.initializeDB(it)
            true
        }
    }
}