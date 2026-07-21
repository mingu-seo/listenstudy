package com.codro.listenstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.codro.listenstudy.BuildConfig
import com.codro.listenstudy.R
import com.codro.listenstudy.ui.theme.QuietReaderShapes
import com.codro.listenstudy.ui.theme.QuietReaderSizes
import com.codro.listenstudy.ui.theme.QuietReaderSpacing
import com.codro.listenstudy.ui.theme.extendedColors

/**
 * In-app "app info" screen. It is the single place the service discloses its operator, data
 * handling, and legal notices, so the required disclosures are driven by [AboutContent] (unit
 * tested) rather than by ad-hoc copy here. Only the runtime version comes from [BuildConfig].
 *
 * Follows the Quiet Reader tokens (spacing/shape/typography) and accessibility conventions used by
 * [SettingsScreen]: section titles are exposed as headings and the only interactive control (Back)
 * keeps a 48dp touch target via [LsOutlinedButton].
 */
@Composable
fun AboutScreen(
    versionName: String = BuildConfig.VERSION_NAME,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = QuietReaderSpacing.lg, vertical = QuietReaderSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.lg),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(
                            minWidth = QuietReaderSizes.MinTouchTarget,
                            minHeight = QuietReaderSizes.MinTouchTarget,
                        ),
                        contentPadding = PaddingValues(
                            horizontal = QuietReaderSpacing.md,
                            vertical = QuietReaderSpacing.sm,
                        ),
                    ) {
                        Text(stringResource(R.string.back), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.padding(horizontal = QuietReaderSpacing.xs))
                    Column {
                        Text(
                            stringResource(R.string.about_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.about_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textSecondary,
                        )
                    }
                }
            }

            // Identity: public app name, developer, runtime version, package.
            item {
                AboutCard {
                    Text(
                        stringResource(R.string.about_app_name, AboutContent.APP_NAME_KO, AboutContent.APP_NAME_EN),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    AboutSecondary(stringResource(R.string.about_developer, AboutContent.DEVELOPER))
                    AboutSecondary(stringResource(R.string.about_version, versionName))
                    AboutSecondary(stringResource(R.string.about_package, AboutContent.PACKAGE_ID))
                }
            }

            // Data-handling disclosures, ordered by AboutContent so the required set stays aligned.
            item {
                AboutCard {
                    AboutSectionTitle(stringResource(R.string.about_data_handling_title))
                    AboutContent.requiredDisclosures
                        .filter { it in DATA_HANDLING_DISCLOSURES }
                        .forEach { disclosure ->
                            DisclosureRow(disclosure)
                        }
                }
            }

            // Privacy notice — current text is rendered in-app and the verified public page opens externally.
            item {
                AboutCard {
                    AboutSectionTitle(stringResource(R.string.about_privacy_title))
                    AboutSecondary(stringResource(R.string.about_privacy_body))
                    AboutSecondary(stringResource(R.string.about_policy_url_label, AboutContent.PRIVACY_POLICY_URL))
                    if (AboutContent.policyPagesPublished) {
                        OutlinedButton(onClick = { uriHandler.openUri(AboutContent.PRIVACY_POLICY_URL) }) {
                            Text(stringResource(R.string.about_open_privacy_policy))
                        }
                    }
                    AboutSectionTitle(stringResource(R.string.about_privacy_full_title))
                    AboutContent.privacySections.forEach { section ->
                        val (titleRes, bodyRes) = privacySectionText(section)
                        PolicySection(titleRes, bodyRes)
                    }
                }
            }

            // Terms of use — current text is rendered in-app and the verified public page opens externally.
            item {
                AboutCard {
                    AboutSectionTitle(stringResource(R.string.about_terms_title))
                    AboutSecondary(stringResource(R.string.about_terms_body))
                    AboutSecondary(stringResource(R.string.about_policy_url_label, AboutContent.TERMS_URL))
                    if (AboutContent.policyPagesPublished) {
                        OutlinedButton(onClick = { uriHandler.openUri(AboutContent.TERMS_URL) }) {
                            Text(stringResource(R.string.about_open_terms))
                        }
                    }
                    AboutSectionTitle(stringResource(R.string.about_terms_full_title))
                    AboutContent.termsSections.forEach { section ->
                        val (titleRes, bodyRes) = termsSectionText(section)
                        PolicySection(titleRes, bodyRes)
                    }
                }
            }

            // Support / privacy contact.
            item {
                AboutCard {
                    AboutSectionTitle(stringResource(R.string.about_contact_title))
                    AboutSecondary(stringResource(R.string.about_contact_email, AboutContent.SUPPORT_EMAIL))
                }
            }

            // Open-source notices for the actual core dependencies (no versions; see build files).
            item {
                AboutCard {
                    AboutSectionTitle(stringResource(R.string.about_open_source_title))
                    AboutSecondary(stringResource(R.string.about_open_source_body))
                    AboutContent.openSourceNotices.forEach { notice ->
                        AboutSecondary(stringResource(R.string.about_open_source_entry, notice.name, notice.license))
                        AboutSecondary(notice.url)
                    }
                }
            }
        }
    }
}

/** The subset of [AboutDisclosure] rendered as generic data-handling rows on the About screen. */
private val DATA_HANDLING_DISCLOSURES = setOf(
    AboutDisclosure.NoLoginNoBackend,
    AboutDisclosure.LocalStorage,
    AboutDisclosure.PhoneTtsNetwork,
    AboutDisclosure.CloudTransferOnSelection,
    AboutDisclosure.UserOwnedKeyAndCost,
    AboutDisclosure.LocalDataDeletion,
    AboutDisclosure.NoAdsNoAnalytics,
)

