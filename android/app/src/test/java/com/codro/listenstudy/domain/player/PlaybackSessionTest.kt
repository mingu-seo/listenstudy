package com.codro.listenstudy.domain.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSessionTest {
    @Test fun `commands manipulate the single owned controller`() = runTest {
        val spoken = mutableListOf<String>()
        val session = PlaybackSession(listOf("one", "two"), speak = { text, _, _ -> spoken += text })

        session.execute(PlaybackSessionCommand.Play)
        session.execute(PlaybackSessionCommand.Next)
        session.execute(PlaybackSessionCommand.SetSpeed(2f))
        session.execute(PlaybackSessionCommand.Jump(0))

        assertEquals(listOf("one", "two"), spoken)
        assertEquals(0, session.state.value.currentIndex)
        assertEquals(2f, session.state.value.speed)
        assertEquals(PlaybackStatus.Paused, session.state.value.status)
    }

    @Test fun `restored session is paused and never speaks`() = runTest {
        var speaks = 0
        val session = PlaybackSession(emptyList(), speak = { _, _, _ -> speaks++ })

        session.restore(listOf("saved", "position"), index = 1, speed = 1.4f)

        assertEquals(0, speaks)
        assertEquals(1, session.state.value.currentIndex)
        assertEquals(1.4f, session.state.value.speed)
        assertEquals(PlaybackStatus.Paused, session.state.value.status)
    }

    @Test fun `stale completion after document replacement is ignored`() = runTest {
        val session = PlaybackSession(listOf("old"), speak = { _, _, _ -> })
        session.execute(PlaybackSessionCommand.Play)
        session.execute(PlaybackSessionCommand.ReplaceDocument(listOf("new"), 0, 1f))

        session.onSentenceDone("listenstudy_sentence_0_0")

        assertEquals("new", session.state.value.currentSentence)
        assertEquals(PlaybackStatus.Paused, session.state.value.status)
    }

    @Test fun `retry after a failure re-speaks the same sentence and never skips it`() = runTest {
        // The exact sequence a cloud failure produces: speak -> error -> user taps 다시 시도. The
        // failed sentence must be the one that plays again, and the abandoned attempt's late
        // completion must not silently advance past it.
        val spoken = mutableListOf<String>()
        val utteranceIds = mutableListOf<String>()
        val session = PlaybackSession(listOf("실패한 문장", "다음 문장"), speak = { text, id, _ ->
            spoken += text
            utteranceIds += id
        })

        session.execute(PlaybackSessionCommand.Play)
        val failedAttempt = utteranceIds.single()
        session.onPlaybackError()

        assertEquals(0, session.state.value.currentIndex)
        assertEquals(PlaybackStatus.Paused, session.state.value.status)

        session.execute(PlaybackSessionCommand.Play)
        session.onSentenceDone(failedAttempt)

        assertEquals(listOf("실패한 문장", "실패한 문장"), spoken)
        assertEquals(0, session.state.value.currentIndex)
        assertEquals(PlaybackStatus.Playing, session.state.value.status)
    }

    @Test fun `switching engines after a failure resumes the same sentence`() = runTest {
        // 휴대폰 음성으로 듣기 only swaps the engine behind `speak`; position must be untouched, so the
        // sentence the cloud failed on is the one the phone voice speaks.
        val cloudSpoken = mutableListOf<String>()
        val localSpoken = mutableListOf<String>()
        var useCloud = true
        val session = PlaybackSession(listOf("첫 문장", "두 번째 문장"), speak = { text, _, _ ->
            if (useCloud) cloudSpoken += text else localSpoken += text
        })

        session.execute(PlaybackSessionCommand.Play)
        session.onPlaybackError()
        useCloud = false
        session.execute(PlaybackSessionCommand.Play)

        assertEquals(listOf("첫 문장"), cloudSpoken)
        assertEquals(listOf("첫 문장"), localSpoken)
        assertEquals(0, session.state.value.currentIndex)
        assertEquals(PlaybackStatus.Playing, session.state.value.status)
    }

    @Test fun `delayed completion from old attempt cannot advance replayed same sentence`() = runTest {
        val utteranceIds = mutableListOf<String>()
        val session = PlaybackSession(listOf("same", "next"), speak = { _, id, _ -> utteranceIds += id })

        session.execute(PlaybackSessionCommand.Play)
        val oldAttempt = utteranceIds.single()
        session.execute(PlaybackSessionCommand.Pause)
        session.execute(PlaybackSessionCommand.Play)

        session.onSentenceDone(oldAttempt)

        assertEquals(0, session.state.value.currentIndex)
        assertEquals(PlaybackStatus.Playing, session.state.value.status)
        assertEquals(2, utteranceIds.distinct().size)
    }
}
