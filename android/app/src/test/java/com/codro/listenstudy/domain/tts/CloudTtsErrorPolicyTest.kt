package com.codro.listenstudy.domain.tts

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The classifier is the single place that decides what a Google Cloud TTS failure means to a user,
 * so it is tested directly rather than through the engine's threads and Android types.
 */
class CloudTtsErrorPolicyTest {

    // ---- classification -------------------------------------------------------------------

    @Test fun `missing or blank api key is an auth failure`() {
        assertEquals(CloudErrorCategory.AuthKeyInvalid, CloudTtsErrorPolicy.classify(CloudTtsFailure.MissingApiKey()))
    }

    @Test fun `unauthorized status is an auth failure`() {
        assertEquals(CloudErrorCategory.AuthKeyInvalid, CloudTtsErrorPolicy.classify(http(401)))
    }

    @Test fun `google reports an invalid api key as a bad request and it stays an auth failure`() {
        // Google answers a malformed key with HTTP 400 + API_KEY_INVALID, not 401. Classifying on the
        // status alone would tell the user their text was wrong when their key is.
        assertEquals(
            CloudErrorCategory.AuthKeyInvalid,
            CloudTtsErrorPolicy.classify(http(400, """{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid. Please pass a valid API key."}}""")),
        )
        assertEquals(
            CloudErrorCategory.AuthKeyInvalid,
            CloudTtsErrorPolicy.classify(http(403, """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}""")),
        )
    }

    @Test fun `android client restriction and disabled api are permission failures`() {
        assertEquals(
            CloudErrorCategory.PermissionRestricted,
            CloudTtsErrorPolicy.classify(http(403, """{"error":{"message":"Requests from this Android client application are blocked."}}""")),
        )
        assertEquals(
            CloudErrorCategory.PermissionRestricted,
            CloudTtsErrorPolicy.classify(http(403, """{"error":{"status":"PERMISSION_DENIED","details":[{"reason":"SERVICE_DISABLED"}]}}""")),
        )
        assertEquals(CloudErrorCategory.PermissionRestricted, CloudTtsErrorPolicy.classify(http(403)))
    }

    @Test fun `quota and billing failures are separated from plain permission failures`() {
        assertEquals(CloudErrorCategory.QuotaOrBilling, CloudTtsErrorPolicy.classify(http(429)))
        assertEquals(
            CloudErrorCategory.QuotaOrBilling,
            CloudTtsErrorPolicy.classify(http(403, """{"error":{"details":[{"reason":"BILLING_DISABLED"}]}}""")),
        )
        assertEquals(
            CloudErrorCategory.QuotaOrBilling,
            CloudTtsErrorPolicy.classify(http(403, """{"error":{"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded"}}""")),
        )
    }

    @Test fun `connection failures are network failures`() {
        assertEquals(CloudErrorCategory.Network, CloudTtsErrorPolicy.classify(UnknownHostException("texttospeech.googleapis.com")))
        assertEquals(CloudErrorCategory.Network, CloudTtsErrorPolicy.classify(ConnectException("ECONNREFUSED")))
        assertEquals(CloudErrorCategory.Network, CloudTtsErrorPolicy.classify(SSLException("handshake")))
        assertEquals(CloudErrorCategory.Network, CloudTtsErrorPolicy.classify(IOException("unexpected end of stream")))
    }

    @Test fun `timeouts are distinguished from generic network failures`() {
        assertEquals(CloudErrorCategory.Timeout, CloudTtsErrorPolicy.classify(SocketTimeoutException("timeout")))
        assertEquals(CloudErrorCategory.Timeout, CloudTtsErrorPolicy.classify(http(408)))
    }

    @Test fun `google server side failures are temporary`() {
        for (status in listOf(500, 502, 503, 504)) {
            assertEquals("HTTP $status", CloudErrorCategory.ServerTemporary, CloudTtsErrorPolicy.classify(http(status)))
        }
        // Google occasionally returns 200 with an empty/corrupt payload; retrying is the right move.
        assertEquals(CloudErrorCategory.ServerTemporary, CloudTtsErrorPolicy.classify(CloudTtsFailure.InvalidAudio()))
    }

    @Test fun `malformed requests and unsupported voices are invalid request failures`() {
        assertEquals(CloudErrorCategory.InvalidRequest, CloudTtsErrorPolicy.classify(http(400)))
        assertEquals(
            CloudErrorCategory.InvalidRequest,
            CloudTtsErrorPolicy.classify(http(400, """{"error":{"message":"This voice does not support the language code ko-KR."}}""")),
        )
        assertEquals(CloudErrorCategory.InvalidRequest, CloudTtsErrorPolicy.classify(http(404)))
    }

    @Test fun `corrupt cache without a key is reported as a key problem`() {
        // The synthesizer only raises this when the cache was unusable AND no key is available to
        // re-synthesize, so the actionable problem is the missing key.
        assertEquals(CloudErrorCategory.AuthKeyInvalid, CloudTtsErrorPolicy.classify(CloudTtsFailure.CacheCorrupt()))
    }

    @Test fun `unrecognized failures fall back to unknown rather than a wrong guess`() {
        assertEquals(CloudErrorCategory.Unknown, CloudTtsErrorPolicy.classify(RuntimeException("boom")))
        assertEquals(CloudErrorCategory.Unknown, CloudTtsErrorPolicy.classify(http(418)))
    }

