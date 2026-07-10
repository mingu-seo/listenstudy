package com.codro.listenstudy.ui

import kotlin.math.roundToInt

data class CloudPreviewFeedback(
    val message: String,
    val inProgress: Boolean,
    val isError: Boolean,
)

object PlayerUiFormatter {
    fun sentenceCounter(currentIndex: Int, total: Int): String {
        if (total <= 0) return "현재 문장 0 / 0"
        val current = (currentIndex + 1).coerceIn(1, total)
        return "현재 문장 $current / $total"
    }

    fun progressPercent(currentIndex: Int, total: Int): Int {
        if (total <= 1) return 0
        val position = currentIndex.coerceIn(0, total - 1)
        return ((position.toFloat() / (total - 1).toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    fun progressFraction(currentIndex: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (progressPercent(currentIndex, total) / 100f).coerceIn(0f, 1f)
    }

    fun speedLabel(speed: Float): String = "%.1fx".format(speed)

    fun cloudPreviewFeedback(status: String): CloudPreviewFeedback {
        val inProgress = status == "클라우드 음성 준비 중…"
        val isError = status.contains("실패") || status.contains("오류") || status.contains("API 키를 먼저")
        return CloudPreviewFeedback(status, inProgress, isError)
    }
}
