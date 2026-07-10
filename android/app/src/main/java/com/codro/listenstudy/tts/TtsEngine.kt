package com.codro.listenstudy.tts

import com.codro.listenstudy.domain.tts.TtsEngineOption
import com.codro.listenstudy.domain.tts.TtsVoiceOption

interface TtsEngine {
    fun initialize()
    fun setSpeechRate(speed: Float)
    fun setVoice(voiceId: String): Boolean
    fun setEngine(enginePackageName: String): Boolean
    fun speak(text: String, utteranceId: String)
    fun stop()
    fun shutdown()
    fun setOnDoneListener(listener: (utteranceId: String) -> Unit)
    fun setOnStatusListener(listener: (message: String) -> Unit)
    fun setOnVoicesChangedListener(listener: (voices: List<TtsVoiceOption>, selectedVoiceId: String?) -> Unit)
    fun setOnEnginesChangedListener(listener: (engines: List<TtsEngineOption>, selectedEnginePackageName: String?) -> Unit)
}
