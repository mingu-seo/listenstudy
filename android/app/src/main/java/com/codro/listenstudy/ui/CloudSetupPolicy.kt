package com.codro.listenstudy.ui

import com.codro.listenstudy.playback.CloudKeySaveOutcome
import com.codro.listenstudy.playback.CloudKeySaveResult

/**
 * UI-independent policy for the BYOK (bring-your-own-key) Google Cloud setup wizard.
 *
 * This layer intentionally holds no Android or Compose types so it can be unit-tested directly.
 * It never stores the raw API key: the key is only ever passed as a transient argument to
 * [CloudSetupPolicy.canSaveKey] and is handed to the service for Keystore-backed storage.
 */
enum class CloudSetupStep {
    /** Optional-feature intro: phone TTS is free and needs no key. */
    Intro,

    /** Explicit data-transfer and billing-responsibility notice. */
    DataAndCostNotice,

    /** Guidance for the Google Cloud Console API/application restrictions. */
    ConsoleRestrictions,

    /** Key entry and secure save. */
    EnterAndSaveKey,

    /** Save-result confirmation and cloud voice preview. */
    PreviewAndDone,
}

/**
 * Outcome of the most recent save request, as reported by the service. [Pending] means the service
 * has not yet reported a save result — it must never be presented as success.
 */
enum class CloudSaveOutcome { Pending, Succeeded, Failed }

/**
 * Presentation model for the final wizard step. Derived from the service's explicit save result for
 * *this* request, rather than from an assumption that the save succeeded.
 */
data class CloudSetupSaveResultModel(
    val outcome: CloudSaveOutcome,
    /** Preview only once this save request is confirmed successful. */
    val canPreview: Boolean,
    /**
     * True when a key is installed (`hasCloudApiKey`) but this request is not confirmed successful,
     * e.g. a failed replacement leaves the previous key active. The UI must say so explicitly
     * instead of letting the lingering key read as proof that the replacement worked.
     */
    val showExistingKeyCaveat: Boolean,
)

/**
 * The two acknowledgements a user must make before a key may be saved. Consent is deliberately
 * separate from the key and is not persisted; it lives only for the duration of the wizard.
 */
data class CloudSetupConsent(
    val textSentToGoogleAcknowledged: Boolean = false,
    val billingResponsibilityAcknowledged: Boolean = false,
) {
    val bothAcknowledged: Boolean
        get() = textSentToGoogleAcknowledged && billingResponsibilityAcknowledged
}

/** Primary call-to-action for the setup entry point, derived from whether a key is configured. */
enum class CloudSetupCta { StartSetup, ReplaceKey }

/**
 * Presentation model derived from service-owned state (`hasCloudApiKey`, cache stats). Carries no
 * key material — only booleans describing which actions the UI should surface.
 */
data class CloudSetupStatusModel(
    val configured: Boolean,
    val cta: CloudSetupCta,
    val canPreview: Boolean,
    val canDeleteKey: Boolean,
    val canClearCache: Boolean,
)

/**
 * Immutable wizard state. Holds the current step and the transient consent, but never the key.
 */
data class CloudSetupWizardState(
    val step: CloudSetupStep = CloudSetupStep.Intro,
    val consent: CloudSetupConsent = CloudSetupConsent(),
) {
    fun acknowledgeDataTransfer(value: Boolean): CloudSetupWizardState =
        copy(consent = consent.copy(textSentToGoogleAcknowledged = value))

    fun acknowledgeBilling(value: Boolean): CloudSetupWizardState =
        copy(consent = consent.copy(billingResponsibilityAcknowledged = value))

    fun goTo(target: CloudSetupStep): CloudSetupWizardState = copy(step = target)

    fun next(): CloudSetupWizardState = copy(step = CloudSetupPolicy.nextStep(step))

    fun previous(): CloudSetupWizardState = copy(step = CloudSetupPolicy.previousStep(step))
}

object CloudSetupPolicy {
    val steps: List<CloudSetupStep> = CloudSetupStep.entries.toList()

    val stepCount: Int get() = steps.size

    fun nextStep(step: CloudSetupStep): CloudSetupStep =
        steps.getOrElse(steps.indexOf(step) + 1) { steps.last() }

    fun previousStep(step: CloudSetupStep): CloudSetupStep =
        steps.getOrElse(steps.indexOf(step) - 1) { steps.first() }

    fun isFirstStep(step: CloudSetupStep): Boolean = step == steps.first()

    fun isLastStep(step: CloudSetupStep): Boolean = step == steps.last()

    /** 1-based index for "N / M" progress labels. */
    fun stepNumber(step: CloudSetupStep): Int = steps.indexOf(step) + 1

    /**
     * The save button is enabled only when both the data-transfer and billing notices have been
     * explicitly acknowledged and the entered key is not blank.
     */
    fun canSaveKey(consent: CloudSetupConsent, keyInput: String): Boolean =
        consent.bothAcknowledged && keyInput.isNotBlank()

    /**
     * Resolves the service's [result] against the request the caller is actually waiting on. A
     * result carrying any other [CloudKeySaveResult.requestId] describes a different request — most
     * dangerously a previous success that has not yet been overwritten — and says nothing about
     * this one, so it stays [CloudSaveOutcome.Pending]. Never success by inference.
     */
    fun saveOutcome(expectedRequestId: Long?, result: CloudKeySaveResult): CloudSaveOutcome = when {
        expectedRequestId == null || result.requestId != expectedRequestId -> CloudSaveOutcome.Pending
        result.outcome == CloudKeySaveOutcome.Success -> CloudSaveOutcome.Succeeded
        result.outcome == CloudKeySaveOutcome.Failure -> CloudSaveOutcome.Failed
        else -> CloudSaveOutcome.Pending
    }

    fun saveResultModel(
        expectedRequestId: Long?,
        result: CloudKeySaveResult,
        hasKey: Boolean,
    ): CloudSetupSaveResultModel {
        val outcome = saveOutcome(expectedRequestId, result)
        val succeeded = outcome == CloudSaveOutcome.Succeeded
        return CloudSetupSaveResultModel(
            outcome = outcome,
            canPreview = succeeded,
            showExistingKeyCaveat = hasKey && !succeeded,
        )
    }

    fun statusModel(hasKey: Boolean, cacheFileCount: Int): CloudSetupStatusModel =
        CloudSetupStatusModel(
            configured = hasKey,
            cta = if (hasKey) CloudSetupCta.ReplaceKey else CloudSetupCta.StartSetup,
            // Cloud preview can succeed from cache even after the key is deleted.
            canPreview = hasKey || cacheFileCount > 0,
            canDeleteKey = hasKey,
            // Cache clearing is a separate action; deleting the key never implicitly clears it.
            canClearCache = cacheFileCount > 0,
        )
}
