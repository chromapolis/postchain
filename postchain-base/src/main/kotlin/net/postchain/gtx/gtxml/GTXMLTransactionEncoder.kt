package net.postchain.gtx.gtxml;

import net.postchain.base.gtxml.ObjectFactory
import net.postchain.base.gtxml.OperationsType
import net.postchain.base.gtxml.SignaturesType
import net.postchain.base.gtxml.SignersType
import net.postchain.common.toHex
import net.postchain.gtx.GTXData
import net.postchain.gtx.OpData
import java.io.StringWriter
import javax.xml.bind.JAXB


object GTXMLTransactionEncoder {

    private val objectFactory = ObjectFactory()

    /**
     * Encodes [GTXData] into XML format
     */
    fun encodeXMLGTXTransaction(gtxData: GTXData): String {
        val transactionType = objectFactory.createTransactionType()
        transactionType.blockchainRID = gtxData.blockchainRID.toHex()
        transactionType.signers = encodeSigners(gtxData.signers)
        transactionType.operations = encodeOperations(gtxData.operations)
        transactionType.signatures = encodeSignature(gtxData.signatures)

        val jaxbElement = objectFactory.createTransaction(transactionType)

        val xmlWriter = StringWriter()
        JAXB.marshal(jaxbElement, xmlWriter)

        return xmlWriter.toString()
    }

    private fun encodeSigners(signers: Array<ByteArray>): SignersType {
        return with(objectFactory.createSignersType()) {
            signers.map(objectFactory::createBytea) // See [ObjectFactory.createBytearrayElement]
                    .toCollection(this.signers)
            return this
        }
    }

    private fun encodeOperations(operations: Array<OpData>): OperationsType {
        return with(objectFactory.createOperationsType()) {
            operations.forEach {
                val operationType = objectFactory.createOperationType()
                operationType.name = it.opName
                it.args.map(GTXMLValueEncoder::createScalarElement)
                        .toCollection(operationType.parameters)
                this.operation.add(operationType)
            }
            return this
        }
    }

    private fun encodeSignature(signatures: Array<ByteArray>): SignaturesType {
        return with (objectFactory.createSignaturesType()) {
            signatures.toCollection(this.bytea)
            return this
        }
    }
}
