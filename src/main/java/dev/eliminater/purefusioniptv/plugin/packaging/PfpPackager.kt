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
 * [build] produces **integrity** only: SHA-256 over every payload file, recorded in the manifest,
 * so the host can detect a changed payload. It does not sign, and it does not decide trust. An
 * unsigned package is a **development** package: usable where the host permits sideload, refused
 * where signatures are required.
 *
 * *Identity* (which key signed it) is [PfpSigner]: it fills [Request.signing] from a real developer
 * key and signs the exact manifest bytes this object produces. *Trust* (whether the host honours
 * that key) is decided by the host and the Marketplace, never by the package.
 *
 * ## Entry point spelling
 *
 * The manifest's `entrypoints[].type` is the lowercase wire value `plugin`, the same spelling the
 * host's `PluginEntrypointType` serializes and the Marketplace verifier accepts. Packages built
 * before F5.3.6 wrote `PLUGIN`. The Android host tolerated that only through Gson's enum-name
 * fallback, and the Marketplace verifier rejects it.
 */
object PfpPackager {
    /** Canonical extension, matching the host's `PluginPackageFormat`. */
    const val EXTENSION: String = "pfp"

    const val MANIFEST_ENTRY: String = "manifest.json"
    const val PAYLOAD_PREFIX: String = "payload/"
    const val DEVELOPER_SIGNATURE_ENTRY: String = "signatures/developer.sig"
    const val MARKETPLACE_SIGNATURE_ENTRY: String = "signatures/marketplace.sig"
    const val DEVELOPER_PUBLIC_KEY_ENTRY: String = "certs/developer-public-key.pem"

    /** Canonical `entrypoints[].type` for a plugin class, matching the host's wire value. */
    const val ENTRYPOINT_TYPE_PLUGIN: String = "plugin"

    /** The only developer signature algorithm this SDK produces. */
    const val SIGNATURE_ALGORITHM_ED25519: String = "Ed25519"

