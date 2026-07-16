package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_FAVORITE
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_NEXT
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_PREV
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_SEEK
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.ACTION_TOGGLE
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.PARAM_ACTION
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.PARAM_INDEX

// ---------------------------------------------------------------------------
//  Music Glance widget — three breakpoints:
//    COMPACT  1x1-ish minimum: art, title/artist, one play/pause tap target.
//    EXPANDED wide row: art, title/artist, transport controls, live progress.
//    HERO     tall: everything EXPANDED has, plus a scrollable Up Next queue.
//  HERO is what "expandable" means for a home-screen widget in practice —
//  Glance re-renders for whichever breakpoint the user's resize lands
//  closest to, there's no in-widget expand/collapse gesture to build.
// ---------------------------------------------------------------------------

class MusicGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT = DpSize(180.dp, 100.dp)
        private val EXPANDED = DpSize(270.dp, 120.dp)
        private val HERO = DpSize(270.dp, 280.dp)

        // Glance's GlanceModifier.cornerRadius(Dp) only accepts one
        // uniform radius — there's no per-corner variant the way
        // RoundedCornerShape has in Compose UI, and it only takes effect
        // on API 31+ (older devices render square corners). A single
        // generous radius is the most "M3 Expressive" this API can
        // actually produce; asymmetric per-corner shapes aren't available
        // for widget surfaces.
        private val OUTER_CORNER_RADIUS = 28.dp
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED, HERO))
    override val stateDefinition = MusicWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, MusicWidgetStateDefinition, id)
        val title = prefs[MusicWidgetState.KEY_TITLE] ?: "Not Playing"
        val artist = prefs[MusicWidgetState.KEY_ARTIST] ?: "Tap to open Toolz"
        val playing = prefs[MusicWidgetState.KEY_PLAYING] ?: false
        val artPath = prefs[MusicWidgetState.KEY_ART_PATH]
        val artShape = prefs[MusicWidgetState.KEY_ART_SHAPE] ?: "CIRCLE"
        val isFavorite = prefs[MusicWidgetState.KEY_IS_FAVORITE] ?: false
        val accentHex = prefs[MusicWidgetState.KEY_ACCENT_COLOR]
        val hasNext = prefs[MusicWidgetState.KEY_HAS_NEXT] ?: false
        val hasPrev = prefs[MusicWidgetState.KEY_HAS_PREV] ?: false

        val positionAtCaptureMs = prefs[MusicWidgetState.KEY_POSITION_MS] ?: 0L
        val durationMs = prefs[MusicWidgetState.KEY_DURATION_MS] ?: 0L
        val capturedAtElapsedMs = prefs[MusicWidgetState.KEY_CAPTURED_AT_ELAPSED_MS] ?: 0L
        val queue = decodeQueueJson(prefs[MusicWidgetState.KEY_QUEUE_JSON])

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

        // Evaluated once per composition — good enough for "looks live"
        // purposes since Glance recomposes on every widget update and the
        // system also nudges RemoteViews-backed progress bars along on its
        // own between pushes. See MusicWidgetSupport.liveProgressFraction.
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val liveProgress = liveProgressFraction(
            positionAtCaptureMs = positionAtCaptureMs,
            durationMs = durationMs,
            capturedAtElapsedMs = capturedAtElapsedMs,
            isPlaying = playing,
            nowElapsedMs = nowElapsedMs
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val tier = when {
                    size.height >= HERO.height - 20.dp -> WidgetTier.Hero
                    size.width >= EXPANDED.width -> WidgetTier.Expanded
                    else -> WidgetTier.Compact
                }

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(OUTER_CORNER_RADIUS)
                        .clickable(actionStartActivity(openMusicIntent)),
                    contentAlignment = Alignment.TopStart,
                ) {
                    when (tier) {
                        WidgetTier.Compact -> CompactMusicContent(
                            title = title,
                            artist = artist,
                            isPlaying = playing,
                            artBitmap = artBitmap,
                            artShape = artShape,
                            accentColor = accentColor
                        )
                        WidgetTier.Expanded -> ExpandedMusicContent(
                            title = title,
                            artist = artist,
                            progress = liveProgress,
                            isPlaying = playing,
                            artBitmap = artBitmap,
                            artShape = artShape,
                            isFavorite = isFavorite,
                            accentColor = accentColor,
                            hasNext = hasNext,
                            hasPrev = hasPrev,
                            nextTitle = queue.firstOrNull()?.title
                        )
                        WidgetTier.Hero -> HeroMusicContent(
                            title = title,
                            artist = artist,
                            progress = liveProgress,
                            isPlaying = playing,
                            artBitmap = artBitmap,
                            artShape = artShape,
                            isFavorite = isFavorite,
                            accentColor = accentColor,
                            hasNext = hasNext,
                            hasPrev = hasPrev,
                            queue = queue
                        )
                    }
                }
            }
        }
    }
}

