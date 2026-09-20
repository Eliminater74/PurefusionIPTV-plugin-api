package dev.eliminater.purefusioniptv.plugin.packaging

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The packager must build from a standalone plugin checkout.
 *
 * A plugin repository has this SDK and its own sources — it does not have the PureFusionIPTV core
 * tree, and it may not even apply the Android Gradle Plugin. Any dependency on either would make
 * `.pfp` production impossible outside the monorepo, which is the one thing this tooling exists to
 * avoid. That is a property of the source, so it is checked against the source.
 */
class PfpPackagerStandaloneTest {

    private val source: String by lazy {
        listOf(
            File("src/main/java/dev/eliminater/purefusioniptv/plugin/packaging/PfpPackager.kt"),
            File("plugin-api/src/main/java/dev/eliminater/purefusioniptv/plugin/packaging/PfpPackager.kt"),
        ).firstOrNull { it.isFile }
            ?.readText()
            ?.replace("\r\n", "\n")
            ?: error("PfpPackager.kt not found from ${File(".").absolutePath}")
    }

    private val imports: List<String> by lazy {
        source.lines().filter { it.trimStart().startsWith("import ") }
    }

    @Test
    fun theePackagerImportsNothingFromTheCoreApp() {
        imports.forEach { line ->
            assertFalse(
                "packaging must not depend on the core app — found: $line",
                line.contains("dev.eliminater.purefusioniptv.") &&
                    !line.contains("dev.eliminater.purefusioniptv.plugin.api."),
            )
        }
    }

    @Test
    fun thePackagerImportsNothingFromAndroid() {
        imports.forEach { line ->
            assertFalse(
                "packaging must run on a plain JVM — found: $line",
                line.contains("android.") || line.contains("androidx."),
            )
        }
    }

    @Test
    fun thePackagerUsesOnlyTheJdkAndTheApiModule() {
        val allowedPrefixes = listOf(
            "import java.",
            "import javax.",
            "import kotlin.",
            "import kotlinx.",
            "import dev.eliminater.purefusioniptv.plugin.api.",
        )
        imports.forEach { line ->
            assertTrue(
                "unexpected dependency in the packager: $line",
                allowedPrefixes.any { line.trimStart().startsWith(it) },
            )
        }
    }

    @Test
    fun theCanonicalExtensionMatchesTheHost() {
        // The host's PluginPackageFormat.EXTENSION is the other half of this contract. They are in
        // different repositories, so the value is pinned on both sides rather than shared.
        assertTrue(PfpPackager.EXTENSION == "pfp")
        assertTrue(PfpPackager.packageFileName("dev.example.p", "1.2.3").endsWith(".pfp"))
    }

    @Test
    fun theArchiveLayoutMatchesTheHostInspector() {
        // These four names are what the host's PluginPackageParser reads. A change on either side
        // without the other produces packages that validate locally and fail on device.
        assertTrue(PfpPackager.MANIFEST_ENTRY == "manifest.json")
        assertTrue(PfpPackager.PAYLOAD_PREFIX == "payload/")
        assertTrue(PfpPackager.DEVELOPER_SIGNATURE_ENTRY == "signatures/developer.sig")
        assertTrue(PfpPackager.MARKETPLACE_SIGNATURE_ENTRY == "signatures/marketplace.sig")
        assertTrue(PfpPackager.DEVELOPER_PUBLIC_KEY_ENTRY == "certs/developer-public-key.pem")
    }
}
