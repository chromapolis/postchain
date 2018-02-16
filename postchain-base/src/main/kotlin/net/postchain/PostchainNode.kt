// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain

import net.postchain.api.rest.Model
import net.postchain.api.rest.PostchainModel
import net.postchain.api.rest.RestApi
import net.postchain.base.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.*
import net.postchain.ebft.*
import net.postchain.ebft.message.EbftMessage
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import net.postchain.base.CryptoSystem
import net.postchain.common.toHex


class PostchainNode {
    lateinit var updateLoop: Thread
    val stopMe = AtomicBoolean(false)
    var restApi: RestApi? = null
    lateinit var blockchainConfiguration: BlockchainConfiguration
    lateinit var storage: Storage
    lateinit var blockQueries: BlockQueries
    lateinit var peerInfos: Array<PeerInfo>
    lateinit var statusManager: BaseStatusManager
    lateinit var commManager: CommManager<EbftMessage>
    lateinit var network: Network
    lateinit var txQueue: TransactionQueue
    lateinit var txForwardingQueue: TransactionQueue
    lateinit var blockStrategy: BlockBuildingStrategy
    lateinit var engine: BlockchainEngine
    lateinit var blockDatabase: BaseBlockDatabase
    lateinit var blockManager: BlockManager
    lateinit var model: Model
    lateinit var syncManager: SyncManager

    protected fun startUpdateLoop(syncManager: SyncManager) {
        updateLoop = thread(name = "updateLoop") {
            while (true) {
                if (stopMe.get()) {
                    break
                }
                syncManager.update()
                Thread.sleep(50)
            }
        }
    }

    fun stop() {
        // Ordering is important.
        // 1. Stop acceptin API calls
        stopMe.set(true)
        restApi?.stop()
        // 2. Close the data sources so that new blocks cant be started
        storage.close()
        // 3. Close the listening port and all TCP connections
        commManager.stop()
        // 4. Stop any in-progress blocks
        blockDatabase.stop()
    }

    fun start(config: Configuration, nodeIndex: Int) {
        // This will eventually become a list of chain ids.
        // But for now it's just a single integer.
        val chainId = config.getLong("activechainids")
        setupDataLayer(config, chainId, nodeIndex)

        val bestHeight = blockQueries.getBestHeight().get()
        peerInfos = createPeerInfos(config)
        statusManager = BaseStatusManager(peerInfos.size, nodeIndex, bestHeight+1)

        val privKey = config.getString("messaging.privkey").hexStringToByteArray()

        val commConfiguration = BasePeerCommConfiguration(peerInfos, nodeIndex, SECP256K1CryptoSystem(), privKey)
        commManager = makeCommManager(commConfiguration)

        txForwardingQueue = NetworkAwareTxQueue(
                txQueue,
                commManager,
                nodeIndex
        )

        blockDatabase = BaseBlockDatabase(engine, blockQueries, nodeIndex)
        blockManager = BaseBlockManager(blockDatabase, statusManager, blockStrategy)

        val port = config.getInt("api.port", 7740)
        if (port != -1) {
            model = PostchainModel(txForwardingQueue, blockchainConfiguration.getTransactionFactory(),
                    blockQueries as BaseBlockQueries)
            val basePath = config.getString("api.basepath", "")
            restApi = RestApi(model, port, basePath)
        }

        // Give the SyncManager the BaseTransactionQueue and not the network-aware one,
        // because we don't want tx forwarding/broadcasting when received through p2p network
        syncManager = SyncManager(statusManager, blockManager, blockDatabase, commManager, txQueue, blockchainConfiguration)
        statusManager.recomputeStatus()
        startUpdateLoop(syncManager)
    }

    private fun setupDataLayer(config: Configuration, chainId: Long, nodeIndex: Int) {
        val dataLayer = createDataLayer(config, chainId, nodeIndex)
        blockchainConfiguration = dataLayer.blockchainConfiguration
        storage = dataLayer.storage
        blockQueries = dataLayer.blockQueries
        txQueue = dataLayer.txQueue
        blockStrategy = dataLayer.blockBuildingStrategy
        engine = dataLayer.engine
    }

    fun start(configFile: String, nodeIndex: Int) {
        val params = Parameters();
        val builder = FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration::class.java).
                configure(params.properties().
                        setFileName(configFile).
                        setListDelimiterHandler(DefaultListDelimiterHandler(',')))
        val config = builder.getConfiguration()
        start(config, nodeIndex)
    }

    fun createPeerInfos(config: Configuration): Array<PeerInfo> {
        // this is for testing only. We can prepare the configuration with a
        // special Array<PeerInfo> for dynamic ports
        val peerInfos = config.getProperty("testpeerinfos")
        if (peerInfos != null) {
            if (peerInfos is PeerInfo) {
                return arrayOf(peerInfos)
            } else {
                return (peerInfos as List<PeerInfo>).toTypedArray()
            }
        }

        var peerCount = 0;
        config.getKeys("node").forEach { peerCount++ }
        peerCount = peerCount/4
        return Array(peerCount, {
            val port = config.getInt("node.$it.port")
            val host = config.getString("node.$it.host")
            val pubKey = config.getString("node.$it.pubkey").hexStringToByteArray()
            if (port == 0) {
                DynamicPortPeerInfo(host, pubKey)
            } else {
                PeerInfo(host, port, pubKey)
            }
        }

        )
    }
}

fun keygen() {
    val cs = SECP256K1CryptoSystem()
    // check that privkey is between 1 - 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140 to be valid?
    val privkey = cs.getRandomBytes(32)
    val pubkey = secp256k1_derivePubKey(privkey)
    println("privkey:\t${privkey.toHex()}")
    println("pubkey: \t${pubkey.toHex()}")
}

/**
 * args: [ { --nodeIndex | -i } <index> ] [ { --config | -c } <configFile> ] [ {--keygen | -k } ]
 */
fun main(args: Array<String>) {
    var i = 0
    var nodeIndex = 0;
    var config = ""
    while (i < args.size) {
        when (args[i]) {
            "-i", "--nodeIndex" -> {
                nodeIndex = parseInt(args[++i])!!
            }
            "-c", "--config" -> {
                config = args[++i]
            }
            "-k", "--keygen" -> {
                keygen()
                exitProcess(0)
            }
        }
        i++
    }
    if (config == "") {
        config = "config/config.$nodeIndex.properties"
    }
    val node = PostchainNode()
    node.start(config, nodeIndex)
}