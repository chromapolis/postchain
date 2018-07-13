package net.postchain.gtx.gtxml

import net.postchain.gtx.*
import net.postchain.gtx.GTXValueType.*
import javax.xml.namespace.QName

/**
 * Returns `true` for [NULL], [STRING], [INTEGER], [BYTEARRAY] types and `false` otherwise
 */
fun isScalar(type: GTXValueType): Boolean =
        type in arrayOf(NULL, STRING, INTEGER, BYTEARRAY)

/**
 * Returns `true` for [NULL], [STRING], [INTEGER], [BYTEARRAY] types and `false` otherwise
 */
fun isScalar(gtxValue: GTXValue): Boolean {
    return when (gtxValue) {
        is GTXNull, is StringGTXValue, is IntegerGTXValue, is ByteArrayGTXValue -> true
        else -> false
    }
}

/**
 * Returns [GTXValueType] object correspondent to [QName]
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