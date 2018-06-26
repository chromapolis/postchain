// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

class BasePeerCommConfiguration(override val peerInfo: Array<PeerInfo>, override val myIndex: Int,
                                private val cryptoSystem: CryptoSystem, private val privKey: ByteArray) : PeerCommConfiguration {


    override fun resolvePeer(peerID: ByteArray): PeerInfo? {
        for (peer in peerInfo) {
            if (peer.pubKey.equals(peerID))
                return peer
        }
        return null
    }

    override fun getSigner(): Signer {
        return cryptoSystem.makeSigner(peerInfo[myIndex].pubKey, privKey)
    }

    override fun getVerifier(): Verifier {
        return cryptoSystem.makeVerifier()
    }
}