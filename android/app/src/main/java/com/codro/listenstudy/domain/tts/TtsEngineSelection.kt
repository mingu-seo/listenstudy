package com.codro.listenstudy.domain.tts

import java.util.Locale

data class TtsEngineOption(
    val packageName: String,
    val label: String,
    val isDefault: Boolean = false,
    val providerRank: Int = PROVIDER_RANK_OTHER,
    val discoverySource: String = DISCOVERY_SOURCE_TTS_SERVICE,
) {
    val isSamsung: Boolean
        get() = packageName.lowercase(Locale.ROOT).contains("samsung") || label.lowercase(Locale.ROOT).contains("samsung")

    val isGoogle: Boolean
        get() = packageName == GOOGLE_ENGINE_PACKAGE || label.lowercase(Locale.ROOT).contains("google")

    companion object {
        const val GOOGLE_ENGINE_PACKAGE = "com.google.android.tts"
        const val SAMSUNG_ENGINE_PACKAGE = "com.samsung.SMT"
        const val PROVIDER_RANK_DEFAULT = 0
        const val PROVIDER_RANK_SAMSUNG = 1
        const val PROVIDER_RANK_GOOGLE = 2
        const val PROVIDER_RANK_OTHER = 5
        const val DISCOVERY_SOURCE_TTS_SERVICE = "TTS 서비스 확인됨"
    }
}

object TtsEngineSelection {
    const val FALLBACK_LABEL = "기본 TTS 엔진"
    const val SYSTEM_DEFAULT_ENGINE_ID = "__listenstudy_system_default_engine__"

    fun preferredEnginePackage(
        engines: List<TtsEngineOption>,
        savedEnginePackage: String?,
    ): String? {
        if (engines.isEmpty()) return null
        if (savedEnginePackage != null && engines.any { it.packageName == savedEnginePackage }) return savedEnginePackage
        return sortedForDisplay(engines).firstOrNull()?.packageName
    }

    fun sortedForDisplay(engines: List<TtsEngineOption>): List<TtsEngineOption> =
        engines.sortedWith(
            compareByDescending<TtsEngineOption> { it.isDefault }
                .thenBy { it.providerRank }
                .thenBy { it.label }
                .thenBy { it.packageName },
        )

    fun labelFor(engines: List<TtsEngineOption>, currentPackageName: String?): String =
        engines.firstOrNull { it.packageName == currentPackageName }?.label ?: FALLBACK_LABEL

    fun providerLabelFor(packageName: String, label: String): String {
        val normalizedPackage = packageName.lowercase(Locale.ROOT)
        val normalizedLabel = label.lowercase(Locale.ROOT)
        return when {
            "samsung" in normalizedPackage || "samsung" in normalizedLabel || packageName == TtsEngineOption.SAMSUNG_ENGINE_PACKAGE -> "Samsung"
            packageName == TtsEngineOption.GOOGLE_ENGINE_PACKAGE || "google" in normalizedPackage || "google" in normalizedLabel -> "Google"
            else -> label.ifBlank { "기기" }
        }
    }

    fun providerRankFor(packageName: String, label: String, isDefault: Boolean): Int {
        if (isDefault) return TtsEngineOption.PROVIDER_RANK_DEFAULT
        val provider = providerLabelFor(packageName, label)
        return when (provider) {
            "Samsung" -> TtsEngineOption.PROVIDER_RANK_SAMSUNG
            "Google" -> TtsEngineOption.PROVIDER_RANK_GOOGLE
            else -> TtsEngineOption.PROVIDER_RANK_OTHER
        }
    }
}
