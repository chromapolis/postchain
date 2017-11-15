package net.postchain.modules.esplix

import net.postchain.core.EContext
import net.postchain.gtx.GTXNull
import net.postchain.gtx.GTXValue
import org.apache.commons.dbutils.QueryRunner

fun getNonceQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    return GTXNull
}
fun getCertificatesQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    return GTXNull
}
fun getMessagesQ(config: EsplixConfig, ctx: EContext, args: GTXValue): GTXValue {
    return GTXNull
}