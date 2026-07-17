package com.codro.listenstudy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuietReaderUiPolicyTest {
    @Test
    fun `settings sections follow user-first approved order`() {
        assertEquals(
            listOf("재생 및 음성", "휴대폰 TTS", "Google Cloud 고급", "저장 공간"),
            QuietReaderUiPolicy.settingsSections,
        )
    }

    @Test
    fun `reader exposes progress format arguments and sentence numbers without UI strings`() {
        assertEquals(ProgressFormatArgs(32, 180, 18), QuietReaderUiPolicy.progressFormatArgs(31, 180))
        assertEquals(ProgressFormatArgs(1, 1, 100), QuietReaderUiPolicy.progressFormatArgs(0, 1))
        assertEquals(null, QuietReaderUiPolicy.progressFormatArgs(0, 0))
        assertEquals(5, QuietReaderUiPolicy.sentenceNumber(4))
    }

    @Test
    fun `header and progress bar both count the current sentence as completed`() {
        assertEquals(ProgressFormatArgs(3, 7, 43), QuietReaderUiPolicy.progressFormatArgs(2, 7))
        assertEquals(3f / 7f, PlayerUiFormatter.progressFraction(2, 7), 0.0001f)
        assertEquals(1f, PlayerUiFormatter.progressFraction(0, 1), 0.0001f)
    }
}
