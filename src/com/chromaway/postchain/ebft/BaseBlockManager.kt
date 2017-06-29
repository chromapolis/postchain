package com.chromaway.postchain.ebft

import com.chromaway.postchain.core.BlockData
import com.chromaway.postchain.core.BlockDataWithWitness

class BaseBlockManager : BlockManager {

    override fun onReceivedUnfinishedBlock(block: BlockData) {

    }

    /*fun onReceivedBlockAtHeight(block: BlockDataWithWitness, height: Long);
    fun isProcessing(): Boolean;
    fun getCurrentBlock(): BlockData?;
    fun getFetchBlockIntent(): BlockIntent;*/
}