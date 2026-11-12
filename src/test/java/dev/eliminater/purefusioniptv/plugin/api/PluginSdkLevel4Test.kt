package dev.eliminater.purefusioniptv.plugin.api

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSdkLevel4Test {
    @Test fun `SDK exposes its version and supported range`() {
        assertEquals("1.1.0", PluginSdk.version)
        assertEquals(4, PluginSdk.currentApi)
        assertEquals(3, PluginSdk.minSupportedApi)
        assertEquals(3..4, PluginSdk.supportedApiRange)
    }

    @Test fun `level 4 adds to the compatibility window`() {
        val evaluate = PluginApiCompatibilityCheck::evaluate
        assertEquals(PluginApiCompatibility.COMPATIBLE, evaluate(3, 4, 3))
        assertEquals(PluginApiCompatibility.COMPATIBLE, PluginApiCompatibilityCheck.evaluate(4))
        assertEquals(PluginApiCompatibility.TOO_NEW, evaluate(4, 3, 3))
        assertEquals(PluginApiCompatibility.TOO_OLD, PluginApiCompatibilityCheck.evaluate(2))
        assertEquals(PluginApiCompatibility.UNSPECIFIED, PluginApiCompatibilityCheck.evaluate(0))
    }

    @Test fun `existing provider roles keep names and ordinals`() {
        assertEquals(
            listOf("EPG_PROVIDER", "CATCHUP_PROVIDER", "METADATA_PROVIDER", "SUBTITLE_ENGINE",
                "ANALYTICS", "CHANNEL_SOURCE", "THEME_PROVIDER", "CLOUD_STORAGE", "SYNC_PROVIDER",
                "UI_EXTENSION", "MEDIA_SERVER", "DVR_PROVIDER"),
            PluginCapability.entries.map { it.name },
        )
    }

    @Test fun `API 3 entry point and isolated callback signatures are retained`() {
        val plugin = Plugin::class.java
        assertEquals("dev.eliminater.purefusioniptv.plugin.api.Plugin", plugin.name)
        assertEquals(Void.TYPE, plugin.getMethod("onLoad", Context::class.java).returnType)
        assertEquals(Void.TYPE, plugin.getMethod("onUnload").returnType)
        assertEquals(Int::class.javaPrimitiveType, plugin.getMethod("getApiLevel").returnType)
        assertEquals(List::class.java, plugin.getMethod("getCapabilities").returnType)
        assertTrue(plugin.isAssignableFrom(MediaServerProvider::class.java))
        assertEquals(String::class.java, MediaServerProvider::class.java.getMethod("getServerType").returnType)
        assertEquals(Void.TYPE, SandboxPluginV1::class.java
            .getMethod("onSandboxLoad", SandboxCapabilitiesV1::class.java).returnType)
        assertEquals(ByteArray::class.java, SandboxSurfacePluginV1::class.java
            .getMethod("handleSurface", ByteArray::class.java).returnType)
        assertEquals(ByteArray::class.java, SandboxSubtitlePluginV1::class.java
            .getMethod("handleSubtitles", ByteArray::class.java).returnType)
    }
}
