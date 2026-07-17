package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.tts.CloudErrorCategory
import com.codro.listenstudy.domain.tts.CloudRecoveryAction
import com.codro.listenstudy.domain.tts.CloudTtsErrorPolicy
import com.codro.listenstudy.domain.tts.PlaybackMode
import org.junit.Assert.*
import org.junit.Test

/**
 * Decides when a cloud error panel appears and disappears. Pure so the rule can be checked without
 * a service, a Looper or a real Google failure.
 */
class CloudErrorUiPolicyTest {

    @Test fun `a new attempt at producing audio clears the previous error`() {
        // Anything that re-speaks, repositions, or reconfigures the cloud path is a fresh attempt:
        // leaving the old panel up would blame the new attempt for the old failure.
        val clearing = listOf(
            ServiceCommand.Play,
            ServiceCommand.Previous,
            ServiceCommand.Next,
            ServiceCommand.Jump(3),
            ServiceCommand.RetryCloudSentence,
            ServiceCommand.UseOnDeviceVoiceForCurrentSentence,
            ServiceCommand.DismissCloudError,
            ServiceCommand.PreviewCloudVoice,
            ServiceCommand.SelectPlaybackMode(PlaybackMode.GOOGLE_WAVENET),
            ServiceCommand.SelectCloudVoice("ko-KR-Wavenet-B"),
            ServiceCommand.SaveApiKey("key", 1),
            ServiceCommand.DeleteApiKey,
            ServiceCommand.ClearCache,
            ServiceCommand.ReplaceDocument(null, "제목", listOf("문장")),
        )

        for (command in clearing) {
            assertTrue("$command", CloudErrorUiPolicy.clearsCloudError(command))
        }
    }

    @Test fun `commands that do not retry audio leave the error visible`() {
        // Pause must NOT clear it: the failure already paused playback, and clearing here would erase
        // the panel the moment it appears. Speed/library/local-voice changes do not retry the cloud.
        val preserving = listOf(
            ServiceCommand.Pause,
            ServiceCommand.SetSpeed(1.5f),
            ServiceCommand.OpenLibrary,
            ServiceCommand.ShowStatus("상태"),
            ServiceCommand.SelectOnDeviceVoice("voice"),
            ServiceCommand.SelectEngine("pkg"),
        )

        for (command in preserving) {
            assertFalse("$command", CloudErrorUiPolicy.clearsCloudError(command))
        }
    }

    @Test fun `switching to the phone voice targets on-device mode`() {
        assertEquals(PlaybackMode.ON_DEVICE, CloudErrorUiPolicy.ON_DEVICE_FALLBACK)
    }

    @Test fun `a cloud error is never shown while the phone voice is already in use`() {
        // After falling back, a late error from the abandoned cloud request must not resurface.
        assertFalse(CloudErrorUiPolicy.shouldShow(PlaybackMode.ON_DEVICE))
        assertTrue(CloudErrorUiPolicy.shouldShow(PlaybackMode.GOOGLE_WAVENET))
        assertTrue(CloudErrorUiPolicy.shouldShow(PlaybackMode.GOOGLE_STANDARD))
    }

    @Test fun `ui state carries no cloud error by default`() {
        assertNull(PlaybackServiceUiState().cloudError)
    }

    @Test fun `ui state carries the classified report for the ui to render`() {
        val report = CloudTtsErrorPolicy.report(CloudErrorCategory.Network)
        val state = PlaybackServiceUiState(cloudError = report)

        assertEquals(CloudErrorCategory.Network, state.cloudError?.category)
        assertEquals(
            listOf(CloudRecoveryAction.Retry, CloudRecoveryAction.UseOnDeviceVoice),
            state.cloudError?.actions,
        )
    }
}
