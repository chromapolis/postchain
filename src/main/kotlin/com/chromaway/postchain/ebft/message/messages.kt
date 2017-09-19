package com.chromaway.postchain.ebft.message

import com.chromaway.postchain.core.ProgrammerError
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
import com.chromaway.postchain.ebft.messages.UnfinishedBlock
import java.io.ByteArrayOutputStream
import java.util.Vector

sealed class Messaged {
    companion object {
        fun decode(bytes: ByteArray): Messaged {
            val message = Message.der_decode(bytes.inputStream())
            return when (message.choiceID) {
                Message.blockDataChosen -> BlockData(message.blockData.header, message.blockData.transactions)
                Message.getBlockAtHeightChosen -> GetBlockAtHeight(message.getBlockAtHeight.height)
                Message.identificationChosen -> Identification(message.identification.yourPubKey, message.identification.timestamp)
                else -> throw ProgrammerError("Message type ${message::class} is not handeled")
            }

        }
    }

    open fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        getBackingInstance().der_encode(out)
        return out.toByteArray()
    }

    abstract fun getBackingInstance(): Message

}

class BlockData(val header: ByteArray, val transactions: List<ByteArray>): Messaged() {
    override fun getBackingInstance(): Message {
        val result = BlockData()
        result.header = header
        result.transactions = Vector(transactions)
        return Message.blockData(result)
    }
}

class BlockSignature(val blockRID: ByteArray, val signature: com.chromaway.postchain.ebft.message.Signature): Messaged() {
    override fun getBackingInstance(): Message {
        val result = BlockSignature()
        result.blockRID = blockRID
        result.signature = signature.createBackingImpl()
        return Message.blockSignature(result)
    }

}

class CompleteBlock(val blockData: BlockData, val height: Long, val witness: ByteArray): Messaged() {
    override fun getBackingInstance(): Message {
        val result = CompleteBlock()
        result.blockData = blockData
        result.height = height
        result.witness = witness
        return Message.completeBlock(result)
    }
}


class GetBlockAtHeight(val height: Long): Messaged() {
    override fun getBackingInstance(): Message {
        val result = GetBlockAtHeight()
        result.height = height
        return Message.getBlockAtHeight(result)
    }
}

class GetBlockSignature(val blockRID: ByteArray) : Messaged() {
    override fun getBackingInstance(): Message {
        val result = GetBlockSignature()
        result.blockRID = blockRID
        return Message.getBlockSignature(result)
    }
}

class GetUnfinishedBlock(val blockRID: ByteArray) : Messaged() {
    override fun getBackingInstance(): Message {
        val result = GetUnfinishedBlock()
        result.blockRID = blockRID
        return Message.getUnfinishedBlock(result)
    }
}

class Identification(val yourPubKey: ByteArray, val timestamp: Long) : Messaged() {
    override fun getBackingInstance(): Message {
        val result = Identification()
        result.yourPubKey = yourPubKey
        result.timestamp = timestamp
        return Message.identification(result)
    }
}

class Signature(val data: ByteArray, val subjectID: ByteArray) {
    fun encode(): ByteArray {
        val result = createBackingImpl()
        val out = ByteArrayOutputStream()
        result.der_encode(out)
        return out.toByteArray()
    }

    fun createBackingImpl(): Signature {
        val result = Signature()
        result.data = data
        result.subjectID = subjectID
        return result
    }
}

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

class Status(val blockRId: ByteArray, val height: Long, val revolting: Boolean, val round: Long, val serial: Long, val state: Long) : Messaged() {
    override fun getBackingInstance(): Message {
        val result = Status()
        result.blockRID = blockRId
        result.height = height
        result.revolting = revolting
        result.round = round
        result.serial = serial
        result.state = state
        return Message.status(result)
    }

}

class UnfinishedBlock(val header: ByteArray, val transactions: List<ByteArray>) : Messaged() {

    override fun encode(): ByteArray {
        val result = UnfinishedBlock()
        result.header = header
        result.transactions = Vector(transactions)
        val out = ByteArrayOutputStream()
        result.der_encode(out)
        return out.toByteArray()
    }


    override fun getBackingInstance(): Message {
        throw ProgrammerError("Not supported")
    }

}

