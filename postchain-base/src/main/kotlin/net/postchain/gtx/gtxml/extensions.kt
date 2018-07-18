package net.postchain.gtx.gtxml

import net.postchain.gtx.GTXValueType
import net.postchain.gtx.GTXValueType.*
import javax.xml.namespace.QName

/**
 * Returns [GTXValueType] object correspondent to [QName]
 */
fun gtxValueTypeOf(qname: QName): GTXValueType =
        gtxValueTypeOf(qname.localPart)

/**
 * Returns [GTXValueType] object correspondent to [String]
 */
fun gtxValueTypeOf(type: String): GTXValueType {
    return when (type) {
        "null" -> NULL
        "string" -> STRING
        "int" -> INTEGER
        "bytea" -> BYTEARRAY
        "array" -> ARRAY
        "dict" -> DICT
        else -> throw IllegalArgumentException("Unknown type of GTXValueType: $type")
    }
}

/**
 * Returns `true` if [QName] corresponds `<param />` tag and `false` otherwise
 */
fun isParam(qname: QName): Boolean =
        "param".equals(qname.localPart, true)