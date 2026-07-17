package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackPersistenceRequest
import com.codro.listenstudy.domain.player.PlaybackStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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

/** Concrete lifecycle transition the service must perform when the playback status changes. */
enum class ServiceLifecycleAction {
    /** Promote to a foreground service (active playback or a running preview). */
    StartForeground,

    /**
     * Release foreground mode but keep the service alive, re-syncing its sticky start mode so a
     * paused session can resume. Safe because pause is a user action, not a background completion.
     */
    ReleaseForegroundAndSync,

    /**
     * Release foreground mode and terminate the service. Used when the final sentence completes so
     * the service is never re-started in the background (Android background-start restriction).
     */
    StopService,
}

/** How a foreground release should treat the ongoing notification. */
enum class ForegroundDetachMode {
    /** Keep the notification (paused/background) — maps to STOP_FOREGROUND_DETACH. */
    Detach,

    /** Remove the notification (terminal completion) — maps to STOP_FOREGROUND_REMOVE. */
    Remove,
}

object PlaybackServiceLifecyclePolicy {
    fun decide(status: PlaybackStatus, previewActive: Boolean): ServiceLifecycleDecision {
        val active = status == PlaybackStatus.Playing || previewActive
        return ServiceLifecycleDecision(foreground = active, sticky = active)
    }

    fun action(status: PlaybackStatus, previewActive: Boolean): ServiceLifecycleAction = when {
        decide(status, previewActive).foreground -> ServiceLifecycleAction.StartForeground
        status == PlaybackStatus.Completed -> ServiceLifecycleAction.StopService
        else -> ServiceLifecycleAction.ReleaseForegroundAndSync
    }

    /**
     * Notification treatment when releasing foreground mode: the terminal stop must REMOVE the stale
     * notification, while a non-terminal release (pause/background) only DETACHes it. Foreground
     * promotion releases nothing, so it returns null.
     */
    fun foregroundRelease(action: ServiceLifecycleAction): ForegroundDetachMode? = when (action) {
        ServiceLifecycleAction.StartForeground -> null
        ServiceLifecycleAction.ReleaseForegroundAndSync -> ForegroundDetachMode.Detach
        ServiceLifecycleAction.StopService -> ForegroundDetachMode.Remove
    }
}

/**
 * Deduplicates lifecycle transitions without ever swallowing the terminal stop.
 *
 * Paused, Idle and Completed all map to `ServiceLifecycleDecision(false, false)`, so a plain
 * decision-equality guard would treat a Paused -> Completed transition as a no-op and never stop the
 * service. This gate keys non-terminal deduplication on the decision but always evaluates the
 * terminal stop, emitting it exactly once so repeated completion callbacks stay idempotent.
 */
class ServiceLifecycleGate {
    // The service always starts not-foreground, so the initial Idle/Paused emission must be a no-op
    // rather than a redundant stopForeground + startService(SYNC).
    private var applied: ServiceLifecycleDecision? = ServiceLifecycleDecision(foreground = false, sticky = false)
    private var stopped = false

    /** Returns the action to perform, or null when it duplicates the last state or the service is stopping. */
    fun next(status: PlaybackStatus, previewActive: Boolean): ServiceLifecycleAction? {
        // Once the terminal stop has been emitted the service is being destroyed; every later
        // transition (including stale completion callbacks) must be inert until reset() re-enables it.
        if (stopped) return null
        val action = PlaybackServiceLifecyclePolicy.action(status, previewActive)
        if (action == ServiceLifecycleAction.StopService) {
            stopped = true
            return action
        }
        val decision = PlaybackServiceLifecyclePolicy.decide(status, previewActive)
        if (decision == applied) return null
        applied = decision
        return action
    }

    /** Re-enables the gate after a terminal stop when newer work revives the session. */
    fun reset() {
        if (!stopped) return
        stopped = false
        applied = ServiceLifecycleDecision(foreground = false, sticky = false)
    }
}

/**
 * Sequences the terminal completion path: persist the final snapshot, then stop — exactly once.
 *
 * Two-phase so the generation this finalization belongs to is captured SYNCHRONOUSLY at completion
 * detection ([begin], on the service main thread), before the suspending [finalize] continuation
 * runs. Any revival accepted afterwards calls [invalidate], which bumps the generation so the stale
 * finalizer skips `stop` and never terminates the newer session — regardless of how the barrier and
 * command coroutines interleave. Ordinary persistence failures are handled safely (the service always
 * terminates rather than lingering), but cancellation is rethrown, never swallowed.
 */
