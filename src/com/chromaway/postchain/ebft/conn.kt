package com.chromaway.postchain.ebft

import com.chromaway.postchain.ebft.messages.Message
import com.chromaway.postchain.engine.PeerCommConfiguration
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

import com.chromaway.postchain.engine.PeerInfo

val MAX_PAYLOAD_SIZE = 10000000

open class PeerConnection<PT>(val id: Int, val receiver: (PT) -> Unit, val decoder: (Int, ByteArray) -> PT, val log: (Exception) -> Unit) {
    @Volatile protected var keepGoing: Boolean = true
    @Volatile protected var socket: Socket? = null
    private val outboundPackets = LinkedBlockingQueue<ByteArray>()
    protected fun readPacketsWhilePossible(dataStream: DataInputStream): Exception? {
        try {
            while (keepGoing) {
                val packetSize = dataStream.readInt()
                if (packetSize > MAX_PAYLOAD_SIZE)
                    throw Error("Packet too large")
                val bytes = ByteArray(packetSize)
                dataStream.readFully(bytes)
                receiver(decoder(id, bytes))
            }
        } catch (e: Exception) {
            return e
        }
        return null
    }

    protected fun writePacketsWhilePossible(dataStream: DataOutputStream): Exception? {
        try {
            while (keepGoing) {
                val bytes = outboundPackets.take()
                if (!keepGoing) return null
                dataStream.write(bytes)
            }
        } catch (e: Exception) {
            return e
        }
        return null
    }

    @Synchronized
    fun stop() {
        keepGoing = false
        outboundPackets.put(byteArrayOf())
        socket?.close()
    }

    fun sendPacket(bytes: ByteArray) {
        if (!keepGoing) return
        outboundPackets.put(bytes)
    }
}

class ActivePeerConnection<PT> (id: Int,
                                val host: String,
                                val port: Int,
                                receiver: (PT) -> Unit,
                                decoder: (Int, ByteArray) -> PT,
                                log: (Exception) -> Unit) : PeerConnection<PT>(id, receiver, decoder, log) {

    @Synchronized
    private fun getConnection(): Socket {
        var s = socket
        if (s != null && s.isConnected && !s.isClosed) {
            return s
        } else {
            if (s != null) s.close()
            s = Socket(host, port)
            socket = s
            return s
        }
    }

    private fun writeLoop() {
        while (keepGoing) {
            try {
                val socket1 = getConnection()
                val err = writePacketsWhilePossible(DataOutputStream(socket1.getOutputStream()))
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
                val socket1 = getConnection()
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
        thread { readLoop() }
        thread { writeLoop() }
    }
}

data class OutboundPacket<PT>(val packet: PT, val recipients: Set<Int>)

class CommManager<PT> (val peers: Array<PeerInfo>,
                       val decoder: (Int, ByteArray) -> PT,
                       val encoder: (PT) -> ByteArray,
                       val log: (Exception) -> Unit)
{
    private lateinit var connections: Array<PeerConnection<PT>>
    var inboundPackets = mutableListOf<Pair<Int, PT>>()
    val outboundPackets = LinkedBlockingQueue<OutboundPacket<PT>>()
    @Volatile private var keepGoing: Boolean = true
    private lateinit var encoderThread: Thread

    @Synchronized
    private fun enqueuePacket(peerIndex: Int, packet: PT) {
        inboundPackets.add(Pair(peerIndex, packet))
    }

    @Synchronized
    fun getPackets(): MutableList<Pair<Int, PT>> {
        val currentQueue = inboundPackets;
        inboundPackets = mutableListOf<Pair<Int, PT>>();
        return currentQueue
    }

    fun sendPacket(packet: PT, recipients: Set<Int>) {
        outboundPackets.put(OutboundPacket(packet, recipients));
    }

    private fun encoderLoop() {
        while (keepGoing) {
            val pkt = outboundPackets.take();
            if (!keepGoing) return
            try {
                val data = encoder(pkt.packet);
                for (idx in pkt.recipients) {
                    connections[idx].sendPacket(data)
                }
            } catch (e: Exception) {
                log(e)
            }
        }
    }

    fun initialize () {
        val connlist = mutableListOf<PeerConnection<PT>>()
        for ((index, peer) in peers.withIndex()) {
            val conn = ActivePeerConnection(index, peer.host, peer.port,
                    { packet -> enqueuePacket(index, packet)},
                    decoder,
                    log)
            conn.start()
            connlist.add(conn)
        }
        connections = connlist.toTypedArray()
        encoderThread = thread { encoderLoop() }
    }

    fun stop () {
        keepGoing = false
        for (c in connections) c.stop()
        encoderThread.interrupt()
    }

}

fun makeCommManager(pc: PeerCommConfiguration): CommManager<Message> {
    val peerInfo = pc.peerInfo
    val signer = pc.getSigner()
    val verifier = pc.getVerifier()
    return CommManager<Message>(
            peerInfo,
            { idx, packet -> decodeAndVerify(packet, peerInfo[idx].pubKey, verifier) },
            { packet -> encodeAndSign(packet, signer) },
            { e -> e.printStackTrace() }
    )

}