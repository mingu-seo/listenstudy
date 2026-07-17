package com.codro.listenstudy.playback

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.*
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.room.Room
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.*
import com.codro.listenstudy.MainActivity
import com.codro.listenstudy.R
import com.codro.listenstudy.data.local.ListenStudyDatabase
import com.codro.listenstudy.data.repository.RoomDocumentLibrary
import com.codro.listenstudy.domain.player.*
import com.codro.listenstudy.domain.tts.*
import com.codro.listenstudy.tts.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel

@OptIn(FlowPreview::class)
class TtsPlaybackService : Service() {
    private val owner = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var local: OnDeviceTtsEngine
    private lateinit var cloud: CloudTtsEngine
    private lateinit var session: PlaybackSession
    private lateinit var db: ListenStudyDatabase
    private lateinit var library: RoomDocumentLibrary
    private lateinit var settings: CloudTtsSettings
    private var mediaSession: MediaSessionCompat? = null
    private val mutableUi = MutableStateFlow(PlaybackServiceUiState())
    private val prefs by lazy { getSharedPreferences("cloud_tts_ui", MODE_PRIVATE) }
    private val persistence = PlaybackPersistenceCoordinator()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var writer: PlaybackPersistenceWriter
    private val commandQueue = Channel<QueuedCommand>(Channel.UNLIMITED)
    private val restoreGate = PlaybackRestoreGate()
    private var previewActive = false
    private val lifecycleGate = ServiceLifecycleGate()
    private val producerGate = PersistenceProducerGate()
    private val terminalCompletion by lazy {
        TerminalCompletionCoordinator(
            // Route the final save through the same serialized writer and await its barrier, so every
            // earlier queued save commits first and the completed position is written last.
            persist = { request -> writer.barrier(request) },
            stop = {
                // Terminal shutdown must REMOVE the stale notification, not merely detach it.
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            },
        )
    }

    private data class QueuedCommand(
        val command: ServiceCommand,
        val isRestore: Boolean = false,
        val restoreToken: Long? = null,
    )

