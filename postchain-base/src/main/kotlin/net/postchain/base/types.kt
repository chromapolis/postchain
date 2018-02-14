// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.core.*
import java.util.*
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

/**
 * Function that will sign some data and return a signature
 * */
typealias Signer = (ByteArray) -> Signature

/**
 * Function that will return a boolean depending on if the data and
 * signature applied to that data will properly verify
 * */
typealias Verifier = (ByteArray, Signature) -> Boolean

interface PeerCommConfiguration {
    val peerInfo: Array<PeerInfo>
    val myIndex: Int
    fun getSigner(): Signer
    fun getVerifier(): Verifier
}

/**
 * Cryptosystem implements necessary cryptographic functionalities
 */
interface CryptoSystem {
    fun digest(bytes: ByteArray): ByteArray
    fun makeSigner(pubKey: ByteArray, privKey: ByteArray): Signer
    fun verifyDigest(ddigest: ByteArray, s: Signature): Boolean
    fun makeVerifier(): Verifier
    fun getRandomBytes(size: Int): ByteArray
}

/**
 * A block builder which automatically manages the connection
 */
interface ManagedBlockBuilder : BlockBuilder {
    fun maybeAppendTransaction(tx: Transaction): Exception?
    fun rollback()
}

interface Storage {
    fun openReadConnection(chainID: Long): EContext
    fun closeReadConnection(ectxt: EContext)

    fun openWriteConnection(chainID: Long): EContext
    fun closeWriteConnection(ectxt: EContext, commit: Boolean)

    fun withSavepoint( ctxt: EContext, fn: ()->Unit ): Exception?

    fun close()
}

fun<RT> withReadConnection(s: Storage, chainID: Long, fn: (EContext)->RT): RT {
    val ctx = s.openReadConnection(chainID)
    try {
        return fn(ctx)
    } finally {
        s.closeReadConnection(ctx)
    }
}

fun withWriteConnection(s: Storage, chainID: Long, fn: (EContext)->Boolean): Boolean {
    val ctx = s.openWriteConnection(chainID)
    var commit = false
    try {
       commit = fn(ctx)
    } finally {
        s.closeWriteConnection(ctx, commit)
    }
    return commit
}

enum class Side {LEFT, RIGHT }

class MerklePathItem(val side: Side, val hash: ByteArray)

typealias MerklePath = ArrayList<MerklePathItem>

class ConfirmationProofMaterial(val txHash: ByteArray,
                                val txHashes: Array<ByteArray>,
                                val header: ByteArray,
                                val witness: ByteArray)