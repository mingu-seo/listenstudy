package com.codro.listenstudy.billing

/**
 * Framework-free mirror of one Billing 9 `ProductDetails.OneTimePurchaseOfferDetails` entry —
 * only the two fields the app uses. Keeping the display price and the launch token in ONE value
 * makes it structurally impossible to show one offer's price and launch another's token.
 */
data class SupporterOfferCandidate(
    val offerToken: String,
    val formattedPrice: String,
)

/**
 * Deterministic choice of the purchasable offer from Billing 9's
 * `oneTimePurchaseOfferDetailsList`. The Supporter product is a single fixed-price non-consumable,
 * so Play normally returns exactly one base offer — but an empty list, blank tokens/prices, or a
 * multi-offer configuration must all degrade safely instead of launching a mismatched flow.
 */
object SupporterOfferSelector {

    /**
     * Returns the first offer carrying both a usable `setOfferToken()` value and a displayable
     * localized price, in Play's eligibility order; null when nothing is purchasable. A null
     * selection must surface as item-unavailable, never as a Ready state or a billing-flow launch.
     */
    fun select(candidates: List<SupporterOfferCandidate>): SupporterOfferCandidate? =
        candidates.firstOrNull { it.offerToken.isNotBlank() && it.formattedPrice.isNotBlank() }
}
