// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.ebft.message

import net.postchain.core.ProgrammerMistake
import net.postchain.ebft.messages.BlockData
import net.postchain.ebft.messages.BlockSignature
import net.postchain.ebft.messages.CompleteBlock
import net.postchain.ebft.messages.Message
import net.postchain.ebft.messages.GetBlockAtHeight
import net.postchain.ebft.messages.GetBlockSignature
import net.postchain.ebft.messages.GetUnfinishedBlock
import net.postchain.ebft.messages.Identification
import net.postchain.ebft.messages.Signature
import net.postchain.ebft.messages.SignedMessage
import net.postchain.ebft.messages.Status
import net.postchain.ebft.messages.Transaction
import java.io.ByteArrayOutputStream
import java.util.Vector


class SignedMessage(val message: ByteArray, val pubKey: ByteArray, val signature: ByteArray) {
    companion object {
        fun decode(bytes: ByteArray): net.postchain.ebft.message.SignedMessage {
            val message = net.postchain.ebft.messages.SignedMessage.der_decode(bytes.inputStream())
            return SignedMessage(message.message, message.pubkey, message.signature)
        }
    }

    fun encode(): ByteArray {
        val result = net.postchain.ebft.messages.SignedMessage()
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
            val message = net.postchain.ebft.messages.Message.der_decode(bytes.inputStream())
            return when (message.choiceID) {
                net.postchain.ebft.messages.Message.getBlockAtHeightChosen -> GetBlockAtHeight(message.getBlockAtHeight.height)
                net.postchain.ebft.messages.Message.identificationChosen -> Identification(message.identification.yourPubKey, message.identification.timestamp)
                net.postchain.ebft.messages.Message.statusChosen -> {
                    val s = message.status
                    net.postchain.ebft.message.Status(s.blockRID, s.height, s.revolting, s.round, s.serial, s.state.toInt())
                }
                net.postchain.ebft.messages.Message.getUnfinishedBlockChosen -> net.postchain.ebft.message.GetUnfinishedBlock(message.getUnfinishedBlock.blockRID)
                net.postchain.ebft.messages.Message.unfinishedBlockChosen -> net.postchain.ebft.message.UnfinishedBlock(message.unfinishedBlock.header, message.unfinishedBlock.transactions)
                net.postchain.ebft.messages.Message.getBlockSignatureChosen -> net.postchain.ebft.message.GetBlockSignature(message.getBlockSignature.blockRID)
                net.postchain.ebft.messages.Message.blockSignatureChosen -> net.postchain.ebft.message.BlockSignature(message.blockSignature.blockRID, net.postchain.core.Signature(message.blockSignature.signature.subjectID, message.blockSignature.signature.data))
                net.postchain.ebft.messages.Message.completeBlockChosen -> net.postchain.ebft.message.CompleteBlock(message.completeBlock.blockData.header, message.completeBlock.blockData.transactions, message.completeBlock.height, message.completeBlock.witness)
                net.postchain.ebft.messages.Message.transactionChosen -> net.postchain.ebft.message.Transaction(message.transaction.data)
                else -> throw ProgrammerMistake("Message type ${message.choiceID} is not handeled")
            }

        }
    }

    open fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        getBackingInstance().der_encode(out)
        return out.toByteArray()
    }

    abstract fun getBackingInstance(): net.postchain.ebft.messages.Message

    override fun toString(): String {
        return this::class.simpleName!!
    }
}

class BlockSignature(val blockRID: ByteArray, val signature: net.postchain.core.Signature): EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.BlockSignature()
        result.blockRID = blockRID
        val sig = net.postchain.ebft.messages.Signature()
        sig.data = signature.data
        sig.subjectID = signature.subjectID
        result.signature = sig
        return net.postchain.ebft.messages.Message.blockSignature(result)
    }
}

class CompleteBlock(val header: ByteArray, val transactions: List<ByteArray>, val height: Long, val witness: ByteArray): EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.CompleteBlock()
        result.blockData = net.postchain.ebft.messages.BlockData()
        result.blockData.header = header
        result.blockData.transactions = Vector(transactions)
        result.height = height
        result.witness = witness
        return net.postchain.ebft.messages.Message.completeBlock(result)
    }
}


class GetBlockAtHeight(val height: Long): EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.GetBlockAtHeight()
        result.height = height
        return net.postchain.ebft.messages.Message.getBlockAtHeight(result)
    }
}

class GetBlockSignature(val blockRID: ByteArray) : EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.GetBlockSignature()
        result.blockRID = blockRID
        return net.postchain.ebft.messages.Message.getBlockSignature(result)
    }
}

class GetUnfinishedBlock(val blockRID: ByteArray) : EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.GetUnfinishedBlock()
        result.blockRID = blockRID
        return net.postchain.ebft.messages.Message.getUnfinishedBlock(result)
    }
}

class Identification(val yourPubKey: ByteArray, val timestamp: Long) : EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.Identification()
        result.yourPubKey = yourPubKey
        result.timestamp = timestamp
        return net.postchain.ebft.messages.Message.identification(result)
    }
}

class Status(val blockRId: ByteArray?, val height: Long, val revolting: Boolean, val round: Long, val serial: Long, val state: Int) : EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.Status()
        result.blockRID = blockRId
        result.height = height
        result.revolting = revolting
        result.round = round
        result.serial = serial
        result.state = state.toLong()
        return net.postchain.ebft.messages.Message.status(result)
    }

}

class UnfinishedBlock(val header: ByteArray, val transactions: List<ByteArray>) : EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.UnfinishedBlock()
        result.header = header
        result.transactions = Vector(transactions)
        return net.postchain.ebft.messages.Message.unfinishedBlock(result)
    }
}

class Transaction(val data: ByteArray): EbftMessage() {
    override fun getBackingInstance(): net.postchain.ebft.messages.Message {
        val result = net.postchain.ebft.messages.Transaction()
        result.data = data;
        return net.postchain.ebft.messages.Message.transaction(result)
    }
}

