package com.codro.listenstudy.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A user-requested restore tapped before the Billing connection is up must not degrade into a
 * plain refresh: the request is held and replayed as exactly one `userRequested = true` ownership
 * query right after billing setup succeeds. Duplicate taps coalesce; connection-time/foreground
 * refreshes stay `userRequested = false`.
 */
class SupporterRestoreCoordinatorTest {

    @Test
    fun `restore while connected queries immediately as user requested`() {
        val coordinator = SupporterRestoreCoordinator()
        assertTrue(coordinator.onRestoreRequested(clientReady = true))
        // Nothing was deferred, so a later setup completion is a plain refresh.
        assertFalse(coordinator.consumePendingRestore(setupOk = true))
    }

    @Test
    fun `restore before connection defers and replays as user requested exactly once`() {
        val coordinator = SupporterRestoreCoordinator()
        assertFalse("must not query before setup", coordinator.onRestoreRequested(clientReady = false))
        assertTrue("held restore must replay as user-requested", coordinator.consumePendingRestore(setupOk = true))
        assertFalse("must replay only once", coordinator.consumePendingRestore(setupOk = true))
    }

    @Test
    fun `duplicate restore taps before connection coalesce into one user requested query`() {
        val coordinator = SupporterRestoreCoordinator()
        assertFalse(coordinator.onRestoreRequested(clientReady = false))
        assertFalse(coordinator.onRestoreRequested(clientReady = false))
        assertFalse(coordinator.onRestoreRequested(clientReady = false))
        val replays = (1..3).count { coordinator.consumePendingRestore(setupOk = true) }
        assertEquals("three taps must collapse into one user-requested restore", 1, replays)
    }

    @Test
    fun `failed setup clears the held restore instead of firing it on a later reconnect`() {
        val coordinator = SupporterRestoreCoordinator()
        assertFalse(coordinator.onRestoreRequested(clientReady = false))
        // Setup failed: the restore attempt ends with this connection attempt. A much later
        // automatic reconnect must not surprise the user with a stale restore result popup.
        assertFalse(coordinator.consumePendingRestore(setupOk = false))
        assertFalse(coordinator.consumePendingRestore(setupOk = true))
    }

    @Test
    fun `plain connect or refresh never becomes user requested`() {
        val coordinator = SupporterRestoreCoordinator()
        // No restore tap at all: every setup completion stays a non-user-requested refresh.
        assertFalse(coordinator.consumePendingRestore(setupOk = true))
        assertFalse(coordinator.consumePendingRestore(setupOk = true))
    }
}
