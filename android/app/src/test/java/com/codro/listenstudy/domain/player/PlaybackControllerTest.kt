package com.codro.listenstudy.domain.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {
    @Test
    fun play_invokes_speaker_with_current_sentence_and_speed() = runTest {
        val spoken = mutableListOf<String>()
        val controller = PlaybackController(
            sentences = listOf("첫 문장", "두 번째 문장"),
            speak = { text, utteranceId, speed -> spoken += "$utteranceId|$speed|$text" },
        )

        controller.setSpeed(1.5f)
        controller.play()

        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
        assertEquals(listOf("listenstudy_sentence_0_0|1.5|첫 문장"), spoken)
    }

    @Test
    fun next_at_end_marks_completed() = runTest {
        val controller = PlaybackController(sentences = listOf("하나"))

        controller.next()

        assertEquals(PlaybackStatus.Completed, controller.state.value.status)
    }

    @Test
    fun manual_next_while_playing_moves_to_next_sentence_and_keeps_playing() = runTest {
        val spoken = mutableListOf<String>()
        var stopCount = 0
        val controller = PlaybackController(
            sentences = listOf("첫 문장", "두 번째 문장", "세 번째 문장"),
            speak = { text, utteranceId, _ -> spoken += "$utteranceId|$text" },
            stopSpeaking = { stopCount++ },
        )

        controller.play()
        controller.next(autoPlay = true)

        assertEquals(1, controller.state.value.currentIndex)
        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
        assertEquals(
            listOf(
                "listenstudy_sentence_0_0|첫 문장",
                "listenstudy_sentence_0_1|두 번째 문장",
            ),
            spoken,
        )
        assertEquals(1, stopCount)
    }

    @Test
    fun manual_previous_while_playing_moves_to_previous_sentence_and_keeps_playing() = runTest {
        val spoken = mutableListOf<String>()
        var stopCount = 0
        val controller = PlaybackController(
            sentences = listOf("첫 문장", "두 번째 문장", "세 번째 문장"),
            speak = { text, utteranceId, _ -> spoken += "$utteranceId|$text" },
            stopSpeaking = { stopCount++ },
        )

        controller.jumpTo(1)
        controller.play()
        controller.previous(autoPlay = true)

        assertEquals(0, controller.state.value.currentIndex)
        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
        assertEquals(
            listOf(
                "listenstudy_sentence_0_1|두 번째 문장",
                "listenstudy_sentence_0_0|첫 문장",
            ),
            spoken,
        )
        assertEquals(2, stopCount)
    }

    @Test
    fun sentence_done_auto_advances_without_stopping_finished_tts() = runTest {
        val spoken = mutableListOf<String>()
        var stopCount = 0
        val controller = PlaybackController(
            sentences = listOf("첫 문장", "두 번째 문장"),
            speak = { text, utteranceId, _ -> spoken += "$utteranceId|$text" },
            stopSpeaking = { stopCount++ },
        )

        controller.play()
        controller.onSentenceDone("listenstudy_sentence_0_0")

        assertEquals(1, controller.state.value.currentIndex)
        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
        assertEquals(
            listOf(
                "listenstudy_sentence_0_0|첫 문장",
                "listenstudy_sentence_0_1|두 번째 문장",
            ),
            spoken,
        )
        assertEquals(0, stopCount)
    }

    @Test
    fun playback_error_leaves_current_sentence_paused_for_retry() = runTest {
        val controller = PlaybackController(sentences = listOf("첫 문장", "두 번째 문장"))
        controller.play()

        controller.onPlaybackError()

        assertEquals(0, controller.state.value.currentIndex)
        assertEquals(PlaybackStatus.Paused, controller.state.value.status)
    }

    @Test
    fun stale_sentence_done_does_not_advance_when_paused() = runTest {
        val controller = PlaybackController(sentences = listOf("첫 문장", "두 번째 문장"))

        controller.play()
        controller.pause()
        controller.onSentenceDone("listenstudy_sentence_0_0")

        assertEquals(0, controller.state.value.currentIndex)
        assertEquals(PlaybackStatus.Paused, controller.state.value.status)
    }

    @Test
    fun replace_sentences_stops_playback_and_resets_position() = runTest {
        var stopCount = 0
        val controller = PlaybackController(
            sentences = listOf("기존 첫 문장", "기존 둘째 문장"),
            stopSpeaking = { stopCount++ },
        )

        controller.play()
        controller.replaceSentences(listOf("새 첫 문장", "새 둘째 문장"))

        assertEquals(0, controller.state.value.currentIndex)
        assertEquals(PlaybackStatus.Idle, controller.state.value.status)
        assertEquals(listOf("새 첫 문장", "새 둘째 문장"), controller.state.value.sentences)
        assertEquals(1, stopCount)
    }

    @Test
    fun stale_sentence_done_from_replaced_document_is_ignored() = runTest {
        val spokenIds = mutableListOf<String>()
        val controller = PlaybackController(
            sentences = listOf("기존 첫 문장", "기존 둘째 문장"),
            speak = { _, utteranceId, _ -> spokenIds += utteranceId },
        )
        controller.play()
        val oldUtteranceId = spokenIds.single()

        controller.replaceSentences(listOf("새 첫 문장", "새 둘째 문장"))
        controller.play()
        controller.onSentenceDone(oldUtteranceId)

        assertEquals(0, controller.state.value.currentIndex)
        assertEquals(PlaybackStatus.Playing, controller.state.value.status)
        assertEquals(listOf("listenstudy_sentence_0_0", "listenstudy_sentence_1_0"), spokenIds)
    }

    @Test
    fun speed_is_clamped_to_supported_range() {
        val controller = PlaybackController(sentences = listOf("하나"))

        controller.setSpeed(9.0f)
        assertEquals(3.0f, controller.state.value.speed)
        controller.setSpeed(0.1f)
        assertEquals(0.5f, controller.state.value.speed)
    }

    @Test
    fun jump_to_out_of_range_is_clamped() {
        val controller = PlaybackController(sentences = listOf("하나", "둘"))

        controller.jumpTo(99)

        assertTrue(controller.state.value.currentIndex == 1)
    }
}
