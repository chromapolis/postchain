package com.chromaway.postchain.ebft

import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.ebft.message.CompleteBlock
import com.chromaway.postchain.ebft.message.GetBlockAtHeight
import com.chromaway.postchain.ebft.message.Messaged
import com.chromaway.postchain.ebft.message.Status
import mu.KLogging

fun decodeBlockDataWithWitness(block: CompleteBlock, bc: BlockchainConfiguration)
        : BlockDataWithWitness
{
    val header = bc.decodeBlockHeader(block.blockData.header)
    val witness = bc.decodeWitness(block.witness)
    return BlockDataWithWitness(header,
            block.blockData.transactions.toArray(arrayOf(byteArrayOf())),
            witness
    )

}

class SyncManager (
        val statusManager: StatusManager,
        val blockManager: BlockManager,
        val blockDatabase: BlockDatabase,
        val commManager: CommManager<Messaged>,
        val blockchainConfiguration: BlockchainConfiguration
) {
    companion object : KLogging()

    fun dispatchMessages () {
        for (packet in commManager.getPackets()) {
            val nodeIndex = packet.first
            val message = packet.second
            when (message) {
                is com.chromaway.postchain.ebft.message.CompleteBlock -> {
                    blockManager.onReceivedBlockAtHeight(
                            decodeBlockDataWithWitness(message, blockchainConfiguration),
                            message.height
                    )
                }
                is Status -> {
                    val nodeStatus = NodeStatus(message.height, message.serial)
                    with (nodeStatus) {
                        round = message.round
                        state = NodeState.values()[message.state.toInt()]
                        revolting = message.revolting
                        blockRID = message.blockRId
                    }
                    statusManager.onStatusUpdate(nodeIndex, nodeStatus)
                }
                is GetBlockAtHeight -> {

                }
            }


        }
    }

    fun processIntent() {

    }




    fun update() {
        dispatchMessages()
        processIntent()
//        revoltTracker.update();
//        statusSync.update();
    }
}