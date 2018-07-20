// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.base.Signer
import net.postchain.base.gtxml.OperationsType
import net.postchain.base.gtxml.ParamType
import net.postchain.base.gtxml.SignersType
import net.postchain.base.gtxml.TransactionType
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.ByteArrayKey
import net.postchain.core.byteArrayKeyOf
import net.postchain.gtx.GTXData
import net.postchain.gtx.GTXValue
import net.postchain.gtx.OpData
import java.io.StringReader
import javax.xml.bind.JAXB
import javax.xml.bind.JAXBElement

class TransactionContext(val blockchainRID: ByteArray?,
                         val params: Map<String, GTXValue> = mapOf(),
                         val autoSign: Boolean = false,
                         val signers: Map<ByteArrayKey, Signer> = mapOf()) {

    companion object {
        fun empty() = TransactionContext(null)
    }
}


object GTXMLTransactionParser {

    /**
     * Parses XML represented as string into [GTXData] within the [TransactionContext]
     */
    fun parseGTXMLTransaction(xml: String, context: TransactionContext): GTXData {
        val transaction = JAXB.unmarshal(StringReader(xml), TransactionType::class.java)

        // Asserting count(signers) == count(signatures)
        requireSignaturesCorrespondsSigners(transaction)

        val gtxData = GTXData(
                parseBlockchainRID(transaction.blockchainRID, context.blockchainRID),
                parseSigners(transaction.signers, context.params),
                parseSignatures(transaction, context.params),
                parseOperations(transaction.operations, context.params))

        if (context.autoSign) {
            signTransaction(gtxData, context.signers)
        }

        return gtxData
    }

    private fun requireSignaturesCorrespondsSigners(tx: TransactionType) {
        if (tx.signatures != null && tx.signers.signers.size != tx.signatures.signatures.size) {
            throw IllegalArgumentException("Number of signers (${tx.signers.signers.size}) is not equal to " +
                    "the number of signatures (${tx.signatures.signatures.size})\n")
        }
    }

    /**
     * Parses XML represented as string into [GTXData] within the jaxbContext of [params] ('<param />') and [signers]
     */
    fun parseGTXMLTransaction(xml: String,
                              params: Map<String, GTXValue> = mapOf(),
                              signers: Map<ByteArrayKey, Signer> = mapOf()): GTXData {

        return parseGTXMLTransaction(
                xml,
                TransactionContext(null, params, true, signers))
    }

    private fun parseBlockchainRID(blockchainRID: String?, contextBlockchainRID: ByteArray?): ByteArray {
        return if (blockchainRID.isNullOrEmpty()) {
            contextBlockchainRID ?: ByteArray(0)
        } else {
            blockchainRID!!.hexStringToByteArray()
                    .takeIf { contextBlockchainRID == null || it.contentEquals(contextBlockchainRID) }
                    ?: throw IllegalArgumentException(
                            "BlockchainRID = '$blockchainRID' of parsed xml transaction is not equal to " +
                                    "TransactionContext.blockchainRID = '${contextBlockchainRID!!.toHex()}'"
                    )
        }
    }

    private fun parseSigners(signers: SignersType, params: Map<String, GTXValue>): Array<ByteArray> {
        return signers.signers
                .map { parseJAXBElementToByteArrayOrParam(it, params) }
                .toTypedArray()
    }

    private fun parseSignatures(transaction: TransactionType, params: Map<String, GTXValue>): Array<ByteArray> {
        return if (transaction.signatures != null) {
            transaction.signatures.signatures
                    .map { parseJAXBElementToByteArrayOrParam(it, params) }
                    .toTypedArray()
        } else {
            Array(transaction.signers.signers.size) { byteArrayOf() }
        }
    }

    private fun parseJAXBElementToByteArrayOrParam(jaxbElement: JAXBElement<*>, params: Map<String, GTXValue>): ByteArray {
        // TODO: [et]: Add better error handling
        return if (jaxbElement.value is ParamType) {
            params[(jaxbElement.value as ParamType).key]
                    ?.asByteArray()
                    ?: throw IllegalArgumentException("Unknown type of GTXMLValue")
        } else {
            jaxbElement.value as ByteArray
        }
    }

    private fun parseOperations(operations: OperationsType, params: Map<String, GTXValue>): Array<OpData> {
        return operations.operation.map {
            OpData(
                    it.name,
                    it.parameters.map {
                        GTXMLValueParser.parseJAXBElementToGTXMLValue(it, params)
                    }.toTypedArray())
        }.toTypedArray()
    }

    private fun signTransaction(gtxData: GTXData, signers: Map<ByteArrayKey, Signer>) {
        for (i in 0 until gtxData.signers.size) {
            if (gtxData.signatures[i].isEmpty()) {
                val signer = signers[gtxData.signers[i]?.byteArrayKeyOf()]
                        ?: throw IllegalArgumentException("Signer ${gtxData.signers[i]?.byteArrayKeyOf()} is absent")
                gtxData.signatures[i] = signer(gtxData.serializeWithoutSignatures()).data
            }
        }
    }
}