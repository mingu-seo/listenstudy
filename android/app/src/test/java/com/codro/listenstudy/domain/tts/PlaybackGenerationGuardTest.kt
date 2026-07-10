package com.codro.listenstudy.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGenerationGuardTest {
    @Test
    fun `invalidated token cannot run a delayed callback`() {
        val guard = PlaybackGenerationGuard()
        val oldToken = guard.invalidateAndGet()
        var calls = 0

        guard.invalidateAndGet()
        val ran = guard.runIfCurrent(oldToken) { calls += 1 }

        assertFalse(ran)
        assertEquals(0, calls)
    }

    @Test
    fun `current token can run callback exactly once per invocation`() {
        val guard = PlaybackGenerationGuard()
        val token = guard.invalidateAndGet()
        var calls = 0

        val ran = guard.runIfCurrent(token) { calls += 1 }

        assertTrue(ran)
        assertEquals(1, calls)
    }
}
