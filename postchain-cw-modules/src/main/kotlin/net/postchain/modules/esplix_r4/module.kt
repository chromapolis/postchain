package net.postchain.modules.esplix_r4

import net.postchain.core.EContext
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.configuration2.Configuration

class EsplixModule(val config: EsplixConfig) : SimpleGTXModule<EsplixConfig>(
        config,
        mapOf(
                "R4createChain" to ::create_chain_op,
                "R4postMessage" to ::post_message_op
        ),
        mapOf(
                "R4getMessages" to ::getMessagesQ,
                "R4getTxRID" to ::getTxRIDQ
        )
) {

    override fun initializeDB(ctx: EContext) {
        GTXSchemaManager.autoUpdateSQLSchema(
                ctx, 0, javaClass, "schema.sql", "chromaway.esplix_r4"
        )
    }
}

class BaseEsplixModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Configuration): GTXModule {
        return EsplixModule(makeBaseEsplixConfig(config))
    }
}
