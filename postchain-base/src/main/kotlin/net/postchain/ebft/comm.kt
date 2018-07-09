package net.postchain.ebft

import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.common.toHex
import net.postchain.core.ByteArrayKey
import net.postchain.core.ProgrammerMistake
import net.postchain.core.UserMistake
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Identification
import net.postchain.ebft.message.SignedMessage
import net.postchain.network.*

class CommManager<PT>(val myIndex: Int,
                      val blockchainRID: ByteArray,
                      val peers: Array<PeerInfo>,
                      val packetConverter: PacketConverter<PT>,
                      val connManager: PeerConnectionManager<PT>
) : BlockchainDataHandler {

    var inboundPackets = mutableListOf<Pair<Int, PT>>()
    val peerIDs = peers.map { ByteArrayKey(it.pubKey) }

    companion object : KLogging()

    private fun decodeAndEnqueue(peerIndex: Int, packet: ByteArray) {
        // packet decoding should not be synchronized so we can make
        // use of parallel processing in different threads
        val decodedPacket = packetConverter.decodePacket(peers[peerIndex].pubKey, packet)
        logger.trace("Receiving $peerIndex -> ${myIndex}: $decodedPacket")
        synchronized(this) {
            inboundPackets.add(Pair(peerIndex, decodedPacket))
        }
    }

    override fun getPacketHandler(peerPubKey: ByteArray): (ByteArray) -> Unit {
        val peerIndex = peers.indexOfFirst { it.pubKey.contentEquals(peerPubKey) }
        if (peerIndex > -1) {
            return { decodeAndEnqueue(peerIndex, it) }
        } else {
            TODO("Handle read-only peer?")
            //throw UserMistake("Got connection from unknown peer")
        }
    }

    @Synchronized
    fun getPackets(): MutableList<Pair<Int, PT>> {
        val currentQueue = inboundPackets
        inboundPackets = mutableListOf<Pair<Int, PT>>()
        return currentQueue
    }

    fun broadcastPacket(packet: PT) {
        connManager.sendPacket(OutboundPacket(packet, peerIDs))
    }

    fun sendPacket(packet: PT, recipients: Set<Int>) {
        if (recipients.isEmpty()) {
            // Using recipients=emptySet() to broadcast may cause
            // code to accidentaly broadcast, when in fact they want to send
            // the packet to exactly no recipients. So we don't allow that.
            throw ProgrammerMistake("Cannot send to no recipients. If you want to broadcast, please use broadcastPacket() instead")
        }
        logger.trace("Sending $myIndex -> $recipients: $packet")
        connManager.sendPacket(OutboundPacket(packet, recipients.map { peerIDs[it] }))
    }


    init {
        connManager.registerBlockchain(blockchainRID, this)
        for ((index, peer) in peers.withIndex()) {
            if (index < myIndex) {
                connManager.connectPeer(peer, packetConverter,
                        { decodeAndEnqueue(index, it) })
            }
        }
    }
}

fun makeConnManager(pc: PeerCommConfiguration): PeerConnectionManager<EbftMessage> {
    val peerInfo = pc.peerInfo
    val signer = pc.getSigner()
    val verifier = pc.getVerifier()

    val packetConverter = object : PacketConverter<EbftMessage> {
        override fun makeIdentPacket(forPeer: ByteArray): ByteArray {
            val bytes = Identification(forPeer, pc.blockchainRID, System.currentTimeMillis()).encode()
            val signature = signer(bytes)
            return SignedMessage(bytes, peerInfo[pc.myIndex].pubKey, signature.data).encode()
        }

        override fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo {
            val signedMessage = decodeSignedMessage(bytes)

            val message = decodeAndVerify(bytes, signedMessage.pubKey, verifier)

            if (message !is Identification) {
                throw UserMistake("Packet was not an Identification. Got ${message::class}")
            }

            if (!peerInfo[pc.myIndex].pubKey.contentEquals(message.yourPubKey)) {
                throw UserMistake("'yourPubKey' ${message.yourPubKey.toHex()} of Identification is not mine")
            }
            return IdentPacketInfo(signedMessage.pubKey, message.blockchainRID)
        }

        override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): EbftMessage {
            return decodeAndVerify(bytes, pubKey, verifier)
        }

        override fun encodePacket(packet: EbftMessage): ByteArray {
            return encodeAndSign(packet, signer)
        }
    }
    return PeerConnectionManager<EbftMessage>(peerInfo[pc.myIndex], packetConverter)
}

fun makeCommManager(pc: PeerCommConfiguration, connManager: PeerConnectionManager<EbftMessage>): CommManager<EbftMessage> {
    return CommManager<EbftMessage>(
            pc.myIndex,
            pc.blockchainRID,
            pc.peerInfo,
            connManager.packetConverter, connManager
    )
}