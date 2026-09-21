package dev.eliminater.purefusioniptv.plugin.api

import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Plugin interface for cloud storage providers.
 */
interface CloudStorageProvider : Plugin {

    val providerName: String

    /**
     * Path to this provider's icon **inside the plugin's own `.pfp` package**, e.g.
     * `assets/icon.png`.
     *
     * Replaced `iconResourceId: Int` at plugin API level 3. A resource id indexes the resource
     * table of the APK that defined it, and the host process has not loaded yours — so the integer
     * crossed the boundary perfectly and meant nothing on arrival. A path into the package is
     * something the host can actually resolve, and it keeps working when the plugin's worker is
     * not running.
     *
     * Relative to the package root, no `..` segments. PNG, JPEG or WebP.
     */
    val iconAssetPath: String?
        get() = null

    suspend fun isAuthenticated(): Boolean

    /**
     * Describe the sign-in flow. The host performs it.
     *
     * Replaced `authenticate(Context): Intent?` at plugin API level 3 — see [PluginUiRequest] for
     * why a `Context` cannot be given to you and an `Intent` cannot be accepted from you.
     *
     * Return null when there is nothing to do, which is how you say "already signed in". The host
     * never treats a request as optional: if you return one, it is validated, and you will receive
     * exactly one [PluginUiResult] through [handleAuthResult] whatever happens to it.
     *
     * Requires `CLOUD_STORAGE_ACCESS` in your package manifest.
     */
    suspend fun authenticate(): PluginUiRequest?

    /**
     * Receive the outcome of the flow [authenticate] asked for.
     *
     * Called exactly once per request, including for outcomes you did not cause —
     * [PluginUiOutcome.CANCELLED], [PluginUiOutcome.DENIED], [PluginUiOutcome.EXPIRED]. Treat
     * anything other than [PluginUiOutcome.COMPLETED] as "not signed in".
     *
     * Verify the `state` parameter yourself; the host cannot do it for you.
     *
     * @return whether the plugin is now authenticated.
     */
    suspend fun handleAuthResult(result: PluginUiResult): Boolean

    suspend fun signOut()

    suspend fun getUserDisplayName(): String?

    suspend fun getUserEmail(): String?

    suspend fun uploadFile(
        fileName: String,
        folderPath: String,
        inputStream: InputStream,
        mimeType: String,
        progressCallback: ((Float) -> Unit)? = null,
    ): String

    suspend fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        progressCallback: ((Float) -> Unit)? = null,
    )

    suspend fun listFiles(folderPath: String): Flow<CloudFile>

    suspend fun deleteFile(fileId: String)

    suspend fun fileExists(fileName: String, folderPath: String): Boolean

    suspend fun getFileMetadata(fileId: String): CloudFile?

    suspend fun getStorageQuota(): StorageQuota

    suspend fun createFolder(folderName: String, parentPath: String): String

    fun getProviderCapabilities(): CloudProviderCapabilities = CloudProviderCapabilities(
        supportsStreaming = false,
        supportsSharing = false,
        supportsVersioning = false,
        maxFileSizeBytes = Long.MAX_VALUE,
    )
}

data class CloudFile(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedTimeMillis: Long,
    val isFolder: Boolean = false,
    val downloadUrl: String? = null,
)

data class StorageQuota(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
) {
    val usagePercent: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes) * 100 else 0f
}

data class CloudProviderCapabilities(
    val supportsStreaming: Boolean,
    val supportsSharing: Boolean,
    val supportsVersioning: Boolean,
    val maxFileSizeBytes: Long,
)
