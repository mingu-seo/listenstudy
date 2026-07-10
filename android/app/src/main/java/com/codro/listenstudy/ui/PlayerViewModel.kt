package com.codro.listenstudy.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.codro.listenstudy.data.local.ListenStudyDatabase
import com.codro.listenstudy.data.repository.DocumentLibrary
import com.codro.listenstudy.data.repository.LibraryItem
import com.codro.listenstudy.data.repository.RoomDocumentLibrary
import com.codro.listenstudy.data.repository.SavedDocument
import com.codro.listenstudy.domain.player.PlaybackController
import com.codro.listenstudy.domain.player.PlaybackServiceCommand
import com.codro.listenstudy.domain.player.PlaybackState
import com.codro.listenstudy.domain.player.PlaybackStatus
import com.codro.listenstudy.domain.text.KoreanRuleBasedSentenceSplitter
import com.codro.listenstudy.io.BoundedInputReader
import com.codro.listenstudy.io.InputTooLargeException
import com.codro.listenstudy.domain.tts.TtsEngineOption
import com.codro.listenstudy.domain.tts.TtsEngineSelection
import com.codro.listenstudy.domain.tts.TtsVoiceOption
import com.codro.listenstudy.domain.tts.TtsVoiceSelection
import com.codro.listenstudy.domain.tts.CloudVoice
import com.codro.listenstudy.domain.tts.CloudVoiceCatalog
import com.codro.listenstudy.domain.tts.PlaybackMode
import com.codro.listenstudy.playback.PlaybackCommandBus
import com.codro.listenstudy.playback.TtsPlaybackService
import com.codro.listenstudy.tts.OnDeviceTtsEngine
import com.codro.listenstudy.tts.CloudTtsEngine
import com.codro.listenstudy.tts.CloudTtsSettings
import com.codro.listenstudy.tts.CloudCacheStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

