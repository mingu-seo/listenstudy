package com.codro.listenstudy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Couples the reader BottomPlayerBar primary control row to a testable ordering so that the row
 * always renders 처음 → 이전 → 재생/일시정지 → 다음 and the 처음 control is wired to sentence index 0
 * (the same [PlayerViewModel.jumpTo] / ServiceCommand.Jump path used by onSentenceClick).
 */
class PrimaryPlaybackControlsTest {
    @Test
    fun `primary playback row shows first previous playpause next in order`() {
        assertEquals(
            listOf(
                PrimaryPlaybackControl.First,
                PrimaryPlaybackControl.Previous,
                PrimaryPlaybackControl.PlayPause,
                PrimaryPlaybackControl.Next,
            ),
            PlayerUiFormatter.primaryPlaybackControls(),
        )
    }

    @Test
    fun `first control leads the row and jumps to sentence index zero`() {
        assertEquals(PrimaryPlaybackControl.First, PlayerUiFormatter.primaryPlaybackControls().first())
        assertEquals(0, PlayerUiFormatter.FIRST_SENTENCE_INDEX)
    }
}
