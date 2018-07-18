package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.gtx.ByteArrayGTXValue
import net.postchain.gtx.GTXNull
import net.postchain.gtx.IntegerGTXValue
import net.postchain.gtx.StringGTXValue
import org.junit.Test

class GTXMLValueEncodeScalarsTest {

    @Test
    fun encodeXMLGTXValue_null_successfully() {
        val gtxValue = GTXNull
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = expected("<null xsi:nil=\"true\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>")

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
}