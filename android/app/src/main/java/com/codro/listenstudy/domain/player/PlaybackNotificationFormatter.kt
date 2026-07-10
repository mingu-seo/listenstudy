package com.codro.listenstudy.domain.player

object PlaybackNotificationFormatter {
    fun playPauseLabel(status: String): String = if (status == "재생 중") "일시정지" else "재생"

    fun mediaSubtitle(status: String, progress: String): String {
        return listOf(status, progress)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
}
