import dev.eliminater.purefusioniptv.plugin.packaging.PfpPackager
import dev.eliminater.purefusioniptv.plugin.packaging.PfpSigner
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PackageWorkflowTest {
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
            ZipFile(file).use { zip -> ZipOutputStream(tampered.outputStream()).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(if (entry.name == "payload/classes.dex") "tampered".toByteArray() else zip.getInputStream(entry).readBytes())
                    out.closeEntry()
                }
            } }
            assertThrows(IllegalArgumentException::class.java) { verifyPackage(tampered, k.publicKey) }
        } finally { dir.deleteRecursively() }
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
