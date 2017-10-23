package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.gtx.GTXValue
import com.chromaway.postchain.gtx.decodeGTXValue
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

open class BaseDBOps: FTDBOps {

    private val r = QueryRunner()
    private val byteArrayRes = ScalarHandler<ByteArray>()

    override fun update(ctx: OpEContext, accountID: ByteArray, assetID: String, amount: Long, allowNeg: Boolean) {
        r.update(ctx.txCtx.conn, "SELECT ft_update_raw(\$1, \$2, \$3, \$4, \$5, \$6, \$7)",
                ctx.txCtx.chainID,
                ctx.txCtx.txIID,
                ctx.opIndex,
                accountID, assetID, amount, allowNeg)
    }

    override fun getDescriptor(ctx: OpEContext, accountID: ByteArray): GTXValue {
        return decodeGTXValue(r.query(ctx.txCtx.conn, "SELECT ft_get_account_desc(\$1, \$2)",
                byteArrayRes, ctx.txCtx.chainID, accountID))
    }

    override fun registerAccount(ctx: OpEContext, accountID: ByteArray, accountType: Int, accountDesc: ByteArray) {
        r.update(ctx.txCtx.conn, "SELECT ft_register_account($1, $2, $3, $4, $5, $6)",
                ctx.txCtx.chainID,
                ctx.txCtx.txIID,
                ctx.opIndex,
                accountID,
                accountType,
                accountDesc)
    }
}