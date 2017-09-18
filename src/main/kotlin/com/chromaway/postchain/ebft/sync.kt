package com.chromaway.postchain.ebft

import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.ebft.messages.CompleteBlock
import com.chromaway.postchain.ebft.messages.Message

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
        val commManager: CommManager<Message>,
        val blockchainConfiguration: BlockchainConfiguration
) {

    fun dispatchMessages () {
        for (packet in commManager.getPackets()) {
            val nodeIndex = packet.first
            val message = packet.second
            when (message.choiceID) {
                Message.completeBlockChosen -> {
                    val completeBlock = message.completeBlock
                    blockManager.onReceivedBlockAtHeight(
                            decodeBlockDataWithWitness(completeBlock, blockchainConfiguration),
                            completeBlock.height
                    )
                }
                Message.statusChosen -> {
                    val status = message.status
                    val nodeStatus = NodeStatus(status.height, status.serial)
                    with (nodeStatus) {
                        round = status.round
                        state = NodeState.values()[status.state.toInt()]
                        revolting = status.revolting
                        blockRID = status.blockRID
                    }
                    statusManager.onStatusUpdate(nodeIndex, nodeStatus)
                }
                Message.getBlockAtHeightChosen -> {

                }
            }


        }
    }


    fun update() {
        Thread.sleep(100)
    }


}