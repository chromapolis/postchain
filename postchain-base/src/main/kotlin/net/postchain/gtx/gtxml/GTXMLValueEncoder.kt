package net.postchain.gtx.gtxml;

import net.postchain.base.gtxml.ArrayType
import net.postchain.base.gtxml.DictType
import net.postchain.base.gtxml.ObjectFactory
import net.postchain.gtx.*
import java.io.StringWriter
import java.math.BigInteger
import javax.xml.bind.JAXB
import javax.xml.bind.JAXBElement


object GTXMLValueEncoder {

    private val objectFactory = ObjectFactory()

    /**
     * Encodes [GTXValue] into XML format
     */
    fun encodeXMLGTXValue(gtxValue: GTXValue): String {
        /**
         * Special case to generate xml element w/o 'xsi:nil="true"' 'xmlns:xsi="..."' attributes.
         */
        if (gtxValue === GTXNull) {
            return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <null/>

            """.trimIndent()
        }

        val jaxbElement = when {
            isScalar(gtxValue) -> createScalarElement(gtxValue)
            gtxValue is ArrayGTXValue -> createArrayElement(gtxValue)
            gtxValue is DictGTXValue -> createDictElement(gtxValue)
            else -> throw IllegalArgumentException("Unknown type of gtxValue")
        }

        val xmlWriter = StringWriter()
        JAXB.marshal(jaxbElement, xmlWriter)

        return xmlWriter.toString()
    }

    fun createScalarElement(gtxValue: GTXValue): JAXBElement<*> {
        return when (gtxValue) {
            is GTXNull -> objectFactory.createNull(null)
            is StringGTXValue -> objectFactory.createString(gtxValue.string)
            is IntegerGTXValue -> objectFactory.createInt(BigInteger.valueOf(gtxValue.integer))
        /**
         * Can't use [ObjectFactory.createBytea] because [HexBinaryAdapter] are not called in this case.
         * Therefore we call it manually.
         */
            is ByteArrayGTXValue -> objectFactory.createBytearrayElement(gtxValue.bytearray)
            else -> throw IllegalArgumentException("Unknown type of gtxValue")
        }
    }

    private fun createArrayElement(gtxValue: ArrayGTXValue): JAXBElement<ArrayType> {
        return with(objectFactory.createArrayType()) {
            gtxValue.array
                    .filter(::isScalar) // Note: Array of arrays and array of dicts are not supported yet
                    .map(::createScalarElement)
                    .toCollection(this.elements)

            objectFactory.createArray(this)
        }
    }

    private fun createDictElement(gtxValue: DictGTXValue): JAXBElement<DictType> {
        return with(objectFactory.createDictType()) {
            gtxValue.dict.map { entry ->
                val entryType = objectFactory.createEntryType()
                entryType.key = entry.key
                entryType.value = createScalarElement(entry.value)
                entryType
            }.toCollection(this.entry)

            objectFactory.createDict(this)
        }
    }
}
