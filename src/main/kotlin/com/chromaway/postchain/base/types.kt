package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.Transaction
import java.util.concurrent.CountDownLatch

open class PeerInfo(val host: String, open val port: Int, val pubKey: ByteArray)

class DynamicPortPeerInfo(host: String, pubKey: ByteArray): PeerInfo(host, 0, pubKey) {
    private val latch = CountDownLatch(1)
    private var assignedPortNumber = 0

    override val port: Int get() {
        latch.await()
        return assignedPortNumber
    }

    fun portAssigned(port: Int) {
        assignedPortNumber = port
        latch.countDown()
    }
}


typealias Signer = (ByteArray) -> Signature
typealias Verifier = (ByteArray, Signature) -> Boolean

interface PeerCommConfiguration {
    val peerInfo: Array<PeerInfo>
    val myIndex: Int
    fun getSigner(): Signer
    fun getVerifier(): Verifier
}

interface CryptoSystem {
    fun digest(bytes: ByteArray): ByteArray
    fun makeSigner(pubKey: ByteArray, privKey: ByteArray): Signer
    fun makeVerifier(): Verifier
}

// block builder which automatically manages the connection
interface ManagedBlockBuilder : BlockBuilder {

    fun maybeAppendTransaction(tx: Transaction): Boolean
    fun maybeAppendTransaction(txData: ByteArray): Boolean

    fun rollback()
}

interface Storage {
    fun openReadConnection(chainID: Int): EContext
    fun closeReadConnection(ectxt: EContext)

    fun openWriteConnection(chainID: Int): EContext
    fun closeWriteConnection(ectxt: EContext, commit: Boolean)


    fun withSavepoint( ctxt: EContext, fn: ()->Unit )
}

fun<RT> withReadConnection(s: Storage, chainID: Int, fn: (EContext)->RT): RT {
    val ctx = s.openReadConnection(chainID)
    try {
        return fn(ctx)
    } finally {
        s.closeReadConnection(ctx)
    }
}

fun withWriteConnection(s: Storage, chainID: Int, fn: (EContext)->Boolean): Boolean {
    val ctx = s.openWriteConnection(chainID)
    var commit = false
    try {
       commit = fn(ctx)
    } finally {
        s.closeWriteConnection(ctx, commit)
    }
    return commit
}


interface TransactionQueue {
    fun getTransactions(): Array<Transaction>
}