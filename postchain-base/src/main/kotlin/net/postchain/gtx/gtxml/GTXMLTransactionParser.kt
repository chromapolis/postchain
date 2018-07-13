// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.base.Signer
import net.postchain.base.gtxml.OperationsType
import net.postchain.base.gtxml.SignaturesType
import net.postchain.base.gtxml.SignersType
import net.postchain.base.gtxml.TransactionType
import net.postchain.common.hexStringToByteArray
import net.postchain.core.ByteArrayKey
import net.postchain.gtx.GTXData
import net.postchain.gtx.GTXValue
import net.postchain.gtx.OpData
import java.io.StringReader
import javax.xml.bind.JAXB

class TransactionContext(val blockchainRID: ByteArray,
                         val params: Map<String, GTXValue> = mapOf(),
                         val autoSign: Boolean = false,
                         val signers: Map<ByteArray, Signer> = mapOf())


object GTXMLTransactionParser {

    /**
     * Parses XML represented as string into [GTXData] within the [transactionContext]
     */
    fun parseGTXMLTransaction(xml: String, transactionContext: TransactionContext?): GTXData {
        val gtxData = doParseGTXMLTransaction(xml)
        // TODO: Use transactionContext here
        return gtxData
    }

    /**
     * Parses XML represented as string into [GTXData] within the jaxbContext of [params] ('<param />') and [signers]
     */
    fun parseGTXMLTransaction(xml: String,
                              params: Map<String, GTXValue> = mapOf(),
                              signers: Map<ByteArrayKey, ByteArray> = mapOf()): GTXData {

        val gtxData = doParseGTXMLTransaction(xml)
        // TODO: Use 'params' and 'signers' here
        return gtxData
    }

    private fun doParseGTXMLTransaction(xml: String): GTXData {
        val transaction = JAXB.unmarshal(StringReader(xml), TransactionType::class.java)
        return GTXData(
                parseBlockchainRID(transaction),
                parseSigners(transaction.signers),
                parseSignatures(transaction.signatures),
                parseOperations(transaction.operations))
    }

    private fun parseBlockchainRID(transaction: TransactionType) =
            transaction.blockchainRID.hexStringToByteArray()

    private fun parseSigners(signers: SignersType): Array<ByteArray> {
        return signers.signers
                .filter { it.name.localPart == "bytea" } // TODO: Redesign, parse <param /> too
                .map { it.value as ByteArray }
                .toTypedArray()
    }

    private fun parseSignatures(signatures: SignaturesType): Array<ByteArray> =
            signatures.bytea.toTypedArray()

    private fun parseOperations(operations: OperationsType): Array<OpData> {
        return operations.operation.map {
            var parameters = it.parameters.map(GTXMLValueParser::parseScalarGTXMLValue)
            OpData(it.name, parameters.toTypedArray())
        }.toTypedArray()
    }
}