class TerminalCompletionCoordinator(
    private val persist: suspend (PlaybackPersistenceRequest) -> Unit,
    private val stop: () -> Unit,
) {
    private var generation = 0L
    private var finalizing = false

    /** Marks any in-flight finalization stale; the next [begin] belongs to the newer session. */
    fun invalidate() {
        generation++
        finalizing = false
    }

    /** Claims terminal finalization synchronously; returns its generation token, or null if one is already active. */
    fun begin(): Long? {
        if (finalizing) return null
        finalizing = true
        return generation
    }

    suspend fun finalize(token: Long, snapshot: PlaybackPersistenceRequest?) {
        if (snapshot != null) {
            try {
                persist(snapshot)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Ordinary persistence failure: never block termination on a save error.
            }
        }
        // Newer work bumped the generation after this finalization began -> do not stop it.
        if (token == generation) stop()
    }
}

/** Gates the debounced position producer so no delayed save enqueues after terminal finalization begins. */
class PersistenceProducerGate {
    private var terminating = false

    /** Producer writes accepted only while no terminal finalization is in progress. */
    fun accept(): Boolean = !terminating

    fun beginTerminal() {
        terminating = true
    }

    /** Reopens the producer when newer work revives the session. */
    fun revive() {
        terminating = false
    }
}

/** Thrown to reject [PlaybackPersistenceWriter.submit]/[PlaybackPersistenceWriter.barrier] once the writer is closed. */
class PlaybackPersistenceWriterClosedException(cause: Throwable? = null) :
    IllegalStateException("PlaybackPersistenceWriter is closed", cause)

/**
 * Single-consumer FIFO writer for playback position saves. Normal saves are fire-and-forget
 * [submit]s (returning false if the writer is closed — never a silent drop); the terminal completion
 * path uses [barrier], which resumes only after its request — and therefore every earlier queued
 * save — has been written, so the final snapshot is committed last and no stale write can follow it.
 *
 * Ordinary write failures are handled safely so draining continues. A CancellationException from a
 * write shuts the writer down cleanly: the in-flight and every queued/future barrier is completed
 * exceptionally (never left to hang), further submit/barrier calls are rejected, and [onDrained] runs
 * exactly once.
 */
class PlaybackPersistenceWriter(
    scope: CoroutineScope,
    private val write: suspend (PlaybackPersistenceRequest) -> Unit,
    private val onSaved: (PlaybackPersistenceRequest) -> Unit = {},
    private val onDrained: () -> Unit = {},
) {
    private data class Task(
        val request: PlaybackPersistenceRequest,
        val ack: CompletableDeferred<Unit>?,
    )

    private val tasks = Channel<Task>(Channel.UNLIMITED)

    @Volatile
    private var closedCause: Throwable? = null
    private var drained = false

    init {
        scope.launch {
            var cause: Throwable? = null
            try {
                for (task in tasks) {
                    try {
                        write(task.request)
                        onSaved(task.request)
                        task.ack?.complete(Unit)
                    } catch (cancellation: CancellationException) {
                        cause = cancellation
                        task.ack?.completeExceptionally(cancellation)
                        throw cancellation
                    } catch (_: Throwable) {
                        // Ordinary persistence failure: keep draining the queue.
                        task.ack?.complete(Unit)
                    }
                }
            } finally {
                shutdown(cause ?: PlaybackPersistenceWriterClosedException())
            }
        }
    }

    private fun shutdown(cause: Throwable) {
        if (closedCause != null) return // exactly-once
        closedCause = cause
        tasks.close(cause)
        while (true) {
            val task = tasks.tryReceive().getOrNull() ?: break
            task.ack?.completeExceptionally(cause)
        }
        if (!drained) {
            drained = true
            onDrained()
        }
    }

    /** Returns false when the writer is closed instead of silently dropping the save. */
    fun submit(request: PlaybackPersistenceRequest): Boolean {
        if (closedCause != null) return false
        return tasks.trySend(Task(request, null)).isSuccess
    }

    /** Suspends until [request] — and therefore every earlier queued save — has been written. */
    suspend fun barrier(request: PlaybackPersistenceRequest) {
        closedCause?.let { throw it }
        val ack = CompletableDeferred<Unit>()
        val sent = tasks.trySend(Task(request, ack))
        if (sent.isFailure) throw (closedCause ?: PlaybackPersistenceWriterClosedException())
        ack.await()
    }

    fun close() {
        tasks.close()
    }
}

/** Keeps error behavior identical for local and cloud playback while preserving engine status text. */
class PlaybackErrorCoordinator(private val onPlaybackError: () -> Unit) {
    fun onError(@Suppress("UNUSED_PARAMETER") message: String) {
        onPlaybackError()
    }
}
