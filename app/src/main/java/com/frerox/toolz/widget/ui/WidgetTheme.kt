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

package com.frerox.toolz.widget.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull

// ---------------------------------------------------------------------------
//  Shared M3 Expressive widget foundation — single source of truth.
//  All Glance widgets use these helpers so radius, touch targets,
//  disabled handling and accent contrast stay consistent.
// ---------------------------------------------------------------------------

val WidgetOuterCorner: Dp = 28.dp
val WidgetOuterCornerSmall: Dp = 20.dp
val WidgetPlaySquircleFraction: Float = 0.35f
const val WIDGET_MIN_TOUCH_DP: Int = 48

/** Adaptive outer radius — small widgets use 20dp so content never looks inset. */
fun widgetOuterCornerFor(width: Dp, height: Dp): Dp =
    if (width < 220.dp || height < 120.dp) WidgetOuterCornerSmall else WidgetOuterCorner

// ---------------------------------------------------------------------------
//  Canonical Responsive sizes — single source of truth for picker + layout.
//  Every widget uses SizeMode.Responsive with these (never Exact):
//  launcher picks the best fit, no stretch/clip on Samsung/Pixel.
//  minResize in XML must equal the smallest tier here.
// ---------------------------------------------------------------------------

object WidgetSizes {
    // Music: S 3x2 bar, M 4x2 transport, L 4x3 queue
    val MusicSmall = androidx.compose.ui.unit.DpSize(180.dp, 110.dp)
    val MusicMedium = androidx.compose.ui.unit.DpSize(270.dp, 150.dp)
    val MusicLarge = androidx.compose.ui.unit.DpSize(320.dp, 220.dp)
    // Pomodoro: square 2x2, wide 4x2, tall 2x3
    val PomoSquare = androidx.compose.ui.unit.DpSize(150.dp, 150.dp)
    val PomoWide = androidx.compose.ui.unit.DpSize(300.dp, 160.dp)
    val PomoTall = androidx.compose.ui.unit.DpSize(180.dp, 220.dp)
    // Toolbar: single-row pill, narrow 2 slots / wide 3 slots
    val ToolbarCompact = androidx.compose.ui.unit.DpSize(200.dp, 72.dp)
    val ToolbarExpanded = androidx.compose.ui.unit.DpSize(320.dp, 72.dp)
    // Timer: square 2x2, wide 4x2
    val TimerSquare = androidx.compose.ui.unit.DpSize(150.dp, 150.dp)
    val TimerWide = androidx.compose.ui.unit.DpSize(300.dp, 160.dp)
}

// ---------------------------------------------------------------------------
//  Breakpoint helpers — NEVER compare LocalSize with ==.
//  Launchers (OneUI, Nothing, third-party) hand back intermediate DpSizes
//  during drag; equality snaps to the wrong tier and clips. Width/height
//  thresholds degrade gracefully to the smaller tier instead.
// ---------------------------------------------------------------------------

fun musicTier(size: androidx.compose.ui.unit.DpSize): Int = when {
    size.width >= 300.dp && size.height >= 190.dp -> 2
    size.width >= 240.dp && size.height >= 135.dp -> 1
    else -> 0
}

fun pomoTier(size: androidx.compose.ui.unit.DpSize): Int = when {
    // Wide wins when clearly landscape; tall when portrait-tall.
    size.width >= 260.dp && size.height < 200.dp -> 2 // wide
    size.width < 220.dp && size.height >= 190.dp -> 1 // tall
    size.width >= 260.dp && size.height >= 190.dp -> 2 // large landscape -> wide
    else -> 0 // square fallback
}

fun timerIsWide(size: androidx.compose.ui.unit.DpSize): Boolean =
    size.width >= 250.dp && size.width > size.height

fun toolbarIsExpanded(size: androidx.compose.ui.unit.DpSize): Boolean =
    size.width >= 280.dp

/**
 * Unified progress-ring bitmap. Transparent bg —
 * caller draws it over GlanceTheme.surface so dark mode never double-fills.
 * 192px is plenty (launcher downsamples); keeps per-update alloc low.
 */
fun drawWidgetRing(
    progress: Float,
    ringColor: Int,
    trackColor: Int,
    sizePx: Int = 192,
    trackAlpha: Int = 72,
): android.graphics.Bitmap {
    val p = progress.coerceIn(0f, 1f)
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val stroke = sizePx * 0.105f
    val inset = stroke / 2f + sizePx * 0.035f
    val bounds = android.graphics.RectF(inset, inset, sizePx - inset, sizePx - inset)
    canvas.drawArc(
        bounds, -90f, 360f, false,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = trackColor
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = android.graphics.Paint.Cap.ROUND
            alpha = trackAlpha
        }
    )
    if (p > 0.005f) {
        canvas.drawArc(
            bounds, -90f, p * 360f, false,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
        )
    }
    return bitmap
}

/**
 * Single deep-link factory for every Glance widget. Uses
 * MainActivity.EXTRA_NAVIGATE_TO ("navigate_to") so widget taps resolve
 * through the same pass-through as shortcuts and notifications.
 */
fun widgetNavIntent(context: Context, route: String): Intent =
    Intent(context, com.frerox.toolz.MainActivity::class.java).apply {
        putExtra(com.frerox.toolz.MainActivity.EXTRA_NAVIGATE_TO, route)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

fun isColorDark(color: Color): Boolean {
    val darkness = 1 - (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue)
    return darkness >= 0.5f
}

fun Color.toWidgetColorProvider(): ColorProvider {
    // MUST use the official factory (FixedColorProvider). Anonymous
    // ColorProvider impls log "Unexpected background color modifier" /
    // "Unexpected progress indicator color" and are dropped by
    // Glance's RemoteViews translator, which only accepts
    // Fixed / Resource / DayNight providers.
    return androidx.glance.unit.ColorProvider(this)
}

fun parseAccentHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        null
    }
}

