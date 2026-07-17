package com.codro.listenstudy.domain.tts

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * What a Google Cloud TTS failure means to the user, in terms of what they can do about it.
 *
 * Deliberately grouped by user action rather than by transport detail: two failures that need the
 * same fix share a category even when their HTTP status differs, and one status can land in several
 * categories (Google answers an invalid key, a disabled API and an exhausted quota all with 403).
 */
enum class CloudErrorCategory {
    /** No key saved, or Google rejected the key itself. */
    AuthKeyInvalid,

    /** The key is real but may not call this API — API disabled, or an application/API restriction. */
    PermissionRestricted,

    /** Quota exhausted or billing not usable. Retrying costs money and fails again. */
    QuotaOrBilling,

    /** The request never reached Google. */
    Network,

    /** Google was reachable but did not answer in time. */
    Timeout,

    /** Google accepted the request and failed on its own side. Retrying is likely to work. */
    ServerTemporary,

    /** Google understood the request and rejected it — malformed body, unsupported voice/language. */
    InvalidRequest,

    /** Genuinely unrecognized. Never guessed into a more specific category. */
    Unknown,
}

/** A recovery the user can take from the error panel. */
enum class CloudRecoveryAction(val label: String) {
    Retry("다시 시도"),
    OpenCloudSettings("설정 열기"),
    UseOnDeviceVoice("휴대폰 음성으로 듣기"),
}

/**
 * Everything the UI needs to present one failure. Built only from a [CloudErrorCategory], never from
 * the underlying throwable, so no HTTP status, response body, API key or internal exception text can
 * reach the screen by construction rather than by careful redaction.
 */
data class CloudErrorReport(
    val category: CloudErrorCategory,
    val title: String,
    val description: String,
    val actions: List<CloudRecoveryAction>,
)

object CloudTtsErrorPolicy {

    /**
     * Maps a synthesis failure onto the action the user must take.
     *
     * Cancellation is normal control flow (stop/skip/mode change invalidated the request), not a
     * failure, so it is rethrown rather than classified — otherwise every interruption would raise an
     * error panel.
     */
    fun classify(failure: Throwable): CloudErrorCategory {
        if (failure is CancellationException) throw failure
        return when (failure) {
            // Raised only when the cache was unusable AND no key was available to re-synthesize, so
            // the actionable problem is the key, not the cache.
            is CloudTtsFailure.MissingApiKey, is CloudTtsFailure.CacheCorrupt -> CloudErrorCategory.AuthKeyInvalid
            // Google answered 2xx with an empty or non-audio payload; a retry usually succeeds.
            is CloudTtsFailure.InvalidAudio -> CloudErrorCategory.ServerTemporary
            is CloudTtsFailure.Http -> httpCategory(failure.statusCode, failure.reason)
            is SocketTimeoutException -> CloudErrorCategory.Timeout
            is InterruptedIOException -> CloudErrorCategory.Timeout
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
            is SSLException,
            -> CloudErrorCategory.Network
            is IOException -> CloudErrorCategory.Network
            else -> CloudErrorCategory.Unknown
        }
    }

    private fun httpCategory(status: Int, reason: String?): CloudErrorCategory {
        val hint = reason.orEmpty().lowercase()
        return when {
            status == 401 -> CloudErrorCategory.AuthKeyInvalid
            // Google reports a malformed/expired key as 400 INVALID_ARGUMENT or 403, never 401, so the
            // reason has to outrank the status here or the user is told their text was wrong.
            hint.mentions("api_key_invalid", "api key not valid", "api key expired", "invalid api key") ->
                CloudErrorCategory.AuthKeyInvalid
            status == 429 -> CloudErrorCategory.QuotaOrBilling
            hint.mentions("billing", "resource_exhausted", "quota") -> CloudErrorCategory.QuotaOrBilling
            status == 403 -> CloudErrorCategory.PermissionRestricted
            status == 408 -> CloudErrorCategory.Timeout
            status == 400 || status == 404 -> CloudErrorCategory.InvalidRequest
            status in 500..599 -> CloudErrorCategory.ServerTemporary
            else -> CloudErrorCategory.Unknown
        }
    }

