package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackState
import com.codro.listenstudy.domain.player.PlaybackPersistenceDecision
import com.codro.listenstudy.domain.player.PlaybackPersistenceCoordinator
import com.codro.listenstudy.domain.player.PlaybackPersistenceRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServiceCoordinatorTest {
    private fun req(index: Int) = PlaybackPersistenceRequest("doc", index, 1f)

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

    @Test fun `terminal completion persists final snapshot before stopping the service`() = runTest {
        // GAP 1: the stop path must AWAIT durable persistence of the final position before stopSelf,
        // otherwise the process can die before Room commits the completed position.
        val events = mutableListOf<String>()
        val saveGate = CompletableDeferred<Unit>()
        val coordinator = TerminalCompletionCoordinator(
            persist = { req -> events += "persist:${req.index}"; saveGate.await() },
            stop = { events += "stop" },
        )

        val token = coordinator.begin()!!
        val job = launch { coordinator.finalize(token, req(42)) }
        runCurrent()
        assertEquals("stop must not run until persistence completes", listOf("persist:42"), events)

        saveGate.complete(Unit)
        job.join()
        assertEquals(listOf("persist:42", "stop"), events)
    }

    @Test fun `terminal completion still stops when persistence fails`() = runTest {
        // Explicit safe failure behavior: a save failure must never leave the service running.
        val events = mutableListOf<String>()
        val coordinator = TerminalCompletionCoordinator(
            persist = { throw RuntimeException("db unavailable") },
            stop = { events += "stop" },
        )

        coordinator.finalize(coordinator.begin()!!, req(7))

        assertEquals(listOf("stop"), events)
    }

    @Test fun `terminal completion rethrows cancellation and does not stop`() = runTest {
        // MINOR: cancellation must not be swallowed as an ordinary persistence failure.
        var stopped = false
        val coordinator = TerminalCompletionCoordinator(
            persist = { throw CancellationException("scope cancelled") },
            stop = { stopped = true },
        )

        var rethrown = false
        try {
            coordinator.finalize(coordinator.begin()!!, req(5))
        } catch (e: CancellationException) {
            rethrown = true
        }

        assertTrue("cancellation must propagate", rethrown)
        assertFalse("cancelled finalize must not stop", stopped)
    }

    @Test fun `terminal completion stops without persisting when there is no snapshot`() = runTest {
        val events = mutableListOf<String>()
        val coordinator = TerminalCompletionCoordinator(
            persist = { events += "persist" },
            stop = { events += "stop" },
        )

        coordinator.finalize(coordinator.begin()!!, null)

        assertEquals(listOf("stop"), events)
    }

    @Test fun `terminal completion begins exactly once for repeated callbacks`() = runTest {
        var persists = 0
        var stops = 0
        val coordinator = TerminalCompletionCoordinator(
            persist = { persists++ },
            stop = { stops++ },
        )

        val first = coordinator.begin()
        val second = coordinator.begin()
        coordinator.finalize(first!!, req(3))

        assertNotNull(first)
        assertNull("a second terminal callback must not start another finalization", second)
        assertEquals(1, persists)
        assertEquals(1, stops)
    }

    @Test fun `newer work during a pending final save cancels the stale stop`() = runTest {
        // IMPORTANT 2: a Play/Previous/ReplaceDocument (generation bump) while the final save is in
        // flight must invalidate the stale finalizer so it cannot stop the newer session.
        val events = mutableListOf<String>()
        val saveGate = CompletableDeferred<Unit>()
        val coordinator = TerminalCompletionCoordinator(
            persist = { events += "persist"; saveGate.await() },
            stop = { events += "stop" },
        )

        val token = coordinator.begin()!!
        val job = launch { coordinator.finalize(token, req(9)) }
        runCurrent()
        assertEquals(listOf("persist"), events)

        coordinator.invalidate() // newer work arrives while the old save is pending
        saveGate.complete(Unit)
        job.join()

        assertEquals("stale finalizer must not stop the newer session", listOf("persist"), events)
    }

    @Test fun `revival accepted after completion detected but before finalize runs prevents the stale stop`() = runTest {
        // IMPORTANT 2 timing: the generation token is captured synchronously at begin() (completion
        // detection). Any revival accepted afterwards — even before the terminal continuation runs —
        // must make the stale finalizer skip stop, regardless of how the coroutines interleave.
        var stopped = false
        val coordinator = TerminalCompletionCoordinator(
            persist = {},
            stop = { stopped = true },
        )

        val token = coordinator.begin()!! // completion detected on the main thread
        coordinator.invalidate()          // revival accepted before the delayed continuation executes
        coordinator.finalize(token, req(9))

        assertFalse("finalizer whose generation was superseded must not stop", stopped)
    }

    @Test fun `finalizer usable again for the newer session after invalidation`() = runTest {
        val events = mutableListOf<String>()
        val coordinator = TerminalCompletionCoordinator(
            persist = { events += "persist:${it.index}" },
            stop = { events += "stop" },
        )

        coordinator.invalidate()
        coordinator.finalize(coordinator.begin()!!, req(4))

        assertEquals(listOf("persist:4", "stop"), events)
    }

    @Test fun `serialized writer commits earlier saves before the final barrier and reports it last`() = runTest {
        // IMPORTANT 1: the terminal barrier must resume only after every earlier queued save committed,
        // with the final snapshot written last — no stale write may follow it.
        val committed = mutableListOf<Int>()
        val firstWrite = CompletableDeferred<Unit>()
        val writer = PlaybackPersistenceWriter(
            scope = backgroundScope,
            write = { r ->
                if (r.index == 1) firstWrite.await()
                committed += r.index
            },
        )

        assertTrue(writer.submit(req(1))) // earlier, held open
        assertTrue(writer.submit(req(2))) // earlier
        val barrier = launch { writer.barrier(req(3)) }
        runCurrent()
        assertTrue("barrier must not resume while earlier saves are pending", committed.isEmpty())

        firstWrite.complete(Unit)
        barrier.join()

        assertEquals(listOf(1, 2, 3), committed)
    }

    @Test fun `producer gate drops a delayed producer write that becomes ready after the terminal barrier`() = runTest {
        // IMPORTANT 1: a stale/debounced producer that becomes ready while the terminal persistence is
        // held open must be gated out, so the final snapshot is the last committed write.
        val committed = mutableListOf<Int>()
        val releaseFinal = CompletableDeferred<Unit>()
        val writer = PlaybackPersistenceWriter(
            scope = backgroundScope,
            write = { r ->
                if (r.index == 9) releaseFinal.await()
                committed += r.index
            },
        )
        val gate = PersistenceProducerGate()

        // Terminal finalization begins: producer is gated, final barrier is submitted and held open.
        gate.beginTerminal()
        val barrier = launch { writer.barrier(req(9)) }
        runCurrent()
        assertTrue("final write is held open", committed.isEmpty())

        // A delayed/debounced producer becomes ready now — it must be dropped, not queued behind 9.
        if (gate.accept()) writer.submit(req(3))
        runCurrent()

        releaseFinal.complete(Unit)
        barrier.join()

        assertEquals("only the final snapshot is committed, and it is last", listOf(9), committed)
    }

    @Test fun `producer gate reopens for the revived session`() {
        val gate = PersistenceProducerGate()
        gate.beginTerminal()
        assertFalse(gate.accept())
        gate.revive()
        assertTrue(gate.accept())
    }

    @Test fun `serialized writer keeps draining after an ordinary save failure`() = runTest {
        val committed = mutableListOf<Int>()
        val writer = PlaybackPersistenceWriter(
            scope = backgroundScope,
            write = { r ->
                if (r.index == 1) throw RuntimeException("transient db error")
                committed += r.index
            },
        )

        assertTrue(writer.submit(req(1)))
        writer.barrier(req(2))

        assertEquals(listOf(2), committed)
    }

    @Test fun `writer cancellation fails the in-flight and queued barriers instead of hanging`() = runTest {
        // IMPORTANT 3: a CancellationException from persistence must not leave the consumer dead with
        // barriers waiting forever. The in-flight barrier and every queued barrier must complete.
        val firstWrite = CompletableDeferred<Unit>()
        val writer = PlaybackPersistenceWriter(
            scope = backgroundScope,
            write = { r ->
                if (r.index == 1) {
                    firstWrite.await()
                    throw CancellationException("persist cancelled")
                }
            },
        )

        val results = mutableMapOf<Int, Result<Unit>>()
        val b1 = launch { results[1] = runCatching { writer.barrier(req(1)) } }
        runCurrent() // b1's task is being processed, awaiting firstWrite
        val b2 = launch { results[2] = runCatching { writer.barrier(req(2)) } }
        runCurrent() // b2's task is queued behind b1

        firstWrite.complete(Unit) // b1 write throws cancellation -> writer shuts down
        b1.join()
        b2.join()

        assertTrue("in-flight barrier must fail", results[1]!!.isFailure)
        assertTrue("queued barrier must fail rather than hang", results[2]!!.isFailure)
    }

    @Test fun `writer rejects submit and barrier after cancellation and drains once`() = runTest {
        var drained = 0
        val writer = PlaybackPersistenceWriter(
            scope = backgroundScope,
            write = { throw CancellationException("dead") },
            onDrained = { drained++ },
        )

        launch { runCatching { writer.barrier(req(1)) } }.join() // triggers shutdown

        assertEquals("onDrained runs exactly once", 1, drained)
        assertFalse("submit after cancellation is rejected explicitly", writer.submit(req(2)))
        var barrierRejected = false
        try {
            writer.barrier(req(3))
        } catch (e: Throwable) {
            barrierRejected = true
        }
        assertTrue("future barrier after cancellation is rejected", barrierRejected)

        writer.close() // idempotent: must not drain a second time
        runCurrent()
        assertEquals(1, drained)
    }

    @Test fun `ui command restarts service before dispatch after background process loss`() {
        val events = mutableListOf<String>()
        val dispatcher = PlaybackCommandDispatcher(
            ensureServiceStarted = { events += "start" },
            dispatch = { events += "dispatch:$it" },
        )

        dispatcher.send(ServiceCommand.Play)

        assertEquals(listOf("start", "dispatch:${ServiceCommand.Play}"), events)
    }
}
