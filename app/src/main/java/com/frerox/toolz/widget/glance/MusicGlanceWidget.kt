package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.unit.ColorProvider
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R

class MusicGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT  = DpSize(180.dp, 100.dp)
        private val EXPANDED = DpSize(270.dp, 120.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED))
    override val stateDefinition = MusicWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs      = getAppWidgetState<Preferences>(context, MusicWidgetStateDefinition, id)
        val title      = prefs[MusicWidgetState.KEY_TITLE]      ?: "Not Playing"
        val artist     = prefs[MusicWidgetState.KEY_ARTIST]     ?: "Tap to open Toolz"
        val progress   = prefs[MusicWidgetState.KEY_PROGRESS]   ?: 0f
        val playing    = prefs[MusicWidgetState.KEY_PLAYING]    ?: false
        val artPath    = prefs[MusicWidgetState.KEY_ART_PATH]
        val artShape   = prefs[MusicWidgetState.KEY_ART_SHAPE]   ?: "CIRCLE"
        val isFavorite = prefs[MusicWidgetState.KEY_IS_FAVORITE] ?: false
        val accentHex  = prefs[MusicWidgetState.KEY_ACCENT_COLOR]
        val hasNext    = prefs[MusicWidgetState.KEY_HAS_NEXT]    ?: false
        val hasPrev    = prefs[MusicWidgetState.KEY_HAS_PREV]    ?: false
        val nextTitle  = prefs[MusicWidgetState.KEY_NEXT_TITLE]

        val artBitmap = artPath?.let {
            try { BitmapFactory.decodeFile(it) } catch (_: Exception) { null }
        }
        
        val accentColor = accentHex?.let { hex ->
            try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
        }

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
                        .cornerRadius(28.dp)
                        .clickable(actionStartActivity(openMusicIntent)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isExpanded) {
                        ExpandedMusicContent(
                            title       = title, 
                            artist      = artist,
                            progress    = progress, 
                            isPlaying   = playing,
                            artBitmap   = artBitmap, 
                            artShape    = artShape,
                            isFavorite  = isFavorite,
                            accentColor = accentColor,
                            hasNext     = hasNext,
                            hasPrev     = hasPrev,
                            nextTitle   = nextTitle
                        )
                    } else {
                        CompactMusicContent(
                            title       = title, 
                            artist      = artist,
                            isPlaying   = playing, 
                            artBitmap   = artBitmap, 
                            artShape    = artShape,
                            accentColor = accentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMusicContent(
    title: String, 
    artist: String, 
    isPlaying: Boolean,
    artBitmap: android.graphics.Bitmap?, 
    artShape: String,
    accentColor: Color?
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artProvider = artBitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_note)
        val cornerDp    = if (artShape == "CIRCLE") 28.dp else 12.dp

        Box(
            modifier = GlanceModifier.size(64.dp).cornerRadius(cornerDp)
                .background(accentColor?.let { ColorProvider(it.copy(alpha = 0.15f)) } ?: GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Image(provider = artProvider, contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerDp), contentScale = ContentScale.Crop)
        }

        Spacer(GlanceModifier.width(12.dp))

        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
        ) {
            Spacer(GlanceModifier.defaultWeight())
            Text(title, maxLines = 1, style = TextStyle(
                color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Text(artist, maxLines = 1, style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.width(8.dp))

        val pkg      = androidx.glance.LocalContext.current.packageName
        val receiver = android.content.ComponentName(pkg, "com.frerox.toolz.widget.glance.MusicWidgetReceiver")
        val toggleIntent = Intent(MUSIC_ACTION_TOGGLE).apply { component = receiver }

        Box(modifier = GlanceModifier.size(48.dp).cornerRadius(24.dp)
                .background(accentColor?.let { ColorProvider(it) } ?: GlanceTheme.colors.primary)
                .clickable(actionSendBroadcast(toggleIntent)),
            contentAlignment = Alignment.Center) {
            Image(provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                contentDescription = null, modifier = GlanceModifier.size(24.dp),
                colorFilter = androidx.glance.ColorFilter.tint(if (accentColor != null && isColorDark(accentColor)) ColorProvider(Color.White) else GlanceTheme.colors.onPrimary))
        }
    }
}

@Composable
private fun ExpandedMusicContent(
    title: String, 
    artist: String, 
    progress: Float, 
    isPlaying: Boolean,
    artBitmap: android.graphics.Bitmap?, 
    artShape: String,
    isFavorite: Boolean,
    accentColor: Color?,
    hasNext: Boolean,
    hasPrev: Boolean,
    nextTitle: String?
) {
    val pkg      = androidx.glance.LocalContext.current.packageName
    val receiver = android.content.ComponentName(pkg, "com.frerox.toolz.widget.glance.MusicWidgetReceiver")
    
    Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artProvider = artBitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_note)
            val cornerDp    = if (artShape == "CIRCLE") 36.dp else 16.dp

            Box(modifier = GlanceModifier.size(72.dp).cornerRadius(cornerDp)
                    .background(accentColor?.let { ColorProvider(it.copy(alpha = 0.1f)) } ?: GlanceTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center) {
                Image(provider = artProvider, contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerDp), contentScale = ContentScale.Crop)
            }

            Spacer(GlanceModifier.width(14.dp))

            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Spacer(GlanceModifier.defaultWeight())
                Text(title, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text(artist, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                
                if (nextTitle != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text("Up next: $nextTitle", maxLines = 1, style = TextStyle(
                        color = accentColor?.let { ColorProvider(it) } ?: GlanceTheme.colors.primary, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.defaultWeight())
            }

            Spacer(GlanceModifier.width(8.dp))

            // Favorite button
            val favIntent = Intent(MUSIC_ACTION_FAVORITE).apply { component = receiver }
            Box(modifier = GlanceModifier.size(40.dp).cornerRadius(20.dp)
                    .clickable(actionSendBroadcast(favIntent)),
                contentAlignment = Alignment.Center) {
                // Using ic_music_note as a placeholder for favorite icon if it doesn't exist
                Image(provider = ImageProvider(R.drawable.ic_music_note),
                    contentDescription = "Favorite",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(if (isFavorite) ColorProvider(Color(0xFFE0555C)) else ColorProvider(Color.Gray.copy(alpha = 0.4f))))
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val prevIntent   = Intent(MUSIC_ACTION_PREV).apply { component = receiver }
            val toggleIntent = Intent(MUSIC_ACTION_TOGGLE).apply { component = receiver }
            val nextIntent   = Intent(MUSIC_ACTION_NEXT).apply { component = receiver }

            Spacer(GlanceModifier.defaultWeight())

            // Previous
            Box(modifier = GlanceModifier.size(44.dp).cornerRadius(22.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(actionSendBroadcast(prevIntent)),
                contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(R.drawable.ic_widget_prev),
                    contentDescription = "Previous",
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(if (hasPrev) GlanceTheme.colors.onSurface else ColorProvider(Color.Gray)))
            }

            Spacer(GlanceModifier.width(16.dp))

            // Play / Pause
            Box(modifier = GlanceModifier.size(56.dp).cornerRadius(28.dp)
                    .background(accentColor?.let { ColorProvider(it) } ?: GlanceTheme.colors.primary)
                    .clickable(actionSendBroadcast(toggleIntent)),
                contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                    contentDescription = null, modifier = GlanceModifier.size(28.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(if (accentColor != null && isColorDark(accentColor)) ColorProvider(Color.White) else GlanceTheme.colors.onPrimary))
            }

            Spacer(GlanceModifier.width(16.dp))

            // Next
            Box(modifier = GlanceModifier.size(44.dp).cornerRadius(22.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(actionSendBroadcast(nextIntent)),
                contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(R.drawable.ic_widget_next),
                    contentDescription = "Next",
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(if (hasNext) GlanceTheme.colors.onSurface else ColorProvider(Color.Gray)))
            }

            Spacer(GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.height(10.dp))

        // Progress Bar
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = accentColor?.let { ColorProvider(it) } ?: GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )
    }
}

private fun isColorDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}
