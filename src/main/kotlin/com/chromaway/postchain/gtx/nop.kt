package com.chromaway.postchain.gtx

import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.TxEContext

class GTX_NOP(u: Unit, opData: ExtOpData): GTXOperation(opData) {
    override fun apply(ctx: TxEContext): Boolean {
        return true
    }

    override fun isCorrect(): Boolean {
        return true;
    }
}


class GTX_NOP_Module: SimpleGTXModule<Unit>(Unit, mapOf(
        "nop" to ::GTX_NOP
), mapOf()) {

    override fun initializeDB(ctx: EContext) {}
}