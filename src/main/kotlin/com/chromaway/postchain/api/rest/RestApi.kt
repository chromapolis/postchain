package com.chromaway.postchain.api.rest

import com.chromaway.postchain.base.ConfirmationProof
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.toHex
import com.chromaway.postchain.core.MultiSigBlockWitness
import com.chromaway.postchain.core.Side
import com.chromaway.postchain.core.TransactionStatus
import com.chromaway.postchain.core.UserMistake
import com.google.gson.*
import mu.KLogging
import spark.Request
import spark.Service
import java.lang.reflect.Type
import java.util.*


class RestApi(private val model: Model, private val listenPort: Int, private val basePath: String) {
    val http = Service.ignite()!!
    companion object: KLogging()
    private val gson = GsonBuilder().
            registerTypeAdapter(ConfirmationProof::class.java, ConfirmationProofSerializer()).
            registerTypeAdapter(ApiTx::class.java, TransactionDeserializer()).
            registerTypeAdapter(ApiStatus::class.java, ApiStatusSerializer()).
            create()!!

    init {
        route(http)
        logger.info { "Rest API listening on port ${actualPort()}" }
        logger.info { "Rest API attached on ${basePath}/" }
    }

    fun actualPort(): Int {
        return http.port()
    }

    private fun toTransaction(req: Request): ApiTx {
        try {
            return gson.fromJson<ApiTx>(req.body(), ApiTx::class.java)
//            val parser = Parser()
//            val jsonObject = parser.parse(StringBuilder(req.body())) as JsonObject
//            val txString = jsonObject.string("tx")!!
//            val tx = Transaction(txString)
//            return tx
        } catch (e: Exception) {
            throw UserMistake("Could not parse json", e)
        }
    }

    private fun toTransactionFromHash(req: Request): ApiTx {
        val hashHex = req.params(":hashHex")
        val txHash: TxHash = toTxHash(hashHex)
        return model.getTransaction(txHash) ?: throw NotFoundError("Can't find tx with hash $hashHex")
    }

    private fun toTxHash(hashHex: String): TxHash {
        val bytes: ByteArray
        try {
            bytes = hashHex.hexStringToByteArray()
        } catch (e: Exception) {
            throw UserMistake("Can't parse hashHex $hashHex", e)
        }
        val txHash: TxHash
        try {
            txHash = TxHash(bytes)
        } catch (e: Exception) {
            throw UserMistake("Bytes $hashHex is not a proper hash", e)
        }
        return txHash
    }

    fun error(error: Exception): String {
        return gson.toJson(ErrorBody(error.message?:"Unknown error"))
    }

    private fun route(http: Service) {

        http.exception(NotFoundError::class.java) { e, _, res ->
            logger.error("NotFoundError:", e)
            res.status(404)
            res.body(error(e))
        }

        http.exception(UserMistake::class.java) { e, _, res ->
            logger.error("UserMistake:", e)
            res.status(400)
            res.body(error(e))
        }
        http.exception(Exception::class.java) { e, _, res ->
            logger.error("Exception:", e)
            res.status(500)
            res.body(error(e))
        }
        http.notFound({ _, _ -> error(UserMistake("Not found")) })
        //http.before()
        http.port(listenPort)

        http.before({ _, res ->
            res.type("application/json")
        })

        http.post("$basePath/tx") { req, _ ->
            val b = req.body()
            logger.debug("Request body: $b")
            val tx = toTransaction(req)
            if (!tx.tx.matches(Regex("[0-9a-f]{2,}"))) {
                throw UserMistake("Invalid tx format. Expected {\"tx\": <hexString>}")
            }
            model.postTransaction(tx)
        }

        http.get("$basePath/tx/:hashHex", "application/json", { req, _ ->
            checkHashHex(req)
            val transaction = toTransactionFromHash(req)
            transaction
        }, gson::toJson)

        http.get("$basePath/tx/:hashHex/confirmationProof", { req, _ ->
            val hashHex = checkHashHex(req)
            model.getConfirmationProof(TxHash(hashHex.hexStringToByteArray()))?:throw NotFoundError("")
        }, gson::toJson)

        http.get("$basePath/tx/:hashHex/status", { req, res ->
            checkHashHex(req)
            val txHash = toTxHash(req.params(":hashHex"))
            model.getStatus(txHash)
        }, gson::toJson)

        http.post("$basePath/query") { req, res ->
            model.query(Query(req.body())).json
        }

        http.awaitInitialization()
    }

