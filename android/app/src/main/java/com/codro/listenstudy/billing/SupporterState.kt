package com.codro.listenstudy.billing

/**
 * Deterministic, framework-free supporter state. Every billing outcome maps onto exactly one of
 * these so the UI and the tests share a single model. No state may gate a core feature — see
 * [SupporterFeaturePolicy].
 */
sealed interface SupporterState {
    /** Google Play billing is not usable on this device/account (e.g. BILLING_UNAVAILABLE). */
    data object Unavailable : SupporterState

    /** Connecting to Play or loading product/ownership data. */
    data object Loading : SupporterState

    /** Ready to purchase. [product] carries Play's localized title/price once loaded. */
    data class ReadyNotPurchased(val product: SupporterProductInfo?) : SupporterState

    /** A purchase exists but is awaiting completion (e.g. cash payment). Grants nothing. */
    data object Pending : SupporterState

    /** The supporter entitlement is owned. */
    data object Purchased : SupporterState

    /** A retryable/reportable failure. Never blocks core functionality. */
    data class Error(val kind: SupporterErrorKind) : SupporterState
}

enum class SupporterErrorKind {
    /** Play billing missing/too old on the device. */
    BillingUnavailable,

    /** The product is not available for purchase (e.g. not yet published). */
    ItemUnavailable,

    /** Offline or Play service connection lost; retryable. */
    Network,

    /** Anything else. Retryable without being alarming. */
    Unknown,
}

/** Whether the supporter row may gate anything. Core features: never. */
object SupporterFeaturePolicy {
    /** The reader, local TTS, and BYOK Cloud TTS are free in every supporter state. */
    @Suppress("UNUSED_PARAMETER")
    fun coreFeaturesEnabled(state: SupporterState): Boolean = true

    /** The optional sepia theme is the only supporter-gated capability. */
    fun sepiaThemeEligible(isSupporter: Boolean): Boolean = isSupporter
}
