package com.frerox.toolz.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.ImageLoader
import coil.request.ImageRequest
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.widget.MusicWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var widgetUpdateJob: Job? = null
    
    @Inject
    lateinit var player: ExoPlayer

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "music_player")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateWidget()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget()
                if (isPlaying) startWidgetTimer() else stopWidgetTimer()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateWidget()
            }
        })
        
        if (player.isPlaying) startWidgetTimer()
        updateWidget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "TOGGLE_PLAY" -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            "SKIP_NEXT" -> player.seekToNext()
            "SKIP_PREV" -> player.seekToPrevious()
        }
        updateWidget()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startWidgetTimer() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = serviceScope.launch {
            while (isActive) {
                updateWidget()
                delay(1000)
            }
        }
    }

    private fun stopWidgetTimer() {
        widgetUpdateJob?.cancel()
        updateWidget()
    }

    private fun updateWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, MusicWidgetProvider::class.java)
        val views = RemoteViews(packageName, R.layout.music_widget)

        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            views.setTextViewText(R.id.widget_music_title, currentItem.mediaMetadata.title ?: "Unknown Title")
            views.setTextViewText(R.id.widget_music_artist, currentItem.mediaMetadata.artist ?: "Unknown Artist")
            
            val duration = player.duration
            val position = player.currentPosition
            val progress = if (duration > 0) (position * 100 / duration).toInt() else 0
            views.setProgressBar(R.id.widget_music_progress, 100, progress, false)
            
            views.setImageViewResource(R.id.widget_music_play_pause, 
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

            val artUri = currentItem.mediaMetadata.artworkUri
            if (artUri != null) {
                serviceScope.launch {
                    val bitmap = loadBitmap(artUri.toString())
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_music_album_art, bitmap)
                    } else {
                        views.setImageViewResource(R.id.widget_music_album_art, R.drawable.ic_music_note)
                    }
                    // Re-apply intents and update
                    setupIntents(views)
                    appWidgetManager.updateAppWidget(componentName, views)
                }
            } else {
                views.setImageViewResource(R.id.widget_music_album_art, R.drawable.ic_music_note)
                setupIntents(views)
                appWidgetManager.updateAppWidget(componentName, views)
            }
        } else {
            views.setTextViewText(R.id.widget_music_title, "NOT PLAYING")
            views.setTextViewText(R.id.widget_music_artist, "Tap to open Toolz")
            views.setProgressBar(R.id.widget_music_progress, 100, 0, false)
            views.setImageViewResource(R.id.widget_music_play_pause, android.R.drawable.ic_media_play)
            views.setImageViewResource(R.id.widget_music_album_art, R.drawable.ic_music_note)
            setupIntents(views)
            appWidgetManager.updateAppWidget(componentName, views)
        }
    }

    private fun setupIntents(views: RemoteViews) {
        val playIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "TOGGLE_PLAY" }
        views.setOnClickPendingIntent(R.id.widget_music_play_pause, PendingIntent.getBroadcast(this, 1, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val nextIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "SKIP_NEXT" }
        views.setOnClickPendingIntent(R.id.widget_music_next, PendingIntent.getBroadcast(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val prevIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "SKIP_PREV" }
        views.setOnClickPendingIntent(R.id.widget_music_prev, PendingIntent.getBroadcast(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        // Open app when clicking on the art or text
        val appIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "music_player")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_music_album_art, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_text_container, appPendingIntent)
    }

    private suspend fun loadBitmap(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(this@MusicPlayerService)
            val request = ImageRequest.Builder(this@MusicPlayerService)
                .data(uri)
                .size(300, 300)
                .allowHardware(false)
                .build()
            val result = loader.execute(request).drawable
            (result as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
