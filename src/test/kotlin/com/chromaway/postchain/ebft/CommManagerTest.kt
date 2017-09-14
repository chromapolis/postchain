package com.chromaway.postchain.ebft

import com.chromaway.postchain.base.BasePeerCommConfiguration
import com.chromaway.postchain.base.IntegrationTest
import com.chromaway.postchain.base.PeerCommConfiguration
import com.chromaway.postchain.base.PeerInfo
import com.chromaway.postchain.core.UserError
import com.chromaway.postchain.parseInt
import mu.KLogging
import org.junit.After
import org.junit.Test


class CommManagerTest : IntegrationTest() {
    companion object : KLogging()
//
//    @Rule @JvmField
//    val globalTimeout = Timeout.seconds(2) // 10 seconds max per method tested

    @Test
    fun testLogging() {
        logger.error("START")
        System.out.println("System.out.println")
    }

    var commManagers = emptyList<CommManager<DummyMessage>>()

    @After
    fun tearDownCommManagers() {
        commManagers.forEach { it.stop() }
    }

    @Test
    fun testTwoNodes() {
        setupCommManagers()

        // If test fails, send() will never return.
        // That's why we don't have any assertions
        send("HelloA", 0, 1)
        send("HelloB", 1, 0)
    }

    @Test
    fun testAutomaticReconnectIfPassiveStops() {
        setupCommManagers()
        send("Hello", 0, 1)

        // Stop the passive side of the connection.
        breakSocket(0, 1)
        awaitNoMorePackets(commManagers[1])

        send("A", 1, 0)
        send("B", 0, 1)
    }

    @Test
    fun testAutomaticReconnectIfActiveStops() {
        setupCommManagers()
        send("Hello from 0", 0, 1)

        // Stop the active side of the connection.
        breakSocket(1, 0)
        awaitNoMorePackets(commManagers[1])

        send("A", 1, 0)
        send("B", 0, 1)
    }

    @Test
    fun test100Nodes() {
        setupCommManagers(10)
        send("Hi all", 9, *IntArray(9, {it}))
    }

    fun breakSocket(nodeIndex: Int, connectionIndex: Int) {
        (commManagers[nodeIndex].connections[connectionIndex] as PeerConnection).socket?.close()
    }

    private fun awaitNoMorePackets(commManager: CommManager<DummyMessage>) {
        // Wait until no more incoming packets
        var packets = commManager.getPackets()
        while (!packets.isEmpty()) {
            Thread.sleep(1)
            packets = commManager.getPackets()
        }
    }

    private fun setupCommManagers(count: Int = 2) {
        val pubKeys = Array<ByteArray>(count, {ByteArray(33, {it.toByte()})})
        val peerInfos = Array(count, { PeerInfo("localhost", 53190 + it, pubKeys[it]) })
        val peerCommConfigurations = Array(count, { BasePeerCommConfiguration(peerInfos, it) })
        commManagers = peerCommConfigurations.map { makeCommManager(it) }
    }
//
//    private fun send(message: String, from: Int, to: Int) {
//        while (true) {
//            val dummyMessage = DummyMessage(from, message)
//            commManagers[from].sendPacket(dummyMessage, setOf(to))
//            Thread.sleep(1);
//            val packets = commManagers[to].getPackets()
//            packets.forEach {
//                if (it.first == from && it.second.index == from
//                        && it.second.text.equals(message)) {
//                    return
//                }
//            }
//        }
//    }

    private fun send(message: String, from: Int, vararg to: Int) {
        val remaining = to.toMutableSet()
        while (true) {
            val dummyMessage = DummyMessage(from, message)
            commManagers[from].sendPacket(dummyMessage, to.toSet())
            Thread.sleep(1)
            remaining.toList().forEach {recipient ->
                val packets = commManagers[recipient].getPackets()
                packets.forEach commManager@ {
                    if (it.first == from && it.second.index == from
                            && it.second.text.equals(message)) {
                        remaining.remove(recipient)
                        return@commManager
                    }
                }
            }
            if (remaining.isEmpty()) return
        }
    }

    data class DummyMessage(val index: Int, val text: String)

    class DummyPacketConverter : PacketConverter<DummyMessage> {
        override fun makeInitPacket(index: Int): ByteArray {
           return "Hi, I'm $index".toByteArray()
        }

        override fun parseInitPacket(bytes: ByteArray): Int {
            val str = kotlin.text.String(bytes)
            return parseInt(str.substring(8))!!
        }

        override fun decodePacket(index: Int, bytes: ByteArray): DummyMessage {
            if (bytes[0].toInt() != index) throw UserError("Invalid signature")
            return DummyMessage(index, String(bytes.sliceArray(1 until bytes.size)))
        }

        override fun encodePacket(packet: DummyMessage): ByteArray {
            return byteArrayOf(packet.index.toByte()) + packet.text.toByteArray()
        }
    }

    private fun makeCommManager(pc: PeerCommConfiguration): CommManager<DummyMessage> {
        val peerInfo = pc.peerInfo
        return CommManager<DummyMessage>(
                pc.myIndex,
                peerInfo,
                DummyPacketConverter(),
                { handleError(it)}
        )
    }

    private fun handleError(e: Exception) {
        logger.error("Shit pommes frites", e)
    }
}