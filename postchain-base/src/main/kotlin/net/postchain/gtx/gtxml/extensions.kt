package net.postchain.gtx.gtxml

import net.postchain.gtx.GTXValueType
import net.postchain.gtx.GTXValueType.*
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName

/**
 * Returns `true` for [NULL], [STRING], [INTEGER], [BYTEARRAY] types and `false` otherwise
 */
fun isScalar(type: GTXValueType): Boolean =
        type in arrayOf(NULL, STRING, INTEGER, BYTEARRAY)

/**
 * Returns [Type] object correspondent to [QName]
 */
fun gtxValueTypeOf(qname: QName): GTXValueType {
    return when (qname.localPart) {
        "null" -> NULL
        "string" -> STRING
        "int" -> INTEGER
        "bytea" -> BYTEARRAY
        "array" -> ARRAY
        "dict" -> DICT
        else -> throw IllegalArgumentException("Can't parse GTXValueType")
    }
}

/**
 * [component1] function for [JAXBElement] class
 */
operator fun <T> JAXBElement<T>.component1(): QName = name

/**
 * [component2] function for [JAXBElement] class
 */
operator fun <T> JAXBElement<T>.component2(): T = value