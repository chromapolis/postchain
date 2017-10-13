package com.chromaway.postchain.gtx

import com.chromaway.postchain.core.Transactor
import com.chromaway.postchain.core.TxEContext

abstract class GTXOperation(val data: ExtOpData): Transactor

class SimpleGTXOperation(data: ExtOpData,
                         val applyF: (TxEContext) -> Boolean,
                         val isCorrectF: () -> Boolean)
    : GTXOperation(data) {
    override fun apply(ctx: TxEContext): Boolean {
        return applyF(ctx)
    }

    override fun isCorrect(): Boolean {
        return isCorrectF()
    }
}

fun gtxOP(applyF: (TxEContext) -> Boolean): (Unit, ExtOpData) -> Transactor {
    return { unit, data ->
        SimpleGTXOperation(data, applyF, { true })
    }
}

fun gtxOP(applyF: (TxEContext) -> Boolean, isCorrectF: () -> Boolean): (Unit, ExtOpData) -> Transactor {
    return { unit, data ->
        SimpleGTXOperation(data, applyF, isCorrectF)
    }
}
