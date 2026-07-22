package com.codro.listenstudy.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full purchase/restore/acknowledge policy, tested on the JVM with a fake store and
 * framework-free events. The Android adapter only translates Play callbacks into these events, so
 * everything Play-dependent stays out of this state machine.
 */
class SupporterBillingStateMachineTest {

    private class FakeStore : SupporterEntitlementStore {
        @JvmField var supporter = false
        @JvmField var sepiaSelected = false
        override fun isSupporter() = supporter
        override fun setSupporter(value: Boolean) { supporter = value }
        override fun isSepiaThemeSelected() = sepiaSelected
        override fun setSepiaThemeSelected(value: Boolean) { sepiaSelected = value }
    }

    private val product = SupporterProductInfo(title = "Supporter", formattedPrice = "₩2,000")

    private fun purchase(
        state: PurchaseStateKind = PurchaseStateKind.PURCHASED,
        acknowledged: Boolean = false,
        token: String = "token-1",
        products: List<String> = listOf(SupporterProduct.PRODUCT_ID),
    ) = SupporterPurchaseRecord(products, state, acknowledged, token)

    /** A machine that has connected and loaded product + empty ownership: ready to purchase. */
    private fun readyMachine(store: FakeStore = FakeStore()): SupporterBillingStateMachine {
        val machine = SupporterBillingStateMachine(store)
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onProductDetails(BillingOutcome.OK, product)
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList())
        return machine
    }

    // --- availability / loading -----------------------------------------------------------------

    @Test
    fun `starts loading when nothing is known`() {
        assertEquals(SupporterState.Loading, SupporterBillingStateMachine(FakeStore()).state.value.state)
    }

    @Test
    fun `starts purchased when the persisted entitlement exists`() {
        val store = FakeStore().apply { supporter = true }
        assertEquals(SupporterState.Purchased, SupporterBillingStateMachine(store).state.value.state)
    }

    @Test
    fun `billing unavailable maps to Unavailable not Error`() {
        val machine = SupporterBillingStateMachine(FakeStore())
        machine.onConnectionResult(BillingOutcome.BILLING_UNAVAILABLE)
        assertEquals(SupporterState.Unavailable, machine.state.value.state)
    }

    @Test
    fun `unsupported play features map to Unavailable`() {
        val machine = SupporterBillingStateMachine(FakeStore())
        machine.onConnectionResult(BillingOutcome.FEATURE_NOT_SUPPORTED)
        assertEquals(SupporterState.Unavailable, machine.state.value.state)
    }

    @Test
    fun `unavailable billing never clears an existing entitlement`() {
        val store = FakeStore().apply { supporter = true }
        val machine = SupporterBillingStateMachine(store)
        machine.onConnectionResult(BillingOutcome.BILLING_UNAVAILABLE)
        assertEquals(SupporterState.Purchased, machine.state.value.state)
        assertTrue(store.supporter)
    }

    @Test
    fun `successful connection alone is still loading until product and ownership arrive`() {
        val machine = SupporterBillingStateMachine(FakeStore())
        machine.onConnectionResult(BillingOutcome.OK)
        assertEquals(SupporterState.Loading, machine.state.value.state)
    }

    @Test
    fun `ready state carries the play-provided localized product info`() {
        val machine = readyMachine()
        assertEquals(SupporterState.ReadyNotPurchased(product), machine.state.value.state)
    }

    @Test
    fun `missing product details map to item unavailable`() {
        val machine = SupporterBillingStateMachine(FakeStore())
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onProductDetails(BillingOutcome.OK, null)
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList())
        assertEquals(SupporterState.Error(SupporterErrorKind.ItemUnavailable), machine.state.value.state)
    }

    // --- purchase flow --------------------------------------------------------------------------

    @Test
    fun `purchased unacknowledged grants entitlement immediately and requests acknowledgement`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        val commands = machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        // Entitlement must not wait for the acknowledgement round-trip.
        assertEquals(SupporterState.Purchased, machine.state.value.state)
        assertTrue(store.supporter)
        assertEquals(listOf(SupporterBillingStateMachine.Command.Acknowledge("token-1")), commands)
        assertEquals(SupporterMessage.PurchaseThanks, machine.state.value.message)
    }

    @Test
    fun `purchased already acknowledged grants entitlement without another acknowledgement`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        val commands = machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase(acknowledged = true)))
        assertEquals(SupporterState.Purchased, machine.state.value.state)
        assertTrue(store.supporter)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `pending purchase never grants entitlement and is never acknowledged`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        val commands = machine.onPurchasesUpdated(
            BillingOutcome.OK,
            listOf(purchase(state = PurchaseStateKind.PENDING)),
        )
        assertEquals(SupporterState.Pending, machine.state.value.state)
        assertFalse(store.supporter)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `pending purchase that later completes grants and acknowledges`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase(state = PurchaseStateKind.PENDING)))
        val commands = machine.onOwnedPurchasesQueried(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        assertEquals(SupporterState.Purchased, machine.state.value.state)
        assertTrue(store.supporter)
        assertEquals(listOf(SupporterBillingStateMachine.Command.Acknowledge("token-1")), commands)
    }

    @Test
    fun `duplicate purchase callbacks with the same token acknowledge only once`() {
        val machine = readyMachine()
        val first = machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        val second = machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        assertEquals(1, first.size)
        assertTrue("duplicate token must not re-acknowledge", second.isEmpty())
        assertEquals(SupporterState.Purchased, machine.state.value.state)
    }

    @Test
    fun `transient acknowledgement failure keeps entitlement and schedules one bounded retry`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        val commands = machine.onAcknowledgeResult("token-1", BillingOutcome.SERVICE_UNAVAILABLE)
        // Entitlement survives the failed acknowledgement — ack is delivery bookkeeping, not the grant.
        assertEquals(SupporterState.Purchased, machine.state.value.state)
        assertTrue(store.supporter)
        assertEquals(
            listOf(
                SupporterBillingStateMachine.Command.AcknowledgeLater(
                    "token-1",
                    SupporterAckRetryPolicy.RETRY_DELAYS_MILLIS[0],
                ),
            ),
            commands,
        )
        // Single-flight: while the retry is scheduled, a concurrent ownership sighting of the same
        // unacknowledged token must not start a second acknowledgement.
        val sighting = machine.onOwnedPurchasesQueried(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        assertTrue("scheduled retry must stay single-flight", sighting.isEmpty())
    }

    @Test
    fun `transient acknowledgement failures back off then fall back to the next sighting`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        val delays = SupporterAckRetryPolicy.RETRY_DELAYS_MILLIS
        delays.forEachIndexed { index, delay ->
            val commands = machine.onAcknowledgeResult("token-1", BillingOutcome.NETWORK_ERROR)
            assertEquals(
                listOf(SupporterBillingStateMachine.Command.AcknowledgeLater("token-1", delay)),
                commands,
            )
            assertTrue("entitlement must survive retry $index", store.supporter)
        }
        // Budget exhausted: no more active retries…
        val exhausted = machine.onAcknowledgeResult("token-1", BillingOutcome.NETWORK_ERROR)
        assertTrue("retry budget must be bounded", exhausted.isEmpty())
        assertTrue(store.supporter)
        // …but the next foreground ownership query re-discovers the token and starts a fresh cycle.
        val sighting = machine.onOwnedPurchasesQueried(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        assertEquals(listOf(SupporterBillingStateMachine.Command.Acknowledge("token-1")), sighting)
        val fresh = machine.onAcknowledgeResult("token-1", BillingOutcome.NETWORK_ERROR)
        assertEquals(
            listOf(SupporterBillingStateMachine.Command.AcknowledgeLater("token-1", delays[0])),
            fresh,
        )
    }

    @Test
    fun `non-retryable acknowledgement failure is not actively retried but the next sighting retries`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        val commands = machine.onAcknowledgeResult("token-1", BillingOutcome.DEVELOPER_ERROR)
        assertTrue("developer errors must not spin an active retry loop", commands.isEmpty())
        assertTrue(store.supporter)
        val sighting = machine.onOwnedPurchasesQueried(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        assertEquals(listOf(SupporterBillingStateMachine.Command.Acknowledge("token-1")), sighting)
    }

    @Test
    fun `acknowledgement success after a scheduled retry ends the cycle`() {
        val machine = readyMachine()
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        machine.onAcknowledgeResult("token-1", BillingOutcome.SERVICE_DISCONNECTED)
        val after = machine.onAcknowledgeResult("token-1", BillingOutcome.OK)
        assertTrue(after.isEmpty())
        val sighting = machine.onOwnedPurchasesQueried(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        assertTrue("an acknowledged token is never re-acknowledged", sighting.isEmpty())
    }

    @Test
    fun `successful acknowledgement is never repeated`() {
        val machine = readyMachine()
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        machine.onAcknowledgeResult("token-1", BillingOutcome.OK)
        val again = machine.onOwnedPurchasesQueried(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        assertTrue(again.isEmpty())
    }

    @Test
    fun `purchases of other products are ignored`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        val commands = machine.onPurchasesUpdated(
            BillingOutcome.OK,
            listOf(purchase(products = listOf("some_other_product"))),
        )
        assertFalse(store.supporter)
        assertTrue(commands.isEmpty())
        assertEquals(SupporterState.ReadyNotPurchased(product), machine.state.value.state)
    }

    // --- cancellation and errors ----------------------------------------------------------------

    @Test
    fun `user cancellation returns to ready and is not an error`() {
        val machine = readyMachine()
        val commands = machine.onPurchasesUpdated(BillingOutcome.USER_CANCELED, emptyList())
        assertEquals(SupporterState.ReadyNotPurchased(product), machine.state.value.state)
        assertEquals(SupporterMessage.PurchaseCancelled, machine.state.value.message)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `already owned triggers an ownership query instead of failing`() {
        val machine = readyMachine()
        val commands = machine.onPurchasesUpdated(BillingOutcome.ITEM_ALREADY_OWNED, emptyList())
        assertEquals(listOf(SupporterBillingStateMachine.Command.QueryOwnedPurchases), commands)
    }

    @Test
    fun `purchase failures map to retryable error kinds`() {
        val machine = readyMachine()
        machine.onPurchasesUpdated(BillingOutcome.NETWORK_ERROR, emptyList())
        assertEquals(SupporterState.Error(SupporterErrorKind.Network), machine.state.value.state)

        machine.onPurchasesUpdated(BillingOutcome.ITEM_UNAVAILABLE, emptyList())
        assertEquals(SupporterState.Error(SupporterErrorKind.ItemUnavailable), machine.state.value.state)

        machine.onPurchasesUpdated(BillingOutcome.ERROR, emptyList())
        assertEquals(SupporterState.Error(SupporterErrorKind.Unknown), machine.state.value.state)
    }

    @Test
    fun `error never clears an existing entitlement`() {
        val store = FakeStore()
        val machine = readyMachine(store)
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase()))
        machine.onPurchasesUpdated(BillingOutcome.ERROR, emptyList())
        assertEquals(SupporterState.Purchased, machine.state.value.state)
        assertTrue(store.supporter)
    }

    @Test
    fun `disconnect keeps entitlement and is otherwise a retryable network error`() {
        val entitled = FakeStore().apply { supporter = true }
        val machineEntitled = SupporterBillingStateMachine(entitled)
        machineEntitled.onDisconnected()
        assertEquals(SupporterState.Purchased, machineEntitled.state.value.state)

        val machineFree = readyMachine()
        machineFree.onDisconnected()
        assertEquals(SupporterState.Error(SupporterErrorKind.Network), machineFree.state.value.state)
    }

    // --- restore --------------------------------------------------------------------------------

    @Test
    fun `reinstall restore path grants from the play ownership query`() {
        val store = FakeStore()
        val machine = SupporterBillingStateMachine(store)
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onProductDetails(BillingOutcome.OK, product)
        val commands = machine.onOwnedPurchasesQueried(
            BillingOutcome.OK,
            listOf(purchase(acknowledged = true, token = "restored-token")),
        )
        assertEquals(SupporterState.Purchased, machine.state.value.state)
        assertTrue(store.supporter)
        assertTrue(commands.isEmpty())
        // Automatic restoration must not celebrate: no popup/nag on every app start.
        assertEquals(null, machine.state.value.message)
    }

    @Test
    fun `user requested restore reports found or not found`() {
        val machine = readyMachine()
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList(), userRequested = true)
        assertEquals(SupporterMessage.NoPurchaseToRestore, machine.state.value.message)

        machine.onOwnedPurchasesQueried(
            BillingOutcome.OK,
            listOf(purchase(acknowledged = true)),
            userRequested = true,
        )
        assertEquals(SupporterMessage.RestoredExisting, machine.state.value.message)
        assertEquals(SupporterState.Purchased, machine.state.value.state)
    }

    @Test
    fun `a successful empty ownership snapshot revokes a stale local entitlement`() {
        val store = FakeStore().apply { supporter = true }
        val machine = SupporterBillingStateMachine(store)
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onProductDetails(BillingOutcome.OK, product)
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList())
        assertFalse("Play says not owned; local grant must not outlive it", store.supporter)
        assertEquals(SupporterState.ReadyNotPurchased(product), machine.state.value.state)
    }

    @Test
    fun `a successful pending-only ownership snapshot revokes a stale local entitlement`() {
        // Spec: only PURCHASED grants entitlement; PENDING never does. If Play's authoritative
        // snapshot succeeds and carries no PURCHASED record, a stale local grant must not survive
        // just because a PENDING record is present.
        val store = FakeStore().apply { supporter = true; sepiaSelected = true }
        val machine = SupporterBillingStateMachine(store)
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onProductDetails(BillingOutcome.OK, product)
        val commands = machine.onOwnedPurchasesQueried(
            BillingOutcome.OK,
            listOf(purchase(state = PurchaseStateKind.PENDING)),
        )
        assertFalse("PENDING must never sustain the entitlement", store.supporter)
        assertFalse("sepia must lose its backing entitlement", store.isSepiaThemeActive())
        assertEquals(SupporterState.Pending, machine.state.value.state)
        assertTrue("PENDING is never acknowledged", commands.isEmpty())
    }

    @Test
    fun `a failed ownership query never revokes the entitlement`() {
        val store = FakeStore().apply { supporter = true }
        val machine = SupporterBillingStateMachine(store)
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onOwnedPurchasesQueried(BillingOutcome.NETWORK_ERROR, emptyList())
        assertTrue("offline must not strip the supporter", store.supporter)
        assertEquals(SupporterState.Purchased, machine.state.value.state)
    }

    // --- refresh / retry recovery ---------------------------------------------------------------

    @Test
    fun `a retry replaying both queries recovers from an initial product details failure`() {
        // First round: product details fail, ownership succeeds. `productKnown` stays false, so an
        // ownership-only refresh could never leave Loading — the retry must replay both queries.
        val machine = SupporterBillingStateMachine(FakeStore())
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onProductDetails(BillingOutcome.NETWORK_ERROR, null)
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList())
        // User taps retry -> refresh replays product details AND ownership (per SupporterRefreshPolicy).
        machine.onProductDetails(BillingOutcome.OK, product)
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList())
        assertEquals(SupporterState.ReadyNotPurchased(product), machine.state.value.state)
    }

    @Test
    fun `a retry recovers regardless of which replayed query answers first`() {
        // Play answers the two async queries in either order; both interleavings must converge.
        val machine = SupporterBillingStateMachine(FakeStore())
        machine.onConnectionResult(BillingOutcome.OK)
        machine.onProductDetails(BillingOutcome.NETWORK_ERROR, null)
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList())
        // Retry: ownership answers before product details this time.
        machine.onOwnedPurchasesQueried(BillingOutcome.OK, emptyList())
        machine.onProductDetails(BillingOutcome.OK, product)
        assertEquals(SupporterState.ReadyNotPurchased(product), machine.state.value.state)
    }

    @Test
    fun `duplicate refresh callbacks keep the entitlement and never re-acknowledge`() {
        // Foreground refresh and a user retry can overlap, delivering duplicate product-details and
        // ownership callbacks. The entitlement must survive and no token is acknowledged twice.
        val store = FakeStore()
        val machine = readyMachine(store)
        machine.onPurchasesUpdated(BillingOutcome.OK, listOf(purchase(acknowledged = false)))
        machine.onAcknowledgeResult("token-1", BillingOutcome.OK)
        repeat(2) {
            machine.onProductDetails(BillingOutcome.OK, product)
            val commands = machine.onOwnedPurchasesQueried(
                BillingOutcome.OK,
                listOf(purchase(acknowledged = false)),
            )
            assertTrue("refresh must not re-acknowledge an acknowledged token", commands.isEmpty())
            assertTrue("entitlement must survive duplicate callbacks", store.supporter)
        }
        assertEquals(SupporterState.Purchased, machine.state.value.state)
    }

    // --- messages -------------------------------------------------------------------------------

    @Test
    fun `messages can be dismissed without changing the state`() {
        val machine = readyMachine()
        machine.onPurchasesUpdated(BillingOutcome.USER_CANCELED, emptyList())
        machine.clearMessage()
        assertEquals(null, machine.state.value.message)
        assertEquals(SupporterState.ReadyNotPurchased(product), machine.state.value.state)
    }
}
