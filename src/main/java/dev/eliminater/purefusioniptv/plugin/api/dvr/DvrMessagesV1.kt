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
    LIST_RULES, UPDATE_RULE, RULE_EPISODES, SET_EPISODE, UPDATE_RECORDING, DELETE_RECORDING, NOTIFY,
    READ_PROGRAMMES(DvrEndpointV1.HOST),
    HOST_DESTINATIONS(DvrEndpointV1.HOST),
    START_CAPTURE(DvrEndpointV1.HOST),
    CAPTURE_STATUS(DvrEndpointV1.HOST),
    CANCEL_CAPTURE(DvrEndpointV1.HOST),
    STOP_CAPTURE(DvrEndpointV1.HOST),
    REGISTER_WAKE(DvrEndpointV1.HOST),
    CANCEL_WAKE(DvrEndpointV1.HOST),
    CREATE_PLAYBACK(DvrEndpointV1.HOST),
    LIST_CAPTURES(DvrEndpointV1.HOST),
    UPDATE_CAPTURE(DvrEndpointV1.HOST),
    DELETE_CAPTURE(DvrEndpointV1.HOST),
    GET_STATE(DvrEndpointV1.HOST),
    PUT_STATE(DvrEndpointV1.HOST),
    LIST_STATE(DvrEndpointV1.HOST),
    READ_LEGACY(DvrEndpointV1.HOST),
    LEGACY_EPISODES(DvrEndpointV1.HOST),
    ADOPT_LEGACY(DvrEndpointV1.HOST),
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

    /**
     * Prevents further occurrences. Existing recordings stay individually addressable. With
     * [cancelUpcoming], occurrences that have not started are canceled too; captured media is never
     * removed by a rule cancellation.
     */
    @Serializable @SerialName("cancel_rule")
    data class CancelRule(val ruleId: String, val cancelUpcoming: Boolean = false) : DvrCommandV1 {
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

    /** Pages the provider's rules, oldest first. */
    @Serializable @SerialName("list_rules")
    data class ListRules(
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.LIST_RULES
    }

    /**
     * Changes a rule. Null fields keep their value. [resetExclusions] allows episodes the user
     * excluded to be recorded again. [refresh] asks the provider to re-check the guide now.
     */
    @Serializable @SerialName("update_rule")
    data class UpdateRule(
        val ruleId: String,
        val paused: Boolean? = null,
        val newEpisodesOnly: Boolean? = null,
        val padding: DvrPaddingV1? = null,
        val resetExclusions: Boolean = false,
        val refresh: Boolean = false,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.UPDATE_RULE
    }

    /** Pages the episode decisions a rule has made, earliest airing first. */
    @Serializable @SerialName("rule_episodes")
    data class RuleEpisodes(
        val ruleId: String,
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.RULE_EPISODES
    }

    /**
     * Excludes an episode from a rule (canceling its recording when it has not started) or allows
     * an excluded episode again.
     */
    @Serializable @SerialName("set_episode")
    data class SetEpisode(val ruleId: String, val episodeId: String, val excluded: Boolean) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.SET_EPISODE
    }

    /** Changes a recording's padding. A running recording keeps its start. */
    @Serializable @SerialName("update_recording")
    data class UpdateRecording(val recordingId: String, val padding: DvrPaddingV1) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.UPDATE_RECORDING
    }

    /** Removes a finished recording and its media. Active recordings must be canceled first. */
    @Serializable @SerialName("delete_recording")
    data class DeleteRecording(val recordingId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.DELETE_RECORDING
    }

    /** Advisory host event. Idempotent; the provider reconciles from durable state. */
    @Serializable @SerialName("notify")
    data class Notify(val event: DvrEventV1) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.NOTIFY
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

    /**
     * Asks the host to accept a single capture job. The reply is a snapshot, never a byte stream.
     * [window] is the nominal (guide) window; [padding] is applied around it. [title] names a
     * capture that has no programme.
     */
    @Serializable @SerialName("start_capture")
    data class StartCapture(
        val channelId: String,
        val destinationId: String,
        val window: DvrWindowV1,
        val programmeId: String? = null,
        val padding: DvrPaddingV1 = DvrPaddingV1(),
        val title: String? = null,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.START_CAPTURE
    }

    @Serializable @SerialName("capture_status")
    data class CaptureStatus(val captureId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.CAPTURE_STATUS
    }

    /**
     * Cancels a capture. With [scheduledOnly] the host cancels only a capture that has not started
     * and answers INVALID_STATE for a running one, so a provider can withdraw an automatic
     * occurrence without ever discarding media that is being captured.
     */
    @Serializable @SerialName("cancel_capture")
    data class CancelCapture(val captureId: String, val scheduledOnly: Boolean = false) : DvrCommandV1 {
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

    /** Pages the host captures visible to this provider, newest first. */
    @Serializable @SerialName("list_captures")
    data class ListCaptures(
        val state: DvrRecordingStateV1? = null,
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.LIST_CAPTURES
    }

    /**
     * Moves or re-pads a capture that has not started, or changes the end of a running one. A
     * running capture keeps its start and its before-padding.
     */
    @Serializable @SerialName("update_capture")
    data class UpdateCapture(
        val captureId: String,
        val window: DvrWindowV1,
        val padding: DvrPaddingV1,
        val programmeId: String? = null,
        val title: String? = null,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.UPDATE_CAPTURE
    }

    /** Removes a terminal capture, its protected media, keys and any network copy. */
    @Serializable @SerialName("delete_capture")
    data class DeleteCapture(val captureId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.DELETE_CAPTURE
    }

    /** Reads one durable provider state record. */
    @Serializable @SerialName("get_state")
    data class GetState(val key: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.GET_STATE
    }

    /** Writes and deletes durable provider state records in one atomic transaction. */
    @Serializable @SerialName("put_state")
    data class PutState(
        val entries: List<DvrStateEntryV1> = emptyList(),
        val deletes: List<String> = emptyList(),
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.PUT_STATE
    }

    /** Pages durable provider state records whose key starts with [prefix], in key order. */
    @Serializable @SerialName("list_state")
    data class ListState(
        val prefix: String = "",
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.LIST_STATE
    }

    /** Pages DVR rules the host held before a provider was installed. */
    @Serializable @SerialName("read_legacy")
    data class ReadLegacy(
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.READ_LEGACY
    }

    /** Pages the episode history of one legacy rule. */
    @Serializable @SerialName("legacy_episodes")
    data class LegacyEpisodes(
        val legacyId: String,
        val cursor: String? = null,
        val pageSize: Int = DvrProtocolV1.MAX_PAGE_SIZE,
    ) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.LEGACY_EPISODES
    }

    /**
     * Confirms that the provider durably holds a legacy rule. The host stops offering it to this
     * provider and never deletes its own copy.
     */
    @Serializable @SerialName("adopt_legacy")
    data class AdoptLegacy(val legacyId: String) : DvrCommandV1 {
        override val operation get() = DvrOperationV1.ADOPT_LEGACY
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
    UNAVAILABLE("Recording is unavailable: check the channel, destination or DVR settings."),
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

    @Serializable @SerialName("rules")
    data class Rules(val items: List<DvrRuleStatusV1>, val nextCursor: String? = null) : DvrPayloadV1

    @Serializable @SerialName("episodes")
    data class Episodes(val items: List<DvrEpisodeEntryV1>, val nextCursor: String? = null) : DvrPayloadV1

    @Serializable @SerialName("recording")
    data class Recording(val snapshot: DvrRecordingV1) : DvrPayloadV1

    @Serializable @SerialName("recordings")
    data class Recordings(val items: List<DvrRecordingV1>, val nextCursor: String? = null) : DvrPayloadV1

    /**
     * A page of guide programmes. [coverage] is the span the host's guide covers for the channel
     * inside the query window, independent of any series filter; null when it has no rows there.
     * [marksNewEpisodes] reports whether that guide marks new or repeated airings at all.
     */
    @Serializable @SerialName("programmes")
    data class Programmes(
        val items: List<DvrProgrammeV1>,
        val nextCursor: String? = null,
        val coverage: DvrWindowV1? = null,
        val marksNewEpisodes: Boolean = false,
    ) : DvrPayloadV1

    @Serializable @SerialName("destinations")
    data class Destinations(val items: List<DvrDestinationV1>) : DvrPayloadV1

    @Serializable @SerialName("playback")
    data class Playback(val handoff: DvrPlaybackV1) : DvrPayloadV1

    @Serializable @SerialName("state")
    data class State(val entries: List<DvrStateEntryV1>, val nextCursor: String? = null) : DvrPayloadV1

    @Serializable @SerialName("legacy_rules")
    data class LegacyRules(val items: List<DvrLegacyRuleV1>, val nextCursor: String? = null) : DvrPayloadV1

    @Serializable @SerialName("legacy_episodes")
    data class LegacyEpisodes(val items: List<DvrLegacyEpisodeV1>, val nextCursor: String? = null) : DvrPayloadV1

    @Serializable @SerialName("ack")
    data object Ack : DvrPayloadV1
}
