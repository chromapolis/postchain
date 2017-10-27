package com.chromaway.postchain

import com.chromaway.postchain.base.data.BaseStorage
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.BlockchainConfigurationFactory
import org.apache.commons.configuration2.Configuration
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import javax.sql.DataSource


fun getBlockchainConfiguration(config: Configuration, chainId: Long): BlockchainConfiguration {
    val bcfClass = Class.forName(config.getString("configurationfactory"))
    val factory = (bcfClass.newInstance() as BlockchainConfigurationFactory)

    return factory.makeBlockchainConfiguration(chainId, config)
}


fun baseStorage(config: Configuration, nodeIndex: Int, wipe: Boolean): BaseStorage {
    val writeDataSource = createBasicDataSource(config)
    writeDataSource.defaultAutoCommit = false
    writeDataSource.maxTotal = 1
    if (wipe) {
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