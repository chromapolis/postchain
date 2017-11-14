// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.test

import net.postchain.PostchainNode
import org.junit.After

open class EbftIntegrationTest : IntegrationTest() {
    protected var ebftNodes: Array<PostchainNode> = arrayOf()

    open fun createEbftNodes(count: Int) {
        ebftNodes = Array(count, { createEBFTNode(count, it) })
    }

    protected fun createEBFTNode(nodeCount: Int, myIndex: Int): PostchainNode {
        configOverrides.setProperty("messaging.privkey", privKeyHex(myIndex))
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val pn = PostchainNode()
        pn.start(createConfig(myIndex, nodeCount), myIndex)
        return pn;
    }

    @After
    fun tearDownEbftNodes() {
        ebftNodes.forEach {
            it.stop()
        }
        ebftNodes = arrayOf()
    }
}