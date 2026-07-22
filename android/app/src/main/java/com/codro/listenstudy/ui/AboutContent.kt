package com.codro.listenstudy.ui

/**
 * The topics the About screen must disclose. The screen renders localized body text for each one
 * (from string resources), but the *set* of required disclosures is fixed here so it can be pinned
 * by a unit test and kept aligned with the privacy policy, terms of use, and Play Data safety.
 *
 * Runtime-only facts (the installed [BuildConfig] version) are intentionally not modeled here — this
 * layer holds no Android types so it stays directly unit-testable.
 */
enum class AboutDisclosure {
    /** No sign-in and no first-party backend. */
    NoLoginNoBackend,

    /** Documents, sentences, progress, settings, and voice cache are stored on the device. */
    LocalStorage,

    /**
     * Phone (system) TTS: offline on-device voices synthesize locally, but a network-backed system or
     * third-party TTS voice may send text to that installed TTS provider under its own policy — never
     * through Codro. Corrects the earlier "phone TTS never transmits" claim.
     */
    PhoneTtsNetwork,

    /**
     * Only when the user chooses Google Cloud TTS is the selected text sent directly to Google Cloud;
     * it also speculatively prefetches up to two upcoming distinct sentences before playback.
     */
    CloudTransferOnSelection,

    /** The Google Cloud API key is user-owned; its cost and quota are the user's responsibility. */
    UserOwnedKeyAndCost,

    /** Removing app data or uninstalling can delete local documents, progress, and settings. */
    LocalDataDeletion,

    /** This release ships no ads and no behavioral-analytics SDKs. */
    NoAdsNoAnalytics,

    /** Support and privacy contact address. */
    SupportContact,

    /** Pointer to the privacy notice. */
    PrivacyNotice,

    /** Pointer to the terms-of-use / usage guidance. */
    TermsGuidance,

    /** Open-source license notices. */
    OpenSource,
}

/**
 * Sections of the in-app privacy notice. The About screen carries the key content of the current
 * policy restructured into sections; each section renders localized title + body from `strings.xml`
 * and must stay aligned with `privacy-policy-ko.md` in numbering and substance (not verbatim). The
 * published codro.it pages carry the full documents and are offered as external links.
 */
enum class PrivacySection {
    /**
     * Policy status and effective date: the in-app notice covers the current, applicable privacy
     * policy as of its effective date (2026-07-22), matching the status/effective-date block of
     * `privacy-policy-ko.md`.
     */
    PolicyStatusAndEffectiveDate,

    /** Operator identity and contact address. */
    Operator,

    /** No Codro account, no first-party backend, no ads, no behavioral analytics. */
    NoAccountNoBackendNoAds,

    /** Documents, sentences, progress, settings, and Cloud voice cache are stored locally. */
    LocalData,

    /** The user-provided Google Cloud API key is Keystore-encrypted and user-deletable. */
    ApiKeyStorageAndDeletion,

    /** Offline on-device voices stay local; network-backed system voices may transmit to their provider. */
    PhoneTtsNetwork,

    /** Cloud TTS sends the sentence text to Google under the user's key, and prefetches upcoming sentences. */
    CloudTransferAndPrefetch,

    /**
     * One-time Supporter purchase: Google Play processes purchase/order/payment data; Codro only
     * receives the entitlement/order status needed to provide Supporter benefits and never stores
     * or logs purchase tokens/order ids. Refunds clear the status per the Play ownership query.
     * Core and BYOK features stay free regardless. §7 in `privacy-policy-ko.md`.
     */
    Billing,

    /** Local-only retention; data is removed on delete / clear-data / uninstall. §8. */
    RetentionDeletion,

    /** Children's privacy: the app is not aimed at children and collects no data from them. */
    Children,

    /** Contact address and update notice. */
    Contact,
}

/**
 * Sections of the in-app terms of use, aligned with `terms-of-use-ko.md` in numbering and
 * substance (not verbatim). Rendered the same way as [PrivacySection] so the terms' key content is
 * readable inside the About screen; the published page carries the full document.
 */
enum class TermsSection {
    /** Current applicability, effective date, operator, package, contact, and web-publication status. */
    PolicyStatusAndOperator,

    /** Purpose of the service. */
    Purpose,

    /** User owns imported content and is responsible for its lawful use. */
    UserContent,

    /** Cloud TTS is optional; its cost and quota are the user's responsibility. */
    CloudCostAndQuota,

    /** Provided "as is" with no guarantee. */
    AsIsNoGuarantee,

    /** Termination by uninstalling; no account to close. */
    Termination,

    /**
     * One-time Supporter purchase processed by Google Play; optional, with core/BYOK features free
     * regardless of purchase state.
     */
    Billing,

