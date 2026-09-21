package dev.eliminater.purefusioniptv.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * Plugin interface for integrating media servers (Emby, Plex, Jellyfin, etc.)
 * into PureFusionIPTV.
 *
 * Media servers provide additional content sources including:
 * - Live TV channels
 * - Recorded content
 * - Movies and TV shows
 * - Music and photos
 */
interface MediaServerProvider : Plugin {

    /**
     * Server type identifier (e.g., "Emby", "Plex", "Jellyfin")
     */
    val serverType: String

    /**
     * Path to this server's icon **inside the plugin's own `.pfp` package**, e.g.
     * `assets/icon.png`.
     *
     * Replaced `serverIconResourceId: Int` at plugin API level 3. A resource id indexes the
     * resource table of the APK that defined it, and the host has not loaded yours — the integer
     * crossed the boundary perfectly and meant nothing on arrival. A package-relative path is
     * something the host can resolve, and it keeps working while the plugin's worker is not
     * running.
     *
     * Relative to the package root, no `..` segments. PNG, JPEG or WebP.
     */
    val serverIconAssetPath: String?
        get() = null

    /**
     * Check if user is authenticated with the media server
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Authenticate with the media server
     * @param serverUrl Base URL of the media server (e.g., "http://192.168.1.100:8096")
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Authentication token or session ID
     */
    suspend fun authenticate(serverUrl: String, username: String, password: String): String

    /**
     * Sign out from the media server
     */
    suspend fun signOut()

    /**
     * Get server information
     */
    suspend fun getServerInfo(): MediaServerInfo

    /**
     * Get all available live TV channels from the media server
     */
    suspend fun getLiveTVChannels(): Flow<MediaServerChannel>

    /**
     * Get live TV channel stream URL
     * @param channelId Server's channel ID
     * @return Stream URL for playback
     */
    suspend fun getChannelStreamUrl(channelId: String): String

