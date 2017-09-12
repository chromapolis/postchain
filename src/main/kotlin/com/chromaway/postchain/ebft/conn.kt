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
    override  fun stop() {}
    override fun sendPacket(b: ByteArray)  {}
}

open class PeerConnection(override var id: Int, val packetHandler: (Int, ByteArray) -> Unit, val log: (Exception) -> Unit)
    : AbstractPeerConnection {
    @Volatile protected var keepGoing: Boolean = true
    @Volatile protected var socket: Socket? = null
    private val outboundPackets = LinkedBlockingQueue<ByteArray>()

    protected fun readOnePacket(dataStream: DataInputStream): ByteArray {
        val packetSize = dataStream.readInt()
        if (packetSize > MAX_PAYLOAD_SIZE)
            throw Error("Packet too large")
        val bytes = ByteArray(packetSize)
        dataStream.readFully(bytes)
        return bytes
    }

    protected fun readPacketsWhilePossible(dataStream: DataInputStream): Exception? {
        try {
            while (keepGoing) {
                packetHandler(id, readOnePacket(dataStream))
            }
        } catch (e: Exception) {
            return e
        }
        return null
    }

    protected fun writeOnePacket(dataStream: DataOutputStream, bytes: ByteArray) {
        dataStream.writeInt(bytes.size)
        dataStream.write(bytes)
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

    override fun sendPacket(bytes: ByteArray) {
        if (!keepGoing) return
        outboundPackets.put(bytes)
    }
}

class PassivePeerConnection(
        val parseInitPacket: (ByteArray) -> Int,
        inSocket: Socket,
        packetHandler: (Int, ByteArray) -> Unit,
        log: (Exception) -> Unit,
        val registerConn: (PeerConnection) -> Unit
) : PeerConnection(-1, packetHandler, log) {

    init {
        socket = inSocket
        thread { readLoop(inSocket) }
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

            id = parseInitPacket(readOnePacket(stream))
            registerConn(this)

            thread { writeLoop(socket1) }

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
        val makeInitPacket: (Int) -> ByteArray,
        packetHandler: (Int, ByteArray) -> Unit,
        log: (Exception) -> Unit
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
                writeOnePacket(stream, makeInitPacket(id)) // write init packet
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
        thread { writeLoop() }
        thread { readLoop() }
    }
}

class PeerConnectionAcceptor(
        port: Int,
        val parseInitPacket: (ByteArray) -> Int,
        val packetHandler: (Int, ByteArray) -> Unit,
        val log: (Exception) -> Unit,
        val registerConn: (PeerConnection)->Unit

) {
    val serverSocket: ServerSocket
    @Volatile var keepGoing = true

    init {
        serverSocket = ServerSocket(port)
        thread { acceptLoop() }
    }

    private fun acceptLoop() {
        try {
            while (keepGoing) {
                val socket = serverSocket.accept()
                PassivePeerConnection(
                        parseInitPacket,
                        socket,
                        packetHandler,
                        log,
                        registerConn
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

class CommManager<PT> (val myIndex: Int,
                       val peers: Array<PeerInfo>,
                       val makeInitPacket: (Int) -> ByteArray,
                       val parseInitPacket: (ByteArray) -> Int,
                       val decoder: (Int, ByteArray) -> PT,
                       val encoder: (PT) -> ByteArray,
                       val log: (Exception) -> Unit)
{
    private val connections: Array<AbstractPeerConnection>
    var inboundPackets = mutableListOf<Pair<Int, PT>>()
    val outboundPackets = LinkedBlockingQueue<OutboundPacket<PT>>()
    @Volatile private var keepGoing: Boolean = true
    private val encoderThread: Thread
    private val connAcceptor: PeerConnectionAcceptor


    private fun decodeAndEnqueue(peerIndex: Int, packet: ByteArray) {
        // packet decoding should not be synchronized so we can make
        // use of parallel processing in different threads
        val decodedPacket = decoder(peerIndex, packet)
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
                val data = encoder(pkt.packet)
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
                        makeInitPacket,
                        { idx, packet -> decodeAndEnqueue(idx, packet) },
                        log)
                conn.start()
                connlist.add(conn)
            } else {
                connlist.add(NullPeerConnect(index))
            }
        }
        connections = connlist.toTypedArray()
        encoderThread = thread { encoderLoop() }
        connAcceptor = PeerConnectionAcceptor(
                peers[myIndex].port,
                parseInitPacket,
                { idx, packet -> decodeAndEnqueue(idx, packet) },
                log,
                { conn -> connlist[conn.id] = conn }
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
    return CommManager<Message>(
            pc.myIndex,
            peerInfo,
            { idx ->
                val gbah = GetBlockAtHeight()
                gbah.height = idx.toLong()
                encodeAndSign(Message.getBlockAtHeight(gbah), signer)
            },
            {
                bytes -> 0
            },
            { idx, packet -> decodeAndVerify(packet, peerInfo[idx].pubKey, verifier) },
            { packet -> encodeAndSign(packet, signer) },
            { e -> e.printStackTrace() }
    )

}