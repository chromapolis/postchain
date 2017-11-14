// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.RuntimeException
import java.net.HttpURLConnection
import java.net.URL

open class RestTools {
    fun post(port: Int, path: String, reqBody: String?): TestResponse {
        try {
            val url = URL("http://localhost:${port}" + path)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
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
            throw RuntimeException("Sending request failed: " + e.message)
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
            throw RuntimeException("Sending request failed: " + e.message)
        }
    }

    val mapType = object : TypeToken<Map<String, Any>>() {}.type

    fun awaitConfirmed(port: Int, txRidHex: String) {
        do {
            val response = get(port, "/tx/${txRidHex}/status")
            if (response == null) throw RuntimeException()
            if (response.code != 200) throw RuntimeException()
            val resultMap: Map<String, Any> = Gson().fromJson(response.body, mapType)
            if (!resultMap.containsKey("status")) throw RuntimeException()
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