package net.postchain.test.modules.esplix

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXDataBuilder
import net.postchain.gtx.gtx
import net.postchain.modules.esplix.BaseEsplixModuleFactory
import net.postchain.test.IntegrationTest
import org.junit.Assert
import org.junit.Test

val testBlockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3".hexStringToByteArray()
val myCS = SECP256K1CryptoSystem()

class EsplixIntegrationTest : IntegrationTest() {

    fun makeCreateChainTx(chainID: ByteArray, creator: Int, nonce: ByteArray, payload: ByteArray): ByteArray {
        val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(creator)), myCS)
        b.addOperation("esplix_create_chain",arrayOf(
                gtx(chainID),
                gtx(nonce),
                gtx(payload)))
        b.finish()
        b.sign(myCS.makeSigner(pubKey(creator), privKey(creator)))
        return b.serialize()

    }

    fun makePostMessage(poster: Int, prevID: ByteArray, payload: ByteArray): ByteArray {
        val b = GTXDataBuilder(testBlockchainRID, arrayOf(pubKey(poster)), myCS)
        b.addOperation("esplix_post_message",arrayOf(
                gtx(prevID),
                gtx(payload)))
        b.finish()
        b.sign(myCS.makeSigner(pubKey(poster), privKey(poster)))
        return b.serialize()
    }

    @Test
    fun testEsplix() {
        configOverrides.setProperty("blockchain.1.configurationfactory",
                GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules",
                BaseEsplixModuleFactory::class.qualifiedName)

        val creator = 0
        val user = 1
        val node = createDataLayer(0)
        var currentBlockHeight = -1L

        fun buildBlockAndCommitWithTx(data: ByteArray, fail: Boolean = false) {
            currentBlockHeight += 1
            try {
                val tx = node.blockchainConfiguration.getTransactionFactory().decodeTransaction(data)
                node.txQueue.enqueue(tx)
                buildBlockAndCommit(node.engine)
                Assert.assertEquals(currentBlockHeight,getBestHeight(node))
                val txSz = getTxRidsAtHeight(node, currentBlockHeight).size
                if (fail)
                    Assert.assertEquals(0, txSz)
                else
                    Assert.assertEquals(1, txSz)
            } catch (e: Error) {
                println(e)
            }
        }

        val chainID = myCS.digest("testchain".toByteArray())
        val payload = ByteArray(50,{1})
        val nonce = node.blockQueries.query("""{"type"="esplix_get_nonce"}"""
        ).get().removeSurrounding("\"").hexStringToByteArray()

        val createChainTx = makeCreateChainTx(
                chainID,
                creator,
                nonce,
                payload)
        buildBlockAndCommitWithTx(createChainTx)

        val messageID = chainID
        val postmessageTx = makePostMessage(
                creator,
                messageID,
                payload)
        buildBlockAndCommitWithTx(postmessageTx)

        val messageID2 = myCS.digest(messageID+payload+pubKey(creator))
        val postMessageTx2 = makePostMessage(
                user,
                messageID2,
                payload
        )
        buildBlockAndCommitWithTx(postMessageTx2)

        val messageID3 = myCS.digest(messageID2+payload+pubKey(user))
        val postMessageTx3 = makePostMessage(
                creator,
                messageID3,
                payload
        )
        buildBlockAndCommitWithTx(postMessageTx3)

        //Deliberately try to post a message that has the incorrect prevID
        val postMessageTx4 = makePostMessage(
                creator,
                ByteArray(32,{0}),
                payload
        )
        buildBlockAndCommitWithTx(postMessageTx4, true)
    }
}