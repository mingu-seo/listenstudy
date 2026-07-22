package com.codro.listenstudy.billing

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-flight guard for launching the Play purchase sheet. Rapid double taps on the purchase
 * button must not call `launchBillingFlow` twice; the flow is over when `onPurchasesUpdated`
 * answers (purchase, cancellation, or error) or when the launch itself fails synchronously —
 * both call [end] so a later deliberate attempt works again.
 */
class SupporterPurchaseFlowGate {

    private val inFlight = AtomicBoolean(false)

    /** True when the caller may launch; false while a previous launch is still unanswered. */
    fun tryBegin(): Boolean = inFlight.compareAndSet(false, true)

    /** The flow finished (callback or synchronous failure). Idempotent. */
    fun end() {
        inFlight.set(false)
    }
}
