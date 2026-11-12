package dev.eliminater.purefusioniptv.plugin.api.dvr

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The receiver of a request. The transport fixes the direction. */
enum class DvrEndpointV1 { PROVIDER, HOST }

/** Advertised operation IDs. Only provider-side operations are valid in a provider description. */
@Serializable
enum class DvrOperationV1(val endpoint: DvrEndpointV1 = DvrEndpointV1.PROVIDER) {
    DESCRIBE, SCHEDULE, RULE_STATUS, CANCEL_RULE, STATUS, LIST_RECORDINGS,
    CANCEL, STOP, LIST_DESTINATIONS, PLAYBACK, WAKE,
    READ_PROGRAMMES(DvrEndpointV1.HOST),
    HOST_DESTINATIONS(DvrEndpointV1.HOST),
    START_CAPTURE(DvrEndpointV1.HOST),
    CAPTURE_STATUS(DvrEndpointV1.HOST),
    CANCEL_CAPTURE(DvrEndpointV1.HOST),
    STOP_CAPTURE(DvrEndpointV1.HOST),
    REGISTER_WAKE(DvrEndpointV1.HOST),
    CANCEL_WAKE(DvrEndpointV1.HOST),
    CREATE_PLAYBACK(DvrEndpointV1.HOST),
}

/**
 * Request IDs correlate replies. For mutations, retries of the same logical operation reuse the
 * original ID and payload. A receiver rejects reuse with a different command or payload.
 */
@Serializable
data class DvrRequestV1(
    val requestId: String,
    val command: DvrCommandV1,
    @Required val schema: String = DvrProtocolV1.SCHEMA,
)

@Serializable
sealed interface DvrCommandV1 {
    val operation: DvrOperationV1

    @Serializable @SerialName("describe")
    data object Describe : DvrCommandV1 {
        override val operation get() = DvrOperationV1.DESCRIBE
    }

    @Serializable @SerialName("schedule")
    data class Schedule(val schedule: DvrScheduleV1) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.SCHEDULE
    }

    @Serializable @SerialName("rule_status")
    data class RuleStatus(val ruleId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.RULE_STATUS
    }

    /** Prevents further occurrences and leaves existing recordings individually addressable. */
    @Serializable @SerialName("cancel_rule")
    data class CancelRule(val ruleId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.CANCEL_RULE
    }

    @Serializable @SerialName("status")
    data class Status(val recordingId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.STATUS
    }

    @Serializable @SerialName("list_recordings")
    data class ListRecordings(
        val state: DvrRecordingStateV1? = null,
        val ruleId: String? = null,
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.LIST_RECORDINGS
    }

    /** Cancels a pending or active occurrence. No playable recording is promised. */
    @Serializable @SerialName("cancel")
    data class Cancel(val recordingId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.CANCEL
    }

    /** Requests finalization of an active capture for playback. */
    @Serializable @SerialName("stop")
    data class Stop(val recordingId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.STOP
    }

    @Serializable @SerialName("list_destinations")
    data object ListDestinations : DvrCommandV1 {
        override val operation get() = DvrOperationV1.LIST_DESTINATIONS
    }

    @Serializable @SerialName("playback")
    data class Playback(val recordingId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.PLAYBACK
    }

    /** Host delivery of a registered wake. Duplicate deliveries reuse the same request ID. */
    @Serializable @SerialName("wake")
    data class Wake(val wakeId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.WAKE
    }

    @Serializable @SerialName("read_programmes")
    data class ReadProgrammes(
        val channelId: String,
        val window: DvrWindowV1,
        val seriesId: String? = null,
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.READ_PROGRAMMES
    }

    @Serializable @SerialName("host_destinations")
    data object HostDestinations : DvrCommandV1 {
        override val operation get() = DvrOperationV1.HOST_DESTINATIONS
    }

    /** Asks the host to accept a single capture job. The reply is a snapshot, never a byte stream. */
    @Serializable @SerialName("start_capture")
    data class StartCapture(
        val channelId: String,
        val destinationId: String,
        val window: DvrWindowV1,
        val programmeId: String? = null,
        val padding: DvrPaddingV1 = DvrPaddingV1(),
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.START_CAPTURE
    }

    @Serializable @SerialName("capture_status")
    data class CaptureStatus(val captureId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.CAPTURE_STATUS
    }

    @Serializable @SerialName("cancel_capture")
    data class CancelCapture(val captureId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.CANCEL_CAPTURE
    }

    @Serializable @SerialName("stop_capture")
    data class StopCapture(val captureId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.STOP_CAPTURE
    }

    @Serializable @SerialName("register_wake")
    data class RegisterWake(val wakeId: String, val runAtEpochMillis: Long) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.REGISTER_WAKE
    }

    @Serializable @SerialName("cancel_wake")
    data class CancelWake(val wakeId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.CANCEL_WAKE
    }

    @Serializable @SerialName("create_playback")
    data class CreatePlayback(val mediaId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.CREATE_PLAYBACK
    }
}

@Serializable
enum class DvrCodeV1(val message: String) {
    OK("Request accepted."),
    INVALID_REQUEST("The DVR request is invalid."),
    UNSUPPORTED("This DVR operation is unavailable."),
    DENIED("The DVR operation is not authorized."),
    NOT_FOUND("The DVR item is unavailable."),
    CONFLICT("The DVR request conflicts with an existing operation."),
    INVALID_STATE("The recording cannot be changed in its current state."),
    BUSY("DVR is busy. Try again later."),
    REQUEST_ID_REUSED("The request ID belongs to a different operation."),
    FAILED("The DVR operation failed."),
}

@Serializable
data class DvrReplyV1(
    val requestId: String,
    val code: DvrCodeV1,
    val payload: DvrPayloadV1? = null,
    val conflicts: List<DvrConflictV1> = emptyList(),
    @Required val schema: String = DvrProtocolV1.SCHEMA,
)

@Serializable
sealed interface DvrPayloadV1 {
    @Serializable @SerialName("provider")
    data class Provider(val info: DvrProviderInfoV1) : DvrPayloadV1

    @Serializable @SerialName("rule")
    data class Rule(val status: DvrRuleStatusV1) : DvrPayloadV1

    @Serializable @SerialName("recording")
    data class Recording(val snapshot: DvrRecordingV1) : DvrPayloadV1

    @Serializable @SerialName("recordings")
    data class Recordings(val items: List<DvrRecordingV1>, val nextCursor: String? = null) : DvrPayloadV1

    @Serializable @SerialName("programmes")
    data class Programmes(val items: List<DvrProgrammeV1>, val nextCursor: String? = null) : DvrPayloadV1

    @Serializable @SerialName("destinations")
    data class Destinations(val items: List<DvrDestinationV1>) : DvrPayloadV1

    @Serializable @SerialName("playback")
    data class Playback(val handoff: DvrPlaybackV1) : DvrPayloadV1

    @Serializable @SerialName("ack")
    data object Ack : DvrPayloadV1
}
