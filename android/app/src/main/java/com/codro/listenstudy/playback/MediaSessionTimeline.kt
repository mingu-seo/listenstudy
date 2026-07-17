package com.codro.listenstudy.playback

data class MediaSessionTimeline(
    val positionMs: Long,
    val durationMs: Long,
) {
    companion object {
        private const val MILLIS_PER_CHARACTER = 180L
        private const val MIN_SENTENCE_DURATION_MS = 1_000L
        private const val MAX_SENTENCE_DURATION_MS = 30_000L

        fun from(
            sentences: List<String>,
            currentIndex: Int,
            completed: Boolean = false,
        ): MediaSessionTimeline {
            if (sentences.isEmpty()) return MediaSessionTimeline(positionMs = 0L, durationMs = 0L)

            val durations = sentences.map(::estimatedSentenceDurationMs)
            val duration = durations.sum()
            val boundedIndex = currentIndex.coerceIn(sentences.indices)
            val position = if (completed) duration else durations.take(boundedIndex).sum()

            return MediaSessionTimeline(positionMs = position, durationMs = duration)
        }

        private fun estimatedSentenceDurationMs(sentence: String): Long =
            (sentence.length * MILLIS_PER_CHARACTER)
                .coerceIn(MIN_SENTENCE_DURATION_MS, MAX_SENTENCE_DURATION_MS)
    }
}
