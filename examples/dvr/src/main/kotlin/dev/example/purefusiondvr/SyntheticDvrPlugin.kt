package dev.example.purefusiondvr

import android.content.Context
import dev.eliminater.purefusioniptv.plugin.api.Plugin
import dev.eliminater.purefusioniptv.plugin.api.PluginCapability
import dev.eliminater.purefusioniptv.plugin.api.SandboxCapabilitiesV1
import dev.eliminater.purefusioniptv.plugin.api.SandboxPluginV1
import dev.eliminater.purefusioniptv.plugin.api.dvr.*

/** An in-memory control-flow example. Its tests supply the host and synthetic data. */
class SyntheticDvrPlugin : Plugin, SandboxPluginV1, DvrProvider {
    override val id = "dev.example.purefusiondvr"
    override val name = "Synthetic DVR Example"
    override val description = "Synthetic DVR control flow for local SDK tests"
    override val author = "Example Developer"
    override val version = "1.0.0"
    override val minAppVersion = "1.0.0"
    override val apiLevel = 4
    override val capabilities = listOf(PluginCapability.DVR_PROVIDER)

    private var host: DvrHostServicesV1? = null
    private data class Receipt(val request: DvrRequestV1, var reply: DvrReplyV1? = null)
    private data class Entry(val captureId: String, var snapshot: DvrRecordingV1)

    private val receipts = mutableMapOf<String, Receipt>()
    private val recordings = linkedMapOf<String, Entry>()

    override fun onLoad(context: Context) = Unit
    override fun onSandboxLoad(capabilities: SandboxCapabilitiesV1) = Unit
    override fun onDvrLoad(host: DvrHostServicesV1) { this.host = host }
    override fun onUnload() { host = null }

    override fun handleDvr(request: ByteArray): ByteArray? {
        val call = DvrProtocolV1.decodeRequest(request, DvrEndpointV1.PROVIDER) ?: return null
        val reply = when {
            call.command == DvrCommandV1.Describe -> ok(call, DvrPayloadV1.Provider(describe()))
            host == null -> error(call, DvrCodeV1.BUSY)
            else -> dispatch(call)
        }
        return DvrProtocolV1.encodeReply(call, reply)
    }

    private fun describe() = DvrProviderInfoV1(
        providerId = id,
        displayName = name,
        version = version,
        operations = listOf(
            DvrOperationV1.DESCRIBE, DvrOperationV1.SCHEDULE, DvrOperationV1.RULE_STATUS,
            DvrOperationV1.STATUS, DvrOperationV1.CANCEL, DvrOperationV1.LIST_RECORDINGS,
            DvrOperationV1.LIST_DESTINATIONS,
        ),
        ruleKinds = listOf(DvrRuleKindV1.ONCE),
        destinationKinds = listOf(DvrDestinationKindV1.DEVICE),
        limitations = listOf("Synthetic one-time schedules with a test host and in-memory state."),
    )

    private fun dispatch(call: DvrRequestV1): DvrReplyV1 = when (val command = call.command) {
        is DvrCommandV1.Schedule -> mutate(call) { schedule(call, command.schedule) }
        is DvrCommandV1.Cancel -> mutate(call) { refresh(call, command.recordingId, cancel = true) }
        is DvrCommandV1.Status -> refresh(call, command.recordingId, cancel = false)
        is DvrCommandV1.RuleStatus -> ruleStatus(call, command.ruleId)
        is DvrCommandV1.ListRecordings -> listRecordings(call, command)
        DvrCommandV1.ListDestinations -> exchange(call, DvrCommandV1.HostDestinations)
        else -> error(call, DvrCodeV1.UNSUPPORTED)
    }

    private fun mutate(call: DvrRequestV1, action: () -> DvrReplyV1): DvrReplyV1 {
        val existing = receipts[call.requestId]
        if (existing != null && existing.request != call) return error(call, DvrCodeV1.REQUEST_ID_REUSED)
        if (existing == null && receipts.size >= MAX_RECEIPTS) return error(call, DvrCodeV1.BUSY)
        val receipt = existing ?: Receipt(call).also { receipts[call.requestId] = it }
        receipt.reply?.let { return it }
        return action().also { if (it.code != DvrCodeV1.BUSY) receipt.reply = it }
    }

    private fun schedule(call: DvrRequestV1, request: DvrScheduleV1): DvrReplyV1 {
        if (request.rule != DvrRuleV1.Once) return error(call, DvrCodeV1.UNSUPPORTED)
        val result = exchange(call, DvrCommandV1.StartCapture(
            request.channelId, request.destinationId, request.window, request.programmeId, request.padding,
        ))
        val capture = (result.payload as? DvrPayloadV1.Recording)?.snapshot ?: return result
        val sequence = recordings.size + 1
        val snapshot = capture.copy(recordingId = "recording-$sequence", ruleId = "rule-$sequence")
        recordings[snapshot.recordingId] = Entry(capture.recordingId, snapshot)
        return ok(call, DvrPayloadV1.Rule(snapshot.ruleStatus()))
    }

