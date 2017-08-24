package com.chromaway.postchain.base

import com.chromaway.postchain.core.*
import java.sql.Connection
import java.util.*

class BaseBlockchainConfiguration(override val chainID: Long, val properties: Properties) : BlockchainConfiguration {
    override val traits = setOf<String>()

    override fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader {
        TODO("Not implemented yet")
        //return BaseBlockHeader(rawBlockHeader)
    }

    override fun decodeWitness(rawWitness: ByteArray): BlockWitness {
        TODO("Not implemented yet")
    }

    override fun getTransactionFactory(): TransactionFactory {
        return BaseTransactionFactory();
    }

    override fun makeBlockBuilder(ctx: EContext): BlockBuilder {
        TODO("Not implemented yet")
//        return BaseBlockBuilder(SECP256K1CryptoSystem(), EContext(), com.chromaway.postchain.base.BaseBlockStore(),
    }

}

