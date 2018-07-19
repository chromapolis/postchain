package net.postchain.common

import assertk.assert
import assertk.isСontentEqualTo
import org.junit.Test

class UtilsHexStringToByteArrayTest {

    @Test
    fun hexStringToByteArray_empty_successfully() {
        val actual = "".hexStringToByteArray()
        val expected = ByteArray(0)

        assert(actual).isСontentEqualTo(expected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun hexStringToByteArray_single_symbol_throws_exception() {
        "0".hexStringToByteArray()
    }

    @Test
    fun hexStringToByteArray_good_hex_string_successfully() {
        val actual = "0123456708090A0B0C0D0E0F".hexStringToByteArray()
        val expected = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F)

        assert(actual).isСontentEqualTo(expected)
    }

    @Test
    fun hexStringToByteArray_good_negative_hex_string_successfully() {
        val actual = "FF88".hexStringToByteArray()
        val expected = byteArrayOf(0xFF.toByte(), 0x88.toByte())

        assert(actual).isСontentEqualTo(expected)
    }

    @Test
    fun hexStringToByteArray_case_insensitive_string_successfully() {
        val actual = "0a0B0C0d0E0F".hexStringToByteArray()
        val expected = byteArrayOf(0x0A, 0x0b, 0x0C, 0x0D, 0x0e, 0x0F)

        assert(actual).isСontentEqualTo(expected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun hexStringToByteArray_not_hex_symbol_throws_exception() {
        "tg".hexStringToByteArray()
    }
}