package com.chromaway.postchain

import com.chromaway.postchain.base.BasePeerCommConfiguration
import com.chromaway.postchain.base.PeerInfo
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.data.BaseStorage
import com.chromaway.postchain.base.data.BaseTransactionQueue
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.BlockchainConfigurationFactory
import com.chromaway.postchain.ebft.BaseBlockDatabase
import com.chromaway.postchain.ebft.BaseBlockManager
import com.chromaway.postchain.ebft.BaseBlockchainEngine
import com.chromaway.postchain.ebft.BaseStatusManager
import com.chromaway.postchain.ebft.ErrContext
import com.chromaway.postchain.ebft.SyncManager
import com.chromaway.postchain.ebft.makeCommManager
import com.chromaway.postchain.gtx.GTXBlockchainConfigurationFactory
import com.chromaway.postchain.gtx.GTXModule
import com.chromaway.postchain.gtx.GTX_NOP_Module
import mu.KLogging
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.commons.dbcp2.BasicDataSource
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class PostchainNode {
    lateinit var updateLoop: Thread
    val stopMe = AtomicBoolean(false)

    protected fun startUpdateLoop(syncManager: SyncManager) {
        updateLoop = thread(name = "updateLoop") {
            while (true) {
                if (stopMe.get()) {
                    break
                }
                syncManager.update()
                if (stopMe.get()) {
                    break
                }
                Thread.sleep(100)
            }
        }
    }

    fun stop() {
        stopMe.set(true)
    }

    fun start(nodeIndex: Int): SyncManager {
        val chainId = 1
        val configs = Configurations()
        val config = configs.properties(File("config.$nodeIndex.properties"))
        config.listDelimiterHandler = DefaultListDelimiterHandler(',')

        val blockchainConfiguration = getBlockchainConfiguration(config.subset("blockchain.$chainId"), chainId)

        val dbConfig = config.subset("database")
        val writeDataSource = createBasicDataSource(dbConfig)
        writeDataSource.maxTotal = 1

        val readDataSource = createBasicDataSource(dbConfig)
        readDataSource.defaultAutoCommit = true
        readDataSource.maxTotal = 2
        readDataSource.defaultReadOnly = true

        val storage = BaseStorage(writeDataSource, readDataSource, nodeIndex)

        val txQueue = BaseTransactionQueue()

        val engine = BaseBlockchainEngine(blockchainConfiguration, storage,
                chainId, txQueue)

        val blockQueries = blockchainConfiguration.makeBlockQueries(storage)

        val ectxt = ErrorContext()
        val peerInfos = createPeerInfos(config)
        val statusManager = BaseStatusManager(ectxt, peerInfos.size, nodeIndex)
        val blockDatabase = BaseBlockDatabase(engine, blockQueries, nodeIndex)
        val blockManager = BaseBlockManager(blockDatabase, statusManager, ectxt)

        val privKey = config.getString("messaging.privkey").hexStringToByteArray()

        val commConfiguration = BasePeerCommConfiguration(peerInfos, nodeIndex, SECP256K1CryptoSystem(), privKey)
        val commManager = makeCommManager(commConfiguration)

        return SyncManager(statusManager, blockManager, blockDatabase, commManager, blockchainConfiguration)
    }

    fun createPeerInfos(config: Configuration): Array<PeerInfo> {
        var peerCount = 0;
        config.getKeys("node").forEach { peerCount++ }
        peerCount = peerCount/4
        return Array(peerCount, {
            PeerInfo(
                    config.getString("node.$it.host"),
                    config.getInt("node.$it.port"),
                    config.getString("node.$it.pubkey").hexStringToByteArray()
            )}
        )
    }

    private fun getBlockchainConfiguration(config: Configuration, chainId: Int): BlockchainConfiguration {
        val bcfClass = Class.forName(config.getString("configurationfactory"))
        val factory = (bcfClass.newInstance() as BlockchainConfigurationFactory)

        return factory.makeBlockchainConfiguration(chainId.toLong(), config)
    }

    private fun createBasicDataSource(config: Configuration): BasicDataSource {
        val dataSource = BasicDataSource()
        val schema = config.getString("schema", "public")
        dataSource.addConnectionProperty("currentSchema", schema)
        dataSource.driverClassName = config.getString("driverclass")
        dataSource.url = config.getString("url")
        dataSource.username = config.getString("username")
        dataSource.password = config.getString("password")
        dataSource.defaultAutoCommit = false

        return dataSource
    }
}

fun main(args : Array<String>) {
    val node = PostchainNode()
    val nodeIndex = parseInt(args[0])!!
    node.start(nodeIndex)

}

class ErrorContext: ErrContext {
    companion object : KLogging()

    override fun fatal(msg: String) {
        logger.error(msg)
    }

    override fun warn(msg: String) {
        logger.warn(msg)
    }

    override fun log(msg: String) {
        logger.info { msg }
    }
}

class TestGtxConfigurationFactory(): GTXBlockchainConfigurationFactory() {
    override fun createGtxModule(config: Configuration): GTXModule {
        return GTX_NOP_Module()
    }
}