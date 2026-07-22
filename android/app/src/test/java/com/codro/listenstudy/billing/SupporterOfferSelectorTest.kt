package com.codro.listenstudy.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Billing 9 one-time products expose a LIST of purchase offers. The UI price and the
 * `setOfferToken()` handed to the billing flow must come from the SAME selected offer, chosen
 * deterministically; with no valid offer the product must not be presented as purchasable.
 */
class SupporterOfferSelectorTest {

    private fun candidate(token: String = "offer-token-1", price: String = "₩2,000") =
        SupporterOfferCandidate(offerToken = token, formattedPrice = price)

    @Test
    fun `empty offer list selects nothing`() {
        assertNull(SupporterOfferSelector.select(emptyList()))
    }

    @Test
    fun `the single base offer of the simple product is selected`() {
        val base = candidate()
        assertSame(base, SupporterOfferSelector.select(listOf(base)))
    }

    @Test
    fun `a blank offer token is never selected`() {
        assertNull(SupporterOfferSelector.select(listOf(candidate(token = ""))))
        assertNull(SupporterOfferSelector.select(listOf(candidate(token = "   "))))
    }

    @Test
    fun `a blank formatted price is never selected`() {
        assertNull(SupporterOfferSelector.select(listOf(candidate(price = ""))))
    }

    @Test
    fun `multiple offers select the first valid one deterministically`() {
        val invalid = candidate(token = "", price = "₩1,000")
        val first = candidate(token = "offer-a")
        val second = candidate(token = "offer-b", price = "₩1,500")
        assertSame(first, SupporterOfferSelector.select(listOf(invalid, first, second)))
        // Same input, same choice — never a different offer on a repeat call.
        assertSame(first, SupporterOfferSelector.select(listOf(invalid, first, second)))
    }

    @Test
    fun `all offers invalid selects nothing`() {
        assertNull(
            SupporterOfferSelector.select(
                listOf(candidate(token = ""), candidate(price = "")),
            ),
        )
    }

    @Test
    fun `displayed price and launched token always come from the same offer`() {
        val a = candidate(token = "offer-a", price = "₩2,000")
        val b = candidate(token = "offer-b", price = "₩1,000")
        val selected = SupporterOfferSelector.select(listOf(a, b))!!
        // Structural guarantee: one object carries both, so they can never diverge.
        assertEquals("offer-a", selected.offerToken)
        assertEquals("₩2,000", selected.formattedPrice)
    }
}
