package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.gtx.EMPTY_SIGNATURE
import com.chromaway.postchain.gtx.GTXBlockchainConfigurationFactory
import com.chromaway.postchain.gtx.GTXDataBuilder
import com.chromaway.postchain.gtx.GTXModule
import com.chromaway.postchain.gtx.GTXNull
import com.chromaway.postchain.gtx.encodeGTXValue
import com.chromaway.postchain.gtx.gtx
import com.chromaway.postchain.gtx.myCS
import com.chromaway.postchain.gtx.privKey
import com.chromaway.postchain.gtx.pubKey
import org.apache.commons.configuration2.Configuration
import org.junit.Assert
import org.junit.Test


fun makeTestTx(): ByteArray {

    val accountDesc = encodeGTXValue(gtx(
            gtx(0L),
            GTXNull,
            gtx(pubKey(0))
    ))

    val b = GTXDataBuilder(EMPTY_SIGNATURE, arrayOf(pubKey(0)), myCS)

    b.addOperation("ft_register", arrayOf(gtx(accountDesc)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

class FTIntegrationTest : IntegrationTest() {

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("blockchain.1.configurationfactory", FTModuleGTXBlockchainConfigurationFactory::class.qualifiedName)
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

        val validTx = enqueueTx(makeTestTx())!!

        buildBlockAndCommit(node.engine)
        Assert.assertEquals(0, getBestHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        Assert.assertEquals(1, riDsAtHeight0.size)
        Assert.assertArrayEquals(validTx.getRID(), riDsAtHeight0[0])

        /*val myConf = node.blockchainConfiguration as GTXBlockchainConfiguration
        val value = node.blockQueries.query(
                """{"type"="gtx_test_get_value", "txRID"="${validTx.getRID().toHex()}"}""")
        Assert.assertEquals("\"true\"", value.get())*/
    }
}

class FTModuleGTXBlockchainConfigurationFactory() : GTXBlockchainConfigurationFactory() {
    override fun createGtxModule(config: Configuration): GTXModule {
        val ftConfig = FTConfig(
                FTIssueRules(arrayOf(), arrayOf()),
                FTTransferRules(arrayOf(), arrayOf(), false),
                FTRegisterRules(arrayOf(), arrayOf()),
                SimpleAccountResolver(
                        mapOf(1 to Pair(::BasicAccount, simpleOutputAccount))
                ),
                BaseDBOps(),
                SECP256K1CryptoSystem()
        )
        return FTModule(ftConfig)
    }
}
