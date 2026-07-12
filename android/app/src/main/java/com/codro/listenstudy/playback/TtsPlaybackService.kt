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
    private val persistenceQueue = Channel<PlaybackPersistenceRequest>(Channel.UNLIMITED)
    private val commandQueue = Channel<QueuedCommand>(Channel.UNLIMITED)
    private val restoreGate = PlaybackRestoreGate()
    private var previewActive = false
    private var lifecycleDecision = ServiceLifecycleDecision(foreground = false, sticky = false)

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
            session.onPlaybackError()
            applyLifecyclePolicy(session.state.value.status)
        }
        local.setOnErrorListener(playbackError::onError)
        cloud.setOnErrorListener(playbackError::onError)
        local.initialize()
        setupMediaSession()
        persistenceScope.launch {
            for (request in persistenceQueue) {
                runCatching { library.savePlayback(request.documentId, request.index, request.speed) }
                    .onSuccess { persistence.saved(request) }
            }
            db.close()
        }
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
            is ServiceCommand.SelectPlaybackMode->{session.execute(PlaybackSessionCommand.Pause);val voice=CloudVoiceCatalog.resolveForMode(command.mode,mutableUi.value.cloudVoice.id);prefs.edit().putString("mode",command.mode.name).putString("voice",voice.id).apply();update{copy(playbackMode=command.mode,cloudVoice=voice,ttsStatus="재생 모드: ${command.mode.label}")}}
            is ServiceCommand.SelectCloudVoice->{val voice=CloudVoiceCatalog.resolveForMode(mutableUi.value.playbackMode,command.voiceId);prefs.edit().putString("voice",voice.id).apply();update{copy(cloudVoice=voice)}}
            is ServiceCommand.SaveApiKey->{if(command.value.isBlank())update{copy(ttsStatus="입력한 API 키가 비어 있습니다.")}else{settings.saveApiKey(command.value);update{copy(hasCloudApiKey=true,ttsStatus="Google Cloud API 키를 비공개 앱 저장소에 저장했습니다.")}}}
            ServiceCommand.DeleteApiKey->{settings.deleteApiKey();update{copy(hasCloudApiKey=false,ttsStatus="Google Cloud API 키를 삭제했습니다.")}}
            ServiceCommand.ClearCache->{session.execute(PlaybackSessionCommand.Pause);cloud.clearCache();update{copy(cloudCacheStats=cloud.stats(),ttsStatus="클라우드 음성 캐시를 삭제했습니다.")}}
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
    private fun enqueuePersistence(request: PlaybackPersistenceRequest) {
        persistenceQueue.trySend(request)
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
        val next = PlaybackServiceLifecyclePolicy.decide(status, previewActive)
        if (next == lifecycleDecision) return
        lifecycleDecision = next
        if (next.foreground) {
            startAsForeground(mutableUi.value)
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        startService(Intent(this, TtsPlaybackService::class.java).setAction(ACTION_SYNC_START_MODE))
    }

    private fun setupMediaSession(){mediaSession=MediaSessionCompat(this,"ListenStudyMediaSession").apply{setCallback(object:MediaSessionCompat.Callback(){override fun onPlay()=execute(ServiceCommand.Play);override fun onPause()=execute(ServiceCommand.Pause);override fun onSkipToPrevious()=execute(ServiceCommand.Previous);override fun onSkipToNext()=execute(ServiceCommand.Next)});isActive=true}}
    private fun labels(ui:PlaybackServiceUiState):Pair<String,String>{val s=when(ui.playback.status){PlaybackStatus.Playing->"재생 중";PlaybackStatus.Paused->"일시정지";PlaybackStatus.Completed->"완료";PlaybackStatus.Idle->"대기 중"};return s to "${if(ui.playback.sentences.isEmpty())0 else ui.playback.currentIndex+1}/${ui.playback.sentences.size} · ${ui.playback.speed}x"}
    private fun updateMediaSession(ui:PlaybackServiceUiState){val(s,p)=labels(ui);val ps=when(ui.playback.status){PlaybackStatus.Playing->PlaybackStateCompat.STATE_PLAYING;PlaybackStatus.Paused->PlaybackStateCompat.STATE_PAUSED;PlaybackStatus.Completed->PlaybackStateCompat.STATE_STOPPED;else->PlaybackStateCompat.STATE_NONE};mediaSession?.setPlaybackState(PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT).setState(ps,PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,1f).build());mediaSession?.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE,ui.documentTitle).putString(MediaMetadataCompat.METADATA_KEY_ARTIST,"ListenStudy").putString(MediaMetadataCompat.METADATA_KEY_ALBUM,p.ifBlank{s}).build())}
    private fun buildNotification(ui:PlaybackServiceUiState):Notification{val(s,p)=labels(ui);val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP},pendingFlags());return NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_listenstudy).setContentTitle(ui.documentTitle).setContentText(PlaybackNotificationFormatter.mediaSubtitle(s,p)).setSubText(p).setStyle(MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0,1,2)).setContentIntent(open).setOngoing(ui.playback.status==PlaybackStatus.Playing).setSilent(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_TRANSPORT).setPriority(NotificationCompat.PRIORITY_LOW).addAction(R.drawable.ic_skip_previous,"이전",commandIntent(PlaybackServiceCommand.ACTION_PREVIOUS,1)).addAction(if(ui.playback.status==PlaybackStatus.Playing)R.drawable.ic_pause else R.drawable.ic_play,PlaybackNotificationFormatter.playPauseLabel(s),commandIntent(PlaybackServiceCommand.ACTION_PLAY_PAUSE,2)).addAction(R.drawable.ic_skip_next,"다음",commandIntent(PlaybackServiceCommand.ACTION_NEXT,3)).build()}
    private fun startAsForeground(ui:PlaybackServiceUiState){val n=buildNotification(ui);if(Build.VERSION.SDK_INT>=29)ServiceCompat.startForeground(this,NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)else startForeground(NOTIFICATION_ID,n)}
    private fun commandIntent(action:String,code:Int)=PendingIntent.getService(this,code,Intent(this,TtsPlaybackService::class.java).setAction(action),pendingFlags())
    private fun pendingFlags()=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=23)PendingIntent.FLAG_IMMUTABLE else 0
    private fun notificationManager()=getSystemService(NotificationManager::class.java)
    private fun createNotificationChannel(){if(Build.VERSION.SDK_INT>=26)notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID,"ListenStudy 재생",NotificationManager.IMPORTANCE_LOW).apply{description="ListenStudy 백그라운드 TTS 재생 알림";setShowBadge(false)})}
    override fun onDestroy(){flushPersistence();persistenceQueue.close();PlaybackServiceConnection.detach(owner);scope.cancel();session.stop();local.shutdown();cloud.shutdown();mediaSession?.run{isActive=false;release()};mediaSession=null;super.onDestroy()}
    companion object {
        private const val CHANNEL_ID = "listenstudy_playback"
        private const val NOTIFICATION_ID = 1001
        private const val LOCAL_PREVIEW_ID = "listenstudy_voice_preview"
        private const val CLOUD_PREVIEW_ID = "listenstudy_cloud_preview"
        private const val ACTION_SYNC_START_MODE = "com.codro.listenstudy.action.SYNC_START_MODE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TtsPlaybackService::class.java))
        }
    }
}
