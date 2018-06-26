// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft

import net.postchain.base.DynamicPortPeerInfo
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.common.toHex
import net.postchain.core.ProgrammerMistake
import net.postchain.core.UserMistake
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Identification
import net.postchain.ebft.message.SignedMessage
import mu.KLogging
import net.postchain.core.ByteArrayKey
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

val MAX_PAYLOAD_SIZE = 10000000

/*
    PeerConnection implements a simple protocol to send data packets (ByteArray:s)
    over a TCP/IP connection. The protocol doesn't care about meaning of packets,
    only their size.

    While the protocol is very generic, interface is designed to work with ebft:

    1. Connection is unreliable, no attempts are made to retransmit messages,
    they might be dropped at any time. We assume that this is handled at application level.
    2. Every peer has an integer ID meaning of which is understood only on higher level.
    3. sendPacket is non-blocking and doesn't guarantee delivery

    The protocol: each packet is preceded by 4-byte length as Java Int (big-endian).
    Thus max packet size is around 2 GB.
    The first packet sent by a connecting party is an identification packet.
    The first packet received by accepting party is an identification packet.

*/

val MAX_QUEUED_PACKETS = 100

interface AbstractPeerConnection {
    fun stop()
    fun sendPacket(b: ByteArray)
}

class NullPeerConnect() : AbstractPeerConnection {
    companion object : KLogging()

    override fun stop() {/*logger.info("")*/
    }

    override fun sendPacket(b: ByteArray) {/*logger.info(String(b))*/
    }
}

abstract class PeerConnection : AbstractPeerConnection {
    @Volatile
    protected var keepGoing: Boolean = true
    @Volatile
    var socket: Socket? = null
    private val outboundPackets = LinkedBlockingQueue<ByteArray>(MAX_QUEUED_PACKETS)

    companion object : KLogging()

    abstract fun handlePacket(pkt: ByteArray)

    protected fun readOnePacket(dataStream: DataInputStream): ByteArray {
        val packetSize = dataStream.readInt()
        if (packetSize > MAX_PAYLOAD_SIZE)
            throw Error("Packet too large")
        val bytes = ByteArray(packetSize)
        dataStream.readFully(bytes)
        logger.trace("Packet received. Length: ${bytes.size}")
        return bytes
    }

    protected fun readPacketsWhilePossible(dataStream: DataInputStream): Exception? {
        try {
            while (keepGoing) {
                val bytes = readOnePacket(dataStream)
                if (bytes.size == 0) {
                    // This is a special packet sent when other side is closing
                    // ignore
                    continue
                }
                handlePacket(bytes)
            }
        } catch (e: Exception) {
            outboundPackets.put(byteArrayOf())
            return e
        }
        return null
    }

    protected fun writeOnePacket(dataStream: DataOutputStream, bytes: ByteArray) {
        dataStream.writeInt(bytes.size)
        dataStream.write(bytes)
        logger.trace("Packet sent: ${bytes.size}")
    }

    protected fun writePacketsWhilePossible(dataStream: DataOutputStream): Exception? {
        try {
            while (keepGoing) {
                val bytes = outboundPackets.take()
                if (!keepGoing) return null
                writeOnePacket(dataStream, bytes)
            }
        } catch (e: Exception) {
            return e
        }
        return null
    }

    @Synchronized
    override fun stop() {
        keepGoing = false
        outboundPackets.put(byteArrayOf())
        socket?.close()
    }

    override fun sendPacket(b: ByteArray) {
        if (!keepGoing) return
        if (outboundPackets.size >= MAX_QUEUED_PACKETS) {
            outboundPackets.poll()
        }
        outboundPackets.put(b)
    }
}

class PassivePeerConnection(
        val packetConverter: IdentPacketConverter,
        inSocket: Socket,
        val registerConn: (info: IdentPacketInfo, PeerConnection) -> (ByteArray) -> Unit
) : PeerConnection() {

    lateinit var packetHandler: (ByteArray) -> Unit

    override fun handlePacket(pkt: ByteArray) {
        packetHandler(pkt)
    }

    init {
        socket = inSocket
        thread(name = "PassiveReadLoop-PeerId-TBA") { readLoop(inSocket) }
    }

    private fun writeLoop(socket1: Socket) {
        try {
            val stream = DataOutputStream(socket1.getOutputStream())
            val err = writePacketsWhilePossible(stream)
            if (err != null) {
                logger.debug("closing socket to: ${err.message}")
            }
            socket1.close()
        } catch (e: Exception) {
            logger.error("failed to cleanly close connection to", e)
        }
    }

    fun readLoop(socket1: Socket) {
        try {
            val stream = DataInputStream(socket1.getInputStream())

            val info = packetConverter.parseIdentPacket(readOnePacket(stream))
            Thread.currentThread().name = "PassiveReadLoop-PeerId"
            packetHandler = registerConn(info, this)

            thread(name = "PassiveWriteLoop-PeerId") { writeLoop(socket1) }

            val err = readPacketsWhilePossible(stream)
            if (err != null) {
                logger.debug("reading packet from stopped: ${err.message}")
                stop()
            }
        } catch (e: Exception) {
            logger.error("readLoop failed", e)
            stop()
        }
    }
}