    private fun refresh(call: DvrRequestV1, recordingId: String, cancel: Boolean): DvrReplyV1 {
        val entry = recordings[recordingId] ?: return error(call, DvrCodeV1.NOT_FOUND)
        val result = exchange(call, if (cancel) DvrCommandV1.CancelCapture(entry.captureId)
            else DvrCommandV1.CaptureStatus(entry.captureId))
        val capture = (result.payload as? DvrPayloadV1.Recording)?.snapshot ?: return result
        val current = entry.snapshot
        val next = capture.copy(recordingId = recordingId, ruleId = current.ruleId)
        if (next.revision < current.revision) return ok(call, DvrPayloadV1.Recording(current))
        if (next.window != current.window || (next.revision - current.revision <= 1 &&
                !DvrProtocolV1.isValidNextSnapshot(current, next))
        ) {
            return error(call, DvrCodeV1.FAILED)
        }
        entry.snapshot = next
        return ok(call, DvrPayloadV1.Recording(next))
    }

    private fun ruleStatus(call: DvrRequestV1, ruleId: String): DvrReplyV1 {
        val entry = recordings.values.firstOrNull { it.snapshot.ruleId == ruleId }
            ?: return error(call, DvrCodeV1.NOT_FOUND)
        val result = refresh(call, entry.snapshot.recordingId, cancel = false)
        val snapshot = (result.payload as? DvrPayloadV1.Recording)?.snapshot ?: return result
        return ok(call, DvrPayloadV1.Rule(snapshot.ruleStatus()))
    }

    private fun listRecordings(call: DvrRequestV1, query: DvrCommandV1.ListRecordings): DvrReplyV1 {
        val matching = recordings.values.map { it.snapshot }.filter {
            (query.state == null || it.state == query.state) && (query.ruleId == null || it.ruleId == query.ruleId)
        }
        val prefix = "page-${query.state?.name ?: "ALL"}-${query.ruleId ?: "all"}-${query.pageSize}-"
        val cursor = query.cursor
        val offset = if (cursor == null) 0 else {
            if (!cursor.startsWith(prefix)) return error(call, DvrCodeV1.INVALID_REQUEST)
            cursor.removePrefix(prefix).toIntOrNull() ?: return error(call, DvrCodeV1.INVALID_REQUEST)
        }
        if (offset !in 0..matching.size) return error(call, DvrCodeV1.INVALID_REQUEST)
        val items = matching.drop(offset).take(query.pageSize)
        val next = (offset + items.size).takeIf { it < matching.size }?.let { "$prefix$it" }
        return ok(call, DvrPayloadV1.Recordings(items, next))
    }

    private fun exchange(call: DvrRequestV1, command: DvrCommandV1): DvrReplyV1 {
        // Each example operation makes at most one host call. It reuses the caller's stable ID
        // in the separate host direction, including after a reply is lost.
        val request = DvrRequestV1(call.requestId, command)
        val bytes = DvrProtocolV1.encodeRequest(request) ?: return error(call, DvrCodeV1.INVALID_REQUEST)
        val response = host?.exchange(bytes) ?: return error(call, DvrCodeV1.BUSY)
        val reply = DvrProtocolV1.decodeReply(request, response) ?: return error(call, DvrCodeV1.BUSY)
        // A host conflict may name a host capture. Keep that ID out of the provider namespace.
        return reply.copy(conflicts = reply.conflicts.map { it.copy(recordingId = null) })
    }

    private fun DvrRecordingV1.ruleStatus(): DvrRuleStatusV1 {
        val state = when (state) {
            DvrRecordingStateV1.CANCELED -> DvrRuleStateV1.CANCELED
            DvrRecordingStateV1.COMPLETED, DvrRecordingStateV1.FAILED -> DvrRuleStateV1.FINISHED
            else -> DvrRuleStateV1.ACTIVE
        }
        return DvrRuleStatusV1(requireNotNull(ruleId), state, window.takeIf { this.state == DvrRecordingStateV1.SCHEDULED })
    }

    private fun ok(call: DvrRequestV1, payload: DvrPayloadV1) = DvrReplyV1(call.requestId, DvrCodeV1.OK, payload)
    private fun error(call: DvrRequestV1, code: DvrCodeV1) = DvrReplyV1(call.requestId, code)

    private companion object { const val MAX_RECEIPTS = 32 }
}
