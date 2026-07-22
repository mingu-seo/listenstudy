package com.codro.listenstudy.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression seam for the ownership-query race: overlapping `queryPurchasesAsync()` callbacks and
 * purchase deltas must never let a stale authoritative snapshot overwrite newer entitlement state.
 * Only the callback of the NEWEST generation may reach the state machine.
 */
class SupporterOwnershipQueryCoordinatorTest {

    private val coordinator = SupporterOwnershipQueryCoordinator()

    @Test
    fun `a single query is accepted`() {
        val ticket = coordinator.begin(userRequested = false)
        assertTrue(coordinator.shouldAccept(ticket))
    }

    @Test
    fun `an old empty snapshot arriving after a purchase delta is ignored`() {
        // Query starts (snapshot will say "not owned"), then the purchase completes. The stale
        // empty snapshot must not be allowed to revoke the fresh grant.
        val stale = coordinator.begin(userRequested = false)
        coordinator.onPurchaseDelta(BillingOutcome.OK, deltaHasPurchases = true)
        assertFalse(coordinator.shouldAccept(stale))
    }

    @Test
    fun `an old purchased snapshot arriving after a newer empty query is ignored`() {
        // Refund/revoke case: the older "purchased" snapshot answers late, after a newer query was
        // started. Only the newest query's callback may win, regardless of arrival order.
        val old = coordinator.begin(userRequested = false)
        val newest = coordinator.begin(userRequested = false)
        assertFalse(coordinator.shouldAccept(old))
        assertTrue(coordinator.shouldAccept(newest))
        // shouldAccept does not mutate: checking in the opposite order gives the same answers.
        assertTrue(coordinator.shouldAccept(newest))
        assertFalse(coordinator.shouldAccept(old))
    }

    @Test
    fun `overlapping restore and refresh accept only the newest and keep its message context`() {
        val refresh = coordinator.begin(userRequested = false)
        val restore = coordinator.begin(userRequested = true)
        assertFalse(coordinator.shouldAccept(refresh))
        assertTrue(coordinator.shouldAccept(restore))
        assertTrue("restore context must survive on the newest ticket", restore.userRequested)
    }

    @Test
    fun `overlapping refresh after restore accepts only the newest plain refresh`() {
        val restore = coordinator.begin(userRequested = true)
        val refresh = coordinator.begin(userRequested = false)
        assertFalse(coordinator.shouldAccept(restore))
        assertTrue(coordinator.shouldAccept(refresh))
        assertFalse("a plain refresh must never inherit restore messaging", refresh.userRequested)
    }

    @Test
    fun `a cancelled or failed purchase delta does not invalidate the in-flight query`() {
        // No purchase state changed, so the running snapshot is still valid.
        val ticket = coordinator.begin(userRequested = false)
        coordinator.onPurchaseDelta(BillingOutcome.USER_CANCELED, deltaHasPurchases = false)
        coordinator.onPurchaseDelta(BillingOutcome.ERROR, deltaHasPurchases = false)
        assertTrue(coordinator.shouldAccept(ticket))
    }

    @Test
    fun `an OK delta without purchases does not invalidate the in-flight query`() {
        val ticket = coordinator.begin(userRequested = false)
        coordinator.onPurchaseDelta(BillingOutcome.OK, deltaHasPurchases = false)
        assertTrue(coordinator.shouldAccept(ticket))
    }

    @Test
    fun `a query started after the purchase delta is accepted`() {
        coordinator.begin(userRequested = false)
        coordinator.onPurchaseDelta(BillingOutcome.OK, deltaHasPurchases = true)
        val fresh = coordinator.begin(userRequested = false)
        assertTrue(coordinator.shouldAccept(fresh))
    }
}
