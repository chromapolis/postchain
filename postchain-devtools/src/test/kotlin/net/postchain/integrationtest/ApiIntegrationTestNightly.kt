// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.integrationtest

import net.postchain.common.RestTools
import net.postchain.common.TestResponse
import net.postchain.base.*
import net.postchain.core.Signature
import net.postchain.core.Transaction
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.test.EbftIntegrationTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Type


class ApiIntegrationTestNightly : EbftIntegrationTest() {
    val restTools = RestTools()

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

    val checkJson = { response: TestResponse, expectedJson: String ->
        val actualMap: Map<String, Any> = gson.fromJson(response.body, mapType)
        val expectedMap: Map<String, Any> = gson.fromJson(expectedJson, mapType)
        assertEquals(expectedMap, actualMap)
    }

    @Test
    fun testMixedAPICalls() {
        createEbftNodes(3)
        testStatusGet("/tx/$hashHex", 404)
        testStatusGet("/tx/${hashHex}/status", 200,
                { checkJson(it, "{\"status\"=\"unknown\"}") })
        val tx1 = TestTransaction(1)
        testStatusPost(0, "/tx",
                "{\"tx\": \"${tx1.getRawData().toHex()}\"}",
                200)
        awaitConfirmed(tx1)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testConfirmationProof() {
        val nodeCount = 3
        createEbftNodes(nodeCount)
        var blockHeight = 0;
        var currentId = 0;
        for (txCount in 1..16) {
            for (i in 1..txCount) {
                val tx = TestTransaction(++currentId)
                testStatusPost(blockHeight % nodeCount, "/tx",
                        "{\"tx\": \"${tx.getRawData().toHex()}\"}",
                        200)
            }
            awaitConfirmed(TestTransaction(currentId))
            blockHeight++
            for (i in 0 until txCount) {
                val txId = currentId - i
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
                    val s = if (it["side"] == 0.0) Side.LEFT else if (it["side"] == 1.0) Side.RIGHT else throw AssertionError("Invalid 'side' of merkle path: ${it["side"]}")
                    val pathItemHash = (it["hash"] as String).hexStringToByteArray()
                    path.add(MerklePathItem(s, pathItemHash))
                }

                val header = ebftNodes[0].blockchainConfiguration.decodeBlockHeader(blockHeader) as BaseBlockHeader
                val txHash = TestTransaction(txId).getHash()
                assertArrayEquals(txHash, hash)
                assertTrue(header.validateMerklePath(path, txHash))
            }

        }

    }

    private fun awaitConfirmed(tx: Transaction) {
        restTools.awaitConfirmed(ebftNodes[0].restApi!!.actualPort(), tx.getRID().toHex())
    }

    private fun testStatusGet(path: String, expectedStatus: Int, extraChecks: (res: TestResponse) -> Unit = {}) {
        val response = get(path)
        if (response == null) {
            fail()
        }
        assertEquals(expectedStatus, response!!.code)
        extraChecks(response)
    }

    private fun testStatusPost(toIndex: Int, path: String, body: String, expectedStatus: Int, extraChecks: (res: TestResponse) -> Unit = {}) {
        val response = restTools.post(ebftNodes[toIndex].restApi!!.actualPort(), path, body)
        assertEquals(expectedStatus, response.code)
        extraChecks(response)
    }

    private fun get(path: String): TestResponse? {
        return restTools.get(ebftNodes[0].restApi!!.actualPort(), path)
    }

}