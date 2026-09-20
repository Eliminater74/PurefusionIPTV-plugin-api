package dev.eliminater.purefusioniptv.plugin.api

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Plugin interface for cloud storage providers.
 */
interface CloudStorageProvider : Plugin {

    val providerName: String

    val iconResourceId: Int
        get() = 0

    suspend fun isAuthenticated(): Boolean

    suspend fun authenticate(context: Context): Intent?

    suspend fun handleAuthResult(resultCode: Int, data: Intent?): Boolean

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
