package dev.eliminater.purefusioniptv.plugin.api.dvr

import java.util.TimeZone

private const val MAX_EPOCH_MS = 253_402_300_799_999L // End of year 9999.
private const val MAX_CAPTURE_MS = DvrProtocolV1.MAX_WINDOW_MS + 2 * DvrProtocolV1.MAX_PADDING_SECONDS * 1000L
private const val MAX_COUNT = 100_000
private val opaqueId = Regex("[A-Za-z0-9][A-Za-z0-9._~-]{0,${DvrProtocolV1.MAX_ID_CHARS - 1}}")
private val statePrefix = Regex("[A-Za-z0-9._~-]{0,${DvrProtocolV1.MAX_STATE_PREFIX_CHARS}}")
private val evidenceToken = Regex("(p|se|t|a):\\S.{0,250}")
private val zoneIds by lazy { TimeZone.getAvailableIDs().toSet() }

private fun String.isId() = length <= DvrProtocolV1.MAX_ID_CHARS && opaqueId.matches(this)
private fun String?.isOptionalId() = this == null || isId()
private fun String.isDisplayText() =
    length in 1..DvrProtocolV1.MAX_TEXT_CHARS && isNotBlank() && none { it.isISOControl() }
private fun String?.isOptionalText() = this == null || isDisplayText()
private fun Int.isCount() = this in 0..MAX_COUNT

private fun Long.isEpoch() = this in 0..MAX_EPOCH_MS
private fun DvrWindowV1.isValid() =
    startEpochMillis.isEpoch() && endEpochMillis.isEpoch() &&
        endEpochMillis - startEpochMillis in 1..DvrProtocolV1.MAX_WINDOW_MS

private fun DvrWindowV1.overlaps(other: DvrWindowV1) =
    startEpochMillis < other.endEpochMillis && other.startEpochMillis < endEpochMillis

private fun DvrPaddingV1.isValid() =
    beforeSeconds in 0..DvrProtocolV1.MAX_PADDING_SECONDS &&
        afterSeconds in 0..DvrProtocolV1.MAX_PADDING_SECONDS

private fun DvrScheduleV1.isValid() =
    channelId.isId() && destinationId.isId() && programmeId.isOptionalId() &&
        window.isValid() && padding.isValid() && when (val recurrence = rule) {
            DvrRuleV1.Once -> true
            is DvrRuleV1.Series -> recurrence.seriesId.isId()
            is DvrRuleV1.Weekly -> recurrence.weekdays.size in 1..7 &&
                recurrence.weekdays.all { it in 1..7 } &&
                recurrence.weekdays.distinct().size == recurrence.weekdays.size &&
                recurrence.zoneId.length <= DvrProtocolV1.MAX_ID_CHARS && recurrence.zoneId in zoneIds
        }

private fun Int.isPageSize() = this in 1..DvrProtocolV1.MAX_PAGE_SIZE

private fun DvrStateEntryV1.isValid() =
    key.isId() && value.length <= DvrProtocolV1.MAX_STATE_VALUE_CHARS

private fun DvrCommandV1.PutState.isValid(): Boolean {
    if (entries.isEmpty() && deletes.isEmpty()) return false
    if (entries.size > DvrProtocolV1.MAX_STATE_PUTS || deletes.size > DvrProtocolV1.MAX_STATE_DELETES) return false
    val keys = entries.map { it.key } + deletes
    return entries.all { it.isValid() } && deletes.all { it.isId() } && keys.distinct().size == keys.size
}

