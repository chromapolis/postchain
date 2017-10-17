package com.chromaway.postchain.ebft.message

import com.chromaway.postchain.core.ProgrammerMistake
import com.chromaway.postchain.ebft.messages.BlockData
import com.chromaway.postchain.ebft.messages.BlockSignature
import com.chromaway.postchain.ebft.messages.CompleteBlock
import com.chromaway.postchain.ebft.messages.Message
import com.chromaway.postchain.ebft.messages.GetBlockAtHeight
import com.chromaway.postchain.ebft.messages.GetBlockSignature
import com.chromaway.postchain.ebft.messages.GetUnfinishedBlock
import com.chromaway.postchain.ebft.messages.Identification
import com.chromaway.postchain.ebft.messages.Signature
import com.chromaway.postchain.ebft.messages.SignedMessage
import com.chromaway.postchain.ebft.messages.Status
import java.io.ByteArrayOutputStream
import java.util.Vector


class SignedMessage(val message: ByteArray, val pubKey: ByteArray, val signature: ByteArray) {
    companion object {
        fun decode(bytes: ByteArray): com.chromaway.postchain.ebft.message.SignedMessage {
            val message = SignedMessage.der_decode(bytes.inputStream())
            return SignedMessage(message.message, message.pubkey, message.signature)
        }
    }

    fun encode(): ByteArray {
        val result = SignedMessage()
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
            val message = Message.der_decode(bytes.inputStream())
            return when (message.choiceID) {
                Message.getBlockAtHeightChosen -> GetBlockAtHeight(message.getBlockAtHeight.height)
                Message.identificationChosen -> Identification(message.identification.yourPubKey, message.identification.timestamp)
                Message.statusChosen -> {
                    val s = message.status
                    com.chromaway.postchain.ebft.message.Status(s.blockRID, s.height, s.revolting, s.round, s.serial, s.state.toInt())
                }
                Message.getUnfinishedBlockChosen -> com.chromaway.postchain.ebft.message.GetUnfinishedBlock(message.getUnfinishedBlock.blockRID)
                Message.blockDataChosen -> com.chromaway.postchain.ebft.message.UnfinishedBlock(message.blockData.header, message.blockData.transactions)
                Message.getBlockSignatureChosen -> com.chromaway.postchain.ebft.message.GetBlockSignature(message.getBlockSignature.blockRID)
                Message.blockSignatureChosen -> com.chromaway.postchain.ebft.message.BlockSignature(message.blockSignature.blockRID, com.chromaway.postchain.core.Signature(message.blockSignature.signature.subjectID, message.blockSignature.signature.data))
                Message.completeBlockChosen -> com.chromaway.postchain.ebft.message.CompleteBlock(message.completeBlock.blockData.header, message.completeBlock.blockData.transactions, message.completeBlock.height, message.completeBlock.witness)
                else -> throw ProgrammerMistake("Message type ${message.choiceID} is not handeled")
            }

        }
    }

    open fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        getBackingInstance().der_encode(out)
        return out.toByteArray()
    }

    abstract fun getBackingInstance(): Message

    override fun toString(): String {
        return this::class.simpleName!!
    }
}

class BlockSignature(val blockRID: ByteArray, val signature: com.chromaway.postchain.core.Signature): EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = BlockSignature()
        result.blockRID = blockRID
        val sig = Signature()
        sig.data = signature.data
        sig.subjectID = signature.subjectID
        result.signature = sig
        return Message.blockSignature(result)
    }
}

class CompleteBlock(val header: ByteArray, val transactions: List<ByteArray>, val height: Long, val witness: ByteArray): EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = CompleteBlock()
        result.blockData = BlockData()
        result.blockData.header = header
        result.blockData.transactions = Vector(transactions)
        result.height = height
        result.witness = witness
        return Message.completeBlock(result)
    }
}


class GetBlockAtHeight(val height: Long): EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = GetBlockAtHeight()
        result.height = height
        return Message.getBlockAtHeight(result)
    }
}

class GetBlockSignature(val blockRID: ByteArray) : EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = GetBlockSignature()
        result.blockRID = blockRID
        return Message.getBlockSignature(result)
    }
}

class GetUnfinishedBlock(val blockRID: ByteArray) : EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = GetUnfinishedBlock()
        result.blockRID = blockRID
        return Message.getUnfinishedBlock(result)
    }
}

class Identification(val yourPubKey: ByteArray, val timestamp: Long) : EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = Identification()
        result.yourPubKey = yourPubKey
        result.timestamp = timestamp
        return Message.identification(result)
    }
}

class Status(val blockRId: ByteArray?, val height: Long, val revolting: Boolean, val round: Long, val serial: Long, val state: Int) : EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = Status()
        result.blockRID = blockRId
        result.height = height
        result.revolting = revolting
        result.round = round
        result.serial = serial
        result.state = state.toLong()
        return Message.status(result)
    }

}

class UnfinishedBlock(val header: ByteArray, val transactions: List<ByteArray>) : EbftMessage() {
    override fun getBackingInstance(): Message {
        val result = com.chromaway.postchain.ebft.messages.BlockData()
        result.header = header
        result.transactions = Vector(transactions)
        return Message.blockData(result)
    }
}

