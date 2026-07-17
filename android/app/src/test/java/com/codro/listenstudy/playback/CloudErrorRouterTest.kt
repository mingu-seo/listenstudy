package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.tts.CloudErrorCategory
import com.codro.listenstudy.domain.tts.CloudTtsErrorPolicy
import com.codro.listenstudy.domain.tts.PlaybackMode
import com.codro.listenstudy.ui.PlayerUiFormatter
import org.junit.Assert.*
import org.junit.Test

/**
 * A cloud failure means different things depending on what was being spoken.
 *
 * A settings voice preview and the document's current sentence both go through the same engine, so
 * without the utterance id a failed preview raises a recovery panel on the reader whose 다시 시도 /
 * 휴대폰 음성 buttons act on a sentence the user was not even listening to.
 */
class CloudErrorRouterTest {

    @Test fun `a failed voice preview never becomes a current-sentence error`() {
        for (mode in PlaybackMode.entries) {
            assertEquals(
                "$mode",
                CloudErrorTarget.PreviewFeedback,
                CloudErrorRouter.route(CloudErrorRouter.CLOUD_PREVIEW_UTTERANCE_ID, mode),
            )
        }
    }

    @Test fun `a failed document sentence raises the recovery panel`() {
        assertEquals(
            CloudErrorTarget.CurrentSentence,
            CloudErrorRouter.route("listenstudy_sentence_0_1_4", PlaybackMode.GOOGLE_WAVENET),
        )
    }

    @Test fun `a late document failure is dropped once the phone voice is in use`() {
        assertEquals(
            CloudErrorTarget.Ignore,
            CloudErrorRouter.route("listenstudy_sentence_0_1_4", PlaybackMode.ON_DEVICE),
        )
    }

    @Test fun `preview feedback is still delivered when the saved mode is the phone voice`() {
        // The BYOK wizard lets the user preview a cloud voice before switching modes, so a preview
        // failure there must be reported even though playbackMode is still ON_DEVICE.
        assertEquals(
            CloudErrorTarget.PreviewFeedback,
            CloudErrorRouter.route(CloudErrorRouter.CLOUD_PREVIEW_UTTERANCE_ID, PlaybackMode.ON_DEVICE),
        )
    }

    @Test fun `preview failure reads as a preview failure and not as a playback failure`() {
        val message = CloudErrorRouter.previewFailureMessage(
            CloudTtsErrorPolicy.report(CloudErrorCategory.AuthKeyInvalid),
        )

        assertTrue(message, message.startsWith("미리듣기 실패"))
        assertTrue(message, message.contains("API 키"))
        assertTrue(message.length <= 60)
    }

    @Test fun `the settings screen flags a preview failure as an error`() {
        // The settings preview feedback keys off the status text. The classified titles contain
        // neither 실패 nor 오류, so a preview failure would otherwise be styled as normal progress.
        for (category in CloudErrorCategory.entries) {
            val message = CloudErrorRouter.previewFailureMessage(CloudTtsErrorPolicy.report(category))
            val feedback = PlayerUiFormatter.cloudPreviewFeedback(message)

            assertTrue("$category", feedback.isError)
            assertFalse("$category", feedback.inProgress)
        }
    }

    @Test fun `preview failure text leaks no http status body or key`() {
        for (category in CloudErrorCategory.entries) {
            val message = CloudErrorRouter.previewFailureMessage(CloudTtsErrorPolicy.report(category))

            assertFalse(message, message.contains("HTTP"))
            assertFalse(message, message.contains("AIza"))
            assertFalse(message, message.contains("Exception"))
            assertFalse(message, message.contains("googleapis"))
        }
    }
}
