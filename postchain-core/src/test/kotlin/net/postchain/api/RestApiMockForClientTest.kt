// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.api

import net.postchain.api.rest.*
import net.postchain.base.ConfirmationProof
import net.postchain.base.hexStringToByteArray
import net.postchain.base.toHex
import net.postchain.core.ProgrammerMistake
import net.postchain.core.TransactionStatus
import net.postchain.core.UserMistake
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

        override fun getTransaction(txRID: TxRID): ApiTx? {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> null
                TxRID(statusConfirmed.hexStringToByteArray()) -> ApiTx("1234")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getConfirmationProof(txRID: TxRID): ConfirmationProof? {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> null
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getStatus(txRID: TxRID): ApiStatus {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> ApiStatus(TransactionStatus.UNKNOWN)
                TxRID(statusWaiting.hexStringToByteArray()) -> ApiStatus(TransactionStatus.WAITING)
                TxRID(statusConfirmed.hexStringToByteArray()) -> ApiStatus(TransactionStatus.CONFIRMED)
                TxRID(statusRejected.hexStringToByteArray()) -> ApiStatus(TransactionStatus.REJECTED)
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