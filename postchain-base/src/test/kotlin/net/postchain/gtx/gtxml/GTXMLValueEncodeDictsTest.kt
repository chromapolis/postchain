package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import net.postchain.gtx.*
import org.junit.Test

class GTXMLValueEncodeDictsTest {

    @Test
    fun encodeXMLGTXValue_dict_empty_successfully() {
        val gtxValue = DictGTXValue(mapOf())
        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)
        val expected = arrayOf(
                expected("<dict></dict>"),
                expected("<dict/>"))

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
    fun encodeXMLGTXValue_compound_dict_successfully() {
        val gtxValue = DictGTXValue(mapOf(
                "k1" to StringGTXValue("hello"),
                "k2" to IntegerGTXValue(42),
                "k3" to ArrayGTXValue(arrayOf()),
                "k4" to ArrayGTXValue(arrayOf(
                        ArrayGTXValue(arrayOf(
                                GTXNull,
                                DictGTXValue(mapOf(
                                        "1" to StringGTXValue("1"),
                                        "2" to IntegerGTXValue(2)
                                ))
                        )),
                        DictGTXValue(mapOf(
                                "array" to ArrayGTXValue(arrayOf(
                                        IntegerGTXValue(1),
                                        StringGTXValue("2")
                                )),
                                "str" to StringGTXValue("foo"),
                                "int" to IntegerGTXValue(42)
                        ))
                )),
                "k5" to DictGTXValue(mapOf()),
                "k6" to DictGTXValue(mapOf(
                        "0" to GTXNull,
                        "1" to StringGTXValue("1"),
                        "2" to IntegerGTXValue(42)
                ))
        ))

        val actual = GTXMLValueEncoder.encodeXMLGTXValue(gtxValue)

        val expected = expected("""
            <dict>
                <entry key="k1">
                    <string>hello</string>
                </entry>
                <entry key="k2">
                    <int>42</int>
                </entry>
                <entry key="k3">
                    <array/>
                </entry>
                <entry key="k4">
                    <array>
                        <array>
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                            <dict>
                                <entry key="1">
                                    <string>1</string>
                                </entry>
                                <entry key="2">
                                    <int>2</int>
                                </entry>
                            </dict>
                        </array>
                        <dict>
                            <entry key="array">
                                <array>
                                    <int>1</int>
                                    <string>2</string>
                                </array>
                            </entry>
                            <entry key="str">
                                <string>foo</string>
                            </entry>
                            <entry key="int">
                                <int>42</int>
                            </entry>
                        </dict>
                    </array>
                </entry>
                <entry key="k5">
                    <dict/>
                </entry>
                <entry key="k6">
                    <dict>
                        <entry key="0">
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                        </entry>
                        <entry key="1">
                            <string>1</string>
                        </entry>
                        <entry key="2">
                            <int>42</int>
                        </entry>
                    </dict>
                </entry>
            </dict>""".trimIndent())

        assert(actual).isEqualTo(expected)
    }
}