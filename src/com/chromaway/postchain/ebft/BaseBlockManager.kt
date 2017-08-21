package com.chromaway.postchain.ebft

import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness
import nl.komponents.kovenant.Promise
import java.util.*

class BaseBlockManager(val blockDB: BlockDatabase, val sm: StatusManager, val ectxt: ErrContext)
    : BlockManager {
    @Volatile var processing = false
    var intent : BlockIntent = DoNothingIntent

    override var currentBlock: BlockData? = null

    @Synchronized
    protected fun<RT> runDBOp(op: () -> Promise<RT, Exception>, onSuccess: (RT)->Unit) {
        if (!processing) {
            processing = true
            intent = DoNothingIntent
            val promise = op()
            promise.success({ res ->
                processing = false
                onSuccess(res)
            })
            promise.fail { err ->
                processing = false
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
                if (sm.onReceivedBlock(block.header.blockRID, sig)) {
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
                if (sm.onHeightAdvance(height + 1)) {
                    currentBlock = null
                }
            })
        }

    }

    protected fun update() {
        if (processing) return
        val smIntent = sm.getBlockIntent()
        intent = DoNothingIntent
        when (smIntent) {
            is CommitBlockIntent -> {
                if (currentBlock == null) {
                    ectxt.fatal("Don't have a block StatusManager wants me to commit")
                    return
                }
                runDBOp({
                    blockDB.commitBlock(sm.commitSignatures)
                }, {
                    sm.onCommittedBlock(currentBlock!!.header.blockRID)
                    currentBlock = null
                })
            }
            is BuildBlockIntent -> {
                runDBOp({
                    blockDB.buildBlock()
                }, {
                    blockAndSignature ->
                    val block = blockAndSignature.first
                    val signature = blockAndSignature.second
                    if (sm.onBuiltBlock(block.header.blockRID, signature)) {
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