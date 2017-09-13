package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.BlockchainConfigurationFactory
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.MultiSigBlockWitnessBuilder
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.core.UserError
import com.chromaway.postchain.ebft.BaseBlockchainEngine
import com.chromaway.postchain.ebft.BlockchainEngine
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import javax.sql.DataSource

class BlockchainEngineTest {
    class Node(val engine: BlockchainEngine, val txQueue: TestTxQueue,
               val readCtx: EContext, val blockchainConfiguration: BlockchainConfiguration, val dataSources: Array<BasicDataSource>) {
        fun close() {
            dataSources.forEach {
                it.close()
            }
            readCtx.conn.close()
        }
    }

    private val nodes = mutableListOf<Node>()

    private val blockStore = BaseBlockStore() as BlockStore

    @After
    fun tearDown() {
        if (nodes != null) {
            nodes.forEach { it.close() }
        }
        nodes.clear()
    }

    private fun createNodes(count: Int): Array<Node> {
        return Array(count, { createNode(it) });
    }

    private fun createNode(nodeIndex: Int): Node {
        val configs = Configurations()
        val config = configs.properties(File("config.properties"))
        config.listDelimiterHandler = DefaultListDelimiterHandler(',')

        val factory = TestBlockchainConfigurationFactory()

        config.addProperty("signers", pubKeysHex.reduce({ acc, value -> "${acc},${value}" }))
        // append nodeIndex to schema name
        config.setProperty("database.schema", config.getString("database.schema") + nodeIndex);
        var blockchainConfiguration = factory.makeBlockchainConfiguration(1, config)

        val peerInfos = arrayOf(PeerInfo("", 1, pubKeys[nodeIndex]))
        val peerCommConf = BasePeerCommConfiguration(peerInfos, nodeIndex)

        val writeDataSource = createBasicDataSource(config, true)
        writeDataSource.maxTotal = 1

        val readDataSource = createBasicDataSource(config)
        readDataSource.maxTotal = 1
        readDataSource.defaultReadOnly = true

        val storage = BaseStorage(writeDataSource, readDataSource)

        val blockStore = BaseBlockStore()
        val readCtx = storage.openReadConnection(1)

        val txQueue = TestTxQueue()


        val cryptoSystem = SECP256K1CryptoSystem()
        val engine = BaseBlockchainEngine(blockchainConfiguration, peerCommConf,
                storage, 1, cryptoSystem, txQueue)

        val node = Node(engine, txQueue, readCtx, blockchainConfiguration, arrayOf(readDataSource, writeDataSource))
        // keep list of nodes to close after test
        nodes.add(node)
        return node
    }

    private val privKeysHex = arrayOf<String>("3132333435363738393031323334353637383930313233343536373839303131",
            "3132333435363738393031323334353637383930313233343536373839303132",
            "3132333435363738393031323334353637383930313233343536373839303133",
            "3132333435363738393031323334353637383930313233343536373839303134")
    private val privKeys = privKeysHex.map { it.hexStringToByteArray() }

    private val pubKeysHex = arrayOf<String>("0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57",
            "035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9",
            "03f811d3e806e6d093a4bcce49c145ba78f9a4b2fbd167753ecab2a13530b081f8",
            "03ef3f5be98d499b048ba28b247036b611a1ced7fcf87c17c8b5ca3b3ce1ee23a4")
    private val pubKeys = pubKeysHex.map { it.hexStringToByteArray() }

