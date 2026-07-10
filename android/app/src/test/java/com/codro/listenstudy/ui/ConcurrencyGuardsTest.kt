package com.codro.listenstudy.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationGuardTest {
    @Test
    fun `only the latest document operation may apply its result`() {
        val guard = GenerationGuard()
        val startup = guard.next()
        val userOpen = guard.next()

        assertFalse(guard.isCurrent(startup))
        assertTrue(guard.isCurrent(userOpen))
    }
}

class PlaybackSaveCoordinatorTest {
    @Test
    fun `request keeps immutable document and playback snapshot`() {
        val coordinator = PlaybackSaveCoordinator()
        val request = coordinator.request("old", 7, 1.5f)
        coordinator.request("new", 0, 1f)

        assertEquals("old", request.documentId)
        assertEquals(7, request.index)
        assertEquals(1.5f, request.speed)
    }

    @Test
    fun `serialized writer skips a superseded request waiting behind a write`() = runTest {
        val coordinator = PlaybackSaveCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val first = coordinator.request("a", 0, 1f)
        val firstJob = async {
            coordinator.save(first) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                writes += it.documentId
            }
        }
        firstStarted.await()
        val stale = coordinator.request("old", 3, 1f)
        val staleJob = async { coordinator.save(stale) { writes += it.documentId } }
        val latest = coordinator.request("new", 4, 2f)
        val latestJob = async { coordinator.save(latest) { writes += it.documentId } }
        releaseFirst.complete(Unit)
        firstJob.await(); staleJob.await(); latestJob.await()

        assertEquals(listOf("a", "new"), writes)
    }
}
