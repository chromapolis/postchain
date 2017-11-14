// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.test.ebft

import mu.KLogging
import net.postchain.base.PeerCommConfiguration
import net.postchain.test.IntegrationTest
import net.postchain.core.UserMistake
import net.postchain.ebft.CommManager
import net.postchain.ebft.PacketConverter
import net.postchain.ebft.PeerConnection
import net.postchain.parseInt
import org.junit.After
import org.junit.Assert.assertEquals
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

    protected fun setupCommManagers(count: Int = 2) {
        val peerCommConfigurations = arrayOfBasePeerCommConfigurations(count)
        commManagers = peerCommConfigurations.map { makeCommManager(it) }
    }

    private fun makeCommManager(pc: PeerCommConfiguration): CommManager<DummyMessage> {
        val peerInfo = pc.peerInfo
        return CommManager<DummyMessage>(
                pc.myIndex,
                peerInfo,
                DummyPacketConverter(pc.myIndex),
                { handleError(it) }
        )
    }

    private fun handleError(e: Exception) {
        logger.error("Shit pommes frites", e)
    }

    class DummyPacketConverter(val myIndex: Int) : PacketConverter<DummyMessage> {
        override fun makeInitPacket(index: Int): ByteArray {
           return "Hi, I'm ${myIndex}. I suppose you're $index".toByteArray()
        }

        override fun parseInitPacket(bytes: ByteArray): Int {
            val str = kotlin.text.String(bytes)
            assertEquals(myIndex, parseInt(str.substring(28, 29)))
            return parseInt(str.substring(8, 9))!!
        }

        override fun decodePacket(index: Int, bytes: ByteArray): DummyMessage {
            if (bytes[0].toInt() != index) throw UserMistake("Invalid signature")
            return DummyMessage(index, String(bytes.sliceArray(1 until bytes.size)))
        }

        override fun encodePacket(packet: DummyMessage): ByteArray {
            return byteArrayOf(packet.index.toByte()) + packet.text.toByteArray()
        }
    }

}