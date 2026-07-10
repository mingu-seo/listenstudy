package com.codro.listenstudy.domain.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackNotificationFormatterTest {
    @Test
    fun playPauseLabel_is_pause_while_playing_and_play_otherwise() {
        assertEquals("일시정지", PlaybackNotificationFormatter.playPauseLabel("재생 중"))
        assertEquals("재생", PlaybackNotificationFormatter.playPauseLabel("일시정지"))
        assertEquals("재생", PlaybackNotificationFormatter.playPauseLabel("완료"))
    }

    @Test
    fun mediaSubtitle_joins_status_and_progress() {
        assertEquals(
            "재생 중 · 2 / 10 · 20% · 1.0x",
            PlaybackNotificationFormatter.mediaSubtitle("재생 중", "2 / 10 · 20% · 1.0x"),
        )
        assertEquals("재생 중", PlaybackNotificationFormatter.mediaSubtitle("재생 중", ""))
    }
}
