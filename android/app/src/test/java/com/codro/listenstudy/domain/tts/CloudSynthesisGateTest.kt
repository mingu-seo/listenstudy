package com.codro.listenstudy.domain.tts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * A queued synthesis job must decide whether it is still wanted BEFORE it touches the network.
 *
 * The engine hands jobs to a single-threaded executor, so a job can sit in the queue while the user
 * taps retry, skips a sentence, or switches to the phone voice. Checking the generation only after
 * synthesis returns is too late: the request has already been sent, and already been billed.
 */
class CloudSynthesisGateTest {

    @Test fun `a superseded job never reaches the network`() = runTest {
        val remote = CountingRemote()
        val guard = PlaybackGenerationGuard()
        val gate = CloudSynthesisGate(guard, remote::audio)
        val token = guard.current()

        // Whatever supersedes the job — retry, next sentence, stop, phone-voice switch — lands here
        // as an invalidation before the queued job gets its turn on the executor.
        guard.invalidateAndGet()

        assertNull(gate.audioIfCurrent(token, request(), "key"))
        assertEquals(0, remote.calls)
    }

    @Test fun `a current job synthesizes exactly once`() = runTest {
        val remote = CountingRemote()
        val guard = PlaybackGenerationGuard()
        val gate = CloudSynthesisGate(guard, remote::audio)

        assertNotNull(gate.audioIfCurrent(guard.current(), request(), "key"))
        assertEquals(1, remote.calls)
    }

    @Test fun `a job superseded while in flight is discarded rather than played`() = runTest {
        // The request cannot be un-sent, but its result must not reach the player: the sentence it
        // belongs to is no longer the one on screen.
        val guard = PlaybackGenerationGuard()
        val remote = CountingRemote(onCall = { guard.invalidateAndGet() })
        val gate = CloudSynthesisGate(guard, remote::audio)

        assertNull(gate.audioIfCurrent(guard.current(), request(), "key"))
        assertEquals(1, remote.calls)
    }

    @Test fun `double tapping retry on the same sentence produces one request not two`() = runTest {
        // Both jobs queue on the same executor. The first is superseded the moment the second speak
        // invalidates, so only the newest one may pay for a request.
        val remote = CountingRemote()
        val guard = PlaybackGenerationGuard()
        val gate = CloudSynthesisGate(guard, remote::audio)

        val firstTap = guard.current()
        val secondTap = guard.invalidateAndGet()

        assertNull(gate.audioIfCurrent(firstTap, request(), "key"))
        assertNotNull(gate.audioIfCurrent(secondTap, request(), "key"))
        assertEquals(1, remote.calls)
    }

    @Test fun `stale prefetch work is dropped before it can spend money`() = runTest {
        // Prefetch is speculative; once its context is gone the queued items are pure waste.
        val remote = CountingRemote()
        val guard = PlaybackGenerationGuard()
        val gate = CloudSynthesisGate(guard, remote::audio)
        val token = guard.current()
        guard.invalidateAndGet()

        for (text in listOf("다음 문장", "그 다음 문장")) {
            assertNull(gate.audioIfCurrent(token, CloudSynthesisRequest(text, VOICE), "key"))
        }
        assertEquals(0, remote.calls)
    }

    @Test fun `a failure from a current job still propagates to be classified`() = runTest {
        // The gate must not swallow failures: a real error on the current sentence has to surface.
        val guard = PlaybackGenerationGuard()
        val gate = CloudSynthesisGate(guard) { _, _ -> throw CloudTtsFailure.Http(503, null) }

        val failure = runCatching { gate.audioIfCurrent(guard.current(), request(), "key") }.exceptionOrNull()

        assertTrue(failure is CloudTtsFailure.Http)
    }

    private fun request() = CloudSynthesisRequest("문장", VOICE)

    private class CountingRemote(private val onCall: () -> Unit = {}) {
        var calls = 0
        @Suppress("UNUSED_PARAMETER")
        suspend fun audio(request: CloudSynthesisRequest, apiKey: String): CachedAudio {
            calls++
            onCall()
            return CachedAudio(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 1, 2), "key-$calls", false)
        }
    }

    private companion object { const val VOICE = "ko-KR-Wavenet-A" }
}