internal fun DvrRequestV1.isValid() =
    schema == DvrProtocolV1.SCHEMA && requestId.isId() && when (val op = command) {
        DvrCommandV1.Describe, DvrCommandV1.ListDestinations, DvrCommandV1.HostDestinations -> true
        is DvrCommandV1.Schedule -> op.schedule.isValid()
        is DvrCommandV1.RuleStatus -> op.ruleId.isId()
        is DvrCommandV1.CancelRule -> op.ruleId.isId()
        is DvrCommandV1.Status -> op.recordingId.isId()
        is DvrCommandV1.Cancel -> op.recordingId.isId()
        is DvrCommandV1.Stop -> op.recordingId.isId()
        is DvrCommandV1.Playback -> op.recordingId.isId()
        is DvrCommandV1.ListRecordings -> op.ruleId.isOptionalId() && op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.Wake -> op.wakeId.isId()
        is DvrCommandV1.ListRules -> op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.UpdateRule -> op.ruleId.isId() && op.padding?.isValid() != false &&
            (op.paused != null || op.newEpisodesOnly != null || op.padding != null || op.resetExclusions || op.refresh)
        is DvrCommandV1.RuleEpisodes -> op.ruleId.isId() && op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.SetEpisode -> op.ruleId.isId() && op.episodeId.isId()
        is DvrCommandV1.UpdateRecording -> op.recordingId.isId() && op.padding.isValid()
        is DvrCommandV1.DeleteRecording -> op.recordingId.isId()
        is DvrCommandV1.Notify -> true
        is DvrCommandV1.ReadProgrammes -> op.channelId.isId() && op.window.isValid() &&
            op.seriesId.isOptionalId() && op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.StartCapture -> op.channelId.isId() && op.destinationId.isId() &&
            op.window.isValid() && op.programmeId.isOptionalId() && op.padding.isValid() && op.title.isOptionalText()
        is DvrCommandV1.CaptureStatus -> op.captureId.isId()
        is DvrCommandV1.CancelCapture -> op.captureId.isId()
        is DvrCommandV1.StopCapture -> op.captureId.isId()
        is DvrCommandV1.RegisterWake -> op.wakeId.isId() && op.runAtEpochMillis.isEpoch()
        is DvrCommandV1.CancelWake -> op.wakeId.isId()
        is DvrCommandV1.CreatePlayback -> op.mediaId.isId()
        is DvrCommandV1.ListCaptures -> op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.UpdateCapture -> op.captureId.isId() && op.window.isValid() && op.padding.isValid() &&
            op.programmeId.isOptionalId() && op.title.isOptionalText()
        is DvrCommandV1.DeleteCapture -> op.captureId.isId()
        is DvrCommandV1.GetState -> op.key.isId()
        is DvrCommandV1.PutState -> op.isValid()
        is DvrCommandV1.ListState -> statePrefix.matches(op.prefix) && op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.ReadLegacy -> op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.LegacyEpisodes -> op.legacyId.isId() && op.cursor.isOptionalId() && op.pageSize.isPageSize()
        is DvrCommandV1.AdoptLegacy -> op.legacyId.isId()
    }

internal fun DvrReplyV1.isValidFor(request: DvrRequestV1): Boolean {
    if (schema != DvrProtocolV1.SCHEMA || requestId != request.requestId) return false
    return when (code) {
        DvrCodeV1.OK -> conflicts.isEmpty() && payload.matches(request.command)
        DvrCodeV1.CONFLICT -> payload == null && conflicts.size in 1..DvrProtocolV1.MAX_CONFLICTS &&
            conflicts.all { it.recordingId.isOptionalId() && it.window?.isValid() != false && it.title.isOptionalText() }
        else -> payload == null && conflicts.isEmpty()
    }
}

