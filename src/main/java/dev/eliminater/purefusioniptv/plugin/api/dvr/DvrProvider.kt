package dev.eliminater.purefusioniptv.plugin.api.dvr

import dev.eliminater.purefusioniptv.plugin.api.Plugin
import dev.eliminater.purefusioniptv.plugin.api.SandboxPluginV1

/**
 * API 4 DVR entry point implemented alongside [Plugin] and [SandboxPluginV1].
 *
 * A DVR-capable host calls [onDvrLoad] after sandbox initialization and admission. Calls are bounded,
 * serialized per worker and may be cut short by worker retirement. Store durable rule and recording
 * state independently of the worker. [Plugin.onUnload] releases session references.
 */
interface DvrProvider {
    /** Receives a session-scoped facade. The host still authorizes each exchange. */
    fun onDvrLoad(host: DvrHostServicesV1)

    /** Handles one [DvrEndpointV1.PROVIDER] control request. Returns null for an invalid envelope. */
    fun handleDvr(request: ByteArray): ByteArray?
}

/**
 * Worker-side proxy for host-owned DVR services. Implemented by a future isolated host adapter.
 *
 * Calls carry [DvrEndpointV1.HOST] envelopes. A null reply means the outcome is unknown. Reconcile
 * durable state or retry a mutation with the original request ID and payload. The host must never
 * return credentials, a stream URL, a local path, a file descriptor or a live platform object.
 */
fun interface DvrHostServicesV1 {
    fun exchange(request: ByteArray): ByteArray?
}
