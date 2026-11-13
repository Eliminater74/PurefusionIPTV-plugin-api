package dev.eliminater.purefusioniptv.plugin.api.dvr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The SDK 1.1.0 corrections that make the DVR contract carry a complete recorder. */
class DvrProtocolV1CorrectionsTest {
    private val window = DvrWindowV1(1_800_000_000_000, 1_800_003_600_000)
    private val capture = DvrRecordingV1(
        recordingId = "cap-7", state = DvrRecordingStateV1.SCHEDULED, revision = 0, window = window,
        padding = DvrPaddingV1(60, 120), destinationId = "default",
        metadata = DvrRecordingMetadataV1("Evening Show", "ch-1", "p-9"),
    )

    private fun <T : DvrCommandV1> roundTrip(command: T, endpoint: DvrEndpointV1): DvrRequestV1 {
        val request = DvrRequestV1("req-1", command)
        val bytes = requireNotNull(DvrProtocolV1.encodeRequest(request)) { "encode $command" }
        assertEquals(request, DvrProtocolV1.decodeRequest(bytes, endpoint))
        assertNull(
            "$command must not route to the other endpoint",
            DvrProtocolV1.decodeRequest(bytes, if (endpoint == DvrEndpointV1.HOST) DvrEndpointV1.PROVIDER else DvrEndpointV1.HOST),
        )
        return request
    }

    private fun assertReply(request: DvrRequestV1, payload: DvrPayloadV1?, valid: Boolean = true) {
        val reply = DvrReplyV1(request.requestId, DvrCodeV1.OK, payload)
        val encoded = DvrProtocolV1.encodeReply(request, reply)
        if (!valid) {
            assertNull("reply $payload must be refused for ${request.command}", encoded)
            return
        }
        assertEquals(reply, DvrProtocolV1.decodeReply(request, requireNotNull(encoded) { "reply $payload" }))
    }

    @Test fun `every new provider operation routes to the provider and binds its reply`() {
        val rule = DvrRuleStatusV1(
            "rule-1", DvrRuleStateV1.PAUSED,
            detail = DvrRuleDetailV1(
                kind = DvrRuleKindV1.SERIES, channelId = "ch-1", destinationId = "default", title = "Evening Show",
                padding = DvrPaddingV1(60, 120), seriesId = "srs-1", newEpisodesOnly = true,
                outcome = DvrRuleOutcomeV1.PAUSED, scheduledCount = 0, conflictCount = 1,
                createdAtEpochMillis = 1_700_000_000_000, message = "Paused",
            ),
        )
        assertReply(roundTrip(DvrCommandV1.ListRules(pageSize = 5), DvrEndpointV1.PROVIDER), DvrPayloadV1.Rules(listOf(rule), "rules-2"))
        val update = roundTrip(DvrCommandV1.UpdateRule("rule-1", paused = true), DvrEndpointV1.PROVIDER)
        assertReply(update, DvrPayloadV1.Rule(rule))
        assertReply(update, DvrPayloadV1.Rule(rule.copy(ruleId = "rule-2")), valid = false)
        val episodes = roundTrip(DvrCommandV1.RuleEpisodes("rule-1"), DvrEndpointV1.PROVIDER)
        val episode = DvrEpisodeEntryV1("ep-1", DvrEpisodeStateV1.EXCLUDED, window, "Evening Show", "S02E05", "cap-7", "Excluded by you")
        assertReply(episodes, DvrPayloadV1.Episodes(listOf(episode)))
        assertReply(episodes, DvrPayloadV1.Episodes(listOf(episode, episode)), valid = false)
        assertReply(roundTrip(DvrCommandV1.SetEpisode("rule-1", "ep-1", excluded = true), DvrEndpointV1.PROVIDER), DvrPayloadV1.Ack)
        val padding = roundTrip(DvrCommandV1.UpdateRecording("cap-7", DvrPaddingV1(0, 600)), DvrEndpointV1.PROVIDER)
        assertReply(padding, DvrPayloadV1.Recording(capture))
        assertReply(padding, DvrPayloadV1.Recording(capture.copy(recordingId = "cap-8")), valid = false)
        assertReply(roundTrip(DvrCommandV1.DeleteRecording("cap-7"), DvrEndpointV1.PROVIDER), DvrPayloadV1.Ack)
        assertReply(roundTrip(DvrCommandV1.Notify(DvrEventV1.GUIDE_UPDATED), DvrEndpointV1.PROVIDER), DvrPayloadV1.Ack)
        val cancel = roundTrip(DvrCommandV1.CancelRule("rule-1", cancelUpcoming = true), DvrEndpointV1.PROVIDER)
        assertReply(cancel, DvrPayloadV1.Rule(rule.copy(state = DvrRuleStateV1.CANCELED)))
        assertReply(cancel, DvrPayloadV1.Rule(rule), valid = false)
    }

