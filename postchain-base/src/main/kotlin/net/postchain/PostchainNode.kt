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
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.common.toHex


/**
 * A postchain node
 *
 * @property updateLoop the main thread
 * @property stopMe boolean, which when set, will stop the thread [updateLoop]
 * @property restApi contains information on the rest API, such as network parameters and available queries
 * @property blockchainConfiguration stateless object which describes an individual blockchain instance
 * @property storage handles back-end database connection and storage
 * @property blockQueries a collection of methods for various blockchain related queries
 * @property peerInfos information relating to our peers
 * @property statusManager manages the status of the consensus protocol
 * @property commManager peer communication manager
 *
 * @property txQueue transaction queue for transactions received from peers. Will not be forwarded to other peers
 * @property txForwardingQueue transaction queue for transactions added locally via the REST API
 * @property blockStrategy strategy configurations for how to create new blocks
 * @property engine blockchain engine used for building and adding new blocks
 * @property blockDatabase wrapper class for the [engine] and [blockQueries], starting new threads when running
 * operations and handling exceptions
 * @property blockManager manages intents and acts as a wrapper for [blockDatabase] and [statusManager]
 * @property model
 * @property syncManager
 */
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
    lateinit var connManager: PeerConnectionManager<EbftMessage>
    lateinit var network: Network
    lateinit var txQueue: TransactionQueue
    lateinit var txForwardingQueue: TransactionQueue
    lateinit var blockStrategy: BlockBuildingStrategy
    lateinit var engine: BlockchainEngine
    lateinit var blockDatabase: BaseBlockDatabase
    lateinit var blockManager: BlockManager
    lateinit var model: Model
    lateinit var syncManager: SyncManager

    /**
     * Create and run the [updateLoop] thread until [stopMe] is set.
     *
     * @param syncManager the syncronization manager
     */
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

    /**
     * Stop the postchain node
     */
    fun stop() {
        // Ordering is important.
        // 1. Stop acceptin API calls
        stopMe.set(true)
        restApi?.stop()
        // 2. Close the data sources so that new blocks cant be started
        storage.close()
        // 3. Close the listening port and all TCP connections
        connManager.stop()
        // 4. Stop any in-progress blocks
        blockDatabase.stop()
    }

    /**
     * Start the postchain node by setting up everything and finally starting the updateLoop thread
     *
     * @param config configuration settings for the node
     * @param nodeIndex the index of the node
     */
    fun start(config: Configuration, nodeIndex: Int) {
        // This will eventually become a list of chain ids.
        // But for now it's just a single integer.
        val chainId = config.getLong("activechainids")
        setupDataLayer(config, chainId, nodeIndex)

        val bestHeight = blockQueries.getBestHeight().get()
        peerInfos = createPeerInfos(config)
        statusManager = BaseStatusManager(peerInfos.size, nodeIndex, bestHeight+1)

        val privKey = config.getString("messaging.privkey").hexStringToByteArray()

        val blockchainRID = (blockchainConfiguration as BaseBlockchainConfiguration).blockchainRID
        val commConfiguration = BasePeerCommConfiguration(peerInfos, blockchainRID, nodeIndex, SECP256K1CryptoSystem(), privKey)
        val connManager = makeConnManager(commConfiguration)
        commManager = makeCommManager(commConfiguration, connManager)

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

    /**
     * Create a data layer including relevant subsystems necessary for the postchain node, including
     * storage, transaction queue, block building strategy and engine.
     *
     * @param config node configuration
     * @param chainId chain identifier
     * @param nodeIndex index of the local node
     */
    private fun setupDataLayer(config: Configuration, chainId: Long, nodeIndex: Int) {
        val dataLayer = createDataLayer(config, chainId, nodeIndex)
        blockchainConfiguration = dataLayer.blockchainConfiguration
        storage = dataLayer.storage
        blockQueries = dataLayer.blockQueries
        txQueue = dataLayer.txQueue
        blockStrategy = dataLayer.blockBuildingStrategy
        engine = dataLayer.engine
    }

    /**
     * Pre-start function used to process the configuration file before calling the final [start] function
     *
     * @param configFile configuration file to parse
     * @param nodeIndex index of the local node
     */
    fun start(configFile: String, nodeIndex: Int) {
        val params = Parameters();
        val builder = FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration::class.java).
                configure(params.properties().
                        setFileName(configFile).
                        setListDelimiterHandler(DefaultListDelimiterHandler(',')))
        val config = builder.getConfiguration()
        start(config, nodeIndex)
    }

    /**
     * Retrieve peer information from config, including networking info and public keys
     *
     * @param config configuration
     * @return peer information
     */
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

/**
 * Cryptographic key generator. Will generate a pair of public and private keys and print to stdout.
 */
fun keygen() {
    val cs = SECP256K1CryptoSystem()
    // check that privkey is between 1 - 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140 to be valid?
    val privkey = cs.getRandomBytes(32)
    val pubkey = secp256k1_derivePubKey(privkey)
    println("privkey:\t${privkey.toHex()}")
    println("pubkey: \t${pubkey.toHex()}")
}

/**
 * Main function, everything starts here
 *
 * @param args [ { --nodeIndex | -i } <index> ] [ { --config | -c } <configFile> ] [ {--keygen | -k } ]
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