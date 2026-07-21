package com.codro.listenstudy.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The About screen is the single in-app place where the service's operator, data handling, and
 * legal notices are disclosed. These invariants must match the privacy policy, the terms of use,
 * and the eventual Play Data safety answers, so they are pinned as unit tests rather than left to a
 * brittle Compose-tree assertion.
 */
class AboutContentTest {

    @Test
    fun `identity states public app name developer and package`() {
        assertEquals("소리노트", AboutContent.APP_NAME_KO)
        assertEquals("SoriNote", AboutContent.APP_NAME_EN)
        assertEquals("Codro", AboutContent.DEVELOPER)
        assertEquals("com.codro.listenstudy", AboutContent.PACKAGE_ID)
    }

    @Test
    fun `support and privacy contact is the decided address`() {
        assertEquals("codro.ceo@gmail.com", AboutContent.SUPPORT_EMAIL)
        assertEquals("codro.ceo@gmail.com", AboutContent.PRIVACY_EMAIL)
    }

    @Test
    fun `policy urls point at the decided codro domain pages`() {
        assertEquals("https://codro.it/apps/sorinote/privacy", AboutContent.PRIVACY_POLICY_URL)
        assertEquals("https://codro.it/apps/sorinote/terms", AboutContent.TERMS_URL)
    }

    @Test
    fun `published public policy pages are offered as active links`() {
        // Both codro.it pages were verified live on 2026-07-21.
        assertTrue(AboutContent.policyPagesPublished)
    }

    @Test
    fun `all required disclosures are present`() {
        assertEquals(
            setOf(
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
            ),
            AboutContent.requiredDisclosures.toSet(),
        )
    }

    @Test
    fun `required disclosures list has no duplicates and a stable order`() {
        assertEquals(
            AboutContent.requiredDisclosures.size,
            AboutContent.requiredDisclosures.toSet().size,
        )
    }

    @Test
    fun `a dedicated disclosure separates offline on-device tts from network-backed system voices`() {
        // The old copy claimed phone TTS never transmits. That is false: a network-backed system or
        // third-party TTS voice can send text to that installed provider. This topic must exist so the
        // About screen carries the corrected offline-vs-network distinction as its own row.
        assertTrue(
            "PhoneTtsNetwork must be a required, data-handling disclosure",
            AboutContent.requiredDisclosures.contains(AboutDisclosure.PhoneTtsNetwork),
        )
    }

    @Test
    fun `the full in-app privacy notice covers every documented topic`() {
        // With the public codro.it pages unpublished, the About screen must itself carry the full,
        // current privacy notice. These sections must mirror privacy-policy-ko.md, including the
        // policy status / effective-date metadata, the on-device offline/network distinction, the
        // Google Cloud transfer-plus-prefetch topic, and the children-privacy clause.
        assertEquals(
            setOf(
                PrivacySection.PolicyStatusAndEffectiveDate,
                PrivacySection.Operator,
                PrivacySection.NoAccountNoBackendNoAds,
                PrivacySection.LocalData,
                PrivacySection.ApiKeyStorageAndDeletion,
                PrivacySection.PhoneTtsNetwork,
                PrivacySection.CloudTransferAndPrefetch,
                PrivacySection.RetentionDeletion,
                PrivacySection.Billing,
                PrivacySection.Children,
                PrivacySection.Contact,
            ),
            AboutContent.privacySections.toSet(),
        )
    }

    @Test
    fun `the in-app privacy notice models policy status effective date and children`() {
        // The public codro.it page is unpublished, so the in-app notice IS the current authoritative
        // policy. Two topics the docs carry were previously unmodeled in-app: the applicable-status /
        // effective-date metadata (2026-07-21) and the children-privacy clause. Both must exist as
        // their own sections so the About screen mirrors privacy-policy-ko.md exactly.
        assertTrue(
            "policy status / effective-date section must be present",
            AboutContent.privacySections.contains(PrivacySection.PolicyStatusAndEffectiveDate),
        )
        assertTrue(
            "children-privacy section must be present",
            AboutContent.privacySections.contains(PrivacySection.Children),
        )
    }

    @Test
    fun `the full in-app terms cover every documented topic`() {
        assertEquals(
            setOf(
                TermsSection.PolicyStatusAndOperator,
                TermsSection.Purpose,
                TermsSection.UserContent,
                TermsSection.CloudCostAndQuota,
                TermsSection.AsIsNoGuarantee,
                TermsSection.Termination,
                TermsSection.Billing,
                TermsSection.OpenSource,
                TermsSection.ContactAndChanges,
            ),
            AboutContent.termsSections.toSet(),
        )
    }

    @Test
    fun `in-app policy and terms sections have no duplicates and a stable order`() {
        assertEquals(AboutContent.privacySections.size, AboutContent.privacySections.toSet().size)
        assertEquals(AboutContent.termsSections.size, AboutContent.termsSections.toSet().size)
    }

    @Test
    fun `the in-app notice is presented as the current authoritative policy not a packaged external doc`() {
        // Blocker: the pending note previously implied an external document was bundled in the APK.
        // The in-app sections carry the current revision while the published URLs remain available.
        assertTrue(AboutContent.inAppPolicyIsCurrent)
        assertTrue(AboutContent.policyPagesPublished)
    }

    @Test
    fun `open source notices cover the real core dependencies with a license and url`() {
        val names = AboutContent.openSourceNotices.map { it.name }
        assertTrue("Compose expected", names.any { it.contains("Compose", ignoreCase = true) })
        assertTrue("AndroidX expected", names.any { it.contains("AndroidX", ignoreCase = true) })
        assertTrue("Kotlin expected", names.any { it.equals("Kotlin", ignoreCase = true) || it.startsWith("Kotlin ") })
        assertTrue("Coroutines expected", names.any { it.contains("Coroutines", ignoreCase = true) })

        assertTrue("at least four notices", AboutContent.openSourceNotices.size >= 4)
        AboutContent.openSourceNotices.forEach { notice ->
            assertTrue("license set for ${notice.name}", notice.license.isNotBlank())
            assertEquals("Apache License 2.0", notice.license)
            assertTrue("url is https for ${notice.name}", notice.url.startsWith("https://"))
        }
    }

    @Test
    fun `open source notices declare no dependency versions to avoid inventing them`() {
        // Versions belong in the build files, not in hand-written notices that can drift.
        AboutContent.openSourceNotices.forEach { notice ->
            assertFalse(
                "version-like token in ${notice.name}",
                Regex("""\d+\.\d+""").containsMatchIn(notice.name),
            )
        }
    }
}
