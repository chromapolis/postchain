package com.chromaway.postchain.gtx

import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.Transactor
import com.chromaway.postchain.core.UserMistake
import com.google.gson.JsonObject

interface GTXModule {
    fun makeTransactor(opData: ExtOpData): Transactor
    fun getOperations(): Set<String>
    fun getQueries(): Set<String>
    fun query(ctxt: EContext, name: String, args: GTXValue): GTXValue
    fun initializeDB(ctx: EContext)
}

abstract class SimpleGTXModule<ConfT>(
        val conf: ConfT,
        val opmap: Map<String, (ConfT, ExtOpData)-> Transactor>,
        val querymap: Map<String, (ConfT, EContext, GTXValue)->GTXValue>
): GTXModule {

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in opmap) {
            return opmap[opData.opName]!!(conf, opData)
        } else {
            throw Error("Unknown operation")
        }
    }

    override fun getOperations(): Set<String> {
        return opmap.keys
    }

    override fun getQueries(): Set<String> {
        return querymap.keys
    }

    override fun query(ctxt: EContext, name: String, args: GTXValue): GTXValue {
        if (name in querymap) {
            return querymap[name]!!(conf, ctxt, args)
        } else throw UserMistake("Unkown query")
    }
}

class CompositeGTXModule (val modules: Array<GTXModule>, allowOverrides: Boolean): GTXModule {

    val opmap: Map<String, GTXModule>
    val qmap: Map<String, GTXModule>
    val ops: Set<String>
    val _queries: Set<String>

    init {
        val _opmap = mutableMapOf<String, GTXModule>()
        val _qmap = mutableMapOf<String, GTXModule>()
        for (m in modules) {
            for (op in m.getOperations()) {
                if (!allowOverrides && op in _opmap) throw Error("Duplicated operation")
                _opmap[op] = m
            }
            for (q in m.getQueries()) {
                if (!allowOverrides && q in _qmap) throw Error("Duplicated operation")
                _qmap[q] = m
            }
        }
        opmap = _opmap.toMap()
        qmap = _qmap.toMap()
        ops = opmap.keys
        _queries = qmap.keys
    }

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in opmap) {
            return opmap[opData.opName]!!.makeTransactor(opData)
        } else {
            throw Error("Unknown operation")
        }
    }

    override fun getOperations(): Set<String> {
        return ops
    }

    override fun getQueries(): Set<String> {
        return _queries
    }

    override fun query(ctxt: EContext, name: String, args: GTXValue): GTXValue {
        if (name in qmap) {
            return qmap[name]!!.query(ctxt, name, args)
        } else {
            throw Error("Unknown query")
        }
    }

    override fun initializeDB(ctx: EContext) {
        for (module in modules) {
            module.initializeDB(ctx)
        }
    }

}