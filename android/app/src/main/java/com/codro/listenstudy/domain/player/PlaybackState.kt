package com.codro.listenstudy.domain.player

enum class PlaybackStatus {
    Idle,
    Playing,
    Paused,
    Completed,
}

data class PlaybackState(
    val sentences: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val speed: Float = 1.0f,
) {
    val currentSentence: String?
        get() = sentences.getOrNull(currentIndex)
}
