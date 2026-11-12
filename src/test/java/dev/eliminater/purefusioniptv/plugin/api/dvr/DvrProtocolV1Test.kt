package dev.eliminater.purefusioniptv.plugin.api.dvr

import org.junit.Assert.*
import org.junit.Test

class DvrProtocolV1Test {
    private val window = DvrWindowV1(1_800_000_000_000, 1_800_003_600_000)
    private val schedule = DvrScheduleV1(
        channelId = "channel-42",
        destinationId = "local-1",
        window = window,
        programmeId = "programme-1",
        padding = DvrPaddingV1(60, 120),
        rule = DvrRuleV1.Series("series-12", newEpisodesOnly = true),
    )
    private val request = DvrRequestV1("req-1", DvrCommandV1.Schedule(schedule))
    private val recording = DvrRecordingV1("rec-1", DvrRecordingStateV1.RECORDING, 1, window)

    @Test fun `programme schedule survives the wire and has a fixed operation name`() {
        val bytes = requireNotNull(DvrProtocolV1.encodeRequest(request))
        assertTrue(bytes.decodeToString().contains("\"schema\":\"pf.dvr.v1\""))
        assertTrue(bytes.decodeToString().contains("\"kind\":\"schedule\""))
        assertEquals(request, DvrProtocolV1.decodeRequest(bytes, DvrEndpointV1.PROVIDER))
        assertNull(DvrProtocolV1.decodeRequest(bytes, DvrEndpointV1.HOST))
    }

    @Test fun `capture exchange carries scoped IDs and returns a matching snapshot`() {
        val capture = DvrRequestV1("capture-1", DvrCommandV1.StartCapture(
            channelId = "channel-42", destinationId = "local-1", window = window,
            programmeId = "programme-1", padding = schedule.padding,
        ))
        val bytes = requireNotNull(DvrProtocolV1.encodeRequest(capture))
        assertEquals(capture, DvrProtocolV1.decodeRequest(bytes, DvrEndpointV1.HOST))
        assertNull(DvrProtocolV1.decodeRequest(bytes, DvrEndpointV1.PROVIDER))
        val result = DvrReplyV1(capture.requestId, DvrCodeV1.OK, DvrPayloadV1.Recording(recording))
        val reply = requireNotNull(DvrProtocolV1.encodeReply(capture, result))
        assertEquals(result, DvrProtocolV1.decodeReply(capture, reply))
        assertNull(DvrProtocolV1.decodeReply(capture.copy(requestId = "other"), reply))
        assertNull(DvrProtocolV1.decodeReply(request, reply))
    }

    @Test fun `rejects invalid shapes and transport limits before dispatch`() {
        val good = """{"schema":"pf.dvr.v1","requestId":"req-1","command":{"kind":"describe"}}"""
        assertNotNull(DvrProtocolV1.decodeRequest(good.encodeToByteArray(), DvrEndpointV1.PROVIDER))
        val invalid = listOf(
            "[]", "{", "$good{}", good.replace("pf.dvr.v1", "pf.dvr.v2"),
            good.replace("\"schema\":\"pf.dvr.v1\",", ""),
            good.replace("\"requestId\":\"req-1\"", "\"requestId\":null"),
            good.replace("\"requestId\":\"req-1\"", "\"requestId\":\"req-1\",\"requestId\":\"req-2\""),
            good.replace("describe", "unknown"),
            good.replace("\"kind\":\"describe\"", "\"kind\":\"describe\",\"extra\":0"),
            good.replace("req-1", "file:///tmp/recording"),
            "[".repeat(50) + "0" + "]".repeat(50),
            good.padEnd(DvrProtocolV1.MAX_REQUEST_BYTES + 1),
        )
        for (json in invalid) {
            assertNull(DvrProtocolV1.decodeRequest(json.encodeToByteArray(), DvrEndpointV1.PROVIDER))
        }
        val invalidUtf8 = good.encodeToByteArray().apply { this[2] = 0xff.toByte() }
        assertNull(DvrProtocolV1.decodeRequest(invalidUtf8, DvrEndpointV1.PROVIDER))
        val limit = DvrProtocolV1.MAX_RESPONSE_BYTES
        assertNull(DvrProtocolV1.decodeReply(request, ByteArray(limit + 1)))
    }

    @Test fun `validates windows padding recurring rules and page size`() {
        val invalidSchedules = listOf(
            schedule.copy(window = window.copy(endEpochMillis = window.startEpochMillis)),
            schedule.copy(window = DvrWindowV1(Long.MIN_VALUE, Long.MAX_VALUE)),
            schedule.copy(padding = DvrPaddingV1(-1, 0)),
            schedule.copy(rule = DvrRuleV1.Weekly(listOf(1, 1), "America/New_York")),
            schedule.copy(rule = DvrRuleV1.Weekly(listOf(1), "../local")),
        )
        invalidSchedules.forEach {
            assertNull(DvrProtocolV1.encodeRequest(request.copy(command = DvrCommandV1.Schedule(it))))
        }
        val valid = schedule.copy(rule = DvrRuleV1.Weekly(listOf(1, 3), "America/New_York"))
        assertNotNull(DvrProtocolV1.encodeRequest(request.copy(command = DvrCommandV1.Schedule(valid))))
        assertNull(DvrProtocolV1.encodeRequest(DvrRequestV1("page", DvrCommandV1.ListRecordings(
            pageSize = DvrProtocolV1.MAX_PAGE_SIZE + 1,
        ))))
    }

