package com.chromaway.postchain.base

import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.core.TransactionEnqueuer
import com.chromaway.postchain.ebft.CommManager
import com.chromaway.postchain.ebft.message.EbftMessage
import mu.KLogging
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class NetworkAwareTxEnqueuer(private val te: TransactionEnqueuer, private val network: CommManager<EbftMessage>, private val nodeIndex: Int) : TransactionEnqueuer by te {
    companion object : KLogging()

    override fun enqueue(tx: com.chromaway.postchain.core.Transaction) {

        /*
These are the cases to take care of:

1. I'm currently primary and haven't done buildBlock() yet
2. I'm currently primary and I have done buildBlock()
3. I'm not primary

Primary may change and buildBlock() may start while tx is in transit.

I started by making a decorator on TransactionEnqueuer called NetworkAwareTransactionEnqueuer that
forwards to current primary if I'm not primary or if I'm primary and buildBlock() has started.
That class needs to know a lot of stuff:

* If we are currently primary
* If buildBlock has been started
* Who the next primary is

Then extra complixities are added if nodes goes down.

I ended up opting for this solution instead:

* Enqueue locally AND broadcast tx (we could possibly limit broadcasting to 2f nodes?)
* When block is committed, the transactions in the block are removed from queue, if present.

This has a few drawbacks:

* DoS attacks becomes easy. Correct (isCorrect() == true) transactions that will fail during
  apply(), can be created en-masse to fill up the "mempools". We have no way to control
  this but to throttle rate of incoming transactions prior to queueing.
* High bandwith requirement

Despite these drawbacks, I think this is the way to go for now. I haven't found another model
where we are guaranteed not to drop transactions.
         */
        val rid = tx.getRID().toHex()
        logger.debug("Node ${nodeIndex} enqueueing tx ${rid}")
        te.enqueue(tx)
        logger.debug("Node ${nodeIndex} broadcasting tx ${rid}")
        network.broadcastPacket(com.chromaway.postchain.ebft.message.Transaction(tx.getRawData()))
        logger.debug("Node ${nodeIndex} Enqueueing tx ${rid} Done")
    }
}