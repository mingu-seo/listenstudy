package com.codro.listenstudy.domain.player

data class PlaybackPersistenceRequest(val documentId: String, val index: Int, val speed: Float)

class PlaybackPersistenceDecision {
    private var last: PlaybackPersistenceRequest? = null

    fun next(documentId: String?, state: PlaybackState): PlaybackPersistenceRequest? {
        val request = documentId?.let {
            PlaybackPersistenceRequest(it, state.currentIndex, state.speed)
        } ?: return null
        if (request == last) return null
        last = request
        return request
    }
}

/** Keeps the newest unsaved snapshot independently of debounce timing. */
class PlaybackPersistenceCoordinator {
    private val decision = PlaybackPersistenceDecision()
    private var pending: PlaybackPersistenceRequest? = null

    @Synchronized
    fun observe(documentId: String?, state: PlaybackState): PlaybackPersistenceRequest? {
        val changed = decision.next(documentId, state) ?: return null
        pending = changed
        return changed
    }

    @Synchronized
    fun snapshotForFlush(): PlaybackPersistenceRequest? = pending

    @Synchronized
    fun saved(request: PlaybackPersistenceRequest) {
        if (pending == request) pending = null
    }
}
