package com.codro.listenstudy.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsEngineSelectionTest {
    @Test
    fun preferredEngine_uses_saved_engine_when_it_still_exists() {
        val engines = sampleEngines(defaultPackage = TtsEngineOption.SAMSUNG_ENGINE_PACKAGE)

        val selected = TtsEngineSelection.preferredEnginePackage(
            engines = engines,
            savedEnginePackage = TtsEngineOption.GOOGLE_ENGINE_PACKAGE,
        )

        assertEquals(TtsEngineOption.GOOGLE_ENGINE_PACKAGE, selected)
    }

    @Test
    fun preferredEngine_uses_phone_default_before_google() {
        val engines = sampleEngines(defaultPackage = TtsEngineOption.SAMSUNG_ENGINE_PACKAGE)

        val selected = TtsEngineSelection.preferredEnginePackage(
            engines = engines,
            savedEnginePackage = null,
        )

        assertEquals(TtsEngineOption.SAMSUNG_ENGINE_PACKAGE, selected)
    }

    @Test
    fun sortedForDisplay_places_default_then_samsung_then_google() {
        val engines = listOf(
            engine(TtsEngineOption.GOOGLE_ENGINE_PACKAGE, "Google Speech Services"),
            engine("com.vendor.tts", "Vendor TTS"),
            engine(TtsEngineOption.SAMSUNG_ENGINE_PACKAGE, "Samsung text-to-speech"),
        )

        val sorted = TtsEngineSelection.sortedForDisplay(engines)

        assertEquals(TtsEngineOption.SAMSUNG_ENGINE_PACKAGE, sorted[0].packageName)
        assertEquals(TtsEngineOption.GOOGLE_ENGINE_PACKAGE, sorted[1].packageName)
    }

    @Test
    fun providerLabel_does_not_treat_generic_korean_voice_id_as_google() {
        val provider = TtsEngineSelection.providerLabelFor(
            packageName = TtsEngineOption.SAMSUNG_ENGINE_PACKAGE,
            label = "Samsung text-to-speech",
        )

        assertEquals("Samsung", provider)
    }

    @Test
    fun labelFor_returns_engine_label_or_fallback() {
        val engines = sampleEngines(defaultPackage = TtsEngineOption.SAMSUNG_ENGINE_PACKAGE)

        assertEquals("Samsung text-to-speech", TtsEngineSelection.labelFor(engines, TtsEngineOption.SAMSUNG_ENGINE_PACKAGE))
        assertEquals("기본 TTS 엔진", TtsEngineSelection.labelFor(engines, "missing"))
    }

    private fun sampleEngines(defaultPackage: String): List<TtsEngineOption> = listOf(
        engine(
            packageName = TtsEngineOption.GOOGLE_ENGINE_PACKAGE,
            label = "Google Speech Services",
            isDefault = defaultPackage == TtsEngineOption.GOOGLE_ENGINE_PACKAGE,
        ),
        engine(
            packageName = TtsEngineOption.SAMSUNG_ENGINE_PACKAGE,
            label = "Samsung text-to-speech",
            isDefault = defaultPackage == TtsEngineOption.SAMSUNG_ENGINE_PACKAGE,
        ),
        engine(
            packageName = "com.vendor.tts",
            label = "Vendor TTS",
            isDefault = defaultPackage == "com.vendor.tts",
        ),
    )

    private fun engine(
        packageName: String,
        label: String,
        isDefault: Boolean = false,
    ): TtsEngineOption = TtsEngineOption(
        packageName = packageName,
        label = label,
        isDefault = isDefault,
        providerRank = TtsEngineSelection.providerRankFor(packageName, label, isDefault),
    )
}
