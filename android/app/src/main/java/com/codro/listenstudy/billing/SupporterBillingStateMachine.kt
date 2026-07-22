package com.codro.listenstudy.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Framework-free mirror of a Play `Purchase` — only the fields the policy needs. */
data class SupporterPurchaseRecord(
    val products: List<String>,
    val purchaseState: PurchaseStateKind,
    val isAcknowledged: Boolean,
    val purchaseToken: String,
)

/** Mirror of `Purchase.PurchaseState`. */
enum class PurchaseStateKind { PURCHASED, PENDING, UNSPECIFIED }

/** Mirror of the `BillingResponseCode`s the policy distinguishes. */
enum class BillingOutcome {
    OK,
    USER_CANCELED,
    ITEM_ALREADY_OWNED,
    ITEM_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    SERVICE_DISCONNECTED,
    BILLING_UNAVAILABLE,
    FEATURE_NOT_SUPPORTED,
    NETWORK_ERROR,
    DEVELOPER_ERROR,
    ERROR,
}

/** One-shot, dismissible UI notices. Kept as an enum so the copy stays in strings.xml. */
enum class SupporterMessage {
    /** Purchase-complete thank-you. */
    PurchaseThanks,

    /** The user backed out; informational, never styled as a failure. */
    PurchaseCancelled,

    /** A user-requested restore found the purchase. */
    RestoredExisting,

    /** A user-requested restore found nothing to restore. */
    NoPurchaseToRestore,
}

data class SupporterUiState(
    val state: SupporterState,
    val message: SupporterMessage? = null,
)

/**
 * Deterministic supporter billing policy. The Android adapter feeds Play callbacks in as events;
 * this machine owns the state, the entitlement grant/revoke rules, and the acknowledgement
 * bookkeeping, and answers with [Command]s for the adapter to execute.
 *
 * Invariants:
 *  - Entitlement is granted only for [PurchaseStateKind.PURCHASED]; PENDING grants nothing.
 *  - The grant never waits for the acknowledgement round-trip.
 *  - Acknowledgement is requested exactly once per token until it succeeds; never for PENDING.
 *  - Failures, cancellation, offline, and unsupported Play never clear an existing entitlement;
 *    only a *successful* ownership snapshot that lacks a PURCHASED record revokes it (a snapshot
 *    with only PENDING revokes too — pending is shown, but grants nothing).
 *
 * All methods are synchronized: Play invokes listener callbacks on its own threads.
 */
class SupporterBillingStateMachine(private val store: SupporterEntitlementStore) {

    sealed interface Command {
        data class Acknowledge(val purchaseToken: String) : Command

        /** Retry the acknowledgement of [purchaseToken] after [delayMillis] (bounded backoff). */
        data class AcknowledgeLater(val purchaseToken: String, val delayMillis: Long) : Command
        data object QueryOwnedPurchases : Command
    }

    private val mutableState = MutableStateFlow(SupporterUiState(resolveInitialState()))
    val state: StateFlow<SupporterUiState> = mutableState

    /** Tokens with an acknowledgement in flight or already confirmed by Play. */
    private val acknowledgedOrRequested = mutableSetOf<String>()

    /** Active retries already spent per token (see [SupporterAckRetryPolicy]). */
    private val ackRetriesSpent = mutableMapOf<String, Int>()

    private var product: SupporterProductInfo? = null
    private var productKnown = false
    private var ownershipKnown = false
    private var pendingSeen = false
    private var unavailable = false
    private var error: SupporterErrorKind? = null

    private fun resolveInitialState(): SupporterState =
        if (store.isSupporter()) SupporterState.Purchased else SupporterState.Loading

    @Synchronized
    fun onConnectionResult(outcome: BillingOutcome) {
        when (outcome) {
            BillingOutcome.OK -> {
                unavailable = false
                error = null
            }
            BillingOutcome.BILLING_UNAVAILABLE, BillingOutcome.FEATURE_NOT_SUPPORTED ->
                unavailable = true
            else -> error = errorKind(outcome)
        }
        publish()
    }

    @Synchronized
    fun onDisconnected() {
        error = SupporterErrorKind.Network
        publish()
    }

    @Synchronized
    fun onProductDetails(outcome: BillingOutcome, product: SupporterProductInfo?) {
        if (outcome == BillingOutcome.OK) {
            this.product = product
            productKnown = true
        } else {
            error = errorKind(outcome)
        }
        publish()
    }

    /**
     * Result of the purchase flow (`onPurchasesUpdated`). This is a delta, not an ownership
     * snapshot, so an empty list never revokes anything.
     */
    @Synchronized
    fun onPurchasesUpdated(
        outcome: BillingOutcome,
        purchases: List<SupporterPurchaseRecord>,
    ): List<Command> = when (outcome) {
        BillingOutcome.OK -> processPurchases(purchases, grantMessage = SupporterMessage.PurchaseThanks)
        BillingOutcome.USER_CANCELED -> {
            // Backing out of the sheet is a normal choice, not a failure.
            publish(message = SupporterMessage.PurchaseCancelled)
            emptyList()
        }
        BillingOutcome.ITEM_ALREADY_OWNED -> {
            // Owned on this account already — restore instead of erroring.
            publish()
            listOf(Command.QueryOwnedPurchases)
        }
        else -> {
            error = errorKind(outcome)
            publish()
            emptyList()
        }
    }

