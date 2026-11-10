import dev.eliminater.purefusioniptv.plugin.api.PluginApi
import dev.eliminater.purefusioniptv.plugin.packaging.PfpPackager
import dev.eliminater.purefusioniptv.plugin.packaging.PfpSigner
import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.*
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipFile

private fun config() = JsonSlurper().parse(File("manifest.json")) as Map<*, *>
private fun Map<*, *>.text(key: String) = this[key] as? String ?: error("Missing $key")
private fun Map<*, *>.number(key: String) = (this[key] as? Number)?.toInt() ?: error("Missing $key")
private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun main(args: Array<String>) {
    val (mode, outputPath, signingPath, filePath) = args
    val c = config()
    require(c["apiLevel"] == PluginApi.CURRENT && c.number("minSdk") >= 26) {
        "Use API ${PluginApi.CURRENT} and minSdk >=26"
    }
    val keyDir = signingPath.takeIf { it.isNotBlank() }?.let(::File)
    if (mode == "keygen") { createKey(requireNotNull(keyDir) { "Set PUREFUSION_PFP_SIGNING_DIR outside the checkout" }, File(".")); println("Created external Ed25519 key; back it up securely."); return }
    if (mode == "keycheck") { println("Developer key fingerprint: " + PfpSigner.developerKeySha256(PfpSigner.publicKeyPem(readKey(requireNotNull(keyDir) { "Set PUREFUSION_PFP_SIGNING_DIR" }).publicKey))); return }
    if (mode == "verify") {
        val file = if (filePath.isNotBlank()) File(filePath) else File("packaging/build/signed/${c.text("pluginId")}-${c.text("version")}.pfp")
        val manifest = verifyPackage(file, keyDir?.let { readKey(it).publicKey })
        bindConfig(c, manifest)
        println("Verified developer signature, payload integrity, identity and absence of private keys: ${file.name}")
        return
    }
    require(mode in listOf("signed", "development"))
    val apk = File("plugin/build/outputs/apk/release/plugin-release.apk")
    val dex = ZipFile(apk).use { zip ->
        val entries = zip.entries().asSequence().filter { it.name.endsWith(".dex") }.toList()
        require(entries.map { it.name } == listOf("classes.dex")) { "Isolated runtime requires exactly one release classes.dex" }
        zip.getInputStream(entries.single()).use { it.readBytes() }
    }
    require(dex.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10))) { "Expected DEX payload" }
    val request = PfpPackager.Request(
        pluginId = c.text("pluginId"), displayName = c.text("displayName"), developerId = c.text("developerId"),
        version = c.text("version"), versionCode = c.number("versionCode"), apiLevel = c.number("apiLevel"),
        minHostVersion = c.text("minHostVersion"), minSdk = c.number("minSdk"), entryPointClassName = c.text("entryPoint"),
        category = c.text("category"), capabilities = (c["capabilities"] as List<*>).map { it as String },
        trustTier = if (mode == "signed") "verified_partner" else PfpPackager.TRUST_TIER_UNSIGNED_DEV,
        payload = listOf(PfpPackager.PayloadSource("classes.dex", dex), PfpPackager.PayloadSource("assets/icon.png", File("marketplace/artwork/plugin-icon-placeholder.png").readBytes())),
    )
    val output = File(outputPath)
    val key = if (mode == "signed") {
        readKey(requireNotNull(keyDir) { "Set PUREFUSION_PFP_SIGNING_DIR; signing never creates a key automatically" })
    } else null
    val file = if (key == null) PfpPackager.build(request, output) else {
        PfpSigner.build(request, key, c.text("marketplaceKeyId"), output)
    }
    try {
        val manifest = if (key == null) verifyDevelopmentPackage(file) else verifyPackage(file, key.publicKey)
        bindConfig(c, manifest)
        ZipFile(file).use { zip ->
            output.resolve("manifest.json").writeBytes(zip.getInputStream(zip.getEntry("manifest.json")).readBytes())
        }
    } catch (e: Exception) {
        file.delete()
        throw e
    }
    println("Built and verified ${file.name} ($mode)")
}

