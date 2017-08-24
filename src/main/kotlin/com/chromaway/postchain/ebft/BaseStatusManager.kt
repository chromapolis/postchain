package com.chromaway.postchain.ebft

import com.chromaway.postchain.core.Signature
import java.util.*

class BaseStatusManager(val ectxt: ErrContext, val nodeCount: Int, val myIndex: Int)
    : StatusManager {
    override val nodeStatuses = Array(nodeCount, {NodeStatus()})
    override val commitSignatures: Array<Signature?> = arrayOfNulls(nodeCount)
    override val myStatus: NodeStatus;
    var intent: BlockIntent = DoNothingIntent
    val quorum2f = (nodeCount / 3) * 2;

    init {
        myStatus = nodeStatuses[myIndex];
    }

    fun countNodes (state: NodeState, height: Long, blockRID: ByteArray?): Int {
        var count: Int = 0;
        for (ns in nodeStatuses) {
            if (ns.height == height && ns.state == state) {
                if (blockRID == null) {
                    if (ns.blockRID == null) count++;
                } else {
                    if (ns.blockRID != null && Arrays.equals(ns.blockRID, blockRID))
                        count++;
                }
            }
        }
        return count;
    }


    @Synchronized
    override fun onStatusUpdate(nodeIndex: Int, status: NodeStatus) {
        val existingStatus = nodeStatuses[nodeIndex];
        if ((status.serial > existingStatus.serial) || (status.height > existingStatus.height)) {
            nodeStatuses[nodeIndex] = status;
            recomputeStatus()
        }
    }

    fun advanceHeight() {
        with (myStatus) {
            height += 1
            serial += 1
            blockRID = null
            round = 0
            revolting = false
            state = NodeState.WaitBlock
        }
        resetCommitSignatures()
        intent = DoNothingIntent
        recomputeStatus()
    }

    fun resetCommitSignatures () {
        for (i in commitSignatures.indices)
            commitSignatures[i] = null
    }

    @Synchronized
    override fun onHeightAdvance(height: Long): Boolean {
        if (height == (myStatus.height + 1)) {
            advanceHeight();
            return true
        } else {
            ectxt.fatal("Height mismatch");
            return false
        }

    }

    @Synchronized
    override fun onCommittedBlock(blockRID: ByteArray) {
        if (Arrays.equals(blockRID, myStatus.blockRID)) {
            advanceHeight()
        } else
            ectxt.fatal("Committed block with wrong RID")
    }

    fun acceptBlock(blockRID: ByteArray, mySignature: Signature) {
        resetCommitSignatures();
        myStatus.blockRID = blockRID;
        myStatus.serial += 1;
        myStatus.state = NodeState.HaveBlock;
        commitSignatures[myIndex] = mySignature;
        intent = DoNothingIntent;
        recomputeStatus();
    }

    @Synchronized
    override fun onReceivedBlock(blockRID: ByteArray, mySignature: Signature): Boolean {
        val _intent = intent
        if (_intent is FetchUnfinishedBlockIntent) {
            val needBlockRID = _intent.blockRID
            if (Arrays.equals(blockRID, needBlockRID)) {
                acceptBlock(blockRID, mySignature);
                return true;
            } else {
                ectxt.warn("Received block which is irrelevant");
                return false;
            }
        } else {
            ectxt.warn("Received block which is irrelevant");
            return false;
        }
    }


    fun primaryIndex(): Int {
        return ((myStatus.height + myStatus.round) % nodeCount).toInt();
    }

    @Synchronized
    override fun onBuiltBlock(blockRID: ByteArray, mySignature: Signature): Boolean {
        if (intent is BuildBlockIntent) {
            if (primaryIndex() != myIndex) {
                ectxt.warn("Inconsistent state: building a block while not a primary");
                return false;
            }
            acceptBlock(blockRID, mySignature);
            return true;
        }
        else {
            ectxt.warn("Received built block while not requesting it.");
            return false;
        }
    }

    @Synchronized
    override fun onCommitSignature(nodeIndex: Int, blockRID: ByteArray, signature: Signature) {
        if (myStatus.state == NodeState.Prepared
                && Arrays.equals(blockRID, myStatus.blockRID))
        {
            this.commitSignatures[nodeIndex] = signature;
            recomputeStatus();
        } else {
            ectxt.warn("Wrong commit signature");
        }
    }

    @Synchronized
    override fun onStartRevolting() {
        myStatus.revolting = true
        myStatus.serial += 1
        recomputeStatus()
    }

    @Synchronized
    override fun getBlockIntent(): BlockIntent {
        return intent
    }

    fun recomputeStatus() {
        for (i in 0..1000) {
            if (!recomputeStatus1()) break
        }
    }

    fun recomputeStatus1 (): Boolean {

        fun resetBlock() {
            myStatus.state = NodeState.WaitBlock;
            myStatus.blockRID = null;
            myStatus.serial += 1;
            resetCommitSignatures();
        }


        // check if we have enough nodes who can participate in building a block
        // this is irrelevant if we are in prepared state, then we just keep trying
        // (might get perma-stuck if number of failures exceeds f)
        if (myStatus.state !== NodeState.Prepared) {
            var sameHeightCount: Int = 0
            var higherHeightCount: Int = 0;
            for (ns in nodeStatuses) {
                if (ns.height == myStatus.height) sameHeightCount++;
                else if (ns.height > myStatus.height) higherHeightCount++;
            }
            if (sameHeightCount <= this.quorum2f) {
                // cannot build a block

                // worth trying to sync?
                if (higherHeightCount > 0) {
                    val _intent = intent

                    if (_intent is FetchBlockAtHeightIntent) {
                        if (_intent.height == myStatus.height)
                            return false;
                        intent = FetchBlockAtHeightIntent(myStatus.height)
                        return true;
                    }

                    if (myStatus.state == NodeState.HaveBlock) {
                        resetBlock();
                    }

                    // try fetching a block
                    this.intent = FetchBlockAtHeightIntent(myStatus.height)
                    return true;
                }
                // there is no point in updating state further, but doesn't hurt anyway...
            }
        }

        if (myStatus.revolting) {
            var nHighRound: Int = 0;
            var nRevolting: Int = 0;
            for (ns in nodeStatuses) {
                if (ns.height != myStatus.height) continue;
                if (ns.round == myStatus.round) {
                    if (ns.revolting) nRevolting++;
                } else if (ns.round > myStatus.round) {
                    nHighRound++;
                }
            }
            if (nHighRound + nRevolting > this.quorum2f) {
                // revolt is successful

                // Note: we do not reset block if NodeState is Prepared.
                if (myStatus.state == NodeState.HaveBlock) {
                    resetBlock();
                }

                myStatus.revolting = false;
                myStatus.round += 1;
                myStatus.serial += 1;
                return true;
            }
        }


        if (myStatus.state === NodeState.HaveBlock) {
            val count = countNodes(NodeState.HaveBlock, myStatus.height, myStatus.blockRID)
                      + countNodes(NodeState.Prepared, myStatus.height, myStatus.blockRID);
            if (count >= this.quorum2f) {
                myStatus.state = NodeState.Prepared;
                myStatus.serial += 1;
                return true;
            }
        } else if (myStatus.state === NodeState.Prepared) {
            if (intent is CommitBlockIntent) return false;
            val count = commitSignatures.count { it != null }
            if (count > this.quorum2f) {
                // check if we have (2f+1) commit signatures including ours, in that case we signal commit intent.
                intent = CommitBlockIntent;
                return true;
            } else {
                // otherwise we set intent to FetchCommitSignatureIntent with current blockRID and list of nodes which
                // are already in prepared state but don't have commit signatures in our array

                val unfetchedNodes = mutableListOf<Int>();
                for ((i, nodeStatus) in nodeStatuses.withIndex()) {
                    val commitSignature = commitSignatures[i];
                    if (commitSignature != null) {
                        if ((nodeStatus.height > myStatus.height)
                                ||
                                ((nodeStatus.height == myStatus.height)
                                        && (nodeStatus.state === NodeState.Prepared)
                                        && nodeStatus.blockRID != null
                                        && Arrays.equals(nodeStatus.blockRID, myStatus.blockRID)))
                        {
                            unfetchedNodes.add(i);
                        }
                    }
                }

                intent = FetchCommitSignatureIntent(myStatus.blockRID as ByteArray, unfetchedNodes.toTypedArray())
            }
        } else if (myStatus.state == NodeState.WaitBlock) {
            if (primaryIndex() == this.myIndex) {
                if (intent !is BuildBlockIntent) {
                    intent = BuildBlockIntent
                    return true;
                }
            } else {
                val primaryBlockRID = this.nodeStatuses[this.primaryIndex()].blockRID;
                if (primaryBlockRID != null) {
                    val _intent = intent
                    if (! ( _intent is FetchUnfinishedBlockIntent && Arrays.equals(_intent.blockRID, myStatus.blockRID))) {
                        intent = FetchUnfinishedBlockIntent(myStatus.blockRID as ByteArray)
                        return true;
                    }
                }
            }
        }
        return false;

    }

}