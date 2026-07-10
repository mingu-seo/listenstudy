package com.codro.listenstudy.domain.tts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CloudTtsTest {
    @Test fun `catalog exposes Korean Standard and WaveNet A through D with WaveNet A default`() {
        assertEquals("ko-KR-Wavenet-A", CloudVoiceCatalog.defaultVoice.id)
        assertEquals(
            listOf("ko-KR-Standard-A", "ko-KR-Standard-B", "ko-KR-Standard-C", "ko-KR-Standard-D"),
            CloudVoiceCatalog.forMode(PlaybackMode.GOOGLE_STANDARD).map { it.id },
        )
        assertEquals(
            listOf("ko-KR-Wavenet-A", "ko-KR-Wavenet-B", "ko-KR-Wavenet-C", "ko-KR-Wavenet-D"),
            CloudVoiceCatalog.forMode(PlaybackMode.GOOGLE_WAVENET).map { it.id },
        )
        assertEquals("WaveNet A", CloudVoiceCatalog.defaultVoice.label)
    }

    @Test fun `saved cloud voice is normalized to selected playback mode`() {
        assertEquals(
            "ko-KR-Standard-A",
            CloudVoiceCatalog.resolveForMode(PlaybackMode.GOOGLE_STANDARD, "ko-KR-Wavenet-D").id,
        )
        assertEquals(
            "ko-KR-Wavenet-C",
            CloudVoiceCatalog.resolveForMode(PlaybackMode.GOOGLE_WAVENET, "ko-KR-Wavenet-C").id,
        )
    }

    @Test fun `cloud diagnostics show actual provider voice and cache`() {
        val diagnostics = CloudPlaybackDiagnostics.create(
            mode = PlaybackMode.GOOGLE_WAVENET,
            voice = CloudVoiceCatalog.defaultVoice,
            cacheFileCount = 3,
            cacheBytes = 2048,
        )

        assertEquals("Google Cloud TTS · WaveNet", diagnostics.engine)
        assertEquals("ko-KR-Wavenet-A", diagnostics.voice)
        assertEquals("로컬 캐시 3개 · 2.0 KB", diagnostics.cache)
    }

    @Test fun `cache key is stable and excludes playback speed`() {
        val slow = CloudCacheKey.create(" 같은 문장 ", "ko-KR-Wavenet-A", "MP3", 0.5f)
        val fast = CloudCacheKey.create(" 같은 문장 ", "ko-KR-Wavenet-A", "MP3", 3.0f)
        assertEquals(slow, fast)
        assertEquals(64, slow.length)
        assertNotEquals(slow, CloudCacheKey.create("같은 문장", "ko-KR-Wavenet-B", "MP3", 1f))
        assertNotEquals(slow, CloudCacheKey.create("다른 문장", "ko-KR-Wavenet-A", "MP3", 1f))
    }

    @Test fun `settings validation requires key only when cloud cache misses`() {
        assertNull(CloudSettingsValidator.validate(PlaybackMode.ON_DEVICE, ""))
        assertNull(CloudSettingsValidator.validate(PlaybackMode.GOOGLE_WAVENET, "saved", cacheHit = true))
        assertEquals("Google Cloud API 키를 먼저 저장해 주세요.", CloudSettingsValidator.validate(PlaybackMode.GOOGLE_STANDARD, " ", cacheHit = false))
        assertNull(CloudSettingsValidator.validate(PlaybackMode.GOOGLE_STANDARD, "key", cacheHit = false))
    }

    @Test fun `cache hit never invokes remote and works without api key`() = runTest {
        val cache = FakeCache(byteArrayOf(1, 2, 3))
        val remote = FakeRemote()
        val result = CloudCacheFirstSynthesizer(cache, remote).audio(
            CloudSynthesisRequest("문장", "ko-KR-Wavenet-A", "MP3"), apiKey = "",
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), result.bytes)
        assertTrue(result.fromCache)
        assertEquals(0, remote.calls)
        assertEquals(0, cache.writes)
    }

    @Test fun `cache miss synthesizes exactly once then atomically stores`() = runTest {
        val cache = FakeCache(null)
        val remote = FakeRemote()
        val request = CloudSynthesisRequest("문장", "ko-KR-Standard-A", "MP3")
        val result = CloudCacheFirstSynthesizer(cache, remote).audio(request, "secret")
        assertArrayEquals(byteArrayOf(9, 8), result.bytes)
        assertFalse(result.fromCache)
        assertEquals(1, remote.calls)
        assertEquals(1, cache.writes)
        assertEquals(CloudCacheKey.create(request.text, request.voiceId, request.format), cache.lastKey)
    }

    @Test fun `http error exposes safe Google reason instead of hiding response body`() {
        val body = """{"error":{"code":403,"message":"Requests from this Android client application are blocked."}}"""

        assertEquals(
            "Google Cloud TTS 요청 실패 (HTTP 403): Requests from this Android client application are blocked.",
            CloudTtsHttpError.message(403, body),
        )
    }

    @Test fun `http error without Google message gives restriction guidance`() {
        assertEquals(
            "Google Cloud TTS 요청 실패 (HTTP 400). API 활성화와 키 제한을 확인하세요.",
            CloudTtsHttpError.message(400, "not-json"),
        )
    }

    private class FakeRemote : CloudTtsRemote {
        var calls = 0
        override suspend fun synthesize(request: CloudSynthesisRequest, apiKey: String): ByteArray {
            calls++
            return byteArrayOf(9, 8)
        }
    }

    private class FakeCache(initial: ByteArray?) : CloudAudioCache {
        private var value = initial
        var writes = 0
        var lastKey: String? = null
        override suspend fun read(key: String): ByteArray? = value
        override suspend fun writeAtomically(key: String, bytes: ByteArray) {
            writes++
            lastKey = key
            value = bytes
        }
    }
}
