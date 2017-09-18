package com.chromaway.postchain.base

class BasePeerCommConfiguration(override val peerInfo: Array<PeerInfo>, override val myIndex: Int,
                                private val cryptoSystem: CryptoSystem, private val privKey: ByteArray) : PeerCommConfiguration {

    override fun getSigner(): Signer {
        return cryptoSystem.makeSigner(peerInfo[myIndex].pubKey, privKey)
    }

    override fun getVerifier(): Verifier {
        return cryptoSystem.makeVerifier()
    }
}