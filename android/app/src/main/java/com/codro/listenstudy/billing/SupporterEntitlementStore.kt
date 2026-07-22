package com.codro.listenstudy.billing

import android.content.Context

/**
 * Local persistence for the supporter entitlement and the optional sepia theme selection.
 *
 * Only these two booleans are ever stored. Purchase tokens, order ids, account identifiers, and
 * payment details are never persisted (and never logged). This is client-only bookkeeping of what
 * Play last reported — it is refreshed from Play's purchase query and is not server-grade fraud
 * prevention.
 */
interface SupporterEntitlementStore {
    fun isSupporter(): Boolean
    fun setSupporter(value: Boolean)

    /** The user's preference. Kept even while not entitled so re-entitlement restores the theme. */
    fun isSepiaThemeSelected(): Boolean
    fun setSepiaThemeSelected(value: Boolean)

    /** The theme actually applies only while the selection is backed by the entitlement. */
    fun isSepiaThemeActive(): Boolean =
        SupporterFeaturePolicy.sepiaThemeEligible(isSupporter()) && isSepiaThemeSelected()
}

/**
 * Store over a plain read/write seam so the persistence policy is JVM-testable; the Android
 * implementation plugs SharedPreferences into the same seam (mirrors the SecretStore pattern).
 */
class KeyValueSupporterEntitlementStore(
    private val read: (key: String, default: Boolean) -> Boolean,
    private val write: (key: String, value: Boolean) -> Unit,
) : SupporterEntitlementStore {
    override fun isSupporter(): Boolean = read(KEY_IS_SUPPORTER, false)
    override fun setSupporter(value: Boolean) = write(KEY_IS_SUPPORTER, value)
    override fun isSepiaThemeSelected(): Boolean = read(KEY_SEPIA_SELECTED, false)
    override fun setSepiaThemeSelected(value: Boolean) = write(KEY_SEPIA_SELECTED, value)

    companion object {
        const val KEY_IS_SUPPORTER = "is_supporter"
        const val KEY_SEPIA_SELECTED = "sepia_theme_selected"
    }
}

/** SharedPreferences-backed store in the app's private storage. */
fun supporterEntitlementStore(context: Context): SupporterEntitlementStore {
    val prefs = context.applicationContext.getSharedPreferences("supporter", Context.MODE_PRIVATE)
    return KeyValueSupporterEntitlementStore(
        read = { key, default -> prefs.getBoolean(key, default) },
        write = { key, value -> prefs.edit().putBoolean(key, value).apply() },
    )
}
