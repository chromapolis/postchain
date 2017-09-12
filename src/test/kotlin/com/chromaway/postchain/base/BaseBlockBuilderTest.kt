package com.chromaway.postchain.base

import com.chromaway.postchain.core.BlockBuilder
import com.chromaway.postchain.core.BlockStore
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.InitialBlockData
import com.chromaway.postchain.core.TransactionFactory
import com.chromaway.postchain.test.MockCryptoSystem
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Test
import java.sql.Connection

class BaseBlockBuilderTest {
    @Test
    /*
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

     */



    fun testBegin() {
//        val conn = mock<Connection> {}
//        val chainID = 18
//        val ctx = EContext(conn, chainID)
//        val initialBlockData = InitialBlockData(1L, ByteArray(32), 0L)
//        var txFactory = mock<TransactionFactory>()
//        val blockStore = mock<BlockStore> {
//            on { beginBlock(ctx) } doReturn(initialBlockData)
//            on { finalizeBlock() }
//        }
//
//        val SUT = BaseBlockBuilder(MockCryptoSystem(), ctx, blockStore, txFactory) as BlockBuilder
//        SUT.begin();
//
//        verify(blockStore).beginBlock(ctx)
//
//        SUT.finalize()
//
//        SUT.commit()

    }
}