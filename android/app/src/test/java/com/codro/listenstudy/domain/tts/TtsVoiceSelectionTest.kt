package com.codro.listenstudy.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceSelectionTest {
    @Test
    fun nextVoice_returns_first_voice_when_current_selection_is_empty() {
        val voices = listOf(
            TtsVoiceOption(id = "voice-a", label = "Voice A"),
            TtsVoiceOption(id = "voice-b", label = "Voice B"),
        )

        val selected = TtsVoiceSelection.nextVoiceId(voices, currentVoiceId = null)

        assertEquals("voice-a", selected)
    }

    @Test
    fun nextVoice_cycles_to_next_voice_and_wraps_to_first() {
        val voices = listOf(
            TtsVoiceOption(id = "voice-a", label = "Voice A"),
            TtsVoiceOption(id = "voice-b", label = "Voice B"),
            TtsVoiceOption(id = "voice-c", label = "Voice C"),
        )

        assertEquals("voice-c", TtsVoiceSelection.nextVoiceId(voices, currentVoiceId = "voice-b"))
        assertEquals("voice-a", TtsVoiceSelection.nextVoiceId(voices, currentVoiceId = "voice-c"))
    }

    @Test
    fun labelFor_returns_fallback_when_voice_list_is_empty_or_missing() {
        val voices = listOf(TtsVoiceOption(id = "voice-a", label = "Voice A"))

        assertEquals("기본 목소리", TtsVoiceSelection.labelFor(emptyList(), currentVoiceId = null))
        assertEquals("기본 목소리", TtsVoiceSelection.labelFor(voices, currentVoiceId = "missing"))
        assertEquals("Voice A", TtsVoiceSelection.labelFor(voices, currentVoiceId = "voice-a"))
    }

    @Test
    fun preferredVoice_uses_saved_voice_when_it_still_exists() {
        val voices = sampleVoices()

        val selected = TtsVoiceSelection.preferredVoiceId(voices, savedVoiceId = "en-offline")

        assertEquals("en-offline", selected)
    }

    @Test
    fun preferredVoice_picks_korean_samsung_offline_before_google_when_no_saved_voice() {
        val voices = sampleVoices()

        val selected = TtsVoiceSelection.preferredVoiceId(voices, savedVoiceId = "missing")

        assertEquals("ko-samsung-offline", selected)
    }

    @Test
    fun preferredVoice_uses_system_default_voice_before_forcing_specific_voice() {
        val voices = listOf(
            TtsVoiceOption(
                id = TtsVoiceSelection.SYSTEM_DEFAULT_VOICE_ID,
                label = "휴대폰 설정 목소리",
                languageTag = "ko-KR",
                isRecommended = true,
                providerRank = 0,
            ),
            TtsVoiceOption(
                id = "ko-kr-x-kob-local",
                label = "한국어 오프라인 · ko-kr-x-kob-local",
                languageTag = "ko-KR",
                isRecommended = true,
                providerRank = TtsVoiceOption.PROVIDER_RANK_GOOGLE,
            ),
        )

        val selected = TtsVoiceSelection.preferredVoiceId(voices, savedVoiceId = null)

        assertEquals(TtsVoiceSelection.SYSTEM_DEFAULT_VOICE_ID, selected)
    }

    @Test
    fun system_settings_voice_requires_engine_restart_to_reload_phone_voice_settings() {
        assertTrue(
            TtsVoiceSelection.shouldRestartEngineForSelection(
                TtsVoiceSelection.SYSTEM_DEFAULT_VOICE_ID,
            ),
        )
        assertFalse(TtsVoiceSelection.shouldRestartEngineForSelection("ko-kr-x-kob-local"))
    }

    @Test
    fun sortedForDisplay_places_recommended_korean_offline_voices_first() {
        val sorted = TtsVoiceSelection.sortedForDisplay(sampleVoices())

        assertEquals("ko-samsung-offline", sorted[0].id)
        assertEquals("ko-google-offline", sorted[1].id)
        assertTrue(sorted.first().isRecommended)
    }

    @Test
    fun filterVoices_supports_recommended_korean_offline_and_all_tabs() {
        val voices = sampleVoices()

        assertEquals(
            listOf("ko-samsung-offline", "ko-google-offline"),
            TtsVoiceSelection.filterVoices(voices, VoiceFilter.Recommended).map { it.id },
        )
        assertEquals(
            listOf("ko-samsung-offline", "ko-google-offline", "ko-online"),
            TtsVoiceSelection.filterVoices(voices, VoiceFilter.Korean).map { it.id },
        )
        assertEquals(
            listOf("ko-samsung-offline", "ko-google-offline", "en-offline"),
            TtsVoiceSelection.filterVoices(voices, VoiceFilter.Offline).map { it.id },
        )
        assertEquals(4, TtsVoiceSelection.filterVoices(voices, VoiceFilter.All).size)
    }

    @Test
    fun friendlyOptionLabels_hide_raw_voice_id_from_primary_label() {
        val voice = TtsVoiceOption(
            id = "ko-kr-x-koc-local",
            label = "Google 한국어 오프라인",
            description = "한국어 · 오프라인 가능",
            languageTag = "ko-KR",
            isNetworkConnectionRequired = false,
            isRecommended = true,
        )

        assertEquals("Google 한국어 오프라인", voice.label)
        assertEquals("한국어 · 오프라인 가능", voice.description)
        assertFalse(voice.label.contains("ko-kr-x"))
    }

    private fun sampleVoices(): List<TtsVoiceOption> = listOf(
        TtsVoiceOption(
            id = "en-offline",
            label = "영어 오프라인",
            description = "영어 · 오프라인 가능",
            languageTag = "en-US",
            isNetworkConnectionRequired = false,
            isRecommended = false,
            providerRank = 5,
        ),
        TtsVoiceOption(
            id = "ko-online",
            label = "Google 한국어 온라인 고품질",
            description = "한국어 · 온라인 필요",
            languageTag = "ko-KR",
            isNetworkConnectionRequired = true,
            isRecommended = false,
            providerRank = TtsVoiceOption.PROVIDER_RANK_GOOGLE,
        ),
        TtsVoiceOption(
            id = "ko-samsung-offline",
            label = "Samsung 한국어 오프라인",
            description = "한국어 · 오프라인 가능",
            languageTag = "ko-KR",
            isNetworkConnectionRequired = false,
            isRecommended = true,
            providerRank = TtsVoiceOption.PROVIDER_RANK_SAMSUNG,
        ),
        TtsVoiceOption(
            id = "ko-google-offline",
            label = "Google 한국어 오프라인",
            description = "한국어 · 오프라인 가능",
            languageTag = "ko-KR",
            isNetworkConnectionRequired = false,
            isRecommended = true,
            providerRank = TtsVoiceOption.PROVIDER_RANK_GOOGLE,
        ),
    )
}
