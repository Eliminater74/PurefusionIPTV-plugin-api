package dev.eliminater.purefusioniptv.plugin.api.dvr

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.IOException

/**
 * Wire codec for UTF-8 pf.dvr.v1 JSON. Both sides must apply the byte limits before dispatch
 * and check the reply against its original request. Null indicates an invalid packet.
 * These checks are structural. Ownership, entitlement, consent and session validity are host checks.
 */
object DvrProtocolV1 {
    const val SCHEMA = "pf.dvr.v1"
    /** Signed manifest routing values for a future DVR-capable host. */
    const val MANIFEST_CATEGORY = "dvr_provider"
    const val MANIFEST_CAPABILITY = "DVR_ACCESS"
    const val MAX_REQUEST_BYTES = 16 * 1024
    const val MAX_RESPONSE_BYTES = 64 * 1024
    const val MAX_JSON_DEPTH = 12
    const val MAX_PAGE_SIZE = 20
    const val MAX_DESTINATIONS = 20
    const val MAX_CONFLICTS = 8
    const val MAX_LIMITATIONS = 8
    const val MAX_COMMERCIAL_MARKERS = 16
    const val MAX_ID_CHARS = 128
    const val MAX_TEXT_CHARS = 256
    const val MAX_PADDING_SECONDS = 2 * 60 * 60
    const val MAX_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    const val CALL_TIMEOUT_MS = 5_000L

    private val json = Json { classDiscriminator = "kind" }
    // Include defaults when checking explicitly provided wire fields for type coercion.
    private val completeJson = Json(json) { encodeDefaults = true }

    fun encodeRequest(request: DvrRequestV1): ByteArray? =
        encode(request, MAX_REQUEST_BYTES, request.isValid())

    fun decodeRequest(bytes: ByteArray, endpoint: DvrEndpointV1): DvrRequestV1? =
        decode<DvrRequestV1>(bytes, MAX_REQUEST_BYTES)?.takeIf {
            it.command.operation.endpoint == endpoint && it.isValid()
        }

    fun encodeReply(request: DvrRequestV1, reply: DvrReplyV1): ByteArray? =
        encode(reply, MAX_RESPONSE_BYTES, request.isValid() && reply.isValidFor(request))

    fun decodeReply(request: DvrRequestV1, bytes: ByteArray): DvrReplyV1? =
        decode<DvrReplyV1>(bytes, MAX_RESPONSE_BYTES)?.takeIf {
            request.isValid() && it.isValidFor(request)
        }

    /**
     * Validates an adjacent durable revision or an exact replay. A poll can miss intermediate
     * revisions. Use this check at the state writer or against an ordered, gap-free history.
     */
    fun isValidNextSnapshot(previous: DvrRecordingV1, next: DvrRecordingV1): Boolean =
        isValidTransition(previous, next)

    private inline fun <reified T> encode(value: T, limit: Int, valid: Boolean): ByteArray? {
        if (!valid) return null
        return checked {
            json.encodeToString(value).encodeToByteArray(throwOnInvalidSequence = true)
                .takeIf { it.size <= limit }
        }
    }

    private inline fun <reified T> decode(bytes: ByteArray, limit: Int): T? {
        if (bytes.size !in 1..limit) return null
        return checked {
            JsonReader(bytes.decodeToString(throwOnInvalidSequence = true).reader()).use { reader ->
                reader.strictness = Strictness.STRICT
                val root = reader.readValue(0)
                require(root is JsonObject && reader.peek() == JsonToken.END_DOCUMENT)
                val value = json.decodeFromJsonElement<T>(root)
                require(root.matchesProvidedFields(completeJson.encodeToJsonElement(value)))
                value
            }
        }
    }

    // Limit nesting and refuse duplicate names while reading, before building the typed model.
    private fun JsonReader.readValue(depth: Int): JsonElement {
        require(depth <= MAX_JSON_DEPTH)
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> {
                beginObject()
                val fields = mutableMapOf<String, JsonElement>()
                while (hasNext()) {
                    val name = nextName().checkUnicode()
                    require(name !in fields)
                    fields[name] = readValue(depth + 1)
                }
                endObject()
                JsonObject(fields)
            }
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                val values = buildList { while (hasNext()) add(readValue(depth + 1)) }
                endArray()
                JsonArray(values)
            }
            JsonToken.STRING -> JsonPrimitive(nextString().checkUnicode())
            JsonToken.NUMBER -> JsonPrimitive(nextString().toLong())
            JsonToken.BOOLEAN -> JsonPrimitive(nextBoolean())
            JsonToken.NULL -> { nextNull(); JsonNull }
            else -> throw IllegalArgumentException("Invalid DVR JSON")
        }
    }

    private fun String.checkUnicode(): String {
        encodeToByteArray(throwOnInvalidSequence = true)
        return this
    }

    private fun JsonElement.matchesProvidedFields(expected: JsonElement): Boolean = when {
        this is JsonObject && expected is JsonObject ->
            all { (key, value) -> expected[key]?.let { value.matchesProvidedFields(it) } == true }
        this is JsonArray && expected is JsonArray ->
            size == expected.size && indices.all { this[it].matchesProvidedFields(expected[it]) }
        else -> this == expected
    }

    private inline fun <T> checked(block: () -> T): T? = try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: IOException) {
        null
    }
}
