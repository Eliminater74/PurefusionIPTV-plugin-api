package dev.eliminater.purefusioniptv.plugin.packaging

import dev.eliminater.purefusioniptv.plugin.api.PluginApi
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `.pfp` production.
 *
 * The package this writes has to be one the host's non-executing inspector accepts, so the entry
 * layout and manifest field names are checked literally rather than round-tripped through a
 * helper that could drift with them.
 */
class PfpPackagerTest {

    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("pfp-packager").toFile()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    private fun request(
        pluginId: String = "dev.example.myplugin",
        apiLevel: Int = PluginApi.CURRENT,
        payload: List<PfpPackager.PayloadSource> = listOf(
            PfpPackager.PayloadSource("classes.dex", byteArrayOf(1, 2, 3, 4)),
        ),
        version: String = "1.0.0",
    ) = PfpPackager.Request(
        pluginId = pluginId,
        displayName = "My Plugin",
        developerId = "dev.example",
        version = version,
        versionCode = 1,
        apiLevel = apiLevel,
        minHostVersion = "2.0.0",
        minSdk = 25,
        entryPointClassName = "dev.example.myplugin.MyPlugin",
        payload = payload,
    )

    private fun entries(file: File): Map<String, ByteArray> =
        ZipFile(file).use { zip ->
            zip.entries().asSequence().associate { entry ->
                entry.name to zip.getInputStream(entry).readBytes()
            }
        }

    // ── 12-14. Output shape ───────────────────────────────────────────────────

    @Test
    fun `the packager emits a pfp with the canonical name`() {
        val output = PfpPackager.build(request(), workDir)
        assertTrue(output.isFile)
        assertEquals("dev.example.myplugin-1.0.0.pfp", output.name)
        assertTrue(output.name.endsWith(".pfp"))
    }

    @Test
    fun `the package contains the manifest and payload at the expected paths`() {
        val entries = entries(PfpPackager.build(request(), workDir))
        assertTrue("manifest must be at the root", entries.containsKey("manifest.json"))
        assertTrue("payload must be under payload/", entries.containsKey("payload/classes.dex"))
    }

    @Test
    fun `the manifest declares identity, api level and entry point`() {
        val manifest = entries(PfpPackager.build(request(), workDir))
            .getValue("manifest.json")
            .toString(Charsets.UTF_8)
        listOf(
            "\"pluginId\": \"dev.example.myplugin\"",
            "\"apiLevel\": ${PluginApi.CURRENT}",
            "\"schemaVersion\": 1",
            "\"versionCode\": 1",
            "\"minHostVersion\": \"2.0.0\"",
            "\"className\": \"dev.example.myplugin.MyPlugin\"",
            "\"hashAlgorithm\": \"SHA-256\"",
        ).forEach { field ->
            assertTrue("manifest must contain $field\n$manifest", manifest.contains(field))
        }
    }

    // ── 15. Hashes ────────────────────────────────────────────────────────────

    @Test
    fun `payload hashes match the bytes actually written`() {
        val bytes = byteArrayOf(9, 8, 7, 6, 5)
        val output = PfpPackager.build(
            request(payload = listOf(PfpPackager.PayloadSource("classes.dex", bytes))),
            workDir,
        )
        val packaged = entries(output)
        val manifest = packaged.getValue("manifest.json").toString(Charsets.UTF_8)
        val expected = PfpPackager.sha256Hex(packaged.getValue("payload/classes.dex"))

        assertEquals(PfpPackager.sha256Hex(bytes), expected)
        assertTrue(
            "the manifest must record the real payload hash",
            manifest.contains("\"sha256\": \"$expected\""),
        )
        assertTrue(manifest.contains("\"path\": \"payload/classes.dex\""))
    }

    @Test
    fun `identical inputs produce identical bytes`() {
        // Reproducibility is what makes "did the payload change?" answerable by comparing files.
        val first = PfpPackager.build(request(), File(workDir, "a"))
        val second = PfpPackager.build(request(), File(workDir, "b"))
        assertTrue(first.readBytes().contentEquals(second.readBytes()))
    }

    // ── 16-17. Unsafe archives cannot be produced ─────────────────────────────

    @Test
    fun `path traversal is refused`() {
        listOf("../evil.dex", "a/../../evil.dex", "./evil.dex", "/abs.dex", "C:/abs.dex")
            .forEach { path ->
                val error = runCatching {
                    PfpPackager.build(
                        request(payload = listOf(PfpPackager.PayloadSource(path, byteArrayOf(1)))),
                        workDir,
                    )
                }.exceptionOrNull()
                assertTrue("$path must be refused", error is PfpPackager.PackagingException)
            }
    }