    @Test fun `cancellation is never classified as an error`() {
        // Cancellation is normal control flow (user pressed stop). Turning it into an error card
        // would show a scary panel every time playback is interrupted.
        assertThrows(CancellationException::class.java) {
            CloudTtsErrorPolicy.classify(CancellationException("stopped"))
        }
    }

    // ---- recovery actions -----------------------------------------------------------------

    @Test fun `transient failures offer retry then phone voice`() {
        for (category in listOf(CloudErrorCategory.Network, CloudErrorCategory.Timeout, CloudErrorCategory.ServerTemporary)) {
            assertEquals(
                "$category",
                listOf(CloudRecoveryAction.Retry, CloudRecoveryAction.UseOnDeviceVoice),
                CloudTtsErrorPolicy.actions(category),
            )
        }
    }

    @Test fun `auth and permission failures offer settings then phone voice and never a pointless retry`() {
        for (category in listOf(CloudErrorCategory.AuthKeyInvalid, CloudErrorCategory.PermissionRestricted)) {
            assertEquals(
                "$category",
                listOf(CloudRecoveryAction.OpenCloudSettings, CloudRecoveryAction.UseOnDeviceVoice),
                CloudTtsErrorPolicy.actions(category),
            )
        }
    }

    @Test fun `quota failures lead with the phone voice because retrying costs money and fails again`() {
        assertEquals(
            listOf(CloudRecoveryAction.UseOnDeviceVoice, CloudRecoveryAction.OpenCloudSettings),
            CloudTtsErrorPolicy.actions(CloudErrorCategory.QuotaOrBilling),
        )
    }

    @Test fun `invalid request failures point at settings and the phone voice`() {
        assertEquals(
            listOf(CloudRecoveryAction.OpenCloudSettings, CloudRecoveryAction.UseOnDeviceVoice),
            CloudTtsErrorPolicy.actions(CloudErrorCategory.InvalidRequest),
        )
    }

    @Test fun `unknown failures allow one manual retry and the phone voice`() {
        assertEquals(
            listOf(CloudRecoveryAction.Retry, CloudRecoveryAction.UseOnDeviceVoice),
            CloudTtsErrorPolicy.actions(CloudErrorCategory.Unknown),
        )
    }

    @Test fun `every category offers the phone voice as a guaranteed way out`() {
        for (category in CloudErrorCategory.entries) {
            assertTrue("$category", CloudRecoveryAction.UseOnDeviceVoice in CloudTtsErrorPolicy.actions(category))
        }
    }

    @Test fun `retry is never offered where an unchanged request cannot succeed`() {
        // Retrying an auth/permission/quota/invalid failure would bill the user for a request that
        // cannot possibly succeed until they change something, so the button is not even present.
        for (category in listOf(
            CloudErrorCategory.AuthKeyInvalid,
            CloudErrorCategory.PermissionRestricted,
            CloudErrorCategory.QuotaOrBilling,
            CloudErrorCategory.InvalidRequest,
        )) {
            assertFalse("$category", CloudRecoveryAction.Retry in CloudTtsErrorPolicy.actions(category))
        }
    }

    // ---- user-facing message safety -------------------------------------------------------

    @Test fun `every category has a short korean title and description`() {
        for (category in CloudErrorCategory.entries) {
            val model = CloudTtsErrorPolicy.report(category)
            assertEquals(category, model.category)
            assertTrue("$category title", model.title.isNotBlank() && model.title.length <= 30)
            assertTrue("$category description", model.description.isNotBlank() && model.description.length <= 90)
            assertEquals(CloudTtsErrorPolicy.actions(category), model.actions)
        }
    }

    @Test fun `quota message tells the user to check billing and quota`() {
        val model = CloudTtsErrorPolicy.report(CloudErrorCategory.QuotaOrBilling)
        assertTrue(model.description.contains("결제") || model.description.contains("사용량"))
    }

    @Test fun `messages never leak the api key the http status the response body or exception names`() {
        val key = "AIzaSyD-SUPER-SECRET-KEY"
        val body = """{"error":{"code":403,"message":"Requests from this Android client application are blocked."}}"""
        val failures = listOf<Throwable>(
            http(403, body),
            http(400, """{"error":{"message":"API key not valid. Please pass a valid API key. key=$key"}}"""),
            http(429, """{"error":{"message":"Quota exceeded for quota metric 'Characters'"}}"""),
            UnknownHostException("texttospeech.googleapis.com"),
            SocketTimeoutException("Read timed out"),
            RuntimeException("java.lang.IllegalStateException: key=$key"),
        )

        for (failure in failures) {
            val model = CloudTtsErrorPolicy.report(CloudTtsErrorPolicy.classify(failure))
            val text = "${model.title} ${model.description}"
            assertFalse(text, text.contains(key))
            assertFalse(text, text.contains("AIza"))
            assertFalse(text, text.contains("HTTP"))
            assertFalse(text, text.contains("403") || text.contains("400") || text.contains("429"))
            assertFalse(text, text.contains("Exception"))
            assertFalse(text, text.contains("blocked"))
            assertFalse(text, text.contains("texttospeech"))
            assertFalse(text, text.contains("googleapis"))
        }
    }

    @Test fun `http failure keeps its diagnostic reason out of toString and message`() {
        val failure = http(403, """{"error":{"message":"Requests from this Android client application are blocked."}}""")
        assertFalse(failure.toString().contains("blocked"))
        assertFalse(failure.message.orEmpty().contains("blocked"))
    }

    private fun http(status: Int, body: String = "") = CloudTtsFailure.Http(status, CloudTtsHttpError.reason(body))
}
