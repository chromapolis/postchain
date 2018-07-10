package net.postchain.modules.certificate

import net.postchain.api.rest.TxRID
import net.postchain.core.EContext
import net.postchain.gtx.*
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
    override fun makeModule(data: GTXValue, blockchianRID: ByteArray): GTXModule {
        return CertificateModule(makeBaseCertificateConfig(data, blockchianRID))
    }
}
