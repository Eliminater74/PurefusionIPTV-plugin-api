package dev.eliminater.purefusioniptv.plugin.api

/**
 * Experimental isolated-runtime extension to API 3.
 *
 * An isolated capability entry point implements this interface in addition to [Plugin]. The
 * sandbox calls [onSandboxLoad] instead of [Plugin.onLoad], and never passes a `Context`.
 *
 * This is not an API level change. The extension is selected by the signed manifest's capability
 * declarations (`NETWORK_EXTERNAL`, `PLUGIN_SCOPED_STORAGE`, `EPG_READ`, `UI_HOME_EXTENSION`) and
 * by the TV owner's consent. A host that does not know a declaration refuses the package before
 * any plugin code runs.
 */
interface SandboxPluginV1 {
    fun onSandboxLoad(capabilities: SandboxCapabilitiesV1)
}

interface SandboxCapabilitiesV1 {
    /** Starts a bounded GET or JSON POST. A positive value is a session-local request ID. */
    fun startHttp(method: String, pathAndQuery: String, jsonBody: ByteArray? = null): Int

    /** Poll for headers or a sequential body chunk. A pending result does not consume any data. */
    fun pollHttp(requestId: Int): SandboxHttpResultV1

    fun cancelHttp(requestId: Int): Boolean
    fun readStorage(key: String): ByteArray?
    fun writeStorage(key: String, value: ByteArray): Boolean
    fun deleteStorage(key: String): Boolean
}

data class SandboxHttpResultV1(
    val status: Int,
    val httpStatus: Int = 0,
    val body: ByteArray? = null,
    val complete: Boolean = false,
) {
    companion object {
        const val PENDING = 0
        const val OK = 1
        const val DENIED = -1
        const val BUSY = -2
        const val FAILED = -3
        const val TOO_LARGE = -4
        const val REDIRECT_DENIED = -5
    }
}
