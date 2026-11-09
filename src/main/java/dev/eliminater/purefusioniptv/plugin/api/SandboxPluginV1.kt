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
    /**
     * Starts a bounded request. A positive value is a session-local request ID; [SandboxHttpResultV1.BUSY]
     * means one exchange is already running or the minimum spacing has not elapsed.
     *
     * A path beginning with `/` is a GET or JSON POST to the origin the TV owner consented to. If
     * the owner saved an API key for that origin (host UI "Plugin API key"), the host attaches it as
     * the chosen header; the plugin never receives or chooses it. Every request carries the host's
     * `User-Agent: PureFusionIPTV v<version>`.
     *
     * An absolute `https://` URL is a credential-free GET of a file on another host of the same
     * registrable domain as a public consented origin (for example a provider's download host).
     * The host refuses other hosts, LAN origins, non-443 ports, userinfo and every redirect.
     */
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
