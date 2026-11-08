package dev.eliminater.purefusioniptv.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxSubtitleProtocolV1Test {
    @Test fun searchRequiresOnlyBoundedMediaMetadata() {
        val request = SandboxSubtitleRequestV1(op = "search", media = SandboxSubtitleMediaV1("movie:1", "Movie", "MOVIE"), language = "en")
        assertTrue(SandboxSubtitleProtocolV1.isValidRequest(request))
        assertFalse(SandboxSubtitleProtocolV1.isValidRequest(request.copy(media = request.media!!.copy(title = "x".repeat(513)))))
        assertFalse(SandboxSubtitleProtocolV1.isValidRequest(request.copy(op = "authenticate")))
        assertFalse(SandboxSubtitleProtocolV1.isValidRequest(request.copy(media = null)))
    }

    @Test fun downloadUsesOpaqueBoundedResultIdentity() {
        assertTrue(SandboxSubtitleProtocolV1.isValidRequest(SandboxSubtitleRequestV1(op = "download", resultId = "123")))
        assertFalse(SandboxSubtitleProtocolV1.isValidRequest(SandboxSubtitleRequestV1(op = "download", resultId = "")))
        assertEquals(512 * 1024, SandboxSubtitleProtocolV1.MAX_SUBTITLE_BYTES)
        assertEquals(30_000L, SandboxSubtitleProtocolV1.CALL_TIMEOUT_MS)
    }

    @Test fun onlySupportedMediaTypesAndPositiveEpisodeNumbersCross() {
        val media = SandboxSubtitleMediaV1("episode:1", "Show", "EPISODE", season = 1, episode = 2)
        assertTrue(SandboxSubtitleProtocolV1.isValidRequest(SandboxSubtitleRequestV1(op = "search", media = media)))
        assertFalse(SandboxSubtitleProtocolV1.isValidRequest(SandboxSubtitleRequestV1(op = "search", media = media.copy(contentType = "LIVE"))))
        assertFalse(SandboxSubtitleProtocolV1.isValidRequest(SandboxSubtitleRequestV1(op = "search", media = media.copy(episode = -1))))
    }
}
