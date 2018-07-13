package net.postchain.gtx.gtxml

import net.postchain.base.gtxml.TestType
import java.io.StringReader
import javax.xml.bind.JAXB

object GTXMLTestParser {

    /**
     * Parses XML represented as string into [TestType]
     */
    fun parseGTXMLTest(xml: String): TestType {
        val test = JAXB.unmarshal(StringReader(xml), TestType::class.java)

        // TODO: Do something here

        return test
    }
}