    private fun String.mentions(vararg needles: String) = needles.any { contains(it) }

    /**
     * Recovery actions, most useful first. Phone TTS is always offered as a guaranteed way out.
     *
     * There is no automatic retry anywhere in the app: every retry is a billed request, so only the
     * user — who alone knows whether they fixed the key, the quota or the network — starts one. That
     * is why Retry is offered only where an identical request could actually succeed unchanged, and
     * is withheld from the auth/permission/quota/invalid categories entirely.
     */
    fun actions(category: CloudErrorCategory): List<CloudRecoveryAction> = when (category) {
        CloudErrorCategory.Network,
        CloudErrorCategory.Timeout,
        CloudErrorCategory.ServerTemporary,
        CloudErrorCategory.Unknown,
        -> listOf(CloudRecoveryAction.Retry, CloudRecoveryAction.UseOnDeviceVoice)

        CloudErrorCategory.AuthKeyInvalid,
        CloudErrorCategory.PermissionRestricted,
        CloudErrorCategory.InvalidRequest,
        -> listOf(CloudRecoveryAction.OpenCloudSettings, CloudRecoveryAction.UseOnDeviceVoice)

        // Retry is omitted from the lead position on purpose: it would be billed and fail again.
        CloudErrorCategory.QuotaOrBilling ->
            listOf(CloudRecoveryAction.UseOnDeviceVoice, CloudRecoveryAction.OpenCloudSettings)
    }

    fun report(category: CloudErrorCategory): CloudErrorReport = CloudErrorReport(
        category = category,
        title = title(category),
        description = description(category),
        actions = actions(category),
    )

    /** Convenience for callers holding a throwable; cancellation still propagates. */
    fun reportFor(failure: Throwable): CloudErrorReport = report(classify(failure))

    private fun title(category: CloudErrorCategory): String = when (category) {
        CloudErrorCategory.AuthKeyInvalid -> "API 키를 확인해 주세요"
        CloudErrorCategory.PermissionRestricted -> "API 사용 권한이 없습니다"
        CloudErrorCategory.QuotaOrBilling -> "사용량 한도를 넘었습니다"
        CloudErrorCategory.Network -> "인터넷에 연결되지 않았습니다"
        CloudErrorCategory.Timeout -> "응답이 너무 늦습니다"
        CloudErrorCategory.ServerTemporary -> "Google 서버가 불안정합니다"
        CloudErrorCategory.InvalidRequest -> "이 음성을 쓸 수 없습니다"
        CloudErrorCategory.Unknown -> "음성을 재생하지 못했습니다"
    }

    private fun description(category: CloudErrorCategory): String = when (category) {
        CloudErrorCategory.AuthKeyInvalid ->
            "저장된 키가 없거나 올바르지 않습니다. 설정에서 키를 다시 저장해 주세요."
        CloudErrorCategory.PermissionRestricted ->
            "Google Cloud에서 이 키로 음성 합성을 쓸 수 있는지, 키 제한 설정을 확인해 주세요."
        CloudErrorCategory.QuotaOrBilling ->
            "Google Cloud 결제와 사용량 한도를 확인해 주세요. 지금은 휴대폰 음성으로 들을 수 있습니다."
        CloudErrorCategory.Network ->
            "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
        CloudErrorCategory.Timeout ->
            "네트워크가 느려 음성을 받지 못했습니다. 잠시 후 다시 시도해 주세요."
        CloudErrorCategory.ServerTemporary ->
            "일시적인 오류입니다. 잠시 후 다시 시도해 주세요."
        CloudErrorCategory.InvalidRequest ->
            "선택한 음성이나 언어를 지원하지 않습니다. 설정에서 다른 음성을 골라 주세요."
        CloudErrorCategory.Unknown ->
            "알 수 없는 오류입니다. 다시 시도하거나 휴대폰 음성으로 들어 주세요."
    }
}
