package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackState
import com.codro.listenstudy.domain.player.PlaybackPersistenceDecision
import com.codro.listenstudy.domain.player.PlaybackPersistenceCoordinator
import org.junit.Assert.*
import org.junit.Test

class PlaybackServiceCoordinatorTest {
    @Test fun `old owner cannot detach newer service and pending queue is bounded`() {
        val facade = PlaybackServiceFacade(maxPending = 2)
        facade.dispatch(ServiceCommand.Play)
        facade.dispatch(ServiceCommand.Next)
        facade.dispatch(ServiceCommand.Previous)
        val first = mutableListOf<ServiceCommand>()
        val owner1 = Any()
        facade.attach(owner1, PlaybackServiceUiState(), first::add)
        assertEquals(listOf(ServiceCommand.Next, ServiceCommand.Previous), first)
        val second = mutableListOf<ServiceCommand>()
        val owner2 = Any()
        facade.attach(owner2, PlaybackServiceUiState(), second::add)
        facade.detach(owner1)
        facade.dispatch(ServiceCommand.Pause)
        assertEquals(listOf(ServiceCommand.Pause), second)
    }

    @Test fun `state never contains raw api key`() {
        assertFalse(PlaybackServiceUiState::class.java.declaredFields.any {
            it.type == String::class.java && it.name.contains("apiKey", ignoreCase = true)
        })
    }

    @Test fun `persistence decision tracks active document index and speed only`() {
        val decision = PlaybackPersistenceDecision()
        assertNull(decision.next(null, PlaybackState(currentIndex = 2, speed = 1.2f)))
        val first = decision.next("doc", PlaybackState(currentIndex = 2, speed = 1.2f))
        assertNotNull(first)
        assertNull(decision.next("doc", PlaybackState(currentIndex = 2, speed = 1.2f)))
        assertNotNull(decision.next("doc", PlaybackState(currentIndex = 3, speed = 1.2f)))
    }

    @Test fun `flush snapshot retains latest debounced position until saved`() {
        val coordinator = PlaybackPersistenceCoordinator()
        val first = coordinator.observe("doc", PlaybackState(currentIndex = 1, speed = 1f))!!
        val latest = coordinator.observe("doc", PlaybackState(currentIndex = 4, speed = 1.5f))!!
        assertEquals(latest, coordinator.snapshotForFlush())
        coordinator.saved(first)
        assertEquals(latest, coordinator.snapshotForFlush())
        coordinator.saved(latest)
        assertNull(coordinator.snapshotForFlush())
    }

    @Test fun `replacement can flush old document before observing new document`() {
        val coordinator = PlaybackPersistenceCoordinator()
        coordinator.observe("old", PlaybackState(currentIndex = 7, speed = 1.3f))
        assertEquals("old", coordinator.snapshotForFlush()!!.documentId)
        coordinator.observe("new", PlaybackState(currentIndex = 0, speed = 1f))
        assertEquals("new", coordinator.snapshotForFlush()!!.documentId)
    }

    @Test fun `detach exposes disconnected non-playing state`() {
        val facade = PlaybackServiceFacade()
        val owner = Any()
        facade.attach(owner, PlaybackServiceUiState(playback = PlaybackState(status = com.codro.listenstudy.domain.player.PlaybackStatus.Playing))) {}
        facade.detach(owner)
        assertNotEquals(com.codro.listenstudy.domain.player.PlaybackStatus.Playing, facade.state.value.playback.status)
    }
}
