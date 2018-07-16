package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.gtx.*
import org.junit.Test

class GTXMLTransactionParserTest {

    @Test
    fun parseGTXMLTransaction_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_full.xml").readText()

        val expected = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                ),
                arrayOf(
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("hello"),
                                        StringGTXValue("hello2"),
                                        StringGTXValue("hello3"),
                                        IntegerGTXValue(42),
                                        IntegerGTXValue(43))),
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("HELLO"),
                                        StringGTXValue("HELLO2"),
                                        IntegerGTXValue(142),
                                        IntegerGTXValue(143)))
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_signers_and_signatures_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_empty_signers_and_signatures.xml").readText()

        val expected = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(),
                arrayOf(),
                arrayOf(
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("hello"),
                                        StringGTXValue("hello2"),
                                        StringGTXValue("hello3"),
                                        IntegerGTXValue(42),
                                        IntegerGTXValue(43))),
                        OpData("ft_transfer",
                                arrayOf(
                                        StringGTXValue("HELLO"),
                                        StringGTXValue("HELLO2"),
                                        IntegerGTXValue(142),
                                        IntegerGTXValue(143)))
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_operations_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_empty_operations.xml").readText()

        val expected = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                ),
                arrayOf()
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_operation_parameters_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_empty_operation_parameters.xml").readText()

        val expected = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                ),
                arrayOf(OpData("ft_transfer", arrayOf()))
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty())

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_params_in_all_sections_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_full_params.xml").readText()

        val expected = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x01, 0x02, 0x03)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D),
                        byteArrayOf(0x0E, 0x0F)
                ),
                arrayOf(OpData("ft_transfer",
                        arrayOf(StringGTXValue("hello"),
                                StringGTXValue("my string param"),
                                IntegerGTXValue(123),
                                ByteArrayGTXValue(byteArrayOf(0x0A, 0x0B, 0x0C)))
                ))
        )

        val context = TransactionContext(
                null,
                mapOf(
                        "param_signer" to ByteArrayGTXValue(byteArrayOf(0x01, 0x02, 0x03)),

                        "param_string" to StringGTXValue("my string param"),
                        "param_int" to IntegerGTXValue(123),
                        "param_bytearray" to ByteArrayGTXValue(byteArrayOf(0x0A, 0x0B, 0x0C)),

                        "param_signature_1" to ByteArrayGTXValue(byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)),
                        "param_signature_2" to ByteArrayGTXValue(byteArrayOf(0x0E, 0x0F))
                )
        )
        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, context)

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_with_params_in_all_sections_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_full_params.xml").readText()

        val expected = GTXData(
                "23213213".hexStringToByteArray(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x01, 0x02, 0x03)
                ),
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D),
                        byteArrayOf(0x0E, 0x0F)
                ),
                arrayOf(OpData("ft_transfer",
                        arrayOf(StringGTXValue("hello"),
                                StringGTXValue("my string param"),
                                IntegerGTXValue(123),
                                ByteArrayGTXValue(byteArrayOf(0x0A, 0x0B, 0x0C)))
                ))
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml,
                mapOf(
                        "param_signer" to ByteArrayGTXValue(byteArrayOf(0x01, 0x02, 0x03)),

                        "param_string" to StringGTXValue("my string param"),
                        "param_int" to IntegerGTXValue(123),
                        "param_bytearray" to ByteArrayGTXValue(byteArrayOf(0x0A, 0x0B, 0x0C)),

                        "param_signature_1" to ByteArrayGTXValue(byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)),
                        "param_signature_2" to ByteArrayGTXValue(byteArrayOf(0x0E, 0x0F))
                ))

        assert(actual).isEqualTo(expected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXMLTransaction_in_context_with_not_found_params_throws_exception() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_full_params_not_found.xml").readText()

        GTXMLTransactionParser.parseGTXMLTransaction(
                xml, TransactionContext.empty())
    }
}