    @Test fun `an update that changes nothing is refused`() {
        assertNull(DvrProtocolV1.encodeRequest(DvrRequestV1("r", DvrCommandV1.UpdateRule("rule-1"))))
        assertNotNull(DvrProtocolV1.encodeRequest(DvrRequestV1("r", DvrCommandV1.UpdateRule("rule-1", refresh = true))))
    }

    @Test fun `host capture listing update and delete carry host snapshots only`() {
        val list = roundTrip(DvrCommandV1.ListCaptures(state = DvrRecordingStateV1.SCHEDULED), DvrEndpointV1.HOST)
        assertReply(list, DvrPayloadV1.Recordings(listOf(capture), "after-cap-7"))
        assertReply(list, DvrPayloadV1.Recordings(listOf(capture.copy(ruleId = "rule-1"))), valid = false)
        assertReply(list, DvrPayloadV1.Recordings(listOf(capture.copy(state = DvrRecordingStateV1.RECORDING, revision = 1))), valid = false)
        val moved = DvrWindowV1(window.startEpochMillis + 600_000, window.endEpochMillis + 600_000)
        val update = roundTrip(DvrCommandV1.UpdateCapture("cap-7", moved, DvrPaddingV1(60, 120), "p-9"), DvrEndpointV1.HOST)
        assertReply(update, DvrPayloadV1.Recording(capture.copy(window = moved, revision = 1)))
        assertReply(update, DvrPayloadV1.Recording(capture), valid = false)
        assertReply(roundTrip(DvrCommandV1.DeleteCapture("cap-7"), DvrEndpointV1.HOST), DvrPayloadV1.Ack)
        val withdraw = roundTrip(DvrCommandV1.CancelCapture("cap-7", scheduledOnly = true), DvrEndpointV1.HOST)
        assertEquals(true, (withdraw.command as DvrCommandV1.CancelCapture).scheduledOnly)
        assertReply(withdraw, DvrPayloadV1.Recording(capture.copy(state = DvrRecordingStateV1.CANCELED, revision = 1)))
        val titled = roundTrip(
            DvrCommandV1.StartCapture("ch-1", "default", window, title = "Manual recording"), DvrEndpointV1.HOST,
        )
        assertReply(titled, DvrPayloadV1.Recording(capture.copy(metadata = null)))
    }

    @Test fun `programmes carry episode evidence coverage and new-episode marking`() {
        val read = roundTrip(DvrCommandV1.ReadProgrammes("ch-1", window, seriesId = "srs-1"), DvrEndpointV1.HOST)
        val programme = DvrProgrammeV1(
            "ch-1", "p-9", "Evening Show", window, "srs-1", isNewEpisode = true,
            episode = DvrEpisodeV1(listOf("se:2:5:0", "p:EP012345.0005", "t:the pilot"), "S02E05"),
        )
        assertReply(read, DvrPayloadV1.Programmes(listOf(programme), coverage = window, marksNewEpisodes = true))
        listOf(
            programme.copy(episode = DvrEpisodeV1(listOf("x:1"))),
            programme.copy(episode = DvrEpisodeV1(listOf("se:"))),
            programme.copy(episode = DvrEpisodeV1(listOf("t:a", "t:a"))),
            programme.copy(episode = DvrEpisodeV1(listOf("p:1", "p:2", "p:3", "p:4", "p:5"))),
            programme.copy(seriesId = "srs-2"),
        ).forEach { assertReply(read, DvrPayloadV1.Programmes(listOf(it)), valid = false) }
        assertReply(read, DvrPayloadV1.Programmes(emptyList(), coverage = DvrWindowV1(5, 1)), valid = false)
    }

    @Test fun `durable state is bounded keyed atomic and ordered`() {
        val get = roundTrip(DvrCommandV1.GetState("rule.r1"), DvrEndpointV1.HOST)
        assertReply(get, DvrPayloadV1.State(listOf(DvrStateEntryV1("rule.r1", """{"v":1}"""))))
        assertReply(get, DvrPayloadV1.State(emptyList()))
        assertReply(get, DvrPayloadV1.State(listOf(DvrStateEntryV1("rule.r2", "{}"))), valid = false)
        val put = roundTrip(
            DvrCommandV1.PutState(listOf(DvrStateEntryV1("rule.r1", "{}")), deletes = listOf("journal.old")), DvrEndpointV1.HOST,
        )
        assertReply(put, DvrPayloadV1.Ack)
        listOf(
            DvrCommandV1.PutState(),
            DvrCommandV1.PutState(listOf(DvrStateEntryV1("a", "1")), deletes = listOf("a")),
            DvrCommandV1.PutState(listOf(DvrStateEntryV1("a", "x".repeat(DvrProtocolV1.MAX_STATE_VALUE_CHARS + 1)))),
            DvrCommandV1.PutState((1..DvrProtocolV1.MAX_STATE_PUTS + 1).map { DvrStateEntryV1("k$it", "") }),
            DvrCommandV1.PutState(listOf(DvrStateEntryV1("../etc", "1"))),
        ).forEach { assertNull("$it", DvrProtocolV1.encodeRequest(DvrRequestV1("p", it))) }
        val list = roundTrip(DvrCommandV1.ListState("rule.", pageSize = 3), DvrEndpointV1.HOST)
        val a = DvrStateEntryV1("rule.a", "1")
        val b = DvrStateEntryV1("rule.b", "2")
        assertReply(list, DvrPayloadV1.State(listOf(a, b), "rule.b"))
        assertReply(list, DvrPayloadV1.State(listOf(b, a)), valid = false)
        assertReply(list, DvrPayloadV1.State(listOf(DvrStateEntryV1("journal.a", "1"))), valid = false)
        assertNull(DvrProtocolV1.encodeRequest(DvrRequestV1("p", DvrCommandV1.ListState("a/b"))))
    }

