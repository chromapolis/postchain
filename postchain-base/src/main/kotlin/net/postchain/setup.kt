// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain

import net.postchain.base.BaseBlockBuildingStrategy
import net.postchain.base.BaseBlockQueries
import net.postchain.base.BaseBlockchainEngine
import net.postchain.base.Storage
import net.postchain.base.data.BaseStorage
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.withWriteConnection
import net.postchain.core.BlockBuildingStrategy
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.TransactionQueue
import net.postchain.ebft.BlockchainEngine
import org.apache.commons.configuration2.CompositeConfiguration
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import java.io.File
import javax.sql.DataSource


fun getBlockchainConfiguration(config: Configuration, chainId: Long): BlockchainConfiguration {
    val bcfClass = Class.forName(config.getString("configurationfactory"))
    val factory = (bcfClass.newInstance() as BlockchainConfigurationFactory)

    return factory.makeBlockchainConfiguration(chainId, config)
}

class DataLayer(val engine: BlockchainEngine,
                val txQueue: TransactionQueue,
                val blockchainConfiguration: BlockchainConfiguration,
                val storage: Storage, val blockQueries: BaseBlockQueries, val blockBuildingStrategy: BlockBuildingStrategy) {
    fun close() {
        storage.close()
    }
}

fun createDataLayer(config: Configuration, chainId: Long, nodeIndex: Int): DataLayer {


    val blockchainConfiguration = getBlockchainConfiguration(config.subset("blockchain.$chainId"), chainId)
    val storage = baseStorage(config, nodeIndex)
    withWriteConnection(storage, chainId, { blockchainConfiguration.initializeDB(it); true })

    val blockQueries = blockchainConfiguration.makeBlockQueries(storage)

    val txQueue = BaseTransactionQueue()
    val strategy = blockchainConfiguration.getBlockBuildingStrategy(blockQueries, txQueue)

    val engine = BaseBlockchainEngine(blockchainConfiguration, storage,
            chainId, txQueue, strategy)

    val node = DataLayer(engine,
            txQueue,
            blockchainConfiguration, storage,
            blockQueries as BaseBlockQueries, strategy)
    return node
}

fun baseStorage(config: Configuration, nodeIndex: Int): BaseStorage {
    val writeDataSource = createBasicDataSource(config)
    writeDataSource.defaultAutoCommit = false
    writeDataSource.maxTotal = 1
    if (config.getBoolean("database.wipe", false)) {
        wipeDatabase(writeDataSource, config)
    }
    createSchemaIfNotExists(writeDataSource, config.getString("database.schema"))

    val readDataSource = createBasicDataSource(config)
    readDataSource.defaultAutoCommit = true
    readDataSource.maxTotal = 2
    readDataSource.defaultReadOnly = true

    val storage = BaseStorage(writeDataSource, readDataSource, nodeIndex)
    return storage
}

fun createBasicDataSource(config: Configuration): BasicDataSource {
    val dataSource = BasicDataSource()
    val schema = config.getString("database.schema", "public")
    dataSource.addConnectionProperty("currentSchema", schema)
    dataSource.driverClassName = config.getString("database.driverclass")
    dataSource.url = config.getString("database.url")
    dataSource.username = config.getString("database.username")
    dataSource.password = config.getString("database.password")
    dataSource.defaultAutoCommit = false
    return dataSource
}

fun wipeDatabase(dataSource: DataSource, config: Configuration) {
    val schema = config.getString("database.schema", "public")
    val queryRunner = QueryRunner()
    val conn = dataSource.connection
    queryRunner.update(conn, "DROP SCHEMA IF EXISTS $schema CASCADE")
    queryRunner.update(conn, "CREATE SCHEMA $schema")
    conn.commit()
    conn.close()
}

private fun createSchemaIfNotExists(dataSource: DataSource, schema: String) {
    val queryRunner = QueryRunner()
    val conn = dataSource.connection
    try {
        queryRunner.update(conn, "CREATE SCHEMA IF NOT EXISTS $schema")
        conn.commit()
    } finally {
        conn.close()
    }
}