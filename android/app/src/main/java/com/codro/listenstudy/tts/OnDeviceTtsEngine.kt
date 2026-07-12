package com.codro.listenstudy.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.codro.listenstudy.domain.tts.TtsEngineOption
import com.codro.listenstudy.domain.tts.TtsEngineSelection
import com.codro.listenstudy.domain.tts.TtsSelectionMigration
import com.codro.listenstudy.domain.tts.TtsVoiceOption
import com.codro.listenstudy.domain.tts.TtsVoiceSelection
import java.util.Locale

class OnDeviceTtsEngine(
    context: Context,
) : TtsEngine {
    private data class PendingSpeech(
        val text: String,
        val utteranceId: String,
    )

    private var onDone: (String) -> Unit = {}
    private var onError: (String) -> Unit = {}
    private var onStatus: (String) -> Unit = {}
    private var onVoicesChanged: (List<TtsVoiceOption>, String?) -> Unit = { _, _ -> }
    private var onEnginesChanged: (List<TtsEngineOption>, String?) -> Unit = { _, _ -> }
    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false
    private var currentRate: Float = 1.0f
    private var pendingSpeech: PendingSpeech? = null
    private var voiceOptions: List<TtsVoiceOption> = emptyList()

    private var engineOptions: List<TtsEngineOption> = emptyList()
    private var selectedVoiceId: String? = null
    private var selectedEnginePackageName: String? = null
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun initialize() {
        if (tts != null) return
        migrateLegacySelectionToSystemDefaultIfNeeded()
        startTextToSpeech(enginePackageName = preferences.getString(KEY_SELECTED_ENGINE_PACKAGE, null), persistEngine = false)
    }

    private fun migrateLegacySelectionToSystemDefaultIfNeeded() {
        val appliedVersion = preferences.getInt(KEY_TTS_SELECTION_MIGRATION_VERSION, 0)
        if (!TtsSelectionMigration.shouldResetSavedSelection(appliedVersion)) return

        preferences.edit()
            .remove(KEY_SELECTED_ENGINE_PACKAGE)
            .remove(KEY_SELECTED_VOICE_ID)
            .putInt(KEY_TTS_SELECTION_MIGRATION_VERSION, TtsSelectionMigration.CURRENT_VERSION)
            .apply()
        selectedEnginePackageName = null
        selectedVoiceId = null
    }

    private fun startTextToSpeech(enginePackageName: String?, persistEngine: Boolean) {
        shutdownCurrentEngine()

        val requestedEngine = enginePackageName?.takeIf { it.isNotBlank() }
        onStatus(
            if (requestedEngine == null) "TTS 초기화 중: 휴대폰 기본 엔진"
            else "TTS 초기화 중: $requestedEngine",
        )
        tts = if (requestedEngine == null) {
            TextToSpeech(appContext) { status -> onInitialized(status, requestedEngine, persistEngine) }
        } else {
            TextToSpeech(appContext, { status -> onInitialized(status, requestedEngine, persistEngine) }, requestedEngine)
        }
    }

    private fun onInitialized(status: Int, requestedEngine: String?, persistEngine: Boolean) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts
            refreshEngineOptions()
            selectedEnginePackageName = resolveActiveEnginePackage(requestedEngine)
            notifyEnginesChanged()
            if (persistEngine) {
                selectedEnginePackageName?.let { preferences.edit().putString(KEY_SELECTED_ENGINE_PACKAGE, it).apply() }
            }
            // setLanguage()는 현재 목소리를 해당 로케일의 기본 보이스로 리셋하므로,
            // 엔진이 휴대폰 TTS 설정의 목소리를 물고 시작한 상태를 최대한 건드리지 않는다.
            val defaultVoice = engine?.let { runCatching { it.defaultVoice }.getOrNull() }
            val selectedLocale = if (defaultVoice != null && isUsableLanguage(defaultVoice.locale)) {
                defaultVoice.locale
            } else {
                selectSupportedLocale()?.also { engine?.setLanguage(it) }
            }
            isReady = selectedLocale != null
            if (selectedLocale != null) {
                onStatus(
                    "TTS 준비 완료: ${selectedEngineLabel()} · ${selectedEnginePackageName.orEmpty()} · ${selectedLocale.displayName}" +
                        defaultVoice?.name?.let { " · 기본 보이스 $it" }.orEmpty(),
                )
                refreshVoiceOptions(preferredLocale = selectedLocale)
            } else {
                onStatus("사용 가능한 TTS 언어를 찾지 못했습니다. 휴대폰 TTS 음성 데이터를 확인하세요.")
            }
            engine?.setSpeechRate(currentRate)
            engine?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    notifyStatus("재생 중: ${utteranceId ?: "unknown"}")
                }

                override fun onError(utteranceId: String?) {
                    notifyError("TTS 재생 오류: ${utteranceId ?: "unknown"}")
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != null) {
                        notifyDone(utteranceId)
                    }
                }
            })
            if (isReady) {
                pendingSpeech?.let { pending ->
                    pendingSpeech = null
                    speakNow(pending.text, pending.utteranceId)
                }
            }
        } else {
            isReady = false
            selectedEnginePackageName = null
            refreshEngineOptions()
            onStatus("TTS 초기화 실패: 휴대폰 TTS 엔진 설정을 확인하세요.")
        }
    }

    private fun isUsableLanguage(locale: Locale): Boolean =
        locale.language == Locale.KOREAN.language || locale.language == Locale.getDefault().language

    private fun selectSupportedLocale(): Locale? {
        val engine = tts ?: return null
        return listOf(Locale.KOREA, Locale.getDefault(), Locale.US).firstOrNull { locale ->
            val result = engine.isLanguageAvailable(locale)
            result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun setSpeechRate(speed: Float) {
        currentRate = speed.coerceIn(0.5f, 3.0f)
        tts?.setSpeechRate(currentRate)
    }

    override fun setEngine(enginePackageName: String): Boolean {
        if (enginePackageName == TtsEngineSelection.SYSTEM_DEFAULT_ENGINE_ID) {
            preferences.edit().remove(KEY_SELECTED_ENGINE_PACKAGE).remove(KEY_SELECTED_VOICE_ID).apply()
            selectedVoiceId = null
            voiceOptions = emptyList()
            notifyVoicesChanged()
            notifyStatus("TTS 엔진 변경: 휴대폰 기본 엔진을 따라갑니다.")
            startTextToSpeech(enginePackageName = null, persistEngine = false)
            return true
        }
        if (engineOptions.none { it.packageName == enginePackageName }) {
            notifyStatus("TTS 엔진 변경 실패: 설치된 엔진이 아닙니다.")
            return false
        }
        if (selectedEnginePackageName == enginePackageName) {
            preferences.edit().putString(KEY_SELECTED_ENGINE_PACKAGE, enginePackageName).apply()
            notifyStatus("이미 선택된 TTS 엔진: ${selectedEngineLabel()}")
            notifyEnginesChanged()
            return true
        }
        preferences.edit().putString(KEY_SELECTED_ENGINE_PACKAGE, enginePackageName).remove(KEY_SELECTED_VOICE_ID).apply()
        selectedVoiceId = null
        voiceOptions = emptyList()
        notifyVoicesChanged()
        startTextToSpeech(enginePackageName = enginePackageName, persistEngine = true)
        return true
    }

    override fun setVoice(voiceId: String): Boolean {
        if (voiceId == TtsVoiceSelection.SYSTEM_DEFAULT_VOICE_ID) {
            selectedVoiceId = voiceId
            preferences.edit().putString(KEY_SELECTED_VOICE_ID, voiceId).apply()
            notifyStatus("목소리 변경: 휴대폰 설정 목소리 사용")
            notifyVoicesChanged()
            // 삼성 TTS 설정에서 목소리를 바꾼 뒤 앱으로 돌아온 경우에도
            // 기존 인스턴스는 이전 설정을 유지할 수 있으므로 항상 재시작한다.
            if (TtsVoiceSelection.shouldRestartEngineForSelection(voiceId)) {
                startTextToSpeech(enginePackageName = preferences.getString(KEY_SELECTED_ENGINE_PACKAGE, null), persistEngine = false)
            }
            return true
        }
        val engine = tts ?: return false
        val voice = engine.voices?.firstOrNull { it.name == voiceId } ?: return false
        return try {
            val result = engine.setVoice(voice)
            if (result == TextToSpeech.SUCCESS) {
                selectedVoiceId = voice.name
                preferences.edit().putString(KEY_SELECTED_VOICE_ID, voice.name).apply()
                notifyStatus("목소리 변경: ${optionLabelForVoice(voice)}")
                notifyVoicesChanged()
                true
            } else {
                notifyStatus("목소리 변경 실패: ${optionLabelForVoice(voice)}")
                false
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "TextToSpeech.setVoice failed", exception)
            notifyStatus("목소리 변경 예외: ${exception.javaClass.simpleName}")
            false
        }
    }

    override fun speak(text: String, utteranceId: String) {
        initialize()
        if (!isReady) {
            pendingSpeech = PendingSpeech(text, utteranceId)
            onStatus("TTS 준비 대기 중입니다. 잠시 후 자동 재생합니다.")
            return
        }
        speakNow(text, utteranceId)
    }

    private fun speakNow(text: String, utteranceId: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        try {
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                notifyError("TTS speak 호출 실패: 휴대폰 TTS 엔진/음성 데이터를 확인하세요.")
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "TextToSpeech.speak failed", exception)
            notifyError("TTS speak 예외: ${exception.javaClass.simpleName}")
        }
    }

    private fun notifyStatus(message: String) {
        mainHandler.post {
            runCatching { onStatus(message) }
                .onFailure { Log.e(TAG, "TTS status callback failed", it) }
        }
    }

    private fun notifyDone(utteranceId: String) {
        mainHandler.post {
            runCatching { onDone(utteranceId) }
                .onFailure {
                    Log.e(TAG, "TTS done callback failed", it)
                    onStatus("TTS 완료 콜백 예외: ${it.javaClass.simpleName}")
                }
        }
    }

    private fun notifyError(message: String) {
        notifyStatus(message)
        mainHandler.post {
            runCatching { onError(message) }
                .onFailure { Log.e(TAG, "TTS error callback failed", it) }
        }
    }

    override fun stop() {
        pendingSpeech = null
        tts?.stop()
        onStatus("정지됨")
    }

    override fun shutdown() {
        pendingSpeech = null
        isReady = false
        shutdownCurrentEngine()
    }

    private fun shutdownCurrentEngine() {
        isReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun setOnDoneListener(listener: (utteranceId: String) -> Unit) {
        onDone = listener
    }

    override fun setOnErrorListener(listener: (message: String) -> Unit) {
        onError = listener
    }

    override fun setOnStatusListener(listener: (message: String) -> Unit) {
        onStatus = listener
    }

    override fun setOnVoicesChangedListener(listener: (voices: List<TtsVoiceOption>, selectedVoiceId: String?) -> Unit) {
        onVoicesChanged = listener
        listener(voiceOptions, selectedVoiceId)
    }

    override fun setOnEnginesChangedListener(listener: (engines: List<TtsEngineOption>, selectedEnginePackageName: String?) -> Unit) {
        onEnginesChanged = listener
        listener(engineOptions, displaySelectedEngineId())
    }

    private fun refreshEngineOptions() {
        val engine = tts ?: return
        val systemDefaultEngine = Settings.Secure.getString(appContext.contentResolver, SETTINGS_TTS_DEFAULT_SYNTH)
            ?: engine.defaultEngine
        val engineList = buildMap {
            engine.engines.orEmpty().forEach { engineInfo ->
                put(engineInfo.name, optionForEngine(engineInfo, systemDefaultEngine))
            }
            queryTtsServiceEngines(systemDefaultEngine).forEach { option ->
                putIfAbsent(option.packageName, option)
            }
            queryKnownTtsServicePackages(systemDefaultEngine).forEach { option ->
                putIfAbsent(option.packageName, option)
            }
        }.values.toList()
        engineOptions = listOf(systemDefaultEngineOption()) + TtsEngineSelection.sortedForDisplay(engineList)
        selectedEnginePackageName = selectedEnginePackageName
            ?: TtsEngineSelection.preferredEnginePackage(
                engines = installedEngineOptions(),
                savedEnginePackage = preferences.getString(KEY_SELECTED_ENGINE_PACKAGE, null),
            )
        notifyEnginesChanged()
    }

    private fun installedEngineOptions(): List<TtsEngineOption> =
        engineOptions.filterNot { it.packageName == TtsEngineSelection.SYSTEM_DEFAULT_ENGINE_ID }

    private fun systemDefaultEngineOption(): TtsEngineOption =
        TtsEngineOption(
            packageName = TtsEngineSelection.SYSTEM_DEFAULT_ENGINE_ID,
            label = "휴대폰 기본 엔진 따라가기",
            isDefault = false,
            providerRank = TtsEngineOption.PROVIDER_RANK_DEFAULT,
            discoverySource = "휴대폰 TTS 설정의 기본 엔진·목소리를 그대로 사용",
        )

    private fun resolveActiveEnginePackage(requestedEngine: String?): String? {
        val savedEngine = preferences.getString(KEY_SELECTED_ENGINE_PACKAGE, null)
        val installedEngines = installedEngineOptions()
        val requestedIsRealTtsEngine = requestedEngine != null && installedEngines.any { it.packageName == requestedEngine }
        if (requestedEngine != null && !requestedIsRealTtsEngine) {
            notifyStatus("요청한 엔진이 실제 TTS 서비스로 확인되지 않아 휴대폰 기본 엔진으로 표시합니다: $requestedEngine")
        }
        return when {
            requestedIsRealTtsEngine -> requestedEngine
            savedEngine != null && installedEngines.any { it.packageName == savedEngine } -> savedEngine
            else -> TtsEngineSelection.preferredEnginePackage(
                engines = installedEngines,
                savedEnginePackage = null,
            )
        }
    }

    private fun optionForEngine(
        engineInfo: TextToSpeech.EngineInfo,
        systemDefaultEngine: String?,
    ): TtsEngineOption {
        val label = engineInfo.label?.toString().orEmpty().ifBlank { engineInfo.name }
        val isDefault = engineInfo.name == systemDefaultEngine
        return TtsEngineOption(
            packageName = engineInfo.name,
            label = label,
            isDefault = isDefault,
            providerRank = TtsEngineSelection.providerRankFor(engineInfo.name, label, isDefault),
        )
    }

    private fun queryTtsServiceEngines(systemDefaultEngine: String?): List<TtsEngineOption> {
        val packageManager = appContext.packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        return resolveInfos.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.serviceInfo?.packageName ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank { packageName }
            optionForEnginePackage(packageName, label, systemDefaultEngine)
        }
    }

    private fun queryKnownTtsServicePackages(systemDefaultEngine: String?): List<TtsEngineOption> {
        val packageManager = appContext.packageManager
        return KNOWN_TTS_ENGINE_PACKAGES.mapNotNull { packageName ->
            val serviceIntent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(packageName)
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(serviceIntent, 0)
            }
            val resolveInfo = resolveInfos.firstOrNull() ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank { packageName }
            optionForEnginePackage(packageName, label, systemDefaultEngine)
        }
    }

    private fun optionForEnginePackage(
        packageName: String,
        label: String,
        systemDefaultEngine: String?,
    ): TtsEngineOption {
        val isDefault = packageName == systemDefaultEngine
        return TtsEngineOption(
            packageName = packageName,
            label = label,
            isDefault = isDefault,
            providerRank = TtsEngineSelection.providerRankFor(packageName, label, isDefault),
        )
    }

    private fun refreshVoiceOptions(preferredLocale: Locale) {
        val engine = tts ?: return
        val voices = engine.voices.orEmpty().toList()

        voiceOptions = TtsVoiceSelection.sortedForDisplay(
            listOf(systemDefaultVoiceOption(preferredLocale)) + voices.map { voice -> optionForVoice(voice, preferredLocale) },
        )
        selectedVoiceId = TtsVoiceSelection.preferredVoiceId(
            voices = voiceOptions,
            savedVoiceId = preferences.getString(KEY_SELECTED_VOICE_ID, null),
        )

        selectedVoiceId
            ?.takeUnless { it == TtsVoiceSelection.SYSTEM_DEFAULT_VOICE_ID }
            ?.let { setVoice(it) }
        val activeVoiceName = runCatching { engine.voice?.name }.getOrNull()
        notifyStatus(
            "TTS 준비 완료: ${selectedEngineLabel()} · ${selectedEnginePackageName.orEmpty()} · " +
                "목소리 ${voiceOptions.size}개 · 적용 보이스 ${activeVoiceName ?: "엔진 기본"}",
        )
        notifyVoicesChanged()
    }

    private fun optionForVoice(
        voice: android.speech.tts.Voice,
        preferredLocale: Locale,
    ): TtsVoiceOption {
        val providerRank = providerRankForCurrentEngine()
        val languageTag = voice.locale.toLanguageTag()
        val isKorean = voice.locale.language == Locale.KOREAN.language
        val isOffline = !voice.isNetworkConnectionRequired
        val isRecommended = isKorean && isOffline
        return TtsVoiceOption(
            id = voice.name,
            label = optionLabelForVoice(voice),
            description = optionDescriptionForVoice(voice, preferredLocale),
            languageTag = languageTag,
            isNetworkConnectionRequired = voice.isNetworkConnectionRequired,
            isRecommended = isRecommended,
            providerRank = providerRank,
        )
    }

    private fun systemDefaultVoiceOption(preferredLocale: Locale): TtsVoiceOption =
        TtsVoiceOption(
            id = TtsVoiceSelection.SYSTEM_DEFAULT_VOICE_ID,
            label = "휴대폰 설정 목소리",
            description = "삼성 설정 화면의 기본 목소리를 그대로 사용합니다 · " +
                "엔진 패키지: ${selectedEnginePackageName.orEmpty()} · " +
                "setVoice 강제 지정 안 함 · 언어: ${preferredLocale.displayName}",
            languageTag = preferredLocale.toLanguageTag(),
            isNetworkConnectionRequired = false,
            isRecommended = true,
            providerRank = 0,
        )

    private fun optionLabelForVoice(voice: android.speech.tts.Voice): String {
        val language = if (voice.locale.language == Locale.KOREAN.language) "한국어" else voice.locale.displayLanguage
        val connection = if (voice.isNetworkConnectionRequired) "온라인" else "오프라인"
        return "$language $connection · ${shortVoiceId(voice.name)}"
    }

    private fun optionDescriptionForVoice(
        voice: android.speech.tts.Voice,
        preferredLocale: Locale,
    ): String {
        val language = voice.locale.displayName
        val connection = if (voice.isNetworkConnectionRequired) "온라인 필요" else "오프라인 가능"
        val localeHint = if (voice.locale.language == preferredLocale.language) "추천 언어" else "다른 언어"
        val voiceIdHint = voice.name.takeIf { it.isNotBlank() }?.let { " · Voice ID: $it" }.orEmpty()
        val engineHint = selectedEnginePackageName?.let { " · 엔진 패키지: $it" }.orEmpty()
        return "$language · $connection · $localeHint$engineHint$voiceIdHint"
    }

    private fun shortVoiceId(voiceId: String): String =
        voiceId.ifBlank { "voice-id-empty" }.let { id ->
            if (id.length <= 28) id else "…${id.takeLast(27)}"
        }

    private fun selectedEngineLabel(): String =
        TtsEngineSelection.labelFor(engineOptions, selectedEnginePackageName)

    private fun currentProviderLabel(): String {
        val selected = engineOptions.firstOrNull { it.packageName == selectedEnginePackageName }
        return TtsEngineSelection.providerLabelFor(
            packageName = selected?.packageName ?: selectedEnginePackageName.orEmpty(),
            label = selected?.label.orEmpty(),
        )
    }

    private fun providerRankForCurrentEngine(): Int {
        val selected = engineOptions.firstOrNull { it.packageName == selectedEnginePackageName }
        return when {
            selected?.isSamsung == true -> TtsVoiceOption.PROVIDER_RANK_SAMSUNG
            selected?.isGoogle == true -> TtsVoiceOption.PROVIDER_RANK_GOOGLE
            else -> TtsVoiceOption.PROVIDER_RANK_OTHER
        }
    }

    private fun notifyVoicesChanged() {
        val voices = voiceOptions
        val voiceId = selectedVoiceId
        mainHandler.post {
            runCatching { onVoicesChanged(voices, voiceId) }
                .onFailure { Log.e(TAG, "TTS voices callback failed", it) }
        }
    }

    // 엔진이 고정 저장돼 있지 않으면(=휴대폰 기본 엔진 따라가기) UI에는
    // 합성 항목을 선택 상태로 보여 준다. 내부 selectedEnginePackageName은
    // 라벨/정렬용으로 실제 바인딩된 패키지를 유지한다.
    private fun displaySelectedEngineId(): String? =
        if (preferences.getString(KEY_SELECTED_ENGINE_PACKAGE, null) != null) {
            selectedEnginePackageName
        } else {
            TtsEngineSelection.SYSTEM_DEFAULT_ENGINE_ID
        }

    private fun notifyEnginesChanged() {
        val engines = engineOptions
        val enginePackage = displaySelectedEngineId()
        mainHandler.post {
            runCatching { onEnginesChanged(engines, enginePackage) }
                .onFailure { Log.e(TAG, "TTS engines callback failed", it) }
        }
    }

    private companion object {
        const val TAG = "ListenStudyTts"
        const val PREFS_NAME = "listenstudy_tts"
        const val KEY_SELECTED_VOICE_ID = "selected_voice_id"
        const val KEY_SELECTED_ENGINE_PACKAGE = "selected_engine_package"
        const val KEY_TTS_SELECTION_MIGRATION_VERSION = "tts_selection_migration_version"
        const val SETTINGS_TTS_DEFAULT_SYNTH = "tts_default_synth"
        val KNOWN_TTS_ENGINE_PACKAGES = listOf(
            TtsEngineOption.SAMSUNG_ENGINE_PACKAGE,
            TtsEngineOption.GOOGLE_ENGINE_PACKAGE,
        )
    }
}
