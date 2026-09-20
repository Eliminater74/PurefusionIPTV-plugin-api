package dev.eliminater.purefusioniptv.plugin.packaging

import dev.eliminater.purefusioniptv.plugin.api.PluginApi
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a canonical `.pfp` plugin package.
 *
 * ## Standalone by construction
 *
 * This uses the JDK only — `java.io`, `java.util.zip`, `java.security`. It imports nothing from
 * Android and nothing from the PureFusionIPTV core app, because a standalone plugin repository has
 * neither. A plugin project depends on this SDK and produces a package from a clean checkout of
 * itself; it never needs the core source tree. `PfpPackagerStandaloneTest` enforces that.
 *
 * ## The layout is the host's, not a new one
 *
 * Entry names mirror exactly what the production installer reads, so a package built here is one
 * the host already knows how to inspect:
 *
 * ```
 * manifest.json                      ← schema + identity + apiLevel + payload hashes
 * payload/<declared files>           ← the executable artifacts
 * certs/developer-public-key.pem     ← optional, signing only
 * signatures/developer.sig           ← optional, signing only
 * signatures/marketplace.sig         ← optional, marketplace counter-signature
 * ```
 *
 * ## Integrity, identity, trust
 *
 * This produces **integrity** only: SHA-256 over every payload file, recorded in the manifest, so
 * the host can detect a changed payload. It does not sign, and it does not decide trust.
 *
 * *Identity* (which key signed it) and *trust* (whether the host honours that key) are separate
 * steps that need a real developer key, which this deliberately does not invent or embed. An
 * unsigned package is a **development** package: usable where the host permits sideload, refused
 * where signatures are required. Signing is future work and the format already carries the fields
 * for it.
 */
object PfpPackager {

    /** Canonical extension, matching the host's `PluginPackageFormat`. */
    const val EXTENSION: String = "pfp"

    const val MANIFEST_ENTRY: String = "manifest.json"
    const val PAYLOAD_PREFIX: String = "payload/"
    const val DEVELOPER_SIGNATURE_ENTRY: String = "signatures/developer.sig"
    const val MARKETPLACE_SIGNATURE_ENTRY: String = "signatures/marketplace.sig"
    const val DEVELOPER_PUBLIC_KEY_ENTRY: String = "certs/developer-public-key.pem"

    /**
     * Fixed timestamp for every entry.
     *
     * ZIP records a modification time per entry, so packaging the same inputs twice would
     * otherwise produce different bytes and make "did the payload actually change?" unanswerable
     * by comparing files. Pinning it makes output reproducible for the same inputs.
     */
    private const val FIXED_ENTRY_TIME_MS: Long = 0L

    /** One file destined for `payload/`. */
    data class PayloadSource(
        /** Path *relative to* `payload/`, e.g. `classes.dex`. */
        val relativePath: String,
        val bytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is PayloadSource &&
                other.relativePath == relativePath &&
                other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * relativePath.hashCode() + bytes.contentHashCode()
    }

    /** Everything needed to build a package. */
    data class Request(
        val pluginId: String,
        val displayName: String,
        val developerId: String,
        val version: String,
        val versionCode: Int,
        val apiLevel: Int,
        val minHostVersion: String,
        val minSdk: Int,
        val entryPointClassName: String,
        val payload: List<PayloadSource>,
        val schemaVersion: Int = 1,
        val category: String = "runtime_extension",
        val installType: String = "executable_bundle",
        val trustTier: String = "UNSIGNED_DEV",
        val capabilities: List<String> = emptyList(),
        val developerKeyId: String = "unsigned-dev",
    )

    class PackagingException(message: String) : IllegalArgumentException(message)

    /** Canonical output name: `<pluginId>-<version>.pfp`. */
    fun packageFileName(pluginId: String, version: String): String =
        "$pluginId-$version.$EXTENSION"

