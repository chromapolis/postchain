package net.postchain.gtx.gtxml

import net.postchain.gtx.GTXValueType
import net.postchain.gtx.GTXValueType.*
import javax.xml.namespace.QName


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

/**
 * Returns `true` if [QName] corresponds `<param />` tag and `false` otherwise
 */
fun isParam(qname: QName): Boolean =
        "param".equals(qname.localPart, true)