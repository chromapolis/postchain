package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.cryptoSystem
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.configurations.GTXTestModule
import com.chromaway.postchain.core.*
import com.chromaway.postchain.ebft.BlockchainEngine
import com.chromaway.postchain.gtx.*
import org.apache.commons.configuration2.Configuration
import org.junit.Assert
import org.junit.Test


fun makeTestTx(): ByteArray {

    val accountDesc = encodeGTXValue(gtx(
            gtx(0L),
            GTXNull,
            gtx(pubKey(0))
    ))

    val b = GTXDataBuilder(arrayOf(pubKey(0)), myCS)

    b.addOperation("ft_register", arrayOf(gtx(accountDesc)))
    b.finish()
    b.sign(myCS.makeSigner(pubKey(0), privKey(0)))
    return b.serialize()
}

class FTIntegrationTest: IntegrationTest() {

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

    override fun makeTestBlockchainConfigurationFactory(): BlockchainConfigurationFactory {
        return object: GTXBlockchainConfigurationFactory() {
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
    }


}