package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLifecyclePolicyTest {
    @Test
    fun `playing and preview are foreground and sticky`() {
        val playing = PlaybackServiceLifecyclePolicy.decide(PlaybackStatus.Playing, previewActive = false)
        val preview = PlaybackServiceLifecyclePolicy.decide(PlaybackStatus.Paused, previewActive = true)

        assertTrue(playing.foreground)
        assertTrue(playing.sticky)
        assertTrue(preview.foreground)
        assertTrue(preview.sticky)
    }

    @Test
    fun `paused idle and completed are background and non sticky`() {
        listOf(PlaybackStatus.Paused, PlaybackStatus.Idle, PlaybackStatus.Completed).forEach { status ->
            val decision = PlaybackServiceLifecyclePolicy.decide(status, previewActive = false)
            assertFalse(decision.foreground)
            assertFalse(decision.sticky)
        }
    }

    @Test
    fun `sticky restart restored paused is non sticky until play`() {
        val restored = PlaybackServiceLifecyclePolicy.decide(PlaybackStatus.Paused, previewActive = false)
        val replayed = PlaybackServiceLifecyclePolicy.decide(PlaybackStatus.Playing, previewActive = false)

        assertFalse(restored.sticky)
        assertTrue(replayed.sticky)
    }
}

class PlaybackRestoreGateTest {
    @Test
    fun `restore may apply if content did not change`() {
        val gate = PlaybackRestoreGate()
        val token = gate.beginRestore()

        assertTrue(gate.canApply(token))
    }

    @Test
    fun `user replacement arriving during restore invalidates result`() {
        val gate = PlaybackRestoreGate()
        val token = gate.beginRestore()
        gate.contentChanged()

        assertFalse(gate.canApply(token))
    }
}

class PlaybackErrorCoordinatorTest {
    @Test
    fun `local error invokes playback session error callback`() {
        var errors = 0
        val coordinator = PlaybackErrorCoordinator { errors++ }

        coordinator.onError("TTS 재생 오류")

        assertTrue(errors == 1)
    }
}
