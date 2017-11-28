package net.postchain.gtx

import net.postchain.test.IntegrationTest
import org.junit.Test

class SQLModuleIntegrationTest: IntegrationTest() {

    fun makeTx(ownerIdx: Int, key: String, value: String): ByteArray {
        val owner = pubKey(ownerIdx)
        val b = GTXDataBuilder(net.postchain.test.gtx.testBlockchainRID, arrayOf(owner), net.postchain.test.gtx.myCS)
        b.addOperation("test_set_value",
                arrayOf(gtx(key), gtx(value), gtx(owner))
        )
        b.finish()
        b.sign(net.postchain.test.gtx.myCS.makeSigner(owner, privKey(ownerIdx)))
        return b.serialize()
    }

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("blockchain.1.configurationfactory",
                GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                listOf(SQLGTXModuleFactory::class.qualifiedName))
        configOverrides.setProperty("blockchain.1.gtx.sqlmodules",
                listOf(javaClass.getResource("sqlmodule1.sql").file))
        val node = createDataLayer(0)

        enqueueTx(node, makeTx(0, "k", "v"), 0)
        buildBlockAndCommit(node)
        enqueueTx(node, makeTx(0, "k", "v2"), 1)
        enqueueTx(node, makeTx(1, "k", "v"), -1)
        buildBlockAndCommit(node)

        verifyBlockchainTransactions(node)
    }

}