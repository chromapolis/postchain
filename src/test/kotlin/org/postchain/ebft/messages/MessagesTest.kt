// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.ebft.messages

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class MessagesTest {
    @Test
    fun testGetBlockAtHeight() {
        val mess = org.postchain.ebft.messages.GetBlockAtHeight()
        mess.height =29
        val out = ByteArrayOutputStream()
        mess.der_encode(out)

        val result = org.postchain.ebft.messages.GetBlockAtHeight.der_decode(out.toByteArray().inputStream())
        assertEquals(29, result.height)
    }

    @Test
    fun testBlockSignature() {
        val mess = org.postchain.ebft.messages.BlockSignature()
        mess.blockRID = ByteArray(32, {it.toByte()})
        val sig = org.postchain.ebft.messages.Signature()
        sig.data = ByteArray(40, {(it+1).toByte()})
        sig.subjectID = ByteArray(33, {it.toByte()})
        mess.signature = sig
        val out = ByteArrayOutputStream()
        mess.der_encode(out)

        val result = org.postchain.ebft.messages.BlockSignature.der_decode(out.toByteArray().inputStream())
        assertArrayEquals(mess.blockRID, result.blockRID)
        assertArrayEquals(mess.signature.subjectID, result.signature.subjectID)
        assertArrayEquals(mess.signature.data, result.signature.data)
    }

    @Test
    fun testMessage() {
        val gbah = org.postchain.ebft.messages.GetBlockAtHeight()
        gbah.height = 19
        val mess = org.postchain.ebft.messages.Message.getBlockAtHeight(gbah)
        val out = ByteArrayOutputStream()
        mess.der_encode(out)

        val result = org.postchain.ebft.messages.Message.der_decode(out.toByteArray().inputStream())
        assertEquals(mess.getBlockAtHeight.height, result.getBlockAtHeight.height)
    }
}