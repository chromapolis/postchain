// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain

import net.postchain.base.*
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.ebft.BlockchainInstanceModel
import net.postchain.ebft.EBFTBlockchainInstance
import net.postchain.ebft.makeConnManager
import net.postchain.ebft.message.EbftMessage
import net.postchain.network.PeerConnectionManager
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import kotlin.system.exitProcess

/**
 * A postchain node
 *
 * @property updateLoop the main thread
 * @property stopMe boolean, which when set, will stop the thread [updateLoop]
 * @property restApi contains information on the rest API, such as network parameters and available queries
 * @property blockchainConfiguration stateless object which describes an individual blockchain instance
 * @property storage handles back-end database connection and storage
 * @property blockQueries a collection of methods for various blockchain related queries
 * @property peerInfos information relating to our peers
 * @property statusManager manages the status of the consensus protocol
 * @property commManager peer communication manager
 *
 * @property txQueue transaction queue for transactions received from peers. Will not be forwarded to other peers
 * @property txForwardingQueue transaction queue for transactions added locally via the REST API
 * @property blockStrategy strategy configurations for how to create new blocks
 * @property engine blockchain engine used for building and adding new blocks
 * @property blockDatabase wrapper class for the [engine] and [blockQueries], starting new threads when running
 * operations and handling exceptions
 * @property blockManager manages intents and acts as a wrapper for [blockDatabase] and [statusManager]
 * @property model
 * @property syncManager
 */
class PostchainNode {

    lateinit var connManager: PeerConnectionManager<EbftMessage>
    lateinit var blockchainInstance: EBFTBlockchainInstance

    fun stop() {
        connManager.stop()
        blockchainInstance.stop()
    }

    fun getModel(): BlockchainInstanceModel {
        return blockchainInstance.getModel()
    }

    /**
     * Start the postchain node by setting up everything and finally starting the updateLoop thread
     *
     * @param config configuration settings for the node
     * @param nodeIndex the index of the node
     */
    fun start(config: Configuration, nodeIndex: Int) {
        // This will eventually become a list of chain ids.
        // But for now it's just a single integer.
        val chainId = config.getLong("activechainids")
        val peerInfos = createPeerInfos(config)
        val privKey = config.getString("messaging.privkey").hexStringToByteArray()
        val blockchainRID = config.getString("blockchain.${chainId}.blockchainrid").hexStringToByteArray() // TODO
        val commConfiguration = BasePeerCommConfiguration(peerInfos, blockchainRID, nodeIndex, SECP256K1CryptoSystem(), privKey)

        connManager = makeConnManager(commConfiguration)
        blockchainInstance = EBFTBlockchainInstance(chainId,
                config,
                nodeIndex,
                commConfiguration,
                connManager
                )
    }

    /**
     * Retrieve peer information from config, including networking info and public keys
     *
     * @param config configuration
     * @return peer information
     */
    fun createPeerInfos(config: Configuration): Array<PeerInfo> {
        // this is for testing only. We can prepare the configuration with a
        // special Array<PeerInfo> for dynamic ports
        val peerInfos = config.getProperty("testpeerinfos")
        if (peerInfos != null) {
            if (peerInfos is PeerInfo) {
                return arrayOf(peerInfos)
            } else {
                return (peerInfos as List<PeerInfo>).toTypedArray()
            }
        }

        var peerCount = 0;
        config.getKeys("node").forEach { peerCount++ }
        peerCount = peerCount/4
        return Array(peerCount, {
            val port = config.getInt("node.$it.port")
            val host = config.getString("node.$it.host")
            val pubKey = config.getString("node.$it.pubkey").hexStringToByteArray()
            if (port == 0) {
                DynamicPortPeerInfo(host, pubKey)
            } else {
                PeerInfo(host, port, pubKey)
            }
        }

        )
    }

    /**
     * Pre-start function used to process the configuration file before calling the final [start] function
     *
     * @param configFile configuration file to parse
     * @param nodeIndex index of the local node
     */
    fun start(configFile: String, nodeIndex: Int) {
        val params = Parameters();
        val builder = FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration::class.java).
                configure(params.properties().
                        setFileName(configFile).
                        setListDelimiterHandler(DefaultListDelimiterHandler(',')))
        val config = builder.getConfiguration()
        start(config, nodeIndex)
    }


}

/**
 * Cryptographic key generator. Will generate a pair of public and private keys and print to stdout.
 */
fun keygen() {
    val cs = SECP256K1CryptoSystem()
    // check that privkey is between 1 - 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140 to be valid?
    val privkey = cs.getRandomBytes(32)
    val pubkey = secp256k1_derivePubKey(privkey)
    println("privkey:\t${privkey.toHex()}")
    println("pubkey: \t${pubkey.toHex()}")
}

/**
 * Main function, everything starts here
 *
 * @param args [ { --nodeIndex | -i } <index> ] [ { --config | -c } <configFile> ] [ {--keygen | -k } ]
 */
fun main(args: Array<String>) {
    var i = 0
    var nodeIndex = 0;
    var config = ""
    while (i < args.size) {
        when (args[i]) {
            "-i", "--nodeIndex" -> {
                nodeIndex = parseInt(args[++i])!!
            }
            "-c", "--config" -> {
                config = args[++i]
            }
            "-k", "--keygen" -> {
                keygen()
                exitProcess(0)
            }
        }
        i++
    }
    if (config == "") {
        config = "config/config.$nodeIndex.properties"
    }
    val node = PostchainNode()
    node.start(config, nodeIndex)
}