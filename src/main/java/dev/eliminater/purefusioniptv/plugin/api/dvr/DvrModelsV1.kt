package dev.eliminater.purefusioniptv.plugin.api.dvr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Half-open UTC interval [startEpochMillis, endEpochMillis). */
@Serializable
data class DvrWindowV1(val startEpochMillis: Long, val endEpochMillis: Long)

/** Host-owned channel, programme and series identifiers plus bounded display metadata. */
@Serializable
data class DvrProgrammeV1(
    val channelId: String,
    val programmeId: String,
    val title: String,
    val window: DvrWindowV1,
    val seriesId: String? = null,
    val isNewEpisode: Boolean? = null,
)

@Serializable
data class DvrPaddingV1(val beforeSeconds: Int = 0, val afterSeconds: Int = 0)

@Serializable
enum class DvrRuleKindV1 { ONCE, SERIES, WEEKLY }

@Serializable
sealed interface DvrRuleV1 {
    @Serializable @SerialName("once")
    data object Once : DvrRuleV1

    /** With newEpisodesOnly, unknown episode status is ineligible. */
    @Serializable @SerialName("series")
    data class Series(val seriesId: String, val newEpisodesOnly: Boolean = false) : DvrRuleV1

    /** ISO weekdays (Monday = 1) and a TZDB zone ID. The initial window anchors local time. */
    @Serializable @SerialName("weekly")
    data class Weekly(val weekdays: List<Int>, val zoneId: String) : DvrRuleV1
}

@Serializable
data class DvrScheduleV1(
    val channelId: String,
    val destinationId: String,
    val window: DvrWindowV1,
    val programmeId: String? = null,
    val padding: DvrPaddingV1 = DvrPaddingV1(),
    val rule: DvrRuleV1 = DvrRuleV1.Once,
)

@Serializable
enum class DvrRuleStateV1 { ACTIVE, FINISHED, CANCELED }

@Serializable
data class DvrRuleStatusV1(
    val ruleId: String,
    val state: DvrRuleStateV1,
    val nextWindow: DvrWindowV1? = null,
)

@Serializable
enum class DvrRecordingStateV1 {
    SCHEDULED, RECORDING, FINALIZING, INTERRUPTED, COMPLETED, CANCELED, FAILED,
}

@Serializable
enum class DvrInterruptionV1 { SOURCE_LOST, STORAGE_UNAVAILABLE, HOST_RESTART, OTHER }

@Serializable
data class DvrProgressV1(val capturedMillis: Long, val bytesWritten: Long)

@Serializable
data class DvrCommercialMarkerV1(val startMillis: Long, val endMillis: Long)

@Serializable
data class DvrMediaV1(
    val mediaId: String,
    val durationMillis: Long,
    val commercialMarkers: List<DvrCommercialMarkerV1> = emptyList(),
)

/** Display metadata retained with a recording even after the guide entry has expired. */
@Serializable
data class DvrRecordingMetadataV1(
    val title: String,
    val channelId: String,
    val programmeId: String? = null,
)

/**
 * Revision starts at zero and increases with each durable change. Provider recording IDs and
 * host capture IDs belong to separate namespaces. Host capture snapshots omit [ruleId].
 */
@Serializable
data class DvrRecordingV1(
    val recordingId: String,
    val state: DvrRecordingStateV1,
    val revision: Long,
    val window: DvrWindowV1,
    val ruleId: String? = null,
    val progress: DvrProgressV1? = null,
    val media: DvrMediaV1? = null,
    val interruption: DvrInterruptionV1? = null,
    val metadata: DvrRecordingMetadataV1? = null,
)

@Serializable
enum class DvrDestinationKindV1 { DEVICE, REMOVABLE, NAS }

@Serializable
data class DvrDestinationV1(
    val destinationId: String,
    val kind: DvrDestinationKindV1,
    val displayName: String,
    val available: Boolean,
    val writable: Boolean,
    val availableBytes: Long? = null,
)

@Serializable
data class DvrProviderInfoV1(
    val providerId: String,
    val displayName: String,
    val version: String,
    val operations: List<DvrOperationV1>,
    val ruleKinds: List<DvrRuleKindV1> = emptyList(),
    val destinationKinds: List<DvrDestinationKindV1> = emptyList(),
    val commercialMarkers: Boolean = false,
    val commercialSkipping: Boolean = false,
    val limitations: List<String> = emptyList(),
)

/** An expiring host-issued reference for a user-initiated playback handoff. */
@Serializable
data class DvrPlaybackV1(val token: String, val expiresAtEpochMillis: Long)

@Serializable
enum class DvrConflictReasonV1 { CAPTURE_LIMIT, OVERLAPPING_RULE, DESTINATION_BUSY, SOURCE_UNAVAILABLE }

@Serializable
data class DvrConflictV1(
    val reason: DvrConflictReasonV1,
    val window: DvrWindowV1? = null,
    val recordingId: String? = null,
)
