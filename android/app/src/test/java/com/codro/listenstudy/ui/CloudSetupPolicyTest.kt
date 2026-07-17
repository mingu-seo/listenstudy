package com.codro.listenstudy.ui

import com.codro.listenstudy.playback.CloudKeySaveOutcome
import com.codro.listenstudy.playback.CloudKeySaveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSetupPolicyTest {

    private val sampleKey = "AIzaSyEXAMPLE_not_a_real_key_1234567890"

    @Test
    fun `wizard steps are ordered from optional intro to preview and done`() {
        assertEquals(
            listOf(
                CloudSetupStep.Intro,
                CloudSetupStep.DataAndCostNotice,
                CloudSetupStep.ConsoleRestrictions,
                CloudSetupStep.EnterAndSaveKey,
                CloudSetupStep.PreviewAndDone,
            ),
            CloudSetupPolicy.steps,
        )
        assertTrue(CloudSetupPolicy.isFirstStep(CloudSetupStep.Intro))
        assertTrue(CloudSetupPolicy.isLastStep(CloudSetupStep.PreviewAndDone))
    }

    @Test
    fun `next and previous step clamp at the boundaries`() {
        assertEquals(CloudSetupStep.DataAndCostNotice, CloudSetupPolicy.nextStep(CloudSetupStep.Intro))
        assertEquals(CloudSetupStep.Intro, CloudSetupPolicy.previousStep(CloudSetupStep.DataAndCostNotice))
        // Clamp at edges instead of wrapping around.
        assertEquals(CloudSetupStep.Intro, CloudSetupPolicy.previousStep(CloudSetupStep.Intro))
        assertEquals(CloudSetupStep.PreviewAndDone, CloudSetupPolicy.nextStep(CloudSetupStep.PreviewAndDone))
    }

    @Test
    fun `key can be saved only when both notices are acknowledged and key is not blank`() {
        val consent = CloudSetupConsent(
            textSentToGoogleAcknowledged = true,
            billingResponsibilityAcknowledged = true,
        )
        assertTrue(CloudSetupPolicy.canSaveKey(consent, sampleKey))
    }

    @Test
    fun `key cannot be saved when either notice is unacknowledged`() {
        assertFalse(
            CloudSetupPolicy.canSaveKey(
                CloudSetupConsent(textSentToGoogleAcknowledged = true, billingResponsibilityAcknowledged = false),
                sampleKey,
            ),
        )
        assertFalse(
            CloudSetupPolicy.canSaveKey(
                CloudSetupConsent(textSentToGoogleAcknowledged = false, billingResponsibilityAcknowledged = true),
                sampleKey,
            ),
        )
        assertFalse(
            CloudSetupPolicy.canSaveKey(CloudSetupConsent(), sampleKey),
        )
    }

    @Test
    fun `key cannot be saved when the input is blank even with full consent`() {
        val consent = CloudSetupConsent(
            textSentToGoogleAcknowledged = true,
            billingResponsibilityAcknowledged = true,
        )
        assertFalse(CloudSetupPolicy.canSaveKey(consent, ""))
        assertFalse(CloudSetupPolicy.canSaveKey(consent, "   "))
    }

    @Test
    fun `not configured state offers a start setup entry point`() {
        val model = CloudSetupPolicy.statusModel(hasKey = false, cacheFileCount = 0)

        assertFalse(model.configured)
        assertEquals(CloudSetupCta.StartSetup, model.cta)
        assertFalse(model.canDeleteKey)
        assertFalse(model.canPreview)
        assertFalse(model.canClearCache)
    }

    @Test
    fun `configured state offers replace preview and delete`() {
        val model = CloudSetupPolicy.statusModel(hasKey = true, cacheFileCount = 3)

        assertTrue(model.configured)
        assertEquals(CloudSetupCta.ReplaceKey, model.cta)
        assertTrue(model.canPreview)
        assertTrue(model.canDeleteKey)
        assertTrue(model.canClearCache)
    }

    @Test
    fun `deleting the key and clearing the cache are independent user actions`() {
        // Key removed but cached audio still present: cache clearing must remain separately available,
        // and deleting the key must not implicitly clear the cache.
        val afterKeyDeleted = CloudSetupPolicy.statusModel(hasKey = false, cacheFileCount = 5)

        assertFalse(afterKeyDeleted.canDeleteKey)
        assertTrue(afterKeyDeleted.canClearCache)
    }

    @Test
    fun `wizard state never carries the raw api key in its display model`() {
        val consent = CloudSetupConsent(
            textSentToGoogleAcknowledged = true,
            billingResponsibilityAcknowledged = true,
        )
        val state = CloudSetupWizardState(step = CloudSetupStep.EnterAndSaveKey, consent = consent)
        val status = CloudSetupPolicy.statusModel(hasKey = true, cacheFileCount = 1)

        assertFalse(state.toString().contains(sampleKey))
        assertFalse(status.toString().contains(sampleKey))
        // canSaveKey takes the key only as a transient argument, never storing it in policy state.
        assertTrue(CloudSetupPolicy.canSaveKey(state.consent, sampleKey))
        assertFalse(state.toString().contains(sampleKey))
    }

    // --- save-result presentation: the outcome is correlated to this exact save request ---
    //
    // The service reports an explicit result carrying the requestId it just processed. A result
    // from any other request — including a previous success still sitting in service state — is
    // not evidence about this request and must stay Pending.

    private fun success(requestId: Long) = CloudKeySaveResult(requestId, CloudKeySaveOutcome.Success)

    private fun failure(requestId: Long) = CloudKeySaveResult(requestId, CloudKeySaveOutcome.Failure)

    @Test
    fun `stale success from a previous request never resolves the current request`() {
        // The race this guards: the user replaces a key while request 7's success is still the
        // service's last reported result. Request 8 has not been processed yet, so the only honest
        // answer is Pending — never Succeeded.
        val stale = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = success(7L),
            hasKey = true,
        )

        assertEquals(CloudSaveOutcome.Pending, stale.outcome)
        assertFalse(stale.canPreview)
        // The previous key is still installed; say so, but never as proof the replacement worked.
        assertTrue(stale.showExistingKeyCaveat)
    }

    @Test
    fun `stale failure from a previous request also stays pending`() {
        val stale = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = failure(7L),
            hasKey = false,
        )

        assertEquals(CloudSaveOutcome.Pending, stale.outcome)
        assertFalse(stale.canPreview)
        assertFalse(stale.showExistingKeyCaveat)
    }

    @Test
    fun `matching success confirms this request and unlocks preview`() {
        val confirmed = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = success(8L),
            hasKey = true,
        )

        assertEquals(CloudSaveOutcome.Succeeded, confirmed.outcome)
        assertTrue(confirmed.canPreview)
        assertFalse(confirmed.showExistingKeyCaveat)
    }

    @Test
    fun `matching failure over an existing key never looks successful`() {
        // The old key is still installed, so hasKey stays true. That must not be read as proof
        // that this replacement request succeeded, and preview must not run against the old key.
        val failed = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = failure(8L),
            hasKey = true,
        )

        assertEquals(CloudSaveOutcome.Failed, failed.outcome)
        assertFalse(failed.canPreview)
        assertTrue(failed.showExistingKeyCaveat)
    }

    @Test
    fun `first-time save failure has no existing key to caveat`() {
        val failed = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = failure(8L),
            hasKey = false,
        )

        assertEquals(CloudSaveOutcome.Failed, failed.outcome)
        assertFalse(failed.canPreview)
        assertFalse(failed.showExistingKeyCaveat)
    }

    @Test
    fun `an unsent request has nothing to report`() {
        // No save dispatched yet: the wizard opened on the final step, or state was restored.
        val none = CloudSetupPolicy.saveResultModel(
            expectedRequestId = null,
            result = success(7L),
            hasKey = true,
        )

        assertEquals(CloudSaveOutcome.Pending, none.outcome)
        assertFalse(none.canPreview)
        assertTrue(none.showExistingKeyCaveat)
    }

    @Test
    fun `the service's empty initial result is pending`() {
        val initial = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = CloudKeySaveResult(),
            hasKey = false,
        )

        assertEquals(CloudSaveOutcome.Pending, initial.outcome)
        assertFalse(initial.canPreview)
    }

    @Test
    fun `a None outcome is pending even when the request id matches`() {
        val cleared = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = CloudKeySaveResult(8L, CloudKeySaveOutcome.None),
            hasKey = true,
        )

        assertEquals(CloudSaveOutcome.Pending, cleared.outcome)
        assertFalse(cleared.canPreview)
        assertTrue(cleared.showExistingKeyCaveat)
    }

    @Test
    fun `save result model never carries the raw api key`() {
        val model = CloudSetupPolicy.saveResultModel(
            expectedRequestId = 8L,
            result = success(8L),
            hasKey = true,
        )

        assertFalse(model.toString().contains(sampleKey))
    }

    @Test
    fun `wizard advances and records consent immutably`() {
        val start = CloudSetupWizardState()
        assertEquals(CloudSetupStep.Intro, start.step)
        assertFalse(start.consent.bothAcknowledged)

        val advanced = start.next()
        assertEquals(CloudSetupStep.DataAndCostNotice, advanced.step)
        // Original state is untouched (immutability).
        assertEquals(CloudSetupStep.Intro, start.step)

        val consented = advanced
            .acknowledgeDataTransfer(true)
            .acknowledgeBilling(true)
        assertTrue(consented.consent.bothAcknowledged)
        assertFalse(advanced.consent.bothAcknowledged)
    }
}
