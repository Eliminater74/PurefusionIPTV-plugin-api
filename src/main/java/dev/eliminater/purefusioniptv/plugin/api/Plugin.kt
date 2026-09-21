package dev.eliminater.purefusioniptv.plugin.api

import android.content.Context

/**
 * Base interface for all PureFusionIPTV plugins.
 *
 * Plugins extend the app's functionality with custom implementations of:
 * - EPG providers (custom TV guide sources)
 * - Catch-up systems (VOD/time-shift playback)
 * - Metadata providers (movie/show information)
 * - Subtitle engines (custom subtitle formats)
 * - Analytics (usage tracking)
 *
 * Plugins are discovered via ServiceLoader or explicit registration.
 *
 * The current runtime provides version compatibility checks and crash containment,
 * but it is not a true security sandbox. Plugins still run in-process and should be
 * treated as trusted code until the managed package/trust architecture exists.
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
     * Called when the plugin is loaded.
     *
     * ## Why this still takes a `Context` when nothing else does
     *
     * Plugin API level 3 removed `Context` from every other member, and this one was examined
     * alongside them and deliberately kept.
     *
     * The others were removed because they asked the *host* to hand its own `Context` across a
     * process boundary. This one does not: when your plugin runs in a worker process, the
     * `Context` you receive is that worker's own application context, constructed in the process
     * your code is executing in. It is a real, usable `Context` for the things a plugin legitimately
     * needs — `SharedPreferences`, a cache directory, a `ConnectivityManager` — and none of them
     * reach into the host.
     *
     * So it is not a host object crossing a boundary; it is your process's own context, handed to
     * you locally. Removing it would cost plugins real functionality and buy no isolation: your
     * worker already runs under the app's UID, and this `Context` grants nothing beyond that.
     *
     * What it is **not** is a route to the host. There is no host `Activity`, no host singleton,
     * and no host component reachable through it. To have the host show something, describe it
     * with [PluginUiRequest].
     *
     * @param context your own process's application context.
     */
    fun onLoad(context: Context)

    /**
     * Called when plugin is unloaded or app is shutting down
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
    MEDIA_SERVER
}
