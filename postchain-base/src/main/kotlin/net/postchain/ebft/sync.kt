// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.ebft.message.*
import net.postchain.ebft.message.Transaction
import java.util.*

fun decodeBlockDataWithWitness(block: CompleteBlock, bc: BlockchainConfiguration)
        : BlockDataWithWitness {
    val header = bc.decodeBlockHeader(block.header)
    val witness = bc.decodeWitness(block.witness)
    return BlockDataWithWitness(header, block.transactions, witness)
}

fun decodeBlockData(block: UnfinishedBlock, bc: BlockchainConfiguration)
        : BlockData {
    val header = bc.decodeBlockHeader(block.header)
    return BlockData(header, block.transactions)
}

class RevoltTracker(private val revoltTimeout: Int, private val statusManager: StatusManager) {
    var deadLine = newDeadLine()
    var prevHeight = statusManager.myStatus.height
    var prevRound = statusManager.myStatus.round

    private fun newDeadLine(): Long {
        return Date().time + revoltTimeout
    }

    // Starts a revolt if certain conditions are met.
    fun update() {
        val current = statusManager.myStatus
        if (current.height > prevHeight ||
                current.height == prevHeight && current.round > prevRound) {
            prevHeight = current.height
            prevRound = current.round
            deadLine = newDeadLine()
        } else if (Date().time > deadLine && !current.revolting) {
            this.statusManager.onStartRevolting()
        }
    }
}


private class StatusSender(private val maxStatusInterval: Int, private val statusManager: StatusManager, private val commManager: CommManager<EbftMessage>) {
    var lastSerial: Long = -1
    var lastSentTime: Long = Date(0L).time

    // Sends a status message to all peers when my status has changed or
    // after a timeout period.
    fun update() {
        val myStatus = statusManager.myStatus
        val isNewState = myStatus.serial > this.lastSerial
        val timeoutExpired = System.currentTimeMillis() - this.lastSentTime > this.maxStatusInterval
        if (isNewState || timeoutExpired) {
            this.lastSentTime = Date().time
            this.lastSerial = myStatus.serial
            val statusMessage = Status(myStatus.blockRID, myStatus.height, myStatus.revolting,
                    myStatus.round, myStatus.serial, myStatus.state.ordinal)
            commManager.broadcastPacket(statusMessage)
        }
    }
}

val StatusLogInterval = 10000L

