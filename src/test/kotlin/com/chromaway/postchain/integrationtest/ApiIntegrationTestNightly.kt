package com.chromaway.postchain.integrationtest

import com.chromaway.postchain.api.RestTools
import com.chromaway.postchain.api.TestResponse
import com.chromaway.postchain.api.rest.PostchainModel
import com.chromaway.postchain.api.rest.RestApi
import com.chromaway.postchain.base.cryptoSystem
import com.chromaway.postchain.base.data.BaseTransactionQueue
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.base.validateMerklePath
import com.chromaway.postchain.core.MerklePath
import com.chromaway.postchain.core.MerklePathItem
import com.chromaway.postchain.core.Side
import com.chromaway.postchain.core.Signature
import com.chromaway.postchain.core.Transaction
import com.chromaway.postchain.ebft.BuildBlockIntent
import com.chromaway.postchain.ebft.EbftIntegrationTest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializer
import com.google.gson.GsonBuilder
import java.lang.reflect.Type


class ApiIntegrationTestNightly : EbftIntegrationTest() {
    lateinit var restApi: RestApi
    val restTools = RestTools()

    fun createSystem(count: Int) {
        ebftNodes = createEbftNodes(count)
        val model = PostchainModel(ebftNodes[0].dataLayer.txEnqueuer, ebftNodes[0].dataLayer.blockchainConfiguration.getTransactionFactory(),
                ebftNodes[0].dataLayer.blockQueries)
        restApi = RestApi(model, 0, "")
    }

    @After
    fun tearDownApi() {
        restApi.stop()
    }

    var hashHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

//    fun testStatusGet
    val gson = GsonBuilder().
        registerTypeAdapter(Double::class.java, object : JsonSerializer<Double> {
        // Avoid parsing integers as doubles
        override fun serialize(src: Double?, typeOfSrc: Type,
                               context: JsonSerializationContext): JsonElement {
            val value = Math.round(src!!).toInt()
            return JsonPrimitive(value)
        }
    }).create()


    val mapType = object : TypeToken<Map<String, Any>>() {}.type

    val checkJson = {response: TestResponse, expectedJson: String ->
        val actualMap: Map<String, Any> = gson.fromJson(response.body, mapType)
        val expectedMap: Map<String, Any> = gson.fromJson(expectedJson, mapType)
        assertEquals(expectedMap, actualMap)
    }

    @Test
    fun testMixedAPICalls() {
        createSystem(3)
        startUpdateLoop()
        ebftNodes[0].statusManager.setBlockIntent(BuildBlockIntent)
        testStatusGet("/tx/$hashHex", 404)
        testStatusGet("/tx/${hashHex}/status", 200,
                {checkJson(it, "{\"status\"=\"unknown\"}")})
        val tx1 = TestTransaction(1)
        testStatusPost("/tx",
                "{\"tx\": \"${tx1.getRawData().toHex()}\"}",
                200)
        awaitConfirmed(tx1)
    }

    @Test
    fun testConfirmationProof() {
        createSystem(3)
        startUpdateLoop()
        ebftNodes[0].statusManager.setBlockIntent(BuildBlockIntent)

        for (txCount in 1..16) {
            for (i in 1..txCount) {

                val tx = TestTransaction((txCount*(txCount-1)/2)+i)
                testStatusPost("/tx",
                        "{\"tx\": \"${tx.getRawData().toHex()}\"}",
                        200)
            }
            awaitConfirmed(TestTransaction(((txCount+1)*txCount/2)))

            for (i in 1..txCount) {
                val txId = txCount*(txCount-1)/2+i
                val response = get("/tx/${TestTransaction(txId).getRID().toHex()}/confirmationProof")
                assertEquals(200, response!!.code)
                val body = response.body!!

                val mapType = object : TypeToken<Map<String, Any>>() {}.type

                val actualMap: Map<String, Any> = gson.fromJson(body, mapType)
                val hash = (actualMap.get("hash") as String).hexStringToByteArray()
                val blockHeader = (actualMap.get("blockHeader") as String).hexStringToByteArray()
                val signatures = actualMap.get("signatures") as List<Map<String, String>>
                val merklePath = actualMap.get("merklePath") as List<Map<String, Any>>
                val verifier = cryptoSystem.makeVerifier()
                signatures.forEach {
                    assertTrue(verifier(blockHeader,
                            Signature(it["pubKey"]!!.hexStringToByteArray(),
                                    it["signature"]!!.hexStringToByteArray())))
                }

                val path = MerklePath()
                merklePath.forEach {
                    // The use of 0.0 and 1.0 is to work around that the json parser creates doubles
                    // instances from json integer values
                    val s = if (it["side"] == 0.0) Side.LEFT else if (it["side"] == 1.0) Side.RIGHT else fail() as Side
                    val pathItemHash = (it["hash"] as String).hexStringToByteArray()
                    path.add(MerklePathItem(s, pathItemHash))
                }

                val header = ebftNodes[0].dataLayer.blockchainConfiguration.decodeBlockHeader(blockHeader)
                val rid = TestTransaction(txId).getRID()
                assertArrayEquals(rid, hash)
                assertTrue(header.validateMerklePath(path, rid))
            }

        }

    }

    private fun awaitConfirmed(tx: Transaction) {
        restTools.awaitConfirmed(restApi.actualPort(), tx)
    }

    private fun testStatusGet(path: String, expectedStatus: Int, extraChecks: (res: TestResponse) -> Unit = {}) {
        val response = get(path)
        if (response == null) {
            fail()
        }
        assertEquals(expectedStatus, response!!.code)
        extraChecks(response)
    }

    private fun testStatusPost(path: String, body: String, expectedStatus: Int, extraChecks: (res: TestResponse) -> Unit = {}) {
        val response = post(path, body)
        if (response == null) {
            fail()
        }
        assertEquals(expectedStatus, response!!.code)
        extraChecks(response)
    }


    private fun post(path: String, reqBody: String?): TestResponse? {
        return restTools.post(restApi.actualPort(), path, reqBody)
    }


    private fun get(path: String): TestResponse? {
        return restTools.get(restApi.actualPort(), path)
    }

}