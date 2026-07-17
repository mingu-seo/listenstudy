package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun `natural completion stops the service instead of restarting it in background`() {
        // Regression: after the final sentence, Completed must NOT re-start the service in the
        // background (Android 15 background-start restriction -> abnormal termination). It must
        // release foreground and select the safe stop path.
        val action = PlaybackServiceLifecyclePolicy.action(PlaybackStatus.Completed, previewActive = false)

        assertEquals(ServiceLifecycleAction.StopService, action)
        assertNotEquals(ServiceLifecycleAction.ReleaseForegroundAndSync, action)
    }

    @Test
    fun `playing keeps the service in the foreground`() {
        assertEquals(
            ServiceLifecycleAction.StartForeground,
            PlaybackServiceLifecyclePolicy.action(PlaybackStatus.Playing, previewActive = false),
        )
    }

    @Test
    fun `manual pause releases foreground but keeps the service alive`() {
        // Pause / non-final auto-advance must not regress: the service stays alive so playback can resume.
        assertEquals(
            ServiceLifecycleAction.ReleaseForegroundAndSync,
            PlaybackServiceLifecyclePolicy.action(PlaybackStatus.Paused, previewActive = false),
        )
    }

    @Test
    fun `preview during completion keeps the service foreground rather than stopping it`() {
        assertEquals(
            ServiceLifecycleAction.StartForeground,
            PlaybackServiceLifecyclePolicy.action(PlaybackStatus.Completed, previewActive = true),
        )
    }

    @Test
    fun `paused then completed still selects terminal stop despite equal decision`() {
        // GAP 2 regression: Paused, Idle and Completed all decide to ServiceLifecycleDecision(false,false),
        // so a plain equality guard swallows Paused -> Completed and the terminal stop never runs.
        val gate = ServiceLifecycleGate()

        assertEquals(ServiceLifecycleAction.StartForeground, gate.next(PlaybackStatus.Playing, previewActive = false))
        assertEquals(ServiceLifecycleAction.ReleaseForegroundAndSync, gate.next(PlaybackStatus.Paused, previewActive = false))
        assertEquals(ServiceLifecycleAction.StopService, gate.next(PlaybackStatus.Completed, previewActive = false))
    }

    @Test
    fun `repeated terminal completion callbacks are idempotent`() {
        val gate = ServiceLifecycleGate()
        gate.next(PlaybackStatus.Playing, previewActive = false)

        assertEquals(ServiceLifecycleAction.StopService, gate.next(PlaybackStatus.Completed, previewActive = false))
        assertNull(gate.next(PlaybackStatus.Completed, previewActive = false))
        assertNull(gate.next(PlaybackStatus.Idle, previewActive = false))
    }

    @Test
    fun `duplicate non terminal transitions are deduplicated`() {
        val gate = ServiceLifecycleGate()

        assertEquals(ServiceLifecycleAction.StartForeground, gate.next(PlaybackStatus.Playing, previewActive = false))
        assertNull(gate.next(PlaybackStatus.Playing, previewActive = false))
        assertEquals(ServiceLifecycleAction.ReleaseForegroundAndSync, gate.next(PlaybackStatus.Paused, previewActive = false))
        assertNull(gate.next(PlaybackStatus.Idle, previewActive = false))
    }

    @Test
    fun `initial idle does not trigger a needless self start or sync`() {
        // MINOR: the service starts not-foreground, so the first Idle emission must be a no-op rather
        // than a redundant stopForeground + startService(SYNC).
        val gate = ServiceLifecycleGate()

        assertNull(gate.next(PlaybackStatus.Idle, previewActive = false))
    }

    @Test
    fun `reset re-enables the gate after a terminal stop so newer work is usable`() {
        // IMPORTANT 2: after a terminal stop the gate is inert; newer work must be able to re-enable it.
        val gate = ServiceLifecycleGate()
        gate.next(PlaybackStatus.Playing, previewActive = false)
        assertEquals(ServiceLifecycleAction.StopService, gate.next(PlaybackStatus.Completed, previewActive = false))
        assertNull(gate.next(PlaybackStatus.Playing, previewActive = false))

        gate.reset()

        assertEquals(ServiceLifecycleAction.StartForeground, gate.next(PlaybackStatus.Playing, previewActive = false))
    }

    @Test
    fun `terminal stop removes the notification while non-terminal release only detaches`() {
        // IMPORTANT 3: completion must REMOVE the foreground notification; pause/background keeps DETACH.
        assertEquals(
            ForegroundDetachMode.Remove,
            PlaybackServiceLifecyclePolicy.foregroundRelease(ServiceLifecycleAction.StopService),
        )
        assertEquals(
            ForegroundDetachMode.Detach,
            PlaybackServiceLifecyclePolicy.foregroundRelease(ServiceLifecycleAction.ReleaseForegroundAndSync),
        )
        assertNull(PlaybackServiceLifecyclePolicy.foregroundRelease(ServiceLifecycleAction.StartForeground))
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
