package com.chromaway.postchain.api

import com.chromaway.postchain.api.rest.Model
import com.chromaway.postchain.api.rest.RestApi
import com.chromaway.postchain.api.rest.Transaction
import com.chromaway.postchain.api.rest.TxHash
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.UserError
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import mu.KLogging
import org.easymock.EasyMock.createMock
import org.easymock.EasyMock.expect
import org.easymock.EasyMock.replay
import org.easymock.EasyMock.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class RestApiTest {
    val basePath = "/basepath"
    lateinit var restApi: RestApi
    lateinit var model: Model
    companion object: KLogging()
    @Before
    fun setup() {
        model = createMock(Model::class.java)
        restApi = RestApi(model, 0, basePath)
    }

    @After
    fun tearDown() {
        restApi?.stop()
        logger.debug { "Stopped" }
    }

    fun testGetTx(path: String, expectedCode: Int, expectedResponse: String? = null) {
        val response = get(path)
        assertEquals(expectedCode, response?.code)
        val g = Gson()
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        if (expectedCode != 200) {
            val actualMap: Map<String, Any> = g.fromJson(response?.body, mapType)
            assertNotNull(actualMap.get("error"))
            assertTrue(actualMap.get("error") is String)
            assertTrue((actualMap.get("error") as String).length>0)
        } else if (expectedResponse != null) {
            val actualMap: Map<String, Any> = g.fromJson(response?.body, mapType)
            val expectedMap: Map<String, Any> = g.fromJson(expectedResponse, mapType)
            assertEquals(expectedMap, actualMap)
        }

    }
    val hashHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    fun testGetTx404whenMissingHash() {
        testGetTx("/tx/", 404)
    }

    @Test
    fun testGetTx404whenTooShortHash() {
        testGetTx("/tx/${hashHex.substring(1)}", 404)
    }

    @Test
    fun testGetTx400whenHashNotHex() {
        testGetTx("/tx/${hashHex.replaceFirst("a", "g")}", 400)
    }

    @Test
    fun testGetTx404whenHashTooLong() {
        testGetTx("/tx/${hashHex}b", 404)
    }

    @Test
    fun testGetTx404whenPathElementAppended() {
        testGetTx("/tx/${hashHex}/b", 404)
    }

    @Test
    fun testGetTx404whenNoneFound() {
        expect(model.getTransaction(TxHash(hashHex.hexStringToByteArray()))).andReturn(null)
        replay(model)
        testGetTx("/tx/${hashHex}", 404)
        verify(model)
    }

    @Test
    fun testGetTxOk() {
        expect(model.getTransaction(TxHash(hashHex.hexStringToByteArray()))).andReturn(Transaction("1234"))
        replay(model)
        testGetTx("/tx/${hashHex}", 200, "{tx: \"1234\"}")
        verify(model)
    }

    @Test
    fun testGetTxOkWhenSlashAppended() {
        testGetTx("/tx/${hashHex}/", 404)
    }

    @Test
    fun testPostTx() {
        val expectedTx = "hello".toByteArray().toHex()
        model.postTransaction(Transaction(expectedTx))
        replay(model)

        testPostTx(200, "{\"tx\": \"$expectedTx\"}")
        verify(model)
    }

    fun testPostTx(expectedCode: Int, body: String) {
        val response = post("/tx", body)
        assertEquals(expectedCode, response?.code)
        val g = Gson()
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        if (expectedCode != 200) {
            val actualMap: Map<String, Any> = g.fromJson(response?.body, mapType)
            assertNotNull(actualMap.get("error"))
            assertTrue(actualMap.get("error") is String)
            assertTrue((actualMap.get("error") as String).length>0)
        }
    }

    @Test
    fun testPostTx400whenEmptyMessage() {
        testPostTx(400, "")
    }

    @Test
    fun testPostTx400whenMissingTxProperty() {
        testPostTx(400, "{}")
    }

    @Test
    fun testPostTx400whenEmptyTxProperty() {
        testPostTx(400, "{\"tx\": \"\"}")
    }

    @Test
    fun testPostTx400whenTxPropertyNotHex() {
        testPostTx(400, "{\"tx\": \"abc123z\"}")
    }

    @Test
    fun testPostTx400whenInvalidJson() {
        testPostTx(400, "a")
    }

    private fun requaest(method: String, path: String, reqBody: String?): TestResponse? {
        try {
            val url = URL("http://localhost:${restApi.actualPort()}" + basePath + path)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("content-type", "application/json")
            connection.requestMethod = method
            if (reqBody != null) {
                connection.doOutput = true
            }
            connection.connect()
            if (reqBody != null) {
                val out = connection.outputStream
                val writer = out.writer()
                writer.write(reqBody)
                writer.close()
            }
            if (connection.responseCode != 200) {
                val body = BufferedReader(InputStreamReader(connection.errorStream)).readLine()
                return TestResponse(connection.responseCode, body)
            }
            return TestResponse(connection.responseCode)
        } catch (e: IOException) {
            e.printStackTrace()
            fail("Sending request failed: " + e.message)
            return null
        }
    }

    private fun post(path: String, reqBody: String?): TestResponse? {
        try {
            val url = URL("http://localhost:${restApi.actualPort()}" + basePath + path)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("content-type", "application/json")
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connect()
            val out = connection.outputStream
            val writer = out.writer()
            writer.write(reqBody)
            writer.close()
            if (connection.responseCode != 200) {
                val body = BufferedReader(InputStreamReader(connection.errorStream)).readLine()
                return TestResponse(connection.responseCode, body)
            }
            return TestResponse(connection.responseCode)
        } catch (e: IOException) {
            e.printStackTrace()
            fail("Sending request failed: " + e.message)
            return null
        }
    }


    private fun get(path: String): TestResponse? {
        try {
            val url = URL("http://localhost:${restApi.actualPort()}" + basePath + path)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("content-type", "application/json")
            connection.requestMethod = "GET"
            connection.connect()
            val body = if (connection.responseCode != 200) {
                BufferedReader(InputStreamReader(connection.errorStream)).readLine()
            } else {
                BufferedReader(InputStreamReader(connection.inputStream)).readLine()
            }
            return TestResponse(connection.responseCode, body)
        } catch (e: IOException) {
            e.printStackTrace()
            fail("Sending request failed: " + e.message)
            return null
        }
    }
}

private data class TestResponse(val code: Int, val body: String? = null)
