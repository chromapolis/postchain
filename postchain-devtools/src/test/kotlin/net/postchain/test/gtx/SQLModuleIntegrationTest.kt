package net.postchain.test.gtx

import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXDataBuilder
import net.postchain.gtx.SQLGTXModuleFactory
import net.postchain.gtx.gtx
import net.postchain.test.IntegrationTest
import org.junit.Test

class SQLModuleIntegrationTest: IntegrationTest() {

    fun makeTx(ownerIdx: Int, key: String, value: String): ByteArray {
        val owner = pubKey(ownerIdx)
        val b = GTXDataBuilder(testBlockchainRID,  arrayOf(owner), myCS)
        b.addOperation("test_set_value",
                arrayOf(gtx(key), gtx(value), gtx(owner))
        )
        b.finish()
        b.sign(myCS.makeSigner(owner, privKey(ownerIdx)))
        return b.serialize()
    }

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("blockchain.1.configurationfactory",
                GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                listOf(SQLGTXModuleFactory::class.qualifiedName))
        configOverrides.setProperty("blockchain.1.gtx.sqlmodules",
                listOf("../postchain-base/src/test/resources/sqlmodule1.sql"))
        val node = createDataLayer(0)

        enqueueTx(node, makeTx(0, "k", "v"), 0)
        buildBlockAndCommit(node)
        enqueueTx(node, makeTx(0, "k", "v2"), 1)
        enqueueTx(node, makeTx(1, "k", "v"), -1)
        buildBlockAndCommit(node)

        verifyBlockchainTransactions(node)
    }

}