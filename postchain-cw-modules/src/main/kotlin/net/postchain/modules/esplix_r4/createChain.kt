package net.postchain.modules.esplix_r4

import net.postchain.base.CryptoSystem
import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

//class EsplixCreateChainData(val chainID: ByteArray, val nonce: ByteArray, val callIndex: Int, val payload: ByteArray)

fun computeChainID(cryptoSystem: CryptoSystem,
                   blockchainRID: ByteArray,
                   nonce: ByteArray, payload: ByteArray, signers: Array<ByteArray>): ByteArray {
    val signersCombined = signers.reduce{it, acc -> it + acc}
    return cryptoSystem.digest(blockchainRID + nonce + payload + signersCombined)
}

class create_chain_op (val config: EsplixConfig, data: ExtOpData): GTXOperation(data) {
    private val r = QueryRunner()
    private val longHandler = ScalarHandler<Long>()
    val nonce = data.args[0].asByteArray()
    val payload = data.args[1].asByteArray()
    val chainID = computeChainID(config.cryptoSystem,
            data.blockchainRID, nonce, payload, data.signers)

    override fun isCorrect(): Boolean {
        if (data.args.size != 3)
            return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        r.query(ctx.conn, "SELECT r4_createChain (?, ?, ?, ?, ?)", longHandler,
                nonce, chainID, ctx.txIID, data.opIndex, payload)
        return true
    }
}
