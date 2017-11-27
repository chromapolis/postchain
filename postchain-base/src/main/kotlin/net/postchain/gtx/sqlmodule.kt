package net.postchain.gtx

import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.core.UserMistake
import org.apache.commons.configuration2.Configuration
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Scanner

fun decodeSQLTextArray(a: Any): Array<String> {
    val arr = a as java.sql.Array
    return (arr.array as Array<String>)
}

class SQLOpArg(val name: String,
               val type: GTXValueType,
               val isSigner: Boolean,
               val isNullable: Boolean)

class SQLOpDesc(val name: String, val query: String, val args: Array<SQLOpArg>)

fun makeSQLOpDesc(opName: String, argNames: Array<String>, argTypes: Array<String>): SQLOpDesc {
    if (argNames.size != argTypes.size)
        throw UserMistake("Cannot define SQL op ${opName}: wrong parameter list")
    if (argTypes[0] != "gtx_ctx")
        throw UserMistake("Cannot define SQL op ${opName}: gtx_ctx must be the first parameter")
    val args = mutableListOf<SQLOpArg>()
    for (i in 1 until argNames.size) {
        val gtxType = when (argTypes[i]) {
            "bigint" -> Pair(GTXValueType.INTEGER, false)
            "int" -> Pair(GTXValueType.INTEGER, false)
            "text" -> Pair(GTXValueType.STRING, false)
            "gtx_signer" -> Pair(GTXValueType.BYTEARRAY, true)
            "bytea" -> Pair(GTXValueType.BYTEARRAY, false)
            else -> throw UserMistake("Unsupported argument type ${argTypes[i]} in ${opName}")
        }
        // only signer is not nullable
        args.add(SQLOpArg(argNames[i], gtxType.first, gtxType.second, !gtxType.second))
    }
    val query = "SELECT ${opName} (${Array(argTypes.size, { "?" }).joinToString(", ")})"
    return SQLOpDesc(opName, query, args.toTypedArray())
}


class SQLGTXOperation(val opDesc: SQLOpDesc, opData: ExtOpData):
        GTXOperation(opData)
{
    lateinit var args: Array<Any?>
    override fun isCorrect(): Boolean {
        if (opDesc.args.size != data.args.size)
            throw GTXOpMistake("Wrong number of arguments", data)

        val myArgs = mutableListOf<Any?>()
        for (i in 0 until opDesc.args.size) {
            val spec = opDesc.args[i]
            val arg = data.args[i]
            if (arg.type != spec.type) {
                if (! (arg.isNull() && spec.isNullable) ) {
                    throw GTXOpMistake("Wrong argument type", data, i)
                }
            }
            if (spec.isSigner) {
                val signer = arg.asByteArray()
                if (!data.signers.any { it.contentEquals(signer) })
                    throw GTXOpMistake("Signer is not present", data, i)
            }

            myArgs.add(arg.asPrimitive())
        }
        args = myArgs.toTypedArray()

        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        val s = ctx.conn.createStruct("gtx_ctx",
                arrayOf(ctx.chainID, ctx.txIID, data.opIndex)
        )
        val r = QueryRunner()
        return r.query(ctx.conn, opDesc.query, ScalarHandler<Boolean>(),
                s, *args)
    }
}

class SQLGTXModule(private val moduleFiles: Array<String>): GTXModule
{
    lateinit var ops: Map<String, SQLOpDesc>
    lateinit var queries: Map<String, SQLOpDesc>

    override fun getOperations(): Set<String> {
        return ops.keys
    }

    override fun getQueries(): Set<String> {
        return queries.keys
    }

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in ops) {
            return SQLGTXOperation(ops[opData.opName]!!, opData)
        } else {
            throw UserMistake("Operation not found")
        }
    }

    override fun query(ctxt: EContext, name: String, args: GTXValue): GTXValue {
        TODO("NOT IMPLEMENTED")
    }

    private fun populateOps(ctx: EContext) {
        val r = QueryRunner()
        val oplist = r.query(ctx.conn, "SELECT * FROM gtx_sqlm_get_functions()", MapListHandler())
        val opList = mutableListOf<Pair<String, SQLOpDesc>>()
        for (op in oplist) {
            val name = op["name"] as String
            val opDesc = makeSQLOpDesc(name,
                    decodeSQLTextArray(op["argnames"]!!),
                    decodeSQLTextArray(op["argtypes"]!!))
            opList.add(name to opDesc)
        }
        ops = mapOf(*opList.toTypedArray())
    }

    override fun initializeDB(ctx: EContext) {
        GTXSchemaManager.autoUpdateSQLSchema(ctx, 0, javaClass, "sqlgtx.sql")
        for (fileName in moduleFiles) {
            val moduleName = "SQLM_" + fileName
            val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
            if (version == null) {
                try {
                    val sql = Scanner(javaClass.getResourceAsStream(fileName), "UTF-8").useDelimiter("\\A").next()
                    ctx.conn.createStatement().use {
                        it.execute(sql)
                    }
                    GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
                } catch (e: Exception) {
                    throw UserMistake("Failed to load SQL GTX module ${fileName}", e)
                }
            }
        }
        populateOps(ctx)
    }
}

class SQLGTXModuleFactory: GTXModuleFactory {
    override fun makeModule(config: Configuration): GTXModule {
        return SQLGTXModule(config.getStringArray("gtx.sqlmodules"))
    }
}