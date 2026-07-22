package com.codro.listenstudy.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entitlement/theme persistence policy over a plain key-value seam, so the rules run on the JVM
 * without SharedPreferences. Only the minimal metadata (supporter yes/no, theme selection) is
 * persisted — never tokens, order ids, or payment details.
 */
class SupporterEntitlementStoreTest {

    private fun store(values: MutableMap<String, Boolean> = mutableMapOf()) =
        KeyValueSupporterEntitlementStore(
            read = { key, default -> values.getOrDefault(key, default) },
            write = { key, value -> values[key] = value },
        )

    @Test
    fun `defaults to not supporter and no sepia selection`() {
        val store = store()
        assertFalse(store.isSupporter())
        assertFalse(store.isSepiaThemeSelected())
    }

    @Test
    fun `persisted supporter entitlement survives a new store instance`() {
        val values = mutableMapOf<String, Boolean>()
        store(values).setSupporter(true)
        assertTrue(store(values).isSupporter())
    }

    @Test
    fun `sepia selection persists independently of entitlement`() {
        val values = mutableMapOf<String, Boolean>()
        store(values).setSepiaThemeSelected(true)
        assertTrue(store(values).isSepiaThemeSelected())
        assertFalse(store(values).isSupporter())
    }

    @Test
    fun `sepia theme is active only when selected AND entitled`() {
        val store = store()
        assertFalse(store.isSepiaThemeActive())

        store.setSepiaThemeSelected(true)
        assertFalse("selection without entitlement must not activate", store.isSepiaThemeActive())

        store.setSupporter(true)
        assertTrue(store.isSepiaThemeActive())
    }

    @Test
    fun `losing entitlement deactivates sepia but keeps the selection`() {
        val store = store()
        store.setSupporter(true)
        store.setSepiaThemeSelected(true)
        store.setSupporter(false)
        assertFalse(store.isSepiaThemeActive())
        assertTrue("selection is kept so re-entitlement restores the theme", store.isSepiaThemeSelected())
    }

    @Test
    fun `store persists only the minimal boolean metadata keys`() {
        val values = mutableMapOf<String, Boolean>()
        val store = store(values)
        store.setSupporter(true)
        store.setSepiaThemeSelected(true)
        assertEquals(
            setOf(
                KeyValueSupporterEntitlementStore.KEY_IS_SUPPORTER,
                KeyValueSupporterEntitlementStore.KEY_SEPIA_SELECTED,
            ),
            values.keys,
        )
    }
}

/** Free/core features must never depend on supporter state. */
class SupporterFeaturePolicyTest {

    @Test
    fun `reader local tts and byok cloud tts stay enabled in every supporter state`() {
        val allStates = listOf(
            SupporterState.Unavailable,
            SupporterState.Loading,
            SupporterState.ReadyNotPurchased(product = null),
            SupporterState.ReadyNotPurchased(SupporterProductInfo("Supporter", "₩2,000")),
            SupporterState.Pending,
            SupporterState.Purchased,
            SupporterState.Error(SupporterErrorKind.BillingUnavailable),
            SupporterState.Error(SupporterErrorKind.ItemUnavailable),
            SupporterState.Error(SupporterErrorKind.Network),
            SupporterState.Error(SupporterErrorKind.Unknown),
        )
        allStates.forEach { state ->
            assertTrue("core features locked in $state", SupporterFeaturePolicy.coreFeaturesEnabled(state))
        }
    }

    @Test
    fun `only the sepia theme is supporter-gated`() {
        assertFalse(SupporterFeaturePolicy.sepiaThemeEligible(isSupporter = false))
        assertTrue(SupporterFeaturePolicy.sepiaThemeEligible(isSupporter = true))
    }
}
