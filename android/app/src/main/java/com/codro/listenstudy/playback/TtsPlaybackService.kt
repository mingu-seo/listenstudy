package com.codro.listenstudy.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.codro.listenstudy.MainActivity
import com.codro.listenstudy.R
import com.codro.listenstudy.domain.player.PlaybackNotificationFormatter
import com.codro.listenstudy.domain.player.PlaybackServiceCommand

class TtsPlaybackService : Service() {
    private var title: String = "ListenStudy"
    private var status: String = "학습 자료 재생 준비"
    private var progress: String = ""
    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "ListenStudyMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = PlaybackCommandBus.dispatch(PlaybackServiceCommand.PlayPause)
                override fun onPause() = PlaybackCommandBus.dispatch(PlaybackServiceCommand.PlayPause)
                override fun onSkipToPrevious() = PlaybackCommandBus.dispatch(PlaybackServiceCommand.Previous)
                override fun onSkipToNext() = PlaybackCommandBus.dispatch(PlaybackServiceCommand.Next)
            })
            isActive = true
        }
        updateMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        PlaybackServiceCommand.fromAction(action)?.let { command ->
            PlaybackCommandBus.dispatch(command)
        }

        if (action == PlaybackServiceCommand.ACTION_UPDATE || PlaybackServiceCommand.fromAction(action) != null || action == null) {
            title = intent?.getStringExtra(PlaybackServiceCommand.EXTRA_TITLE) ?: title
            status = intent?.getStringExtra(PlaybackServiceCommand.EXTRA_STATUS) ?: status
            progress = intent?.getStringExtra(PlaybackServiceCommand.EXTRA_PROGRESS) ?: progress
            updateMediaSession()
            startAsForeground()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags(),
        )
        val subtitle = PlaybackNotificationFormatter.mediaSubtitle(status, progress)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_listenstudy)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(progress)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setContentIntent(openAppIntent)
            .setOngoing(status == "재생 중")
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_skip_previous, "이전", commandPendingIntent(PlaybackServiceCommand.ACTION_PREVIOUS, 1))
            .addAction(
                if (status == "재생 중") R.drawable.ic_pause else R.drawable.ic_play,
                PlaybackNotificationFormatter.playPauseLabel(status),
                commandPendingIntent(PlaybackServiceCommand.ACTION_PLAY_PAUSE, 2),
            )
            .addAction(R.drawable.ic_skip_next, "다음", commandPendingIntent(PlaybackServiceCommand.ACTION_NEXT, 3))
            .build()
    }

    private fun updateMediaSession() {
        val playbackState = when (status) {
            "재생 중" -> PlaybackStateCompat.STATE_PLAYING
            "일시정지" -> PlaybackStateCompat.STATE_PAUSED
            "완료" -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_NONE
        }
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                )
                .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build(),
        )
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "ListenStudy")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, progress.ifBlank { status })
                .build(),
        )
    }

    private fun commandPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TtsPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ListenStudy 재생",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ListenStudy 백그라운드 TTS 재생 알림"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "listenstudy_playback"
        private const val NOTIFICATION_ID = 1001

        fun startOrUpdate(context: Context, title: String, status: String, progress: String) {
            val intent = Intent(context, TtsPlaybackService::class.java).apply {
                action = PlaybackServiceCommand.ACTION_UPDATE
                putExtra(PlaybackServiceCommand.EXTRA_TITLE, title)
                putExtra(PlaybackServiceCommand.EXTRA_STATUS, status)
                putExtra(PlaybackServiceCommand.EXTRA_PROGRESS, progress)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
