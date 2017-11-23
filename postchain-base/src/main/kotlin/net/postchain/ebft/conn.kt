// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft

import net.postchain.base.DynamicPortPeerInfo
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.base.toHex
import net.postchain.core.ProgrammerMistake
import net.postchain.core.UserMistake
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Identification
import net.postchain.ebft.message.SignedMessage
import mu.KLogging
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

interface AbstractPeerConnection {
    var id: Int
    fun stop()
    fun sendPacket(b: ByteArray)
}

class NullPeerConnect(override var id: Int) : AbstractPeerConnection {
    companion object : KLogging()

    override  fun stop() {/*logger.info("")*/}
    override fun sendPacket(b: ByteArray)  {/*logger.info(String(b))*/}
}

open class PeerConnection(override var id: Int, val packetHandler: (Int, ByteArray) -> Unit) : AbstractPeerConnection {
    @Volatile protected var keepGoing: Boolean = true
    @Volatile var socket: Socket? = null
    private val outboundPackets = LinkedBlockingQueue<ByteArray>()
    companion object : KLogging()

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
                packetHandler(id, bytes)
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
        outboundPackets.put(b)
    }
}

class PassivePeerConnection(
        val packetConverter: InitPacketConverter,
        inSocket: Socket,
        packetHandler: (Int, ByteArray) -> Unit,
        val registerConn: (PeerConnection) -> Unit,
        val myIndex: Int
) : PeerConnection(-1, packetHandler) {

    init {
        socket = inSocket
        thread(name="$myIndex-PassiveReadLoop-PeerId-TBA") { readLoop(inSocket) }
    }

    private fun writeLoop(socket1: Socket) {
        try {
            val stream = DataOutputStream(socket1.getOutputStream())
            val err = writePacketsWhilePossible(stream)
            if (err != null) {
                logger.debug("$myIndex closing socket to $id: ${err.message}")
            }
            socket1.close()
        } catch (e: Exception) {
            logger.error("$myIndex failed to cleanly close connection to $id", e)
        }
    }

    fun readLoop(socket1: Socket) {
        try {
            val stream = DataInputStream(socket1.getInputStream())

            id = packetConverter.parseInitPacket(readOnePacket(stream))
            Thread.currentThread().name = "$myIndex-PassiveReadLoop-PeerId-$id"
            registerConn(this)

            thread(name="$myIndex-PassiveWriteLoop-PeerId-$id") { writeLoop(socket1) }

            val err = readPacketsWhilePossible(stream)
            if (err != null) {
                logger.debug("$myIndex reading packet from ${id} stopped: ${err.message}")
            }
        } catch (e: Exception) {
            logger.error("$myIndex readLoop failed", e)
        }
    }
}

class ActivePeerConnection(
        id: Int,
        val peer: PeerInfo,
        val packetConverter: InitPacketConverter,
        packetHandler: (Int, ByteArray) -> Unit,
        val myIndex: Int
) : PeerConnection(id, packetHandler) {

    val connAvail = CyclicBarrier(2)

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
                writeOnePacket(stream, packetConverter.makeInitPacket(id)) // write init packet
                val err = writePacketsWhilePossible(stream)
                if (err != null) {
                    logger.debug("$myIndex sending packet to ${id} failed: ${err.message}")
                }
                socket1.close()
            } catch (e: Exception) {
                logger.debug("$myIndex disconnected from ${id}: ${e.message}")
                Thread.sleep(100)
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
                    logger.debug("$myIndex reading packet from ${id} failed: ${err.message}")
                }
                socket1.close()
            } catch (e: Exception) {
                logger.debug("$myIndex readLoop for $id failed. Will retry. ${e.message}")
                Thread.sleep(100)
            }
        }
    }

    fun start() {
        thread(name="$myIndex-ActiveWriteLoop-PeerId-$id") { writeLoop() }
        thread(name="$myIndex-ActiveReadLoop-PeerId-$id") { readLoop() }
    }
}

class PeerConnectionAcceptor(
        peer: PeerInfo,
        val initPacketConverter: InitPacketConverter,
        val packetHandler: (Int, ByteArray) -> Unit,
        val registerConn: (PeerConnection)->Unit,
        val myIndex: Int

) {
    val serverSocket: ServerSocket
    @Volatile var keepGoing = true
    companion object : KLogging()

    init {
        if (peer is DynamicPortPeerInfo) {
            serverSocket = ServerSocket(0)
            peer.portAssigned(serverSocket.localPort)
        } else {
            serverSocket = ServerSocket(peer.port)
        }
        logger.info("Starting server on port ${peer.port} done")
        thread(name="$myIndex-acceptLoop") { acceptLoop() }
    }

    private fun acceptLoop() {
        try {
            while (keepGoing) {
                val socket = serverSocket.accept()
                logger.info("${myIndex} accept socket")
                PassivePeerConnection(
                        initPacketConverter,
                        socket,
                        packetHandler,
                        registerConn,
                        myIndex
                )
            }
        } catch (e: Exception) {
            logger.debug("${myIndex} exiting accept loop")
        }
    }

    fun stop() {
        keepGoing = false
        serverSocket.close()
    }

}

data class OutboundPacket<PT>(val packet: PT, val recipients: Set<Int>)

interface InitPacketConverter {
    fun makeInitPacket(index: Int): ByteArray
    fun parseInitPacket(bytes: ByteArray): Int
}

interface PacketConverter<PT>: InitPacketConverter {
    fun decodePacket(index: Int, bytes: ByteArray): PT
    fun encodePacket(packet: PT): ByteArray
}

