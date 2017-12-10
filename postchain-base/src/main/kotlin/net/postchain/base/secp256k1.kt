// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.core.Signature
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.ec.CustomNamedCurves
import org.spongycastle.crypto.params.ECDomainParameters
import org.spongycastle.crypto.params.ECPrivateKeyParameters
import org.spongycastle.crypto.params.ECPublicKeyParameters
import org.spongycastle.crypto.signers.ECDSASigner
import org.spongycastle.crypto.signers.HMacDSAKCalculator
import org.spongycastle.util.Arrays
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom


// signing code taken from bitcoinj ECKey

val CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1")
val CURVE = ECDomainParameters(CURVE_PARAMS.curve, CURVE_PARAMS.g, CURVE_PARAMS.n, CURVE_PARAMS.h)
val HALF_CURVE_ORDER: BigInteger = CURVE_PARAMS.n.shiftRight(1)

fun bigIntegerToBytes(b: BigInteger, numBytes: Int): ByteArray {
    val bytes = ByteArray(numBytes)
    val biBytes = b.toByteArray()
    val start = if (biBytes.size == numBytes + 1) 1 else 0
    val length = Math.min(biBytes.size, numBytes)
    System.arraycopy(biBytes, start, bytes, numBytes - length, length)
    return bytes
}

/*
fun encodeSignature(r: BigInteger, s: BigInteger): ByteArray {
    val bos = ByteArrayOutputStream(72)
    val seq = DERSequenceGenerator(bos)
    seq.addObject(ASN1Integer(r))
    seq.addObject(ASN1Integer(s))
    seq.close()
    return bos.toByteArray()
}
*/

fun encodeSignature(r: BigInteger, s: BigInteger): ByteArray {
    return Arrays.concatenate(
            bigIntegerToBytes(r, 32),
            bigIntegerToBytes(s, 32)
    )
}

fun secp256k1_decodeSignature(bytes: ByteArray): Array<BigInteger> {
    val r = BigInteger(1, bytes.sliceArray(0..31))
    val s = BigInteger(1, bytes.sliceArray(32..63))
    return arrayOf(r, s)
}

/*
fun secp256k1_decodeSignature(bytes: ByteArray): Array<BigInteger> {
    var decoder: ASN1InputStream? = null
    try {
        decoder = ASN1InputStream(bytes)
        val seq = (decoder.readObject() ?: throw IllegalArgumentException("Reached past end of ASN.1 stream.")) as DLSequence
        val r: ASN1Integer
        val s: ASN1Integer
        try {
            r = seq.getObjectAt(0) as ASN1Integer
            s = seq.getObjectAt(1) as ASN1Integer
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(e)
        }
        // OpenSSL deviates from the DER spec by interpreting these values as unsigned, though they should not be
        // Thus, we always use the positive versions. See: http://r6.ca/blog/20111119T211504Z.html
        return arrayOf(r.positiveValue, s.positiveValue)
    } catch (e: IOException) {
        throw IllegalArgumentException(e)
    } finally {
        if (decoder != null)
            try {
                decoder.close()
            } catch (x: IOException) { }
    }
}*/

fun secp256k1_sign(digest: ByteArray, privateKeyBytes: ByteArray): ByteArray {
    val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
    val privateKey = BigInteger(1, privateKeyBytes)
    val privKey = ECPrivateKeyParameters(privateKey, CURVE)
    signer.init(true, privKey)
    val components = signer.generateSignature(digest)
    if (components[0] <= HALF_CURVE_ORDER) {
        // canonicalize low S
        components[1] = CURVE.n.subtract(components[1])
    }
    return encodeSignature(components[0], components[1])
}

fun secp256k1_verify(digest: ByteArray, pubKey: ByteArray, signature: ByteArray): Boolean {
    val signer = ECDSASigner()
    val params = ECPublicKeyParameters(CURVE.curve.decodePoint(pubKey), CURVE)
    signer.init(false, params)
    try {
        val sig = secp256k1_decodeSignature(signature)
        return signer.verifySignature(digest, sig[0], sig[1])
    } catch (e: Exception) {
        return false
    }
}

fun secp256k1_derivePubKey(privKey: ByteArray): ByteArray {
    val d = BigInteger(privKey)
    val q = CURVE_PARAMS.g.multiply(d)
    return q.getEncoded(true)
}

/**
 * A collection of cryptographic functions based on the elliptic curve secp256k1
 */
class SECP256K1CryptoSystem : CryptoSystem {
    private val rand = SecureRandom()

    /**
     * Calculate the hash digest of a message
     *
     * @param bytes A ByteArray of data consisting of the message we want the hash digest of
     * @return The hash digest of [bytes]
     */
    override fun digest(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes)
    }

    /**
     * Create a function to be used for signing data based on supplied key parameters
     *
     * @param pubkey The public key used to verify the signature
     * @param privKey The private key used to create the signature
     * @return A function to be used to sign specified [data] with [privkey]
     */
    override fun makeSigner(pubKey: ByteArray, privKey: ByteArray): Signer {
        return { data ->
            Signature(pubKey, secp256k1_sign(digest(data), privKey))  }
    }

    /**
     * Verify a signature from hash digest of a message
     *
     * @param digest The hash digest of the message we want to verify the signature [s] for
     * @param s The signature to verify
     * @return True or false depending on the outcome of the verification
     */
    override fun verifyDigest(digest: ByteArray, s: Signature): Boolean {
        return secp256k1_verify(digest, s.subjectID, s.data)
    }

    /**
     * Create a function to be used for verifying a signature based on some data
     *
     * @return A function that will take a signature and some data and return a boolean
     */
    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            secp256k1_verify(digest(data), signature.subjectID, signature.data)
        }
    }

    /**
     * Generate some amount of random bytes
     *
     * @param size The number of bytes to generate
     * @return The random bytes in a ByteArray
     */
    //TODO: Is it really secure to use SecureRandom()? Needs more research.
    override fun getRandomBytes(size: Int): ByteArray {
        val ret = ByteArray(size)
        rand.nextBytes(ret)
        return ret
    }
}