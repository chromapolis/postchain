package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import net.postchain.gtx.*
import org.junit.Test

class GTXMLValueEncodeTest {

    private val xmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    @Test
    fun encodeXMLGTXValue_null_successfully() {
        val gtxValue = GTXNull
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("<null/>")

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXValue_string_successfully() {
        val gtxValue = StringGTXValue("hello")
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("<string>hello</string>")

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXValue_int_successfully() {
        val gtxValue = IntegerGTXValue(42)
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("<int>42</int>")

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXValue_bytea_successfully() {
        val gtxValue = ByteArrayGTXValue(
                byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("<bytea>0102030A0B0C</bytea>")

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXValue_bytea_empty_successfully() {
        val gtxValue = ByteArrayGTXValue(byteArrayOf())
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("<bytea></bytea>")

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXValue_array_successfully() {
        val gtxValue = ArrayGTXValue(arrayOf(
                StringGTXValue("hello"),
                IntegerGTXValue(42)))
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("""
            <array>
                <string>hello</string>
                <int>42</int>
            </array>""".trimIndent())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXValue_array_empty_successfully() {
        val gtxValue = ArrayGTXValue(arrayOf())
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = arrayOf(
                expected("<array></array>"),
                expected("<array/>"))

        assert(actual).isIn(*expected)
    }

    @Test
    fun encodeXMLGTXValue_dict_successfully() {
        val gtxValue = DictGTXValue(mapOf(
                "hello" to StringGTXValue("world"),
                "123" to IntegerGTXValue(123L)
        ))
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("""
            <dict>
                <entry key="hello">
                    <string>world</string>
                </entry>
                <entry key="123">
                    <int>123</int>
                </entry>
            </dict>
        """.trimIndent())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGTXValue_dict_empty_successfully() {
        val gtxValue = DictGTXValue(mapOf())
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = arrayOf(
                expected("<dict></dict>"),
                expected("<dict/>"))

        assert(actual).isIn(*expected)
    }

    private fun expected(body: String) =
            "$xmlHeader\n$body\n"
}