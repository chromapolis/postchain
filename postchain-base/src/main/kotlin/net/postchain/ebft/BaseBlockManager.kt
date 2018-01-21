// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft

import net.postchain.core.BlockBuildingStrategy
import net.postchain.core.BlockData
import net.postchain.core.BlockDataWithWitness
import mu.KLogging
import nl.komponents.kovenant.Promise
import java.util.Arrays

class BaseBlockManager(val blockDB: BlockDatabase, val statusManager: StatusManager,
                       val blockStrategy: BlockBuildingStrategy)
    : BlockManager {
    @Volatile var processing = false
    var intent : BlockIntent = DoNothingIntent
    companion object: KLogging()
    override var currentBlock: BlockData? = null

    @Synchronized
    protected fun<RT> runDBOp(op: () -> Promise<RT, Exception>, onSuccess: (RT)->Unit) {
        if (!processing) {
            processing = true
            intent = DoNothingIntent
            val promise = op()
            promise.success({ res ->
                onSuccess(res)
                processing = false
            })
            promise.fail { err ->
                processing = false
                logger.debug("Error in runDBOp()", err)
            }
        }
    }

    override fun onReceivedUnfinishedBlock(block: BlockData) {
        val theIntent = intent
        if (theIntent is FetchUnfinishedBlockIntent
                && Arrays.equals(theIntent.blockRID, block.header.blockRID))
        {
            runDBOp({
                blockDB.loadUnfinishedBlock(block)
            }, { sig ->
                if (statusManager.onReceivedBlock(block.header.blockRID, sig)) {
                    currentBlock = block
                }
            })
        }
    }

    override fun onReceivedBlockAtHeight(block: BlockDataWithWitness, height: Long) {
        val theIntent = intent
        if (theIntent is FetchBlockAtHeightIntent
             && theIntent.height == height)
        {
            runDBOp({
                blockDB.addBlock(block)
            }, {
                blockStrategy.blockCommitted(block)
                if (statusManager.onHeightAdvance(height + 1)) {
                    currentBlock = null
                }
            })
        }

    }

    protected fun update() {
        if (processing) return
        val smIntent = statusManager.getBlockIntent()
        intent = DoNothingIntent
        when (smIntent) {
            is CommitBlockIntent -> {
                if (currentBlock == null) {
                    logger.error("Don't have a block StatusManager wants me to commit")
                    return
                }
                runDBOp({
                    blockDB.commitBlock(statusManager.commitSignatures)
                }, {
                    statusManager.onCommittedBlock(currentBlock!!.header.blockRID)
                    blockStrategy.blockCommitted(currentBlock!!)
                    currentBlock = null
                })
            }
            is BuildBlockIntent -> {
                // It's our turn to build a block. But we need to consult the
                // BlockBuildingStrategy in order to figure out if this is the
                // right time. For example, the strategy may decide that
                // we won't build a block until we have at least three transactions
                // in the transaction queue. Or it will only build a block every 10 minutes.
                // Be careful not to have a BlockBuildingStrategy that conflicts with the
                // RevoltTracker of SyncManager.
                if (!blockStrategy.shouldBuildBlock()) {
                    return
                }
                runDBOp({
                    blockDB.buildBlock()
                }, {
                    blockAndSignature ->
                    val block = blockAndSignature.first
                    val signature = blockAndSignature.second
                    if (statusManager.onBuiltBlock(block.header.blockRID, signature)) {
                        currentBlock = block
                    }
                })
            }
            else -> intent = smIntent
        }
    }


    override fun isProcessing(): Boolean {
        return processing
    }

    override fun getBlockIntent(): BlockIntent {
        update()
        return intent
    }
}