private fun DvrPayloadV1?.matches(command: DvrCommandV1): Boolean = when (command) {
    DvrCommandV1.Describe -> this is DvrPayloadV1.Provider && info.isValid()
    is DvrCommandV1.Schedule -> this is DvrPayloadV1.Rule && status.isValid() &&
        status.state != DvrRuleStateV1.CANCELED
    is DvrCommandV1.RuleStatus -> this is DvrPayloadV1.Rule && status.isValid() && status.ruleId == command.ruleId
    is DvrCommandV1.CancelRule -> this is DvrPayloadV1.Rule && status.isValid() &&
        status.ruleId == command.ruleId && status.state != DvrRuleStateV1.ACTIVE && status.state != DvrRuleStateV1.PAUSED
    is DvrCommandV1.UpdateRule -> this is DvrPayloadV1.Rule && status.isValid() && status.ruleId == command.ruleId
    is DvrCommandV1.ListRules -> this is DvrPayloadV1.Rules &&
        items.isPage(command.pageSize, command.cursor, nextCursor, DvrRuleStatusV1::ruleId) && items.all { it.isValid() }
    is DvrCommandV1.RuleEpisodes -> this is DvrPayloadV1.Episodes &&
        items.isPage(command.pageSize, command.cursor, nextCursor, DvrEpisodeEntryV1::episodeId) && items.all { it.isValid() }
    is DvrCommandV1.Status -> isRecording(command.recordingId)
    is DvrCommandV1.Cancel -> isRecording(command.recordingId)
    is DvrCommandV1.Stop -> isRecording(command.recordingId)
    is DvrCommandV1.UpdateRecording -> isRecording(command.recordingId)
    is DvrCommandV1.CaptureStatus -> isRecording(command.captureId, hostCapture = true)
    is DvrCommandV1.CancelCapture -> isRecording(command.captureId, hostCapture = true)
    is DvrCommandV1.StopCapture -> isRecording(command.captureId, hostCapture = true)
    is DvrCommandV1.UpdateCapture -> this is DvrPayloadV1.Recording &&
        isRecording(command.captureId, hostCapture = true) && snapshot.window == command.window
    is DvrCommandV1.StartCapture -> this is DvrPayloadV1.Recording && isRecording(hostCapture = true) &&
        snapshot.window == command.window &&
        (snapshot.metadata == null || (snapshot.metadata.channelId == command.channelId &&
            (command.programmeId == null || snapshot.metadata.programmeId == command.programmeId)))
    is DvrCommandV1.ListRecordings -> this is DvrPayloadV1.Recordings &&
        items.isPage(command.pageSize, command.cursor, nextCursor, DvrRecordingV1::recordingId) &&
        items.all { it.isValid() && (command.state == null || command.state == it.state) &&
            (command.ruleId == null || command.ruleId == it.ruleId) }
    is DvrCommandV1.ListCaptures -> this is DvrPayloadV1.Recordings &&
        items.isPage(command.pageSize, command.cursor, nextCursor, DvrRecordingV1::recordingId) &&
        items.all { it.isValid() && it.ruleId == null && (command.state == null || command.state == it.state) }
    is DvrCommandV1.ReadProgrammes -> this is DvrPayloadV1.Programmes &&
        items.isPage(command.pageSize, command.cursor, nextCursor, DvrProgrammeV1::programmeId) &&
        coverage?.isValid() != false &&
        items.all { it.isValid() && it.channelId == command.channelId && it.window.overlaps(command.window) &&
            (command.seriesId == null || command.seriesId == it.seriesId) }
    DvrCommandV1.ListDestinations, DvrCommandV1.HostDestinations ->
        this is DvrPayloadV1.Destinations && items.size <= DvrProtocolV1.MAX_DESTINATIONS &&
            items.all { it.isValid() } && items.distinctBy { it.destinationId }.size == items.size
    is DvrCommandV1.Playback, is DvrCommandV1.CreatePlayback ->
        this is DvrPayloadV1.Playback && handoff.token.isId() && handoff.expiresAtEpochMillis.isEpoch()
    is DvrCommandV1.GetState -> this is DvrPayloadV1.State && nextCursor == null && entries.size <= 1 &&
        entries.all { it.isValid() && it.key == command.key }
    is DvrCommandV1.ListState -> this is DvrPayloadV1.State &&
        entries.isPage(command.pageSize, command.cursor, nextCursor, DvrStateEntryV1::key) &&
        entries.all { it.isValid() && it.key.startsWith(command.prefix) } &&
        entries.zipWithNext().all { (left, right) -> left.key < right.key }
    is DvrCommandV1.ReadLegacy -> this is DvrPayloadV1.LegacyRules &&
        items.isPage(command.pageSize, command.cursor, nextCursor, DvrLegacyRuleV1::legacyId) && items.all { it.isValid() }
    is DvrCommandV1.LegacyEpisodes -> this is DvrPayloadV1.LegacyEpisodes &&
        items.isPage(command.pageSize, command.cursor, nextCursor) { it.entry.episodeId } && items.all { it.isValid() }
    is DvrCommandV1.RegisterWake, is DvrCommandV1.CancelWake, is DvrCommandV1.Wake,
    is DvrCommandV1.SetEpisode, is DvrCommandV1.DeleteRecording, is DvrCommandV1.Notify,
    is DvrCommandV1.DeleteCapture, is DvrCommandV1.PutState, is DvrCommandV1.AdoptLegacy,
    -> this == DvrPayloadV1.Ack
}

