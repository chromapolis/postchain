package com.chromaway.postchain.engine

import com.chromaway.postchain.core.Signature

data class PeerInfo(val host: String, val port: Int, val pubKey: ByteArray)

typealias Signer = (ByteArray) -> Signature;
typealias Verifier = (ByteArray, Signature) -> Boolean;

interface PeerCommConfiguration {
    val peerInfo: Array<PeerInfo>
    fun getSigner(): Signer
    fun getVerifier(): Verifier
}

interface CryptoSystem {
    fun digest(bytes: ByteArray): ByteArray
    fun makeSigner(pubKey: ByteArray, privKey: ByteArray): Signer
    fun makeVerifier(): Verifier
}