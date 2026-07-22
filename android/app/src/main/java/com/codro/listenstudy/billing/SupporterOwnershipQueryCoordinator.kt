package com.codro.listenstudy.billing

/**
 * Framework-free ordering guard for `queryPurchasesAsync()` callbacks. Ownership snapshots are
 * authoritative (they may revoke), so a stale one answering late must never overwrite newer
 * entitlement state. Every query [begin]s a new generation; only the newest generation's callback
 * [shouldAccept]s, and a real purchase delta ([onPurchaseDelta]) invalidates every query that was
 * already in flight — its snapshot predates the purchase.
 *
 * Synchronized: queries start on the main thread while Play delivers results and purchase deltas
 * on its own threads.
 */
class SupporterOwnershipQueryCoordinator {

    /** Identity of one started query; carries the user-requested restore context with it. */
    data class Ticket(val generation: Long, val userRequested: Boolean)

    private var latestGeneration = 0L

    /** A new ownership query is starting; every older in-flight query becomes stale. */
    @Synchronized
    fun begin(userRequested: Boolean): Ticket = Ticket(++latestGeneration, userRequested)

    /** Whether this query's callback is still the newest word and may reach the state machine. */
    @Synchronized
    fun shouldAccept(ticket: Ticket): Boolean = ticket.generation == latestGeneration

    /**
     * `onPurchasesUpdated` delivered an actual purchase delta: snapshots queried before it are
     * stale (they can't contain the new purchase) and must be dropped. Cancellations, failures,
     * and empty deltas change no purchase state, so in-flight snapshots stay valid.
     */
    @Synchronized
    fun onPurchaseDelta(outcome: BillingOutcome, deltaHasPurchases: Boolean) {
        if (outcome == BillingOutcome.OK && deltaHasPurchases) latestGeneration++
    }
}
