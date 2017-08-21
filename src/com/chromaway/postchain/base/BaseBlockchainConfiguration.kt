package com.chromaway.postchain.base

import com.chromaway.postchain.core.*
import java.sql.Connection
import java.util.*

class BaseBlockchainConfiguration(override val chainID: Long, val properties: Properties) : BlockchainConfiguration {
    override val traits = setOf<String>()

    override fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader {
        return BaseBlockHeader(rawBlockHeader)
    }

    fun decodeWitness(rawWitness: ByteArray): BlockWitness {

    }

    fun getTransactionFactory(): TransactionFactory
    fun makeBlockBuilder(conn: Connection): BlockBuilder

}