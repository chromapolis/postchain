package net.postchain.modules.esplix;

import net.postchain.gtx.GTXValue
import net.postchain.modules.ft.OpEContext
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

open class BaseDBOps: EsplixDBOps {

    private val r = QueryRunner()
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val longHandler = ScalarHandler<Long>()
    private val nullableLongHandler = ScalarHandler<Long?>()
    private val unitHandler = ScalarHandler<Unit>()
    private val mapListHandler = MapListHandler()

    override fun getCertificates(ctx: OpEContext, id: ByteArray, authority: ByteArray?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getMessages(ctx: OpEContext, chainID: ByteArray, messageID: ByteArray, maxHits: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
