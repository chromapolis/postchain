package com.chromaway.postchain.configurations

import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.Signer
import com.chromaway.postchain.base.data.BaseBlockBuilder
import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.BlockchainConfiguration
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.core.TxEContext
import com.chromaway.postchain.core.UserMistake
import com.chromaway.postchain.gtx.ExtOpData
import com.chromaway.postchain.gtx.GTXBlockchainConfiguration
import com.chromaway.postchain.gtx.GTXBlockchainConfigurationFactory
import com.chromaway.postchain.gtx.GTXModule
import com.chromaway.postchain.gtx.GTXOperation
import com.chromaway.postchain.gtx.GTXSchemaManager
import com.chromaway.postchain.gtx.NullGTXValue
import com.chromaway.postchain.gtx.SimpleGTXModule
import com.chromaway.postchain.gtx.gtx
import org.apache.commons.configuration2.Configuration
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler


private class SingleNodeBlockBuilder(cryptoSystem: CryptoSystem, eContext: EContext,
                                     store: BlockStore, txFactory: TransactionFactory,
                                     subjects: Array<ByteArray>, blockSigner: Signer)
    : BaseBlockBuilder(cryptoSystem, eContext, store, txFactory, subjects, blockSigner) {
    override fun getRequiredSigCount(): Int {return 1}
}

private class SingleNodeGtxBlockchainConfiguration(chainID: Long, config: Configuration, module: GTXModule): GTXBlockchainConfiguration(chainID, config, module) {
    override fun createBlockBuilderInstance(cryptoSystem: CryptoSystem, ctx: EContext,
                                            blockStore: BlockStore, transactionFactory: TransactionFactory,
                                            signers: Array<ByteArray>, blockSigner: Signer): BlockBuilder {
        return SingleNodeBlockBuilder(cryptoSystem, ctx, blockStore,
                getTransactionFactory(), signers, blockSigner)
    }
}

class SingleNodeGtxBlockchainConfigurationFactory(): GTXBlockchainConfigurationFactory() {
    override fun makeBlockchainConfiguration(chainID: Long, config: Configuration): BlockchainConfiguration {
        return SingleNodeGtxBlockchainConfiguration(chainID, config, createGtxModule(config.subset("gtx")))
    }
}

private val r = QueryRunner()
private val nullableStringReader = ScalarHandler<String?>()

class GTXTestOp(u: Unit, opdata: ExtOpData): GTXOperation(opdata) {
    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        data.args[1].asString()
        return data.args[0].asInteger() == 1L
    }

    override fun apply(ctx: TxEContext): Boolean {
        r.update(ctx.conn,
                """INSERT INTO gtx_test_value(tx_iid, value) VALUES (?, ?)""",
                ctx.txIID, data.args[1].asString())
        return true
    }
}

class GTXTestModule: SimpleGTXModule<Unit>(Unit,
        mapOf("gtx_test" to ::GTXTestOp),
        mapOf("gtx_test_get_value" to { u, ctxt, args ->
            val txRID = args.get("txRID")
            if (txRID == null) {
                throw UserMistake("No txRID property supplied")
            }

            val value = r.query(ctxt.conn,
                    """SELECT value FROM gtx_test_value
                    INNER JOIN transactions ON gtx_test_value.tx_iid = transactions.tx_iid
                    WHERE transactions.tx_rid = ?""",
                    nullableStringReader, txRID.asByteArray(true))
            if (value == null)
                NullGTXValue()
            else
                gtx(value)
        })
) {
    override fun initializeDB(ctx: EContext) {
        val moduleName = this::class.qualifiedName!!
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            r.update(ctx.conn, """
CREATE TABLE gtx_test_value(tx_iid BIGINT PRIMARY KEY, value TEXT NOT NULL)
            """)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}