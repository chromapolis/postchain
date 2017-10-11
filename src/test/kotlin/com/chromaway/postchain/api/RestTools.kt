package com.chromaway.postchain.api

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*

open class RestTools {
    fun post(port: Int, path: String, reqBody: String?): TestResponse? {
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

    fun get(port: Int, path: String): TestResponse? {
        try {
            val url = URL("http://localhost:${port}" + path)
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

data class TestResponse(val code: Int, val body: String? = null)