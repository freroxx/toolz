package com.frerox.toolz.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.frerox.toolz.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    
    @Inject
    lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(Intent(this@MusicPlayerService, MainActivity::class.java).apply {
                putExtra("navigate_to", "music_player")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent!!)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
