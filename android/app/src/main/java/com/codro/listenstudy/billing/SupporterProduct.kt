package com.codro.listenstudy.billing

/**
 * Play Console contract for the one-time Supporter product. This layer is framework-free so the
 * contract can be pinned by JVM unit tests; the adapter ([SupporterBillingClient]) translates
 * [PLAY_PRODUCT_TYPE] into `BillingClient.ProductType.INAPP`.
 *
 * The actual charged price (fixed 2,000원) is a Play Console configuration decision. The app never
 * hard-codes it; it displays the Play-provided localized name and formatted price instead.
 */
object SupporterProduct {
    const val PRODUCT_ID = "listenstudy_supporter_2000"

    /** Mirrors `BillingClient.ProductType.INAPP` without linking the Play library into this layer. */
    const val PLAY_PRODUCT_TYPE = "inapp"

    /** Supporter is a permanent entitlement: acknowledge it, never consume it. */
    const val CONSUMABLE = false
    const val REQUIRES_ACKNOWLEDGEMENT = true
}

/** Play-provided localized display data for the product. Never contains a hard-coded price. */
data class SupporterProductInfo(
    val title: String,
    val formattedPrice: String,
)