    private fun checkHashHex(req: Request): String {
        val hashHex = req.params(":hashHex")
        if (hashHex.length != 64 && !hashHex.matches(Regex("[0-9a-f]{64}"))) {
            throw NotFoundError("Invalid hashHex. Expected 64 hex digits [0-9a-f]")
        }
        return hashHex
    }

    fun stop() {
        http.stop()
        // Ugly hack to workaround that there is no blocking stop.
        // Test cases won't work correctly without it
        Thread.sleep(100)

    }
}

data class Query(val json: String)
data class QueryResult(val json: String)

private class ConfirmationProofSerializer: JsonSerializer<ConfirmationProof> {
    override fun serialize(src: ConfirmationProof?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val proof = JsonObject()
        if (src == null) {
            return proof
        }
        proof.add("hash", JsonPrimitive(src.txRID.toHex()))
        proof.add("blockHeader", JsonPrimitive(src.header.toHex()))

        val sigs = JsonArray()
        (src.witness as MultiSigBlockWitness).getSignatures().forEach {
            val sig = JsonObject()
            sig.addProperty("pubKey", it.subjectID.toHex())
            sig.addProperty("signature", it.data.toHex())
            sigs.add(sig)
        }
        proof.add("signatures", sigs)
        val path = JsonArray()
        src.merklePath.forEach {
            val pathElement = JsonObject()
            pathElement.addProperty("side", if (it.side == Side.LEFT) 0 else 1)
            pathElement.addProperty("hash", it.hash.toHex())
            path.add(pathElement)
        }
        proof.add("merklePath", path)
        return proof
    }
}
private class TransactionDeserializer: JsonDeserializer<ApiTx> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ApiTx {
        if (json == null) {
            throw UserMistake("Can't parse tx")
        }
        val root = json as JsonObject
        return ApiTx(root.get("tx")!!.asString)
    }
}
private class ApiStatusSerializer: JsonSerializer<ApiStatus> {
    override fun serialize(src: ApiStatus?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val obj = JsonObject()
        obj.add("status", JsonPrimitive(src!!.status))
        return obj
    }
}

class ApiStatus(private val txStatus: TransactionStatus) {
    val status: String get() {
        return when (txStatus) {
            TransactionStatus.UNKNOWN -> "unknown"
            TransactionStatus.WAITING -> "waiting"
            TransactionStatus.CONFIRMED -> "confirmed"
            TransactionStatus.REJECTED -> "rejected"
        }
    }
}

class ApiTx(val tx: String) {
    val bytes: ByteArray get() {return tx.hexStringToByteArray()}
    init {
        require(tx.length > 1) {"Tx length must not be >= 2"}
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ApiTx) {
            return false
        }
        return other.bytes.contentEquals(bytes)
    }

    override fun hashCode(): Int {
        return tx.hashCode()
    }
}

class TxHash(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) {"Hash must be exactly 32 bytes"}
    }
    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) return true
        if (other !is TxHash) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(bytes)
    }
}
data class ErrorBody(val error: String = "")

interface Model {
    fun postTransaction(tx: ApiTx)
    fun getTransaction(txHash: TxHash): ApiTx?
    fun getConfirmationProof(txHash: TxHash): ConfirmationProof?
    fun getStatus(txHash: TxHash): ApiStatus
    fun query(query: Query): QueryResult
}

class NotFoundError(message: String): Exception(message)
