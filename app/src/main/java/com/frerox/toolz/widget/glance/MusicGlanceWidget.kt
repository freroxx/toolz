/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.frerox.toolz.R
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_FAVORITE
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_NEXT
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_PREV
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_REPEAT
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_SEEK
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_SHUFFLE
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_TOGGLE
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.PARAM_ACTION
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.PARAM_INDEX
import com.frerox.toolz.widget.ui.WidgetOuterCorner
import com.frerox.toolz.widget.ui.decodeWidgetArt
import com.frerox.toolz.widget.ui.isColorDark
import com.frerox.toolz.widget.ui.readWidgetAppearance
import com.frerox.toolz.widget.ui.resolveWidgetAccent
import com.frerox.toolz.widget.ui.toWidgetColorProvider
import com.frerox.toolz.widget.ui.widgetBackgroundProvider
import com.frerox.toolz.widget.ui.widgetNavIntent

// ---------------------------------------------------------------------------
//  Music Glance widget — Material 3 Expressive.
//  Two breakpoints driven by the live canvas (SizeMode.Exact):
//    COMPACT  — art, title/artist, play, inset progress
//    EXPANDED — art, title, transport, progress + time, Up Next (max 3 rows)
//  Outer container is never clickable (nested clickables break on Samsung /
//  Pixel launchers); art + titles carry the open-app action as siblings.
// ---------------------------------------------------------------------------

class MusicGlanceWidget : GlanceAppWidget() {

    companion object {
        // Reference sizes for the widget picker + preview rendering.
        private val COMPACT = DpSize(180.dp, 110.dp)
        private val EXPANDED = DpSize(270.dp, 210.dp)

        private val OUTER_CORNER_RADIUS = WidgetOuterCorner
    }