    override fun onCreate() {
        super.onCreate(); createNotificationChannel()
        db=Room.databaseBuilder(this,ListenStudyDatabase::class.java,"listenstudy.db").build(); library=RoomDocumentLibrary(db)
        settings=CloudTtsSettings(this); local=OnDeviceTtsEngine(this); cloud=CloudTtsEngine(this)
        val mode=runCatching{PlaybackMode.valueOf(prefs.getString("mode",PlaybackMode.ON_DEVICE.name)!!)}.getOrDefault(PlaybackMode.ON_DEVICE)
        val voice=CloudVoiceCatalog.resolveForMode(mode,prefs.getString("voice",null))
        mutableUi.value=mutableUi.value.copy(playbackMode=mode,cloudVoice=voice,hasCloudApiKey=settings.hasApiKey(),cloudCacheStats=cloud.stats())
        session=PlaybackSession(emptyList(), speak={ text,id,speed ->
            val ui=mutableUi.value
            if(ui.playbackMode==PlaybackMode.ON_DEVICE){local.setSpeechRate(speed);local.speak(text,id)}
            else {
                val key = settings.apiKey()
                cloud.speak(text,id,speed,ui.cloudVoice.id,key)
                val playback = session.state.value
                cloud.prefetch(CloudPrefetchPlanner(2).plan(playback.sentences, playback.currentIndex), ui.cloudVoice.id, key)
            }
        }, stopSpeaking={local.stop();cloud.stop()})
        local.setOnStatusListener{update { copy(ttsStatus=it) }}
        local.setOnVoicesChangedListener{options,selected->update{copy(voiceOptions=options,selectedVoiceId=selected)}}
        local.setOnEnginesChangedListener{options,selected->update{copy(engineOptions=options,selectedEnginePackageName=selected)}}
        cloud.setOnStatusListener{update{copy(ttsStatus=it,cloudCacheStats=cloud.stats())}}
        local.setOnDoneListener(::onSpeechDone)
        cloud.setOnDoneListener(::onSpeechDone)
        val playbackError = PlaybackErrorCoordinator {
            previewActive = false
            // Playback stopped here, so queued prefetch for sentences ahead is now speculative work
            // for a document nobody is listening to. onPlaybackError only pauses state; unlike pause
            // it does not run stopSpeaking, so nothing else would invalidate it.
            cloud.cancelPrefetch()
            session.onPlaybackError()
            applyLifecyclePolicy(session.state.value.status)
        }
        local.setOnErrorListener(playbackError::onError)
        cloud.setOnErrorListener(playbackError::onError)
        // The pause path above already stopped on the failed sentence; this only decides where the
        // classified report belongs. A failed settings preview must never raise a recovery panel on
        // the reader, whose buttons would act on a sentence the user was not listening to.
        cloud.setOnCloudErrorListener { utteranceId, report ->
            when (CloudErrorRouter.route(utteranceId, mutableUi.value.playbackMode)) {
                CloudErrorTarget.PreviewFeedback ->
                    update { copy(ttsStatus = CloudErrorRouter.previewFailureMessage(report)) }
                CloudErrorTarget.CurrentSentence -> update { copy(cloudError = report) }
                CloudErrorTarget.Ignore -> Unit
            }
        }
        local.initialize()
        setupMediaSession()
        writer = PlaybackPersistenceWriter(
            scope = persistenceScope,
            write = { request -> library.savePlayback(request.documentId, request.index, request.speed) },
            onSaved = { request -> persistence.saved(request) },
            onDrained = { db.close() },
        )
        scope.launch {
            for (queued in commandQueue) executeSerialized(queued)
        }
        PlaybackServiceConnection.attach(owner, mutableUi.value, ::execute)
        scope.launch { session.state.collect { playback -> update { copy(playback=playback) } } }
        scope.launch { mutableUi.collect { ui ->
            PlaybackServiceConnection.publish(owner, ui)
            updateMediaSession(ui)
            notificationManager().notify(NOTIFICATION_ID, buildNotification(ui))
            applyLifecyclePolicy(ui.playback.status)
        }}
        scope.launch {
            mutableUi.map { persistence.observe(it.documentId, it.playback) }
                .filterNotNull()
                .debounce(250)
                .collectLatest(::enqueuePersistence)
        }
        val restoreToken = restoreGate.beginRestore()
        scope.launch {
            val restored = withContext(Dispatchers.IO) { library.loadMostRecent() }
            if (restored != null && restoreGate.canApply(restoreToken)) {
                commandQueue.send(
                    QueuedCommand(
                        command = ServiceCommand.ReplaceDocument(
                            restored.documentId,
                            restored.title,
                            restored.sentences,
                            restored.index,
                            restored.speed,
                        ),
                        isRestore = true,
                        restoreToken = restoreToken,
                    ),
                )
            }
        }
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // Required immediately for every foreground-service start, before restore/other async work.
        startAsForeground(mutableUi.value)
        when(PlaybackServiceCommand.fromAction(intent?.action)){
            PlaybackServiceCommand.PlayPause->execute(if(session.state.value.status==PlaybackStatus.Playing) ServiceCommand.Pause else ServiceCommand.Play)
            PlaybackServiceCommand.Previous->execute(ServiceCommand.Previous)
            PlaybackServiceCommand.Next->execute(ServiceCommand.Next)
            null->Unit
        }
        val decision = PlaybackServiceLifecyclePolicy.decide(session.state.value.status, previewActive)
        if (!decision.foreground) stopForeground(STOP_FOREGROUND_DETACH)
        return if (decision.sticky) START_STICKY else START_NOT_STICKY
    }
    override fun onBind(intent:Intent?):IBinder?=null

