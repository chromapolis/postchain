package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.cryptoSystem
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.*
import com.chromaway.postchain.ebft.BlockchainEngine
import org.apache.commons.configuration2.Configuration
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler
import org.junit.Assert
import org.junit.Test

private val r = QueryRunner()
private val stringReader = ScalarHandler<String>()

class GTXTestOp(u: Unit, opdata: ExtOpData): GTXOperation(opdata) {
    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        data.args[1].asString()
        return data.args[0].asInteger() == 1L
    }

    override fun apply(ctx: TxEContext): Boolean {
        r.update(ctx.conn,
                """INSERT INTO gtx_test_value(tx_iid, value) VALUES (?, ?)""",
                ctx.txIID, data.args[1].asString())
        return true
    }
}

class GTXTestModule: SimpleGTXModule<Unit>(Unit,
        mapOf("gtx_test" to ::GTXTestOp),
        mapOf("gtx_test_get_value" to { u, ctxt, args ->
            gtx(r.query(ctxt.conn,
                    """SELECT value FROM gtx_test_value
                    INNER JOIN transactions ON gtx_test_value.tx_iid = transactions.tx_iid
                    WHERE transactions.tx_rid = ?""",
                    stringReader, args.get("txRID")!!.asByteArray(true)))
        })
) {
    override fun initializeDB(ctx: EContext) {
        val moduleName = this::class.qualifiedName!!
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            r.update(ctx.conn, """
CREATE TABLE gtx_test_value(tx_iid BIGINT PRIMARY KEY, value TEXT NOT NULL)
            """)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}

fun makeTestTx(id: Long, value: String): ByteArray {
    val b = GTXDataBuilder(arrayOf(pubKey(0)), myCS)
    b.addOperation("gtx_test", arrayOf(gtx(id), gtx(value)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

class GTXIntegrationTest: IntegrationTest() {

    @Test
    fun testBuildBlock() {
        val node = createDataLayer(0)

        fun enqueueTx(data: ByteArray): Transaction? {
            try {
                val tx = node.blockchainConfiguration.getTransactionFactory().decodeTransaction(data)
                node.txEnqueuer.enqueue(tx)
                return tx
            } catch (e: Error) {
                println(e)
            }
            return null
        }

        val validTx = enqueueTx(makeTestTx(1, "true"))!!
        enqueueTx(makeTestTx(2, "false"))
        enqueueTx(makeNOPGTX())

        buildBlockAndCommit(node.engine)
        Assert.assertEquals(0, getBestHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        Assert.assertEquals(1, riDsAtHeight0.size)
        Assert.assertArrayEquals(validTx.getRID(), riDsAtHeight0[0])

        val myConf = node.blockchainConfiguration as GTXBlockchainConfiguration
        val value = node.blockQueries.query(
                """{"type"="gtx_test_get_value", "txRID"="${validTx.getRID().toHex()}"}""")
        Assert.assertEquals("\"true\"", value.get())
    }

    override fun makeTestBlockchainConfigurationFactory(): BlockchainConfigurationFactory {
        return object: GTXBlockchainConfigurationFactory() {
            override fun createGtxModule(config: Configuration): GTXModule {
                return GTXTestModule()
            }
        }
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun buildBlockAndCommit(engine: BlockchainEngine) {
        val blockBuilder = engine.buildBlock()
        commitBlock(blockBuilder)
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun commitBlock(blockBuilder: BlockBuilder): BlockWitness {
        val witnessBuilder = blockBuilder.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        Assert.assertNotNull(witnessBuilder)
        val blockData = blockBuilder.getBlockData()
        // Simulate other peers sign the block
        val blockHeader = blockData.header
        var i = 0;
        while (!witnessBuilder.isComplete()) {
            witnessBuilder.applySignature(cryptoSystem.makeSigner(pubKey(i), privKey(i))(blockHeader.rawData))
            i++
        }
        val witness = witnessBuilder.getWitness()
        blockBuilder.commit(witness)
        return witness
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun getTxRidsAtHeight(node: DataLayer, height: Int): Array<ByteArray> {
        val list = node.blockQueries.getBlockRids(height.toLong()).get()
        return node.blockQueries.getBlockTransactionRids(list[0]).get().toTypedArray()
    }

    // TODO: these functions are copy-pasted from BlockchainEngineTest, factor them out
    private fun getBestHeight(node: DataLayer): Long {
        return node.blockQueries.getBestHeight().get()
    }


}