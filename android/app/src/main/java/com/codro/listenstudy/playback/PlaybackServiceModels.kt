package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackState
import com.codro.listenstudy.domain.tts.CloudErrorReport
import com.codro.listenstudy.domain.tts.CloudVoice
import com.codro.listenstudy.domain.tts.CloudVoiceCatalog
import com.codro.listenstudy.domain.tts.PlaybackMode
import com.codro.listenstudy.domain.tts.TtsEngineOption
import com.codro.listenstudy.domain.tts.TtsVoiceOption
import com.codro.listenstudy.tts.CloudCacheStats

/** What the service determined about one specific [ServiceCommand.SaveApiKey] request. */
enum class CloudKeySaveOutcome { None, Success, Failure }

/**
 * The service's explicit, correlated verdict on a key-save request. [requestId] identifies the
 * request the service actually processed, so a caller can tell its own result apart from a
 * previous one still sitting in service state. A status message can never carry that distinction.
 */
data class CloudKeySaveResult(
    val requestId: Long = NO_REQUEST,
    val outcome: CloudKeySaveOutcome = CloudKeySaveOutcome.None,
) {
    companion object {
        /** Id reserved for "no save request has been processed"; real ids start at 1. */
        const val NO_REQUEST: Long = 0L
    }
}

/** The correlated verdict plus the status message a single key-save attempt should produce. */
data class CloudKeySaveResolution(val result: CloudKeySaveResult, val message: String)

/**
 * Pure mapping from the outcome of a key-save attempt to UI state. Kept out of the service so the
 * decision can be tested without Keystore, SharedPreferences or a Looper.
 */
object CloudKeySavePolicy {
    fun resolve(requestId: Long, isBlank: Boolean, saved: Boolean): CloudKeySaveResolution {
        val succeeded = !isBlank && saved
        return CloudKeySaveResolution(
            result = CloudKeySaveResult(
                requestId = requestId,
                outcome = if (succeeded) CloudKeySaveOutcome.Success else CloudKeySaveOutcome.Failure,
            ),
            message = when {
                isBlank -> "입력한 API 키가 비어 있습니다."
                succeeded -> "Google Cloud API 키를 비공개 앱 저장소에 저장했습니다."
                else -> "API 키를 안전하게 저장하지 못했습니다. 다시 시도해 주세요."
            },
        )
    }
}

/**
 * When the cloud error panel is shown and cleared.
 *
 * Split out of the service so the rule is checkable without a Looper or a real Google failure. The
 * panel is a recovery surface, not a notification: it stays until the user acts or a fresh attempt
 * at producing audio supersedes it.
 */
object CloudErrorUiPolicy {
    /** The mode "휴대폰 음성으로 듣기" falls back to. */
    val ON_DEVICE_FALLBACK: PlaybackMode = PlaybackMode.ON_DEVICE

    /**
     * A cloud error is meaningless once the phone voice is in use, so a late report from an
     * already-abandoned cloud request is dropped rather than shown against local playback.
     */
    fun shouldShow(mode: PlaybackMode): Boolean = mode != PlaybackMode.ON_DEVICE

    /**
     * True when accepting [command] is a fresh attempt at producing audio, or an explicit dismissal.
     *
     * Pause is excluded on purpose: a failure already pauses playback, so clearing on Pause would
     * erase the panel at the moment it appears.
     */
    fun clearsCloudError(command: ServiceCommand): Boolean = when (command) {
        ServiceCommand.Play,
        ServiceCommand.Previous,
        ServiceCommand.Next,
        ServiceCommand.RetryCloudSentence,
        ServiceCommand.UseOnDeviceVoiceForCurrentSentence,
        ServiceCommand.DismissCloudError,
        ServiceCommand.PreviewCloudVoice,
        ServiceCommand.DeleteApiKey,
        ServiceCommand.ClearCache,
        is ServiceCommand.Jump,
        is ServiceCommand.SelectPlaybackMode,
        is ServiceCommand.SelectCloudVoice,
        is ServiceCommand.SaveApiKey,
        is ServiceCommand.ReplaceDocument,
        -> true

        ServiceCommand.Pause,
        ServiceCommand.OpenLibrary,
        is ServiceCommand.SetSpeed,
        is ServiceCommand.ShowStatus,
        is ServiceCommand.SelectOnDeviceVoice,
        is ServiceCommand.SelectEngine,
        is ServiceCommand.PreviewLocalVoice,
        -> false
    }
}

data class PlaybackServiceUiState(
    val playback: PlaybackState = PlaybackState(),
    /**
     * The classified failure of the current sentence, or null when there is none. Carries only the
     * category and its Korean copy — never the HTTP status, response body or key.
     */
    val cloudError: CloudErrorReport? = null,
    val ttsStatus: String = "TTS 준비 전",
    val voiceOptions: List<TtsVoiceOption> = emptyList(),
    val selectedVoiceId: String? = null,
    val engineOptions: List<TtsEngineOption> = emptyList(),
    val selectedEnginePackageName: String? = null,
    val playbackMode: PlaybackMode = PlaybackMode.ON_DEVICE,
    val cloudVoice: CloudVoice = CloudVoiceCatalog.defaultVoice,
    val hasCloudApiKey: Boolean = false,
    val cloudKeySaveResult: CloudKeySaveResult = CloudKeySaveResult(),
    val cloudCacheStats: CloudCacheStats = CloudCacheStats(0, 0),
    val documentId: String? = null,
    val documentTitle: String = "샘플 학습 자료",
)

sealed interface ServiceCommand {
    data object Play : ServiceCommand
    data object Pause : ServiceCommand
    data object Previous : ServiceCommand
    data object Next : ServiceCommand
    data class Jump(val index: Int) : ServiceCommand
    data class SetSpeed(val speed: Float) : ServiceCommand
    data object OpenLibrary : ServiceCommand
    data class SelectOnDeviceVoice(val voiceId: String) : ServiceCommand
    data class SelectEngine(val packageName: String) : ServiceCommand
    data class PreviewLocalVoice(val voiceId: String?) : ServiceCommand
    data class SelectPlaybackMode(val mode: PlaybackMode) : ServiceCommand
    data class SelectCloudVoice(val voiceId: String) : ServiceCommand
    /**
     * [requestId] correlates the service's [CloudKeySaveResult] back to this exact request.
     *
     * [value] is a secret. The generated `toString` would print it verbatim into logs, crash
     * reports and debugger output, so it is overridden to redact the key while still identifying
     * the command and its request id.
     */
    data class SaveApiKey(val value: String, val requestId: Long) : ServiceCommand {
        override fun toString(): String = "SaveApiKey(value=<redacted>, requestId=$requestId)"
    }
    data object DeleteApiKey : ServiceCommand
    data object ClearCache : ServiceCommand
    data object PreviewCloudVoice : ServiceCommand

    /** Re-speaks the sentence that failed, from the same position. Only ever user-initiated. */
    data object RetryCloudSentence : ServiceCommand

    /** Switches to phone TTS and continues from the sentence that failed. */
    data object UseOnDeviceVoiceForCurrentSentence : ServiceCommand

    /** Hides the error panel without retrying or switching. */
    data object DismissCloudError : ServiceCommand
    data class ShowStatus(val message: String) : ServiceCommand
    data class ReplaceDocument(
        val documentId: String?,
        val title: String,
        val sentences: List<String>,
        val index: Int = 0,
        val speed: Float = 1f,
    ) : ServiceCommand
}
