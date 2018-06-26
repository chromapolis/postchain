package net.postchain.network

interface BlockchainDataHandler {
    fun getPacketHandler(peerPubKey: ByteArray): (ByteArray) -> Unit
}