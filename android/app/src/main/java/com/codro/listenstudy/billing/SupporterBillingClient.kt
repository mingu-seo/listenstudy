package com.codro.listenstudy.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Application-scoped adapter between Google Play Billing 9 and the unit-tested
 * [SupporterBillingStateMachine]. This class only translates Play callbacks into framework-free
 * events and executes the commands the machine answers with; every grant/acknowledge/restore
 * decision lives in the machine.
 *
 * Lifecycle: one process-wide instance owns one BillingClient connection
 * (`enableAutoServiceReconnection` keeps it alive), so nothing is recreated per recomposition and
 * no Activity is retained — the Activity is only a call parameter of [launchPurchase].
 *
 * Security note: verification is client-only (Play's purchase query is trusted as-is). With no
 * backend there is no server-side signature/receipt validation, so this is not server-grade fraud
 * prevention — an accepted limitation for a goodwill-only product. Purchase tokens, order ids, and
 * account identifiers are never persisted or logged.
 */
class SupporterBillingClient private constructor(
    context: Context,
    private val store: SupporterEntitlementStore,
) : PurchasesUpdatedListener {

    private val machine = SupporterBillingStateMachine(store)

    /** Supporter state for the Settings UI. */
    val uiState: StateFlow<SupporterUiState> = machine.state

    private val mutableSepiaActive = MutableStateFlow(store.isSepiaThemeActive())

    /** Whether the sepia theme should be applied right now (selection backed by entitlement). */
    val sepiaThemeActive: StateFlow<Boolean> = mutableSepiaActive

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    /** Play's ProductDetails paired with the offer token of the SAME selected offer the UI shows. */
    private data class LaunchableProduct(val details: ProductDetails, val offerToken: String)

    /** Kept only so [launchPurchase] can hand Play back its own objects; display data is in state. */
    @Volatile
    private var launchable: LaunchableProduct? = null

    private val connecting = AtomicBoolean(false)

    /** Holds a restore tapped before setup so its user-requested context survives the connection. */
    private val restoreCoordinator = SupporterRestoreCoordinator()

    /** Orders overlapping ownership-query callbacks so a stale snapshot never wins (generation id). */
    private val ownershipQueries = SupporterOwnershipQueryCoordinator()

    /** Single-flight guard: one billing-flow launch at a time, released by callback or sync failure. */
    private val purchaseGate = SupporterPurchaseFlowGate()

    /** Application-scoped; only used to delay bounded acknowledgement retries. Never holds an Activity. */
    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Connects once; safe to call repeatedly. */
    fun connect() {
        if (billingClient.isReady || !connecting.compareAndSet(false, true)) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting.set(false)
                machine.onConnectionResult(result.outcome())
                val setupOk = result.responseCode == BillingClient.BillingResponseCode.OK
                val userRequested = restoreCoordinator.consumePendingRestore(setupOk)
                if (setupOk) {
                    queryProductDetails()
                    queryOwnedPurchases(userRequested = userRequested)
                }
                syncTheme()
            }

            override fun onBillingServiceDisconnected() {
                connecting.set(false)
                // Auto-reconnection is enabled; the machine treats this as a transient network error.
                machine.onDisconnected()
                syncTheme()
            }
        })
    }

    /**
     * App-foreground refresh and the user retry. On a ready client this replays BOTH queries (per
     * [SupporterRefreshPolicy]) so a failed initial `queryProductDetails` is recovered too — an
     * ownership-only refresh would leave the state machine `productKnown = false` (stuck Loading).
     */
    fun refresh() {
        when (SupporterRefreshPolicy.onRefresh(billingClient.isReady)) {
            SupporterRefreshAction.QueryProductAndOwnership -> {
                queryProductDetails()
                queryOwnedPurchases(userRequested = false)
            }
            SupporterRefreshAction.Connect -> connect()
        }
    }

    /**
     * User-requested restore (reinstall/device change). Before the connection is up the request is
     * held and replayed as exactly one `userRequested = true` query right after setup succeeds.
     */
    fun restore() {
        if (restoreCoordinator.onRestoreRequested(billingClient.isReady)) {
            queryOwnedPurchases(userRequested = true)
        } else {
            connect()
        }
    }

    /** Launches the Play purchase sheet from the current foreground Activity. */
    fun launchPurchase(activity: Activity) {
        val product = launchable
        if (!billingClient.isReady || product == null) {
            connect()
            return
        }
        // Single-flight: a double tap while the sheet is opening must not launch a second flow.
        if (!purchaseGate.tryBegin()) return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product.details)
                        // Billing 9: the launched offer must be the SAME one whose price the UI shows.
                        .setOfferToken(product.offerToken)
                        .build(),
                ),
            )
            .build()
        val launch = billingClient.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            // Synchronous launch failures never reach onPurchasesUpdated; report them ourselves.
            purchaseGate.end()
            execute(machine.onPurchasesUpdated(launch.outcome(), emptyList()))
            syncTheme()
        }
    }

    fun setSepiaTheme(enabled: Boolean) {
        store.setSepiaThemeSelected(enabled)
        syncTheme()
    }

    fun clearMessage() = machine.clearMessage()

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        // The flow is over (purchase, cancellation, or error): allow a later deliberate launch.
        purchaseGate.end()
        val records = purchases.orEmpty().map { it.record() }
        // A real purchase delta outdates every ownership snapshot queried before it.
        ownershipQueries.onPurchaseDelta(result.outcome(), deltaHasPurchases = records.isNotEmpty())
        execute(machine.onPurchasesUpdated(result.outcome(), records))
        syncTheme()
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SupporterProduct.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            val details = detailsResult.productDetailsList
                .firstOrNull { it.productId == SupporterProduct.PRODUCT_ID }
            // Billing 9: pick ONE purchasable offer; its price is displayed and its token launched.
            // Without a valid offer the product is not purchasable — pass null so the machine shows
            // item-unavailable instead of a Ready state whose launch would fail or mismatch.
            val offer = SupporterOfferSelector.select(details?.offerCandidates().orEmpty())
            launchable =
                if (details != null && offer != null) LaunchableProduct(details, offer.offerToken)
                else null
            machine.onProductDetails(
                result.outcome(),
                if (details != null && offer != null) {
                    SupporterProductInfo(title = details.name, formattedPrice = offer.formattedPrice)
                } else {
                    null
                },
            )
            syncTheme()
        }
    }

    private fun queryOwnedPurchases(userRequested: Boolean) {
        val ticket = ownershipQueries.begin(userRequested)
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            // Drop stale callbacks: a newer query or a purchase delta has since made this snapshot
            // outdated, and letting it through could overwrite fresher entitlement state.
            if (!ownershipQueries.shouldAccept(ticket)) return@queryPurchasesAsync
            execute(
                machine.onOwnedPurchasesQueried(
                    result.outcome(),
                    purchases.map { it.record() },
                    userRequested = ticket.userRequested,
                ),
            )
            syncTheme()
        }
    }

    private fun execute(commands: List<SupporterBillingStateMachine.Command>) {
        commands.forEach { command ->
            when (command) {
                is SupporterBillingStateMachine.Command.Acknowledge ->
                    acknowledge(command.purchaseToken)
                is SupporterBillingStateMachine.Command.AcknowledgeLater ->
                    retryScope.launch {
                        delay(command.delayMillis)
                        acknowledge(command.purchaseToken)
                    }
                SupporterBillingStateMachine.Command.QueryOwnedPurchases ->
                    queryOwnedPurchases(userRequested = false)
            }
        }
    }

    private fun acknowledge(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            execute(machine.onAcknowledgeResult(purchaseToken, result.outcome()))
        }
    }

    private fun syncTheme() {
        mutableSepiaActive.value = store.isSepiaThemeActive()
    }

    companion object {
        @Volatile
        private var instance: SupporterBillingClient? = null

        fun get(context: Context): SupporterBillingClient =
            instance ?: synchronized(this) {
                instance ?: SupporterBillingClient(
                    context.applicationContext,
                    supporterEntitlementStore(context),
                ).also { instance = it }
            }
    }
}