    @Test fun `state revisions reject illegal adjacent transitions and stale snapshots`() {
        val scheduled = recording.copy(state = DvrRecordingStateV1.SCHEDULED, revision = 0)
        assertTrue(DvrProtocolV1.isValidNextSnapshot(scheduled, recording))
        assertTrue(DvrProtocolV1.isValidNextSnapshot(recording, recording))
        assertFalse(DvrProtocolV1.isValidNextSnapshot(recording, scheduled))
        assertFalse(DvrProtocolV1.isValidNextSnapshot(scheduled, recording.copy(revision = 0)))
        assertFalse(DvrProtocolV1.isValidNextSnapshot(recording, recording.copy(
            state = DvrRecordingStateV1.SCHEDULED, revision = 2,
        )))
        val interrupted = recording.copy(
            state = DvrRecordingStateV1.INTERRUPTED, revision = 2, interruption = DvrInterruptionV1.HOST_RESTART,
        )
        assertTrue(DvrProtocolV1.isValidNextSnapshot(recording, interrupted))
        assertTrue(DvrProtocolV1.isValidNextSnapshot(interrupted, recording.copy(revision = 3)))
        val canceled = recording.copy(state = DvrRecordingStateV1.CANCELED, revision = 2)
        assertFalse(DvrProtocolV1.isValidNextSnapshot(canceled, recording.copy(revision = 3)))
    }

    @Test fun `errors expose fixed codes and only conflicts carry conflict detail`() {
        val unsupported = DvrReplyV1(request.requestId, DvrCodeV1.UNSUPPORTED)
        assertEquals(unsupported, DvrProtocolV1.decodeReply(
            request, requireNotNull(DvrProtocolV1.encodeReply(request, unsupported)),
        ))
        val conflict = DvrReplyV1(request.requestId, DvrCodeV1.CONFLICT, conflicts = listOf(
            DvrConflictV1(DvrConflictReasonV1.CAPTURE_LIMIT, window),
        ))
        assertNotNull(DvrProtocolV1.encodeReply(request, conflict))
        assertNull(DvrProtocolV1.encodeReply(request, conflict.copy(code = DvrCodeV1.BUSY)))
        assertNull(DvrProtocolV1.encodeReply(request, unsupported.copy(
            payload = DvrPayloadV1.Recording(recording),
        )))
    }

    @Test fun `completed discovery validates state filters media markers and metadata`() {
        val call = DvrRequestV1("completed", DvrCommandV1.ListRecordings(
            state = DvrRecordingStateV1.COMPLETED, ruleId = "rule-1", pageSize = 1,
        ))
        val media = DvrMediaV1("media-1", 360_000, listOf(DvrCommercialMarkerV1(60_000, 90_000)))
        val completed = recording.copy(
            state = DvrRecordingStateV1.COMPLETED, media = media, ruleId = "rule-1",
            metadata = DvrRecordingMetadataV1("Synthetic programme", "channel-42", "programme-1"),
        )
        val reply = DvrReplyV1(call.requestId, DvrCodeV1.OK, DvrPayloadV1.Recordings(listOf(completed), "next-page"))
        assertEquals(reply, DvrProtocolV1.decodeReply(call, requireNotNull(DvrProtocolV1.encodeReply(call, reply))))
        val invalid = listOf(
            completed.copy(state = DvrRecordingStateV1.RECORDING),
            completed.copy(media = null),
            completed.copy(ruleId = "other"),
            completed.copy(metadata = completed.metadata?.copy(channelId = "https://example.invalid")),
            completed.copy(media = media.copy(commercialMarkers = listOf(
                DvrCommercialMarkerV1(1, 3), DvrCommercialMarkerV1(2, 4),
            ))),
        )
        invalid.forEach {
            assertNull(DvrProtocolV1.encodeReply(call, reply.copy(payload = DvrPayloadV1.Recordings(listOf(it)))))
        }
        assertNull(DvrProtocolV1.encodeReply(call, reply.copy(payload = DvrPayloadV1.Recordings(listOf(completed, completed)))))
    }

    @Test fun `rejects coerced scalars but accepts any order of valid fields`() {
        val reordered = """{"command":{"pageSize":1,"kind":"list_recordings"},"requestId":"page","schema":"pf.dvr.v1"}"""
        val decoded = DvrProtocolV1.decodeRequest(reordered.encodeToByteArray(), DvrEndpointV1.PROVIDER)
        assertEquals(DvrRequestV1("page", DvrCommandV1.ListRecordings(pageSize = 1)), decoded)
        for (value in listOf("\"1\"", "1.0", "1e0", "true", "9223372036854775808")) {
            assertNull(DvrProtocolV1.decodeRequest(
                reordered.replace("\"pageSize\":1", "\"pageSize\":$value").encodeToByteArray(), DvrEndpointV1.PROVIDER,
            ))
        }
    }

    @Test fun `playback handoff and wake replies stay bound to the corresponding command`() {
        val playback = DvrRequestV1("play", DvrCommandV1.CreatePlayback("media-1"))
        val handoff = DvrPayloadV1.Playback(DvrPlaybackV1("playback-token", window.endEpochMillis))
        val reply = DvrReplyV1(playback.requestId, DvrCodeV1.OK, handoff)
        val wake = playback.copy(command = DvrCommandV1.RegisterWake("wake-1", window.startEpochMillis))
        assertNotNull(DvrProtocolV1.encodeReply(playback, reply))
        assertNull(DvrProtocolV1.encodeReply(wake, reply))
        assertNotNull(DvrProtocolV1.encodeReply(wake, reply.copy(payload = DvrPayloadV1.Ack)))
    }
}
