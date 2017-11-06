// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.base

import org.postchain.core.BlockHeader
import org.postchain.core.InitialBlockData
import org.postchain.core.UserMistake
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BaseBlockHeaderTest {
    val prevBlockRID0 = ByteArray(32, {if (it==31) 99 else 0}) // This is incorrect. Should include 99 at the end
    val cryptoSystem = SECP256K1CryptoSystem()

    @Test
    fun makeHeaderWithCchainId0() {
        val prevBlockRid = ByteArray(32)
        // BlockchainId=0 should be allowed.
        val header0 = createHeader(2L, 0, prevBlockRid, 0)
        val decodedHeader0 = BaseBlockHeader(header0.rawData, cryptoSystem)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    @Test
    fun decodeMakeHeaderChainIdMax() {
        val prevBlockRid = ByteArray(24)+ByteArray(8, {if (it==0) 127 else -1})

        val header0 = createHeader(2L, Long.MAX_VALUE, prevBlockRid, 0)

        val decodedHeader0 = BaseBlockHeader(header0.rawData, cryptoSystem)
        assertArrayEquals(prevBlockRid, decodedHeader0.prevBlockRID)
    }

    private fun createHeader(blockIID: Long, chainId: Long, prevBlockRid: ByteArray, height: Long): BlockHeader {
        val rootHash = ByteArray(32, {0})
        val timestamp = 10000L + height
        val blockData = InitialBlockData(blockIID, chainId, prevBlockRid, height, timestamp)
        return BaseBlockHeader.make(SECP256K1CryptoSystem(), blockData, rootHash, timestamp)
    }
}