    /** Development tier. A signed package must declare a Marketplace tier instead. */
    const val TRUST_TIER_UNSIGNED_DEV: String = "UNSIGNED_DEV"

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
        /** Present only on a developer-signed package. [PfpSigner] fills it; callers do not. */
        val signing: Signing? = null,
    )

    /**
     * Signing identity committed inside the signed manifest.
     *
     * The Marketplace signs these exact manifest bytes after review, so [developerKeySha256] binds
     * the developer certificate that sits *outside* `manifest.json` to the approved release.
     */
    data class Signing(
        val developerKeyId: String,
        /** SHA-256 of the whitespace-free public PEM Base64 body as US-ASCII, lowercase hex. */
        val developerKeySha256: String,
        val marketplaceKeyId: String,
        val developerSignatureAlgorithm: String = SIGNATURE_ALGORITHM_ED25519,
        val marketplaceSignatureAlgorithm: String = SIGNATURE_ALGORITHM_ED25519,
    )

    class PackagingException(
        message: String,
    ) : IllegalArgumentException(message)

    /** Canonical output name: `<pluginId>-<version>.pfp`. */
    fun packageFileName(
        pluginId: String,
        version: String,
    ): String = "$pluginId-$version.$EXTENSION"

    /**
     * Validates [request] and writes the package to [outputDir].
     *
     * Every failure is raised before a byte is written, so a rejected build leaves no partial
     * artifact behind.
     */
    fun build(
        request: Request,
        outputDir: File,
    ): File {
        if (request.signing != null) {
            throw PackagingException("A signed package is built by PfpSigner, which signs it")
        }
        return writePackage(request, manifestBytes(request), outputDir, emptyMap())
    }

    /**
     * The exact `manifest.json` bytes for [request], after validation.
     *
     * A developer signature covers these bytes verbatim, so they are produced in one place.
     */
    fun manifestBytes(request: Request): ByteArray {
        validate(request)
        val hashes =
            request.payload.associate { source ->
                source.relativePath to sha256Hex(source.bytes)
            }
        return buildManifestJson(request, hashes).toByteArray(Charsets.UTF_8)
    }

    /**
     * Writes the manifest, the sorted payload, then [metadata] entries in their given order.
     *
     * Shared by [build] and [PfpSigner], so a signed package differs from an unsigned one only in
     * its manifest and the added certificate and signature entries.
     */
    internal fun writePackage(
        request: Request,
        manifestBytes: ByteArray,
        outputDir: File,
        metadata: Map<String, ByteArray>,
    ): File {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw PackagingException("Could not create output directory: ${outputDir.path}")
        }
        val output = File(outputDir, packageFileName(request.pluginId, request.version))

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeEntry(zip, MANIFEST_ENTRY, manifestBytes)
            // Sorted so entry order is a function of the inputs, not of map iteration.
            request.payload.sortedBy { it.relativePath }.forEach { source ->
                writeEntry(zip, PAYLOAD_PREFIX + source.relativePath, source.bytes)
            }
            metadata.forEach { (name, bytes) -> writeEntry(zip, name, bytes) }
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
        request.signing?.let { validateSigning(request, it) }

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
     * A signed manifest must name a Marketplace tier and well-formed key identities. The
     * Marketplace refuses anything else, and signing an unpublishable manifest wastes a release.
     */
    private fun validateSigning(
        request: Request,
        signing: Signing,
    ) {
        if (request.trustTier == TRUST_TIER_UNSIGNED_DEV || request.trustTier == "local_untrusted") {
            throw PackagingException(
                "A signed package must declare a Marketplace trust tier, not ${request.trustTier}",
            )
        }
        if (!KEY_ID.matches(signing.developerKeyId) || signing.developerKeyId == "unsigned-dev") {
            throw PackagingException("Invalid developer key id '${signing.developerKeyId}'")
        }
        if (signing.developerKeyId != request.developerKeyId) {
            throw PackagingException("Signing developer key id differs from the request")
        }
        if (!SHA256_HEX.matches(signing.developerKeySha256)) {
            throw PackagingException("developerKeySha256 must be 64 lowercase hex characters")
        }
        if (!KEY_ID.matches(signing.marketplaceKeyId)) {
            throw PackagingException("Invalid Marketplace key id '${signing.marketplaceKeyId}'")
        }
        if (signing.developerSignatureAlgorithm != SIGNATURE_ALGORITHM_ED25519) {
            throw PackagingException("This SDK signs with Ed25519 only")
        }
        if (signing.marketplaceSignatureAlgorithm !in MARKETPLACE_ALGORITHMS) {
            throw PackagingException(
                "Unsupported Marketplace signature algorithm " +
                    "'${signing.marketplaceSignatureAlgorithm}'",
            )
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
        return segments.size >= 2 &&
            segments.all { segment ->
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
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        bytes: ByteArray,
    ) {
        val entry = ZipEntry(name)
        entry.time = FIXED_ENTRY_TIME_MS
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun buildManifestJson(
        request: Request,
        hashes: Map<String, String>,
    ): String {
        val payloadEntries =
            request.payload
                .sortedBy { it.relativePath }
                .joinToString(",\n") { source ->
                    """        {
          "path": "${PAYLOAD_PREFIX}${escape(source.relativePath)}",
          "sha256": "${hashes.getValue(source.relativePath)}"
        }"""
                }
        val capabilityEntries = request.capabilities.joinToString(", ") { "\"${escape(it)}\"" }
        val signing = request.signing
        val signingEntries =
            if (signing == null) {
                listOf(
                    "developerKeyId" to request.developerKeyId,
                    "developerSignatureAlgorithm" to SIGNATURE_ALGORITHM_ED25519,
                )
            } else {
                listOf(
                    "developerKeyId" to signing.developerKeyId,
                    "developerKeySha256" to signing.developerKeySha256,
                    "developerSignatureAlgorithm" to signing.developerSignatureAlgorithm,
                    "marketplaceKeyId" to signing.marketplaceKeyId,
                    "marketplaceSignatureAlgorithm" to signing.marketplaceSignatureAlgorithm,
                )
            }.joinToString(",\n") { (name, value) -> "    \"$name\": \"${escape(value)}\"" }
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
    { "type": "$ENTRYPOINT_TYPE_PLUGIN", "className": "${escape(request.entryPointClassName)}" }
  ],
  "capabilities": [$capabilityEntries],
  "payload": {
    "hashAlgorithm": "SHA-256",
    "files": [
$payloadEntries
    ]
  },
  "signing": {
$signingEntries
  }
}
"""
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private const val MAX_PLUGIN_ID_LENGTH = 128

    /** Key id shape the Marketplace backend accepts. */
    private val KEY_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val SHA256_HEX = Regex("^[a-f0-9]{64}$")
    private val MARKETPLACE_ALGORITHMS = setOf(SIGNATURE_ALGORITHM_ED25519, "SHA256withRSA")
}
