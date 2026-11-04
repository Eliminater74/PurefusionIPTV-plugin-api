package dev.eliminater.purefusioniptv.plugin.packaging

import java.io.File
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/**
 * Builds a **developer-signed** `.pfp`.
 *
 * ## What is signed
 *
 * The developer signs the exact bytes of `manifest.json` with Ed25519. Those bytes come from
 * [PfpPackager.manifestBytes], so a signed package carries the same manifest layout and payload
 * hashes as an unsigned one, plus the signing block. Three entries are added after the payload:
 *
 * ```
 * certs/developer-public-key.pem   ← SPKI public key, one Base64 line
 * signatures/developer.sig         ← Base64 Ed25519 signature over manifest.json
 * ```
 *
 * `signing.developerKeySha256` inside the manifest is the SHA-256 of the whitespace-free PEM Base64
 * body as US-ASCII. The host and the Marketplace compute the same fingerprint from the certificate
 * and refuse a mismatch. The certificate cannot be swapped without invalidating the signature the
 * Marketplace later adds over the same manifest.
 *
 * ## What is not here
 *
 * No key is generated, stored or embedded. The caller supplies a key it already controls, and this
 * object never writes the private key anywhere. The Marketplace counter-signature
 * (`signatures/marketplace.sig`) is added by the host from the approved release, never by a
 * developer.
 *
 * Standalone by construction, like [PfpPackager]: JDK only (`java.security`), no Android and no
 * core-app imports. Ed25519 through JCA needs a JDK 15+ build machine. The signer never runs on a
 * device.
 */
object PfpSigner {
    /** A developer key pair plus the stable id the Marketplace records for it. */
    class DeveloperKey(
        val developerKeyId: String,
        val privateKey: PrivateKey,
        val publicKey: PublicKey,
    )

    /**
     * Validates, signs and writes the package to [outputDir].
     *
     * The developer signature is verified against [key]'s public key before any byte is written,
     * and a key pair that does not match is refused.
     */
    fun build(
        request: PfpPackager.Request,
        key: DeveloperKey,
        marketplaceKeyId: String,
        outputDir: File,
        marketplaceSignatureAlgorithm: String = PfpPackager.SIGNATURE_ALGORITHM_ED25519,
    ): File {
        val signed = signedRequest(request, key, marketplaceKeyId, marketplaceSignatureAlgorithm)
        val manifest = PfpPackager.manifestBytes(signed)
        val signature = sign(manifest, key)
        return PfpPackager.writePackage(
            signed,
            manifest,
            outputDir,
            linkedMapOf(
                PfpPackager.DEVELOPER_PUBLIC_KEY_ENTRY to
                    publicKeyPem(key.publicKey).toByteArray(Charsets.US_ASCII),
                PfpPackager.DEVELOPER_SIGNATURE_ENTRY to signature.toByteArray(Charsets.US_ASCII),
            ),
        )
    }

    /** [request] with its signing block and tier set for a developer-signed release. */
    fun signedRequest(
        request: PfpPackager.Request,
        key: DeveloperKey,
        marketplaceKeyId: String,
        marketplaceSignatureAlgorithm: String = PfpPackager.SIGNATURE_ALGORITHM_ED25519,
    ): PfpPackager.Request {
        if (request.signing != null) {
            throw PfpPackager.PackagingException("The signing block is filled by the signer")
        }
        requireEd25519(key.publicKey)
        val pem = publicKeyPem(key.publicKey)
        return request.copy(
            developerKeyId = key.developerKeyId,
            signing =
                PfpPackager.Signing(
                    developerKeyId = key.developerKeyId,
                    developerKeySha256 = developerKeySha256(pem),
                    marketplaceKeyId = marketplaceKeyId,
                    marketplaceSignatureAlgorithm = marketplaceSignatureAlgorithm,
                ),
        )
    }

    /** Base64 Ed25519 signature over [bytes], checked against the public key before returning. */
    fun sign(
        bytes: ByteArray,
        key: DeveloperKey,
    ): String {
        requireEd25519(key.publicKey)
        val signature =
            try {
                Signature.getInstance(PfpPackager.SIGNATURE_ALGORITHM_ED25519).run {
                    initSign(key.privateKey)
                    update(bytes)
                    sign()
                }
            } catch (error: java.security.GeneralSecurityException) {
                throw PfpPackager.PackagingException(
                    "Ed25519 signing failed (${error.javaClass.simpleName}). A JDK 15+ build " +
                        "machine and an Ed25519 PKCS#8 private key are required.",
                )
            }
        if (!verify(bytes, signature, key.publicKey)) {
            throw PfpPackager.PackagingException(
                "The private key does not match the developer public key ${key.developerKeyId}",
            )
        }
        return base64(signature)
    }

    /** One-line SPKI PEM with a trailing newline, the layout the host and Marketplace accept. */
    fun publicKeyPem(publicKey: PublicKey): String {
        requireEd25519(publicKey)
        return "-----BEGIN PUBLIC KEY-----\n" + base64(publicKey.encoded) + "\n-----END PUBLIC KEY-----\n"
    }

    /** Host developer-key fingerprint: SHA-256 of the whitespace-free PEM Base64 body. */
    fun developerKeySha256(publicKeyPem: String): String {
        val body =
            publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .filterNot { it.isWhitespace() }
        if (body.isEmpty()) throw PfpPackager.PackagingException("Empty public key PEM")
        return MessageDigest
            .getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
    }

    private fun verify(
        bytes: ByteArray,
        signature: ByteArray,
        publicKey: PublicKey,
    ): Boolean =
        try {
            Signature.getInstance(PfpPackager.SIGNATURE_ALGORITHM_ED25519).run {
                initVerify(publicKey)
                update(bytes)
                verify(signature)
            }
        } catch (_: java.security.GeneralSecurityException) {
            false
        }

    /** Exactly a 44-byte Ed25519 SubjectPublicKeyInfo (RFC 8410). */
    private fun requireEd25519(publicKey: PublicKey) {
        val encoded = publicKey.encoded
        if (encoded == null ||
            encoded.size != ED25519_SPKI_PREFIX.size + ED25519_KEY_BYTES ||
            !encoded.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX)
        ) {
            throw PfpPackager.PackagingException("The developer key must be an Ed25519 public key")
        }
    }

    /**
     * RFC 4648 Base64 with padding.
     *
     * Written out because `java.util.Base64` is API 26 and this module's minSdk is 25. The signer
     * never runs on a device, but the class is compiled into the host app.
     */
    internal fun base64(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index < bytes.size) {
            val b0 = bytes[index].toInt() and 0xff
            val b1 = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else -1
            val b2 = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else -1
            out.append(ALPHABET[b0 ushr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 < 0) 0 else b1 ushr 4)])
            out.append(if (b1 < 0) '=' else ALPHABET[((b1 and 0x0f) shl 2) or (if (b2 < 0) 0 else b2 ushr 6)])
            out.append(if (b2 < 0) '=' else ALPHABET[b2 and 0x3f])
            index += 3
        }
        return out.toString()
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val ED25519_KEY_BYTES = 32

    /** DER SEQUENCE { SEQUENCE { OID 1.3.101.112 } BIT STRING (0 unused bits) } header. */
    private val ED25519_SPKI_PREFIX =
        byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
}