private enum class WidgetTier { Compact, Expanded, Hero }

// ---------------------------------------------------------------------------
//  Shared building blocks
// ---------------------------------------------------------------------------

/** Resolves a Bitmap art provider, falling back to the app's music-note glyph. */
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
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(backgroundColor)
            .clickable(
                actionRunCallback<MusicActionCallback>(
                    actionParametersOf(PARAM_ACTION to action)
                )
            ),
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
private fun FavoriteButton(isFavorite: Boolean, size: Dp) {
    val iconRes = if (isFavorite) R.drawable.ic_widget_favorite_filled else R.drawable.ic_widget_favorite_outline
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
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
            modifier = GlanceModifier.size(size * 0.55f),
            colorFilter = androidx.glance.ColorFilter.tint(
                if (isFavorite) Color(0xFFE0555C).toColorProvider() else GlanceTheme.colors.onSurfaceVariant
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
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size * 0.4f) // squircle-leaning radius reads as more "expressive" than a plain circle at this size
            .background(accentColor?.toColorProvider() ?: GlanceTheme.colors.primary)
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
                if (accentColor != null && isColorDark(accentColor)) Color.White.toColorProvider() else GlanceTheme.colors.onPrimary
            )
        )
    }
}

// ---------------------------------------------------------------------------
//  Compact tier
// ---------------------------------------------------------------------------

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
        val artProvider = rememberArtProvider(artBitmap)
        val cornerDp = if (artShape == "CIRCLE") 28.dp else 14.dp

        Box(
            modifier = GlanceModifier.size(56.dp).cornerRadius(cornerDp)
                .background(accentColor?.copy(alpha = 0.15f)?.toColorProvider() ?: GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = artProvider, contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerDp), contentScale = ContentScale.Crop
            )
        }

        Spacer(GlanceModifier.width(12.dp))

        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                title, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            )
            Text(
                artist, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.width(8.dp))

        PlayPauseButton(
            isPlaying = isPlaying,
            accentColor = accentColor,
            size = 44.dp,
            iconSize = 22.dp
        )
    }
}

// ---------------------------------------------------------------------------
//  Expanded tier
// ---------------------------------------------------------------------------

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
    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        NowPlayingHeader(
            title = title,
            artist = artist,
            artBitmap = artBitmap,
            artShape = artShape,
            accentColor = accentColor,
            isFavorite = isFavorite,
            nextTitle = nextTitle,
            artSize = 68.dp,
            titleFontSize = 17.sp,
            favoriteButtonSize = 36.dp
        )

        Spacer(GlanceModifier.height(10.dp))
        TransportRow(
            isPlaying = isPlaying,
            accentColor = accentColor,
            hasNext = hasNext,
            hasPrev = hasPrev,
            secondaryButtonSize = 42.dp,
            secondaryIconSize = 20.dp,
            playButtonSize = 54.dp,
            playIconSize = 26.dp
        )
        Spacer(GlanceModifier.height(10.dp))

        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = accentColor?.let { it.toColorProvider() } ?: GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
//  Hero tier — everything Expanded has, plus a live Up Next queue.
// ---------------------------------------------------------------------------

