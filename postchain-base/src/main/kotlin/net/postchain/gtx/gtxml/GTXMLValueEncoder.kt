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
        return with(StringWriter()) {
            JAXB.marshal(encodeGTXMLValueToJAXBElement(gtxValue), this)
            toString()
        }
    }

    fun encodeGTXMLValueToJAXBElement(gtxValue: GTXValue): JAXBElement<*> {
        return when (gtxValue) {
        /**
         * Note: null element will be equal to:
         *      `<null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>`
         */
            is GTXNull -> objectFactory.createNull(null)
            is StringGTXValue -> objectFactory.createString(gtxValue.string)
            is IntegerGTXValue -> objectFactory.createInt(BigInteger.valueOf(gtxValue.integer))
        /**
         * Can't use [ObjectFactory.createBytea] because [HexBinaryAdapter] are not called in this case.
         * Therefore we call it manually.
         */
            is ByteArrayGTXValue -> objectFactory.createBytearrayElement(gtxValue.bytearray)
            is ArrayGTXValue -> createArrayElement(gtxValue)
            is DictGTXValue -> createDictElement(gtxValue)
            else -> throw IllegalArgumentException("Unknown type of gtxValue")
        }
    }

    private fun createArrayElement(gtxValue: ArrayGTXValue): JAXBElement<ArrayType> {
        return with(objectFactory.createArrayType()) {
            gtxValue.array
                    .map(::encodeGTXMLValueToJAXBElement)
                    .toCollection(this.elements)

            objectFactory.createArray(this)
        }
    }

    private fun createDictElement(gtxValue: DictGTXValue): JAXBElement<DictType> {
        return with(objectFactory.createDictType()) {
            gtxValue.dict.map { entry ->
                val entryType = objectFactory.createEntryType()
                entryType.key = entry.key
                entryType.value = encodeGTXMLValueToJAXBElement(entry.value)
                entryType
            }.toCollection(this.entry)

            objectFactory.createDict(this)
        }
    }
}
