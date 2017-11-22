// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.core

import net.postchain.base.toHex

class ByteArrayKey(val byteArray: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (super.equals(other)) return true
        if (other is ByteArrayKey) {
            return other.byteArray.contentEquals(byteArray)
        } else return false
    }

    override fun hashCode(): Int {
        return byteArray.contentHashCode()
    }

    override fun toString(): String {
        return byteArray.toHex()
    }
}