@Composable
private fun HeroMusicContent(
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
    queue: List<QueueTrackInfo>
) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        NowPlayingHeader(
            title = title,
            artist = artist,
            artBitmap = artBitmap,
            artShape = artShape,
            accentColor = accentColor,
            isFavorite = isFavorite,
            nextTitle = null, // the queue list below already shows what's next — no need to repeat it in the header
            artSize = 64.dp,
            titleFontSize = 17.sp,
            favoriteButtonSize = 36.dp
        )

        Spacer(GlanceModifier.height(10.dp))
        TransportRow(
            isPlaying = isPlaying,
            accentColor = accentColor,
            hasNext = hasNext,
            hasPrev = hasPrev,
            secondaryButtonSize = 40.dp,
            secondaryIconSize = 19.dp,
            playButtonSize = 50.dp,
            playIconSize = 24.dp
        )
        Spacer(GlanceModifier.height(8.dp))

        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = accentColor?.let { it.toColorProvider() } ?: GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )

        Spacer(GlanceModifier.height(14.dp))

        if (queue.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Text(
                    "Queue is empty",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            }
        } else {
            Text(
                "UP NEXT",
                style = TextStyle(
                    color = accentColor?.let { it.toColorProvider() } ?: GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(bottom = 6.dp)
            )
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                itemsIndexed(
                    items = queue,
                    itemId = { _, track -> track.queueIndex.toLong() }
                ) { _, track ->
                    QueueRow(track = track, accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(track: QueueTrackInfo, accentColor: Color?) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
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
        Box(
            modifier = GlanceModifier.size(6.dp).cornerRadius(3.dp)
                .background(accentColor?.copy(alpha = 0.5f)?.toColorProvider() ?: GlanceTheme.colors.onSurfaceVariant)
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                track.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                track.artist,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Shared header + transport row (Expanded and Hero look identical here —
//  only sizes differ — so both delegate to the same two composables rather
//  than keeping two near-duplicate blocks in sync by hand).
// ---------------------------------------------------------------------------

@Composable
private fun NowPlayingHeader(
    title: String,
    artist: String,
    artBitmap: android.graphics.Bitmap?,
    artShape: String,
    accentColor: Color?,
    isFavorite: Boolean,
    nextTitle: String?,
    artSize: Dp,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    favoriteButtonSize: Dp
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artProvider = rememberArtProvider(artBitmap)
        val cornerDp = if (artShape == "CIRCLE") artSize / 2 else 16.dp

        Box(
            modifier = GlanceModifier.size(artSize).cornerRadius(cornerDp)
                .background(accentColor?.copy(alpha = 0.1f)?.toColorProvider() ?: GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = artProvider, contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerDp), contentScale = ContentScale.Crop
            )
        }

        Spacer(GlanceModifier.width(14.dp))

        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                title, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurface, fontSize = titleFontSize, fontWeight = FontWeight.Bold
                )
            )
            Text(
                artist, maxLines = 1, style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
            )

            if (nextTitle != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "Up next: $nextTitle", maxLines = 1, style = TextStyle(
                        color = accentColor?.let { it.toColorProvider() } ?: GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.width(8.dp))

        FavoriteButton(isFavorite = isFavorite, size = favoriteButtonSize)
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    accentColor: Color?,
    hasNext: Boolean,
    hasPrev: Boolean,
    secondaryButtonSize: Dp,
    secondaryIconSize: Dp,
    playButtonSize: Dp,
    playIconSize: Dp
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(GlanceModifier.defaultWeight())

        TransportButton(
            iconRes = R.drawable.ic_widget_prev,
            contentDescription = "Previous",
            enabled = hasPrev,
            size = secondaryButtonSize,
            iconSize = secondaryIconSize,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
            iconTint = GlanceTheme.colors.onSurface,
            action = ACTION_PREV
        )

        Spacer(GlanceModifier.width(14.dp))

        PlayPauseButton(
            isPlaying = isPlaying,
            accentColor = accentColor,
            size = playButtonSize,
            iconSize = playIconSize
        )

        Spacer(GlanceModifier.width(14.dp))

        TransportButton(
            iconRes = R.drawable.ic_widget_next,
            contentDescription = "Next",
            enabled = hasNext,
            size = secondaryButtonSize,
            iconSize = secondaryIconSize,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
            iconTint = GlanceTheme.colors.onSurface,
            action = ACTION_NEXT
        )

        Spacer(GlanceModifier.defaultWeight())
    }
}

private fun isColorDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}

private fun Color.toColorProvider(): ColorProvider = object : ColorProvider {
    override fun getColor(context: Context): Color = this@toColorProvider
}
