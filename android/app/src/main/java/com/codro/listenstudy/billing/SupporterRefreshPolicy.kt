package com.codro.listenstudy.billing

/** What a refresh/retry must do, decided by [SupporterRefreshPolicy]. */
enum class SupporterRefreshAction {
    /** Client is ready: re-query product details AND the ownership snapshot together. */
    QueryProductAndOwnership,

    /** No connection yet: (re)connect; the connection callback runs both queries itself. */
    Connect,
}

/**
 * Framework-free policy for the user retry ("다시 시도") and the app-foreground refresh, which both
 * funnel into `SupporterBillingClient.refresh()`. On a ready client a refresh must replay BOTH
 * queries: after an initial `queryProductDetails` failure the state machine stays
 * `productKnown = false`, so an ownership-only refresh could never leave Loading and the retry
 * button would silently do nothing. Replaying both is safe — the state machine tolerates duplicate
 * product/ownership callbacks and never re-acknowledges or drops the entitlement for them.
 */
object SupporterRefreshPolicy {

    fun onRefresh(clientReady: Boolean): SupporterRefreshAction =
        if (clientReady) SupporterRefreshAction.QueryProductAndOwnership
        else SupporterRefreshAction.Connect
}