    private fun execute(command: ServiceCommand) {
        if (command is ServiceCommand.ReplaceDocument) restoreGate.contentChanged()
        // Cleared at ACCEPTANCE, not when the command runs: the panel must go the moment the user acts,
        // and a stale panel must never outlive the attempt it described.
        if (CloudErrorUiPolicy.clearsCloudError(command) && mutableUi.value.cloudError != null) {
            update { copy(cloudError = null) }
        }
        // Invalidate any pending terminal finalization at ACCEPTANCE time (main thread), before the
        // command is serialized — otherwise the terminal continuation could stopSelf() first. Bumping
        // the generation now guarantees a stale finalizer sees it and skips stop; re-enabling the gate
        // and reopening the producer readies the revived session.
        if (command.revivesPlayback()) {
            terminalCompletion.invalidate()
            lifecycleGate.reset()
            producerGate.revive()
        }
        commandQueue.trySend(QueuedCommand(command))
    }

    private suspend fun executeSerialized(queued: QueuedCommand) {
        val command = queued.command
        if (queued.isRestore && !restoreGate.canApply(queued.restoreToken ?: return)) return
        when(command){
            ServiceCommand.Play->session.execute(PlaybackSessionCommand.Play)
            ServiceCommand.Pause->{session.execute(PlaybackSessionCommand.Pause);flushPersistence()}
            ServiceCommand.Previous->session.execute(PlaybackSessionCommand.Previous); ServiceCommand.Next->session.execute(PlaybackSessionCommand.Next)
            is ServiceCommand.Jump->session.execute(PlaybackSessionCommand.Jump(command.index)); is ServiceCommand.SetSpeed->session.execute(PlaybackSessionCommand.SetSpeed(command.speed))
            ServiceCommand.OpenLibrary->{session.execute(PlaybackSessionCommand.Pause);flushPersistence()}
            is ServiceCommand.SelectOnDeviceVoice -> {
                session.execute(PlaybackSessionCommand.Pause)
                if (!local.setVoice(command.voiceId)) update { copy(ttsStatus = "목소리 변경 실패: 선택한 목소리를 사용할 수 없습니다.") }
            }
            is ServiceCommand.SelectEngine -> {
                session.execute(PlaybackSessionCommand.Pause)
                if (!local.setEngine(command.packageName)) update { copy(ttsStatus = "TTS 엔진 변경 실패: 선택한 엔진을 사용할 수 없습니다.") }
            }
            is ServiceCommand.PreviewLocalVoice -> {
                session.execute(PlaybackSessionCommand.Pause)
                val voiceId = command.voiceId
                if (voiceId == null) {
                    update { copy(ttsStatus = "미리들을 목소리가 없습니다.") }
                } else if (!local.setVoice(voiceId)) {
                    update { copy(ttsStatus = "목소리 미리듣기 실패: 선택한 목소리를 사용할 수 없습니다.") }
                } else {
                    previewActive = true
                    applyLifecyclePolicy(session.state.value.status)
                    local.setSpeechRate(session.state.value.speed)
                    local.speak("안녕하세요. 이 목소리로 학습 자료를 읽어드립니다.", LOCAL_PREVIEW_ID)
                }
            }
            is ServiceCommand.SelectPlaybackMode->{session.execute(PlaybackSessionCommand.Pause);applyPlaybackMode(command.mode)}
            is ServiceCommand.SelectCloudVoice->{val voice=CloudVoiceCatalog.resolveForMode(mutableUi.value.playbackMode,command.voiceId);prefs.edit().putString("voice",voice.id).apply();update{copy(cloudVoice=voice)}}
            is ServiceCommand.SaveApiKey -> {
                // Keystore + SharedPreferences commits are blocking disk I/O and must not run on the
                // main thread. Suspending here keeps the command queue serialized (the next command
                // waits) while the work itself happens on the IO dispatcher; the key never leaves
                // this block. The result is published only once the save (or blank rejection) has
                // actually resolved, and always stamped with this request's id, so the UI can tell
                // this outcome apart from a previous one.
                val isBlank = command.value.isBlank()
                val attempt = withContext(Dispatchers.IO) {
                    val saved = !isBlank && settings.saveApiKey(command.value)
                    saved to settings.hasApiKey()
                }
                val resolution = CloudKeySavePolicy.resolve(command.requestId, isBlank, attempt.first)
                update {
                    copy(
                        hasCloudApiKey = attempt.second,
                        cloudKeySaveResult = resolution.result,
                        ttsStatus = resolution.message,
                    )
                }
            }
            ServiceCommand.DeleteApiKey -> {
                // Deletion touches the same secure storage, so it is subject to the same rule.
                val outcome = withContext(Dispatchers.IO) {
                    val deleted = settings.deleteApiKey()
                    deleted to settings.hasApiKey()
                }
                update {
                    copy(
                        hasCloudApiKey = outcome.second,
                        ttsStatus = if (outcome.first) "Google Cloud API 키를 삭제했습니다."
                        else "API 키를 완전히 삭제하지 못했습니다. 다시 시도해 주세요.",
                    )
                }
            }
            ServiceCommand.ClearCache->{session.execute(PlaybackSessionCommand.Pause);cloud.clearCache();update{copy(cloudCacheStats=cloud.stats(),ttsStatus="클라우드 음성 캐시를 삭제했습니다.")}}
            // Explicit, user-initiated retry of the SAME sentence. Deliberately not automatic: a retry
            // is a billed request, and only the user can know whether the failure has been addressed.
            ServiceCommand.RetryCloudSentence -> session.execute(PlaybackSessionCommand.Play)
            ServiceCommand.UseOnDeviceVoiceForCurrentSentence -> {
                // Cancel the in-flight cloud request first so the abandoned synthesis cannot fire a
                // late error against the local playback that is about to start.
                cloud.stop()
                applyPlaybackMode(CloudErrorUiPolicy.ON_DEVICE_FALLBACK)
                // Position is untouched, so this resumes the sentence that failed.
                session.execute(PlaybackSessionCommand.Play)
            }
            // The panel is already cleared at acceptance; nothing else to undo.
            ServiceCommand.DismissCloudError -> Unit
            ServiceCommand.PreviewCloudVoice -> {
                session.execute(PlaybackSessionCommand.Pause)
                previewActive = true
                applyLifecyclePolicy(session.state.value.status)
                val ui = mutableUi.value
                cloud.speak("안녕하세요. Google Cloud 음성 미리듣기입니다.", CLOUD_PREVIEW_ID, ui.playback.speed, ui.cloudVoice.id, settings.apiKey())
            }
            is ServiceCommand.ReplaceDocument->{flushPersistence();session.execute(PlaybackSessionCommand.Pause);update{copy(documentId=command.documentId,documentTitle=command.title)};session.execute(PlaybackSessionCommand.ReplaceDocument(command.sentences,command.index,command.speed))}
            is ServiceCommand.ShowStatus->update { copy(ttsStatus = command.message) }
        }
    }
    private fun update(block:PlaybackServiceUiState.()->PlaybackServiceUiState){mutableUi.value=mutableUi.value.block()}

