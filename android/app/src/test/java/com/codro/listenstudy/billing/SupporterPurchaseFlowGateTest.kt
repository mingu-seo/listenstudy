package com.codro.listenstudy.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Single-flight guard for the purchase button: rapid double taps must not launch two billing
 * flows. The gate opens again when the flow finishes (callback, cancellation, or a synchronous
 * launch failure).
 */
class SupporterPurchaseFlowGateTest {

    private val gate = SupporterPurchaseFlowGate()

    @Test
    fun `first tap acquires the gate`() {
        assertTrue(gate.tryBegin())
    }

    @Test
    fun `a second tap while the flow is in flight is rejected`() {
        assertTrue(gate.tryBegin())
        assertFalse("double tap must not launch a second flow", gate.tryBegin())
        assertFalse(gate.tryBegin())
    }

    @Test
    fun `finishing the flow reopens the gate`() {
        assertTrue(gate.tryBegin())
        gate.end()
        assertTrue("a later deliberate purchase attempt must work", gate.tryBegin())
    }

    @Test
    fun `end is idempotent and safe without a running flow`() {
        gate.end()
        assertTrue(gate.tryBegin())
        gate.end()
        gate.end()
        assertTrue(gate.tryBegin())
    }
}