private fun <T> List<T>.isPage(sizeLimit: Int, cursor: String?, next: String?, id: (T) -> String) =
    size <= sizeLimit && distinctBy(id).size == size &&
        (next == null || (isNotEmpty() && next.isId() && next != cursor))

private fun DvrProviderInfoV1.isValid() =
    providerId.isId() && displayName.isDisplayText() && version.isDisplayText() &&
        operations.size <= DvrOperationV1.entries.size && DvrOperationV1.DESCRIBE in operations &&
        operations.all { it.endpoint == DvrEndpointV1.PROVIDER } && operations.distinct().size == operations.size &&
        ruleKinds.size <= DvrRuleKindV1.entries.size && ruleKinds.distinct().size == ruleKinds.size &&
        (ruleKinds.isNotEmpty() == (DvrOperationV1.SCHEDULE in operations)) &&
        destinationKinds.size <= DvrDestinationKindV1.entries.size &&
        destinationKinds.distinct().size == destinationKinds.size &&
        (!commercialSkipping || commercialMarkers) &&
        limitations.size <= DvrProtocolV1.MAX_LIMITATIONS && limitations.all { it.isDisplayText() }

private fun DvrRuleDetailV1.isValid() =
    channelId.isId() && destinationId.isId() && title.isDisplayText() && padding.isValid() &&
        seriesId.isOptionalId() && (kind == DvrRuleKindV1.SERIES) == (seriesId != null) &&
        scheduledCount.isCount() && conflictCount.isCount() && createdAtEpochMillis.isEpoch() &&
        checkedAtEpochMillis?.isEpoch() != false && message.isOptionalText()

private fun DvrRuleStatusV1.isValid() =
    ruleId.isId() && (nextWindow == null || (state == DvrRuleStateV1.ACTIVE && nextWindow.isValid())) &&
        detail?.isValid() != false

private fun DvrEpisodeV1.isValid() =
    evidence.size <= DvrProtocolV1.MAX_EVIDENCE_TOKENS && evidence.distinct().size == evidence.size &&
        evidence.all { it.isEvidence() } && label.isOptionalText()

private fun String.isEvidence() =
    length <= DvrProtocolV1.MAX_TEXT_CHARS && none { it.isISOControl() } && evidenceToken.matches(this)

private fun DvrProgrammeV1.isValid() =
    channelId.isId() && programmeId.isId() && title.isDisplayText() && window.isValid() && seriesId.isOptionalId() &&
        episode?.isValid() != false

private fun DvrEpisodeEntryV1.isValid() =
    episodeId.isId() && window.isValid() && title.isDisplayText() && label.isOptionalText() &&
        recordingId.isOptionalId() && detail.isOptionalText()

private fun DvrLegacyRuleV1.isValid() =
    legacyId.isId() && channelId.isId() && title.isDisplayText() && seriesId.isId() && padding.isValid() &&
        createdAtEpochMillis.isEpoch() && destinationId.isId()

