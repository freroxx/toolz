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
val WidgetPlaySquircleFraction: Float = 0.35f
const val WIDGET_MIN_TOUCH_DP: Int = 48

fun isColorDark(color: Color): Boolean {
    val darkness = 1 - (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue)
    return darkness >= 0.5f
}

fun Color.toWidgetColorProvider(): ColorProvider {
    val self = this
    return object : ColorProvider {
        override fun getColor(context: Context): Color = self
    }
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
