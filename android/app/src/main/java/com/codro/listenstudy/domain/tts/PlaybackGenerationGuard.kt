package com.codro.listenstudy.domain.tts

import java.util.concurrent.atomic.AtomicLong

/**
 * Guards callbacks that may be delivered after playback has been replaced or stopped.
 * The validity check intentionally runs at callback execution time.
 */
class PlaybackGenerationGuard {
    private val generation = AtomicLong(0L)

    fun invalidateAndGet(): Long = generation.incrementAndGet()

    fun current(): Long = generation.get()

    fun isCurrent(token: Long): Boolean = token == generation.get()

    inline fun runIfCurrent(token: Long, action: () -> Unit): Boolean {
        if (!isCurrent(token)) return false
        action()
        return true
    }
}