    /**
     * Selects the playback mode and the voice that mode supports, persisting both. Shared by an
     * explicit mode change and the error panel's phone-voice fallback so the two cannot drift.
     */
    private fun applyPlaybackMode(mode: PlaybackMode) {
        val voice = CloudVoiceCatalog.resolveForMode(mode, mutableUi.value.cloudVoice.id)
        prefs.edit().putString("mode", mode.name).putString("voice", voice.id).apply()
        update { copy(playbackMode = mode, cloudVoice = voice, ttsStatus = "재생 모드: ${mode.label}") }
    }
    private fun enqueuePersistence(request: PlaybackPersistenceRequest) {
        // Gate out delayed/debounced producer writes once terminal finalization has begun, so nothing
        // enqueues behind the terminal barrier and overwrites the final position.
        if (!producerGate.accept()) return
        // submit() returns false only when the writer is closed (service terminating/destroyed); the
        // terminal barrier already persisted the final position, so a dropped late save is expected.
        writer.submit(request)
    }
    private fun flushPersistence() {
        persistence.observe(mutableUi.value.documentId, session.state.value)
        persistence.snapshotForFlush()?.let(::enqueuePersistence)
    }

    private fun onSpeechDone(utteranceId: String) {
        if (utteranceId == LOCAL_PREVIEW_ID || utteranceId == CLOUD_PREVIEW_ID) {
            previewActive = false
            applyLifecyclePolicy(session.state.value.status)
            return
        }
        scope.launch {
            delay(180)
            session.onSentenceDone(utteranceId)
        }
    }

