package com.codro.listenstudy.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackServiceCommandTest {
    @Test
    fun fromAction_maps_notification_actions_to_playback_commands() {
        assertEquals(PlaybackServiceCommand.PlayPause, PlaybackServiceCommand.fromAction("com.codro.listenstudy.action.PLAY_PAUSE"))
        assertEquals(PlaybackServiceCommand.Previous, PlaybackServiceCommand.fromAction("com.codro.listenstudy.action.PREVIOUS"))
        assertEquals(PlaybackServiceCommand.Next, PlaybackServiceCommand.fromAction("com.codro.listenstudy.action.NEXT"))
    }

    @Test
    fun fromAction_returns_null_for_unknown_action() {
        assertNull(PlaybackServiceCommand.fromAction("unknown"))
        assertNull(PlaybackServiceCommand.fromAction(null))
    }
}