private fun bindConfig(c: Map<*, *>, m: Map<*, *>) {
    for (field in listOf("pluginId", "displayName", "developerId", "version", "versionCode", "category", "apiLevel", "minSdk", "minHostVersion"))
        require(c[field] == m[field]) { "Manifest differs from configuration: $field" }
    require((c["capabilities"] as List<*>).toSet() == (m["capabilities"] as List<*>).toSet()) { "Capability mismatch" }
    require(((m["entrypoints"] as List<*>).single() as Map<*, *>)["className"] == c["entryPoint"]) { "Entrypoint mismatch" }
}

/** Local preflight uses the production entry layout, Ed25519 exact-byte signature and PEM-body fingerprint. */
fun verifyPackage(file: File, expectedKey: PublicKey? = null): Map<*, *> =
    verifyArchive(file, signed = true, expectedKey = expectedKey)

fun verifyDevelopmentPackage(file: File): Map<*, *> = verifyArchive(file, signed = false)

private fun verifyArchive(file: File, signed: Boolean, expectedKey: PublicKey? = null): Map<*, *> {
    require(file.isFile && file.length() in 1..64L * 1024 * 1024) { "Missing or oversized .pfp" }
    ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence().toList()
        val names = entries.map { it.name }
        require(entries.size <= 256 && names.map { it.lowercase() }.distinct().size == names.size) { "Duplicate archive entries" }
        var total = 0L
        val data = entries.associate { entry ->
            require(!entry.isDirectory && entry.size in 0..32L * 1024 * 1024 && !entry.name.contains('\\') &&
                entry.name.split('/').none { it == ".." || it.isEmpty() }) { "Unsafe archive entry" }
            total += entry.size; require(total <= 96L * 1024 * 1024) { "Expanded package too large" }
            val bytes = zip.getInputStream(entry).use { it.readNBytes(32 * 1024 * 1024 + 1) }
            require(bytes.size.toLong() == entry.size) { "Invalid archive size" }
            val text = String(bytes, Charsets.ISO_8859_1)
            // RFC 8410 Ed25519 PKCS#8 prefix catches raw private DER as well as PEM.
            val privateDerPrefix = byteArrayOf(0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20)
            val hasPrivateDer = bytes.size >= privateDerPrefix.size && (0..bytes.size - privateDerPrefix.size).any { offset ->
                privateDerPrefix.indices.all { bytes[offset + it] == privateDerPrefix[it] }
            }
            require(!hasPrivateDer && !Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----").containsMatchIn(text) && !Regex("(?i)(private|secret|credential|password|\\.key$|\\.p12$|\\.pfx$|\\.jks$)").containsMatchIn(entry.name)) { "Private key or secret file in package" }
            entry.name to bytes
        }
        val manifestBytes = requireNotNull(data["manifest.json"]) { "Missing manifest" }
        require(manifestBytes.size <= 262144)
        val manifest = JsonSlurper().parse(manifestBytes) as Map<*, *>
        require(manifest["schemaVersion"] == 1) { "Unsupported or invalid manifest schema" }
        require(manifest["apiLevel"] == PluginApi.CURRENT) { "Unsupported or invalid Plugin API level" }
        require(manifest.number("minSdk") >= 26)
        val trustTier = if (signed) "verified_partner" else PfpPackager.TRUST_TIER_UNSIGNED_DEV
        require(manifest["installType"] == "executable_bundle" && manifest["trustTier"] == trustTier)
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(manifest.text("pluginId")))
        val payload = manifest["payload"] as Map<*, *>
        require(payload["hashAlgorithm"] == "SHA-256")
        val files = payload["files"] as List<*>
        val declared = files.map { (it as Map<*, *>).text("path") }
        val executables = declared.filter { Regex("(?i)\\.(dex|apk|jar)$").containsMatchIn(it) }
        require(declared.distinct().size == declared.size && declared.all { it.startsWith("payload/") } && executables == listOf("payload/classes.dex")) { "Expected exactly payload/classes.dex as the executable" }
        val entrypoints = manifest["entrypoints"] as? List<*> ?: error("Missing entrypoint")
        val entrypoint = entrypoints.singleOrNull() as? Map<*, *> ?: error("Expected one plugin entrypoint")
        require(entrypoint["type"] == "plugin" && Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+").matches(entrypoint.text("className"))) { "Invalid plugin entrypoint" }
        val expected = (if (signed) setOf("manifest.json", "certs/developer-public-key.pem", "signatures/developer.sig")
            else setOf("manifest.json")) + declared
        require(names.toSet() == expected) { "Unexpected or missing package entries" }
        for (item in files) {
            val f = item as Map<*, *>; require(sha(requireNotNull(data[f.text("path")])) == f["sha256"]) { "Payload integrity failure" }
        }
        if (!signed) return manifest
        val pem = String(requireNotNull(data["certs/developer-public-key.pem"]), Charsets.US_ASCII)
        val public = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(decodePem(pem, "PUBLIC KEY")))
        if (expectedKey != null) require(public.encoded.contentEquals(expectedKey.encoded)) { "Wrong developer key" }
        val signing = manifest["signing"] as Map<*, *>
        require(signing["developerSignatureAlgorithm"] == "Ed25519" && signing["marketplaceSignatureAlgorithm"] == "Ed25519" && signing["marketplaceKeyId"] == "market_root_v1")
        require(signing["developerKeySha256"] == PfpSigner.developerKeySha256(pem)) { "Certificate fingerprint mismatch" }
        val verifier = Signature.getInstance("Ed25519"); verifier.initVerify(public); verifier.update(manifestBytes)
        require(verifier.verify(Base64.getDecoder().decode(requireNotNull(data["signatures/developer.sig"])))) { "Invalid developer signature" }
        return manifest
    }
}

