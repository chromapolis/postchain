// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.base.Signer
import net.postchain.base.gtxml.*
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.ByteArrayKey
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

        return GTXData(
                parseBlockchainRID(transaction.blockchainRID, context.blockchainRID),
                parseSigners(transaction.signers, context.params),
                parseSignatures(transaction.signatures, context.params),
                parseOperations(transaction.operations, context.params))
    }

    /**
     * Parses XML represented as string into [GTXData] within the jaxbContext of [params] ('<param />') and [signers]
     */
    fun parseGTXMLTransaction(xml: String,
                              params: Map<String, GTXValue> = mapOf(),
                              signers: Map<ByteArrayKey, Signer> = mapOf()): GTXData {

        return parseGTXMLTransaction(
                xml,
                TransactionContext(null, params = params, signers = signers))
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

    private fun parseSignatures(signatures: SignaturesType, params: Map<String, GTXValue>): Array<ByteArray> {
        return signatures.signatures
                .map { parseJAXBElementToByteArrayOrParam(it, params) }
                .toTypedArray()
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
}