@Composable
private fun DisclosureRow(disclosure: AboutDisclosure) {
    val (titleRes, bodyRes) = disclosureText(disclosure) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.xxs)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        AboutSecondary(stringResource(bodyRes))
    }
}

/**
 * Maps a disclosure to its (title, body) string resources. Exhaustive `when` so a newly added
 * [AboutDisclosure] fails to compile until its copy is provided. Disclosures with a dedicated card
 * (privacy, terms, contact, open source) return null and are rendered elsewhere.
 */
private fun disclosureText(disclosure: AboutDisclosure): Pair<Int, Int>? = when (disclosure) {
    AboutDisclosure.NoLoginNoBackend ->
        R.string.about_disclosure_no_backend_title to R.string.about_disclosure_no_backend_body
    AboutDisclosure.LocalStorage ->
        R.string.about_disclosure_local_storage_title to R.string.about_disclosure_local_storage_body
    AboutDisclosure.PhoneTtsNetwork ->
        R.string.about_disclosure_phone_tts_title to R.string.about_disclosure_phone_tts_body
    AboutDisclosure.CloudTransferOnSelection ->
        R.string.about_disclosure_cloud_transfer_title to R.string.about_disclosure_cloud_transfer_body
    AboutDisclosure.UserOwnedKeyAndCost ->
        R.string.about_disclosure_user_key_title to R.string.about_disclosure_user_key_body
    AboutDisclosure.LocalDataDeletion ->
        R.string.about_disclosure_local_deletion_title to R.string.about_disclosure_local_deletion_body
    AboutDisclosure.NoAdsNoAnalytics ->
        R.string.about_disclosure_no_ads_title to R.string.about_disclosure_no_ads_body
    AboutDisclosure.PrivacyNotice,
    AboutDisclosure.TermsGuidance,
    AboutDisclosure.SupportContact,
    AboutDisclosure.OpenSource -> null
}

/**
 * Maps a [PrivacySection] to its (title, body) string resources. Exhaustive `when` so a newly added
 * section fails to compile until its localized copy is provided.
 */
private fun privacySectionText(section: PrivacySection): Pair<Int, Int> = when (section) {
    PrivacySection.PolicyStatusAndEffectiveDate ->
        R.string.about_privacy_s_status_title to R.string.about_privacy_s_status_body
    PrivacySection.Operator ->
        R.string.about_privacy_s_operator_title to R.string.about_privacy_s_operator_body
    PrivacySection.NoAccountNoBackendNoAds ->
        R.string.about_privacy_s_no_account_title to R.string.about_privacy_s_no_account_body
    PrivacySection.LocalData ->
        R.string.about_privacy_s_local_title to R.string.about_privacy_s_local_body
    PrivacySection.ApiKeyStorageAndDeletion ->
        R.string.about_privacy_s_api_key_title to R.string.about_privacy_s_api_key_body
    PrivacySection.PhoneTtsNetwork ->
        R.string.about_privacy_s_phone_tts_title to R.string.about_privacy_s_phone_tts_body
    PrivacySection.CloudTransferAndPrefetch ->
        R.string.about_privacy_s_cloud_title to R.string.about_privacy_s_cloud_body
    PrivacySection.RetentionDeletion ->
        R.string.about_privacy_s_retention_title to R.string.about_privacy_s_retention_body
    PrivacySection.Billing ->
        R.string.about_privacy_s_billing_title to R.string.about_privacy_s_billing_body
    PrivacySection.Children ->
        R.string.about_privacy_s_children_title to R.string.about_privacy_s_children_body
    PrivacySection.Contact ->
        R.string.about_privacy_s_contact_title to R.string.about_privacy_s_contact_body
}

/** Maps a [TermsSection] to its (title, body) string resources. Exhaustive by design (see above). */
private fun termsSectionText(section: TermsSection): Pair<Int, Int> = when (section) {
    TermsSection.PolicyStatusAndOperator ->
        R.string.about_terms_s_status_title to R.string.about_terms_s_status_body
    TermsSection.Purpose ->
        R.string.about_terms_s_purpose_title to R.string.about_terms_s_purpose_body
    TermsSection.UserContent ->
        R.string.about_terms_s_user_content_title to R.string.about_terms_s_user_content_body
    TermsSection.CloudCostAndQuota ->
        R.string.about_terms_s_cloud_cost_title to R.string.about_terms_s_cloud_cost_body
    TermsSection.AsIsNoGuarantee ->
        R.string.about_terms_s_as_is_title to R.string.about_terms_s_as_is_body
    TermsSection.Termination ->
        R.string.about_terms_s_termination_title to R.string.about_terms_s_termination_body
    TermsSection.Billing ->
        R.string.about_terms_s_billing_title to R.string.about_terms_s_billing_body
    TermsSection.OpenSource ->
        R.string.about_terms_s_open_source_title to R.string.about_terms_s_open_source_body
    TermsSection.ContactAndChanges ->
        R.string.about_terms_s_contact_title to R.string.about_terms_s_contact_body
}

@Composable
private fun PolicySection(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.xxs)) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        AboutSecondary(stringResource(bodyRes))
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = QuietReaderShapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(QuietReaderSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm),
        ) {
            content()
        }
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun AboutSecondary(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.extendedColors.textSecondary,
    )
}