    @Test
    fun `backslashes and empty segments are refused`() {
        listOf("dir\\evil.dex", "dir//evil.dex", "dir/").forEach { path ->
            val error = runCatching {
                PfpPackager.build(
                    request(payload = listOf(PfpPackager.PayloadSource(path, byteArrayOf(1)))),
                    workDir,
                )
            }.exceptionOrNull()
            assertTrue("$path must be refused", error is PfpPackager.PackagingException)
        }
    }

    @Test
    fun `duplicate payload paths are refused, including case-only differences`() {
        val duplicate = runCatching {
            PfpPackager.build(
                request(
                    payload = listOf(
                        PfpPackager.PayloadSource("classes.dex", byteArrayOf(1)),
                        PfpPackager.PayloadSource("classes.dex", byteArrayOf(2)),
                    ),
                ),
                workDir,
            )
        }.exceptionOrNull()
        assertTrue(duplicate is PfpPackager.PackagingException)

        val caseOnly = runCatching {
            PfpPackager.build(
                request(
                    payload = listOf(
                        PfpPackager.PayloadSource("classes.dex", byteArrayOf(1)),
                        PfpPackager.PayloadSource("Classes.DEX", byteArrayOf(2)),
                    ),
                ),
                workDir,
            )
        }.exceptionOrNull()
        assertTrue(caseOnly is PfpPackager.PackagingException)
    }

    // ── 18-20. Invalid requests fail loudly ───────────────────────────────────

    @Test
    fun `an invalid plugin id is refused`() {
        listOf("", "nodots", "1bad.start", "bad..segment", "has space.plugin")
            .forEach { id ->
                val error = runCatching { PfpPackager.build(request(pluginId = id), workDir) }
                    .exceptionOrNull()
                assertTrue("'$id' must be refused", error is PfpPackager.PackagingException)
            }
    }

    @Test
    fun `a missing api level is refused with actionable text`() {
        val error = runCatching {
            PfpPackager.build(request(apiLevel = PluginApi.LEVEL_UNSPECIFIED), workDir)
        }.exceptionOrNull()
        assertTrue(error is PfpPackager.PackagingException)
        assertTrue(
            "the message must tell the developer what to set",
            error!!.message!!.contains("PluginApi.CURRENT"),
        )
    }

    @Test
    fun `an empty payload is refused`() {
        val error = runCatching { PfpPackager.build(request(payload = emptyList()), workDir) }
            .exceptionOrNull()
        assertTrue(error is PfpPackager.PackagingException)
    }

    @Test
    fun `a missing entry point is refused`() {
        val error = runCatching {
            PfpPackager.build(request().copy(entryPointClassName = "  "), workDir)
        }.exceptionOrNull()
        assertTrue(error is PfpPackager.PackagingException)
    }

    @Test
    fun `a rejected build writes nothing`() {
        val out = File(workDir, "rejected")
        runCatching { PfpPackager.build(request(pluginId = "bad"), out) }
        assertTrue("no partial artifact may be left behind", out.listFiles().isNullOrEmpty())
    }

    // ── 22. The host owns plugin-api classes ──────────────────────────────────

    @Test
    fun `bundling plugin-api bytecode into the payload is refused`() {
        // Two copies of Plugin loaded by different classloaders are different JVM types; catching
        // it here turns a baffling runtime ClassCastException into a build error.
        val error = runCatching {
            PfpPackager.build(
                request(
                    payload = listOf(
                        PfpPackager.PayloadSource(
                            "dev/eliminater/purefusioniptv/plugin/api/Plugin.class",
                            byteArrayOf(1),
                        ),
                    ),
                ),
                workDir,
            )
        }.exceptionOrNull()
        assertTrue(error is PfpPackager.PackagingException)
        assertTrue(error!!.message!!.contains("plugin-api"))
    }

    @Test
    fun `ordinary plugin classes are allowed`() {
        val output = PfpPackager.build(
            request(
                payload = listOf(
                    PfpPackager.PayloadSource("dev/example/myplugin/MyPlugin.class", byteArrayOf(1)),
                ),
            ),
            workDir,
        )
        assertNotNull(entries(output)["payload/dev/example/myplugin/MyPlugin.class"])
    }
}
