// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx

import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.UserMistake
import org.apache.commons.configuration2.Configuration

interface GTXModule {
    fun makeTransactor(opData: ExtOpData): Transactor
    fun getOperations(): Set<String>
    fun getQueries(): Set<String>
    fun query(ctxt: EContext, name: String, args: GTXValue): GTXValue
    fun initializeDB(ctx: EContext)
}

interface GTXModuleFactory {
    fun makeModule(config: GTXValue, blockchainRID: ByteArray): GTXModule
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
            throw UserMistake("Unknown operation")
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

class CompositeGTXModule (val modules: Array<GTXModule>, val allowOverrides: Boolean): GTXModule {

    lateinit var opmap: Map<String, GTXModule>
    lateinit var qmap: Map<String, GTXModule>
    lateinit var ops: Set<String>
    lateinit var _queries: Set<String>

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in opmap) {
            return opmap[opData.opName]!!.makeTransactor(opData)
        } else {
            throw UserMistake("Unknown operation")
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
            throw UserMistake("Unknown query")
        }
    }

    override fun initializeDB(ctx: EContext) {
        for (module in modules) {
            module.initializeDB(ctx)
        }
        val _opmap = mutableMapOf<String, GTXModule>()
        val _qmap = mutableMapOf<String, GTXModule>()
        for (m in modules) {
            for (op in m.getOperations()) {
                if (!allowOverrides && op in _opmap) throw UserMistake("Duplicated operation")
                _opmap[op] = m
            }
            for (q in m.getQueries()) {
                if (!allowOverrides && q in _qmap) throw UserMistake("Duplicated operation")
                _qmap[q] = m
            }
        }
        opmap = _opmap.toMap()
        qmap = _qmap.toMap()
        ops = opmap.keys
        _queries = qmap.keys
    }

}