    @Test fun `legacy rules and their episode history page through the host`() {
        val legacy = DvrLegacyRuleV1(
            "legacy-4", "ch-1", "Evening Show", "srs-1", newEpisodesOnly = false,
            padding = DvrPaddingV1(60, 120), paused = false, createdAtEpochMillis = 1_700_000_000_000,
        )
        assertEquals(DvrProtocolV1.DEFAULT_DESTINATION, legacy.destinationId)
        assertReply(roundTrip(DvrCommandV1.ReadLegacy(), DvrEndpointV1.HOST), DvrPayloadV1.LegacyRules(listOf(legacy)))
        val history = roundTrip(DvrCommandV1.LegacyEpisodes("legacy-4"), DvrEndpointV1.HOST)
        val entry = DvrLegacyEpisodeV1(
            DvrEpisodeEntryV1("occ-3", DvrEpisodeStateV1.CAPTURED, window, "Evening Show", "S02E05", "cap-7"),
            evidence = listOf("p:EP012345.0005", "se:2:5:0"), generatedPadding = DvrPaddingV1(60, 120),
        )
        assertReply(history, DvrPayloadV1.LegacyEpisodes(listOf(entry), "occ-3"))
        assertReply(history, DvrPayloadV1.LegacyEpisodes(listOf(entry.copy(evidence = listOf("bad")))), valid = false)
        assertReply(roundTrip(DvrCommandV1.AdoptLegacy("legacy-4"), DvrEndpointV1.HOST), DvrPayloadV1.Ack)
    }

    @Test fun `conflict replies may name the title that holds the slot`() {
        val request = DvrRequestV1("s", DvrCommandV1.StartCapture("ch-1", "default", window))
        val conflict = DvrReplyV1(
            "s", DvrCodeV1.CONFLICT,
            conflicts = listOf(DvrConflictV1(DvrConflictReasonV1.CAPTURE_LIMIT, window, "cap-2", "Late News")),
        )
        assertEquals(conflict, DvrProtocolV1.decodeReply(request, requireNotNull(DvrProtocolV1.encodeReply(request, conflict))))
        val unavailable = DvrReplyV1("s", DvrCodeV1.UNAVAILABLE)
        assertEquals(unavailable, DvrProtocolV1.decodeReply(request, requireNotNull(DvrProtocolV1.encodeReply(request, unavailable))))
    }

    @Test fun `scheduled windows may move and running recordings keep their start`() {
        val scheduled = capture
        val moved = scheduled.copy(revision = 1, window = DvrWindowV1(window.startEpochMillis + 60_000, window.endEpochMillis))
        assertTrue(DvrProtocolV1.isValidNextSnapshot(scheduled, moved))
        val running = moved.copy(state = DvrRecordingStateV1.RECORDING, revision = 2)
        val extended = running.copy(revision = 3, window = running.window.copy(endEpochMillis = running.window.endEpochMillis + 600_000))
        assertTrue(DvrProtocolV1.isValidNextSnapshot(running, extended))
        val shiftedStart = running.copy(revision = 3, window = window)
        assertFalse(DvrProtocolV1.isValidNextSnapshot(running, shiftedStart))
    }

    @Test fun `a provider description may advertise the new operations but never host ones`() {
        val describe = DvrRequestV1("d", DvrCommandV1.Describe)
        val info = DvrProviderInfoV1(
            providerId = "dvr-1", displayName = "DVR", version = "1.0.0",
            operations = listOf(DvrOperationV1.DESCRIBE, DvrOperationV1.SCHEDULE, DvrOperationV1.LIST_RULES,
                DvrOperationV1.UPDATE_RULE, DvrOperationV1.NOTIFY),
            ruleKinds = listOf(DvrRuleKindV1.ONCE, DvrRuleKindV1.SERIES),
        )
        assertReply(describe, DvrPayloadV1.Provider(info))
        assertReply(describe, DvrPayloadV1.Provider(info.copy(operations = info.operations + DvrOperationV1.PUT_STATE)), valid = false)
    }
}
