package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.tts.CloudErrorReport
import com.codro.listenstudy.domain.tts.PlaybackMode

/** Where a cloud failure belongs, based on what was being spoken when it happened. */
enum class CloudErrorTarget {
    /** A settings voice preview failed: report it in settings, next to the preview button. */
    PreviewFeedback,

    /** The document's current sentence failed: raise the recovery panel on the reader. */
    CurrentSentence,

    /** Nothing to say — a late failure from an already-abandoned cloud request. */
    Ignore,
}

/**
 * Routes a cloud failure to the surface it actually describes.
 *
 * A settings voice preview and the document's current sentence share one engine, so without the
 * utterance id a failed preview raises a recovery panel on the reader whose 다시 시도 / 휴대폰 음성
 * buttons act on a sentence the user was never listening to.
 */
object CloudErrorRouter {
    /** Utterance id the service speaks cloud previews under. */
    const val CLOUD_PREVIEW_UTTERANCE_ID = "listenstudy_cloud_preview"

    fun route(utteranceId: String, mode: PlaybackMode): CloudErrorTarget = when {
        // Checked before the mode: the BYOK wizard previews a cloud voice while the saved mode is
        // still ON_DEVICE, and that preview's failure is exactly what the user is waiting to hear.
        utteranceId == CLOUD_PREVIEW_UTTERANCE_ID -> CloudErrorTarget.PreviewFeedback
        !CloudErrorUiPolicy.shouldShow(mode) -> CloudErrorTarget.Ignore
        else -> CloudErrorTarget.CurrentSentence
    }

    /**
     * Short status line for a failed preview.
     *
     * Built from the classified [report], so it carries no HTTP status, response body or key. The
     * "미리듣기 실패" prefix does two jobs: it stops the message reading as a failure of the document
     * playback, and it is what makes the settings preview feedback style it as an error — the
     * classified titles alone contain neither 실패 nor 오류.
     */
    fun previewFailureMessage(report: CloudErrorReport): String = "미리듣기 실패: ${report.title}"
}
