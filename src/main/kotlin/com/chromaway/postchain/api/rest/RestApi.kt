package com.chromaway.postchain.api.rest

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.string
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.UserError
import mu.KLogging
import spark.Request
import spark.Service

class Query
class QueryResult
class ConfirmationProof
enum class TransactionStatus { UNKNOWN, REJECTED, WAITING, CONFIRMED }

class Transaction(tx: String) {
    val bytes: ByteArray
    init {
        require(tx.length > 1) {"Tx length must not be 0"}
        bytes = tx.hexStringToByteArray()
    }
    fun bytes(): ByteArray {
        return bytes;
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Transaction) {
            return false
        }
        return other.bytes.contentEquals(bytes)
    }
}

class TxHash(private val bytes: ByteArray) {
    init {
        require(bytes.size == 32) {"Hash must be exactly 32 bytes"}
    }
    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) return true
        if (other !is TxHash) return false
        return bytes.contentEquals(other.bytes)
    }
}
data class ErrorBody(val error: String = "")

interface Model {
    fun postTransaction(tx: Transaction)
    fun getTransaction(txHash: TxHash): Transaction?
    fun getConfirmationProof(txHash: TxHash): ConfirmationProof
    fun getStatus(txHash: TxHash): TransactionStatus
    fun query(query: Query): QueryResult
}

class NotFoundError(message: String): Exception(message)

class RestApi(private val model: Model, val listenPort: Int, val basePath: String) {
    val http = Service.ignite()
    companion object: KLogging()
    init {
        route(http)
    }

    fun actualPort(): Int {
        return http.port()
    }

    fun toTransaction(req: Request): Transaction {
        try {
            val parser = Parser()
            val jsonObject = parser.parse(StringBuilder(req.body())) as JsonObject
            val txString = jsonObject.string("tx")!!
            val tx = Transaction(txString)
            return tx
        } catch (e: Exception) {
            throw UserError("Could not parse json", e)
        }
    }

    fun toTransactionFromHash(req: Request): Transaction {
        val hashHex = req.params(":hashHex")
        val bytes: ByteArray
        try {
            bytes = hashHex.hexStringToByteArray()
        } catch (e: Exception) {
            throw UserError("Can't parse hashHex $hashHex", e)
        }
        val txHash: TxHash
        try {
            txHash = TxHash(bytes)
        } catch (e: Exception) {
            throw UserError("Bytes $hashHex is not a proper hash", e)
        }
        return model.getTransaction(txHash) ?: throw NotFoundError("Can't find tx with hash $hashHex")
    }

    fun error(error: Exception): String {
        return JsonObject(mapOf("error" to error.message)).toJsonString()
    }

    fun route(http: Service) {

        http.exception(NotFoundError::class.java) { e, req, res ->
            logger.error("NotFoundError:", e)
            res.status(404)
            res.body(error(e))
        }

        http.exception(UserError::class.java) { e, req, res ->
            logger.error("UserError:", e)
            res.status(400)
            res.body(error(e))
        }
        http.exception(Exception::class.java) { e, req, res ->
            logger.error("Exception:", e)
            res.status(500)
            res.body(error(e))
        }
        http.notFound({req, res ->
            error(UserError("Not found"))
        })
        //http.before()
        http.port(listenPort)

        http.before({req, res ->
            res.type("application/json")
        })

        http.post("$basePath/tx") { req, res ->
            val b = req.body()
            logger.debug("Request body: $b")
            model.postTransaction(toTransaction(req))
        }

        http.get("$basePath/tx/:hashHex", "application/json") { req, res ->
            val hashHex = req.params(":hashHex")
            if(hashHex.length != 64 && !hashHex.matches(Regex("[0-9a-f]{64}"))) {
                throw NotFoundError("Invalid hashHex. Expected 64 hex digits [0-9a-f]")
            }
            val transaction = toTransactionFromHash(req)
            JsonObject(mapOf("tx" to transaction.bytes.toHex())).toJsonString()
        }

        http.get("$basePath/tx/:hashHex/confirmationProof") { req, res ->
        }

        http.get("$basePath/tx/:hashHex/status") { req, res ->
        }

        http.post("$basePath/query") { req, res ->
        }

        http.awaitInitialization()
    }

    fun stop() {
        http.stop()
        // Ugly hack to workaround that there is no blocking stop.
        // Test cases won't work correctly without it
        Thread.sleep(100)

    }
}