package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.gtx.*
import org.junit.Test

class GTXMLValueParserScalarsTest {

    @Test
    fun parseGTXValue_null_successfully() {
        val xml = "<null />"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = GTXNull

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_string_successfully() {
        val xml = "<string>hello</string>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = StringGTXValue("hello")

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_int_successfully() {
        val xml = "<int>42</int>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = IntegerGTXValue(42L)

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_bytea_successfully() {
        val xml = "<bytea>0102030A0B0C</bytea>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ByteArrayGTXValue(
                byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_bytea_empty_successfully() {
        val xml = "<bytea></bytea>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ByteArrayGTXValue(
                byteArrayOf())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_param_successfully() {
        val xml = "<param key='param_key'/>"

        val actual = GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("param_key" to IntegerGTXValue(123)))

        val expected = IntegerGTXValue(123)

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_param_compatible_type_successfully() {
        val xml = "<param key='param_key' type='int'/>"

        val actual = GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("param_key" to IntegerGTXValue(123)))

        val expected = IntegerGTXValue(123)

        assert(actual).isEqualTo(expected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_param_incompatible_type_successfully() {
        val xml = "<param key='param_key' type='string'/>"
        GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("param_key" to ArrayGTXValue(arrayOf(IntegerGTXValue(123)))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_param_unknown_type_successfully() {
        val xml = "<param key='param_key' type='UNKNOWN_TYPE'/>"
        GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("param_key" to IntegerGTXValue(123)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_param_not_found_throws_exception() {
        val xml = "<param key='param_key_not_found' type='int'/>"
        GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("param_key" to IntegerGTXValue(123)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_array_with_CASE_SENSITIVE_not_found_param_throws_exception() {
        val xml = "<param key='CASE_SENSITIVE_KEY' type='int'/>"
        GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("case_sensitive_key" to IntegerGTXValue(123)))
    }
}