private fun decodePem(text: String, label: String): ByteArray {
    require(text.trim().startsWith("-----BEGIN $label-----") && text.trim().endsWith("-----END $label-----")) { "Invalid $label PEM" }
    return Base64.getDecoder().decode(text.replace("-----BEGIN $label-----", "").replace("-----END $label-----", "").filterNot { it.isWhitespace() })
}
fun readKey(directory: File): PfpSigner.DeveloperKey {
    require(directory.isAbsolute) { "Use an absolute signing directory outside Git" }
    require(generateSequence(directory.canonicalFile.toPath()) { it.parent }.none { Files.exists(it.resolve(".git")) }) { "Signing keys must be outside every Git checkout" }
    require(directory.isDirectory) { "Missing signing directory; use createSigningKey once" }
    fun read(name: String): String { val f = directory.resolve(name); require(f.isFile) { "Missing signing file: $name" }; return f.readText(Charsets.US_ASCII) }
    val id = read("developer-key-id.txt").trim(); require(id.matches(Regex("[A-Za-z0-9._-]{1,128}")))
    val factory = KeyFactory.getInstance("Ed25519")
    val key = PfpSigner.DeveloperKey(id, factory.generatePrivate(PKCS8EncodedKeySpec(decodePem(read("developer-private.pem"), "PRIVATE KEY"))),
        factory.generatePublic(X509EncodedKeySpec(decodePem(read("developer-public.pem"), "PUBLIC KEY"))))
    PfpSigner.sign("PureFusion signing configuration check".toByteArray(), key)
    return key
}
fun createKey(directory: File, project: File) {
    require(directory.isAbsolute) { "Use an absolute signing directory outside Git" }
    val target = directory.canonicalFile.toPath(); val root = project.canonicalFile.toPath()
    require(!target.startsWith(root)) { "Signing keys must be outside the project checkout" }
    require(generateSequence(target) { it.parent }.none { Files.exists(it.resolve(".git")) }) {
        "Signing keys must be outside every Git checkout (including enclosing SDKs and linked worktrees)"
    }
    require(!Files.exists(target)) { "Signing directory already exists; refusing to overwrite or generate another key" }
    Files.createDirectories(target)
    val posix = Files.getFileAttributeView(target, PosixFileAttributeView::class.java)
    if (posix != null) Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwx------"))
    val acl = Files.getFileAttributeView(target, AclFileAttributeView::class.java)
    if (acl != null) {
        val owner = Files.getOwner(target)
        acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(*AclEntryPermission.entries.toTypedArray())
            .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build())
    }
    require(posix != null || acl != null) { "Filesystem cannot restrict key access; use a protected filesystem" }
    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val privatePem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n"
    for ((name, text) in mapOf("developer-private.pem" to privatePem, "developer-public.pem" to PfpSigner.publicKeyPem(pair.public), "developer-key-id.txt" to "developer-${UUID.randomUUID()}\n")) {
        val path = target.resolve(name)
        Files.writeString(path, text, StandardOpenOption.CREATE_NEW)
        if (posix != null) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
    }
}
