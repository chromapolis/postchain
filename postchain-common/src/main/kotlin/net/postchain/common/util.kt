// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.common

private val HEX_CHARS = "0123456789ABCDEF"

fun String.hexStringToByteArray(): ByteArray {
    if (length % 2 != 0) {
        throw IllegalArgumentException("Invalid hex string: length is not an even number")
    }

    val result = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i], ignoreCase = true)
        if (firstIndex == -1) {
            throw IllegalArgumentException("Char ${this[i]} is not a hex digit")
        }

        val secondIndex = HEX_CHARS.indexOf(this[i + 1], ignoreCase = true)
        if (secondIndex == -1) {
            throw IllegalArgumentException("Char ${this[i]} is not a hex digit")
        }

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }

    return result
}


private val HEX_CHARARRAY = HEX_CHARS.toCharArray()

fun ByteArray.toHex(): String {
    val result = StringBuffer()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARARRAY[firstIndex])
        result.append(HEX_CHARARRAY[secondIndex])
    }

    return result.toString()
}
