// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.test

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.baseStorage
import net.postchain.core.*
import net.postchain.ebft.BlockchainEngine
import net.postchain.getBlockchainConfiguration
import org.apache.commons.configuration2.CompositeConfiguration
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.MapConfiguration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

open class IntegrationTest {
    protected val nodes = mutableListOf<DataLayer>()
    val configOverrides = MapConfiguration(mutableMapOf<String, String>())
    val cryptoSystem = SECP256K1CryptoSystem()

    companion object : KLogging()

    fun privKey(index: Int): ByteArray {
        // private key index 0 is all zeroes except byte 16 which is 1
        // private key index 12 is all 12:s except byte 16 which is 1
        // reason for byte16=1 is that private key cannot be all zeroes
        return ByteArray(32, { if (it == 16) 1.toByte() else index.toByte() })
    }

    fun privKeyHex(index: Int): String {
        return privKey(index).toHex()
    }

    fun pubKey(index: Int): ByteArray {
        return secp256k1_derivePubKey(privKey(index))
    }

    fun pubKeyHex(index: Int): String {
        return pubKey(index).toHex()
    }

    class DataLayer(val engine: BlockchainEngine,
                    val txQueue: TransactionQueue,
                    val blockchainConfiguration: BlockchainConfiguration,
                    val storage: Storage, val blockQueries: BaseBlockQueries) {
        fun close() {
            storage.close()
        }
    }

    open class TestBlockchainConfiguration(chainID: Long, config: Configuration) : BaseBlockchainConfiguration(chainID, config) {
        val transactionFactory = TestTransactionFactory()

        override fun getTransactionFactory(): TransactionFactory {
            return transactionFactory
        }
    }

    class TestTransactionFactory : TransactionFactory {
        val specialTxs = mutableMapOf<Int, Transaction>()

        override fun decodeTransaction(data: ByteArray): Transaction {
            val id = DataInputStream(data.inputStream()).readInt()
            if (specialTxs.containsKey(DataInputStream(data.inputStream()).readInt())) {
                return specialTxs[id]!!
            }
            val result = TestTransaction(id)
            assertArrayEquals(result.getRawData(), data)
            return result
        }
    }

    open class TestTransaction(val id: Int, val good: Boolean = true, val correct: Boolean = true) : Transaction {
        override fun isCorrect(): Boolean {
            return correct
        }

        override fun apply(ctx: TxEContext): Boolean {
            return good
        }

        override fun getRawData(): ByteArray {
            return bytes(40)
        }

        private fun bytes(length: Int): ByteArray {
            val byteStream = ByteArrayOutputStream(length)
            val out = DataOutputStream(byteStream)
            for (i in 0 until length/4) {
                out.writeInt(id)
            }
            out.flush()
            return byteStream.toByteArray()
        }

        override fun getRID(): ByteArray {
            return bytes(32)
        }

        override fun getHash(): ByteArray {
            return getRID().reversed().toByteArray()
        }
    }

    class UnexpectedExceptionTransaction(id: Int): TestTransaction(id) {
        override fun apply(ctx: TxEContext): Boolean {
            throw RuntimeException("Expected exception")
        }
    }

    inner class ErrorTransaction(id: Int, private val applyThrows: Boolean, private val isCorrectThrows: Boolean) : TestTransaction(id) {
        override fun isCorrect(): Boolean {
            if (isCorrectThrows) throw UserMistake("Thrown from isCorrect()")
            return true
        }

        override fun apply(ctx: TxEContext): Boolean {
            if (applyThrows) throw UserMistake("Thrown from apply()")
            return true
        }
    }
/*
    class TestTxQueue : TransactionQueue {
        private val q = ArrayList<Transaction>()

        override fun dequeueTransactions(): Array<Transaction> {
            val result = Array(q.size, { q[it] })
            q.clear()
            return result
        }

        override fun peekTransactions(): List<Transaction> {
            return q
        }

        fun add(tx: Transaction) {
            q.add(tx)
        }

        override fun removeAll(transactionsToRemove: Collection<Transaction>) {
            q.removeAll(transactionsToRemove)
        }
    }
    */

    // PeerInfos must be shared between all nodes because
    // a listening node will update the PeerInfo port after
    // ServerSocket is created.
    var peerInfos: Array<PeerInfo>? = null

    @After
    fun tearDown() {
        nodes.forEach { it.close() }
        nodes.clear()
        logger.debug("Closed nodes")
        peerInfos = null
        configOverrides.clear()
    }

