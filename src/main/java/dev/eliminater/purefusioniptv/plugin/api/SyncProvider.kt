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
    data class Error(val message: String, val exception: Throwable?) : SyncState()
    data class Conflict(val conflicts: List<SyncConflict>) : SyncState()
}

data class SyncResult(
    val success: Boolean,
    val dataType: SyncDataType,
    val itemsSynced: Int,
    val conflicts: List<SyncConflict>,
    val error: String? = null,
)

data class SyncConflict(
    val id: String,
    val dataType: SyncDataType,
    val localData: Any,
    val remoteData: Any,
    val localTimestamp: Long,
    val remoteTimestamp: Long,
)

enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
    MERGE,
    ASK_USER,
}
