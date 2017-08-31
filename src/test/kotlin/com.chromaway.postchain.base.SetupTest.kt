package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockEContext
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.BlockchainConfigurationFactory
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.ebft.BaseBlockchainEngine
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.ex.ConfigurationException
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.*
import javax.sql.DataSource

class SetupTest {

    private class TestBlockchainConfigurationFactory : BlockchainConfigurationFactory {

        override fun makeBlockchainConfiguration(chainID: Long, properties: Properties): BlockchainConfiguration {

            return BaseBlockchainConfiguration(chainID, properties)
        }
    }

    @Test
    fun apa() {
        Assert.assertEquals(1, 2);
    }

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
        val factory = TestBlockchainConfigurationFactory()
        //
        val blockchainConfiguration = factory.makeBlockchainConfiguration(1, Properties())


        val transactionFactory = blockchainConfiguration.getTransactionFactory()

        ///Transaction transaction = transactionFactory.decodeTransaction(new byte[222]);
        val peerInfos = arrayOf(PeerInfo("", 1, kotlin.ByteArray(33)));
        val peerCommConf = BasePeerCommConfiguration(peerInfos, 0);

        val configs = Configurations()
        val config = configs.properties(File("config.properties"))

        val writeDataSource = createBasicDataSource(config, true)
        writeDataSource.maxTotal = 1

        val readDataSource = createBasicDataSource(config)
        readDataSource.initialSize = 5
        readDataSource.maxTotal = 10
        readDataSource.defaultReadOnly = true;


        val storage = BaseStorage(writeDataSource, readDataSource)


        val txQueue = object : TransactionQueue {
            override fun getTransactions(): Array<Transaction> {
                return arrayOf(object : Transaction {
                    override fun getRawData(): ByteArray {
                        return ByteArray(10)
                    }

                    override fun isCorrect(): Boolean {
                        return true
                    }

                    override fun apply(ctx: BlockEContext): Boolean {
                        return false
                    }
                })
            }
        }

        val engine = BaseBlockchainEngine(blockchainConfiguration, peerCommConf,
                storage, 1, SECP256K1CryptoSystem(), txQueue)

        val blockBuilder = engine.buildBlock()
        //        blockBuilder.getBlockWitnessBuilder().

    }

    private fun createBasicDataSource(config: Configuration, wipe: Boolean = false): BasicDataSource {
        val dataSource = BasicDataSource()
        val schema = config.getString("database.schema", "public");
        dataSource.addConnectionProperty("currentSchema", schema);
        dataSource.driverClassName = config.getString("database.DriverClass")
        dataSource.url = config.getString("database.url")
        dataSource.username = config.getString("database.username")
        dataSource.password = config.getString("database.password")
        dataSource.defaultAutoCommit = false;
        if (wipe) {
            wipeDatabase(dataSource, schema);
        }

        return dataSource
    }

    private fun wipeDatabase(dataSource: DataSource, schema: String) {
        val queryRunner = QueryRunner();
        val conn = dataSource.getConnection();
        queryRunner.update(conn, "DROP SCHEMA IF EXISTS ${schema} CASCADE");
        queryRunner.update(conn, "CREATE SCHEMA ${schema}");
        conn.commit();
        conn.close();
    }
}