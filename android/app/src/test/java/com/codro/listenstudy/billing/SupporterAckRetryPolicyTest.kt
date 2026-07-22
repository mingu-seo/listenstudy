package com.codro.listenstudy.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bounded active retry for failed acknowledgements. Transient failures get a short constant
 * backoff sequence; non-retryable failures and exhausted budgets fall back to the passive path
 * (the next foreground ownership query re-discovers the unacknowledged purchase).
 */
class SupporterAckRetryPolicyTest {

    @Test
    fun `transient failures follow the bounded backoff sequence then stop`() {
        val transients = listOf(
            BillingOutcome.SERVICE_DISCONNECTED,
            BillingOutcome.SERVICE_UNAVAILABLE,
            BillingOutcome.NETWORK_ERROR,
            BillingOutcome.ERROR,
        )
        transients.forEach { outcome ->
            SupporterAckRetryPolicy.RETRY_DELAYS_MILLIS.forEachIndexed { attempt, delay ->
                assertEquals(delay, SupporterAckRetryPolicy.nextDelayMillis(outcome, attempt))
            }
            assertNull(
                "retry budget must be bounded for $outcome",
                SupporterAckRetryPolicy.nextDelayMillis(
                    outcome,
                    SupporterAckRetryPolicy.RETRY_DELAYS_MILLIS.size,
                ),
            )
        }
    }

    @Test
    fun `the backoff sequence is short, increasing, and exactly three attempts`() {
        val delays = SupporterAckRetryPolicy.RETRY_DELAYS_MILLIS
        assertEquals(3, delays.size)
        assertEquals(delays, delays.sorted())
        // "Short and reasonable": the whole active budget stays under a minute.
        assertEquals(true, delays.sum() < 60_000L)
    }

    @Test
    fun `non-retryable failures are never actively retried`() {
        listOf(
            BillingOutcome.DEVELOPER_ERROR,
            BillingOutcome.ITEM_UNAVAILABLE,
            BillingOutcome.ITEM_ALREADY_OWNED,
            BillingOutcome.BILLING_UNAVAILABLE,
            BillingOutcome.FEATURE_NOT_SUPPORTED,
            BillingOutcome.USER_CANCELED,
        ).forEach { outcome ->
            assertNull("$outcome must not retry", SupporterAckRetryPolicy.nextDelayMillis(outcome, 0))
        }
    }

    @Test
    fun `OK never schedules a retry`() {
        assertNull(SupporterAckRetryPolicy.nextDelayMillis(BillingOutcome.OK, 0))
    }
}