class ActivePeerConnection(
        val peer: PeerInfo,
        val packetConverter: IdentPacketConverter,
        val packetHandler: (ByteArray) -> Unit
) : PeerConnection() {

    val connAvail = CyclicBarrier(2)

    override fun handlePacket(pkt: ByteArray) {
        packetHandler(pkt)
    }

    private fun writeLoop() {
        while (keepGoing) {
            try {
                if (socket != null && !(socket!!.isClosed)) socket!!.close()
                socket = Socket(peer.host, peer.port)
                // writer loop sets up a serverSocket then waits for read loop to sync
                // if exception is thrown when connecting, read loop will just wait for the next cycle
                connAvail.await()
                val socket1 = socket ?: throw Exception("No connection")
                val stream = DataOutputStream(socket1.getOutputStream())
                TODO("add peer id")
                writeOnePacket(stream, packetConverter.makeIdentPacket(ByteArray(0))) // write Ident packet
                val err = writePacketsWhilePossible(stream)
                if (err != null) {
                    logger.debug(" sending packet to  failed: ${err.message}")
                }
                socket1.close()
            } catch (e: Exception) {
                logger.debug(" disconnected from : ${e.message}")
                Thread.sleep(2500)
            }
        }
    }

    private fun readLoop() {
        while (keepGoing) {
            try {
                connAvail.await()
                val socket1 = socket ?: throw Exception("No connection")
                val err = readPacketsWhilePossible(DataInputStream(socket1.getInputStream()))
                if (err != null) {
                    logger.debug("reading packet from  failed: ${err.message}")
                }
                socket1.close()
            } catch (e: Exception) {
                logger.debug("readLoop for failed. Will retry. ${e.message}")
                Thread.sleep(2500)
            }
        }
    }

    fun start() {
        thread(name = "-ActiveWriteLoop-PeerId-") { writeLoop() }
        thread(name = "-ActiveReadLoop-PeerId-") { readLoop() }
    }
}

class PeerConnectionAcceptor(
        peer: PeerInfo,
        val IdentPacketConverter: IdentPacketConverter,
        val registerConn: (IdentPacketInfo, PeerConnection) -> (ByteArray) -> Unit

) {
    val serverSocket: ServerSocket
    @Volatile
    var keepGoing = true

    companion object : KLogging()

    init {
        if (peer is DynamicPortPeerInfo) {
            serverSocket = ServerSocket(0)
            peer.portAssigned(serverSocket.localPort)
        } else {
            serverSocket = ServerSocket(peer.port)
        }
        logger.info("Starting server on port ${peer.port} done")
        thread(name = "-acceptLoop") { acceptLoop() }
    }

    private fun acceptLoop() {
        try {
            while (keepGoing) {
                val socket = serverSocket.accept()
                logger.info("accept socket")
                PassivePeerConnection(
                        IdentPacketConverter,
                        socket,
                        registerConn
                )
            }
        } catch (e: Exception) {
            logger.error("exiting accept loop")
        }
    }

    fun stop() {
        keepGoing = false
        serverSocket.close()
    }

}

typealias PeerID = ByteArray

data class OutboundPacket<PT>(val packet: PT, val recipients: List<AbstractPeerConnection>)
data class IdentPacketInfo(val peerID: PeerID, val blockchainRID: ByteArray)

interface IdentPacketConverter {
    fun makeIdentPacket(forPeer: PeerID): ByteArray
    fun parseIdentPacket(bytes: ByteArray): IdentPacketInfo
}

interface PacketConverter<PT> : IdentPacketConverter {
    fun decodePacket(pubKey: ByteArray, bytes: ByteArray): PT
    fun encodePacket(packet: PT): ByteArray
}

class PeerConnectionManager<PT>(val myPeerInfo: PeerInfo, val packetConverter: PacketConverter<PT>) {
    val connections = mutableListOf<AbstractPeerConnection>()
    @Volatile private var keepGoing: Boolean = true
    private val encoderThread: Thread
    private val connAcceptor: PeerConnectionAcceptor

    private val blockchains = mutableMapOf<ByteArrayKey, CommManager<PT>>()

