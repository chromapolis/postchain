package net.postchain.gtx.gtxml

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.toHex
import net.postchain.core.Signature
import net.postchain.gtx.*
import org.junit.Test

class SigningTest {

    @Test
    fun autoSign_autosigning_for_empty_signatures_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/auto-sign/tx.xml").readText()

        val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"

        val tx = GTXMLTransactionParser.parseGTXMLTransaction(
                xml,
                TransactionContext(null)
        )

        val cs = SECP256K1CryptoSystem()
        val pubKey = pubKey(2)
        val privKey = privKey(2)
        val signer = cs.makeSigner(pubKey, privKey)

        println(pubKey.toHex())
        println(tx.signers[0].toHex() == "03B2EF623E7EC933C478135D1763853CBB91FC31BA909AEC1411CA253FDCC1AC94")

        // Auto-signing
        val signature = signer(tx.serializeWithoutSignatures())
        val signatureStr = signature.data.toHex()
        println(signatureStr)
        val verify = cs.verifyDigest(tx.getDigestForSigning(cs), signature)
        println("Verify!: $verify")

        println("\n===")

        ////===

        println("\n\n\n===")

        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val s = signer(data)
        val v = cs.verifyDigest(cs.digest(data), s)
        println("Verify: $v")



    }

}