import dev.eliminater.purefusioniptv.plugin.api.PluginApi
import dev.eliminater.purefusioniptv.plugin.packaging.PfpPackager
import dev.eliminater.purefusioniptv.plugin.packaging.PfpSigner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PackageWorkflowTest {
    @get:Rule val fixtures: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private fun request() = PfpPackager.Request("dev.example.test", "Test", "example", "1.0.0", 1, 3,
        "1.0.0", 26, "dev.example.Test", listOf(PfpPackager.PayloadSource("classes.dex", "dex\n035\u0000fixture".toByteArray())),
        category = "ui_extension", trustTier = "verified_partner", capabilities = listOf("UI_HOME_EXTENSION"))
    private fun key(): PfpSigner.DeveloperKey {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        return PfpSigner.DeveloperKey("test-key", pair.private, pair.public)
    }
    @Test fun signedPackageVerifiesAndRejectsWrongKeyAndTampering() {
        val dir = Files.createTempDirectory("pfp-test").toFile()
        try {
            val k = key(); val file = PfpSigner.build(request(), k, "market_root_v1", dir)
            assertEquals("dev.example.test", verifyPackage(file, k.publicKey)["pluginId"])
            assertThrows(IllegalArgumentException::class.java) { verifyPackage(file, key().publicKey) }
            val tampered = dir.resolve("tampered.pfp")
            rewritePackage(file, tampered) { name, bytes ->
                if (name == "payload/classes.dex") "tampered".toByteArray() else bytes
            }
            assertEquals("Payload integrity failure",
                assertThrows(IllegalArgumentException::class.java) { verifyPackage(tampered, k.publicKey) }.message)
        } finally { dir.deleteRecursively() }
    }

    @Test fun developmentPackageVerifiesWithCurrentApiAndCannotPassSignedPreflight() {
        val file = developmentFixture()
        val manifest = verifyDevelopmentPackage(file)
        assertEquals("dev.example.test", manifest["pluginId"])
        assertEquals(PluginApi.CURRENT, manifest["apiLevel"])
        assertThrows(IllegalArgumentException::class.java) { verifyPackage(file) }
    }

    @Test fun developmentPackageStillAcceptsApi3() {
        val file = PfpPackager.build(
            request().copy(apiLevel = 3, trustTier = PfpPackager.TRUST_TIER_UNSIGNED_DEV),
            fixtures.newFolder(),
        )
        assertEquals(3, verifyDevelopmentPackage(file)["apiLevel"])
    }

    @Test fun truncatedArchiveIsRejected() {
        val source = developmentFixture()
        val truncated = fixtures.newFile("truncated.pfp")
        truncated.writeBytes(source.readBytes().copyOf(32))
        assertThrows(ZipException::class.java) { verifyDevelopmentPackage(truncated) }
    }

    @Test fun missingManifestOrDexIsRejected() {
        val source = developmentFixture()
        for ((index, missing) in listOf("manifest.json", "payload/classes.dex").withIndex()) {
            val incomplete = fixtures.newFile("incomplete-$index.pfp")
            rewritePackage(source, incomplete) { name, bytes -> bytes.takeUnless { name == missing } }
            val expected = if (missing == "manifest.json") "Missing manifest" else "Unexpected or missing package entries"
            assertEquals(expected, assertThrows(IllegalArgumentException::class.java) {
                verifyDevelopmentPackage(incomplete)
            }.message)
        }
    }

    @Test fun incompatibleOrMalformedApiDeclarationsAreRejected() {
        val source = developmentFixture()
        val invalidLevels = listOf("null", "\"3\"", "3.5", "0",
            (PluginApi.MIN_SUPPORTED - 1).toString(), (PluginApi.CURRENT + 1).toString())
        for ((index, value) in invalidLevels.withIndex()) {
            val invalid = fixtures.newFile("invalid-api-$index.pfp")
            rewritePackage(source, invalid) { name, bytes ->
                if (name == "manifest.json") {
                    bytes.decodeToString().replace("\"apiLevel\": ${PluginApi.CURRENT}", "\"apiLevel\": $value").toByteArray()
                } else bytes
            }
            assertEquals("Unsupported or invalid Plugin API level", assertThrows(IllegalArgumentException::class.java) {
                verifyDevelopmentPackage(invalid)
            }.message)
        }
    }

    @Test fun malformedSchemaMetadataIsRejected() {
        val source = developmentFixture()
        val invalid = fixtures.newFile("invalid-metadata.pfp")
        rewritePackage(source, invalid) { name, bytes ->
            if (name == "manifest.json") {
                bytes.decodeToString().replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"").toByteArray()
            } else bytes
        }
        assertEquals("Unsupported or invalid manifest schema", assertThrows(IllegalArgumentException::class.java) {
            verifyDevelopmentPackage(invalid)
        }.message)
    }

    private fun developmentFixture() = PfpPackager.build(
        request().copy(apiLevel = PluginApi.CURRENT, trustTier = PfpPackager.TRUST_TIER_UNSIGNED_DEV),
        fixtures.newFolder(),
    )

    private fun rewritePackage(source: File, target: File, transform: (String, ByteArray) -> ByteArray?) {
        ZipFile(source).use { zip ->
            ZipOutputStream(target.outputStream()).use { out ->
                for (entry in zip.entries()) {
                    val original = zip.getInputStream(entry).use { it.readBytes() }
                    val bytes = transform(entry.name, original) ?: continue
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
    }
    @Test fun privateKeyMaterialInPayloadIsRejected() {
        val dir = Files.createTempDirectory("pfp-leak").toFile()
        try {
            val k = key()
            val req = request().copy(payload = request().payload + PfpPackager.PayloadSource("assets/data.txt", "-----BEGIN PRIVATE KEY-----".toByteArray()))
            val file = PfpSigner.build(req, k, "market_root_v1", dir)
            assertThrows(IllegalArgumentException::class.java) { verifyPackage(file, k.publicKey) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun keysAreExternalAndNeverOverwritten() {
        val dir = Files.createTempDirectory("pfp-key-test").toFile(); val project = dir.resolve("project").apply { mkdir() }
        try {
            assertThrows(IllegalArgumentException::class.java) { createKey(project.resolve("keys"), project) }
            val external = dir.resolve("keys")
            createKey(external, project)
            val before = external.resolve("developer-private.pem").readBytes()
            readKey(external)
            assertThrows(IllegalArgumentException::class.java) { createKey(external, project) }
            assertArrayEquals(before, external.resolve("developer-private.pem").readBytes())
            assertThrows(IllegalArgumentException::class.java) { readKey(dir.resolve("missing")) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun mismatchedPrivatePublicPairFailsBeforePackaging() {
        val a = key(); val b = key()
        assertThrows(IllegalArgumentException::class.java) {
            PfpSigner.sign("test".toByteArray(), PfpSigner.DeveloperKey("bad", a.privateKey, b.publicKey))
        }
    }
    @Test fun nestedProjectCannotCreateKeyElsewhereInEnclosingGitCheckout() {
        val dir = Files.createTempDirectory("pfp-git-key-test").toFile()
        try {
            // A .git file represents a submodule or linked worktree as well as a .git directory.
            dir.resolve(".git").writeText("gitdir: external-worktree")
            val project = dir.resolve("sdk/template").apply { mkdirs() }
            assertThrows(IllegalArgumentException::class.java) { createKey(dir.resolve("sdk/keys"), project) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun binaryPrivateKeyInPayloadIsRejected() {
        val dir = Files.createTempDirectory("pfp-der-leak").toFile()
        try {
            val k = key()
            val req = request().copy(payload = request().payload + PfpPackager.PayloadSource("assets/data.bin", k.privateKey.encoded))
            val file = PfpSigner.build(req, k, "market_root_v1", dir)
            assertThrows(IllegalArgumentException::class.java) { verifyPackage(file, k.publicKey) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun uppercaseSecondExecutableIsRejectedLikeProduction() {
        val dir = Files.createTempDirectory("pfp-extra-executable").toFile()
        try {
            val k = key()
            for (extension in listOf("DEX", "APK", "JAR")) {
                val req = request().copy(payload = request().payload + PfpPackager.PayloadSource("extra.$extension", byteArrayOf(1,2)))
                val file = PfpSigner.build(req, k, "market_root_v1", dir)
                assertThrows(IllegalArgumentException::class.java) { verifyPackage(file, k.publicKey) }
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun relativeSigningDirectoryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { createKey(java.io.File("../signing-relative-test"), java.io.File(".")) }
    }
    @Test fun duplicatePayloadIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PfpPackager.validate(request().copy(payload = request().payload + request().payload)) }
    }
}
