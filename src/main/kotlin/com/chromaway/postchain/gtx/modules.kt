package com.chromaway.postchain.gtx

import com.chromaway.postchain.core.Transactor


interface GTXModule {
    fun makeTransactor(opData: ExtOpData): Transactor
    fun getOperations(): Set<String>
}

open class SimpleGTXModule<ConfT>(
        val conf: ConfT, val opmap: Map<String,
        (ConfT, ExtOpData)-> Transactor>
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
}

class CompositeGTXModule (val modules: Array<GTXModule>, allowOverrides: Boolean): GTXModule {

    val opmap: Map<String, GTXModule>
    val ops: Set<String>

    init {
        val map = mutableMapOf<String, GTXModule>()
        for (m in modules) {
            for (op in m.getOperations()) {
                if (!allowOverrides && op in map) throw Error("Duplicated operation")
                map[op] = m
            }
        }
        opmap = map.toMap()
        ops = opmap.keys
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

}