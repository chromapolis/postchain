package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.BlockchainConfigurationFactory
import com.chromaway.postchain.core.MultiSigBlockWitnessBuilder
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.core.UserError
import com.chromaway.postchain.ebft.BaseBlockchainEngine
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.commons.configuration2.ex.ConfigurationException
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.sql.DataSource

class SetupTest {

    private class TestBlockchainConfigurationFactory : BlockchainConfigurationFactory {

        override fun makeBlockchainConfiguration(chainID: Long, config: Configuration):
                BlockchainConfiguration {

            return BaseBlockchainConfiguration(chainID, config)
        }
    }

    @Test
    fun apa() {
        assertEquals(1, 2)
    }

    var txIndex = 0

    open inner class TestTransaction(val good: Boolean = true, val correct: Boolean = true, val id: Int = this.txIndex++) : Transaction {
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

    inner class ErrorTransaction(val applyThrows: Boolean, val isCorrectThrows: Boolean) : TestTransaction() {
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
    @Throws(ConfigurationException::class)
    fun setupSystem() {
        /*
1. Manager reads JSON and finds BlockchainConfigurationFactory class name.
2. Manager instantiates a class which implements BlockchainConfigurationFactory interface, and feeds it JSON data.
3. BlockchainConfigurationFactory creates BlockchainConfiguration object.
4. BlockchainConfiguration acts as a block factory and creates a transaction factory, presumably passing it configuration data in some form.
5. TransactionFactory will create Transaction objects according to configuration, possibly also passing it the configuration data.
6. Transaction object can perform its duties according to the configuration it received, perhaps creating sub-objects called transactors and passing them the configuration.
 */
        val configs = Configurations()
        val config = configs.properties(File("config.properties"))
        config.listDelimiterHandler = DefaultListDelimiterHandler(',')

        val factory = TestBlockchainConfigurationFactory()

        config.addProperty("signers", pubKeysHex.reduce({ acc, value -> "${acc},${value}" }))

        val blockchainConfiguration = factory.makeBlockchainConfiguration(1, config)

        val transactionFactory = blockchainConfiguration.getTransactionFactory()

        ///Transaction transaction = transactionFactory.decodeTransaction(new byte[222]);
        val peerInfos = arrayOf(PeerInfo("", 1, kotlin.ByteArray(33)))
        val peerCommConf = BasePeerCommConfiguration(peerInfos, 0)

        val writeDataSource = createBasicDataSource(config, true)
        writeDataSource.maxTotal = 1

        val readDataSource = createBasicDataSource(config)
        readDataSource.initialSize = 5
        readDataSource.maxTotal = 10
        readDataSource.defaultReadOnly = true

        val storage = BaseStorage(writeDataSource, readDataSource)

        val blockStore = BaseBlockStore()
        val readCtx = storage.openReadConnection(1)

        val txQueue = TestTxQueue()

        val cryptoSystem = SECP256K1CryptoSystem()
        val engine = BaseBlockchainEngine(blockchainConfiguration, peerCommConf,
                storage, 1, cryptoSystem, txQueue)

        txQueue.add(TestTransaction())
        buildBlockAndCommit(engine)
        assertEquals(0, blockStore.getLastBlockHeight(readCtx))
        val riDsAtHeight0 = blockStore.getTxRIDsAtHeight(readCtx, 0)
        assertEquals(1, riDsAtHeight0.size)
        assertArrayEquals(TestTransaction(id = 0).getRID(), riDsAtHeight0[0])

        txQueue.add(TestTransaction(true, true))
        txQueue.add(TestTransaction(true, true))
        buildBlockAndCommit(engine)
        assertEquals(1, blockStore.getLastBlockHeight(readCtx))
        assertTrue(riDsAtHeight0.contentDeepEquals(blockStore.getTxRIDsAtHeight(readCtx, 0)))
        val riDsAtHeight1 = blockStore.getTxRIDsAtHeight(readCtx, 1)
        assertTrue(riDsAtHeight1.contentDeepEquals(Array<ByteArray>(2,
                { TestTransaction(id = it + 1).getRID() })))

        // Empty block. All tx will be failing
        txQueue.add(TestTransaction(true, false))
        txQueue.add(TestTransaction(false, true))
        txQueue.add(TestTransaction(false, false))
        txQueue.add(ErrorTransaction(true, true))
        txQueue.add(ErrorTransaction(false, true))
        txQueue.add(ErrorTransaction(true, false))

        buildBlockAndCommit(engine)
        assertEquals(2, blockStore.getLastBlockHeight(readCtx))
        assertTrue(riDsAtHeight1.contentDeepEquals(blockStore.getTxRIDsAtHeight(readCtx, 1)))
        val txRIDsAtHeight2 = blockStore.getTxRIDsAtHeight(readCtx, 2)
        assertEquals(0, txRIDsAtHeight2.size)
    }

    private fun buildBlockAndCommit(engine: BaseBlockchainEngine) {
        val blockBuilder = engine.buildBlock()
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
        conn.commit()
        conn.close()
    }
}