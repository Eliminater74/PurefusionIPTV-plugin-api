package dev.eliminater.purefusioniptv.plugin.packaging

import dev.eliminater.purefusioniptv.plugin.api.PluginApi
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Developer signing and the canonical entry point spelling.
 *
 * Keys are generated per test and never leave the temporary directory.
 */
class PfpSignerTest {

    private lateinit var workDir: File
    private val developer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val key = PfpSigner.DeveloperKey("fixture_dev_1", developer.private, developer.public)

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("pfp-signer").toFile()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    private fun request(trustTier: String = "verified_partner") = PfpPackager.Request(
        pluginId = "dev.example.signed",
        displayName = "Signed Plugin",
        developerId = "Example",
        version = "1.2.3",
        versionCode = 7,
        apiLevel = PluginApi.CURRENT,
        minHostVersion = "1.0.0",
        minSdk = 25,
        entryPointClassName = "dev.example.signed.SignedPlugin",
        payload = listOf(
            PfpPackager.PayloadSource("classes.apk", byteArrayOf(5, 6, 7, 8)),
            PfpPackager.PayloadSource("assets/icon.png", byteArrayOf(9, 10)),
        ),
        trustTier = trustTier,
        capabilities = listOf("MEDIA_SERVER_ACCESS", "NETWORK_EXTERNAL"),
    )

