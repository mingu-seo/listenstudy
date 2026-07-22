package com.codro.listenstudy.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A user retry ("다시 시도") and the app-foreground refresh both funnel into `refresh()`. When the
 * client is ready, a refresh must re-query BOTH product details and the ownership snapshot: after an
 * initial `queryProductDetails` failure the state machine stays `productKnown = false`, so an
 * ownership-only refresh could never leave Loading and the retry button would be a no-op. Before the
 * connection is up, refresh stays a plain connect.
 */
class SupporterRefreshPolicyTest {

    @Test
    fun `refresh on a ready client re-queries product details and ownership together`() {
        assertEquals(
            SupporterRefreshAction.QueryProductAndOwnership,
            SupporterRefreshPolicy.onRefresh(clientReady = true),
        )
    }

    @Test
    fun `refresh before the connection is up connects instead of querying`() {
        assertEquals(
            SupporterRefreshAction.Connect,
            SupporterRefreshPolicy.onRefresh(clientReady = false),
        )
    }
}
