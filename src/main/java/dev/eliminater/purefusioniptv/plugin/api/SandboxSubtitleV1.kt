package dev.eliminater.purefusioniptv.plugin.api

/**
 * Additive API-3 subtitle extension for a signed `subtitle_provider` executable release.
 * Implement alongside [SandboxPluginV1]. Only the isolated UID lane calls this interface.
 *
 * UTF-8 `pf.subtitle.v1` JSON has three operations: describe, search and download. Host-owned
 * metadata and an opaque result ID are the only inputs; credentials, local paths, host Context,
 * player objects and executable UI never cross this contract. The host validates returned
 * values and keeps all subtitle decoding, attachment and rendering. Provider messages are
 * diagnostics only; the host displays its own fixed status text.
 *
 * Calls are serialized and bounded by [SandboxSubtitleProtocolV1.CALL_TIMEOUT_MS]. Cancellation
 * or timeout tears down the exact worker tenancy and its capability session; implementations
 * should promptly cancel their own outstanding work in onUnload. No callback may attach a late
 * result to playback. A host can restart the worker for the next request.
 */
interface SandboxSubtitlePluginV1 {
    fun handleSubtitles(request: ByteArray): ByteArray?
}

/** Typed JSON values. Optional metadata is omitted or null when unavailable. */
data class SandboxSubtitleMediaV1(
    val contentKey: String,
    val title: String,
    val contentType: String,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

data class SandboxSubtitleRequestV1(
    val schema: String = SandboxSubtitleProtocolV1.SCHEMA,
    val op: String,
    val media: SandboxSubtitleMediaV1? = null,
    val language: String? = null,
    val resultId: String? = null,
)

data class SandboxSubtitleResultV1(
    val id: String,
    /** Display text only; the host never uses this as a filesystem path. */
    val fileName: String,
    val language: String,
    val releaseName: String? = null,
    val downloadCount: Int = 0,
    val hearingImpaired: Boolean = false,
    val autoMatch: Boolean = false,
)

data class SandboxSubtitleResponseV1(
    val schema: String = SandboxSubtitleProtocolV1.SCHEMA,
    val status: String,
    val message: String? = null,
    val results: List<SandboxSubtitleResultV1> = emptyList(),
    val dataBase64: String? = null,
    val format: String? = null,
    val languages: List<String> = emptyList(),
)

object SandboxSubtitleProtocolV1 {
    const val SCHEMA = "pf.subtitle.v1"
    const val MAX_REQUEST_BYTES = 16 * 1024
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    const val MAX_SUBTITLE_BYTES = 512 * 1024
    const val MAX_RESULTS = 100
    const val MAX_TEXT_CHARS = 512
    const val MAX_ID_CHARS = 256
    const val CALL_TIMEOUT_MS = 30_000L

    fun isValidRequest(request: SandboxSubtitleRequestV1): Boolean {
        if (request.schema != SCHEMA || request.op !in setOf("describe", "search", "download")) return false
        if (request.language != null && !isLanguage(request.language)) return false
        if (request.resultId != null && !isText(request.resultId, MAX_ID_CHARS)) return false
        val media = request.media
        if (media != null && (!isText(media.contentKey, MAX_ID_CHARS) || !isText(media.title, MAX_TEXT_CHARS) ||
                media.contentType !in setOf("MOVIE", "EPISODE") || media.tmdbId?.let { it <= 0 } == true ||
                media.imdbId?.let { !Regex("tt[0-9]{1,16}").matches(it) } == true ||
                media.year?.let { it !in 1800..3000 } == true || media.season?.let { it !in 0..10000 } == true ||
                media.episode?.let { it !in 1..100000 } == true)) return false
        return when (request.op) {
            "describe" -> media == null && request.resultId == null && request.language == null
            "search" -> media != null && request.resultId == null
            "download" -> request.resultId != null && media == null
            else -> false
        }
    }

    fun isLanguage(value: String): Boolean = Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?").matches(value)

    fun isText(value: String, maxChars: Int): Boolean =
        value.isNotBlank() && value.length <= maxChars && value.none { it < ' ' || it == '\u007f' }
}
