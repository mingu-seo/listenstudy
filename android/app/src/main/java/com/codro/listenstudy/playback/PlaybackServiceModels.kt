package com.codro.listenstudy.playback

import com.codro.listenstudy.domain.player.PlaybackState
import com.codro.listenstudy.domain.tts.CloudVoice
import com.codro.listenstudy.domain.tts.CloudVoiceCatalog
import com.codro.listenstudy.domain.tts.PlaybackMode
import com.codro.listenstudy.domain.tts.TtsEngineOption
import com.codro.listenstudy.domain.tts.TtsVoiceOption
import com.codro.listenstudy.tts.CloudCacheStats

data class PlaybackServiceUiState(
    val playback: PlaybackState = PlaybackState(),
    val ttsStatus: String = "TTS 준비 전",
    val voiceOptions: List<TtsVoiceOption> = emptyList(),
    val selectedVoiceId: String? = null,
    val engineOptions: List<TtsEngineOption> = emptyList(),
    val selectedEnginePackageName: String? = null,
    val playbackMode: PlaybackMode = PlaybackMode.ON_DEVICE,
    val cloudVoice: CloudVoice = CloudVoiceCatalog.defaultVoice,
    val hasCloudApiKey: Boolean = false,
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
    data class SaveApiKey(val value: String) : ServiceCommand
    data object DeleteApiKey : ServiceCommand
    data object ClearCache : ServiceCommand
    data object PreviewCloudVoice : ServiceCommand
    data class ShowStatus(val message: String) : ServiceCommand
    data class ReplaceDocument(
        val documentId: String?,
        val title: String,
        val sentences: List<String>,
        val index: Int = 0,
        val speed: Float = 1f,
    ) : ServiceCommand
}
