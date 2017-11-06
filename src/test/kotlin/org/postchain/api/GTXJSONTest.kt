// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.api

import org.postchain.gtx.make_gtx_gson
import org.postchain.base.hexStringToByteArray
import org.postchain.gtx.GTXValue
import org.postchain.gtx.gtx
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert
import org.junit.Test

class GTXJSONTest {

    @Test
    fun testSerialization() {
        val gson = make_gtx_gson()
        val jsonValue = JsonObject()
        jsonValue.add("foo", JsonPrimitive("bar"))
        jsonValue.add("bar", JsonPrimitive("1234"))
        val gtxValue = gson.fromJson<GTXValue>(jsonValue, GTXValue::class.java)!!
        Assert.assertEquals("bar", gtxValue["foo"]!!.asString())
        Assert.assertEquals("1234", gtxValue["bar"]!!.asString())
        Assert.assertTrue(gtxValue["bar"]!!.asByteArray(true).size == 2)
    }

    @Test
    fun testBoth() {
        val gson = make_gtx_gson()
        val gtxValue = gtx("foo" to gtx("bar"), "bar" to gtx("1234".hexStringToByteArray()))
        val jsonValue = gson.toJson(gtxValue, GTXValue::class.java)
        println(jsonValue)
        val gtxValue2 = gson.fromJson<GTXValue>(jsonValue, GTXValue::class.java)!!
        Assert.assertEquals("bar", gtxValue2["foo"]!!.asString())
        Assert.assertEquals("1234", gtxValue2["bar"]!!.asString())
        Assert.assertTrue(gtxValue2["bar"]!!.asByteArray(true).size == 2)
    }


}