package dev.example.purefusiondvr

import dev.eliminater.purefusioniptv.plugin.api.Plugin
import dev.eliminater.purefusioniptv.plugin.api.PluginCapability
import dev.eliminater.purefusioniptv.plugin.api.dvr.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.ServiceLoader

class SyntheticDvrPluginTest {
    private val window = DvrWindowV1(1_800_000_000_000, 1_800_003_600_000)
    private val schedule = DvrScheduleV1("synthetic-channel", "synthetic-device", window, "synthetic-programme")
    private val host = FakeHost()
    private val plugin = SyntheticDvrPlugin().apply { onDvrLoad(host) }

    @Test fun `service registration and future manifest agree with entry metadata`() {
        val entry = ServiceLoader.load(Plugin::class.java).single()
        val manifest = JSONObject(File("manifest.json").readText())
        assertTrue(entry is DvrProvider)
        assertEquals(entry.javaClass.name, manifest.getString("entryPoint"))
        assertEquals(entry.id, manifest.getString("pluginId"))
        assertEquals(entry.name, manifest.getString("displayName"))
        assertEquals(entry.author, manifest.getString("publisher"))
        assertEquals(entry.version, manifest.getString("version"))
        assertEquals(entry.minAppVersion, manifest.getString("minHostVersion"))
        assertEquals(4, entry.apiLevel)
        assertEquals(entry.apiLevel, manifest.getInt("apiLevel"))
        assertEquals(listOf(PluginCapability.DVR_PROVIDER), entry.capabilities)
        assertEquals(DvrProtocolV1.MANIFEST_CATEGORY, manifest.getString("category"))
        assertEquals(DvrProtocolV1.MANIFEST_CAPABILITY, manifest.getJSONArray("capabilities").getString(0))
    }

    @Test fun `schedule discovery status and cancellation use only scoped host requests`() {
        val description = send("describe", DvrCommandV1.Describe).payload as DvrPayloadV1.Provider
        assertEquals(listOf(DvrRuleKindV1.ONCE), description.info.ruleKinds)
        assertTrue(DvrOperationV1.CANCEL in description.info.operations)
        assertFalse(DvrOperationV1.STOP in description.info.operations)

        val destinations = send("destinations", DvrCommandV1.ListDestinations).payload as DvrPayloadV1.Destinations
        assertEquals(schedule.destinationId, destinations.items.single().destinationId)

        val accepted = send("schedule", DvrCommandV1.Schedule(schedule))
        assertEquals(DvrCodeV1.OK, accepted.code)
        val ruleId = (accepted.payload as DvrPayloadV1.Rule).status.ruleId
        assertEquals(accepted, send("schedule", DvrCommandV1.Schedule(schedule)))
        assertEquals(1, host.started)
        assertEquals(DvrCodeV1.REQUEST_ID_REUSED, send("schedule",
            DvrCommandV1.Schedule(schedule.copy(programmeId = "other-programme"))).code)

        val activeRule = send("rule-status", DvrCommandV1.RuleStatus(ruleId)).payload as DvrPayloadV1.Rule
        assertEquals(DvrRuleStateV1.ACTIVE, activeRule.status.state)
        val listed = send("list", DvrCommandV1.ListRecordings(ruleId = ruleId)).payload as DvrPayloadV1.Recordings
        val recordingId = listed.items.single().recordingId
        val before = send("status", DvrCommandV1.Status(recordingId)).payload as DvrPayloadV1.Recording
        assertEquals(DvrRecordingStateV1.SCHEDULED, before.snapshot.state)

        val canceled = send("cancel", DvrCommandV1.Cancel(recordingId))
        assertEquals(DvrRecordingStateV1.CANCELED, (canceled.payload as DvrPayloadV1.Recording).snapshot.state)
        assertEquals(canceled, send("cancel", DvrCommandV1.Cancel(recordingId)))
        assertEquals(1, host.canceled)
        val after = send("status-after", DvrCommandV1.Status(recordingId)).payload as DvrPayloadV1.Recording
        assertTrue(DvrProtocolV1.isValidNextSnapshot(before.snapshot, after.snapshot))
    }

