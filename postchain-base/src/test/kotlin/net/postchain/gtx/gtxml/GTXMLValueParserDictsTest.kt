package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.gtx.*
import org.junit.Test

class GTXMLValueParserDictsTest {

    @Test
    fun parseGTXValue_dict_empty_successfully() {
        val xml = "<dict></dict>"
        val actual = GTXMLValueParser.parseGTXMLValue(xml)
        val expected = DictGTXValue(mapOf())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_dict_of_scalars_successfully() {
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

    @Test
    fun parseGTXValue_dict_of_dicts_successfully() {
        val xml = """
            <dict>
                <entry key="hello">
                    <string>world</string>
                </entry>
                <entry key="my_dict">
                    <dict>
                        <entry key="str"><string>kitty</string></entry>
                        <entry key="number"><int>123</int></entry>
                    </dict>
                </entry>
            </dict>
        """.trimIndent()

        val actual = GTXMLValueParser.parseGTXMLValue(xml)

        val expected = DictGTXValue(mapOf(
                "hello" to StringGTXValue("world"),
                "my_dict" to DictGTXValue(mapOf(
                        "str" to StringGTXValue("kitty"),
                        "number" to IntegerGTXValue(123)
                ))
        ))

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXValue_dict_of_all_types_successfully() {
        val xml = """
            <dict>
                <entry key="entry_1">
                    <string>foo</string>
                </entry>

                <entry key="entry_2">
                    <int>42</int>
                </entry>

                <entry key="entry_3">
                    <array>
                        <string>foo</string>
                        <string>bar</string>
                        <array>
                            <param type='int' key='param_int_42'/>
                            <int>43</int>
                            <array>
                                <int>44</int>
                            </array>
                            <dict>
                                <entry key="hello"><string>world</string></entry>
                                <entry key="123"><int>123</int></entry>
                            </dict>
                        </array>
                        <dict>
                            <entry key="hello">
                                <array>
                                    <string>world</string>
                                    <string>world</string>
                                </array>
                            </entry>
                            <entry key="123"><int>123</int></entry>
                        </dict>
                    </array>
                </entry>

                <entry key="entry_4">
                    <dict>
                        <entry key="null_entry"><null/></entry>
                        <entry key="hello"><string>world</string></entry>
                        <entry key="dict123">
                            <dict>
                                <entry key="hello">
                                    <param type='string' key='param_string_foo'/>
                                </entry>
                                <entry key="123"><int>123</int></entry>
                            </dict>
                        </entry>
                        <entry key="array123">
                            <array>
                                <int>42</int>
                                <int>43</int>
                            </array>
                        </entry>
                    </dict>
                </entry>

                <entry key="entry_5">
                    <dict>
                        <entry key="hello"><string>world</string></entry>
                        <entry key="123"><int>123</int></entry>
                        <entry key="null_entry">
                            <null/>
                        </entry>
                        <entry key="null_entry2">
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                        </entry>
                    </dict>
                </entry>

            </dict>
        """.trimIndent()

        val actual = GTXMLValueParser.parseGTXMLValue(
                xml,
                mapOf(
                        "param_int_42" to IntegerGTXValue(42),
                        "param_string_foo" to StringGTXValue("foo"))
        )

        val expected = DictGTXValue(mapOf(
                "entry_1" to StringGTXValue("foo"),

                "entry_2" to IntegerGTXValue(42),

                "entry_3" to ArrayGTXValue(arrayOf(
                        StringGTXValue("foo"),
                        StringGTXValue("bar"),
                        ArrayGTXValue(arrayOf(
                                IntegerGTXValue(42),
                                IntegerGTXValue(43),
                                ArrayGTXValue(arrayOf(
                                        IntegerGTXValue(44)
                                )),
                                DictGTXValue(mapOf(
                                        "hello" to StringGTXValue("world"),
                                        "123" to IntegerGTXValue(123)
                                ))
                        )),
                        DictGTXValue(mapOf(
                                "hello" to ArrayGTXValue(arrayOf(
                                        StringGTXValue("world"),
                                        StringGTXValue("world")
                                )),
                                "123" to IntegerGTXValue(123)
                        ))
                )),

                "entry_4" to DictGTXValue(mapOf(
                        "null_entry" to GTXNull,
                        "hello" to StringGTXValue("world"),
                        "dict123" to DictGTXValue(mapOf(
                                "hello" to StringGTXValue("foo"),
                                "123" to IntegerGTXValue(123)
                        )),
                        "array123" to ArrayGTXValue(arrayOf(
                                IntegerGTXValue(42),
                                IntegerGTXValue(43)
                        ))
                )),

                "entry_5" to DictGTXValue(mapOf(
                        "hello" to StringGTXValue("world"),
                        "123" to IntegerGTXValue(123),
                        "null_entry" to GTXNull,
                        "null_entry2" to GTXNull
                ))
        ))

        assert(actual).isEqualTo(expected)
    }
}