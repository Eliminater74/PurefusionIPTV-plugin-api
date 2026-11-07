package dev.eliminater.purefusioniptv.plugin.api

/**
 * Experimental isolated-runtime extension: a feature screen that the host draws.
 *
 * A release that declares `UI_HOME_EXTENSION` implements this interface next to [SandboxPluginV1].
 * The host never receives a View, a layout or executable UI from the plugin. It sends one bounded
 * `pf.surface.v1` JSON request at a time and draws the returned JSON document with its own trusted
 * components: sections, headers, cards, a status line, paging, and host-owned dialogs.
 *
 * Playback, Guide, reminders, favorites and Multi-View stay host actions. A card can only name a
 * channel and an airing; the host reloads that identity, rechecks access and runs its own owner.
 *
 * Calls arrive on a host Binder thread, one at a time, while the user has the screen open. Return
 * promptly; the host gives up after [SandboxSurfaceProtocolV1.CALL_TIMEOUT_MS]. Returning null, or
 * an invalid document, shows the host's generic error state.
 */
interface SandboxSurfacePluginV1 {
    fun handleSurface(request: ByteArray): ByteArray?
}

/**
 * Bounded, read-only access to the TV's own Live TV channels and active guide (`EPG_READ`).
 *
 * The capability object passed to [SandboxPluginV1.onSandboxLoad] also implements this interface
 * when the signed release declares `EPG_READ`. Requests are `pf.catalog.v1` JSON. The host derives
 * the eligible playlists and the guide horizon from the account's current entitlement on every
 * call; a request cannot widen them. Calls succeed only while the TV owner has consented and only
 * during a [SandboxSurfacePluginV1.handleSurface] call, and return null when denied.
 */
interface SandboxLiveCatalogV1 {
    fun queryLiveCatalog(request: ByteArray): ByteArray?
}

/** Fixed protocol limits. The host enforces them; a plugin should stay well inside them. */
object SandboxSurfaceProtocolV1 {
    const val SURFACE_SCHEMA: String = "pf.surface.v1"
    const val CATALOG_SCHEMA: String = "pf.catalog.v1"
    const val VERSION: Int = 1

    /** Uncompressed request or document size. The host compresses both directions on the wire. */
    const val MAX_DOCUMENT_BYTES: Int = 2 * 1024 * 1024
    const val MAX_REQUEST_BYTES: Int = 64 * 1024
    const val MAX_STATE_CHARS: Int = 16 * 1024
    const val MAX_SECTIONS: Int = 32
    const val MAX_CARDS: Int = 1200
    const val MAX_ROWS_PER_SECTION: Int = 1200
    const val MAX_TEXT_CHARS: Int = 240
    /**
     * How long the host waits for one surface call. Generous on purpose: a cold-started TV can
     * spend tens of seconds in its own guide warm-up while the first catalog reads run (measured
     * on the developer AVD, November 6, 2026). The screen shows its loading state meanwhile.
     */
    const val CALL_TIMEOUT_MS: Long = 90_000L

    const val MAX_CATALOG_CHANNEL_ROWS: Int = 257
    const val MAX_CATALOG_PROGRAMME_ROWS: Int = 201
    const val MAX_CATALOG_TERMS: Int = 64
    const val MAX_CATALOG_PHRASES: Int = 512
}