    private fun entries(file: File): Map<String, ByteArray> =
        ZipFile(file).use { zip ->
            zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() }
        }

    private fun names(file: File): List<String> =
        ZipFile(file).use { zip -> zip.entries().asSequence().map { it.name }.toList() }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `unsigned packages use the canonical lowercase plugin entry point`() {
        val manifest = entries(PfpPackager.build(request("UNSIGNED_DEV"), workDir))
            .getValue("manifest.json").toString(Charsets.UTF_8)
        assertTrue(manifest, manifest.contains("{ \"type\": \"plugin\", \"className\": "))
        assertFalse(manifest, manifest.contains("\"PLUGIN\""))
        // The development signing block is unchanged.
        assertTrue(manifest.contains("\"developerKeyId\": \"unsigned-dev\",\n    \"developerSignatureAlgorithm\": \"Ed25519\"\n  }"))
        assertFalse(manifest.contains("marketplaceKeyId"))
    }

    @Test
    fun `a signed package verifies over the exact manifest bytes and binds its certificate`() {
        val file = PfpSigner.build(request(), key, "market_root_v1", workDir)
        val entries = entries(file)
        val manifest = entries.getValue(PfpPackager.MANIFEST_ENTRY)
        val pem = entries.getValue(PfpPackager.DEVELOPER_PUBLIC_KEY_ENTRY).toString(Charsets.US_ASCII)
        val signature = Base64.getDecoder()
            .decode(entries.getValue(PfpPackager.DEVELOPER_SIGNATURE_ENTRY).toString(Charsets.US_ASCII))

        val verified = Signature.getInstance("Ed25519").run {
            initVerify(developer.public); update(manifest); verify(signature)
        }
        assertTrue(verified)

        val body = pem.lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")
        assertArrayEquals(developer.public.encoded, Base64.getDecoder().decode(body))
        assertEquals("-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n", pem)

        val text = manifest.toString(Charsets.UTF_8)
        listOf(
            "\"trustTier\": \"verified_partner\"",
            "\"type\": \"plugin\"",
            "\"developerKeyId\": \"fixture_dev_1\"",
            "\"developerKeySha256\": \"${sha256(body.toByteArray(Charsets.US_ASCII))}\"",
            "\"developerSignatureAlgorithm\": \"Ed25519\"",
            "\"marketplaceKeyId\": \"market_root_v1\"",
            "\"marketplaceSignatureAlgorithm\": \"Ed25519\"",
        ).forEach { assertTrue("missing $it\n$text", text.contains(it)) }
        assertFalse(text.contains("unsigned-dev"))
    }

    @Test
    fun `signing adds only the certificate and signature after an unchanged payload`() {
        val signed = PfpSigner.build(request(), key, "market_root_v1", File(workDir, "signed"))
        val unsigned = PfpPackager.build(request(), File(workDir, "unsigned"))
        assertEquals(
            listOf(
                "manifest.json", "payload/assets/icon.png", "payload/classes.apk",
                "certs/developer-public-key.pem", "signatures/developer.sig",
            ),
            names(signed),
        )
        val a = entries(signed)
        val b = entries(unsigned)
        listOf("payload/assets/icon.png", "payload/classes.apk").forEach {
            assertArrayEquals(it, b.getValue(it), a.getValue(it))
        }
        assertFalse(a.containsKey(PfpPackager.MARKETPLACE_SIGNATURE_ENTRY))
    }

    @Test
    fun `signing is reproducible for the same key and inputs`() {
        val first = PfpSigner.build(request(), key, "market_root_v1", File(workDir, "a")).readBytes()
        val second = PfpSigner.build(request(), key, "market_root_v1", File(workDir, "b")).readBytes()
        assertArrayEquals(first, second)
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val third = PfpSigner.build(
            request(), PfpSigner.DeveloperKey("fixture_dev_1", other.private, other.public),
            "market_root_v1", File(workDir, "c"),
        ).readBytes()
        assertNotEquals(sha256(first), sha256(third))
    }

    @Test
    fun `a private key that does not match the public key is refused before writing`() {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val mismatched = PfpSigner.DeveloperKey("fixture_dev_1", other.private, developer.public)
        val out = File(workDir, "mismatch")
        try {
            PfpSigner.build(request(), mismatched, "market_root_v1", out)
            fail("mismatched key pair must be refused")
        } catch (expected: PfpPackager.PackagingException) {
            assertTrue(expected.message!!.contains("does not match"))
        }
        assertFalse(out.exists() && out.listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun `non Ed25519 developer keys are refused`() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        try {
            PfpSigner.build(request(), PfpSigner.DeveloperKey("rsa_dev", rsa.private, rsa.public), "market_root_v1", workDir)
            fail("RSA developer key must be refused")
        } catch (expected: PfpPackager.PackagingException) {
            assertTrue(expected.message!!.contains("Ed25519"))
        }
    }

    @Test
    fun `a signed package cannot claim the development tier`() {
        listOf("UNSIGNED_DEV", "local_untrusted").forEach { tier ->
            try {
                PfpSigner.build(request(tier), key, "market_root_v1", workDir)
                fail("$tier must be refused for a signed package")
            } catch (expected: PfpPackager.PackagingException) {
                assertTrue(expected.message!!.contains("Marketplace trust tier"))
            }
        }
    }

    @Test
    fun `malformed key ids are refused`() {
        listOf("", "unsigned-dev", "has space", "-leading").forEach { id ->
            try {
                PfpSigner.build(request(), PfpSigner.DeveloperKey(id, developer.private, developer.public), "market_root_v1", workDir)
                fail("developer key id '$id' must be refused")
            } catch (_: PfpPackager.PackagingException) {
            }
        }
        try {
            PfpSigner.build(request(), key, "bad id", workDir)
            fail("bad Marketplace key id must be refused")
        } catch (_: PfpPackager.PackagingException) {
        }
    }

    @Test
    fun `the unsigned builder refuses a request that already carries a signing block`() {
        val signedRequest = PfpSigner.signedRequest(request(), key, "market_root_v1")
        try {
            PfpPackager.build(signedRequest, workDir)
            fail("an unsigned build must not emit a signing block without a signature")
        } catch (expected: PfpPackager.PackagingException) {
            assertTrue(expected.message!!.contains("PfpSigner"))
        }
    }

    @Test
    fun `a changed manifest no longer verifies`() {
        val file = PfpSigner.build(request(), key, "market_root_v1", workDir)
        val entries = entries(file)
        val manifest = entries.getValue("manifest.json").toString(Charsets.UTF_8)
            .replace("\"versionCode\": 7", "\"versionCode\": 8").toByteArray()
        val signature = Base64.getDecoder()
            .decode(entries.getValue("signatures/developer.sig").toString(Charsets.US_ASCII))
        val verified = Signature.getInstance("Ed25519").run {
            initVerify(developer.public); update(manifest); verify(signature)
        }
        assertFalse(verified)
    }

    @Test
    fun `the fingerprint and base64 match the JDK encoder for every padding length`() {
        val pem = PfpSigner.publicKeyPem(developer.public)
        val body = Base64.getEncoder().encodeToString(developer.public.encoded)
        assertEquals("-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n", pem)
        assertEquals(sha256(body.toByteArray(Charsets.US_ASCII)), PfpSigner.developerKeySha256(pem))
        assertEquals(PfpSigner.developerKeySha256(pem), PfpSigner.developerKeySha256(pem.replace("\n", "\r\n")))
        (0..70).forEach { length ->
            val bytes = ByteArray(length) { (it * 37 + length - 128).toByte() }
            assertEquals(Base64.getEncoder().encodeToString(bytes), PfpSigner.base64(bytes))
        }
        val signature = PfpSigner.sign("probe".toByteArray(), key)
        assertEquals(64, Base64.getDecoder().decode(signature).size)
    }
}
