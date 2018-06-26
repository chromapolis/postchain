package net.postchain.ebft

import net.postchain.api.rest.PostchainModel
import net.postchain.api.rest.RestApi
import net.postchain.base.BaseBlockQueries
import net.postchain.base.NetworkAwareTxQueue
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.Storage
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

    var worker: EBFTBlockchainInstanceWorker

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

    fun getModel(): BlockchainInstanceModel {
        return worker
    }

    init {
        worker = buildWorker()
    }
}



interface BlockchainInstanceModel {
    val blockchainConfiguration: BlockchainConfiguration
    val storage: Storage
    val blockQueries: BlockQueries
    val statusManager: BaseStatusManager
    val commManager: CommManager<EbftMessage>

    val txQueue: TransactionQueue
    val txForwardingQueue: TransactionQueue
    val blockStrategy: BlockBuildingStrategy
    val engine: BlockchainEngine
    val blockDatabase: BaseBlockDatabase
    val blockManager: BlockManager
    val syncManager: SyncManager
    val restApi: RestApi?
    val apiModel: PostchainModel?
}


class EBFTBlockchainInstanceWorker(
        chainId: Long,
        config: Configuration,
        nodeIndex: Int,
        peerCommConfiguration: PeerCommConfiguration,
        connManager: PeerConnectionManager<EbftMessage>)

    : BlockchainInstanceModel
{

    lateinit var updateLoop: Thread
    val stopMe = AtomicBoolean(false)

    override val blockchainConfiguration: BlockchainConfiguration
    override val storage: Storage
    override val blockQueries: BlockQueries
    override val statusManager: BaseStatusManager
    override val commManager: CommManager<EbftMessage>
    override val txQueue: TransactionQueue
    override val txForwardingQueue: TransactionQueue
    override val blockStrategy: BlockBuildingStrategy
    override val engine: BlockchainEngine
    override val blockDatabase: BaseBlockDatabase
    override val blockManager: BlockManager
    override val syncManager: SyncManager
    override val restApi: RestApi?
    override val apiModel: PostchainModel?


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
        val dataLayer = createDataLayer(config, chainId, nodeIndex)
        blockchainConfiguration = dataLayer.blockchainConfiguration
        storage = dataLayer.storage
        blockQueries = dataLayer.blockQueries
        txQueue = dataLayer.txQueue
        blockStrategy = dataLayer.blockBuildingStrategy
        engine = dataLayer.engine

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
            val model = PostchainModel(txForwardingQueue, blockchainConfiguration.getTransactionFactory(),
                    blockQueries as BaseBlockQueries)
            apiModel = model
            val basePath = config.getString("api.basepath", "")
            restApi = RestApi(model, port, basePath)
        } else {
            restApi = null
            apiModel = null
        }

        // Give the SyncManager the BaseTransactionQueue and not the network-aware one,
        // because we don't want tx forwarding/broadcasting when received through p2p network
        syncManager = SyncManager(statusManager, blockManager, blockDatabase, commManager, txQueue, blockchainConfiguration)
        statusManager.recomputeStatus()
        startUpdateLoop(syncManager)
    }

}