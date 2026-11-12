package dev.eliminater.purefusioniptv.plugin.api

/**
 * The versioned contract between PureFusionIPTV and a plugin.
 *
 * ## Why this is not the app version
 *
 * Plugins previously declared only `minAppVersion` and were checked against the *host application*
 * version. That couples every plugin to the app's release cadence: shipping `2.0.0-Beta-23` says
 * nothing about whether the interfaces a plugin compiles against changed, and a plugin author has
 * no way to express "I need the interfaces as they were at this point" other than guessing a
 * marketing version.
 *
 * [CURRENT] is a monotonic integer that changes **only** when this module's public surface changes.
 * The app version and the API level move independently, and a plugin declares the API level it was
 * built against.
 *
 * ## Evolution rules
 *
 * * Adding a new interface, a new capability constant, or a new method **with a default
 *   implementation** is backwards compatible: [CURRENT] increases, [MIN_SUPPORTED] does not.
 * * Removing or changing the signature of anything already published is a breaking change:
 *   [MIN_SUPPORTED] moves up to the new level, and every plugin below it is rejected before its
 *   code is loaded.
 * * A plugin built against a *newer* level than the host provides is also rejected — it may
 *   reference members this host does not have, which would fail at link time inside plugin code
 *   rather than at a boundary the host controls.
 *
 * Once this module is published, [CURRENT] is a compatibility burden. Treat an increment as a
 * public API release, not a build detail.
 */
object PluginApi {

    /**
     * The API level this build of the module publishes.
     *
     * Level 1 is the first level with an explicit number. Plugins built before this existed
     * declared no level at all; the host treats a missing declaration as [LEVEL_UNSPECIFIED].
     *
     * **Level 2** repaired the `SyncProvider` schema: `SyncConflict.localData` and `remoteData`
     * changed from `Any` to [SyncPayload], and `SyncState.Error` dropped its `Throwable` in favour
     * of a string `errorCode`. Both were unavoidable — neither `Any` nor `Throwable` can cross a
     * process boundary, so those members could not be served to a plugin running out of process at
     * all.
     *
     * **Level 3** removed `android.content.Context` and `android.content.Intent` from the
     * provider UI methods. [Plugin.onLoad] still accepts a process-local context.
     * Provider auth and settings now use [PluginUiRequest] and [PluginUiResult]. The two
     * resource ID properties became package-relative asset paths. The host owns the UI launch.
     *
     * **Level 4** adds the isolated DVR provider contract. Level 3 entry points remain compatible.
     */
    const val CURRENT: Int = 4

    /**
     * The oldest plugin-declared level this host still accepts.
     *
     * Raised to 3 by the level-3 UI-contract change. The evolution rules above require it: a
     * plugin compiled against level 2 overrides `authenticate(Context)` and
     * `createSettingsIntent(Context)`, neither of which exists any more, so its class would fail
     * to link inside its own code. Rejecting it at install time — where the host can explain that
     * the plugin needs updating — is the point of having the level at all.
     */
    const val MIN_SUPPORTED: Int = 3

    /** A plugin that declared no API level. Rejected rather than guessed at. */
    const val LEVEL_UNSPECIFIED: Int = 0
}

/** Why a plugin's declared API level was accepted or refused. */
enum class PluginApiCompatibility(val logName: String) {
    COMPATIBLE("compatible"),

    /** The plugin declared no API level; the host will not guess one. */
    UNSPECIFIED("unspecified"),

    /** Built against a level this host has stopped supporting. The plugin must be updated. */
    TOO_OLD("too_old"),

    /** Built against a newer level than this host provides. The *host* must be updated. */
    TOO_NEW("too_new"),
    ;

    val isCompatible: Boolean get() = this == COMPATIBLE
}

/**
 * Host-side API level checking.
 *
 * Deliberately pure and free of Android types so both the host and a plugin's own build tooling
 * can evaluate compatibility without instantiating anything.
 */
object PluginApiCompatibilityCheck {

    /**
     * @param declaredLevel the API level the plugin was built against, or
     *   [PluginApi.LEVEL_UNSPECIFIED] when it declared none.
     * @param hostCurrent the level this host publishes; defaults to [PluginApi.CURRENT].
     * @param hostMinSupported the oldest level this host accepts; defaults to
     *   [PluginApi.MIN_SUPPORTED].
     */
    fun evaluate(
        declaredLevel: Int,
        hostCurrent: Int = PluginApi.CURRENT,
        hostMinSupported: Int = PluginApi.MIN_SUPPORTED,
    ): PluginApiCompatibility = when {
        declaredLevel <= PluginApi.LEVEL_UNSPECIFIED -> PluginApiCompatibility.UNSPECIFIED
        declaredLevel < hostMinSupported -> PluginApiCompatibility.TOO_OLD
        declaredLevel > hostCurrent -> PluginApiCompatibility.TOO_NEW
        else -> PluginApiCompatibility.COMPATIBLE
    }

    /**
     * Human-readable explanation for a refusal, for the installer UI and logs.
     *
     * Says which side needs updating, because "incompatible" alone leaves a user with no action.
     */
    fun explain(
        compatibility: PluginApiCompatibility,
        declaredLevel: Int,
        hostCurrent: Int = PluginApi.CURRENT,
        hostMinSupported: Int = PluginApi.MIN_SUPPORTED,
    ): String = when (compatibility) {
        PluginApiCompatibility.COMPATIBLE ->
            "Built against plugin API $declaredLevel (host supports $hostMinSupported-$hostCurrent)."
        PluginApiCompatibility.UNSPECIFIED ->
            "This plugin does not declare a plugin API level and cannot be loaded."
        PluginApiCompatibility.TOO_OLD ->
            "This plugin targets plugin API $declaredLevel; PureFusionIPTV now requires at least " +
                "$hostMinSupported. Update the plugin."
        PluginApiCompatibility.TOO_NEW ->
            "This plugin targets plugin API $declaredLevel; this build of PureFusionIPTV provides " +
                "$hostCurrent. Update PureFusionIPTV."
    }
}
