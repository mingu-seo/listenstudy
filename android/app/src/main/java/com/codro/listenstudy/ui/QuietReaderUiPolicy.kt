package com.codro.listenstudy.ui

import com.codro.listenstudy.domain.reader.ReadingProgressPolicy

data class ProgressFormatArgs(
    val current: Int,
    val total: Int,
    val percent: Int,
)

object QuietReaderUiPolicy {
    val settingsSections = listOf(
        "재생 및 음성",
        "휴대폰 TTS",
        "Google Cloud 고급",
        "저장 공간",
    )

    fun progressFormatArgs(currentIndex: Int, sentenceCount: Int): ProgressFormatArgs? =
        if (sentenceCount <= 0) {
            null
        } else {
            ProgressFormatArgs(
                current = ReadingProgressPolicy.currentPosition(currentIndex, sentenceCount),
                total = sentenceCount,
                percent = ReadingProgressPolicy.percent(currentIndex, sentenceCount),
            )
        }

    fun progressFraction(currentIndex: Int, sentenceCount: Int): Float {
        return ReadingProgressPolicy.fraction(currentIndex, sentenceCount)
    }

    fun sentenceNumber(index: Int): Int = index.coerceAtLeast(0) + 1
}
