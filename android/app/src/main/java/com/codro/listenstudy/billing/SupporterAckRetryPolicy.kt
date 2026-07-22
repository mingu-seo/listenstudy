package com.codro.listenstudy.billing

/**
 * Bounded active retry for failed purchase acknowledgements. Play refunds unacknowledged
 * purchases after ~3 days, so a transient failure should not wait passively for the next
 * incidental ownership query — but the budget stays small: if the process dies or the budget is
 * exhausted, the next foreground ownership query re-discovers the unacknowledged purchase and
 * retries anyway. Acknowledgement is delivery bookkeeping; no retry outcome ever touches the
 * entitlement, and purchase tokens are never logged.
 */
object SupporterAckRetryPolicy {

    /** Short increasing backoff; the whole active budget is 3 attempts within under a minute. */
    val RETRY_DELAYS_MILLIS: List<Long> = listOf(2_000L, 8_000L, 30_000L)

    /**
     * Delay before retry number `retriesSoFar + 1`, or null to stop retrying actively (success,
     * non-retryable failure, or exhausted budget — the passive sighting path takes over).
     */
    fun nextDelayMillis(outcome: BillingOutcome, retriesSoFar: Int): Long? {
        if (!isTransient(outcome)) return null
        return RETRY_DELAYS_MILLIS.getOrNull(retriesSoFar)
    }

    /** Only genuinely transient service/network results earn an active retry. */
    fun isTransient(outcome: BillingOutcome): Boolean = when (outcome) {
        BillingOutcome.SERVICE_DISCONNECTED,
        BillingOutcome.SERVICE_UNAVAILABLE,
        BillingOutcome.NETWORK_ERROR,
        BillingOutcome.ERROR,
        -> true
        else -> false
    }
}
