package com.codro.listenstudy.domain.tts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CloudReliabilityTest {
    @Test fun `valid cached mp3 works offline and keyless`() = runTest {
        val cache = Cache(validMp3())
        val remote = Remote()
        val result = CloudCacheFirstSynthesizer(cache, remote).audio(request(), "")
        assertTrue(result.fromCache)
        assertEquals(0, remote.calls)
    }

    @Test fun `empty and corrupt cache are invalidated then synthesized when key exists`() = runTest {
        for (bad in listOf(byteArrayOf(), byteArrayOf(1, 2, 3))) {
            val cache = Cache(bad)
            val remote = Remote()
            val result = CloudCacheFirstSynthesizer(cache, remote).audio(request(), "key")
            assertFalse(result.fromCache)
            assertEquals(1, cache.invalidations)
            assertEquals(1, remote.calls)
        }
    }

    @Test fun `corrupt cache without key fails with recovery guidance`() = runTest {
        val cache = Cache(byteArrayOf(1, 2, 3))
        val failure = runCatching { CloudCacheFirstSynthesizer(cache, Remote()).audio(request(), "") }.exceptionOrNull()
        assertTrue(failure is CloudTtsFailure.CacheCorrupt)
        assertTrue(failure!!.message!!.contains("API 키"))
    }

    @Test fun `cloud failures distinguish auth quota network and playback recovery`() {
        assertEquals(CloudFailureKind.AUTH, CloudFailureClassifier.http(401))
        assertEquals(CloudFailureKind.AUTH, CloudFailureClassifier.http(403))
        assertEquals(CloudFailureKind.QUOTA, CloudFailureClassifier.http(429))
        assertEquals(CloudFailureKind.NETWORK, CloudFailureClassifier.network().kind)
        assertEquals(CloudFailureKind.PLAYBACK, CloudFailureClassifier.playback().kind)
    }

    @Test fun `prefetch planner takes at most three next unique sentences and changes generation`() {
        val planner = CloudPrefetchPlanner(maxAhead = 3)
        assertEquals(listOf("b", "c", "d"), planner.plan(listOf("a", "b", "b", "c", "d"), 0))
        val first = planner.contextToken("doc1", "voice")
        val same = planner.contextToken("doc1", "voice")
        val changed = planner.contextToken("doc2", "voice")
        assertEquals(first, same)
        assertNotEquals(first, changed)
    }

    private fun request() = CloudSynthesisRequest("문장", "ko-KR-Wavenet-A")
    private fun validMp3() = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0, 1)
    private class Remote : CloudTtsRemote { var calls=0; override suspend fun synthesize(request: CloudSynthesisRequest, apiKey: String): ByteArray { calls++; return byteArrayOf(0xff.toByte(), 0xfb.toByte(), 1, 2) } }
    private class Cache(var bytes: ByteArray?) : CloudAudioCache {
        var invalidations=0
        override suspend fun read(key:String)=bytes
        override suspend fun writeAtomically(key:String,bytes:ByteArray){this.bytes=bytes}
        override suspend fun invalidate(key:String){ invalidations++; bytes=null }
    }
}
