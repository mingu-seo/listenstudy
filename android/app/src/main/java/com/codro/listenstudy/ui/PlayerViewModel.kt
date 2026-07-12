package com.codro.listenstudy.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.codro.listenstudy.data.local.ListenStudyDatabase
import com.codro.listenstudy.data.repository.*
import com.codro.listenstudy.playback.ServiceCommand
import com.codro.listenstudy.domain.text.KoreanRuleBasedSentenceSplitter
import com.codro.listenstudy.domain.tts.*
import com.codro.listenstudy.io.BoundedInputReader
import com.codro.listenstudy.io.InputTooLargeException
import com.codro.listenstudy.playback.PlaybackServiceConnection
import com.codro.listenstudy.playback.TtsPlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.charset.*

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val sampleText = "ListenStudy는 공부 자료를 이동 중에도 들을 수 있게 만드는 앱입니다. 문장 단위 재생을 시작하세요."
    private val splitter = KoreanRuleBasedSentenceSplitter()
    private val database = Room.databaseBuilder(application, ListenStudyDatabase::class.java, "listenstudy.db").build()
    private val library: DocumentLibrary = RoomDocumentLibrary(database)
    private val _libraryItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    val libraryItems = _libraryItems.asStateFlow()
    private val _showLibrary = MutableStateFlow(false)
    val showLibrary = _showLibrary.asStateFlow()
    private val documentLoadGuard = GenerationGuard()
    private var documentLoadJob: Job? = null
    private val ui = PlaybackServiceConnection.uiState
    private fun <T> serviceField(block: (com.codro.listenstudy.playback.PlaybackServiceUiState) -> T) =
        ui.map(block).distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, block(ui.value))
    val state = serviceField { it.playback }
    val ttsStatus = serviceField { it.ttsStatus }
    val voiceOptions = serviceField { it.voiceOptions }
    val selectedVoiceId = serviceField { it.selectedVoiceId }
    val engineOptions = serviceField { it.engineOptions }
    val selectedEnginePackageName = serviceField { it.selectedEnginePackageName }
    val documentTitle = serviceField { it.documentTitle }
    val playbackMode = serviceField { it.playbackMode }
    val cloudVoice = serviceField { it.cloudVoice }
    val hasCloudApiKey = serviceField { it.hasCloudApiKey }
    val cloudCacheStats = serviceField { it.cloudCacheStats }

    init {
        TtsPlaybackService.start(application)
        viewModelScope.launch { library.observeLibrary().collect { _libraryItems.value = it } }
        val generation = documentLoadGuard.next()
        documentLoadJob = viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { library.loadMostRecent() }
            if (restored != null && documentLoadGuard.isCurrent(generation) && ui.value.documentId == null) applySavedDocument(restored)
            else if (
                restored == null &&
                documentLoadGuard.isCurrent(generation) &&
                ui.value.playback.sentences.isEmpty()
            ) {
                replaceSample()
            }
        }
    }

    fun loadTextFile(uri: Uri) {
        val generation = beginDocumentLoad()
        documentLoadJob = viewModelScope.launch {
            send(ServiceCommand.ShowStatus("텍스트 파일을 읽는 중입니다."))
            try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val title = app.contentResolver.displayName(uri) ?: "불러온 학습 자료"
                    val bytes = app.contentResolver.openInputStream(uri)?.use {
                        BoundedInputReader.read(it, MAX_TEXT_FILE_BYTES)
                    } ?: error("파일을 열 수 없습니다.")
                    Triple(title, uri.toString(), splitter.split(bytes.decodeTextFile()))
                }
                    .let { (title, source, spans) ->
                if (!documentLoadGuard.isCurrent(generation)) return@launch
                if (spans.isEmpty()) {
                    send(ServiceCommand.ShowStatus("문장으로 읽을 텍스트가 없습니다."))
                    return@launch
                }
                val saved = withContext(Dispatchers.IO) { library.importDocument(title, source, spans) }
                if (documentLoadGuard.isCurrent(generation)) {
                    applySavedDocument(saved)
                    send(ServiceCommand.ShowStatus("파일 불러오기 완료: $title · ${spans.size}문장"))
                }
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (documentLoadGuard.isCurrent(generation)) {
                    val message = if (failure is InputTooLargeException) {
                        "파일 불러오기 실패: TXT 파일은 10MB 이하만 열 수 있습니다."
                    } else {
                        "파일 불러오기 실패: ${failure.javaClass.simpleName}"
                    }
                    send(ServiceCommand.ShowStatus(message))
                }
            }
        }
    }
    fun play() = send(ServiceCommand.Play)
    fun pause() = send(ServiceCommand.Pause)
    fun previous() = send(ServiceCommand.Previous)
    fun next() = send(ServiceCommand.Next)
    fun faster() = send(ServiceCommand.SetSpeed(state.value.speed + .1f))
    fun slower() = send(ServiceCommand.SetSpeed(state.value.speed - .1f))
    fun jumpTo(index: Int) = send(ServiceCommand.Jump(index))
    fun openLibrary() { send(ServiceCommand.OpenLibrary); _showLibrary.value = true }
    fun openPlayer() { _showLibrary.value = false }
    fun openDocument(id: String) { val g=beginDocumentLoad(); documentLoadJob=viewModelScope.launch { withContext(Dispatchers.IO){library.loadDocument(id)}?.takeIf{documentLoadGuard.isCurrent(g)}?.let{applySavedDocument(it)} } }
    fun deleteDocument(id: String) { val g=beginDocumentLoad(); documentLoadJob=viewModelScope.launch { withContext(Dispatchers.IO){library.deleteDocument(id)}; if(ui.value.documentId==id && documentLoadGuard.isCurrent(g)){ val next=withContext(Dispatchers.IO){library.loadMostRecent()}; if(next!=null) applySavedDocument(next) else replaceSample() } } }
    fun changeVoice() { TtsVoiceSelection.nextVoiceId(voiceOptions.value, selectedVoiceId.value)?.let(::selectVoice) }
    fun selectVoice(id: String) = send(ServiceCommand.SelectOnDeviceVoice(id))
    fun selectEngine(id: String) = send(ServiceCommand.SelectEngine(id))
    fun previewVoice(id: String? = selectedVoiceId.value) = send(ServiceCommand.PreviewLocalVoice(id))
    fun selectPlaybackMode(mode: PlaybackMode) = send(ServiceCommand.SelectPlaybackMode(mode))
    fun selectCloudVoice(id: String) = send(ServiceCommand.SelectCloudVoice(id))
    fun saveCloudApiKey(value: String) = send(ServiceCommand.SaveApiKey(value))
    fun deleteCloudApiKey() = send(ServiceCommand.DeleteApiKey)
    fun clearCloudCache() = send(ServiceCommand.ClearCache)
    fun previewCloudVoice() = send(ServiceCommand.PreviewCloudVoice)
    fun persistNow() { /* Service persists independently. */ }
    fun openTtsSettings() {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(ACTION_TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { failure ->
            send(ServiceCommand.ShowStatus("TTS 설정 화면 열기 실패: ${failure.javaClass.simpleName}"))
        }
    }
    private fun send(command: ServiceCommand) {
        // The ViewModel starts the service once in init. Commands then stay in-process;
        // the running service promotes itself only for playback/preview transitions.
        PlaybackServiceConnection.dispatch(command)
    }
    private fun applySavedDocument(d: SavedDocument) { send(ServiceCommand.ReplaceDocument(d.documentId,d.title,d.sentences,d.index,d.speed)); _showLibrary.value=false }
    private fun replaceSample() = send(ServiceCommand.ReplaceDocument(null,"샘플 학습 자료",splitter.split(sampleText).map{it.text}))
    private fun beginDocumentLoad():Long { documentLoadJob?.cancel(); return documentLoadGuard.next() }
    override fun onCleared() { database.close(); super.onCleared() }
    private companion object { const val MAX_TEXT_FILE_BYTES=10*1024*1024; const val ACTION_TTS_SETTINGS="com.android.settings.TTS_SETTINGS" }
}
private fun android.content.ContentResolver.displayName(uri: Uri): String? = query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null }
private fun ByteArray.decodeTextFile():String = runCatching{decodeStrict(StandardCharsets.UTF_8)}.getOrElse{decodeStrict(Charset.forName("EUC-KR"))}.replace("\uFEFF","").trim()
private fun ByteArray.decodeStrict(charset:Charset):String = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(this)).toString()
