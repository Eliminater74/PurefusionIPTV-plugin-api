package dev.eliminater.purefusioniptv.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Plugin interface for synchronizing app data across devices.
 */
interface SyncProvider : Plugin {

    val syncState: StateFlow<SyncState>

    suspend fun initialize(): Boolean

    suspend fun syncAll(direction: SyncDirection = SyncDirection.BIDIRECTIONAL): SyncResult

    suspend fun sync(
        dataType: SyncDataType,
        direction: SyncDirection = SyncDirection.BIDIRECTIONAL,
    ): SyncResult

    suspend fun getLastSyncTime(dataType: SyncDataType): Long?

    suspend fun setAutoSync(enabled: Boolean, intervalMinutes: Int = 60)

    suspend fun isAutoSyncEnabled(): Boolean

    suspend fun resolveConflict(conflict: SyncConflict, resolution: ConflictResolution)

    suspend fun getPendingConflicts(): List<SyncConflict>

    suspend fun clearSyncData()
}

enum class SyncDirection {
    UPLOAD,
    DOWNLOAD,
    BIDIRECTIONAL,
}

enum class SyncDataType {
    SETTINGS,
    PLAYLISTS,
    FAVORITES,
    WATCH_HISTORY,
    EPG_SOURCES,
    CATEGORIES,
    PARENTAL_CONTROLS,
}

sealed class SyncState {
    object Idle : SyncState()

    data class Syncing(val dataType: SyncDataType, val progress: Float) : SyncState()

    data class Success(val dataType: SyncDataType, val timestamp: Long) : SyncState()

    /**
     * Synchronisation failed.
     *
     * ## Why there is no `Throwable` here
     *
     * This used to carry the exception that caused the failure, which worked only because plugins
     * ran inside the host process. They no longer necessarily do, and a `Throwable` cannot cross
     * a process boundary in any useful way: its class lives in the *plugin's* classloader, so the
     * host would have nothing to deserialise it into, and a stack trace from inside a plugin
     * routinely contains file paths and request URLs that then land in host logs.
     *
     * [errorCode] replaces it with something both sides can actually use — a short, stable,
     * plugin-defined token such as `"quota_exceeded"` or `"auth_expired"` — which is what a caller
     * wanting to branch on the failure needed anyway. A human-readable explanation belongs in
     * [message]; keep it free of credentials, URLs and paths, because it is displayed and logged.
     */
    data class Error(val message: String, val errorCode: String? = null) : SyncState()

    data class Conflict(val conflicts: List<SyncConflict>) : SyncState()
}

data class SyncResult(
    val success: Boolean,
    val dataType: SyncDataType,
    val itemsSynced: Int,
    val conflicts: List<SyncConflict>,
    val error: String? = null,
)

/**
 * One record that changed on both sides since the last successful sync.
 *
 * [localData] and [remoteData] were declared `Any` until plugin API level 2. That made the type
 * impossible to move between processes — there is no way to serialise `Any` — and it also meant
 * the host could not display a conflict, compare the two sides, or persist one for later, because
 * it had no idea what it was holding. Both are now [SyncPayload].
 */
data class SyncConflict(
    val id: String,
    val dataType: SyncDataType,
    val localData: SyncPayload,
    val remoteData: SyncPayload,
    val localTimestamp: Long,
    val remoteTimestamp: Long,
)

/**
 * A serialised record, in whatever form the plugin chose.
 *
 * ## Why this is an envelope and not a schema
 *
 * The data in a conflict is the *app's* data — a playlist, a favourites list, a settings blob —
 * and its shape is between the plugin and the backing service. Declaring a schema for it here
 * would freeze every one of those shapes into the plugin API, where each change would be a
 * breaking release for everyone.
 *
 * What the host actually needs is narrower: enough to move the value across a process boundary,
 * show the user which side is which, and hand the chosen one back to `resolveConflict`. Three
 * strings do all of that, and none of them require the host to understand the content.
 */
data class SyncPayload(
    /**
     * Media type the plugin assigned, e.g. `application/json`.
     *
     * Advisory. The host does not parse [content]; this exists so a plugin can tell its own
     * formats apart when the payload comes back through `resolveConflict`.
     */
    val contentType: String,

    /** The serialised record. Opaque to the host. */
    val content: String,

    /** Optional revision or ETag from the remote, if the service provides one. */
    val revision: String? = null,
)

enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
    MERGE,
    ASK_USER,
}
