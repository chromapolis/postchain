package com.chromaway.postchain.api

import com.chromaway.postchain.api.rest.*
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.ProgrammerMistake
import com.chromaway.postchain.core.UserMistake
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import mu.KLogging
import org.easymock.EasyMock.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class RestApiTest : RestTools() {
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
        restApi.stop()
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
    val hashHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

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
        expect(model.getTransaction(TxRID(hashHex.hexStringToByteArray()))).andReturn(null)
        replay(model)
        testGetTx("/tx/${hashHex}", 404)
        verify(model)
    }

    @Test
    fun testGetTxOk() {
        expect(model.getTransaction(TxRID(hashHex.hexStringToByteArray()))).andReturn(ApiTx("1234"))
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
        model.postTransaction(ApiTx(expectedTx))
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

    @Test
    fun testGetConfirmationProof404whenTxDoesNotExist() {
        expect(model.getConfirmationProof(TxRID(hashHex.hexStringToByteArray()))).andReturn(null)
        replay(model)
        val response = get("/tx/$hashHex/confirmationProof")
        assertEquals(404, response?.code)
        verify(model)
    }

    @Test
    fun testQuery() {
        val qString = """{"a"="b", "c"=3}"""
        val query = Query(qString)
        val rString = """{"d"=false}"""
        val queryResult = QueryResult(rString)
        expect(model.query(query)).andReturn(queryResult)
        replay(model)
        val response = post("/query", qString)
        verify(model)
        assertEquals(200, response!!.code)
        assertJsonEquals(rString, response.body!!)
    }


    @Test
    fun testQueryUserError() {
        val qString = """{"a"="b", "c"=3}"""
        val query = Query(qString)
        expect(model.query(query)).andThrow(UserMistake("expected error"))
        replay(model)
        val response = post("/query", qString)
        verify(model)
        assertEquals(400, response!!.code)
        assertJsonEquals("""{"error": "expected error"}""", response.body!!)
    }


    @Test
    fun testQueryOtherError() {
        val qString = """{"a"="b", "c"=3}"""
        val query = Query(qString)
        expect(model.query(query)).andThrow(ProgrammerMistake("expected error"))
        replay(model)
        val response = post("/query", qString)
        verify(model)
        assertEquals(500, response!!.code)
        assertJsonEquals("""{"error": "expected error"}""", response.body!!)
    }

    private fun post(path: String, reqBody: String?): TestResponse? {
        return super.post(restApi.actualPort(), basePath + path, reqBody)
    }


    private fun get(path: String): TestResponse? {
        return super.get(restApi.actualPort(), basePath + path)
    }
}