class SyncManager(
        val statusManager: StatusManager,
        val blockManager: BlockManager,
        val blockDatabase: BlockDatabase,
        val commManager: CommManager<EbftMessage>,
        private val txQueue: TransactionQueue,
        val blockchainConfiguration: BlockchainConfiguration
) {
    private val revoltTracker = RevoltTracker(60000, statusManager)
    private val statusSender = StatusSender(1000, statusManager, commManager)
    private val defaultTimeout = 1000
    private var currentTimeout = defaultTimeout
    private var processingIntent : BlockIntent = DoNothingIntent
    private var processingIntentDeadline = 0L
    private var lastStatusLogged = Date().time

    companion object : KLogging()

    fun dispatchMessages() {
        for (packet in commManager.getPackets()) {
            val nodeIndex = packet.first
            val message = packet.second
            try {
                when (message) {
                    is Status -> {
                        val nodeStatus = NodeStatus(message.height, message.serial)
                        nodeStatus.blockRID = message.blockRId
                        nodeStatus.revolting = message.revolting
                        nodeStatus.round = message.round
                        nodeStatus.state = NodeState.values()[message.state]
                        statusManager.onStatusUpdate(nodeIndex, nodeStatus)
                    }
                    is BlockSignature -> {
                        val signature = Signature(message.signature.subjectID, message.signature.data)
                        val smBlockRID = this.statusManager.myStatus.blockRID
                        if (smBlockRID == null) {
                            logger.info("Received signature not needed")
                        } else if (!smBlockRID.contentEquals(message.blockRID)) {
                            logger.info("Receive signature for a different block")
                        } else if (this.blockDatabase.verifyBlockSignature(signature)) {
                                this.statusManager.onCommitSignature(nodeIndex, message.blockRID, signature)
                        }
                    }
                    is CompleteBlock -> {
                        blockManager.onReceivedBlockAtHeight(
                                decodeBlockDataWithWitness(message, blockchainConfiguration),
                                message.height
                        )
                    }
                    is UnfinishedBlock -> {
                        blockManager.onReceivedUnfinishedBlock(decodeBlockData(message, blockchainConfiguration))
                    }
                    is GetUnfinishedBlock -> sendUnfinishedBlock(nodeIndex)
                    is GetBlockAtHeight -> sendBlockAtHeight(nodeIndex, message.height)
                    is GetBlockSignature -> sendBlockSignature(nodeIndex, message.blockRID)
                    is Transaction -> handleTransaction(nodeIndex, message)
                    else -> throw ProgrammerMistake("Unhandled type ${message::class}")
                }
            } catch (e: Exception) {
                logger.error("Couldn't handle message ${message}. Ignoring and continuing", e)
            }
        }
    }

    private fun handleTransaction(index: Int, message: Transaction) {
        val tx = blockchainConfiguration.getTransactionFactory().decodeTransaction(message.data)
        if (!tx.isCorrect()) {
            throw UserMistake("Transaction ${tx.getRID()} is not correct")
        }
        txQueue.enqueue(tx)
    }

    private fun sendBlockSignature(nodeIndex: Int, blockRID: ByteArray) {
        val currentBlock = this.blockManager.currentBlock
        if (currentBlock != null && currentBlock.header.blockRID.contentEquals(blockRID)) {
            assert(statusManager.myStatus.blockRID!!.contentEquals(currentBlock.header.blockRID))
            val signature = statusManager.getCommitSignature()
            if (signature != null) {
                commManager.sendPacket(BlockSignature(blockRID, signature), setOf(nodeIndex))
            }
            return
        }
        val blockSignature = blockDatabase.getBlockSignature(blockRID)
        blockSignature success {
            val packet = BlockSignature(blockRID, it)
            commManager.sendPacket(packet, setOf(nodeIndex))
        } fail {
            logger.debug("Error sending BlockSignature", it)
        }
    }

    private fun sendBlockAtHeight(nodeIndex: Int, height: Long) {
        val blockAtHeight = blockDatabase.getBlockAtHeight(height)
        blockAtHeight success {
            val packet = CompleteBlock(it.header.rawData, it.transactions.toList(),
                    height, it.witness!!.getRawData())
            commManager.sendPacket(packet, setOf(nodeIndex))
        } fail { logger.debug("Error sending CompleteBlock", it) }
    }

    private fun sendUnfinishedBlock(nodeIndex: Int) {
        val currentBlock = blockManager.currentBlock
        if (currentBlock != null) {
            commManager.sendPacket(UnfinishedBlock(currentBlock.header.rawData, currentBlock.transactions.toList()), setOf(nodeIndex))
        }
    }

    private fun selectRandomNode(match: (NodeStatus) -> Boolean): Int? {
        val matchingIndexes = mutableListOf<Int>()
        statusManager.nodeStatuses.forEachIndexed({ index, status ->
            if (match(status)) matchingIndexes.add(index)
        })
        if (matchingIndexes.isEmpty()) return null
        if (matchingIndexes.size == 1) return matchingIndexes[0]
        return matchingIndexes[Math.floor(Math.random() * matchingIndexes.size).toInt()]
    }

    private fun fetchBlockAtHeight(height: Long) {
        val nodeIndex = selectRandomNode { it.height > height } ?: return
        logger.debug("Fetching block at height ${height} from node ${nodeIndex}")
        commManager.sendPacket(GetBlockAtHeight(height), setOf(nodeIndex))
    }

    private fun fetchCommitSignatures(blockRID: ByteArray, nodes: Array<Int>) {
        val message = GetBlockSignature(blockRID)
        logger.debug("Fetching commit signature for block with RID ${blockRID.toHex()} from nodes ${Arrays.toString(nodes)}")
        commManager.sendPacket(message, nodes.toSet())
    }

    private fun fetchUnfinishedBlock(blockRID: ByteArray) {
        val height = statusManager.myStatus.height
        val nodeIndex = selectRandomNode {
            it.height == height && (it.blockRID?.contentEquals(blockRID) ?: false)
        }
        if (nodeIndex == null) {
            return
        }
        logger.debug("Fetching unfinished block with RID ${blockRID.toHex()}from node ${nodeIndex} ")
        commManager.sendPacket(GetUnfinishedBlock(blockRID), setOf(nodeIndex))
    }

    fun processIntent() {
        val intent = blockManager.getBlockIntent()
        if (intent == processingIntent) {
            if (intent is DoNothingIntent) return
            if (Date().time > processingIntentDeadline) {
                this.currentTimeout = (this.currentTimeout.toDouble() * 1.1).toInt() // exponential back-off
            } else {
                return
            }
        } else {
            currentTimeout = defaultTimeout
        }
        when (intent) {
            DoNothingIntent -> Unit
            is FetchBlockAtHeightIntent -> fetchBlockAtHeight(intent.height)
            is FetchCommitSignatureIntent -> fetchCommitSignatures(intent.blockRID, intent.nodes)
            is FetchUnfinishedBlockIntent -> fetchUnfinishedBlock(intent.blockRID)
            else -> throw ProgrammerMistake("Unrecognized intent: ${intent::class}")
        }
        processingIntent = intent
        processingIntentDeadline = Date().time + currentTimeout
    }

    fun logStatus() {
        for ((idx, ns) in statusManager.nodeStatuses.withIndex()) {
            val blockRID = ns.blockRID
            val haveSignature = statusManager.commitSignatures[idx] != null
            logger.info {
                "Node ${idx} he:${ns.height} ro:${ns.round} st:${ns.state}" +
                " ${if (ns.revolting) "R" else ""} blockRID=${if (blockRID == null) "null" else blockRID.toHex()}" +
                " havesig:${haveSignature}"
            }
        }
    }

    fun update() {
        // Process all messages from peers, one at a time. Some
        // messages may trigger asynchronous code which will
        // send replies at a later time, others will send replies
        // immeiately
        dispatchMessages()

        // An intent is something that we want to do with our current block.
        // The current intent is fetched from the BlockManager and will result in
        // some messages being sent to peers requesting data like signatures or
        // complete blocks
        processIntent()

        // RevoltTracker will check trigger a revolt if conditions for revolting are met
        // A revolt will be triggerd by calling statusManager.onStartRevolting()
        // Typical revolt conditions
        //    * A timeout happens and round has not increased. Round is increased then 2f+1 nodes
        //      are revolting.
        revoltTracker.update()

        // Sends a status message to all peers when my status has changed or after a timeout
        statusSender.update()

        if (Date().time - lastStatusLogged >= StatusLogInterval) {
            logStatus()
            lastStatusLogged = Date().time
        }
    }
}