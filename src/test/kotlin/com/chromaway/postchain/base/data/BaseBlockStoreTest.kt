package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.core.EContext
import com.chromaway.postchain.core.UserMistake
import org.easymock.EasyMock.expect
import org.easymock.EasyMock.mock
import org.easymock.EasyMock.replay
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection

class BaseBlockStoreTest {
    val cryptoSystem = SECP256K1CryptoSystem()
    val blockchainRID = cryptoSystem.digest("Test BlockchainRID".toByteArray())
    lateinit var sut: BaseBlockStore
    lateinit var db: DatabaseAccess
    lateinit var ctx: EContext
    @Before
    fun setup() {
        sut = BaseBlockStore()
        db = mock(DatabaseAccess::class.java)
        sut.db = db
        ctx = EContext(mock(Connection::class.java), 2L, 0)
    }

    @Test
    fun beginBlockReturnsBlockchainRIDOnFirstBlock() {
        expect(db.getLastBlockHeight(ctx)).andReturn(-1)
        expect(db.getBlockchainRID(ctx)).andReturn(blockchainRID)
        expect(db.insertBlock(ctx, 0)).andReturn(17)
        expect(db.getLastBlockTimestamp(ctx)).andReturn(1509606236)
        replay(db)
        val initialBlockData = sut.beginBlock(ctx)
        assertArrayEquals(blockchainRID, initialBlockData.prevBlockRID)
    }

    @Test
    fun beginBlockReturnsPrevBlockRIdOnSecondBlock() {
        val anotherRID = cryptoSystem.digest("A RID".toByteArray())
        expect(db.getLastBlockHeight(ctx)).andReturn(0)
        expect(db.getBlockRIDs(ctx, 0)).andReturn(listOf(anotherRID))
        expect(db.insertBlock(ctx, 1)).andReturn(17)
        expect(db.getLastBlockTimestamp(ctx)).andReturn(1509606236)
        replay(db)
        val initialBlockData = sut.beginBlock(ctx)
        assertArrayEquals(anotherRID, initialBlockData.prevBlockRID)
    }

    @Test(expected = UserMistake::class)
    fun beginBlockMissingBlockchainRIDOnFirstBlock() {
        expect(db.getLastBlockHeight(ctx)).andReturn(-1)
        expect(db.getBlockchainRID(ctx)).andReturn(null)
        expect(db.getLastBlockTimestamp(ctx)).andReturn(1509606236)
        replay(db)
        sut.beginBlock(ctx)
    }
}