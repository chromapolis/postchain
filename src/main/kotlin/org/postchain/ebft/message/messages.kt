// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.ebft.message

import org.postchain.core.ProgrammerMistake
import org.postchain.ebft.messages.BlockData
import org.postchain.ebft.messages.BlockSignature
import org.postchain.ebft.messages.CompleteBlock
import org.postchain.ebft.messages.Message
import org.postchain.ebft.messages.GetBlockAtHeight
import org.postchain.ebft.messages.GetBlockSignature
import org.postchain.ebft.messages.GetUnfinishedBlock
import org.postchain.ebft.messages.Identification
import org.postchain.ebft.messages.Signature
import org.postchain.ebft.messages.SignedMessage
import org.postchain.ebft.messages.Status
import org.postchain.ebft.messages.Transaction
import java.io.ByteArrayOutputStream
import java.util.Vector


class SignedMessage(val message: ByteArray, val pubKey: ByteArray, val signature: ByteArray) {
    companion object {
        fun decode(bytes: ByteArray): org.postchain.ebft.message.SignedMessage {
            val message = org.postchain.ebft.messages.SignedMessage.der_decode(bytes.inputStream())
            return SignedMessage(message.message, message.pubkey, message.signature)
        }
    }

    fun encode(): ByteArray {
        val result = org.postchain.ebft.messages.SignedMessage()
        result.message = message
        result.pubkey = pubKey
        result.signature = signature
        val out = ByteArrayOutputStream()
        result.der_encode(out)
        return out.toByteArray()
    }
}

sealed class EbftMessage {
    companion object {
        fun decode(bytes: ByteArray): EbftMessage {
            val message = org.postchain.ebft.messages.Message.der_decode(bytes.inputStream())
            return when (message.choiceID) {
                org.postchain.ebft.messages.Message.getBlockAtHeightChosen -> GetBlockAtHeight(message.getBlockAtHeight.height)
                org.postchain.ebft.messages.Message.identificationChosen -> Identification(message.identification.yourPubKey, message.identification.timestamp)
                org.postchain.ebft.messages.Message.statusChosen -> {
                    val s = message.status
                    org.postchain.ebft.message.Status(s.blockRID, s.height, s.revolting, s.round, s.serial, s.state.toInt())
                }
                org.postchain.ebft.messages.Message.getUnfinishedBlockChosen -> org.postchain.ebft.message.GetUnfinishedBlock(message.getUnfinishedBlock.blockRID)
                org.postchain.ebft.messages.Message.unfinishedBlockChosen -> org.postchain.ebft.message.UnfinishedBlock(message.unfinishedBlock.header, message.unfinishedBlock.transactions)
                org.postchain.ebft.messages.Message.getBlockSignatureChosen -> org.postchain.ebft.message.GetBlockSignature(message.getBlockSignature.blockRID)
                org.postchain.ebft.messages.Message.blockSignatureChosen -> org.postchain.ebft.message.BlockSignature(message.blockSignature.blockRID, org.postchain.core.Signature(message.blockSignature.signature.subjectID, message.blockSignature.signature.data))
                org.postchain.ebft.messages.Message.completeBlockChosen -> org.postchain.ebft.message.CompleteBlock(message.completeBlock.blockData.header, message.completeBlock.blockData.transactions, message.completeBlock.height, message.completeBlock.witness)
                org.postchain.ebft.messages.Message.transactionChosen -> org.postchain.ebft.message.Transaction(message.transaction.data)
                else -> throw ProgrammerMistake("Message type ${message.choiceID} is not handeled")
            }

        }
    }

    open fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        getBackingInstance().der_encode(out)
        return out.toByteArray()
    }

    abstract fun getBackingInstance(): org.postchain.ebft.messages.Message

    override fun toString(): String {
        return this::class.simpleName!!
    }
}

class BlockSignature(val blockRID: ByteArray, val signature: org.postchain.core.Signature): EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.BlockSignature()
        result.blockRID = blockRID
        val sig = org.postchain.ebft.messages.Signature()
        sig.data = signature.data
        sig.subjectID = signature.subjectID
        result.signature = sig
        return org.postchain.ebft.messages.Message.blockSignature(result)
    }
}

class CompleteBlock(val header: ByteArray, val transactions: List<ByteArray>, val height: Long, val witness: ByteArray): EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.CompleteBlock()
        result.blockData = org.postchain.ebft.messages.BlockData()
        result.blockData.header = header
        result.blockData.transactions = Vector(transactions)
        result.height = height
        result.witness = witness
        return org.postchain.ebft.messages.Message.completeBlock(result)
    }
}


class GetBlockAtHeight(val height: Long): EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.GetBlockAtHeight()
        result.height = height
        return org.postchain.ebft.messages.Message.getBlockAtHeight(result)
    }
}

class GetBlockSignature(val blockRID: ByteArray) : EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.GetBlockSignature()
        result.blockRID = blockRID
        return org.postchain.ebft.messages.Message.getBlockSignature(result)
    }
}

class GetUnfinishedBlock(val blockRID: ByteArray) : EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.GetUnfinishedBlock()
        result.blockRID = blockRID
        return org.postchain.ebft.messages.Message.getUnfinishedBlock(result)
    }
}

class Identification(val yourPubKey: ByteArray, val timestamp: Long) : EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.Identification()
        result.yourPubKey = yourPubKey
        result.timestamp = timestamp
        return org.postchain.ebft.messages.Message.identification(result)
    }
}

class Status(val blockRId: ByteArray?, val height: Long, val revolting: Boolean, val round: Long, val serial: Long, val state: Int) : EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.Status()
        result.blockRID = blockRId
        result.height = height
        result.revolting = revolting
        result.round = round
        result.serial = serial
        result.state = state.toLong()
        return org.postchain.ebft.messages.Message.status(result)
    }

}

class UnfinishedBlock(val header: ByteArray, val transactions: List<ByteArray>) : EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.UnfinishedBlock()
        result.header = header
        result.transactions = Vector(transactions)
        return org.postchain.ebft.messages.Message.unfinishedBlock(result)
    }
}

class Transaction(val data: ByteArray): EbftMessage() {
    override fun getBackingInstance(): org.postchain.ebft.messages.Message {
        val result = org.postchain.ebft.messages.Transaction()
        result.data = data;
        return org.postchain.ebft.messages.Message.transaction(result)
    }
}

