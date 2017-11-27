package net.postchain.modules.esplix

import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

//class EsplixCreateChainData(val chainID: ByteArray, val nonce: ByteArray, val callIndex: Int, val payload: ByteArray)

class create_chain_op (val config: EsplixConfig, data: ExtOpData): GTXOperation(data) {
    private val r = QueryRunner()
    private val longHandler = ScalarHandler<Long>()
    val chainID = data.args[0].asByteArray()
    val nonce = data.args[1].asByteArray()
    val payload = data.args[2].asByteArray()

    override fun isCorrect(): Boolean {
        if (data.args.size != 3)
            return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        r.query(ctx.conn, "SELECT mcs_r2_createChain (?, ?, ?, ?, ?)", longHandler,
                nonce, chainID, ctx.txIID, data.opIndex, payload)
        return true
    }
}
