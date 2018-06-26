package net.postchain.ebft

import net.postchain.api.rest.Model
import net.postchain.api.rest.PostchainModel
import net.postchain.api.rest.RestApi
import net.postchain.base.*
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockBuildingStrategy
import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.TransactionQueue
import net.postchain.createDataLayer
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.PeerConnectionManager
import org.apache.commons.configuration2.Configuration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class EBFTBlockchainInstance(
        val chainId: Long,
        val config: Configuration,
        val nodeIndex: Int,
        val peerCommConfiguration: PeerCommConfiguration,
        val connManager: PeerConnectionManager<EbftMessage>) {

    private var worker: EBFTBlockchainInstanceWorker

    private fun buildWorker(): EBFTBlockchainInstanceWorker {
        return EBFTBlockchainInstanceWorker(
                chainId,
                config,
                nodeIndex,
                peerCommConfiguration,
                connManager
        )
    }

    fun stop() {
        worker.stop()
    }

    init {
        worker = buildWorker()
    }
}


class EBFTBlockchainInstanceWorker(
        chainId: Long,
        config: Configuration,
        nodeIndex: Int,
        peerCommConfiguration: PeerCommConfiguration,
        connManager: PeerConnectionManager<EbftMessage>) {

    lateinit var updateLoop: Thread
    val stopMe = AtomicBoolean(false)

    lateinit var blockchainConfiguration: BlockchainConfiguration
    lateinit var storage: Storage
    lateinit var blockQueries: BlockQueries
    //val peerInfos: Array<PeerInfo>
    val statusManager: BaseStatusManager
    val commManager: CommManager<EbftMessage>

    lateinit var txQueue: TransactionQueue
    lateinit var txForwardingQueue: TransactionQueue
    lateinit var blockStrategy: BlockBuildingStrategy
    lateinit var engine: BlockchainEngine
    lateinit var blockDatabase: BaseBlockDatabase
    lateinit var blockManager: BlockManager
    lateinit var model: Model
    lateinit var syncManager: SyncManager

    val restApi: RestApi?


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
        // connManager.stop()
        // 4. Stop any in-progress blocks
        blockDatabase.stop()
    }

    init {
        setupDataLayer(config, chainId, nodeIndex)

        val bestHeight = blockQueries.getBestHeight().get()
        statusManager = BaseStatusManager(peerCommConfiguration.peerInfo.size, nodeIndex, bestHeight+1)
        commManager = makeCommManager(peerCommConfiguration, connManager)

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
        } else {
            restApi = null
        }

        // Give the SyncManager the BaseTransactionQueue and not the network-aware one,
        // because we don't want tx forwarding/broadcasting when received through p2p network
        syncManager = SyncManager(statusManager, blockManager, blockDatabase, commManager, txQueue, blockchainConfiguration)
        statusManager.recomputeStatus()
        startUpdateLoop(syncManager)
    }

}