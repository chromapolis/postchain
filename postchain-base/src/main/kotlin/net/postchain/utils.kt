// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain

import net.postchain.base.Signer
import net.postchain.base.gtxml.*
import net.postchain.core.ByteArrayKey
import net.postchain.gtx.GTXData
import net.postchain.gtx.GTXNull
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx
import javax.xml.bind.JAXBContext

class TransactionContext(val blockchainRID: ByteArray?,
                         val params: Map<String, GTXValue> = mapOf(),
                         val autoSign: Boolean = false,
                         val signers: Map<ByteArrayKey, Signer> = mapOf()) {
}

class GTXMLParser() {

    //private val jctx = JAXBContext.newInstance(Test::class.java)
    //private val unmarshaller = jctx.createUnmarshaller()

    fun parseGTXMLValue(xml: String, params: Map<String, GTXValue> = mapOf()): GTXValue {
        xml.reader().use {
            println(it)
            //val v = unmarshaller.unmarshal(it) as Test
            //println(v.javaClass)
            //println(v.block)
            //println(v.block.get(0).transaction)
        }
        return gtx(0)
    }

    fun parseGTXMLTransaction(xml: String,
                              ctx: TransactionContext): GTXData {
        TODO()
    }

    fun parseGTXMLTransaction(xml: String,
                              params: Map<String, GTXValue> = mapOf(),
                              signers: Map<ByteArrayKey, ByteArray> = mapOf()): GTXData {
        TODO()
    }

    fun encodeGTXMLValue(v: GTXValue): String {
        TODO()
    }

    fun encodeGTXMLTransaction(d: GTXData): String {
        TODO()
    }
}
fun parseInt(str: String): Int? {
    return str.toIntOrNull()
}
/*
fun printProduct(arg1: String, arg2: String) {
   val x = parseInt(arg1)
   val y = parseInt(arg2)

    // Using `x * y` yields error because they may hold nulls.
    if (x != null && y != null) {
        -        // x and y are automatically cast to non-nullable after null check
        -        println(x * y)
        -    }
    -    else {
        -        println("either '$arg1' or '$arg2' is not a number")
        -    }
    -}*/