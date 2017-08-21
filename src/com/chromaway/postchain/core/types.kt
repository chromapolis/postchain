package com.chromaway.postchain.core

import java.sql.Connection;
import java.util.*

// TODO: can we generalize conn? We can make it an Object, but then we have to do typecast everywhere...
open class EContext(val conn: Connection, val chainID: Long)

class BlockEContext(conn: Connection, chainID: Long, val blockIID: Long)
    : EContext(conn, chainID)

interface BlockHeader {
    val prevBlockRID: ByteArray;
    val rawData: ByteArray;
    val blockRID: ByteArray; // it's not a part of header but derived from it
}

open class BlockData(val header: BlockHeader, val transactions: Array<ByteArray>)

// Witness is a generalization over signatures
// Block-level witness is something which proves that block is valid and properly authorized

interface BlockWitness {
    val blockRID: ByteArray
    fun getRawData(): ByteArray
}

open class BlockDataWithWitness(header: BlockHeader, transactions: Array<ByteArray>, val witness: BlockWitness?)
    : BlockData(header, transactions)

// id is something which identifies subject which produces the
// signature, e.g. pubkey or hash of pubkey
class Signature(val subjectID: ByteArray, val data: ByteArray)

interface MultiSigBlockWitness : BlockWitness {
    fun getSignatures(): Array<Signature>;
}

interface BlockWitnessBuilder {
    fun isComplete(): Boolean
    fun getWitness(): BlockWitness // throws when not complete
}

interface MultiSigBlockWitnessBuilder : BlockWitnessBuilder {
    fun getMySignature(): Signature;
    fun applySignature(s: Signature);
}

// Transactor is an individual operation which can be applied to the database
// Transaction might consist of one or more operations
// Transaction should be serializable, but transactor doesn't need to have a serialized
// representation as we only care about storing of the whole Transaction

interface Transactor {
    fun isCorrect(): Boolean
    fun apply(ctx: BlockEContext): Boolean
}

interface Transaction : Transactor {
    fun getRawData(): ByteArray
}

// BlockchainConfiguration is a stateless objects which describes
// an individual blockchain instance within Postchain system

interface BlockchainConfiguration {
    val chainID: Long
    val traits: Set<String>

    fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader
    fun decodeWitness(rawWitness: ByteArray): BlockWitness
    fun getTransactionFactory(): TransactionFactory
    fun makeBlockBuilder(conn: Connection): BlockBuilder
}

interface BlockchainConfigurationFactory {
    fun makeBlockchainConfiguration(chainID: Long, properties: Properties)
}

interface TransactionFactory {
    fun decodeTransaction(data: ByteArray): Transaction
}

interface BlockBuilder {
    fun begin()
    fun appendTransaction(tx: Transaction)
    fun appendTransaction(txData: ByteArray)
    fun finalize()
    fun finalizeAndValidate(bh: BlockHeader)
    fun getBlockData(): BlockData
    fun getBlockWitnessBuilder(): BlockWitnessBuilder?;
    fun commit(w: BlockWitness?)
}

class InitialBlockData(val blockIID: Long, val prevBlockRID: ByteArray, val height: Long)

interface BlockStore {
    fun beginBlock(ctx: EContext): InitialBlockData
    fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader)
    fun commitBlock(bctx: BlockEContext, w: BlockWitness?)
    fun getBlockHeight(blockRID: ByteArray): Long? // returns null if not found
    fun getBlockRID(height: Long): ByteArray? // returns null if height is out of range
    fun getLastBlockHeight(): Long // height of the last block, first block has height 0
    fun getBlockData(height: Long): BlockData
    fun getWitnessData(height: Long): ByteArray
}