    protected fun createEngines(count: Int): Array<DataLayer> {
        return Array(count, { createDataLayer(it, count) })
    }

    protected fun createConfig(nodeIndex: Int, nodeCount: Int = 1): Configuration {
        val configs = Configurations()
        val baseConfig = configs.properties(File("config.properties"))
        baseConfig.listDelimiterHandler = DefaultListDelimiterHandler(',')
        val chainId = baseConfig.getLong("activechainids")
        baseConfig.setProperty("blockchain.$chainId.signers", Array(nodeCount, { pubKeyHex(it) }).reduce({ acc, value -> "$acc,$value" }))
        // append nodeIndex to schema name
        baseConfig.setProperty("database.schema", baseConfig.getString("database.schema") + nodeIndex)
        baseConfig.setProperty("database.wipe", true)
        baseConfig.setProperty("blockchain.$chainId.blocksigningprivkey", privKeyHex(nodeIndex))
        for (i in 0 until nodeCount) {
            baseConfig.setProperty("node.$i.id", "node$i")
            baseConfig.setProperty("node.$i.host", "127.0.0.1")
            baseConfig.setProperty("node.$i.port", "0")
            baseConfig.setProperty("node.$i.pubkey", pubKeyHex(i))
        }
        baseConfig.setProperty("blockchain.$chainId.testmyindex", nodeIndex)
        val composite = CompositeConfiguration()
        composite.addConfiguration(configOverrides)
        composite.addConfiguration(baseConfig)
        return composite
    }

    protected fun createDataLayer(nodeIndex: Int, nodeCount: Int = 1): DataLayer {

        val config = createConfig(nodeIndex, nodeCount)
        val chainId = config.getLong("activechainids")
        val blockchainConfiguration = getBlockchainConfiguration(config.subset("blockchain.$chainId"), chainId)
        val storage = baseStorage(config, nodeIndex)
        val txQueue = BaseTransactionQueue()
        val blockQueries = blockchainConfiguration.makeBlockQueries(storage)
        val strategy = OnDemandBlockBuildingStrategy(config,
                blockchainConfiguration,
                blockQueries, txQueue)


        val engine = BaseBlockchainEngine(blockchainConfiguration, storage,
                chainId, txQueue, strategy
        )

        engine.initializeDB()

        val node = DataLayer(engine,
                txQueue,
                blockchainConfiguration, storage,
                blockQueries as BaseBlockQueries)
        // keep list of nodes to close after test
        nodes.add(node)
        return node
    }


    protected fun createBasePeerCommConfiguration(nodeCount: Int, myIndex: Int): BasePeerCommConfiguration {
        val peerInfos = createPeerInfos(nodeCount)
        val privKey = privKey(myIndex)
        return BasePeerCommConfiguration(peerInfos, myIndex, SECP256K1CryptoSystem(), privKey)
    }

    fun createPeerInfos(nodeCount: Int): Array<PeerInfo> {
        if (peerInfos == null) {
            val pubKeysToUse = Array<ByteArray>(nodeCount, { pubKey(it) })
            peerInfos = Array<PeerInfo>(nodeCount, { DynamicPortPeerInfo("localhost", pubKeysToUse[it]) })
        }
        return peerInfos!!
    }

    protected fun arrayOfBasePeerCommConfigurations(count: Int): Array<BasePeerCommConfiguration> {
        return Array(count, { createBasePeerCommConfiguration(count, it) })
    }

    protected fun buildBlockAndCommit(engine: BlockchainEngine) {
        val blockBuilder = engine.buildBlock()
        commitBlock(blockBuilder)
    }

    private fun commitBlock(blockBuilder: BlockBuilder): BlockWitness {
        val witnessBuilder = blockBuilder.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        assertNotNull(witnessBuilder)
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

    protected fun getTxRidsAtHeight(node: DataLayer, height: Long): Array<ByteArray> {
        val list = node.blockQueries.getBlockRids(height).get()
        return node.blockQueries.getBlockTransactionRids(list[0]).get().toTypedArray()
    }

    protected fun getBestHeight(node: DataLayer): Long {
        return node.blockQueries.getBestHeight().get()
    }
}



class TestBlockchainConfigurationFactory : BlockchainConfigurationFactory {

    override fun makeBlockchainConfiguration(chainID: Long, config: Configuration):
            BlockchainConfiguration {
        return IntegrationTest.TestBlockchainConfiguration(chainID, config)
    }
}