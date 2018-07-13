package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.gtx.*
import org.junit.Test

class GTXMLValueParserTest {

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
        val value = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ByteArrayGTXValue(
                byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))

        assert(value).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_bytea_empty_successfully() {
        val xml = "<bytea></bytea>"
        val value = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ByteArrayGTXValue(
                byteArrayOf())

        assert(value).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_array_successfully() {
        val xml = "<array><string>hello</string><int>42</int></array>"
        val value = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ArrayGTXValue(arrayOf(
                StringGTXValue("hello"),
                IntegerGTXValue(42)
        ))

        assert(value).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_array_empty_successfully() {
        val xml = "<array></array>"
        val value = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ArrayGTXValue(arrayOf())

        assert(value).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_dict_successfully() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="123"><int>123</int></entry>
            </dict>
        """.trimIndent()

        val value = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = DictGTXValue(mapOf(
                "hello" to StringGTXValue("world"),
                "123" to IntegerGTXValue(123L)
        ))

        assert(value).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_dict_empty_successfully() {
        val xml = "<dict></dict>"
        val value = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = DictGTXValue(mapOf())

        assert(value).isEqualTo(expected)
    }
}