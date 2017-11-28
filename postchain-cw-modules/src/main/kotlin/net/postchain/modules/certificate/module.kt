package net.postchain.modules.certificate

import net.postchain.core.EContext
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.configuration2.Configuration

class CertificateModule(val config: CertificateConfig) : SimpleGTXModule<CertificateConfig>(
        config,
        mapOf(
                "certificate" to ::certificate_op
        ),
        mapOf(
                "get_certificates" to ::getCertificatesQ
        )
) {

    override fun initializeDB(ctx: EContext) {
        GTXSchemaManager.autoUpdateSQLSchema(
                ctx, 0, javaClass, "schema.sql", "chromaway.certificate"
        )
    }
}

class BaseCertificateModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Configuration): GTXModule {
        return CertificateModule(makeBaseCertificateConfig(config))
    }
}
