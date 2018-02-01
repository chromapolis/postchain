package net.postchain.gtx

import com.google.gson.Gson
import net.postchain.core.ProgrammerMistake
import net.postchain.core.UserMistake
import net.postchain.test.IntegrationTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

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
        enqueueTx(node, makeTx(0, "k2", "v2"), 1)
        enqueueTx(node, makeTx(1, "k", "v"), -1)
        buildBlockAndCommit(node)

        verifyBlockchainTransactions(node)

        assertFailsWith<UserMistake> { node.blockQueries.query("""{tdype: 'test_get_value'}""").get() }

        assertFailsWith<UserMistake> { node.blockQueries.query("""{type: 'non-existing'}""").get() }

        val gson = make_gtx_gson()

        var result = node.blockQueries.query("""{type: 'test_get_value', q_key: 'hello'}""").get()
        var gtxResult = gson.fromJson<GTXValue>(result, GTXValue::class.java)
        assertEquals(0, gtxResult.getSize())

        result = node.blockQueries.query("""{type: 'test_get_value', q_key: 'k'}""").get()
        gtxResult = gson.fromJson<GTXValue>(result, GTXValue::class.java)

        assertEquals(1, gtxResult.getSize())
        val hit0 = gtxResult.get(0).asDict()
        assertNotNull(hit0.get("val"))
        assertEquals("v2", hit0.get("val")!!.asString())
        assertNotNull(hit0.get("owner"))
        assertTrue(pubKey(0).contentEquals(hit0.get("owner")!!.asByteArray(true)))

        println(result)
    }

}