@OptIn(kotlinx.coroutines.FlowPreview::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val sampleText = """
        ListenStudy는 공부 자료를 이동 중에도 들을 수 있게 만드는 앱입니다.
        첫 번째 목표는 서버 없이 Android 기기에서 동작하는 로컬 TTS 플레이어입니다.
        문장 단위로 분리하고, 현재 문장을 화면에서 강조합니다.
        사용자는 이전 문장과 다음 문장으로 이동할 수 있습니다.
        배속은 0.5배부터 3.0배까지 조절합니다.
        화면을 꺼도 재생되도록 Foreground Service 골격을 준비합니다.
        실제 기기에서는 알림 권한과 배터리 최적화를 확인해야 합니다.
    """.trimIndent()

    private val splitter = KoreanRuleBasedSentenceSplitter()
    private val database = Room.databaseBuilder(application, ListenStudyDatabase::class.java, "listenstudy.db").build()
    private val library: DocumentLibrary = RoomDocumentLibrary(database)
    private val _libraryItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    val libraryItems: StateFlow<List<LibraryItem>> = _libraryItems.asStateFlow()
    private val _showLibrary = MutableStateFlow(false)
    val showLibrary: StateFlow<Boolean> = _showLibrary.asStateFlow()
    private var activeDocumentId: String? = null
    private val documentLoadGuard = GenerationGuard()
    private var documentLoadJob: Job? = null
    private val saveCoordinator = PlaybackSaveCoordinator()
    private val _ttsStatus = MutableStateFlow("TTS 준비 전")
    val ttsStatus: StateFlow<String> = _ttsStatus
    private val _voiceOptions = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val voiceOptions: StateFlow<List<TtsVoiceOption>> = _voiceOptions
    private val _selectedVoiceId = MutableStateFlow<String?>(null)
    val selectedVoiceId: StateFlow<String?> = _selectedVoiceId
    private val _engineOptions = MutableStateFlow<List<TtsEngineOption>>(emptyList())
    val engineOptions: StateFlow<List<TtsEngineOption>> = _engineOptions
    private val _selectedEnginePackageName = MutableStateFlow<String?>(null)
    val selectedEnginePackageName: StateFlow<String?> = _selectedEnginePackageName
    private val _documentTitle = MutableStateFlow("샘플 학습 자료")
    val documentTitle: StateFlow<String> = _documentTitle
    private var playbackServiceStarted = false

    private val cloudSettings = CloudTtsSettings(application)
    private val cloudPrefs = application.getSharedPreferences("cloud_tts_ui", Application.MODE_PRIVATE)
    private val _playbackMode = MutableStateFlow(runCatching {
        PlaybackMode.valueOf(cloudPrefs.getString("mode", PlaybackMode.ON_DEVICE.name)!!)
    }.getOrDefault(PlaybackMode.ON_DEVICE))
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode
    private val _cloudVoice = MutableStateFlow(
        CloudVoiceCatalog.resolveForMode(
            mode = _playbackMode.value,
            savedVoiceId = cloudPrefs.getString("voice", CloudVoiceCatalog.defaultVoice.id),
        ),
    )
    val cloudVoice: StateFlow<CloudVoice> = _cloudVoice
    private val _hasCloudApiKey = MutableStateFlow(cloudSettings.hasApiKey())
    val hasCloudApiKey: StateFlow<Boolean> = _hasCloudApiKey
    private val _cloudCacheStats = MutableStateFlow(CloudCacheStats(0, 0))
    val cloudCacheStats: StateFlow<CloudCacheStats> = _cloudCacheStats

    private val ttsEngine = OnDeviceTtsEngine(application)
    private val cloudTtsEngine = CloudTtsEngine(application)
    private val controller = PlaybackController(
        sentences = splitter.split(sampleText).map { it.text },
        speak = { text, utteranceId, speed ->
            if (_playbackMode.value == PlaybackMode.ON_DEVICE) {
                ttsEngine.setSpeechRate(speed)
                ttsEngine.speak(text, utteranceId)
            } else {
                cloudTtsEngine.speak(text, utteranceId, speed, _cloudVoice.value.id, cloudSettings.apiKey())
            }
        },
        stopSpeaking = { ttsEngine.stop(); cloudTtsEngine.stop() },
    )

    init {
        ttsEngine.setOnStatusListener { message ->
            _ttsStatus.value = message
        }
        ttsEngine.setOnVoicesChangedListener { voices, selectedVoiceId ->
            _voiceOptions.value = voices
            _selectedVoiceId.value = selectedVoiceId
        }
        ttsEngine.setOnEnginesChangedListener { engines, selectedEnginePackageName ->
            _engineOptions.value = engines
            _selectedEnginePackageName.value = selectedEnginePackageName
        }
        val doneListener: (String) -> Unit = { utteranceId ->
            viewModelScope.launch {
                _ttsStatus.value = "문장 완료: $utteranceId → 다음 문장 준비"
                delay(NEXT_SENTENCE_DELAY_MS)
                runCatching { controller.onSentenceDone(utteranceId) }
                    .onFailure { exception ->
                        _ttsStatus.value = "다음 문장 전환 오류: ${exception.javaClass.simpleName}"
                    }
            }
        }
        ttsEngine.setOnDoneListener(doneListener)
        cloudTtsEngine.setOnDoneListener(doneListener)
        cloudTtsEngine.setOnStatusListener { message ->
            _ttsStatus.value = message
            refreshCloudCacheStats()
        }
        cloudTtsEngine.setOnErrorListener { message ->
            controller.onPlaybackError()
            _ttsStatus.value = message
        }
        refreshCloudCacheStats()
        ttsEngine.initialize()
        PlaybackCommandBus.setListener { command ->
            when (command) {
                PlaybackServiceCommand.PlayPause -> {
                    if (controller.state.value.status == PlaybackStatus.Playing) pause() else play()
                }
                PlaybackServiceCommand.Previous -> previous()
                PlaybackServiceCommand.Next -> next()
            }
        }
        viewModelScope.launch {
            controller.state.collect { playbackState ->
                if (playbackServiceStarted) updatePlaybackNotification(playbackState)
            }
        }
        viewModelScope.launch {
            library.observeLibrary().collect { _libraryItems.value = it }
        }
        val startupGeneration = documentLoadGuard.next()
        documentLoadJob = viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { library.loadMostRecent() }
            if (restored != null && documentLoadGuard.isCurrent(startupGeneration)) applySavedDocument(restored)
        }
        viewModelScope.launch {
            controller.state
                .map { createSaveRequest() }
                .distinctUntilChanged()
                .debounce(250)
                .collect { request -> if (request != null) persistPlayback(request) }
        }
    }

    val state: StateFlow<PlaybackState> = controller.state

    fun loadTextFile(uri: Uri) {
        val generation = beginDocumentLoad()
        documentLoadJob = viewModelScope.launch {
            _ttsStatus.value = "텍스트 파일을 읽는 중입니다."
            runCatching {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val title = app.contentResolver.displayName(uri) ?: "불러온 학습 자료"
                    val bytes = app.contentResolver.openInputStream(uri)?.use {
                        BoundedInputReader.read(it, MAX_TEXT_FILE_BYTES)
                    }
                        ?: error("파일을 열 수 없습니다.")
                    val text = bytes.decodeTextFile()
                    Triple(title, uri.toString(), splitter.split(text))
                }
            }.onSuccess { (title, sourceUri, sentenceSpans) ->
                if (!documentLoadGuard.isCurrent(generation)) return@onSuccess
                val sentences = sentenceSpans.map { it.text }
                if (sentences.isEmpty()) {
                    _ttsStatus.value = "문장으로 읽을 텍스트가 없습니다."
                    return@onSuccess
                }
                val saved = withContext(Dispatchers.IO) {
                    library.importDocument(title, sourceUri, sentenceSpans)
                }
                if (!documentLoadGuard.isCurrent(generation)) return@onSuccess
                applySavedDocument(saved)
                _ttsStatus.value = "파일 불러오기 완료: $title · ${sentences.size}문장"
            }.onFailure { exception ->
                if (documentLoadGuard.isCurrent(generation)) {
                    _ttsStatus.value = if (exception is InputTooLargeException) {
                        "파일 불러오기 실패: TXT 파일은 10MB 이하만 열 수 있습니다."
                    } else {
                        "파일 불러오기 실패: ${exception.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    fun play() {
        viewModelScope.launch {
            controller.play()
            playbackServiceStarted = true
            updatePlaybackNotification(controller.state.value)
        }
    }

    fun pause() {
        controller.pause()
        if (playbackServiceStarted) updatePlaybackNotification(controller.state.value)
    }

    fun previous() {
        viewModelScope.launch { controller.previous(autoPlay = state.value.status == PlaybackStatus.Playing) }
    }

    fun next() {
        viewModelScope.launch { controller.next(autoPlay = state.value.status == PlaybackStatus.Playing) }
    }

    fun faster() = controller.setSpeed(state.value.speed + 0.1f)

    fun slower() = controller.setSpeed(state.value.speed - 0.1f)

    fun openLibrary() {
        controller.pause()
        _showLibrary.value = true
        persistNow()
    }

    fun openPlayer() { _showLibrary.value = false }

    fun openDocument(id: String) {
        val generation = beginDocumentLoad()
        documentLoadJob = viewModelScope.launch {
            val document = withContext(Dispatchers.IO) { library.loadDocument(id) }
            if (document != null && documentLoadGuard.isCurrent(generation)) applySavedDocument(document)
        }
    }

    fun deleteDocument(id: String) {
        val generation = beginDocumentLoad()
        if (activeDocumentId == id) activeDocumentId = null
        documentLoadJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { library.deleteDocument(id) }
            if (activeDocumentId == null && documentLoadGuard.isCurrent(generation)) {
                val fallback = withContext(Dispatchers.IO) { library.loadMostRecent() }
                if (!documentLoadGuard.isCurrent(generation)) return@launch
                if (fallback != null) {
                    applySavedDocument(fallback)
                } else {
                    activeDocumentId = null
                    _documentTitle.value = "샘플 학습 자료"
                    controller.replaceSentences(splitter.split(sampleText).map { it.text })
                    controller.setSpeed(1f)
                }
            }
        }
    }

    fun changeVoice() {
        val nextVoiceId = TtsVoiceSelection.nextVoiceId(_voiceOptions.value, _selectedVoiceId.value)
        if (nextVoiceId == null) {
            _ttsStatus.value = "선택 가능한 목소리가 없습니다. 휴대폰 TTS 음성 데이터를 추가해 주세요."
            return
        }
        selectVoice(nextVoiceId)
    }

    fun selectVoice(voiceId: String) {
        if (ttsEngine.setVoice(voiceId)) {
            _selectedVoiceId.value = voiceId
        }
    }

    fun selectEngine(enginePackageName: String) {
        viewModelScope.launch {
            controller.pause()
            if (ttsEngine.setEngine(enginePackageName)) {
                _selectedEnginePackageName.value = enginePackageName
                val engineLabel = TtsEngineSelection.labelFor(_engineOptions.value, enginePackageName)
                _ttsStatus.value = "TTS 엔진 변경: $engineLabel"
            }
        }
    }

    fun previewVoice(voiceId: String? = _selectedVoiceId.value) {
        viewModelScope.launch {
            controller.pause()
            if (voiceId != null) selectVoice(voiceId)
            val voiceLabel = TtsVoiceSelection.labelFor(_voiceOptions.value, _selectedVoiceId.value)
            ttsEngine.setSpeechRate(state.value.speed)
            ttsEngine.speak("안녕하세요. 이 목소리로 학습 자료를 읽어드립니다.", "listenstudy_voice_preview")
            _ttsStatus.value = "목소리 미리듣기: $voiceLabel"
        }
    }

    fun selectPlaybackMode(mode: PlaybackMode) {
        controller.pause()
        _playbackMode.value = mode
        cloudPrefs.edit().putString("mode", mode.name).apply()
        if (mode != PlaybackMode.ON_DEVICE) {
            val resolvedVoice = CloudVoiceCatalog.resolveForMode(mode, _cloudVoice.value.id)
            _cloudVoice.value = resolvedVoice
            cloudPrefs.edit().putString("voice", resolvedVoice.id).apply()
        }
        _ttsStatus.value = "재생 모드: ${mode.label}"
    }

    fun selectCloudVoice(voiceId: String) {
        val voice = CloudVoiceCatalog.resolveForMode(_playbackMode.value, voiceId)
        if (_playbackMode.value != PlaybackMode.ON_DEVICE && voice.mode != _playbackMode.value) return
        _cloudVoice.value = voice
        cloudPrefs.edit().putString("voice", voice.id).apply()
    }

    fun saveCloudApiKey(value: String) {
        if (value.isBlank()) { _ttsStatus.value = "입력한 API 키가 비어 있습니다."; return }
        cloudSettings.saveApiKey(value)
        _hasCloudApiKey.value = true
        _ttsStatus.value = "Google Cloud API 키를 비공개 앱 저장소에 저장했습니다."
    }

    fun deleteCloudApiKey() {
        cloudSettings.deleteApiKey()
        _hasCloudApiKey.value = false
        _ttsStatus.value = "Google Cloud API 키를 삭제했습니다. 캐시 음성은 계속 재생할 수 있습니다."
    }

    fun clearCloudCache() {
        controller.pause()
        cloudTtsEngine.clearCache()
        refreshCloudCacheStats()
        _ttsStatus.value = "클라우드 음성 캐시를 삭제했습니다."
    }

    fun previewCloudVoice() {
        controller.pause()
        cloudTtsEngine.speak(
            "안녕하세요. Google Cloud 음성 미리듣기입니다.",
            "listenstudy_cloud_preview",
            state.value.speed,
            _cloudVoice.value.id,
            cloudSettings.apiKey(),
        )
    }

    private fun refreshCloudCacheStats() { _cloudCacheStats.value = cloudTtsEngine.stats() }

    fun openTtsSettings() {
        runCatching {
            val intent = Intent(ACTION_TTS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        }.onFailure { exception ->
            _ttsStatus.value = "TTS 설정 화면 열기 실패: ${exception.javaClass.simpleName}"
        }
    }

    fun jumpTo(index: Int) = controller.jumpTo(index)

    fun persistNow() {
        val request = createSaveRequest() ?: return
        viewModelScope.launch { persistPlayback(request) }
    }

    private suspend fun applySavedDocument(document: SavedDocument) {
        createSaveRequest()?.let { persistPlayback(it) }
        controller.pause()
        activeDocumentId = document.documentId
        _documentTitle.value = document.title
        controller.replaceSentences(document.sentences)
        controller.setSpeed(document.speed)
        controller.jumpTo(document.index)
        _showLibrary.value = false
        _ttsStatus.value = "이어듣기 준비: ${document.title} · ${document.index + 1}/${document.sentences.size}"
    }

    private fun createSaveRequest(): PlaybackSaveRequest? {
        val id = activeDocumentId ?: return null
        val snapshot = state.value
        return saveCoordinator.request(id, snapshot.currentIndex, snapshot.speed)
    }

    private suspend fun persistPlayback(request: PlaybackSaveRequest) {
        saveCoordinator.save(request) { immutable ->
            withContext(Dispatchers.IO) {
                library.savePlayback(immutable.documentId, immutable.index, immutable.speed)
            }
        }
    }

    private fun beginDocumentLoad(): Long {
        documentLoadJob?.cancel()
        return documentLoadGuard.next()
    }

    private fun updatePlaybackNotification(playbackState: PlaybackState) {
        val statusLabel = when (playbackState.status) {
            PlaybackStatus.Playing -> "재생 중"
            PlaybackStatus.Paused -> "일시정지"
            PlaybackStatus.Completed -> "완료"
            PlaybackStatus.Idle -> "대기 중"
        }
        val progressLabel = PlayerUiFormatter.sentenceCounter(playbackState.currentIndex, playbackState.sentences.size) +
            " · " + PlayerUiFormatter.progressPercent(playbackState.currentIndex, playbackState.sentences.size) + "%" +
            " · " + PlayerUiFormatter.speedLabel(playbackState.speed)
        TtsPlaybackService.startOrUpdate(
            context = getApplication<Application>(),
            title = _documentTitle.value,
            status = statusLabel,
            progress = progressLabel,
        )
    }

    override fun onCleared() {
        PlaybackCommandBus.setListener(null)
        ttsEngine.shutdown()
        cloudTtsEngine.shutdown()
        database.close()
        super.onCleared()
    }

    private companion object {
        const val NEXT_SENTENCE_DELAY_MS = 180L
        const val MAX_TEXT_FILE_BYTES = 10 * 1024 * 1024
        const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
    }
}

private fun android.content.ContentResolver.displayName(uri: Uri): String? {
    return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}

private fun ByteArray.decodeTextFile(): String {
    return runCatching { decodeStrict(StandardCharsets.UTF_8) }
        .getOrElse { decodeStrict(Charset.forName("EUC-KR")) }
        .replace("\uFEFF", "")
        .trim()
}

@Throws(CharacterCodingException::class)
private fun ByteArray.decodeStrict(charset: Charset): String {
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(this)).toString()
}
