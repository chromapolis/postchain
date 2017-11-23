// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx

import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.core.UserMistake

class GTXOpMistake(message: String, opData: ExtOpData, argPos: Int? = null, cause: Exception? = null)
    : UserMistake( message +
        " (in ${opData.opName} #${opData.opIndex} " +
        (if (argPos != null) "(arg ${argPos})" else "") + ")",
        cause)

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