    @Test
    fun testBuildBlock() {
        val node = createNode(0)
        node.txQueue.add(TestTransaction(0))
        buildBlockAndCommit(node.engine)
        assertEquals(0, blockStore.getLastBlockHeight(node.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node.readCtx, 0)
        assertEquals(1, riDsAtHeight0.size)
        assertArrayEquals(TestTransaction(id = 0).getRID(), riDsAtHeight0[0])

        node.txQueue.add(TestTransaction(1))
        node.txQueue.add(TestTransaction(2))
        buildBlockAndCommit(node.engine)
        assertEquals(1, blockStore.getLastBlockHeight(node.readCtx))
        assertTrue(riDsAtHeight0.contentDeepEquals(blockStore.getTxRIDsAtHeight(node.readCtx, 0)))
        val riDsAtHeight1 = blockStore.getTxRIDsAtHeight(node.readCtx, 1)
        assertTrue(riDsAtHeight1.contentDeepEquals(Array<ByteArray>(2,
                { TestTransaction(it + 1).getRID() })))

        // Empty block. All tx will be failing
        node.txQueue.add(TestTransaction(3, good = true, correct = false))
        node.txQueue.add(TestTransaction(4, good = false, correct = true))
        node.txQueue.add(TestTransaction(5, good = false, correct = false))
        node.txQueue.add(ErrorTransaction(6, true, true))
        node.txQueue.add(ErrorTransaction(7, false, true))
        node.txQueue.add(ErrorTransaction(8, true, false))

        buildBlockAndCommit(node.engine)
        assertEquals(2, blockStore.getLastBlockHeight(node.readCtx))
        assertTrue(riDsAtHeight1.contentDeepEquals(blockStore.getTxRIDsAtHeight(node.readCtx, 1)))
        val txRIDsAtHeight2 = blockStore.getTxRIDsAtHeight(node.readCtx, 2)
        assertEquals(0, txRIDsAtHeight2.size)
    }

    @Test
    fun testLoadUnfinishedEmptyBlock() {
        val (node0, node1) = createNodes(2)

        val blockData = createBlockWithTx(node0, 0)

        loadUnfinishedAndCommit(node1, blockData)
        assertEquals(0, blockStore.getLastBlockHeight(node1.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node1.readCtx, 0)
        assertEquals(0, riDsAtHeight0.size)
    }

    @Test
    fun testLoadUnfinishedBlock2tx() {
        val (node0, node1) = createNodes(2)

        val blockData = createBlockWithTx(node0, 2)
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, blockStore.getLastBlockHeight(node1.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node1.readCtx, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array<ByteArray>(2, { TestTransaction(it).getRID() })))
    }

    @Test
    fun testMultipleLoadUnfinishedBlocks() {
        val (node0, node1) = createNodes(2)
        for (i in 0..10) {
            val blockData = createBlockWithTx(node0, 2, i * 2)

            loadUnfinishedAndCommit(node1, blockData)

            assertEquals(i.toLong(), blockStore.getLastBlockHeight(node1.readCtx))
            val riDsAtHeighti = blockStore.getTxRIDsAtHeight(node1.readCtx, i.toLong())
            assertTrue(riDsAtHeighti.contentDeepEquals(Array<ByteArray>(2, { TestTransaction(i as Int * 2 + it).getRID() })))
        }
    }

    @Test
    fun testLoadUnfinishedBlockTxFail() {
        val (node0, node1) = createNodes(2)

        val blockData = createBlockWithTx(node0, 2)

        val bc = node1.blockchainConfiguration as TestBlockchainConfiguration
        // Make the tx invalid on follower. Should discard whole block
        bc.transactionFactory.specialTxs.put(0, ErrorTransaction(0, true, false));
        try {
            loadUnfinishedAndCommit(node1, blockData)
            fail()
        } catch (userError: UserError) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, blockStore.getLastBlockHeight(node1.readCtx))

        bc.transactionFactory.specialTxs.clear()
        // And we can create a new valid block afterwards.
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, blockStore.getLastBlockHeight(node1.readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(node1.readCtx, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array<ByteArray>(2, { TestTransaction(it).getRID() })))
    }