    private fun applyLifecyclePolicy(status: PlaybackStatus) {
        // Gate deduplicates non-terminal transitions but always lets the terminal stop through, even
        // though Paused/Idle/Completed share the same ServiceLifecycleDecision(false, false).
        when (lifecycleGate.next(status, previewActive) ?: return) {
            ServiceLifecycleAction.StartForeground -> {
                startAsForeground(mutableUi.value)
                syncStartMode()
            }
            ServiceLifecycleAction.ReleaseForegroundAndSync -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                syncStartMode()
            }
            // Final sentence finished: persist the completed position, then release foreground and
            // terminate. Never re-start the service in the background — doing so violates Android
            // background-start rules and Samsung Device Care reports it as an abnormal termination
            // while the screen is locked.
            ServiceLifecycleAction.StopService -> finalizeCompletion()
        }
    }

    private fun finalizeCompletion() {
        // Close the producer gate synchronously so no debounced save can enqueue behind the barrier.
        producerGate.beginTerminal()
        // Capture the generation token synchronously at completion detection; a revival accepted after
        // this point bumps the generation and the stale finalizer will skip stop.
        val token = terminalCompletion.begin() ?: return
        val snapshot = run {
            persistence.observe(mutableUi.value.documentId, session.state.value)
            persistence.snapshotForFlush()
        }
        scope.launch { terminalCompletion.finalize(token, snapshot) }
    }

    /** Commands that resume/replace playback (or start a preview) after a completion, invalidating any pending finalization. */
    private fun ServiceCommand.revivesPlayback(): Boolean = when (this) {
        is ServiceCommand.Play,
        is ServiceCommand.Previous,
        is ServiceCommand.Next,
        is ServiceCommand.Jump,
        is ServiceCommand.ReplaceDocument,
        is ServiceCommand.PreviewLocalVoice,
        is ServiceCommand.PreviewCloudVoice,
        // Both resume playback, so a pending terminal finalization must not stop the service first.
        is ServiceCommand.RetryCloudSentence,
        is ServiceCommand.UseOnDeviceVoiceForCurrentSentence -> true
        else -> false
    }

    private fun syncStartMode() {
        startService(Intent(this, TtsPlaybackService::class.java).setAction(ACTION_SYNC_START_MODE))
    }

    private fun setupMediaSession(){mediaSession=MediaSessionCompat(this,"ListenStudyMediaSession").apply{setCallback(object:MediaSessionCompat.Callback(){override fun onPlay()=execute(ServiceCommand.Play);override fun onPause()=execute(ServiceCommand.Pause);override fun onSkipToPrevious()=execute(ServiceCommand.Previous);override fun onSkipToNext()=execute(ServiceCommand.Next)});isActive=true}}
    private fun labels(ui:PlaybackServiceUiState):Pair<String,String>{val s=when(ui.playback.status){PlaybackStatus.Playing->"재생 중";PlaybackStatus.Paused->"일시정지";PlaybackStatus.Completed->"완료";PlaybackStatus.Idle->"대기 중"};return s to "${if(ui.playback.sentences.isEmpty())0 else ui.playback.currentIndex+1}/${ui.playback.sentences.size} · ${ui.playback.speed}x"}
    private fun updateMediaSession(ui: PlaybackServiceUiState) {
        val (statusLabel, progressLabel) = labels(ui)
        val playbackState = when (ui.playback.status) {
            PlaybackStatus.Playing -> PlaybackStateCompat.STATE_PLAYING
            PlaybackStatus.Paused -> PlaybackStateCompat.STATE_PAUSED
            PlaybackStatus.Completed -> PlaybackStateCompat.STATE_STOPPED
            PlaybackStatus.Idle -> PlaybackStateCompat.STATE_NONE
        }
        val timeline = MediaSessionTimeline.from(
            sentences = ui.playback.sentences,
            currentIndex = ui.playback.currentIndex,
            completed = ui.playback.status == PlaybackStatus.Completed,
        )
        val playbackSpeed = if (ui.playback.status == PlaybackStatus.Playing) ui.playback.speed else 0f

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                )
                .setState(playbackState, timeline.positionMs, playbackSpeed)
                .build(),
        )
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, ui.documentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "ListenStudy")
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM,
                    progressLabel.ifBlank { statusLabel },
                )
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, timeline.durationMs)
                .build(),
        )
    }
    private fun buildNotification(ui:PlaybackServiceUiState):Notification{val(s,p)=labels(ui);val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP},pendingFlags());return NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_listenstudy).setContentTitle(ui.documentTitle).setContentText(PlaybackNotificationFormatter.mediaSubtitle(s,p)).setSubText(p).setStyle(MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0,1,2)).setContentIntent(open).setOngoing(ui.playback.status==PlaybackStatus.Playing).setSilent(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_TRANSPORT).setPriority(NotificationCompat.PRIORITY_LOW).addAction(R.drawable.ic_skip_previous,"이전",commandIntent(PlaybackServiceCommand.ACTION_PREVIOUS,1)).addAction(if(ui.playback.status==PlaybackStatus.Playing)R.drawable.ic_pause else R.drawable.ic_play,PlaybackNotificationFormatter.playPauseLabel(s),commandIntent(PlaybackServiceCommand.ACTION_PLAY_PAUSE,2)).addAction(R.drawable.ic_skip_next,"다음",commandIntent(PlaybackServiceCommand.ACTION_NEXT,3)).build()}
    private fun startAsForeground(ui:PlaybackServiceUiState){val n=buildNotification(ui);if(Build.VERSION.SDK_INT>=29)ServiceCompat.startForeground(this,NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)else startForeground(NOTIFICATION_ID,n)}
    private fun commandIntent(action:String,code:Int)=PendingIntent.getService(this,code,Intent(this,TtsPlaybackService::class.java).setAction(action),pendingFlags())
    private fun pendingFlags()=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=23)PendingIntent.FLAG_IMMUTABLE else 0
    private fun notificationManager()=getSystemService(NotificationManager::class.java)
    private fun createNotificationChannel(){if(Build.VERSION.SDK_INT>=26)notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID,"ListenStudy 재생",NotificationManager.IMPORTANCE_LOW).apply{description="ListenStudy 백그라운드 TTS 재생 알림";setShowBadge(false)})}
    override fun onDestroy(){flushPersistence();writer.close();PlaybackServiceConnection.detach(owner);scope.cancel();session.stop();local.shutdown();cloud.shutdown();mediaSession?.run{isActive=false;release()};mediaSession=null;super.onDestroy()}
    companion object {
        private const val CHANNEL_ID = "listenstudy_playback"
        private const val NOTIFICATION_ID = 1001
        private const val LOCAL_PREVIEW_ID = "listenstudy_voice_preview"
        // Bound to the router's constant: if these two ever drift, preview failures silently start
        // raising recovery panels on the reader again.
        private const val CLOUD_PREVIEW_ID = CloudErrorRouter.CLOUD_PREVIEW_UTTERANCE_ID
        private const val ACTION_SYNC_START_MODE = "com.codro.listenstudy.action.SYNC_START_MODE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TtsPlaybackService::class.java))
        }
    }
}
