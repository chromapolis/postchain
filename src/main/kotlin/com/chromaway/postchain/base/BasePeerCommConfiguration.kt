package com.chromaway.postchain.base

class BasePeerCommConfiguration(override val peerInfo: Array<PeerInfo>, override val myIndex: Int) : PeerCommConfiguration {

    override fun getSigner(): Signer {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getVerifier(): Verifier {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}