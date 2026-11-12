package dev.eliminater.purefusioniptv.plugin.api

import android.content.Context

/**
 * Base interface for all PureFusionIPTV plugins.
 *
 * Installed executable packages identify their entry point in the manifest. A plugin that uses
 * ServiceLoader for discovery registers this interface's fully qualified name.
 *
 * Admitted Marketplace executables normally run in an isolated UID on Android API 26 or later.
 * The host has a separate app-UID compatibility worker for a pinned first-party Emby release.
 * A [PluginCapability] value describes a provider role and does not grant host access.
 */
interface Plugin {
    /**
     * Unique identifier for this plugin (e.g., "com.example.myplugin")
     * Must be unique across all installed plugins.
     */
    val id: String

    /**
     * Human-readable plugin name displayed in UI
     */
    val name: String

    /**
     * Plugin description explaining functionality
     */
    val description: String

    /**
     * Plugin version string (semantic versioning recommended: "1.0.0")
     */
    val version: String

    /**
     * Author/developer name
     */
    val author: String

    /**
     * Minimum required PureFusionIPTV version
     * Format: "major.minor.patch" (e.g., "1.0.0")
     *
     * This is a *product* floor — "needs a build with this feature in it". It is not the
     * compatibility contract for these interfaces; see [apiLevel].
     */
    val minAppVersion: String

    /**
     * The plugin API level this plugin was built against — normally [PluginApi.CURRENT] at
     * build time.
     *
     * This, not [minAppVersion], is what the host checks before loading plugin code. The default
     * is [PluginApi.LEVEL_UNSPECIFIED] so plugins written before the level existed still compile;
     * the host refuses to load them and reports why, rather than guessing a level for them.
     */
    val apiLevel: Int get() = PluginApi.LEVEL_UNSPECIFIED

    /**
     * Plugin capabilities - what types of features this plugin provides
     */
    val capabilities: List<PluginCapability>

    /**
     * Initializes a capability-free isolated executable or a compatibility entry point.
     *
     * Isolated entries with manifest capabilities, and isolated subtitle providers, receive
     * [SandboxPluginV1.onSandboxLoad] instead. For a capability-free isolated executable, the
     * local context still has only the isolated UID's access. The pinned compatibility worker
     * receives its own application context under the app UID.
     *
     * @param context application context created in the process that runs the plugin.
     */
    fun onLoad(context: Context)

    /**
     * Releases resources during graceful unload. Process termination can skip this callback.
     */
    fun onUnload()

    /**
     * Check if plugin is compatible with current app version
     * @param appVersion Current app version string
     * @return true if compatible, false otherwise
     */
    fun isCompatible(appVersion: String): Boolean {
        // Default implementation: simple version comparison
        val minVersion = parseVersion(minAppVersion)
        val currentVersion = parseVersion(appVersion)
        return currentVersion >= minVersion
    }

    /**
     * Parse semantic version string to comparable integer.
     * Prerelease suffixes (e.g. `1.0.0-Beta-9.7`) compare using the base `major.minor.patch` only.
     */
    private fun parseVersion(version: String): Int {
        val base = version.substringBefore('-').trim()
        val parts = base.split(".")
        if (parts.size != 3) return 0
        return try {
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            val patch = parts[2].substringBefore('-').toInt()
            (major * 10000) + (minor * 100) + patch
        } catch (e: NumberFormatException) {
            0
        }
    }
}

/**
 * Plugin capability types
 */
enum class PluginCapability {
    /** Provides custom EPG data sources */
    EPG_PROVIDER,

    /** Provides catch-up/VOD playback */
    CATCHUP_PROVIDER,

    /** Provides movie/show metadata */
    METADATA_PROVIDER,

    /** Provides subtitle parsing/rendering */
    SUBTITLE_ENGINE,

    /** Provides analytics/tracking */
    ANALYTICS,

    /** Provides custom channel sources */
    CHANNEL_SOURCE,

    /** Provides custom themes/UI */
    THEME_PROVIDER,

    /** Provides cloud storage integration (Google Drive, OneDrive, etc.) */
    CLOUD_STORAGE,

    /** Provides data synchronization across devices */
    SYNC_PROVIDER,

    /** Provides UI extensions (settings, menu items, etc.) */
    UI_EXTENSION,

    /** Provides media server integration (Emby, Plex, Jellyfin, etc.) */
    MEDIA_SERVER,

    /** Provides DVR workflows through the API 4 isolated DVR contract. */
    DVR_PROVIDER,
}
