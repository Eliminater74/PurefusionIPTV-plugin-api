package dev.example.purefusionstarter

import android.content.Context
import dev.eliminater.purefusioniptv.plugin.api.*
import org.json.JSONArray
import org.json.JSONObject

/** No host Context, Views, direct sockets, files, background jobs or provider claims. */
class StarterPlugin : Plugin, SandboxPluginV1, SandboxSurfacePluginV1 {
    override val id = BuildConfig.pluginId
    override val name = BuildConfig.displayName
    override val author = BuildConfig.publisher
    override val description = BuildConfig.description
    override val version = BuildConfig.version
    override val minAppVersion = BuildConfig.minHostVersion
    override val apiLevel = BuildConfig.pluginApiLevel
    override val capabilities = emptyList<PluginCapability>()
    private var broker: SandboxCapabilitiesV1? = null
    private var count = 0
    private var greeting = "Welcome to your plugin"
    private var step = 1
    private var status = "Use the D-pad to select an action. Back returns to the host."
    private var failed = false
    private var loaded = false
    private var nextStorage = 0L
    private var http = 0
    private var polls = 0
    private var bytes = 0

    // The isolated lane calls onSandboxLoad, never this legacy method.
    override fun onLoad(context: Context) = Unit
    @Synchronized override fun onSandboxLoad(capabilities: SandboxCapabilitiesV1) {
        onUnload()
        broker = capabilities
        count = 0; step = 1; greeting = "Welcome to your plugin"; loaded = false; failed = false
        nextStorage = 0L
    }
    @Synchronized override fun onUnload() {
        if (http > 0) broker?.cancelHttp(http)
        http = 0; broker = null; loaded = false
    }
    private fun storageTurn() {
        val remaining = nextStorage - System.nanoTime()
        if (remaining > 0) Thread.sleep((remaining + 999_999) / 1_000_000)
        nextStorage = System.nanoTime() + 80_000_000 // broker requires >=75 ms between operations
    }
    private fun restore(b: SandboxCapabilitiesV1) {
        storageTurn()
        val raw = b.readStorage("preferences_v1") ?: return // absent, denied and busy share null
        try {
            val state = JSONObject(String(raw, Charsets.UTF_8))
            val n = state.getInt("count"); val s = state.getInt("step"); val g = state.getString("greeting")
            require(state.getInt("schemaVersion") == 1 && n in 0..1_000_000 && s in 1..10 && validText(g))
            count = n; step = s; greeting = g
        } catch (_: Exception) { failed = true; status = "Stored preferences are invalid; defaults are shown. Save to replace them." }
    }
    private fun validText(value: String) = value.length in 1..80 && value == value.trim() &&
        value.none { it == '<' || it == '>' || it.code < 32 || it.code in 0x7f..0x9f || it.code in 0x202a..0x202e || it.code in 0x2066..0x2069 }
    private fun save(b: SandboxCapabilitiesV1, newCount: Int, newGreeting: String = greeting, newStep: Int = step) {
        val record = JSONObject().put("schemaVersion", 1).put("count", newCount).put("greeting", newGreeting).put("step", newStep)
        storageTurn()
        if (b.writeStorage("preferences_v1", record.toString().toByteArray(Charsets.UTF_8))) {
            count = newCount; greeting = newGreeting; step = newStep; failed = false; status = "Saved in private plugin storage."
        } else { failed = true; status = "Change not saved. Storage may be denied, busy or unavailable. Check Plugin permissions and retry." }
    }
    @Synchronized override fun handleSurface(request: ByteArray): ByteArray? {
        val b = broker ?: return null
        if (request.size > SandboxSurfaceProtocolV1.MAX_REQUEST_BYTES) return null
        val r = try { JSONObject(String(request, Charsets.UTF_8)) } catch (_: Exception) { return null }
        if (r.optString("schema") != SandboxSurfaceProtocolV1.SURFACE_SCHEMA) return null
        try {
            if (!loaded) { restore(b); loaded = true }
            if (r.optString("type") == "back") {
                cancel(b)
                return JSONObject().put("schema", "pf.surface.v1").put("partial", true).put("handled", false).toString().toByteArray()
            }
            var dialog: JSONObject? = null
            when (r.optString("type")) {
                "activate" -> when (r.optString("card")) {
                    "increment" -> save(b, (count + step).coerceAtMost(1_000_000))
                    "preferences" -> dialog = preferences()
                    "network" -> {
                        cancel(b)
                        http = b.startHttp("GET", "/", null)
                        polls = 0; bytes = 0
                        failed = http <= 0
                        status = if (failed) "Network denied or busy. Optional NETWORK_EXTERNAL and TV consent are required." else "Loading through the approved network broker..."
                        if (http <= 0) http = 0
                    }
                    "cancel" -> { cancel(b); status = "Request cancelled."; failed = false }
                }
                "dialog" -> if (r.optString("dialog") == "preferences" && r.optString("result") == "submit") {
                    val values = r.optJSONObject("values") ?: JSONObject()
                    val g = values.optString("greeting"); val s = values.optString("step").toIntOrNull()
                    if (!validText(g) || s == null || s !in 1..10) {
                        failed = true; status = "Enter plain greeting text (1–80 characters) and an integer step from 1 to 10."
                        dialog = preferences()
                    } else save(b, count, g, s)
                }
                "continue", "refresh" -> if (http > 0) poll(b)
            }
            return document(dialog).toString().toByteArray(Charsets.UTF_8)
        } catch (e: InterruptedException) {
            cancel(b); Thread.currentThread().interrupt(); return null
        } catch (_: Exception) {
            cancel(b); failed = true; status = "The operation failed. Reopen this screen to retry."
            return document(null).toString().toByteArray(Charsets.UTF_8)
        }
    }
    private fun cancel(b: SandboxCapabilitiesV1) { if (http > 0) b.cancelHttp(http); http = 0 }
    private fun poll(b: SandboxCapabilitiesV1) {
        if (++polls > 50) { cancel(b); failed = true; status = "Network example timed out."; return }
        val result = b.pollHttp(http)
        if (result.status == SandboxHttpResultV1.PENDING) { Thread.sleep(100); return }
        bytes += result.body?.size ?: 0
        if (result.status != SandboxHttpResultV1.OK || bytes > 256 * 1024) {
            cancel(b); failed = true; status = "Network unavailable, denied or response too large."
        } else if (result.complete) {
            status = "HTTP ${result.httpStatus}: $bytes bytes received. Response content is not displayed."
            cancel(b); failed = false
        }
    }
    private fun preferences() = JSONObject().put("id", "preferences").put("kind", "form").put("title", "Example preferences")
        .put("fields", JSONArray().put(JSONObject().put("id", "greeting").put("hint", "Display greeting").put("value", greeting).put("max", 80))
            .put(JSONObject().put("id", "step").put("hint", "Counter step (1–10)").put("value", step.toString()).put("max", 2)))
        .put("submit", "Save").put("cancel", "Cancel")
    private fun document(dialog: JSONObject?): JSONObject {
        val cards = JSONObject(); val rows = JSONArray()
        for ((id, title) in listOf("increment" to "Add $step", "preferences" to "Edit preferences", "network" to "Try optional HTTP", "cancel" to "Cancel HTTP")) {
            cards.put(id, JSONObject().put("title", title).put("subtitle", if (id == "increment") greeting else "Host-rendered action")
                .put("icon", "plugin").put("activate", "plugin").put("actions", JSONArray()))
            rows.put(JSONObject().put("card", id))
        }
        return JSONObject().put("schema", "pf.surface.v1").put("title", name).put("subtitle", "$greeting · Counter: $count")
            .put("state", "v1").put("sections", JSONArray().put(JSONObject().put("id", "example").put("title", "Example actions").put("count", 4)))
            .put("rows", JSONObject().put("example", rows)).put("cards", cards).put("emptyText", "No actions are available.")
            .put("loadingText", "Waiting for brokered HTTP...").put("loading", http > 0).put("continue", http > 0)
            .put("focus", "increment").put("status", status).put("error", failed).put("dialog", dialog ?: JSONObject.NULL)
    }
}