    val outboundPackets = LinkedBlockingQueue<OutboundPacket<PT>>(MAX_QUEUED_PACKETS)

    companion object : KLogging()

    private fun encoderLoop() {
        while (keepGoing) {
            try {
                val pkt = outboundPackets.take()
                if (!keepGoing) return
                val data = packetConverter.encodePacket(pkt.packet)

                for (r in pkt.recipients) {
                    r.sendPacket(data)
                }
            } catch (e: InterruptedException) {
                logger.debug { "interrupted while taking next outbound packet" }
            } catch (e: Exception) {
                logger.debug("Exception in encoderLoop", e)
            }
        }
    }

    fun sendPacket(packet: OutboundPacket<PT>) {
        outboundPackets.add(packet)
    }

    fun connectPeer(peer: PeerInfo, packetConverter: IdentPacketConverter, packetHandler: (ByteArray) -> Unit): AbstractPeerConnection {
        val conn = ActivePeerConnection(peer,
                packetConverter,
                packetHandler
        )
        conn.start()
        connections.add(conn)
        return conn
    }

    fun stop() {
        keepGoing = false
        connAcceptor.stop()
        for (c in connections) c.stop()
        encoderThread.interrupt()
    }

    init {
        encoderThread = thread(name = "encoderLoop") { encoderLoop() }

        val registerConn = {
            info: IdentPacketInfo, conn: PeerConnection ->

            val commManager = blockchains[ByteArrayKey(info.blockchainRID)]
            commManager!!.getPacketHandler(info.peerID)
            // { conn -> logger.info("$myIndex Registering ${conn.id} $conn");connections[conn.id] = conn }
        }

        connAcceptor = PeerConnectionAcceptor(
                myPeerInfo,
                packetConverter, registerConn
        )
    }

}

class CommManager<PT>(val myIndex: Int,
                      val peers: Array<PeerInfo>,
                      val packetConverter: PacketConverter<PT>,
                      val connManager: PeerConnectionManager<PT>
) {
    val connections: Array<AbstractPeerConnection>
    var inboundPackets = mutableListOf<Pair<Int, PT>>()

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

    fun getPacketHandler(peerPubKey: ByteArray): (ByteArray) -> Unit {
        val peerIndex = peers.indexOfFirst { it.pubKey.equals(peerPubKey) }
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
        connManager.sendPacket(OutboundPacket(packet, connections.toList()))
    }

    fun sendPacket(packet: PT, recipients: Set<Int>) {
        if (recipients.isEmpty()) {
            // Using recipients=emptySet() to broadcast may cause
            // code to accidentaly broadcast, when in fact they want to send
            // the packet to exactly no recipients. So we don't allow that.
            throw ProgrammerMistake("Cannot send to no recipients. If you want to broadcast, please use broadcastPacket() instead")
        }
        logger.trace("Sending $myIndex -> $recipients: $packet")
        connManager.sendPacket(OutboundPacket(packet, recipients.map { connections[it]}))
    }


    init {
        val connlist = mutableListOf<AbstractPeerConnection>()
        for ((index, peer) in peers.withIndex()) {
            if (index < myIndex) {
               connlist.add(connManager.connectPeer(peer, packetConverter,
                       { decodeAndEnqueue(index, it)}))
            } else {
                connlist.add(NullPeerConnect())
            }
        }
        connections = connlist.toTypedArray()
    }
}

/*
val peerIndex = peerInfo.indexOfFirst { it.pubKey.contentEquals(signedMessage.pubKey) }
            if (peerIndex == -1) {
                throw UserMistake("I don't know pubkey ${signedMessage.pubKey.toHex()}")
            }
 */

fun makeCommManager(pc: PeerCommConfiguration): CommManager<EbftMessage> {
    val peerInfo = pc.peerInfo
    val signer = pc.getSigner()
    val verifier = pc.getVerifier()

    val packetConverter = object : PacketConverter<EbftMessage> {
        override fun makeIdentPacket(peerID: ByteArray): ByteArray {
            val bytes = Identification(peerID, System.currentTimeMillis()).encode()
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
            return IdentPacketInfo(signedMessage.pubKey, ByteArray(0))
        }

        override fun decodePacket(pubKey: ByteArray, bytes: ByteArray): EbftMessage {
            return decodeAndVerify(bytes, pubKey, verifier)
        }

        override fun encodePacket(packet: EbftMessage): ByteArray {
            return encodeAndSign(packet, signer)
        }
    }
    val connManager = PeerConnectionManager<EbftMessage>(peerInfo[pc.myIndex], packetConverter)
    return CommManager<EbftMessage>(
            pc.myIndex,
            peerInfo,
            packetConverter, connManager
    )

}