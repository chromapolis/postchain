package net.postchain.test

import net.postchain.DataLayer
import net.postchain.base.gtxml.Tests
import net.postchain.common.hexStringToByteArray
import javax.xml.bind.JAXBContext

class Tester : IntegrationTest() {


    fun runGTXMLTests(xml: String, node: DataLayer): Boolean {

        val jctx = JAXBContext.newInstance(Tests::class.java)
        val unmarshaller = jctx.createUnmarshaller()

        xml.reader().use {
            val tests = unmarshaller.unmarshal(it) as Tests
            tests.test.forEach { test ->
                test.block.forEach { block ->
                    block.transaction.forEach { tx ->
                        val chainRID = tx.blockchainRID?.hexStringToByteArray()
                        val fail = tx.failure
                        val signature = tx.signatures
                        val signers = tx.signers
                        tx.operations.operation.forEach { op ->
                            println(op.name)
                            println(op)
                            val name = op.name

                        }
                    }
                }
            }
        }
        return true
    }

    fun createDataL(id: Int = 0): DataLayer {
        return createDataLayer(id)
    }
}

fun main(args: Array<String>) {
    //var i = 0
    //var filename:String = ""
    //while (i < args.size) {
    //when (args[i]) {
    //"-f", "--file" -> {
    //filename = args[++i]
    //}
    //}
    //i++
    //}

    //val it = GTXMLTest()
    //val parser = GTXMLParser()
    //if(!filename.isEmpty())
    //it.runGTXMLTests(File(filename).inputStream().bufferedReader().readText())
    //else
    //val v = parser.parseGTXMLValue("<test><block></block></test>")
    val tester = Tester()
    val node = tester.createDataL(0)
    val xml = """
        <tests>
        <test>
  <block>
    <transaction>
      <signers><param type="bytea" key="user_1_pub"/></signers>
      <operations>
       <operation name="etk_transfer">
          <param type="bytea" key="user_1_pub" />
          <int>42</int>
       </operation>
      </operations>
     </transaction>
     <transaction failure='true'>
      <signers><param type="bytea" key="user_1_pub"/></signers>
      <operations>
       <operation name="etk_transfer">
          <param type="bytea" key="user_1_pub" />
          <int>422139012312312</int>
       </operation>
      </operations>
     </transaction>
    </block>
   </test>
   </tests>
    """.trimIndent()
    tester.runGTXMLTests(xml, node)
}