    /** Open-source notice pointer. */
    OpenSource,

    /** Contact and change-notice. */
    ContactAndChanges,
}

/**
 * A single third-party component notice (open-source or SDK). Each entry carries its own license —
 * they are not all Apache 2.0 (Play Billing ships under the Android SDK license). Versions live in
 * the build files, never here.
 */
data class OpenSourceNotice(
    val name: String,
    val license: String,
    val url: String,
)

/**
 * UI-independent content policy for the About screen. Holds only the operator identity, contact,
 * policy URLs, the required disclosure set, and open-source notices — no secrets and no key
 * material. Body copy for each disclosure is localized in `strings.xml`.
 */
object AboutContent {
    const val APP_NAME_KO = "소리노트"
    const val APP_NAME_EN = "SoriNote"
    const val DEVELOPER = "Codro"
    const val PACKAGE_ID = "com.codro.listenstudy"

    const val SUPPORT_EMAIL = "codro.ceo@gmail.com"
    const val PRIVACY_EMAIL = "codro.ceo@gmail.com"

    const val PRIVACY_POLICY_URL = "https://codro.it/apps/sorinote/privacy"
    const val TERMS_URL = "https://codro.it/apps/sorinote/terms"

    /** Both public policy pages were verified live on 2026-07-21. */
    const val policyPagesPublished = true

    /**
     * Whether the privacy notice and terms rendered inside the About screen (from [privacySections] /
     * [termsSections]) are the current, authoritative policy. They are: nothing is packaged as an
     * external document. The in-app sections carry the current revision and the published pages are
     * available as external references.
     */
    const val inAppPolicyIsCurrent = true

    /** The disclosure topics the About screen must surface. Order is stable and free of duplicates. */
    val requiredDisclosures: List<AboutDisclosure> = listOf(
        AboutDisclosure.NoLoginNoBackend,
        AboutDisclosure.LocalStorage,
        AboutDisclosure.PhoneTtsNetwork,
        AboutDisclosure.CloudTransferOnSelection,
        AboutDisclosure.UserOwnedKeyAndCost,
        AboutDisclosure.LocalDataDeletion,
        AboutDisclosure.NoAdsNoAnalytics,
        AboutDisclosure.SupportContact,
        AboutDisclosure.PrivacyNotice,
        AboutDisclosure.TermsGuidance,
        AboutDisclosure.OpenSource,
    )

    /**
     * Ordered sections of the in-app privacy notice. Stable order, no duplicates; each maps to a
     * localized title/body in `strings.xml` and stays aligned with `privacy-policy-ko.md`.
     */
    val privacySections: List<PrivacySection> = listOf(
        PrivacySection.PolicyStatusAndEffectiveDate,
        PrivacySection.Operator,
        PrivacySection.NoAccountNoBackendNoAds,
        PrivacySection.LocalData,
        PrivacySection.ApiKeyStorageAndDeletion,
        PrivacySection.PhoneTtsNetwork,
        PrivacySection.CloudTransferAndPrefetch,
        PrivacySection.Billing,
        PrivacySection.RetentionDeletion,
        PrivacySection.Children,
        PrivacySection.Contact,
    )

    /**
     * Ordered sections of the in-app terms of use. Stable order, no duplicates; each maps to a
     * localized title/body in `strings.xml` and stays aligned with `terms-of-use-ko.md`.
     */
    val termsSections: List<TermsSection> = listOf(
        TermsSection.PolicyStatusAndOperator,
        TermsSection.Purpose,
        TermsSection.UserContent,
        TermsSection.CloudCostAndQuota,
        TermsSection.AsIsNoGuarantee,
        TermsSection.Termination,
        TermsSection.Billing,
        TermsSection.OpenSource,
        TermsSection.ContactAndChanges,
    )

    /**
     * In-app component notices for the app's actual direct/core dependencies as declared in the
     * Gradle build files, each under its own license. Versions are deliberately omitted so this
     * list cannot drift out of sync with the build.
     */
    val openSourceNotices: List<OpenSourceNotice> = listOf(
        OpenSourceNotice(
            name = "Jetpack Compose",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/compose",
        ),
        OpenSourceNotice(
            name = "AndroidX (Core, Activity, Lifecycle, Material3, Media3, Room)",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx",
        ),
        OpenSourceNotice(
            name = "Kotlin",
            license = "Apache License 2.0",
            url = "https://github.com/JetBrains/kotlin",
        ),
        OpenSourceNotice(
            name = "Kotlin Coroutines",
            license = "Apache License 2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        OpenSourceNotice(
            name = "Google Play Billing Library",
            license = "Android Software Development Kit License",
            url = "https://developer.android.com/google/play/billing",
        ),
    )
}
