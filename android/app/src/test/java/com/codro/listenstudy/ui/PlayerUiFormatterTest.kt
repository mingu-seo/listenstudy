package com.codro.listenstudy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUiFormatterTest {
    @Test
    fun `current sentence label is one based and clamps empty list`() {
        assertEquals("현재 문장 3 / 7", PlayerUiFormatter.sentenceCounter(currentIndex = 2, total = 7))
        assertEquals("현재 문장 0 / 0", PlayerUiFormatter.sentenceCounter(currentIndex = 0, total = 0))
    }

    @Test
    fun `progress percent uses playback position and clamps bounds`() {
        assertEquals(33, PlayerUiFormatter.progressPercent(currentIndex = 2, total = 7))
        assertEquals(100, PlayerUiFormatter.progressPercent(currentIndex = 10, total = 7))
        assertEquals(0, PlayerUiFormatter.progressPercent(currentIndex = 0, total = 7))
        assertEquals(0, PlayerUiFormatter.progressPercent(currentIndex = 0, total = 0))
        assertEquals(0, PlayerUiFormatter.progressPercent(currentIndex = 0, total = 1))
    }

    @Test
    fun `speed label keeps one decimal place`() {
        assertEquals("1.0x", PlayerUiFormatter.speedLabel(1.0f))
        assertEquals("1.3x", PlayerUiFormatter.speedLabel(1.25f))
    }

    @Test
    fun `cloud preview feedback exposes progress while synthesis is pending`() {
        val feedback = PlayerUiFormatter.cloudPreviewFeedback("클라우드 음성 준비 중…")

        assertEquals("클라우드 음성 준비 중…", feedback.message)
        assertEquals(true, feedback.inProgress)
    }

    @Test
    fun `bottom player keeps primary playback controls visible while collapsed and expanded`() {
        val collapsed = PlayerUiFormatter.bottomPlayerControlVisibility(expanded = false)
        val expanded = PlayerUiFormatter.bottomPlayerControlVisibility(expanded = true)

        assertEquals(true, collapsed.showPrimaryPlaybackControls)
        assertEquals(false, collapsed.showAdditionalControls)
        assertEquals(true, expanded.showPrimaryPlaybackControls)
        assertEquals(true, expanded.showAdditionalControls)
    }

    @Test
    fun `document text layout uses compact sentence spacing and hides diagnostics`() {
        val layout = PlayerUiFormatter.documentTextLayout()

        assertEquals(3, layout.sentenceVerticalPaddingDp)
        assertEquals(0, layout.sentenceSpacingDp)
        assertEquals(false, layout.showCompactDiagnostics)
    }

    @Test
    fun `cloud preview feedback exposes terminal api error`() {
        val feedback = PlayerUiFormatter.cloudPreviewFeedback(
            "Google Cloud TTS 요청 실패 (HTTP 403): API 키의 애플리케이션 제한을 확인하세요.",
        )

        assertEquals(false, feedback.inProgress)
        assertEquals(true, feedback.isError)
    }
}
