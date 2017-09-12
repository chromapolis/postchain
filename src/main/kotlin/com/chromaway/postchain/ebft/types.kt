package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.ManagedBlockBuilder
import com.chromaway.postchain.base.PeerCommConfiguration
import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import com.chromaway.postchain.core.Signature
import nl.komponents.kovenant.*
import java.util.*

interface ErrContext {
    fun fatal(msg: String)
    fun warn(msg: String)
    fun log(msg: String)
}

enum class NodeState {
    WaitBlock, // PBFT: before PRE-PREPARE
    HaveBlock, // PBFT: after PRE-PREPARE, PREPARE message is sent
    Prepared   // PBFT: _prepared_ state, COMMIT message is sent
}

class NodeStatus (var height: Long, var serial: Long) {

    var state: NodeState = NodeState.WaitBlock
    var round: Long = 0  // PBFT: view-number
    var blockRID : ByteArray? = null

    var revolting: Boolean = false // PBFT: VIEW-CHANGE (?)

    constructor (): this(0, -1)

}



interface BlockDatabase {
    fun addBlock(block: BlockDataWithWitness): Promise<Unit, Exception> // add a complete block after the current one
    fun loadUnfinishedBlock(block: BlockData): Promise<Signature, Exception> // returns block signature if successful
    fun commitBlock(signatures: Array<Signature?>): Promise<Unit, Exception>
    fun buildBlock(): Promise<Pair<BlockData, Signature>, Exception>

    fun verifyBlockSignature(s: Signature): Boolean
    fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception>
    fun getBlockAtHeight(height: Long): Promise<BlockData, Exception>
}

sealed class BlockIntent

object DoNothingIntent : BlockIntent()
object CommitBlockIntent : BlockIntent()
object BuildBlockIntent : BlockIntent()

data class FetchBlockAtHeightIntent(val height: Long): BlockIntent()

data class FetchUnfinishedBlockIntent(val blockRID: ByteArray) : BlockIntent() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as FetchUnfinishedBlockIntent
        if (!Arrays.equals(blockRID, other.blockRID)) return false
        return true
    }
}


data class FetchCommitSignatureIntent(val blockRID: ByteArray, val nodes: Array<Int>): BlockIntent()

interface BlockManager {
    var currentBlock: BlockData?
    fun onReceivedUnfinishedBlock(block: BlockData)
    fun onReceivedBlockAtHeight(block: BlockDataWithWitness, height: Long)
    fun isProcessing(): Boolean
    fun getBlockIntent(): BlockIntent
}

interface StatusManager {
    val nodeStatuses: Array<NodeStatus>
    val commitSignatures: Array<Signature?>
    val myStatus: NodeStatus

    fun onStatusUpdate(nodeIndex: Int, status: NodeStatus) // STATUS message from another node
    fun onHeightAdvance(height: Long):Boolean // a complete block was received from other peers, go forward
    fun onCommittedBlock(blockRID: ByteArray) // when block committed to the database
    fun onReceivedBlock(blockRID: ByteArray, mySignature: Signature): Boolean // received block was validated by BlockManager/DB
    fun onBuiltBlock(blockRID: ByteArray, mySignature: Signature): Boolean // block built by BlockManager/BlockDatabase (on a primary node)
    fun onCommitSignature(nodeIndex: Int, blockRID: ByteArray, signature: Signature)
    fun onStartRevolting()

    fun getBlockIntent(): BlockIntent
}

interface BlockchainEngine {
    val peerCommConfiguration: PeerCommConfiguration
    val cryptoSystem: CryptoSystem

    fun addBlock(block: BlockDataWithWitness)
    fun loadUnfinishedBlock(block: BlockData): ManagedBlockBuilder
    fun buildBlock(): ManagedBlockBuilder

}