private fun BillingResult.outcome(): BillingOutcome = when (responseCode) {
    BillingClient.BillingResponseCode.OK -> BillingOutcome.OK
    BillingClient.BillingResponseCode.USER_CANCELED -> BillingOutcome.USER_CANCELED
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingOutcome.ITEM_ALREADY_OWNED
    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingOutcome.ITEM_UNAVAILABLE
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> BillingOutcome.SERVICE_UNAVAILABLE
    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> BillingOutcome.SERVICE_DISCONNECTED
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> BillingOutcome.BILLING_UNAVAILABLE
    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingOutcome.FEATURE_NOT_SUPPORTED
    BillingClient.BillingResponseCode.NETWORK_ERROR -> BillingOutcome.NETWORK_ERROR
    BillingClient.BillingResponseCode.DEVELOPER_ERROR -> BillingOutcome.DEVELOPER_ERROR
    else -> BillingOutcome.ERROR
}

/**
 * Billing 9 offer list mapped into the framework-free selector input. The deprecated singular
 * `oneTimePurchaseOfferDetails` is kept as a fallback for the case where Play populates only the
 * legacy field; it feeds the SAME selector, so the displayed price and the launched offer token
 * still come from one selected offer.
 */
private fun ProductDetails.offerCandidates(): List<SupporterOfferCandidate> {
    val offers = oneTimePurchaseOfferDetailsList?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(oneTimePurchaseOfferDetails)
    return offers.map {
        SupporterOfferCandidate(
            offerToken = it.offerToken.orEmpty(),
            formattedPrice = it.formattedPrice.orEmpty(),
        )
    }
}

private fun Purchase.record() = SupporterPurchaseRecord(
    products = products,
    purchaseState = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PurchaseStateKind.PURCHASED
        Purchase.PurchaseState.PENDING -> PurchaseStateKind.PENDING
        else -> PurchaseStateKind.UNSPECIFIED
    },
    isAcknowledged = isAcknowledged,
    purchaseToken = purchaseToken,
)
