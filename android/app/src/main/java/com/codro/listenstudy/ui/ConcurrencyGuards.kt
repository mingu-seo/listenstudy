package com.codro.listenstudy.ui

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GenerationGuard {
    private var generation = 0L

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun isCurrent(candidate: Long): Boolean = candidate == generation
}

data class PlaybackSaveRequest(
    val documentId: String,
    val index: Int,
    val speed: Float,
    val revision: Long,
)

class PlaybackSaveCoordinator {
    private val mutex = Mutex()
    private var latestRevision = 0L

    @Synchronized
    fun request(documentId: String, index: Int, speed: Float): PlaybackSaveRequest {
        latestRevision += 1
        return PlaybackSaveRequest(documentId, index, speed, latestRevision)
    }

    suspend fun save(request: PlaybackSaveRequest, writer: suspend (PlaybackSaveRequest) -> Unit) {
        mutex.withLock {
            val current = synchronized(this) { request.revision == latestRevision }
            if (current) writer(request)
        }
    }
}
