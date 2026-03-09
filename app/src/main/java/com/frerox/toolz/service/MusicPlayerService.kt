package com.frerox.toolz.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.widget.MusicWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import java.io.InputStream
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    
    @Inject
    lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "music_player")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateWidgets()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidgets()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateWidgets()
            }
        })
    }

    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, MusicWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(packageName, R.layout.music_widget)
        val currentTrack = player.currentMediaItem
        
        if (currentTrack != null) {
            views.setTextViewText(R.id.widget_music_title, currentTrack.mediaMetadata.title ?: "Unknown")
            views.setTextViewText(R.id.widget_music_artist, currentTrack.mediaMetadata.artist ?: "Unknown Artist")
            views.setImageViewResource(R.id.widget_music_play_pause, 
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            
            currentTrack.mediaMetadata.artworkUri?.let { uri ->
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    views.setImageViewBitmap(R.id.widget_music_album_art, bitmap)
                } catch (e: Exception) {
                    views.setImageViewResource(R.id.widget_music_album_art, android.R.drawable.ic_menu_report_image)
                }
            } ?: run {
                views.setImageViewResource(R.id.widget_music_album_art, android.R.drawable.ic_menu_report_image)
            }
        }

        val playPauseIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "TOGGLE_PLAY" }
        views.setOnClickPendingIntent(R.id.widget_music_play_pause, PendingIntent.getBroadcast(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE))

        val nextIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "SKIP_NEXT" }
        views.setOnClickPendingIntent(R.id.widget_music_next, PendingIntent.getBroadcast(this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE))

        val prevIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "SKIP_PREV" }
        views.setOnClickPendingIntent(R.id.widget_music_prev, PendingIntent.getBroadcast(this, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE))

        val openIntent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "music_player") }
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE))

        for (id in ids) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            // DO NOT release the singleton player here.
            // Just release the media session.
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
