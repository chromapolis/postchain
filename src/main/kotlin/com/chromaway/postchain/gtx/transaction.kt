package com.chromaway.postchain.gtx

import com.chromaway.postchain.base.BaseBlockQueries
import com.chromaway.postchain.base.CryptoSystem
import com.chromaway.postchain.base.Storage
import com.chromaway.postchain.base.data.BaseBlockchainConfiguration
import com.chromaway.postchain.base.hexStringToByteArray
import com.chromaway.postchain.base.secp256k1_derivePubKey
import com.chromaway.postchain.core.*
import nl.komponents.kovenant.Promise
import org.apache.commons.configuration2.Configuration

class GTXTransaction (val _rawData: ByteArray, module: GTXModule, val cs: CryptoSystem): Transaction {

    val myRID: ByteArray = cs.digest(_rawData)
    val data: GTXData
    val signers: Array<ByteArray>
    val signatures: Array<ByteArray>
    val ops: Array<Transactor>
    var isChecked: Boolean = false
    val digestForSigning: ByteArray

    init {
        data = decodeGTXData(_rawData)

        digestForSigning = data.getDigestForSigning(cs)

        signers = data.signers
        signatures = data.signatures

        ops = data.getExtOpData().map({ module.makeTransactor(it) }).toTypedArray()
    }

    override fun isCorrect(): Boolean {
        if (isChecked) return true

        if (signatures.size != signers.size) return false

        for ((idx, signer) in signers.withIndex()) {
            val signature = signatures[idx]
            if (!cs.verifyDigest(digestForSigning, Signature(signer, signature))) {
                return false
            }
        }

        for (op in ops) {
            if (!op.isCorrect()) return false
        }

        isChecked = true
        return true
    }

    override fun getRawData(): ByteArray {
        return _rawData
    }

    override fun getRID(): ByteArray {
         return myRID
    }

    override fun apply(ctx: TxEContext): Boolean {
        if (!isCorrect()) throw UserMistake("Transaction is not correct")
        for (op in ops) {
            if (!op.apply(ctx))
                throw UserMistake("Operation failed")
        }
        return true
    }

}

class GTXTransactionFactory(val blockchainID: ByteArray, val module: GTXModule, val cs: CryptoSystem): TransactionFactory {
    override fun decodeTransaction(data: ByteArray): Transaction {
        val tx = GTXTransaction(data, module, cs)
        if (tx.data.blockchainID.contentEquals(blockchainID))
            return tx
        else
            throw UserMistake("Transaction has wrong blockchainID")
    }
}

open class GTXBlockchainConfiguration(chainID: Long, config: Configuration, val module: GTXModule)
    :BaseBlockchainConfiguration(chainID, config)
{
    val txFactory = GTXTransactionFactory(EMPTY_SIGNATURE, module, cryptoSystem)

    override fun getTransactionFactory(): TransactionFactory {
        return txFactory
    }

    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        GTXSchemaManager.initializeDB(ctx)
        module.initializeDB(ctx)
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries {
        val blockSigningPrivateKey = config.getString("blocksigningprivkey").hexStringToByteArray()
        val blockSigningPublicKey = secp256k1_derivePubKey(blockSigningPrivateKey)

        return object: BaseBlockQueries(this@GTXBlockchainConfiguration, storage, blockStore,
                chainID, blockSigningPublicKey) {
            private val gson = make_gtx_gson()

            override fun query(query: String): Promise<String, Exception> {
                val gtxQuery = gson.fromJson<GTXValue>(query, GTXValue::class.java)
                return runOp {
                    val type = gtxQuery.asDict().get("type")?: throw UserMistake("Missing query type")
                    val queryResult = module.query(it, type.asString(), gtxQuery)
                    gtxToJSON(queryResult, gson)
                }
            }
        }
    }
}

open class GTXBlockchainConfigurationFactory(): BlockchainConfigurationFactory {
    override fun makeBlockchainConfiguration(chainID: Long, config: Configuration): BlockchainConfiguration {
        return GTXBlockchainConfiguration(chainID, config, createGtxModule(config.subset("gtx")))
    }

    open fun createGtxModule(config: Configuration): GTXModule {
        val list = config.getList(String::class.java, "modules")
        if (list == null || list.size == 0) {
            throw UserMistake("Missing GTX module in config. expected property 'blockchain.<chainId>.gtx.modules'")
        }
        return if (list.size == 1) {
            val moduleClass = Class.forName(list[0])
            moduleClass.newInstance() as GTXModule
        } else {
            val moduleList = list.map {
                val moduleClass = Class.forName(list[0])
                moduleClass.newInstance() as GTXModule
            }
            val allowOverrides = config.getBoolean("allowoverrides", false)
            CompositeGTXModule(moduleList.toTypedArray(), allowOverrides)
        }
    }
}