package net.postchain.test

import net.postchain.base.gtxml.TestType
import net.postchain.common.hexStringToByteArray
import net.postchain.configurations.GTXTestModule
import net.postchain.core.Transaction
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.SQLGTXModuleFactory
import net.postchain.gtx.StandardOpsGTXModule
import net.postchain.gtx.gtxml.GTXMLTransactionParser
import net.postchain.gtx.gtxml.TransactionContext
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.StringReader
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement


class TestLauncher : IntegrationTest() {

    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
            .hexStringToByteArray()
    private val jaxbContext = JAXBContext.newInstance("net.postchain.base.gtxml")

    @Test
    fun runXMLGTXTests() {
        runXMLGTXTests(
                File("tx.xml").readText())
    }

    fun runXMLGTXTests(xml: String): Boolean {
        configOverrides.setProperty(
                "blockchain.1.configurationfactory",
                GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty(
                "blockchain.1.gtx.modules",
                listOf(StandardOpsGTXModule::class.qualifiedName))

        val dataLayer = createDataLayer(0)
        val testType = parseTest(xml)

        val validTxs = mutableListOf<Transaction?>()

        var blockNum = 0L
        testType.block.forEach {
            println("Block $it will be processed")

            it.transaction.forEach {
                println("Transaction $it will be processed")

                val gtxData = GTXMLTransactionParser.parseGTXMLTransaction(
                        it,
                        TransactionContext(blockchainRID))

                validTxs.add(
                        enqueueTx(dataLayer, gtxData.serialize(), blockNum))
            }

            buildBlockAndCommit(dataLayer)
            blockNum++
        }

//        verifyBlockchainTransactions(dataLayer)
        var currentBlockHeight = -1L
        currentBlockHeight += 1
//        buildBlockAndCommit(dataLayer.engine)
        Assert.assertEquals(currentBlockHeight, getBestHeight(dataLayer))
        val ridsAtHeight = getTxRidsAtHeight(dataLayer, currentBlockHeight)
        for (vtx in validTxs) {
            val vtxRID = vtx!!.getRID()
            Assert.assertTrue(ridsAtHeight.any { it.contentEquals(vtxRID) })
        }
        Assert.assertEquals(validTxs.size, ridsAtHeight.size)
        validTxs.clear()


        return false
    }

    private fun parseTest(xml: String): TestType {
        val jaxbElement = jaxbContext
                .createUnmarshaller()
                .unmarshal(StringReader(xml)) as JAXBElement<*>

        return jaxbElement.value as TestType
    }
}