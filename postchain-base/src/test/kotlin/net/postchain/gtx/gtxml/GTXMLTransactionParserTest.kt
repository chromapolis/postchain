package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.gtx.GTXData
import net.postchain.gtx.IntegerGTXValue
import net.postchain.gtx.OpData
import net.postchain.gtx.StringGTXValue
import org.junit.Test

class GTXMLTransactionParserTest {

    @Test
    fun parseGTXMLTransaction_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/tx_full.xml").readText()

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

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, null)

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_signers_and_signatures_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/tx_empty_signers_and_signatures.xml").readText()

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

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, null)

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_operations_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/tx_empty_operations.xml").readText()

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

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, null)

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_operation_parameters_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/tx_empty_operation_parameters.xml").readText()

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

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, null)

        assert(actual).isEqualTo(expected)
    }
}