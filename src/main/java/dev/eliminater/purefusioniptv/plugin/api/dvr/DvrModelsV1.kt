package dev.eliminater.purefusioniptv.plugin.api.dvr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Half-open UTC interval [startEpochMillis, endEpochMillis). */
@Serializable
data class DvrWindowV1(val startEpochMillis: Long, val endEpochMillis: Long)

/**
 * Host-derived episode identity of one airing.
 *
 * [evidence] holds up to [DvrProtocolV1.MAX_EVIDENCE_TOKENS] stable tokens, strongest first:
 * `p:<provider episode id>`, `se:<season>:<episode>:<part>` and `t:<normalized episode title>`.
 * The host normalizes them, so the same episode yields the same token across guide refreshes. Two
 * airings that share a token are the same episode unless other tokens contradict it. [label] is a
 * display hint such as "S02E05" or the episode title.
 */
@Serializable
data class DvrEpisodeV1(
    val evidence: List<String> = emptyList(),
    val label: String? = null,
)

/** Host-owned channel, programme and series identifiers plus bounded display metadata. */
@Serializable
data class DvrProgrammeV1(
    val channelId: String,
    val programmeId: String,
    val title: String,
    val window: DvrWindowV1,
    val seriesId: String? = null,
    /** True for a new or premiere airing, false for a marked repeat, null when the guide says neither. */
    val isNewEpisode: Boolean? = null,
    val episode: DvrEpisodeV1? = null,
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

/**
 * A recording request. A host may expose the destination ID [DvrProtocolV1.DEFAULT_DESTINATION],
 * which follows the user's DVR storage setting at the moment each capture is accepted.
 */
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
enum class DvrRuleStateV1 { ACTIVE, PAUSED, FINISHED, CANCELED }

/** The result of a rule's most recent guide check. */
@Serializable
enum class DvrRuleOutcomeV1 {
    PENDING, OK, PAUSED, NO_GUIDE_DATA, CHANNEL_UNAVAILABLE, RECORDING_DISABLED,
    NEW_ONLY_UNAVAILABLE, DESTINATION_UNAVAILABLE, LIMIT_REACHED, ERROR,
}

/** Display and edit detail for a rule. All text is untrusted provider display text. */
@Serializable
data class DvrRuleDetailV1(
    val kind: DvrRuleKindV1,
    val channelId: String,
    val destinationId: String,
    val title: String,
    val padding: DvrPaddingV1 = DvrPaddingV1(),
    val seriesId: String? = null,
    val newEpisodesOnly: Boolean = false,
    val outcome: DvrRuleOutcomeV1 = DvrRuleOutcomeV1.PENDING,
    val scheduledCount: Int = 0,
    val conflictCount: Int = 0,
    val createdAtEpochMillis: Long = 0,
    val checkedAtEpochMillis: Long? = null,
    val message: String? = null,
)

@Serializable
data class DvrRuleStatusV1(
    val ruleId: String,
    val state: DvrRuleStateV1,
    val nextWindow: DvrWindowV1? = null,
    val detail: DvrRuleDetailV1? = null,
)

/** A rule's decision about one episode (or one unidentified airing). */
@Serializable
enum class DvrEpisodeStateV1 {
    /** A recording owned by the rule is scheduled or running. */
    SCHEDULED,

    /** Another recording already covers this airing. */
    COVERED,

    /** Every candidate airing conflicted with a recording that has priority. */
    CONFLICT,

    /** Recorded. Re-airings of an identified episode are not recorded again. */
    CAPTURED,

    /** The user excluded it. Not recorded until allowed again. */
    EXCLUDED,

    /** The recording failed; a later airing may be tried. */
    FAILED,

    /** The airing left the guide before it started. */
    REMOVED,

    /** The airing ended without a recording. */
    MISSED,
}

@Serializable
data class DvrEpisodeEntryV1(
    val episodeId: String,
    val state: DvrEpisodeStateV1,
    val window: DvrWindowV1,
    val title: String,
    val label: String? = null,
    val recordingId: String? = null,
    val detail: String? = null,
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
 *
 * [window] is the nominal (guide) window and [padding] the padding applied around it. [message] is
 * a short display note, for example why a recording failed or how it ended.
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
    val padding: DvrPaddingV1? = null,
    val destinationId: String? = null,
    val message: String? = null,
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
    /** Display title of the recording that holds the slot, when the caller may see it. */
    val title: String? = null,
)

/** Advisory host events delivered with [DvrCommandV1.Notify]. */
@Serializable
enum class DvrEventV1 {
    /** A provider session started (after install, update, boot or process restart). */
    STARTUP,

    /** The host committed a guide update. */
    GUIDE_UPDATED,

    /** Host captures changed outside the provider, for example a capture finished. */
    CAPTURES_CHANGED,

    /** The user changed DVR settings such as the storage destination. */
    SETTINGS_CHANGED,
}

/** One durable provider state record. [value] is opaque provider text. */
@Serializable
data class DvrStateEntryV1(val key: String, val value: String)

/** A rule the host kept before a DVR provider owned scheduling. */
@Serializable
data class DvrLegacyRuleV1(
    val legacyId: String,
    val channelId: String,
    val title: String,
    val seriesId: String,
    val newEpisodesOnly: Boolean,
    val padding: DvrPaddingV1,
    val paused: Boolean,
    val createdAtEpochMillis: Long,
    val destinationId: String = DvrProtocolV1.DEFAULT_DESTINATION,
)

/** One episode decision of a legacy rule, with the host evidence tokens that identified it. */
@Serializable
data class DvrLegacyEpisodeV1(
    val entry: DvrEpisodeEntryV1,
    val evidence: List<String> = emptyList(),
    /** True when the airing had no trustworthy episode identity and stands only for itself. */
    val airingOnly: Boolean = false,
    val generatedPadding: DvrPaddingV1 = DvrPaddingV1(),
)
