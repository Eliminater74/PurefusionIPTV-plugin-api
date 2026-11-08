package dev.example.purefusionstarter

import dev.eliminater.purefusioniptv.plugin.api.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.ServiceLoader

class StarterPluginTest {
    private fun call(p: StarterPlugin, type: String, extra: String = ""): JSONObject? =
        p.handleSurface("""{"schema":"pf.surface.v1","type":"$type"$extra}""".toByteArray())?.let { JSONObject(String(it)) }

    @Test fun discoveryAndLifecycleUseIsolatedEntrypoint() {
        val p = ServiceLoader.load(Plugin::class.java, javaClass.classLoader).single() as StarterPlugin
        assertEquals(BuildConfig.pluginId, p.id)
        assertEquals(3, p.apiLevel)
        assertTrue(p.capabilities.isEmpty())
        assertNull(call(p, "open"))
        p.onSandboxLoad(MemoryBroker())
        assertEquals(BuildConfig.displayName, call(p, "open")!!.getString("title"))
        p.onUnload()
        assertNull(call(p, "open"))
    }
    @Test fun actionPersistsAcrossWorkerRestart() {
        val b = MemoryBroker(); val p = StarterPlugin(); p.onSandboxLoad(b)
        call(p, "open"); val doc = call(p, "activate", ",\"card\":\"increment\"")!!
        assertTrue(doc.getString("subtitle").contains("1"))
        p.onUnload(); val next = StarterPlugin(); next.onSandboxLoad(b)
        assertTrue(call(next, "open")!!.getString("subtitle").contains("1"))
    }
    @Test fun deniedStorageDoesNotClaimSavedState() {
        val p = StarterPlugin(); p.onSandboxLoad(MemoryBroker(denied = true))
        call(p, "open")
        val d = call(p, "activate", ",\"card\":\"increment\"")!!
        assertTrue(d.getBoolean("error")); assertTrue(d.getString("status").contains("not saved"))
        assertTrue(d.getString("subtitle").contains("0"))
    }
    @Test fun corruptedStorageRecoversWithoutExecutingUi() {
        val b = MemoryBroker(); b.values["preferences_v1"] = "broken".toByteArray()
        val p = StarterPlugin(); p.onSandboxLoad(b)
        val d = call(p, "open")!!
        assertEquals("plugin", d.getJSONObject("cards").getJSONObject("increment").getString("activate"))
        assertEquals(0, d.getJSONObject("cards").getJSONObject("increment").getJSONArray("actions").length())
        assertTrue(d.getBoolean("error"))
    }
    @Test fun preferencesValidateAndSurviveRestart() {
        val b = MemoryBroker(); val p = StarterPlugin(); p.onSandboxLoad(b); call(p, "open")
        val invalid = call(p, "dialog", ",\"dialog\":\"preferences\",\"result\":\"submit\",\"values\":{\"greeting\":\"\",\"step\":\"0\"}")!!
        assertTrue(invalid.getBoolean("error"))
        val saved = call(p, "dialog", ",\"dialog\":\"preferences\",\"result\":\"submit\",\"values\":{\"greeting\":\"Hello TV\",\"step\":\"3\"}")!!
        assertFalse(saved.getBoolean("error"))
        p.onUnload(); val next = StarterPlugin(); next.onSandboxLoad(b); call(next, "open")
        assertTrue(call(next, "activate", ",\"card\":\"increment\"")!!.getString("subtitle").contains("3"))
    }
    @Test fun backAndMalformedRequestsAreSafe() {
        val p = StarterPlugin(); p.onSandboxLoad(MemoryBroker())
        assertFalse(call(p, "back")!!.getBoolean("handled"))
        assertNull(p.handleSurface("not json".toByteArray()))
        assertNull(p.handleSurface("{}".toByteArray()))
        assertNull(p.handleSurface(ByteArray(65537)))
    }
    @Test fun httpDenialAndCancellationAreBounded() {
        val b = MemoryBroker(); val p = StarterPlugin(); p.onSandboxLoad(b); call(p, "open")
        assertTrue(call(p, "activate", ",\"card\":\"network\"")!!.getBoolean("error"))
        b.httpId = 7
        assertTrue(call(p, "activate", ",\"card\":\"network\"")!!.getBoolean("loading"))
        call(p, "activate", ",\"card\":\"cancel\"")
        assertEquals(7, b.cancelled)
        call(p, "activate", ",\"card\":\"network\""); p.onUnload(); assertEquals(7, b.cancelled)
    }
    @Test fun httpCompletionReturnsToHostWithoutBlocking() {
        val b = MemoryBroker(); b.httpId = 4
        val p = StarterPlugin(); p.onSandboxLoad(b); call(p, "open")
        call(p, "activate", ",\"card\":\"network\"")
        b.reply = SandboxHttpResultV1(SandboxHttpResultV1.OK, 200, "hello".toByteArray(), true)
        val d = call(p, "continue")!!
        assertFalse(d.getBoolean("loading")); assertTrue(d.getString("status").contains("200"))
    }
}

private class MemoryBroker(val denied: Boolean = false) : SandboxCapabilitiesV1 {
    val values = mutableMapOf<String, ByteArray>(); var httpId = -1; var cancelled = 0
    var reply = SandboxHttpResultV1(SandboxHttpResultV1.PENDING)
    override fun readStorage(key: String) = if (denied) null else values[key]
    override fun writeStorage(key: String, value: ByteArray): Boolean { if (denied) return false; values[key] = value.copyOf(); return true }
    override fun deleteStorage(key: String) = !denied && values.remove(key) != null
    override fun startHttp(method: String, pathAndQuery: String, jsonBody: ByteArray?) = httpId
    override fun pollHttp(requestId: Int) = reply
    override fun cancelHttp(requestId: Int): Boolean { cancelled = requestId; return true }
}
