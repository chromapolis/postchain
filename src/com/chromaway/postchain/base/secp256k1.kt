package com.chromaway.postchain.base

import com.chromaway.postchain.core.Signature
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.ec.CustomNamedCurves;
import org.spongycastle.crypto.params.*;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.crypto.signers.HMacDSAKCalculator;
import org.spongycastle.crypto.params.ECPrivateKeyParameters
import java.math.BigInteger
import org.spongycastle.asn1.ASN1Integer
import org.spongycastle.asn1.DERSequenceGenerator
import java.io.ByteArrayOutputStream
import org.spongycastle.crypto.params.ECPublicKeyParameters
import java.io.IOException
import org.spongycastle.asn1.DLSequence
import org.spongycastle.asn1.ASN1InputStream
import java.security.MessageDigest



// signing code taken from bitcoinj ECKey

val CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1")
val CURVE = ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH())
val HALF_CURVE_ORDER = CURVE_PARAMS.getN().shiftRight(1)

fun bigIntegerToBytes(b: BigInteger, numBytes: Int): ByteArray {
    val bytes = ByteArray(numBytes)
    val biBytes = b.toByteArray()
    val start = if (biBytes.size == numBytes + 1) 1 else 0
    val length = Math.min(biBytes.size, numBytes)
    System.arraycopy(biBytes, start, bytes, numBytes - length, length)
    return bytes
}

fun encodeSignature(r: BigInteger, s: BigInteger): ByteArray {
    val bos = ByteArrayOutputStream(72)
    val seq = DERSequenceGenerator(bos)
    seq.addObject(ASN1Integer(r))
    seq.addObject(ASN1Integer(s))
    seq.close()
    return bos.toByteArray()
}

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
}

fun secp256k1_sign(digest: ByteArray, privateKeyBytes: ByteArray): ByteArray {
    val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
    val privateKey = BigInteger(1, privateKeyBytes)
    val privKey = ECPrivateKeyParameters(privateKey, CURVE)
    signer.init(true, privKey)
    val components = signer.generateSignature(digest)
    if (components[0] <= HALF_CURVE_ORDER) {
        // canonicalize low S
        components[1] = CURVE.getN().subtract(components[1])
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

class SECP256K1CryptoSystem : CryptoSystem {

    override fun digest(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(bytes);
    }
    override fun makeSigner(pubKey: ByteArray, privKey: ByteArray): Signer {
        return { data ->
            Signature(pubKey, secp256k1_sign(digest(data), privKey))  }
    }
    override fun makeVerifier(): Verifier {
        return { data, signature: Signature ->
            secp256k1_verify(digest(data), signature.subjectID, signature.data)
        }
    }
}