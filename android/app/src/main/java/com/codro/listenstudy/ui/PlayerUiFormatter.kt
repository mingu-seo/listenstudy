package com.codro.listenstudy.ui

import com.codro.listenstudy.domain.tts.PlaybackMode
import com.codro.listenstudy.ui.theme.QuietReaderType
import kotlin.math.roundToInt

data class CloudPreviewFeedback(
    val message: String,
    val inProgress: Boolean,
    val isError: Boolean,
)

data class BottomPlayerControlVisibility(
    val showPrimaryPlaybackControls: Boolean,
    val showAdditionalControls: Boolean,
)

data class DocumentTextLayout(
    val sentenceFontSizeSp: Int,
    val sentenceLineHeightSp: Int,
    val sentenceVerticalPaddingDp: Int,
    val sentenceSpacingDp: Int,
    val showCompactDiagnostics: Boolean,
)

object PlayerUiFormatter {
    fun documentTextLayout(): DocumentTextLayout = DocumentTextLayout(
        sentenceFontSizeSp = QuietReaderType.ReaderFontSizeSp,
        sentenceLineHeightSp = QuietReaderType.ReaderLineHeightSp,
        sentenceVerticalPaddingDp = 1,
        sentenceSpacingDp = 0,
        showCompactDiagnostics = false,
    )

    fun bottomPlayerControlVisibility(expanded: Boolean): BottomPlayerControlVisibility =
        BottomPlayerControlVisibility(
            showPrimaryPlaybackControls = true,
            showAdditionalControls = expanded,
        )

    fun sentenceCounter(currentIndex: Int, total: Int): String {
        if (total <= 0) return "현재 문장 0 / 0"
        val current = (currentIndex + 1).coerceIn(1, total)
        return "현재 문장 $current / $total"
    }

    fun progressPercent(currentIndex: Int, total: Int): Int =
        (QuietReaderUiPolicy.progressFraction(currentIndex, total) * 100).roundToInt()

    fun progressFraction(currentIndex: Int, total: Int): Float =
        QuietReaderUiPolicy.progressFraction(currentIndex, total)

    fun speedLabel(speed: Float): String = "%.1fx".format(speed)

    /**
     * The cloud preview feedback panel lives directly under the `Cloud 음성 미리듣기` button, so it is
     * shown only in the active cloud modes where that button is rendered. In 휴대폰 TTS there is no
     * cloud preview to report on, so the panel stays hidden.
     */
    fun showsCloudPreviewFeedback(mode: PlaybackMode): Boolean = mode != PlaybackMode.ON_DEVICE

    fun cloudPreviewFeedback(status: String): CloudPreviewFeedback {
        val inProgress = status == "클라우드 음성 준비 중…"
        val isError = status.contains("실패") || status.contains("오류") || status.contains("API 키를 먼저")
        return CloudPreviewFeedback(status, inProgress, isError)
    }
}