    /**
     * Result of `queryPurchasesAsync(INAPP)`: Play's authoritative ownership snapshot, used for
     * connection-time/foreground refresh and reinstall/device-change restoration. Only a successful
     * snapshot may revoke a stale local entitlement.
     */
    @Synchronized
    fun onOwnedPurchasesQueried(
        outcome: BillingOutcome,
        purchases: List<SupporterPurchaseRecord>,
        userRequested: Boolean = false,
    ): List<Command> {
        if (outcome != BillingOutcome.OK) {
            error = errorKind(outcome)
            publish()
            return emptyList()
        }
        ownershipKnown = true
        error = null
        val owned = purchases.filter { SupporterProduct.PRODUCT_ID in it.products }
        pendingSeen = owned.any { it.purchaseState == PurchaseStateKind.PENDING }
        val hasPurchased = owned.any { it.purchaseState == PurchaseStateKind.PURCHASED }
        if (!hasPurchased && store.isSupporter()) {
            // Play's authoritative snapshot has no PURCHASED record (refund/revoke, or only a
            // not-yet-completed PENDING): drop the local grant. PENDING never sustains entitlement.
            store.setSupporter(false)
        }
        val message = when {
            !userRequested -> null
            hasPurchased -> SupporterMessage.RestoredExisting
            else -> SupporterMessage.NoPurchaseToRestore
        }
        return processPurchases(owned, grantMessage = message, alreadyFiltered = true)
    }

    /**
     * Acknowledgement round-trip finished. Entitlement is untouched in every branch: ack is
     * delivery bookkeeping, not the grant itself. Transient failures earn a bounded active retry
     * ([Command.AcknowledgeLater]); while one is scheduled the token stays in
     * [acknowledgedOrRequested], so overlapping ownership sightings never start a second
     * acknowledgement (single-flight). Non-retryable failures and an exhausted budget free the
     * token instead, so the next ownership sighting retries passively.
     */
    @Synchronized
    fun onAcknowledgeResult(purchaseToken: String, outcome: BillingOutcome): List<Command> {
        if (outcome == BillingOutcome.OK) {
            ackRetriesSpent.remove(purchaseToken)
            return emptyList()
        }
        val retriesSoFar = ackRetriesSpent[purchaseToken] ?: 0
        val delayMillis = SupporterAckRetryPolicy.nextDelayMillis(outcome, retriesSoFar)
        return if (delayMillis != null) {
            ackRetriesSpent[purchaseToken] = retriesSoFar + 1
            listOf(Command.AcknowledgeLater(purchaseToken, delayMillis))
        } else {
            ackRetriesSpent.remove(purchaseToken)
            acknowledgedOrRequested.remove(purchaseToken)
            emptyList()
        }
    }

    @Synchronized
    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    private fun processPurchases(
        purchases: List<SupporterPurchaseRecord>,
        grantMessage: SupporterMessage?,
        alreadyFiltered: Boolean = false,
    ): List<Command> {
        val relevant =
            if (alreadyFiltered) purchases
            else purchases.filter { SupporterProduct.PRODUCT_ID in it.products }
        val purchased = relevant.filter { it.purchaseState == PurchaseStateKind.PURCHASED }
        if (relevant.any { it.purchaseState == PurchaseStateKind.PENDING }) pendingSeen = true
        val commands = mutableListOf<Command>()
        var newlyGranted = false
        purchased.forEach { record ->
            pendingSeen = false
            error = null
            if (!store.isSupporter()) {
                store.setSupporter(true)
                newlyGranted = true
            }
            if (!record.isAcknowledged && acknowledgedOrRequested.add(record.purchaseToken)) {
                commands += Command.Acknowledge(record.purchaseToken)
            }
        }
        publish(message = grantMessage.takeIf { newlyGranted || grantMessage != SupporterMessage.PurchaseThanks })
        return commands
    }

    private fun errorKind(outcome: BillingOutcome): SupporterErrorKind = when (outcome) {
        BillingOutcome.BILLING_UNAVAILABLE, BillingOutcome.FEATURE_NOT_SUPPORTED ->
            SupporterErrorKind.BillingUnavailable
        BillingOutcome.ITEM_UNAVAILABLE -> SupporterErrorKind.ItemUnavailable
        BillingOutcome.NETWORK_ERROR,
        BillingOutcome.SERVICE_UNAVAILABLE,
        BillingOutcome.SERVICE_DISCONNECTED -> SupporterErrorKind.Network
        else -> SupporterErrorKind.Unknown
    }

    /** Deterministic priority: entitlement > pending > unavailable > error > loading > ready. */
    private fun publish(message: SupporterMessage? = mutableState.value.message) {
        val state = when {
            store.isSupporter() -> SupporterState.Purchased
            pendingSeen -> SupporterState.Pending
            unavailable -> SupporterState.Unavailable
            error != null -> SupporterState.Error(error!!)
            !productKnown || !ownershipKnown -> SupporterState.Loading
            // Play answered but without our product: it is not purchasable right now.
            product == null -> SupporterState.Error(SupporterErrorKind.ItemUnavailable)
            else -> SupporterState.ReadyNotPurchased(product)
        }
        mutableState.value = SupporterUiState(state, message)
    }
}
