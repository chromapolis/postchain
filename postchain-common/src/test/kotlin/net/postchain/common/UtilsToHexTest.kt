package net.postchain.common

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.Test

class UtilsToHexTest {

    @Test
    fun toHex_empty_successfully() {
        val actual = byteArrayOf().toHex()
        val expected = ""

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun toHex_good_hex_in_array_successfully() {
        val actual = byteArrayOf(0x01, 0x02, 0x0A, 0xF).toHex()
        val expected = "01020a0F"

        assert(actual).isEqualTo(expected, ignoreCase = true)
    }

    @Test
    fun toHex_negative_in_array_successfully() {
        val actual = byteArrayOf(0xFF.toByte(), 0xFE.toByte()).toHex()
        val expected = "FFFE"

        assert(actual).isEqualTo(expected, ignoreCase = true)
    }
}