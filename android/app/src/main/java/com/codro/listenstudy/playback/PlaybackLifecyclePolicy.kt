package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackStatus

/** Rejects a restore result when user/session content changed while storage was loading. */
class PlaybackRestoreGate {
    private var generation: Long = 0

    fun beginRestore(): Long = generation

    fun contentChanged() {
        generation++
    }

    fun canApply(token: Long): Boolean = token == generation
}

data class ServiceLifecycleDecision(
    val foreground: Boolean,
    val sticky: Boolean,
)

object PlaybackServiceLifecyclePolicy {
    fun decide(status: PlaybackStatus, previewActive: Boolean): ServiceLifecycleDecision {
        val active = status == PlaybackStatus.Playing || previewActive
        return ServiceLifecycleDecision(foreground = active, sticky = active)
    }
}

/** Keeps error behavior identical for local and cloud playback while preserving engine status text. */
class PlaybackErrorCoordinator(private val onPlaybackError: () -> Unit) {
    fun onError(@Suppress("UNUSED_PARAMETER") message: String) {
        onPlaybackError()
    }
}