class CommManager<PT> (val myIndex: Int,
                       peers: Array<PeerInfo>,
                       val packetConverter: PacketConverter<PT>)
{
    val connections: Array<AbstractPeerConnection>
    var inboundPackets = mutableListOf<Pair<Int, PT>>()
    val outboundPackets = LinkedBlockingQueue<OutboundPacket<PT>>()
    @Volatile private var keepGoing: Boolean = true
    private val encoderThread: Thread
    private val connAcceptor: PeerConnectionAcceptor
    companion object : KLogging()

    private fun decodeAndEnqueue(peerIndex: Int, packet: ByteArray) {
        // packet decoding should not be synchronized so we can make
        // use of parallel processing in different threads
        val decodedPacket = packetConverter.decodePacket(peerIndex, packet)
        logger.trace("Receiving $peerIndex -> ${myIndex}: $decodedPacket")
        synchronized(this) {
            inboundPackets.add(Pair(peerIndex, decodedPacket))
        }
    }

    @Synchronized
    fun getPackets(): MutableList<Pair<Int, PT>> {
        val currentQueue = inboundPackets
        inboundPackets = mutableListOf<Pair<Int, PT>>()
        return currentQueue
    }

    fun broadcastPacket(packet: PT) {
        outboundPackets.put(OutboundPacket(packet, emptySet()))
    }

    fun sendPacket(packet: PT, recipients: Set<Int>) {
        if (recipients.isEmpty()) {
            // Using recipients=emptySet() to broadcast may cause
            // code to accidentaly broadcast, when in fact they want to send
            // the packet to exactly no recipients. So we don't allow that.
            throw ProgrammerMistake("Cannot send to no recipients. If you want to broadcast, please use broadcastPacket() instead")
        }
        logger.trace("Sending $myIndex -> $recipients: $packet")
        outboundPackets.put(OutboundPacket(packet, recipients))
    }

    private fun encoderLoop() {
        while (keepGoing) {
            try {
                val pkt = outboundPackets.take()
                if (!keepGoing) return
                val data = packetConverter.encodePacket(pkt.packet)
                if (pkt.recipients.isEmpty()) {
                    // Don't mind avoiding myIndex, just send to NullCommChannel. It's fine
                    connections.forEach { it.sendPacket(data) }
                } else {
                    for (idx in pkt.recipients) {
                        connections[idx].sendPacket(data)
                    }
                }
            } catch (e: InterruptedException) {
                logger.debug { "${myIndex} interrupted while taking next outbound packet" }
            } catch (e: Exception) {
                logger.debug("${myIndex} Exception in encoderLoop", e)
            }
        }
    }

    init {
        val connlist = mutableListOf<AbstractPeerConnection>()
        for ((index, peer) in peers.withIndex()) {
            if (index < myIndex) {
                val conn = ActivePeerConnection(index, peer,
                        packetConverter,
                        { idx, packet -> decodeAndEnqueue(idx, packet) },
                        myIndex)
                conn.start()
                connlist.add(conn)
            } else {
                connlist.add(NullPeerConnect(index))
            }
        }
        connections = connlist.toTypedArray()
        encoderThread = thread(name="$myIndex-encoderLoop") { encoderLoop() }
        connAcceptor = PeerConnectionAcceptor(
                peers[myIndex],
                packetConverter,
                { idx, packet -> decodeAndEnqueue(idx, packet) },
                { conn -> logger.info("$myIndex Registering ${conn.id} $conn");connections[conn.id] = conn },
                myIndex
        )
    }

    fun stop () {
        keepGoing = false
        connAcceptor.stop()
        for (c in connections) c.stop()
        encoderThread.interrupt()
    }

}

fun makeCommManager(pc: PeerCommConfiguration): CommManager<EbftMessage> {
    val peerInfo = pc.peerInfo
    val signer = pc.getSigner()
    val verifier = pc.getVerifier()

    val packetConverter = object: PacketConverter<EbftMessage> {
        override fun makeInitPacket(index: Int): ByteArray {
            val bytes = Identification(peerInfo[index].pubKey, System.currentTimeMillis()).encode()
            val signature = signer(bytes)
            return SignedMessage(bytes, peerInfo[pc.myIndex].pubKey, signature.data).encode()
        }
        override fun parseInitPacket(bytes: ByteArray): Int {
            val signedMessage = decodeSignedMessage(bytes)
            val peerIndex = peerInfo.indexOfFirst { it.pubKey.contentEquals(signedMessage.pubKey) }
            if (peerIndex == -1) {
                throw UserMistake("I don't know pubkey ${signedMessage.pubKey.toHex()}")
            }
            val message = decodeAndVerify(bytes, peerInfo[peerIndex].pubKey, verifier)

            if (message !is Identification) {
                throw UserMistake("Packet was not an Identification. Got ${message::class}")
            }

            if (!peerInfo[pc.myIndex].pubKey.contentEquals(message.yourPubKey)) {
                throw UserMistake("'yourPubKey' ${message.yourPubKey.toHex()} of Identification is not mine")
            }
            return peerIndex
        }
        override fun decodePacket(index: Int, bytes: ByteArray): EbftMessage {
            return decodeAndVerify(bytes, peerInfo[index].pubKey, verifier)
        }
        override fun encodePacket(packet: EbftMessage): ByteArray {
            return encodeAndSign(packet, signer)
        }
    }
    return CommManager<EbftMessage>(
            pc.myIndex,
            peerInfo,
            packetConverter
    )

}