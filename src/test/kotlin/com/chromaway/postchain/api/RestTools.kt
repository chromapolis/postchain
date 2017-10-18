package com.chromaway.postchain.api

import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.Transaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

open class RestTools {
    fun post(port: Int, path: String, reqBody: String?): TestResponse {
        try {
            val url = URL("http://localhost:${port}" + path)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("content-type", "application/json")
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connect()
            val out = connection.outputStream
            val writer = out.writer()
            writer.write(reqBody)
            writer.close()

            return readResponse(connection)
        } catch (e: IOException) {
            e.printStackTrace()
            fail("Sending request failed: " + e.message)
            throw Error()
        }
    }

    fun get(port: Int, path: String): TestResponse? {
        try {
            val url = URL("http://localhost:${port}" + path)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("content-type", "application/json")
            connection.requestMethod = "GET"
            connection.connect()

            return readResponse(connection)
        } catch (e: IOException) {
            e.printStackTrace()
            fail("Sending request failed: " + e.message)
            return null
        }
    }

    fun assertJsonEquals(expectedJson: String, actual: String) {
        val parser = JsonParser()
        assertEquals(parser.parse(expectedJson), parser.parse(actual))
    }

    val mapType = object : TypeToken<Map<String, Any>>() {}.type

    fun awaitConfirmed(port: Int, tx: Transaction) {
        do {
            val response = get(port, "/tx/${tx.getRID().toHex()}/status")
            if (response == null) throw Error()
            assertEquals(200, response.code)
            val resultMap: Map<String, Any> = Gson().fromJson(response.body, mapType)
            Assert.assertTrue(resultMap.containsKey("status"))
            Thread.sleep(100)
        } while (resultMap.get("status") != "confirmed")
    }

    private fun readResponse(connection: HttpURLConnection): TestResponse {
        val inStr = if (connection.responseCode != 200)
            connection.errorStream
        else
            connection.inputStream
        val body = BufferedReader(InputStreamReader(inStr)).readLine()
        return TestResponse(connection.responseCode, body)
    }
}

data class TestResponse(val code: Int, val body: String? = null)