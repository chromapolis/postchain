package net.postchain.gtx.gtxml

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.gtx.GTXData
import org.junit.Test

class GTXMLTransactionParserBlockchainRIDTest {

    @Test
    fun parseGTXMLTransaction_in_context_with_empty_blockchainRID_successfully() {
        val xml = """
            <transaction blockchainRID="">
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expected = GTXData(
                byteArrayOf(0x0A, 0x0B, 0x0C),
                arrayOf(), arrayOf(), arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml,
                TransactionContext(byteArrayOf(0x0A, 0x0B, 0x0C)))

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_no_blockchainRID_successfully() {
        val xml = """
            <transaction>
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expected = GTXData(
                byteArrayOf(0x0A, 0x0B, 0x0C),
                arrayOf(), arrayOf(), arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml,
                TransactionContext(byteArrayOf(0x0A, 0x0B, 0x0C)))

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_no_blockchainRID_and_null_context_one_successfully() {
        val xml = """
            <transaction>
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expected = GTXData(
                byteArrayOf(),
                arrayOf(), arrayOf(), arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml,
                TransactionContext(null))

        assert(actual).isEqualTo(expected)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_blockchainRID_equal_to_context_one_successfully() {
        val xml = """
            <transaction blockchainRID="0a0b0c">
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expected = GTXData(
                byteArrayOf(0x0A, 0x0B, 0x0C),
                arrayOf(), arrayOf(), arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml,
                TransactionContext(null))

        assert(actual).isEqualTo(expected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseGTXMLTransaction_in_context_with_blockchainRID_not_equal_to_context_one_successfully() {
        val xml = """
            <transaction blockchainRID="0a0b0c">
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expected = GTXData(
                byteArrayOf(0x0A, 0x0B, 0x0C),
                arrayOf(), arrayOf(), arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml,
                TransactionContext(byteArrayOf(0x01, 0x02, 0x03)))

        assert(actual).isEqualTo(expected)
    }
}