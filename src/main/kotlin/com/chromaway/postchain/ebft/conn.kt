package com.chromaway.postchain.ebft

import com.chromaway.postchain.ebft.messages.GetBlockAtHeight
import com.chromaway.postchain.ebft.messages.Message
import com.chromaway.postchain.base.PeerCommConfiguration
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

import com.chromaway.postchain.base.PeerInfo
import mu.KLogging
import java.net.ServerSocket
import java.util.concurrent.CyclicBarrier

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

open class PeerConnection(override var id: Int, val packetHandler: (Int, ByteArray) -> Unit,
                          val log: (Exception) -> Unit) : AbstractPeerConnection {
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
        logger.info("Packet received: ${String(bytes)}")
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
        logger.info("Packet sent: ${String(bytes)}")
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
        log: (Exception) -> Unit,
        val registerConn: (PeerConnection) -> Unit,
        val myIndex: Int
) : PeerConnection(-1, packetHandler, log) {

    init {
        socket = inSocket
        thread(name="$myIndex-PassiveReadLoop-PeerId-TBA") { readLoop(inSocket) }
    }

    private fun writeLoop(socket1: Socket) {
        try {
            val stream = DataOutputStream(socket1.getOutputStream())
            val err = writePacketsWhilePossible(stream)
             if (err != null) {
                log(err)
            }
            socket1.close()
        } catch (e: Exception) {
            log(e)
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
                log(err)
            }
        } catch (e: Exception) {
            log(e)
        }
    }
}

class ActivePeerConnection(
        id: Int,
        val host: String,
        val port: Int,
        val packetConverter: InitPacketConverter,
        packetHandler: (Int, ByteArray) -> Unit,
        log: (Exception) -> Unit,
        val myIndex: Int
) : PeerConnection(id, packetHandler, log) {

    val connAvail = CyclicBarrier(2)

    private fun writeLoop() {
        while (keepGoing) {
            try {
                if (socket != null && !(socket!!.isClosed)) socket!!.close()
                socket = Socket(host, port)
                // writer loop sets up a serverSocket then waits for read loop to sync
                // if exception is thrown when connecting, read loop will just wait for the next cycle
                connAvail.await()
                val socket1 = socket ?: throw Error("No connection")
                val stream = DataOutputStream(socket1.getOutputStream())
                writeOnePacket(stream, packetConverter.makeInitPacket(myIndex)) // write init packet
                val err = writePacketsWhilePossible(stream)
                if (err != null) {
                    log(err)
                }
                socket1.close()
            } catch (e: Exception) {
                log(e)
                Thread.sleep(100)
            }
        }
    }

    private fun readLoop() {
        while (keepGoing) {
            try {
                connAvail.await()
                val socket1 = socket ?: throw Error("No connection")
                val err = readPacketsWhilePossible(DataInputStream(socket1.getInputStream()))
                if (err != null) {
                    log(err)
                }
                socket1.close()
            } catch (e: Exception) {
                log(e)
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
        port: Int,
        val initPacketConverter: InitPacketConverter,
        val packetHandler: (Int, ByteArray) -> Unit,
        val log: (Exception) -> Unit,
        val registerConn: (PeerConnection)->Unit,
        val myIndex: Int

) {
    val serverSocket: ServerSocket
    @Volatile var keepGoing = true
    companion object : KLogging()

    init {
        logger.info("Starting server on port $port")
        serverSocket = ServerSocket(port)
        logger.info("Starting server on port $port done")
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
                        log,
                        registerConn,
                        myIndex
                )
            }
        } catch (e: Exception) {
            log(e)
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
                       val packetConverter: PacketConverter<PT>,
                       val log: (Exception) -> Unit)
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

    fun sendPacket(packet: PT, recipients: Set<Int>) {
        outboundPackets.put(OutboundPacket(packet, recipients))
    }

    private fun encoderLoop() {
        while (keepGoing) {
            val pkt = outboundPackets.take()
            if (!keepGoing) return
            try {
                val data = packetConverter.encodePacket(pkt.packet)
                for (idx in pkt.recipients) {
                    connections[idx].sendPacket(data)
                }
            } catch (e: Exception) {
                log(e)
            }
        }
    }

    init {
        val connlist = mutableListOf<AbstractPeerConnection>()
        for ((index, peer) in peers.withIndex()) {
            if (index < myIndex) {
                val conn = ActivePeerConnection(index, peer.host, peer.port,
                        packetConverter,
                        { idx, packet -> decodeAndEnqueue(idx, packet) },
                        log, myIndex)
                conn.start()
                connlist.add(conn)
            } else {
                connlist.add(NullPeerConnect(index))
            }
        }
        connections = connlist.toTypedArray()
        encoderThread = thread(name="$myIndex-encoderLoop") { encoderLoop() }
        connAcceptor = PeerConnectionAcceptor(
                peers[myIndex].port,
                packetConverter,
                { idx, packet -> decodeAndEnqueue(idx, packet) },
                log,
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

fun makeCommManager(pc: PeerCommConfiguration): CommManager<Message> {
    val peerInfo = pc.peerInfo
    val signer = pc.getSigner()
    val verifier = pc.getVerifier()

    val packetConverter = object: PacketConverter<Message> {
        override fun makeInitPacket(index: Int): ByteArray {
            val gbah = GetBlockAtHeight()
            gbah.height = index.toLong()
            return encodeAndSign(Message.getBlockAtHeight(gbah), signer)
        }
        override fun parseInitPacket(bytes: ByteArray): Int {
            return 0
        }
        override fun decodePacket(index: Int, bytes: ByteArray): Message {
            return decodeAndVerify(bytes, peerInfo[index].pubKey, verifier)
        }
        override fun encodePacket(packet: Message): ByteArray {
            return encodeAndSign(packet, signer)
        }
    }
    return CommManager<Message>(
            pc.myIndex,
            peerInfo,
            packetConverter,
            { e -> e.printStackTrace() }
    )

}