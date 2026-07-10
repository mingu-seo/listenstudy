package com.codro.listenstudy.domain.tts

import java.util.Locale

data class TtsVoiceOption(
    val id: String,
    val label: String,
    val description: String = "",
    val languageTag: String = "",
    val isNetworkConnectionRequired: Boolean = false,
    val isRecommended: Boolean = false,
    val providerRank: Int = PROVIDER_RANK_OTHER,
) {
    val isKorean: Boolean
        get() = languageTag.lowercase(Locale.ROOT).startsWith("ko") || label.contains("한국어")

    val isOffline: Boolean
        get() = !isNetworkConnectionRequired

    companion object {
        const val PROVIDER_RANK_SAMSUNG = 1
        const val PROVIDER_RANK_GOOGLE = 2
        const val PROVIDER_RANK_OTHER = 5
    }
}

enum class VoiceFilter(
    val title: String,
) {
    Recommended("추천"),
    Korean("한국어"),
    Offline("오프라인"),
    All("전체"),
}

object TtsVoiceSelection {
    const val SYSTEM_DEFAULT_VOICE_ID = "__listenstudy_system_default_voice__"
    const val FALLBACK_LABEL = "기본 목소리"

    fun shouldRestartEngineForSelection(voiceId: String): Boolean =
        voiceId == SYSTEM_DEFAULT_VOICE_ID

    fun nextVoiceId(voices: List<TtsVoiceOption>, currentVoiceId: String?): String? {
        val sorted = sortedForDisplay(voices)
        if (sorted.isEmpty()) return null
        val currentIndex = sorted.indexOfFirst { it.id == currentVoiceId }
        return if (currentIndex < 0) {
            sorted.first().id
        } else {
            sorted[(currentIndex + 1) % sorted.size].id
        }
    }

    fun labelFor(voices: List<TtsVoiceOption>, currentVoiceId: String?): String =
        voices.firstOrNull { it.id == currentVoiceId }?.label ?: FALLBACK_LABEL

    fun optionFor(voices: List<TtsVoiceOption>, currentVoiceId: String?): TtsVoiceOption? =
        voices.firstOrNull { it.id == currentVoiceId }

    fun preferredVoiceId(voices: List<TtsVoiceOption>, savedVoiceId: String?): String? {
        if (voices.isEmpty()) return null
        if (savedVoiceId != null && voices.any { it.id == savedVoiceId }) return savedVoiceId
        if (voices.any { it.id == SYSTEM_DEFAULT_VOICE_ID }) return SYSTEM_DEFAULT_VOICE_ID
        return sortedForDisplay(voices).firstOrNull()?.id
    }

    fun sortedForDisplay(voices: List<TtsVoiceOption>): List<TtsVoiceOption> =
        voices.sortedWith(
            compareByDescending<TtsVoiceOption> { it.isRecommended }
                .thenByDescending { it.isKorean }
                .thenByDescending { it.isOffline }
                .thenBy { it.providerRank }
                .thenBy { it.label }
                .thenBy { it.id },
        )

    fun filterVoices(
        voices: List<TtsVoiceOption>,
        filter: VoiceFilter,
    ): List<TtsVoiceOption> {
        val sorted = sortedForDisplay(voices)
        return when (filter) {
            VoiceFilter.Recommended -> sorted.filter { it.isRecommended }
            VoiceFilter.Korean -> sorted.filter { it.isKorean }
            VoiceFilter.Offline -> sorted.filter { it.isOffline }
            VoiceFilter.All -> sorted
        }.ifEmpty { sorted }
    }
}
