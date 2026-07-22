package com.codro.listenstudy.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Play Console contract for the Supporter product. The product id and type are release
 * configuration, not implementation detail: a mismatch silently breaks purchase and restore, so
 * they are locked here. The fixed 2,000원 price is configured in Play Console, never in code.
 */
class SupporterProductTest {

    @Test
    fun `product id matches the play console supporter product`() {
        assertEquals("listenstudy_supporter_2000", SupporterProduct.PRODUCT_ID)
    }

    @Test
    fun `product is a one-time inapp product`() {
        // Must equal BillingClient.ProductType.INAPP ("inapp") without linking the Play library
        // into this JVM test.
        assertEquals("inapp", SupporterProduct.PLAY_PRODUCT_TYPE)
    }

    @Test
    fun `product is non-consumable and must never be consumed`() {
        assertFalse(SupporterProduct.CONSUMABLE)
    }

    @Test
    fun `purchased product must be acknowledged`() {
        assertTrue(SupporterProduct.REQUIRES_ACKNOWLEDGEMENT)
    }
}