    @Test
    fun testLoadUnfinishedBlockInvalidHeader() {
        val (node0, node1) = createNodes(2)

        val blockData = createBlockWithTx(node0, 2)
        blockData.header.prevBlockRID[0]++
        try {
            loadUnfinishedAndCommit(node1, blockData)
            fail()
        } catch (userError: UserError) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, blockStore.getLastBlockHeight(node1.readCtx))
    }

    private fun createBlockWithTx(node: Node, txCount: Int, startId: Int = 0): BlockData {
        for (i in startId..startId + txCount - 1) {
            node.txQueue.add(TestTransaction(i));
        }
        val blockBuilder = node.engine.buildBlock()
        val blockData = blockBuilder.getBlockData()
        commitBlock(blockBuilder)
        return blockData;
    }


    private class TestBlockchainConfigurationFactory : BlockchainConfigurationFactory {

        override fun makeBlockchainConfiguration(chainID: Long, config: Configuration):
                BlockchainConfiguration {
            return TestBlockchainConfiguration(chainID, config)
        }
    }

    private class TestBlockchainConfiguration(chainID: Long, config: Configuration) : BaseBlockchainConfiguration(chainID, config) {
        val transactionFactory = TestTransactionFactory()

        override fun getTransactionFactory(): TransactionFactory {
            return transactionFactory
        }
    }

    private class TestTransactionFactory : TransactionFactory {
        val specialTxs = mutableMapOf<Byte, Transaction>()

        override fun decodeTransaction(data: ByteArray): Transaction {
            if (specialTxs.containsKey(data[0])) {
                return specialTxs[data[0]]!!;
            }
            val result = TestTransaction(data[0].toInt())
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
            return ByteArray(id + 40, { id.toByte() })
        }

        override fun getRID(): ByteArray {
            return ByteArray(32, { id.toByte() })
        }
    }

    inner class ErrorTransaction(id: Int, val applyThrows: Boolean, val isCorrectThrows: Boolean) : TestTransaction(id) {
        override fun isCorrect(): Boolean {
            if (isCorrectThrows) throw UserError("Thrown from isCorrect()")
            return true
        }

        override fun apply(ctx: TxEContext): Boolean {
            if (applyThrows) throw UserError("Thrown from apply()")
            return true
        }
    }

    class TestTxQueue : TransactionQueue {
        val q = ArrayList<Transaction>()

        override fun getTransactions(): Array<Transaction> {
            val result = Array<Transaction>(q.size, { q[it] })
            q.clear()
            return result
        }

        fun add(tx: Transaction) {
            q.add(tx)
        }
    }

    private fun loadUnfinishedAndCommit(node: Node, blockData: BlockData) {
        val blockBuilder = node.engine.loadUnfinishedBlock(blockData)
        commitBlock(blockBuilder)
    }

    private fun buildBlockAndCommit(engine: BlockchainEngine) {
        val blockBuilder = engine.buildBlock()
        commitBlock(blockBuilder)
    }

    private fun commitBlock(blockBuilder: BlockBuilder) {
        val witnessBuilder = blockBuilder.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        assertNotNull(witnessBuilder)
        blockBuilder.finalize()
        val blockData = blockBuilder.getBlockData()
        // Simulate other peers sign the block
        val blockHeader = blockData.header
        val signatures = privKeys.mapIndexed { index, bytes -> cryptoSystem.makeSigner(pubKeys[index], bytes)(blockHeader.rawData) }
        signatures.forEach { witnessBuilder.applySignature(it) }
        blockBuilder.commit(witnessBuilder.getWitness())
    }

    private fun createBasicDataSource(config: Configuration, wipe: Boolean = false): BasicDataSource {
        val dataSource = BasicDataSource()
        val schema = config.getString("database.schema", "public")
        dataSource.addConnectionProperty("currentSchema", schema)
        dataSource.driverClassName = config.getString("database.DriverClass")
        dataSource.url = config.getString("database.url")
        dataSource.username = config.getString("database.username")
        dataSource.password = config.getString("database.password")
        dataSource.defaultAutoCommit = false
        if (wipe) {
            wipeDatabase(dataSource, schema)

        }

        return dataSource
    }

    private fun wipeDatabase(dataSource: DataSource, schema: String) {
        val queryRunner = QueryRunner()
        val conn = dataSource.connection
        queryRunner.update(conn, "DROP SCHEMA IF EXISTS ${schema} CASCADE")
        queryRunner.update(conn, "CREATE SCHEMA ${schema}")
        // Implementation specific initialization.
        (blockStore as BaseBlockStore).initialize(EContext(conn, 1))
        conn.commit()
        conn.close()
    }
}