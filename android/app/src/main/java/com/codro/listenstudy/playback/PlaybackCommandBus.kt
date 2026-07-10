package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackServiceCommand

object PlaybackCommandBus {
    private var listener: ((PlaybackServiceCommand) -> Unit)? = null

    fun setListener(listener: ((PlaybackServiceCommand) -> Unit)?) {
        this.listener = listener
    }

    fun dispatch(command: PlaybackServiceCommand) {
        listener?.invoke(command)
    }
}
