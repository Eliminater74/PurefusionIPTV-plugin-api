package dev.eliminater.purefusioniptv.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compatibility rule the host applies before it loads any plugin code.
 *
 * This is the contract external developers build against, so its behaviour is pinned here in the
 * module that publishes it rather than only in the app that consumes it.
 */
class PluginApiCompatibilityTest {

    private fun evaluate(
        declared: Int,
        hostCurrent: Int = 3,
        hostMin: Int = 2,
    ) = PluginApiCompatibilityCheck.evaluate(declared, hostCurrent, hostMin)

    // ── 8. Compatible ─────────────────────────────────────────────────────────

    @Test
    fun `a level inside the supported range is compatible`() {
        listOf(2, 3).forEach { level ->
            assertEquals(
                "level $level",
                PluginApiCompatibility.COMPATIBLE,
                evaluate(level),
            )
        }
        assertTrue(evaluate(3).isCompatible)
    }

    @Test
    fun `a plugin built against the current level is compatible by default`() {
        assertEquals(
            PluginApiCompatibility.COMPATIBLE,
            PluginApiCompatibilityCheck.evaluate(PluginApi.CURRENT),
        )
    }

    // ── 9. Incompatible ───────────────────────────────────────────────────────

    @Test
    fun `a level below the supported floor is too old`() {
        assertEquals(PluginApiCompatibility.TOO_OLD, evaluate(1))
        assertFalse(evaluate(1).isCompatible)
    }

    @Test
    fun `a level above what the host publishes is too new`() {
        assertEquals(PluginApiCompatibility.TOO_NEW, evaluate(4))
        assertFalse(evaluate(4).isCompatible)
    }

    @Test
    fun `an undeclared level is refused rather than guessed`() {
        // Plugins written before the level existed default to LEVEL_UNSPECIFIED. Assuming a level
        // for them would mean linking code against a contract nobody checked.
        assertEquals(PluginApiCompatibility.UNSPECIFIED, evaluate(PluginApi.LEVEL_UNSPECIFIED))
        assertEquals(PluginApiCompatibility.UNSPECIFIED, evaluate(0))
        assertEquals(PluginApiCompatibility.UNSPECIFIED, evaluate(-1))
    }

    @Test
    fun `only COMPATIBLE permits loading`() {
        val compatible = PluginApiCompatibility.entries.filter { it.isCompatible }
        assertEquals(listOf(PluginApiCompatibility.COMPATIBLE), compatible)
    }

    // ── The published constants ───────────────────────────────────────────────

    @Test
    fun `the published levels are coherent`() {
        assertTrue("the floor cannot exceed the current level", PluginApi.MIN_SUPPORTED <= PluginApi.CURRENT)
        assertTrue("levels start above unspecified", PluginApi.CURRENT > PluginApi.LEVEL_UNSPECIFIED)
        assertEquals(0, PluginApi.LEVEL_UNSPECIFIED)
    }

    @Test
    fun `the default Plugin declares no api level`() {
        // A plugin that has not opted in must not be silently treated as current.
        val plugin = object : Plugin {
            override val id = "dev.example.plugin"
            override val name = "Example"
            override val description = "Example"
            override val version = "1.0.0"
            override val author = "Example"
            override val minAppVersion = "1.0.0"
            override val capabilities = emptyList<PluginCapability>()
            override fun onLoad(context: android.content.Context) = Unit
            override fun onUnload() = Unit
        }
        assertEquals(PluginApi.LEVEL_UNSPECIFIED, plugin.apiLevel)
        assertEquals(
            PluginApiCompatibility.UNSPECIFIED,
            PluginApiCompatibilityCheck.evaluate(plugin.apiLevel),
        )
    }

    @Test
    fun `a plugin can declare a level`() {
        val plugin = object : Plugin {
            override val id = "dev.example.plugin"
            override val name = "Example"
            override val description = "Example"
            override val version = "1.0.0"
            override val author = "Example"
            override val minAppVersion = "1.0.0"
            override val apiLevel = PluginApi.CURRENT
            override val capabilities = emptyList<PluginCapability>()
            override fun onLoad(context: android.content.Context) = Unit
            override fun onUnload() = Unit
        }
        assertEquals(
            PluginApiCompatibility.COMPATIBLE,
            PluginApiCompatibilityCheck.evaluate(plugin.apiLevel),
        )
    }

    // ── Explanations ──────────────────────────────────────────────────────────

    @Test
    fun `each refusal says which side needs updating`() {
        assertTrue(
            PluginApiCompatibilityCheck.explain(PluginApiCompatibility.TOO_OLD, 1, 3, 2)
                .contains("Update the plugin"),
        )
        assertTrue(
            PluginApiCompatibilityCheck.explain(PluginApiCompatibility.TOO_NEW, 4, 3, 2)
                .contains("Update PureFusionIPTV"),
        )
        assertTrue(
            PluginApiCompatibilityCheck.explain(PluginApiCompatibility.UNSPECIFIED, 0, 3, 2)
                .contains("does not declare"),
        )
    }

    @Test
    fun `app version and api level are independent`() {
        // The whole reason this exists: minAppVersion is a product floor, not the interface
        // contract, and a host may support several api levels within one app release.
        assertEquals(PluginApiCompatibility.COMPATIBLE, evaluate(2, hostCurrent = 3, hostMin = 2))
        assertEquals(PluginApiCompatibility.COMPATIBLE, evaluate(3, hostCurrent = 3, hostMin = 2))
        assertEquals(PluginApiCompatibility.TOO_OLD, evaluate(1, hostCurrent = 3, hostMin = 2))
    }
}