    /**
     * Get EPG programs for a specific channel
     * @param channelId Server's channel ID
     * @param startTimeMillis Start time in epoch milliseconds
     * @param endTimeMillis End time in epoch milliseconds
     */
    suspend fun getChannelPrograms(
        channelId: String,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Flow<MediaServerProgram>

    /**
     * Get recorded programs/DVR content
     */
    suspend fun getRecordings(): Flow<MediaServerRecording>

    /**
     * Get recording stream URL
     * @param recordingId Server's recording ID
     * @return Stream URL for playback
     */
    suspend fun getRecordingStreamUrl(recordingId: String): String

    /**
     * Get movies library
     */
    suspend fun getMovies(): Flow<MediaServerMovie>

    /**
     * Get TV shows library
     */
    suspend fun getTVShows(): Flow<MediaServerTVShow>

    /**
     * Get episodes for a TV show
     * @param showId Server's show ID
     */
    suspend fun getEpisodes(showId: String): Flow<MediaServerEpisode>

    /**
     * Get media stream URL (for movies, episodes, etc.)
     * @param itemId Server's media item ID
     * @param mediaType Type of media (MOVIE, EPISODE, RECORDING)
     * @return Stream URL for playback
     */
    suspend fun getMediaStreamUrl(itemId: String, mediaType: MediaType): String

    /**
     * Search across all media types
     * @param query Search query
     */
    suspend fun search(query: String): MediaServerSearchResults

    /**
     * Get continue watching / in progress items
     */
    suspend fun getContinueWatching(): Flow<MediaServerItem>

    /**
     * Update playback progress
     * @param itemId Server's media item ID
     * @param positionMillis Current playback position in milliseconds
     */
    suspend fun updatePlaybackProgress(itemId: String, positionMillis: Long)

    /**
     * Mark item as watched
     * @param itemId Server's media item ID
     */
    suspend fun markAsWatched(itemId: String)

    /**
     * Get user's favorites
     */
    suspend fun getFavorites(): Flow<MediaServerItem>

    /**
     * Add item to favorites
     * @param itemId Server's media item ID
     */
    suspend fun addToFavorites(itemId: String)

    /**
     * Remove item from favorites
     * @param itemId Server's media item ID
     */
    suspend fun removeFromFavorites(itemId: String)

    /**
     * Check if server supports a specific feature
     */
    fun supportsFeature(feature: MediaServerFeature): Boolean

    /**
     * Describe a settings page for this plugin. The host opens it.
     *
     * Replaced `createSettingsIntent(Context): Intent?` at plugin API level 3, for the same
     * reasons as [CloudStorageProvider.authenticate] — see [PluginUiRequest].
     *
     * Return null when the plugin has nothing to configure, which is the default. The host
     * validates whatever is returned and will refuse a plugin that has not declared
     * `UI_SETTINGS_EXTENSION` in its package manifest.
     *
     * There is no result: the user is finished when they leave.
     */
    fun settingsRequest(): PluginUiRequest? = null
}

/**
 * Media server information
 */
data class MediaServerInfo(
    val serverName: String,
    val serverVersion: String,
    val serverUrl: String,
    val userName: String?,
    val supportedFeatures: List<MediaServerFeature>
)

/**
 * Media server features
 */
enum class MediaServerFeature {
    LIVE_TV,
    DVR_RECORDINGS,
    MOVIES,
    TV_SHOWS,
    MUSIC,
    PHOTOS,
    TRANSCODING,
    DIRECT_PLAY,
    SUBTITLES,
    MULTIPLE_PROFILES,
    PARENTAL_CONTROLS,
    CONTINUE_WATCHING,
    FAVORITES,
    SEARCH
}

/**
 * Live TV channel from media server
 */
data class MediaServerChannel(
    val id: String,
    val name: String,
    val number: String?,
    val imageUrl: String?,
    val hasEPG: Boolean,
    val currentProgram: MediaServerProgram?
)

/**
 * EPG program from media server
 */
data class MediaServerProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val imageUrl: String?,
    val episodeInfo: String?,
    val genres: List<String>
)

/**
 * DVR recording from media server
 */
data class MediaServerRecording(
    val id: String,
    val title: String,
    val description: String?,
    val channelName: String?,
    val recordedTimeMillis: Long,
    val durationMillis: Long,
    val imageUrl: String?,
    val episodeInfo: String?,
    val fileSize: Long
)

/**
 * Movie from media server
 */
data class MediaServerMovie(
    val id: String,
    val title: String,
    val description: String?,
    val year: Int?,
    val durationMillis: Long,
    val imageUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    val rating: Float?,
    val communityRating: Float?
)

/**
 * TV show from media server
 */
data class MediaServerTVShow(
    val id: String,
    val title: String,
    val description: String?,
    val year: Int?,
    val imageUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    val rating: Float?,
    val seasonCount: Int,
    val episodeCount: Int
)

/**
 * TV show episode from media server
 */
data class MediaServerEpisode(
    val id: String,
    val showId: String,
    val showTitle: String,
    val title: String,
    val description: String?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val durationMillis: Long,
    val imageUrl: String?,
    val airDate: String?
)

/**
 * Generic media server item (for mixed results)
 */
data class MediaServerItem(
    val id: String,
    val type: MediaType,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val progress: Float? = null, // 0.0 to 1.0 for continue watching
    val resumePositionMillis: Long? = null
)

/**
 * Media type enumeration
 */
enum class MediaType {
    LIVE_TV,
    RECORDING,
    MOVIE,
    EPISODE,
    SHOW,
    MUSIC,
    PHOTO
}

/**
 * Search results from media server
 */
data class MediaServerSearchResults(
    val channels: List<MediaServerChannel>,
    val recordings: List<MediaServerRecording>,
    val movies: List<MediaServerMovie>,
    val shows: List<MediaServerTVShow>,
    val episodes: List<MediaServerEpisode>
)
