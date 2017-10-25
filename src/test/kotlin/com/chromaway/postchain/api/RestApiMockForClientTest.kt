package com.chromaway.postchain.api

import com.chromaway.postchain.api.rest.ApiStatus
import com.chromaway.postchain.api.rest.ApiTx
import com.chromaway.postchain.api.rest.Model
import com.chromaway.postchain.api.rest.Query
import com.chromaway.postchain.api.rest.QueryResult
import com.chromaway.postchain.api.rest.RestApi
import com.chromaway.postchain.api.rest.TxHash
import com.chromaway.postchain.base.ConfirmationProof
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.ProgrammerMistake
import com.chromaway.postchain.core.TransactionStatus
import com.chromaway.postchain.core.UserMistake
import mu.KLogging
import org.junit.After
import org.junit.Test

class RestApiMockForClientManual : RestTools() {
    val listenPort = 49545
    val basePath = "/basepath"
    lateinit var restApi: RestApi

    companion object: KLogging()

    @After
    fun tearDown() {
        restApi.stop()
        logger.debug { "Stopped" }
    }

    @Test
    fun startMockRestApi() {
        val model = MockModel()
        restApi = RestApi(model, listenPort, basePath)
        logger.info("Ready to serve on port ${restApi.actualPort()}")
        Thread.sleep(600000) // Wait 10 minutes
    }




    class MockModel: Model {
        val statusUnknown   = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val statusRejected  = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val statusConfirmed = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val statusNotFound  = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val statusWaiting   = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        override fun postTransaction(tx: ApiTx) {
            when (tx.tx) {
                "helloOK".toByteArray().toHex() -> return
                "hello400".toByteArray().toHex() -> throw UserMistake("expected error")
                "hello500".toByteArray().toHex() -> throw ProgrammerMistake("expected error")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getTransaction(txHash: TxHash): ApiTx? {
            return when (txHash) {
                TxHash(statusUnknown.hexStringToByteArray()) -> null
                TxHash(statusConfirmed.hexStringToByteArray()) -> ApiTx("1234")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getConfirmationProof(txHash: TxHash): ConfirmationProof? {
            return when (txHash) {
                TxHash(statusUnknown.hexStringToByteArray()) -> null
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getStatus(txHash: TxHash): ApiStatus {
            return when (txHash) {
                TxHash(statusUnknown.hexStringToByteArray()) -> ApiStatus(TransactionStatus.UNKNOWN)
                TxHash(statusWaiting.hexStringToByteArray()) -> ApiStatus(TransactionStatus.WAITING)
                TxHash(statusConfirmed.hexStringToByteArray()) -> ApiStatus(TransactionStatus.CONFIRMED)
                TxHash(statusRejected.hexStringToByteArray()) -> ApiStatus(TransactionStatus.REJECTED)
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun query(query: Query): QueryResult {
            return QueryResult(when (query.json) {
                """{"a":"oknullresponse","c":3}""" -> ""
                """{"a":"okemptyresponse","c":3}""" -> """{}"""
                """{"a":"oksimpleresponse","c":3}""" -> """{"test":"hi"}"""
                """{"a":"usermistake","c":3}""" -> throw UserMistake("expected error")
                """{"a":"programmermistake","c":3}""" -> throw ProgrammerMistake("expected error")
                else -> throw ProgrammerMistake("unexpected error")
            })
        }

    }
}