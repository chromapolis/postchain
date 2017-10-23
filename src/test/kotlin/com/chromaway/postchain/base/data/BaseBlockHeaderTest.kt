package com.chromaway.postchain.base.data

import com.chromaway.postchain.base.BaseBlockHeader
import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.core.BlockHeader
import com.chromaway.postchain.core.InitialBlockData
import com.chromaway.postchain.core.UserMistake
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BaseBlockHeaderTest {
    val prevBlockRID0 = ByteArray(32, {if (it==31) 99 else 0}) // This is incorrect. Should include 99 at the end
    val cryptoSystem = SECP256K1CryptoSystem()

    @Test(expected = UserMistake::class)
    fun makeHeaderWithWrongBlockchainIdFails() {
        val badPrevBlockRID = ByteArray(32, { if (it == 31) 98 else 0 })
        createHeader(2L, 99, badPrevBlockRID, 0)
    }

    @Test
    fun makeHeaderWithBlockchainId0() {
        val prevBlockRid = ByteArray(32)
        // BlockchainId=0 should be allowed.
        val header0 = createHeader(2L, 0, prevBlockRid, 0)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, cryptoSystem)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    @Test
    fun makeAndDecodeHeaderWithCorrectBlockchainId() {
        val header0 = createHeader(2L, 99, prevBlockRID0, 0)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, cryptoSystem)
        assertArrayEquals(prevBlockRID0, decodedHeader0.prevBlockRID)
    }

    @Test
    fun decodeMakeHeaderAtHeight1() {
        val header0 = createHeader(2L, 99, prevBlockRID0, 0)

        val prevBlockRID1 = header0.blockRID
        val header1 = createHeader(3L, 99, prevBlockRID1, 1)

        val decodedHeader1 = BaseBlockHeader(header1.rawData, cryptoSystem)
        assertArrayEquals(prevBlockRID1, decodedHeader1.prevBlockRID)
    }


    @Test
    fun decodeMakeHeaderChainIdMax() {
        val prevBlockRid = ByteArray(28)+ByteArray(4, {if (it==0) 127 else -1})

        val header0 = createHeader(2L, Integer.MAX_VALUE, prevBlockRid, 0)

        val decodedHeader0 = BaseBlockHeader(header0.rawData, cryptoSystem)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    private fun createHeader(blockIID: Long, chainId: Int, prevBlockRid: ByteArray, height: Long): BlockHeader {
        val blockData = InitialBlockData(blockIID, chainId, prevBlockRid, height)
        val rootHash = ByteArray(32, {0})
        val timestamp = 10000L + height
        return BaseBlockHeader.make(SECP256K1CryptoSystem(), blockData, rootHash, timestamp)
    }
}