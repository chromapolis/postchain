// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.test.modules.ft

import net.postchain.DataLayer
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.Transaction
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXDataBuilder
import net.postchain.gtx.GTXValue
import net.postchain.gtx.gtx
import net.postchain.gtx.make_gtx_gson
import net.postchain.modules.ft.AccountUtil
import net.postchain.modules.ft.BaseFTModuleFactory
import net.postchain.modules.ft.BasicAccount
import net.postchain.test.IntegrationTest
import org.junit.Assert
import org.junit.Test

val testBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3".hexStringToByteArray()
val myCS = SECP256K1CryptoSystem()

open class FTIntegrationTest : IntegrationTest() {
    val issuerPubKeys = arrayOf(pubKey(0), pubKey(1))
    val issuerPrivKeys = arrayOf(privKey(0), privKey(1))
    val accUtil = AccountUtil(net.postchain.test.modules.ft.testBlockchainRID, SECP256K1CryptoSystem())
    val issuerID = accUtil.makeAccountID(accUtil.issuerAccountDesc(issuerPubKeys[0]))
    val aliceIdx = 1
    val bobIdx = 2
    val alicePubKey = pubKey(aliceIdx)
    val alicePrivKey = privKey(aliceIdx)
    val aliceAccountDesc = BasicAccount.makeDescriptor(net.postchain.test.modules.ft.testBlockchainRID, alicePubKey)
    val aliceAccountID = accUtil.makeAccountID(aliceAccountDesc)
    val bobPubKey = pubKey(bobIdx)
    val bobPrivKey = privKey(bobIdx)
    val bobAccountDesc = BasicAccount.makeDescriptor(net.postchain.test.modules.ft.testBlockchainRID, bobPubKey)
    val bobAccountID = accUtil.makeAccountID(bobAccountDesc)
    val invalidAccountID = accUtil.makeAccountID("hello".toByteArray())

    fun makeRegisterTx(accountDescs: Array<ByteArray>, registrator: Int): ByteArray {
        val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(registrator)), myCS)
        for (desc in accountDescs) {
            b.addOperation("ft_register", arrayOf(gtx(desc)))
        }
        b.finish()
        b.sign(myCS.makeSigner(pubKey(registrator), privKey(registrator)))
        return b.serialize()
    }

    fun makeIssueTx(issuerIdx: Int, issuerID: ByteArray, recipientID: ByteArray, assetID: String, amout: Long): ByteArray {
        val b = GTXDataBuilder(testBlockchainRID, arrayOf(issuerPubKeys[issuerIdx]), myCS)
        b.addOperation("ft_issue", arrayOf(
                gtx(issuerID), gtx(assetID), gtx(amout), gtx(recipientID)
        ))
        b.finish()
        b.sign(myCS.makeSigner(issuerPubKeys[issuerIdx], issuerPrivKeys[issuerIdx]))
        return b.serialize()
    }

    fun makeTransferTx(senderIdx: Int,
                       senderID: ByteArray,
                       assetID: String,
                       amout: Long,
                       recipientID: ByteArray,
                       memo1: String? = null, memo2: String? = null): ByteArray {
        return makeTransferTx(pubKey(senderIdx), privKey(senderIdx), senderID, assetID, amout, recipientID, memo1, memo2)
    }

    fun makeTransferTx(senderPubKey: ByteArray,
                       senderPrivKey: ByteArray,
                       senderID: ByteArray,
                       assetID: String,
                       amout: Long,
                       recipientID: ByteArray,
                       memo1: String? = null, memo2: String? = null): ByteArray {
        val b = GTXDataBuilder(testBlockchainRID, arrayOf(senderPubKey), myCS)

        val args = mutableListOf<GTXValue>()
        args.add(gtx(gtx(gtx(senderID), gtx(assetID), gtx(amout)))) // inputs

        val output = mutableListOf<GTXValue>(gtx(recipientID), gtx(assetID), gtx(amout))
        if (memo2 != null) {
            output.add(gtx("memo" to gtx(memo2)))
        }
        args.add(gtx(gtx(*output.toTypedArray()))) // outputs
        if (memo1 != null) {
            args.add(gtx("memo" to gtx(memo1)))
        }
        b.addOperation("ft_transfer", args.toTypedArray())
        b.finish()
        b.sign(myCS.makeSigner(senderPubKey, senderPrivKey))
        return b.serialize()
    }

    fun enqueueTx(node: DataLayer, data: ByteArray): Transaction? {
        return enqueueTx(node, data, -1)
    }

}