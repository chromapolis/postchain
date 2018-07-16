package net.postchain.gtx.gtxml

import net.postchain.base.gtxml.ArrayType
import net.postchain.base.gtxml.DictType
import net.postchain.base.gtxml.ParamType
import net.postchain.gtx.*
import net.postchain.gtx.GTXValueType.*
import java.io.StringReader
import java.math.BigInteger
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement

object GTXMLValueParser {

    private val jaxbContext = JAXBContext.newInstance("net.postchain.base.gtxml")

    /**
     * Parses XML represented as string into [GTXValue] and resolves params ('<param />') by [params] map
     */
    fun parseGTXMLValue(xml: String, params: Map<String, GTXValue> = mapOf()): GTXValue {
        val jaxbElement = parseJaxbElement(xml)
        val (qname, value) = jaxbElement

        return when {
            isScalar(qname) -> parseScalarGTXMLValue(jaxbElement, params)
            ARRAY == gtxValueTypeOf(qname) -> parseArrayGTXMLValue(value as ArrayType, params)
            DICT == gtxValueTypeOf(qname) -> parseDictGTXMLValue(value as DictType, params)
            else -> throw IllegalArgumentException("Unknown type of GTXMLValue")
        }
    }

    fun parseScalarGTXMLValue(jaxbElement: JAXBElement<*>, params: Map<String, GTXValue>): GTXValue {
        val (qname, value) = jaxbElement

        return if (isParam(qname)) {
            parseParam(value as ParamType, params)
        } else {
            when (gtxValueTypeOf(qname)) {
                NULL -> GTXNull
                STRING -> StringGTXValue(value as String)
                INTEGER -> IntegerGTXValue((value as BigInteger).longValueExact())
                BYTEARRAY -> ByteArrayGTXValue(value as ByteArray)
                else -> throw IllegalArgumentException("Unknown type of GTXMLValue")
            }
        }
    }

    private fun parseArrayGTXMLValue(array: ArrayType, params: Map<String, GTXValue>): ArrayGTXValue {
        val elements = array.elements.map { parseScalarGTXMLValue(it, params) }
        return ArrayGTXValue(elements.toTypedArray())
    }

    private fun parseDictGTXMLValue(dict: DictType, params: Map<String, GTXValue>): DictGTXValue {
        val parsedDict = dict.entry.map {
            it.key to parseScalarGTXMLValue(it.value, params)
        }.toMap()

        return DictGTXValue(parsedDict)
    }

    private fun parseParam(paramType: ParamType, params: Map<String, GTXValue>): GTXValue {
        // TODO: [et]: Resolve using of [paramType.type]
        return params[paramType.key]
                ?: throw IllegalArgumentException("Can't resolve param ${paramType.key}")
    }

    private fun parseJaxbElement(xml: String) =
            jaxbContext.createUnmarshaller().unmarshal(StringReader(xml)) as JAXBElement<*>
}