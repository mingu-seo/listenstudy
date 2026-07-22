package com.codro.listenstudy.billing

/**
 * Framework-free decision seam for the user-requested restore context. A restore tapped before the
 * BillingClient connection is ready cannot query yet; this coordinator holds that intent so the
 * connection-time ownership query fires as `userRequested = true` exactly once — instead of the
 * request silently degrading into a plain refresh. Duplicate taps coalesce into one held restore;
 * plain connect/refresh paths never become user-requested.
 *
 * Synchronized: restore taps arrive on the main thread while Play delivers setup callbacks on its
 * own threads.
 */
class SupporterRestoreCoordinator {

    private var pendingUserRestore = false

    /**
     * A restore was requested. Returns true when the client is ready and the caller should query
     * ownership as user-requested right now; otherwise the intent is held for the next successful
     * setup and the caller should (re)connect.
     */
    @Synchronized
    fun onRestoreRequested(clientReady: Boolean): Boolean {
        if (clientReady) return true
        pendingUserRestore = true
        return false
    }

    /**
     * Billing setup finished. Returns whether the connection-time ownership query must carry
     * `userRequested = true`. The held intent is consumed either way: on success it is replayed
     * exactly once; on failure the restore attempt ends with this connection attempt, so a much
     * later automatic reconnect never fires a stale restore popup.
     */
    @Synchronized
    fun consumePendingRestore(setupOk: Boolean): Boolean {
        val wasPending = pendingUserRestore
        pendingUserRestore = false
        return setupOk && wasPending
    }
}
