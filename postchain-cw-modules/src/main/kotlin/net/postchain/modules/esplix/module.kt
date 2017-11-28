package net.postchain.modules.esplix

import net.postchain.core.EContext
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.configuration2.Configuration

class EsplixModule(val config: EsplixConfig) : SimpleGTXModule<EsplixConfig>(
        config,
        mapOf(
                "esplix_create_chain" to ::create_chain_op,
                "esplix_post_message" to ::post_message_op
        ),
        mapOf(
                "esplix_get_nonce" to ::getNonceQ,
                "esplix_get_messages" to ::getMessagesQ
        )
) {

    override fun initializeDB(ctx: EContext) {
        GTXSchemaManager.autoUpdateSQLSchema(
                ctx, 0, javaClass, "schema.sql", "chromaway.esplix"
        )
    }
}

class BaseEsplixModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Configuration): GTXModule {
        return EsplixModule(makeBaseEsplixConfig(config))
    }
}
