package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R

// ---------------------------------------------------------------------------
//  Music Pill — Glance widget
//  Compact  (< 270dp wide) : thumbnail + title/artist + prev/play/skip controls
//  Expanded (≥ 270dp wide) : full art + title/artist/progress + all controls
// ---------------------------------------------------------------------------

class MusicGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT  = DpSize(180.dp, 80.dp)
        private val EXPANDED = DpSize(270.dp, 100.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED))
    override val stateDefinition = MusicWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs   = getAppWidgetState<Preferences>(context, MusicWidgetStateDefinition, id)
        val title   = prefs[MusicWidgetState.KEY_TITLE]    ?: "Not Playing"
        val artist  = prefs[MusicWidgetState.KEY_ARTIST]   ?: "Tap to open Toolz"
        val progress= prefs[MusicWidgetState.KEY_PROGRESS]  ?: 0f
        val playing = prefs[MusicWidgetState.KEY_PLAYING]  ?: false
        val artPath = prefs[MusicWidgetState.KEY_ART_PATH]
        val artShape= prefs[MusicWidgetState.KEY_ART_SHAPE] ?: "CIRCLE"

        val artBitmap = artPath?.let {
            try { BitmapFactory.decodeFile(it) } catch (_: Exception) { null }
        }

        // Intent to open the music player in the app
        val openMusicIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "music_player")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        provideContent {
            GlanceTheme {
                val size = androidx.glance.LocalSize.current
                val isExpanded = size.width >= EXPANDED.width

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity(openMusicIntent))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isExpanded) {
                        ExpandedMusicContent(
                            title     = title, artist    = artist,
                            progress  = progress, isPlaying = playing,
                            artBitmap = artBitmap, artShape  = artShape,
                            openMusicIntent = openMusicIntent,
                        )
                    } else {
                        CompactMusicContent(
                            title = title, artist = artist,
                            isPlaying = playing, artBitmap = artBitmap, artShape = artShape,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMusicContent(
    title: String, artist: String, isPlaying: Boolean,
    artBitmap: android.graphics.Bitmap?, artShape: String,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artProvider = artBitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_note)
        val cornerDp    = if (artShape == "CIRCLE") 28.dp else 12.dp

        // Album art
        Box(
            modifier = GlanceModifier.size(52.dp).cornerRadius(cornerDp)
                .background(GlanceTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(provider = artProvider, contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }

        Spacer(GlanceModifier.width(10.dp))

        // Title + artist (fills available space)
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, maxLines = 1, style = TextStyle(
                color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Text(artist, maxLines = 1, style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        }

        Spacer(GlanceModifier.width(6.dp))

        // Compact controls: Play/Pause + Skip Next
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pkg      = androidx.glance.LocalContext.current.packageName
            val receiver = android.content.ComponentName(pkg, "com.frerox.toolz.widget.glance.MusicWidgetReceiver")
            val toggleIntent = Intent(MUSIC_ACTION_TOGGLE).apply { component = receiver }
            val nextIntent   = Intent(MUSIC_ACTION_NEXT).apply { component = receiver }

            // Play / Pause
            Box(modifier = GlanceModifier.size(38.dp).cornerRadius(19.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionSendBroadcast(toggleIntent)),
                contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                    contentDescription = null, modifier = GlanceModifier.size(20.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary))
            }

            Spacer(GlanceModifier.width(6.dp))

            // Skip Next
            Box(modifier = GlanceModifier.size(34.dp).cornerRadius(17.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(actionSendBroadcast(nextIntent)),
                contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(R.drawable.ic_widget_next),
                    contentDescription = "Skip",
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
            }
        }
    }
}

@Composable
private fun ExpandedMusicContent(
    title: String, artist: String, progress: Float, isPlaying: Boolean,
    artBitmap: android.graphics.Bitmap?, artShape: String,
    openMusicIntent: Intent,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artProvider = artBitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_note)
            val cornerDp    = if (artShape == "CIRCLE") 40.dp else 16.dp

            Box(modifier = GlanceModifier.size(72.dp).cornerRadius(cornerDp)
                    .background(GlanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center) {
                Image(provider = artProvider, contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }

            Spacer(GlanceModifier.width(16.dp))

            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically) {
                
                Text(title, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text(artist, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                
                Spacer(GlanceModifier.height(10.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pkg      = androidx.glance.LocalContext.current.packageName
                    val receiver = android.content.ComponentName(pkg, "com.frerox.toolz.widget.glance.MusicWidgetReceiver")
                    val prevIntent   = Intent(MUSIC_ACTION_PREV).apply { component = receiver }
                    val toggleIntent = Intent(MUSIC_ACTION_TOGGLE).apply { component = receiver }
                    val nextIntent   = Intent(MUSIC_ACTION_NEXT).apply { component = receiver }

                    // Previous
                    Box(modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionSendBroadcast(prevIntent)),
                        contentAlignment = Alignment.Center) {
                        Image(provider = ImageProvider(R.drawable.ic_widget_prev),
                            contentDescription = "Previous",
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
                    }

                    Spacer(GlanceModifier.width(8.dp))

                    // Play / Pause (primary)
                    Box(modifier = GlanceModifier.size(44.dp).cornerRadius(22.dp)
                            .background(GlanceTheme.colors.primary)
                            .clickable(actionSendBroadcast(toggleIntent)),
                        contentAlignment = Alignment.Center) {
                        Image(provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                            contentDescription = null, modifier = GlanceModifier.size(22.dp),
                            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary))
                    }

                    Spacer(GlanceModifier.width(8.dp))

                    // Skip Next
                    Box(modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionSendBroadcast(nextIntent)),
                        contentAlignment = Alignment.Center) {
                        Image(provider = ImageProvider(R.drawable.ic_widget_next),
                            contentDescription = "Next",
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
                    }
                }
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // Progress bar at bottom
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )
    }
}

// Broadcast action constants (namespaced to avoid conflicts)
const val MUSIC_ACTION_TOGGLE = "com.frerox.toolz.WIDGET_MUSIC_TOGGLE"
const val MUSIC_ACTION_NEXT   = "com.frerox.toolz.WIDGET_MUSIC_NEXT"
const val MUSIC_ACTION_PREV   = "com.frerox.toolz.WIDGET_MUSIC_PREV"
