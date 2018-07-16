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
    fun parseGTXValue_array_successfully() {
        val xml = "<array><string>hello</string><int>42</int></array>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ArrayGTXValue(arrayOf(
                StringGTXValue("hello"),
                IntegerGTXValue(42)
        ))

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_array_empty_successfully() {
        val xml = "<array></array>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = ArrayGTXValue(arrayOf())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_dict_successfully() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="123"><int>123</int></entry>
            </dict>
        """.trimIndent()

        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = DictGTXValue(mapOf(
                "hello" to StringGTXValue("world"),
                "123" to IntegerGTXValue(123L)
        ))

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_dict_empty_successfully() {
        val xml = "<dict></dict>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = DictGTXValue(mapOf())

        assert(actual).isEqualTo(expected)
    }


    /// <param />

    @Test
    fun parseGTXValue_array_with_params_successfully() {
        val xml = "<array><string>hello</string><param type='int' key='num'/></array>"
        val actual = GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("num" to IntegerGTXValue(42)))

        val expected = ArrayGTXValue(arrayOf(
                StringGTXValue("hello"),
                IntegerGTXValue(42)
        ))

        assert(actual).isEqualTo(expected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_array_with_not_found_param_throws_exception() {
        val xml = "<array><string>hello</string><param type='int' key='UNKNOWN_KEY'/></array>"
        GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("num" to IntegerGTXValue(42)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_array_with_CASE_SENSITIVE_not_found_param_throws_exception() {
        val xml = "<array><string>hello</string><param type='int' key='CASE_SENSITIVE_KEY'/></array>"
        GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("case_sensitive_key" to IntegerGTXValue(42)))
    }

    @Test
    fun parseGTXValue_dict_with_params_successfully() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="num123"><param type='int' key='p_num123'/></entry>
                <entry key="num124"><param type='int' key='p_num124'/></entry>
                <entry key="string1"><param type='string' key='p_str1'/></entry>
                <entry key="bytearray1"><param type='bytea' key='p_butea1'/></entry>
            </dict>
        """.trimIndent()

        val actual = GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf(
                        "p_num123" to IntegerGTXValue(123),
                        "p_num124" to IntegerGTXValue(124),
                        "p_str1" to StringGTXValue("my str 1"),
                        "p_butea1" to ByteArrayGTXValue(byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))
                ))

        val expected = DictGTXValue(mapOf(
                "hello" to StringGTXValue("world"),
                "num123" to IntegerGTXValue(123L),
                "num124" to IntegerGTXValue(124L),
                "string1" to StringGTXValue("my str 1"),
                "bytearray1" to ByteArrayGTXValue(byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))
        ))

        assert(actual).isEqualTo(expected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_dict_with_not_found_param_throws_exception() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="num123"><param type='int' key='UNKNOWN_KEY'/></entry>
            </dict>
        """.trimIndent()

        GTXMLValueParser.parseGTXMLValue(xml, mapOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXValue_dict_with_CASE_SENSITIVE_not_found_param_throws_exception() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="num123"><param type='int' key='CASE_SENSITIVE_KEY'/></entry>
            </dict>
        """.trimIndent()

        GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf("case_sensitive_key" to IntegerGTXValue(42)))
    }
}