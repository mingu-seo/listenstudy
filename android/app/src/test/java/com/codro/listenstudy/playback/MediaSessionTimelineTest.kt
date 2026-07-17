package com.codro.listenstudy.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSessionTimelineTest {
    @Test
    fun `timeline exposes estimated text duration and current sentence start`() {
        val sentences = listOf("가나다라", "가나다라마바사아자차")

        assertEquals(
            MediaSessionTimeline(positionMs = 1_000L, durationMs = 2_800L),
            MediaSessionTimeline.from(sentences = sentences, currentIndex = 1),
        )
    }

    @Test
    fun `timeline clamps invalid indexes and reaches duration when completed`() {
        val sentences = listOf("첫 문장", "두 번째 문장")
        val active = MediaSessionTimeline.from(sentences = sentences, currentIndex = 99)
        val completed = MediaSessionTimeline.from(sentences = sentences, currentIndex = 1, completed = true)

        assertEquals(1_000L, active.positionMs)
        assertEquals(completed.durationMs, completed.positionMs)
    }

    @Test
    fun `empty document reports zero position and duration`() {
        assertEquals(
            MediaSessionTimeline(positionMs = 0L, durationMs = 0L),
            MediaSessionTimeline.from(sentences = emptyList(), currentIndex = 0),
        )
    }
}