    @Test fun `a dropped acceptance is retried without duplicating host work`() {
        plugin.onDvrLoad { bytes ->
            val reply = host.exchange(bytes)
            if (host.dropOnce) { host.dropOnce = false; null } else reply
        }
        val command = DvrCommandV1.Schedule(schedule)
        assertEquals(DvrCodeV1.BUSY, send("schedule", command).code)
        assertEquals(DvrCodeV1.REQUEST_ID_REUSED, send("schedule",
            DvrCommandV1.Schedule(schedule.copy(programmeId = "other-programme"))).code)
        assertEquals(DvrCodeV1.OK, send("schedule", command).code)
        assertEquals(1, host.started)
    }

    @Test fun `unsupported requests and denied destinations produce safe results`() {
        val recurring = schedule.copy(rule = DvrRuleV1.Series("synthetic-series"))
        assertEquals(DvrCodeV1.UNSUPPORTED, send("recurring", DvrCommandV1.Schedule(recurring)).code)
        assertEquals(DvrCodeV1.UNSUPPORTED, send("stop", DvrCommandV1.Stop("rec-1")).code)
        assertEquals(DvrCodeV1.DENIED, send("denied", DvrCommandV1.Schedule(
            schedule.copy(destinationId = "ungranted-destination"),
        )).code)
        assertEquals(0, host.started)
        assertNull(plugin.handleDvr("{}".encodeToByteArray()))
        plugin.onUnload()
        assertEquals(DvrCodeV1.BUSY, send("unloaded", DvrCommandV1.Schedule(schedule)).code)
    }

    private fun send(id: String, command: DvrCommandV1): DvrReplyV1 {
        val request = DvrRequestV1(id, command)
        val bytes = requireNotNull(DvrProtocolV1.encodeRequest(request))
        return requireNotNull(DvrProtocolV1.decodeReply(request, requireNotNull(plugin.handleDvr(bytes))))
    }

    private class FakeHost : DvrHostServicesV1 {
        var started = 0
        var canceled = 0
        var dropOnce = true
        private val captures = mutableMapOf<String, DvrRecordingV1>()
        private val receipts = mutableMapOf<String, Pair<DvrRequestV1, DvrReplyV1>>()

        override fun exchange(request: ByteArray): ByteArray? {
            val call = DvrProtocolV1.decodeRequest(request, DvrEndpointV1.HOST) ?: return null
            val prior = receipts[call.requestId]
            if (prior != null) {
                val reply = if (prior.first == call) prior.second
                    else DvrReplyV1(call.requestId, DvrCodeV1.REQUEST_ID_REUSED)
                return DvrProtocolV1.encodeReply(call, reply)
            }
            val command = call.command
            val reply = when (command) {
                DvrCommandV1.HostDestinations -> ok(call, DvrPayloadV1.Destinations(listOf(
                    DvrDestinationV1("synthetic-device", DvrDestinationKindV1.DEVICE, "Test storage", true, true),
                )))
                is DvrCommandV1.StartCapture -> {
                    if (command.destinationId != "synthetic-device") {
                        DvrReplyV1(call.requestId, DvrCodeV1.DENIED)
                    } else {
                        val id = "capture-${++started}"
                        val capture = DvrRecordingV1(id, DvrRecordingStateV1.SCHEDULED, 0, command.window)
                        captures[id] = capture
                        ok(call, DvrPayloadV1.Recording(capture))
                    }
                }
                is DvrCommandV1.CaptureStatus -> ok(call, DvrPayloadV1.Recording(captures.getValue(command.captureId)))
                is DvrCommandV1.CancelCapture -> {
                    val existing = captures.getValue(command.captureId)
                    val snapshot = existing.copy(state = DvrRecordingStateV1.CANCELED, revision = existing.revision + 1)
                    captures[command.captureId] = snapshot
                    canceled++
                    ok(call, DvrPayloadV1.Recording(snapshot))
                }
                else -> DvrReplyV1(call.requestId, DvrCodeV1.UNSUPPORTED)
            }
            if (command is DvrCommandV1.StartCapture || command is DvrCommandV1.CancelCapture) {
                receipts[call.requestId] = call to reply
            }
            return DvrProtocolV1.encodeReply(call, reply)
        }

        private fun ok(call: DvrRequestV1, payload: DvrPayloadV1) = DvrReplyV1(call.requestId, DvrCodeV1.OK, payload)
    }
}
