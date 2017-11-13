// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseStorage
import net.postchain.core.EContext

private val HEX_CHARS = "0123456789abcdef"

fun String.hexStringToByteArray() : ByteArray {

    val result = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i])
        if (firstIndex == -1) {
            throw ArrayIndexOutOfBoundsException("Char ${this[i]} is not a hex digit")
        }
        val secondIndex = HEX_CHARS.indexOf(this[i + 1])
        if (secondIndex == -1) {
            throw ArrayIndexOutOfBoundsException("Char ${this[i]} is not a hex digit")
        }

        val octet = firstIndex.shl(4).or(secondIndex)
        result.set(i.shr(1), octet.toByte())
    }

    return result
}


private val HEX_CHARARRAY = HEX_CHARS.toCharArray()

fun ByteArray.toHex() : String{
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