    /**
     * Validates [request] and writes the package to [outputDir].
     *
     * Every failure is raised before a byte is written, so a rejected build leaves no partial
     * artifact behind.
     */
    fun build(request: Request, outputDir: File): File {
        validate(request)

        val hashes = request.payload.associate { source ->
            source.relativePath to sha256Hex(source.bytes)
        }
        val manifestJson = buildManifestJson(request, hashes)

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw PackagingException("Could not create output directory: ${outputDir.path}")
        }
        val output = File(outputDir, packageFileName(request.pluginId, request.version))

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeEntry(zip, MANIFEST_ENTRY, manifestJson.toByteArray(Charsets.UTF_8))
            // Sorted so entry order is a function of the inputs, not of map iteration.
            request.payload.sortedBy { it.relativePath }.forEach { source ->
                writeEntry(zip, PAYLOAD_PREFIX + source.relativePath, source.bytes)
            }
        }
        return output
    }

    /**
     * Everything that makes a package unbuildable, checked up front.
     *
     * These mirror the host's own structural validation. Failing here means a developer learns at
     * build time rather than from an install rejection on a TV.
     */
    fun validate(request: Request) {
        if (!isValidPluginId(request.pluginId)) {
            throw PackagingException(
                "Invalid plugin id '${request.pluginId}': expected reverse-DNS such as " +
                    "dev.example.myplugin",
            )
        }
        if (request.apiLevel <= PluginApi.LEVEL_UNSPECIFIED) {
            throw PackagingException(
                "Missing apiLevel. Set it to PluginApi.CURRENT (${PluginApi.CURRENT}); the host " +
                    "refuses packages that do not declare one.",
            )
        }
        if (request.displayName.isBlank()) throw PackagingException("Missing displayName")
        if (request.developerId.isBlank()) throw PackagingException("Missing developerId")
        if (request.version.isBlank()) throw PackagingException("Missing version")
        if (request.versionCode <= 0) throw PackagingException("versionCode must be positive")
        if (request.minHostVersion.isBlank()) throw PackagingException("Missing minHostVersion")
        if (request.minSdk <= 0) throw PackagingException("minSdk must be positive")
        if (request.entryPointClassName.isBlank()) {
            throw PackagingException("Missing entry point class name")
        }
        if (request.payload.isEmpty()) {
            throw PackagingException("A package must contain at least one payload file")
        }

        val seen = HashSet<String>()
        val seenFolded = HashSet<String>()
        request.payload.forEach { source ->
            val path = source.relativePath
            assertSafeRelativePath(path)
            if (!seen.add(path)) {
                throw PackagingException("Duplicate payload path: $path")
            }
            // The host rejects case-folded duplicates too; a case-insensitive filesystem would
            // otherwise let two declared entries collide on extraction.
            if (!seenFolded.add(path.lowercase(Locale.US))) {
                throw PackagingException("Duplicate payload path differing only by case: $path")
            }
            if (source.bytes.isEmpty()) {
                throw PackagingException("Empty payload file: $path")
            }
            if (isHostOwnedApiEntry(path)) {
                throw PackagingException(
                    "Payload must not bundle plugin-api classes ($path). The host owns those " +
                        "types; a bundled copy loads under a different classloader and is a " +
                        "different JVM type, which breaks every cast across the boundary.",
                )
            }
        }
    }

    /**
     * Refuses anything that could escape `payload/` on extraction, or that a ZIP reader might
     * normalise differently from the writer.
     */
    private fun assertSafeRelativePath(path: String) {
        if (path.isBlank()) throw PackagingException("Empty payload path")
        if (path.contains('\\')) {
            throw PackagingException("Payload path must use '/' separators: $path")
        }
        if (path.startsWith("/")) {
            throw PackagingException("Payload path must be relative: $path")
        }
        if (path.contains("//")) {
            throw PackagingException("Payload path must not contain empty segments: $path")
        }
        if (path.endsWith("/")) {
            throw PackagingException("Payload path must not be a directory: $path")
        }
        if (path.split('/').any { it == "." || it == ".." }) {
            throw PackagingException("Path traversal is not allowed in payload path: $path")
        }
        if (path.any { it.isISOControl() }) {
            throw PackagingException("Control characters are not allowed in payload path: $path")
        }
        // A drive-letter or UNC-looking prefix is meaningless inside the archive and is a sign the
        // caller passed an absolute host path by mistake.
        if (path.length >= 2 && path[1] == ':') {
            throw PackagingException("Payload path must be relative: $path")
        }
    }

    /** Reverse-DNS, mirroring the host's `PluginArchiveSafety.isValidPluginId`. */
    fun isValidPluginId(pluginId: String): Boolean {
        if (pluginId.isBlank() || pluginId.length > MAX_PLUGIN_ID_LENGTH) return false
        val segments = pluginId.split('.')
        if (segments.size < 2) return false
        return segments.all { segment ->
            segment.isNotEmpty() &&
                segment.first().isLetter() &&
                segment.all { it.isLetterOrDigit() || it == '_' }
        }
    }

    /** True when an entry would duplicate a class the host must own. */
    fun isHostOwnedApiEntry(relativePath: String): Boolean {
        val asClass = relativePath.removeSuffix(".class").replace('/', '.')
        return asClass.startsWith("dev.eliminater.purefusioniptv.plugin.api.")
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        entry.time = FIXED_ENTRY_TIME_MS
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun buildManifestJson(request: Request, hashes: Map<String, String>): String {
        val payloadEntries = request.payload
            .sortedBy { it.relativePath }
            .joinToString(",\n") { source ->
                """        {
          "path": "${PAYLOAD_PREFIX}${escape(source.relativePath)}",
          "sha256": "${hashes.getValue(source.relativePath)}"
        }"""
            }
        val capabilityEntries = request.capabilities.joinToString(", ") { "\"${escape(it)}\"" }
        return """{
  "schemaVersion": ${request.schemaVersion},
  "apiLevel": ${request.apiLevel},
  "pluginId": "${escape(request.pluginId)}",
  "displayName": "${escape(request.displayName)}",
  "developerId": "${escape(request.developerId)}",
  "version": "${escape(request.version)}",
  "versionCode": ${request.versionCode},
  "category": "${escape(request.category)}",
  "installType": "${escape(request.installType)}",
  "trustTier": "${escape(request.trustTier)}",
  "minHostVersion": "${escape(request.minHostVersion)}",
  "minSdk": ${request.minSdk},
  "entrypoints": [
    { "type": "PLUGIN", "className": "${escape(request.entryPointClassName)}" }
  ],
  "capabilities": [$capabilityEntries],
  "payload": {
    "hashAlgorithm": "SHA-256",
    "files": [
$payloadEntries
    ]
  },
  "signing": {
    "developerKeyId": "${escape(request.developerKeyId)}",
    "developerSignatureAlgorithm": "Ed25519"
  }
}
"""
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private const val MAX_PLUGIN_ID_LENGTH = 128
}