fun formatWidgetTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

fun formatWidgetClock(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m.toInt(), s.toInt())
}

/** Downsampled art decode — never OOM on large album files. Returns null on failure. */
fun decodeWidgetArt(path: String?, maxSizePx: Int = 256): android.graphics.Bitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > maxSizePx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.takeIf { it.width > 0 && it.height > 0 }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun rememberWidgetArtProvider(artBitmap: android.graphics.Bitmap?): ImageProvider =
    artBitmap?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_note)

// ---------------------------------------------------------------------------
//  Appearance — read from SettingsRepository (truth), never from Glance state.
//  Glance provideGlance calls this via EntryPoint; falls back safely.
// ---------------------------------------------------------------------------

data class WidgetAppearance(
    val followDynamic: Boolean = true,
    val customAccent: Int? = null,
    val customBackground: Int? = null,
    val opacity: Float = 1f,
    val showArt: Boolean = true,
    val showQueue: Boolean = true,
    val haptics: Boolean = true
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetAppearanceEntryPoint {
    fun settingsRepository(): SettingsRepository
}

suspend fun readWidgetAppearance(context: Context): WidgetAppearance {
    return try {
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetAppearanceEntryPoint::class.java
        ).settingsRepository()
        WidgetAppearance(
            followDynamic = repo.widgetFollowDynamic.firstOrNull() ?: true,
            customAccent = repo.widgetAccentColor.firstOrNull(),
            customBackground = repo.widgetBackgroundColor.firstOrNull(),
            opacity = (repo.widgetOpacity.firstOrNull() ?: 1f).coerceIn(0.4f, 1f),
            showArt = repo.widgetShowArt.firstOrNull() ?: true,
            showQueue = repo.widgetShowQueue.firstOrNull() ?: true,
            haptics = repo.widgetHaptics.firstOrNull() ?: true
        )
    } catch (_: Exception) {
        WidgetAppearance()
    }
}

/**
 * Resolve effective accent: dynamic (track accent from service) wins when
 * followDynamic is true; otherwise custom accent from settings; else null
 * so callers fall back to GlanceTheme.colors.primary.
 */
fun resolveWidgetAccent(trackHex: String?, appearance: WidgetAppearance): Color? {
    if (appearance.followDynamic) {
        parseAccentHex(trackHex)?.let { return it }
    }
    appearance.customAccent?.let {
        return try {
            Color(it)
        } catch (_: Exception) {
            null
        }
    }
    return parseAccentHex(trackHex)
}

/**
 * Custom background with opacity — null when followDynamic (use GlanceTheme
 * surface). Applied by all 4 widgets to their outer container.
 */
fun widgetBackgroundProvider(appearance: WidgetAppearance): ColorProvider? {
    if (appearance.followDynamic) return null
    val base = appearance.customBackground ?: return null
    return try {
        Color(base).copy(alpha = appearance.opacity).toWidgetColorProvider()
    } catch (_: Exception) {
        null
    }
}

/**
 * Non-null outer background — custom when set, otherwise GlanceTheme surface.
 * Every widget must use this (never hardcoded surface) so opacity/accent
 * settings apply uniformly in light + dark.
 */
@Composable
fun widgetOuterBackground(appearance: WidgetAppearance): ColorProvider =
    widgetBackgroundProvider(appearance) ?: GlanceTheme.colors.surface

// ---------------------------------------------------------------------------
//  Shared buttons — 48dp min touch, disabled-safe (no clickable when off).
// ---------------------------------------------------------------------------

@Composable
fun WidgetPlayPauseButton(
    isPlaying: Boolean,
    accentColor: Color?,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    contentDescription: String = "Play",
    onClick: GlanceModifier = GlanceModifier
) {
    // Caller passes clickable modifier; this composable only draws.
    Box(
        modifier = GlanceModifier.size(size).cornerRadius(size * WidgetPlaySquircleFraction)
            .background(accentColor?.toWidgetColorProvider() ?: GlanceTheme.colors.primary)
            .then(onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            ),
            contentDescription = if (isPlaying) "Pause" else contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(
                if (accentColor != null && isColorDark(accentColor)) Color.White.toWidgetColorProvider()
                else GlanceTheme.colors.onPrimary
            )
        )
    }
}

@Composable
fun WidgetControlButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    size: Dp = 48.dp,
    iconSize: Dp = 20.dp,
    active: Boolean = false,
    activeTint: Color? = null,
    clickModifier: GlanceModifier? = null
) {
    val bg = when {
        active -> activeTint?.toWidgetColorProvider() ?: GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val fg = when {
        active -> if (activeTint != null && isColorDark(activeTint)) Color.White.toWidgetColorProvider()
        else GlanceTheme.colors.onPrimary
        enabled -> GlanceTheme.colors.onSurface
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    var mod = GlanceModifier.size(size).cornerRadius(size / 2).background(bg)
    // Disabled-safe: no clickable when disabled or no action supplied.
    if (enabled && clickModifier != null) mod = mod.then(clickModifier)
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(fg)
        )
    }
}

@Composable
fun WidgetTimeLabel(text: String) {
    Text(
        text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        ),
        maxLines = 1
    )
}