private fun DvrLegacyEpisodeV1.isValid() =
    entry.isValid() && generatedPadding.isValid() &&
        evidence.size <= DvrProtocolV1.MAX_EVIDENCE_TOKENS * 2 && evidence.distinct().size == evidence.size &&
        evidence.all { it.isEvidence() }

private fun DvrDestinationV1.isValid() =
    destinationId.isId() && displayName.isDisplayText() &&
        (!writable || available) && (availableBytes == null || availableBytes >= 0)

private fun DvrPayloadV1?.isRecording(expectedId: String? = null, hostCapture: Boolean = false) =
    this is DvrPayloadV1.Recording && snapshot.isValid() &&
        (expectedId == null || snapshot.recordingId == expectedId) && (!hostCapture || snapshot.ruleId == null)

private fun DvrRecordingV1.isValid() =
    recordingId.isId() && ruleId.isOptionalId() && revision >= 0 && window.isValid() &&
        (progress == null || (progress.capturedMillis in 0..MAX_CAPTURE_MS && progress.bytesWritten >= 0)) &&
        (metadata == null || (metadata.title.isDisplayText() && metadata.channelId.isId() &&
            metadata.programmeId.isOptionalId())) &&
        padding?.isValid() != false && destinationId.isOptionalId() && message.isOptionalText() &&
        if (state == DvrRecordingStateV1.COMPLETED) {
            media?.isValid() == true && interruption == null
        } else {
            media == null && ((state == DvrRecordingStateV1.INTERRUPTED) == (interruption != null))
        }

private fun DvrMediaV1.isValid(): Boolean {
    if (!mediaId.isId() || durationMillis !in 1..MAX_CAPTURE_MS) return false
    if (commercialMarkers.size > DvrProtocolV1.MAX_COMMERCIAL_MARKERS) return false
    return commercialMarkers.all { it.startMillis in 0..<it.endMillis && it.endMillis <= durationMillis } &&
        commercialMarkers.zipWithNext().all { (left, right) -> left.endMillis <= right.startMillis }
}

/**
 * A recording's window may move while it is scheduled, and a running recording may change its end
 * (padding edits apply live). Its start is fixed once it runs; a finished recording never changes.
 */
private fun windowChangeAllowed(previous: DvrRecordingV1, next: DvrRecordingV1): Boolean = when {
    next.window == previous.window -> true
    previous.state == DvrRecordingStateV1.SCHEDULED -> true
    previous.state == DvrRecordingStateV1.RECORDING ->
        next.window.startEpochMillis == previous.window.startEpochMillis
    else -> false
}

internal fun isValidTransition(previous: DvrRecordingV1, next: DvrRecordingV1): Boolean {
    if (!previous.isValid() || !next.isValid()) return false
    if (previous == next) return true
    if (next.recordingId != previous.recordingId || next.ruleId != previous.ruleId ||
        !windowChangeAllowed(previous, next) || next.revision != previous.revision + 1
    ) return false

    val state = next.state
    return when (previous.state) {
        DvrRecordingStateV1.SCHEDULED -> state in setOf(
            DvrRecordingStateV1.SCHEDULED, DvrRecordingStateV1.RECORDING, DvrRecordingStateV1.CANCELED,
            DvrRecordingStateV1.FAILED, DvrRecordingStateV1.INTERRUPTED,
        )
        DvrRecordingStateV1.RECORDING -> state != DvrRecordingStateV1.SCHEDULED
        DvrRecordingStateV1.FINALIZING -> state in setOf(
            DvrRecordingStateV1.FINALIZING, DvrRecordingStateV1.COMPLETED, DvrRecordingStateV1.CANCELED,
            DvrRecordingStateV1.FAILED, DvrRecordingStateV1.INTERRUPTED,
        )
        DvrRecordingStateV1.INTERRUPTED -> state != DvrRecordingStateV1.SCHEDULED
        DvrRecordingStateV1.COMPLETED, DvrRecordingStateV1.CANCELED, DvrRecordingStateV1.FAILED -> false
    }
}