    // Exact: launcher stretches the single layout to the real canvas and
    // LocalSize drives the compact/expanded breakpoint — every intermediate
    // resize step re-lays out instead of snapping between fixed sizes.
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED))
    override val stateDefinition = MusicWidgetStateDefinition

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(OUTER_CORNER_RADIUS),
                    contentAlignment = Alignment.TopStart
                ) {
                    CompactMusicContent(
                        title = "Midnight Drive",
                        artist = "Neon Coast",
                        isPlaying = true,
                        artBitmap = null,
                        artShape = "SQUIRCLE",
                        accentColor = null,
                        progress = 0.42f,
                        isFavorite = true,
                        openMusicIntent = widgetNavIntent(context, "music_player")
                    )
                }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, MusicWidgetStateDefinition, id)
        val title = prefs[MusicWidgetState.KEY_TITLE]?.takeIf { it.isNotBlank() } ?: "Not Playing"
        val artist = prefs[MusicWidgetState.KEY_ARTIST]?.takeIf { it.isNotBlank() } ?: "Tap to open Toolz"
        val playing = prefs[MusicWidgetState.KEY_PLAYING] ?: false
        val artPath = prefs[MusicWidgetState.KEY_ART_PATH]
        val artShape = prefs[MusicWidgetState.KEY_ART_SHAPE] ?: "CIRCLE"
        val isFavorite = prefs[MusicWidgetState.KEY_IS_FAVORITE] ?: false
        val accentHex = prefs[MusicWidgetState.KEY_ACCENT_COLOR]
        val hasNext = prefs[MusicWidgetState.KEY_HAS_NEXT] ?: false
        val hasPrev = prefs[MusicWidgetState.KEY_HAS_PREV] ?: false
        val isShuffle = prefs[MusicWidgetState.KEY_IS_SHUFFLE] ?: false
        val repeatMode = prefs[MusicWidgetState.KEY_REPEAT_MODE] ?: 0

        val positionAtCaptureMs = prefs[MusicWidgetState.KEY_POSITION_MS] ?: 0L
        val durationMs = prefs[MusicWidgetState.KEY_DURATION_MS] ?: 0L
        val capturedAtElapsedMs = prefs[MusicWidgetState.KEY_CAPTURED_AT_ELAPSED_MS] ?: 0L
        val queue = decodeQueueJson(prefs[MusicWidgetState.KEY_QUEUE_JSON])

        val appearance = try { readWidgetAppearance(context) } catch (_: Exception) {
            com.frerox.toolz.widget.ui.WidgetAppearance()
        }
        val artBitmap = if (appearance.showArt) decodeWidgetArt(artPath) else null
        val accentColor = resolveWidgetAccent(accentHex, appearance)
        val openMusicIntent = widgetNavIntent(context, "music_player")

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val liveProgress = liveProgressFraction(
            positionAtCaptureMs = positionAtCaptureMs,
            durationMs = durationMs,
            capturedAtElapsedMs = capturedAtElapsedMs,
            isPlaying = playing,
            nowElapsedMs = nowElapsedMs
        )
        val livePositionMs = if (playing) {
            (positionAtCaptureMs + (nowElapsedMs - capturedAtElapsedMs).coerceAtLeast(0L))
                .coerceIn(0L, durationMs.coerceAtLeast(0L))
        } else {
            positionAtCaptureMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        }
        val positionLabel = formatTime(livePositionMs)
        val durationLabel = formatTime(durationMs)

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val isExpanded = size.width >= 250.dp && size.height >= 170.dp

                val outerBg = widgetBackgroundProvider(appearance)
                    ?: GlanceTheme.colors.surface
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(outerBg)
                        .cornerRadius(OUTER_CORNER_RADIUS),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (isExpanded) {
                        ExpandedMusicContent(
                            title = title,
                            artist = artist,
                            progress = liveProgress,
                            positionLabel = positionLabel,
                            durationLabel = durationLabel,
                            isPlaying = playing,
                            artBitmap = artBitmap,
                            artShape = artShape,
                            isFavorite = isFavorite,
                            accentColor = accentColor,
                            hasNext = hasNext,
                            hasPrev = hasPrev,
                            isShuffle = isShuffle,
                            repeatMode = repeatMode,
                            queue = if (appearance.showQueue) queue else emptyList(),
                            openMusicIntent = openMusicIntent
                        )
                    } else {
                        CompactMusicContent(
                            title = title,
                            artist = artist,
                            isPlaying = playing,
                            artBitmap = artBitmap,
                            artShape = artShape,
                            accentColor = accentColor,
                            progress = liveProgress,
                            isFavorite = isFavorite,
                            openMusicIntent = openMusicIntent
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Helpers
// ---------------------------------------------------------------------------

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** Resolves art provider, fallback to music_note glyph. Production: never null. */
@Composable
private fun rememberArtProvider(artBitmap: android.graphics.Bitmap?): ImageProvider =
    artBitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_note)

@Composable
private fun TransportButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    size: Dp,
    iconSize: Dp,
    backgroundColor: ColorProvider,
    iconTint: ColorProvider,
    action: String
) {
    // Disabled-safe: no clickable when disabled — prevents dead taps.
    var mod = GlanceModifier
        .size(size)
        .cornerRadius(size / 2)
        .background(backgroundColor)
    if (enabled) {
        mod = mod.clickable(
            actionRunCallback<MusicActionCallback>(
                actionParametersOf(PARAM_ACTION to action)
            )
        )
    }
    Box(
        modifier = mod,
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = androidx.glance.ColorFilter.tint(
                if (enabled) iconTint else GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, size: Dp, accentColor: Color?) {
    val iconRes = if (isFavorite) R.drawable.ic_widget_favorite_filled else R.drawable.ic_widget_favorite_outline
    val bg = if (isFavorite) (accentColor?.copy(alpha = 0.12f)?.toWidgetColorProvider() ?: GlanceTheme.colors.primaryContainer)
             else GlanceTheme.colors.surfaceVariant
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(bg)
            .clickable(
                actionRunCallback<MusicActionCallback>(
                    actionParametersOf(PARAM_ACTION to ACTION_FAVORITE)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            modifier = GlanceModifier.size(size * 0.52f),
            colorFilter = androidx.glance.ColorFilter.tint(
                if (isFavorite) Color(0xFFE0555C).toWidgetColorProvider() else GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    accentColor: Color?,
    size: Dp,
    iconSize: Dp
) {
    // M3 Expressive: squircle FAB in the accent container.
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size * 0.35f)
            .background(accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary)
            .clickable(
                actionRunCallback<MusicActionCallback>(
                    actionParametersOf(PARAM_ACTION to ACTION_TOGGLE)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = GlanceModifier.size(iconSize),
            colorFilter = androidx.glance.ColorFilter.tint(
                if (accentColor != null && isColorDark(accentColor)) Color.White.toWidgetColorProvider() else GlanceTheme.colors.onPrimary
            )
        )
    }
}

@Composable
private fun TimeLabel(text: String, color: ColorProvider) {
    Text(
        text,
        style = androidx.glance.text.TextStyle(
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        ),
        maxLines = 1
    )
}

// ---------------------------------------------------------------------------
//  Compact tier — art, titles, play, inset progress
// ---------------------------------------------------------------------------

@Composable
private fun CompactMusicContent(
    title: String,
    artist: String,
    isPlaying: Boolean,
    artBitmap: android.graphics.Bitmap?,
    artShape: String,
    accentColor: Color?,
    progress: Float,
    isFavorite: Boolean,
    openMusicIntent: Intent
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artProvider = rememberArtProvider(artBitmap)
            val cornerDp = when (artShape) {
                "CIRCLE" -> 28.dp
                "SQUIRCLE", "SQUARE_ROUNDED" -> 20.dp
                else -> 12.dp
            }
            // Art — taps open the player (sibling of transport, never a parent).
            Box(
                modifier = GlanceModifier.size(52.dp).cornerRadius(cornerDp)
                    .background(accentColor?.copy(alpha = 0.12f)?.toWidgetColorProvider() ?: GlanceTheme.colors.surfaceVariant)
                    .clickable(actionStartActivity(openMusicIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = artProvider, contentDescription = "Album art",
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerDp), contentScale = ContentScale.Crop
                )
            }

            Spacer(GlanceModifier.width(10.dp))

            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    .clickable(actionStartActivity(openMusicIntent))
            ) {
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, maxLines = 1, style = TextStyle(
                            color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    )
                    if (isFavorite) {
                        Spacer(GlanceModifier.width(4.dp))
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_favorite_filled),
                            contentDescription = null,
                            modifier = GlanceModifier.size(10.dp),
                            colorFilter = androidx.glance.ColorFilter.tint(Color(0xFFE0555C).toWidgetColorProvider())
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlaying) {
                        Box(modifier = GlanceModifier.size(6.dp).cornerRadius(3.dp).background(Color(0xFF4CAF50).toWidgetColorProvider())) {}
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(
                        artist, maxLines = 1, style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
            }

            Spacer(GlanceModifier.width(8.dp))

            PlayPauseButton(
                isPlaying = isPlaying,
                accentColor = accentColor,
                size = 48.dp,
                iconSize = 22.dp
            )
        }
        // Inset progress: 12dp horizontal + 10dp bottom so the bar never
        // clips under the 28dp outer corners. Display only — never a seek target.
        Box(modifier = GlanceModifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                color = accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Expanded tier — header + transport + progress + time + Up Next
// ---------------------------------------------------------------------------

@Composable
private fun ExpandedMusicContent(
    title: String,
    artist: String,
    progress: Float,
    positionLabel: String,
    durationLabel: String,
    isPlaying: Boolean,
    artBitmap: android.graphics.Bitmap?,
    artShape: String,
    isFavorite: Boolean,
    accentColor: Color?,
    hasNext: Boolean,
    hasPrev: Boolean,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    queue: List<QueueTrackInfo> = emptyList(),
    openMusicIntent: Intent
) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        NowPlayingHeader(
            title = title,
            artist = artist,
            artBitmap = artBitmap,
            artShape = artShape,
            accentColor = accentColor,
            isFavorite = isFavorite,
            isPlaying = isPlaying,
            artSize = 60.dp,
            titleFontSize = 15.sp,
            favoriteButtonSize = 36.dp,
            openMusicIntent = openMusicIntent
        )

        Spacer(GlanceModifier.height(8.dp))
        TransportRow(
            isPlaying = isPlaying,
            accentColor = accentColor,
            hasNext = hasNext,
            hasPrev = hasPrev,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            secondaryButtonSize = 40.dp,
            secondaryIconSize = 18.dp,
            playButtonSize = 52.dp,
            playIconSize = 24.dp
        )
        Spacer(GlanceModifier.height(8.dp))

        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(positionLabel, GlanceTheme.colors.onSurfaceVariant)
            Spacer(GlanceModifier.width(8.dp))
            Box(modifier = GlanceModifier.defaultWeight()) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            TimeLabel(durationLabel, GlanceTheme.colors.onSurfaceVariant)
        }

        if (queue.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "UP NEXT",
                    style = TextStyle(
                        color = accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    "• ${queue.size}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    if (isShuffle) "Shuffle on" else "Tap to play",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().height(48.dp)) {
                itemsIndexed(
                    items = queue.take(3),
                    itemId = { _, track -> track.mediaId.hashCode().toLong() }
                ) { _, track ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable(actionRunCallback<MusicActionCallback>(actionParametersOf(PARAM_ACTION to ACTION_SEEK, PARAM_INDEX to track.queueIndex))),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = GlanceModifier.size(5.dp).cornerRadius(2.dp).background(accentColor?.copy(alpha = 0.5f)?.toWidgetColorProvider() ?: GlanceTheme.colors.onSurfaceVariant)) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                track.title,
                                maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            )
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            track.artist,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }
        } else {
            Spacer(GlanceModifier.height(6.dp))
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(28.dp)
                    .background(GlanceTheme.colors.surfaceVariant).cornerRadius(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Queue empty — add tracks from Library",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                )
            }
        }
    }
}

@Composable
private fun QueueRow(track: QueueTrackInfo, accentColor: Color?, index: Int = -1) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(
                actionRunCallback<MusicActionCallback>(
                    actionParametersOf(
                        PARAM_ACTION to ACTION_SEEK,
                        PARAM_INDEX to track.queueIndex
                    )
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index > 0) {
            Box(
                modifier = GlanceModifier.size(20.dp).cornerRadius(10.dp)
                    .background(accentColor?.copy(alpha = 0.12f)?.toWidgetColorProvider() ?: GlanceTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$index",
                    style = TextStyle(color = accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                )
            }
        } else {
            Box(
                modifier = GlanceModifier.size(6.dp).cornerRadius(3.dp)
                    .background(accentColor?.copy(alpha = 0.5f)?.toWidgetColorProvider() ?: GlanceTheme.colors.onSurfaceVariant)
            ) {}
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                track.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                track.artist,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier.size(18.dp).cornerRadius(9.dp)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_next),
                contentDescription = "Play",
                modifier = GlanceModifier.size(10.dp),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Shared header + transport row
// ---------------------------------------------------------------------------

@Composable
private fun NowPlayingHeader(
    title: String,
    artist: String,
    artBitmap: android.graphics.Bitmap?,
    artShape: String,
    accentColor: Color?,
    isFavorite: Boolean,
    isPlaying: Boolean = false,
    artSize: Dp,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    favoriteButtonSize: Dp,
    openMusicIntent: Intent
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artProvider = rememberArtProvider(artBitmap)
        val cornerDp = when (artShape) {
            "CIRCLE" -> artSize / 2
            "SQUIRCLE", "SQUARE_ROUNDED" -> artSize * 0.32f
            else -> 14.dp
        }

        Box(
            modifier = GlanceModifier.size(artSize).cornerRadius(cornerDp)
                .background(accentColor?.copy(alpha = 0.10f)?.toWidgetColorProvider() ?: GlanceTheme.colors.surfaceVariant)
                .clickable(actionStartActivity(openMusicIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = artProvider, contentDescription = "Album art",
                modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerDp), contentScale = ContentScale.Crop
            )
        }

        Spacer(GlanceModifier.width(12.dp))

        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                .clickable(actionStartActivity(openMusicIntent))
        ) {
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, maxLines = 1, style = TextStyle(
                        color = GlanceTheme.colors.onSurface, fontSize = titleFontSize, fontWeight = FontWeight.Bold
                    )
                )
                if (isPlaying) {
                    Spacer(GlanceModifier.width(6.dp))
                    Box(modifier = GlanceModifier.size(7.dp).cornerRadius(3.dp).background(Color(0xFF4CAF50).toWidgetColorProvider())) {}
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    artist, maxLines = 1, style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                )
                if (isFavorite) {
                    Spacer(GlanceModifier.width(4.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_favorite_filled),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(Color(0xFFE0555C).toWidgetColorProvider())
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.width(8.dp))

        FavoriteButton(isFavorite = isFavorite, size = favoriteButtonSize, accentColor = accentColor)
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    accentColor: Color?,
    hasNext: Boolean,
    hasPrev: Boolean,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    secondaryButtonSize: Dp,
    secondaryIconSize: Dp,
    playButtonSize: Dp,
    playIconSize: Dp
) {
    // Glance hard limit: max 10 children per Row. Outer Row has 5 children
    // (left cluster, spacer, play, spacer, right cluster); each cluster is a
    // nested Row of 3. Never nests clickables.
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(40.dp).cornerRadius(20.dp)
                    .background(
                        if (isShuffle) accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary
                        else GlanceTheme.colors.surfaceVariant
                    )
                    .clickable(actionRunCallback<MusicActionCallback>(actionParametersOf(PARAM_ACTION to ACTION_SHUFFLE))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_shuffle),
                    contentDescription = if (isShuffle) "Shuffle on" else "Shuffle off",
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(
                        if (isShuffle) (if (accentColor != null && isColorDark(accentColor)) Color.White.toWidgetColorProvider() else GlanceTheme.colors.onPrimary)
                        else GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            TransportButton(
                iconRes = R.drawable.ic_widget_prev,
                contentDescription = "Previous track",
                enabled = hasPrev,
                size = secondaryButtonSize,
                iconSize = secondaryIconSize,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                iconTint = GlanceTheme.colors.onSurface,
                action = ACTION_PREV
            )
        }

        Spacer(GlanceModifier.width(8.dp))

        PlayPauseButton(
            isPlaying = isPlaying,
            accentColor = accentColor,
            size = playButtonSize,
            iconSize = playIconSize
        )

        Spacer(GlanceModifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TransportButton(
                iconRes = R.drawable.ic_widget_next,
                contentDescription = "Next track",
                enabled = hasNext,
                size = secondaryButtonSize,
                iconSize = secondaryIconSize,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                iconTint = GlanceTheme.colors.onSurface,
                action = ACTION_NEXT
            )
            Spacer(GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier.size(40.dp).cornerRadius(20.dp)
                    .background(
                        if (repeatMode != 0) accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary
                        else GlanceTheme.colors.surfaceVariant
                    )
                    .clickable(actionRunCallback<MusicActionCallback>(actionParametersOf(PARAM_ACTION to ACTION_REPEAT))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(if (repeatMode == 1) R.drawable.ic_widget_repeat_one else R.drawable.ic_widget_repeat),
                    contentDescription = when (repeatMode) { 1 -> "Repeat one" ; 2 -> "Repeat all" ; else -> "Repeat off" },
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(
                        if (repeatMode != 0) (if (accentColor != null && isColorDark(accentColor)) Color.White.toWidgetColorProvider() else GlanceTheme.colors.onPrimary)
                        else GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}
