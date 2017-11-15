package net.postchain.modules.esplix

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation

class certificate_op (val config: EsplixConfig, data: ExtOpData): GTXOperation(data) {
    override fun isCorrect(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun apply(ctx: TxEContext): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
