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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.widget.ui.WidgetAppearance
import com.frerox.toolz.widget.ui.WidgetSizes
import com.frerox.toolz.widget.ui.drawWidgetRing
import com.frerox.toolz.widget.ui.formatWidgetClock
import com.frerox.toolz.widget.ui.pomoTier
import com.frerox.toolz.widget.ui.readWidgetAppearance
import com.frerox.toolz.widget.ui.widgetBackgroundProvider
import com.frerox.toolz.widget.ui.widgetOuterCornerFor

// ---------------------------------------------------------------------------
//  Pomodoro — M3 Expressive, Responsive (square / wide / tall).
//  Square 150x150: ring + time + play
//  Wide   300x160: ring + goal + 3 controls
//  Tall   180x220: stacked ring + controls (2x3 cells)
//  Outer never clickable; time + ring open app as siblings.
// ---------------------------------------------------------------------------

class PomodoroGlanceWidget : GlanceAppWidget() {

    // Responsive: launcher picks best fit. Exact stretched the single layout
    // and snapped thresholds — the resize bug.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.PomoSquare, WidgetSizes.PomoWide, WidgetSizes.PomoTall)
    )
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.PomoSquare, WidgetSizes.PomoWide)
    )
    override val stateDefinition = PomodoroWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, PomodoroWidgetStateDefinition, id)
        val mode = prefs[PomodoroWidgetState.KEY_MODE] ?: "WORK"
        val storedRemaining: Long = try {
            prefs[PomodoroWidgetState.KEY_REMAINING_MS] ?: legacyRemaining(prefs)
        } catch (_: ClassCastException) {
            legacyRemaining(prefs)
        }
        val totalMs: Long = try {
            (prefs[PomodoroWidgetState.KEY_TOTAL_MS] ?: legacyTotal(prefs)).takeIf { it > 0L }
                ?: 25 * 60 * 1000L
        } catch (_: ClassCastException) {
            legacyTotal(prefs).takeIf { it > 0L } ?: 25 * 60 * 1000L
        }
        val isRunning = try { prefs[PomodoroWidgetState.KEY_IS_RUNNING] ?: false } catch (_: Exception) { false }
        val sessionsDone = try { (prefs[PomodoroWidgetState.KEY_SESSIONS_DONE] ?: 0).coerceAtLeast(0) } catch (_: Exception) { 0 }
        val sessionsGoal = try { (prefs[PomodoroWidgetState.KEY_SESSIONS_GOAL] ?: 8).coerceIn(1, 12) } catch (_: Exception) { 8 }
        val capturedAt = try { prefs[PomodoroWidgetState.KEY_CAPTURED_AT_ELAPSED_MS] ?: SystemClock.elapsedRealtime() } catch (_: Exception) { SystemClock.elapsedRealtime() }

        val nowElapsed = SystemClock.elapsedRealtime()
        val liveRemaining = if (isRunning) {
            (storedRemaining - (nowElapsed - capturedAt).coerceAtLeast(0L)).coerceAtLeast(0L)
        } else {
            storedRemaining.coerceAtLeast(0L)
        }
        val elapsedProgress = (1f - liveRemaining.toFloat() / totalMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        val goalProgress = (sessionsDone.toFloat() / sessionsGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

        val appearance: WidgetAppearance = try { readWidgetAppearance(context) } catch (_: Exception) {
            WidgetAppearance()
        }
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val palette = PomodoroWidgetPalette.resolve(context, mode, night, appearance.customAccent)
        val ringBitmap = drawWidgetRing(
            progress = elapsedProgress,
            ringColor = palette.accent,
            trackColor = palette.track,
            sizePx = 192
        )
        val goalBitmap = drawWidgetRing(
            progress = goalProgress,
            ringColor = palette.secondary,
            trackColor = palette.track,
            sizePx = 96
        )

        val openPomodoroIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "pomodoro")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val tier = pomoTier(size)
                val outerBg = widgetBackgroundProvider(appearance) ?: GlanceTheme.colors.surface
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(outerBg)
                        .cornerRadius(widgetOuterCornerFor(size.width, size.height)),
                    contentAlignment = Alignment.Center
                ) {
                    when (tier) {
                        2 -> ExpandedPomodoroContent(
                            mode = mode, isRunning = isRunning,
                            sessionsDone = sessionsDone, sessionsGoal = sessionsGoal,
                            ringBitmap = ringBitmap, goalBitmap = goalBitmap,
                            displayMs = liveRemaining, openPomodoroIntent = openPomodoroIntent
                        )
                        1 -> TallPomodoroContent(
                            mode = mode, isRunning = isRunning,
                            sessionsDone = sessionsDone, sessionsGoal = sessionsGoal,
                            ringBitmap = ringBitmap,
                            displayMs = liveRemaining, openPomodoroIntent = openPomodoroIntent
                        )
                        else -> CompactPomodoroContent(
                            mode = mode, isRunning = isRunning,
                            ringBitmap = ringBitmap,
                            displayMs = liveRemaining, openPomodoroIntent = openPomodoroIntent
                        )
                    }
                }
            }
        }
    }
}

fun formatWidgetMillis(ms: Long): String = formatWidgetClock(ms)

@Composable
private fun CompactPomodoroContent(
    mode: String,
    isRunning: Boolean,
    ringBitmap: android.graphics.Bitmap,
    displayMs: Long,
    openPomodoroIntent: Intent
) {
    val actions = rememberPomodoroActions()
    Box(modifier = GlanceModifier.fillMaxSize().padding(9.dp), contentAlignment = Alignment.Center) {
        Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhasePill(mode = mode, isRunning = isRunning)
            Spacer(GlanceModifier.height(7.dp))
            Text(
                text = formatWidgetClock(displayMs),
                modifier = GlanceModifier.clickable(actionStartActivity(openPomodoroIntent)),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(9.dp))
            WidgetIconButton(
                icon = if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                contentDescription = if (isRunning) "Pause Pomodoro" else "Start Pomodoro",
                action = actions.toggle,
                prominent = true,
                mode = mode
            )
        }
    }
}

@Composable
private fun TallPomodoroContent(
    mode: String,
    isRunning: Boolean,
    sessionsDone: Int,
    sessionsGoal: Int,
    ringBitmap: android.graphics.Bitmap,
    displayMs: Long,
    openPomodoroIntent: Intent
) {
    // 2x3 portrait: stacked ring on top, controls below. No clipping.
    val actions = rememberPomodoroActions()
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(120.dp)
                .clickable(actionStartActivity(openPomodoroIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                PhasePill(mode = mode, isRunning = isRunning)
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = formatWidgetClock(displayMs),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = "$sessionsDone of $sessionsGoal sessions",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetIconButton(
                icon = R.drawable.ic_widget_reset,
                contentDescription = "Reset Pomodoro",
                action = actions.reset,
                prominent = false,
                mode = mode
            )
            Spacer(GlanceModifier.width(10.dp))
            WidgetIconButton(
                icon = if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                contentDescription = if (isRunning) "Pause Pomodoro" else "Start Pomodoro",
                action = actions.toggle,
                prominent = true,
                mode = mode
            )
            Spacer(GlanceModifier.width(10.dp))
            WidgetIconButton(
                icon = R.drawable.ic_widget_next,
                contentDescription = "Skip Pomodoro phase",
                action = actions.skip,
                prominent = false,
                mode = mode
            )
        }
    }
}

@Composable
private fun ExpandedPomodoroContent(
    mode: String,
    isRunning: Boolean,
    sessionsDone: Int,
    sessionsGoal: Int,
    ringBitmap: android.graphics.Bitmap,
    goalBitmap: android.graphics.Bitmap,
    displayMs: Long,
    openPomodoroIntent: Intent
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(130.dp)
                .clickable(actionStartActivity(openPomodoroIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                PhasePill(mode = mode, isRunning = isRunning)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = formatWidgetClock(displayMs),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(GlanceModifier.width(14.dp))

        Column(
            modifier = GlanceModifier.fillMaxHeight().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Image(provider = ImageProvider(goalBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                    Text(
                        text = sessionsDone.coerceAtMost(99).toString(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "Daily focus",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "$sessionsDone of $sessionsGoal sessions",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(GlanceModifier.height(16.dp))

            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val actions = rememberPomodoroActions()
                WidgetIconButton(
                    icon = R.drawable.ic_widget_reset,
                    contentDescription = "Reset Pomodoro",
                    action = actions.reset,
                    prominent = false,
                    mode = mode
                )
                Spacer(GlanceModifier.width(10.dp))
                WidgetIconButton(
                    icon = if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    contentDescription = if (isRunning) "Pause Pomodoro" else "Start Pomodoro",
                    action = actions.toggle,
                    prominent = true,
                    mode = mode
                )
                Spacer(GlanceModifier.width(10.dp))
                WidgetIconButton(
                    icon = R.drawable.ic_widget_next,
                    contentDescription = "Skip Pomodoro phase",
                    action = actions.skip,
                    prominent = false,
                    mode = mode
                )
            }
        }
    }
}

@Composable
private fun PhasePill(mode: String, isRunning: Boolean) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(14.dp)
            .background(if (mode == "WORK") GlanceTheme.colors.primaryContainer else GlanceTheme.colors.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${modeLabel(mode)} • ${if (isRunning) "LIVE" else "READY"}",
            style = TextStyle(
                color = if (mode == "WORK") GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onTertiaryContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun WidgetIconButton(
    icon: Int,
    contentDescription: String,
    action: Intent,
    prominent: Boolean,
    mode: String
) {
    val buttonSize = if (prominent) 52.dp else 48.dp
    val background = when {
        prominent && mode == "WORK" -> GlanceTheme.colors.primary
        prominent -> GlanceTheme.colors.tertiary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val foreground = when {
        prominent && mode == "WORK" -> GlanceTheme.colors.onPrimary
        prominent -> GlanceTheme.colors.onTertiary
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    Box(
        modifier = GlanceModifier
            .size(buttonSize)
            .cornerRadius(buttonSize / 2)
            .background(background)
            .clickable(actionSendBroadcast(action)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(if (prominent) 25.dp else 20.dp),
            colorFilter = ColorFilter.tint(foreground)
        )
    }
}

@Composable
private fun rememberPomodoroActions(): PomodoroWidgetActions {
    val context = LocalContext.current
    val receiver = ComponentName(context, PomodoroWidgetReceiver::class.java)
    return PomodoroWidgetActions(
        toggle = Intent(POMODORO_ACTION_TOGGLE).apply { component = receiver },
        reset = Intent(POMODORO_ACTION_RESET).apply { component = receiver },
        skip = Intent(POMODORO_ACTION_SKIP).apply { component = receiver }
    )
}

private data class PomodoroWidgetActions(
    val toggle: Intent,
    val reset: Intent,
    val skip: Intent
)

private data class PomodoroWidgetPalette(
    val accent: Int,
    val secondary: Int,
    val track: Int
) {
    companion object {
        fun resolve(context: Context, mode: String, night: Boolean, customAccent: Int?): PomodoroWidgetPalette {
            // Custom accent wins when user disabled dynamic color — consistent with Music.
            if (customAccent != null) {
                val track = if (night) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()
                return PomodoroWidgetPalette(customAccent, customAccent, track)
            }
            val accent = if (Build.VERSION.SDK_INT >= 31) {
                try {
                    context.resources.getColor(
                        if (mode == "WORK") android.R.color.system_accent1_200 else android.R.color.system_accent3_200,
                        context.theme
                    )
                } catch (_: Exception) {
                    if (mode == "WORK") 0xFF6750A4.toInt() else 0xFF7D5260.toInt()
                }
            } else if (mode == "WORK") {
                0xFF6750A4.toInt()
            } else {
                0xFF7D5260.toInt()
            }
            val secondary = if (Build.VERSION.SDK_INT >= 31) {
                try {
                    context.resources.getColor(android.R.color.system_accent2_200, context.theme)
                } catch (_: Exception) {
                    0xFF625B71.toInt()
                }
            } else {
                0xFF625B71.toInt()
            }
            // Night-aware track — fixes invisible ring in dark mode.
            val track = if (night) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()
            return PomodoroWidgetPalette(accent, secondary, track)
        }
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "SHORT_BREAK" -> "SHORT"
    "LONG_BREAK" -> "LONG"
    else -> "FOCUS"
}

private fun legacyRemaining(prefs: Preferences): Long {
    return try {
        prefs[PomodoroWidgetState.KEY_REMAINING_MS_LEGACY]?.toLong() ?: 25 * 60 * 1000L
    } catch (_: Exception) {
        25 * 60 * 1000L
    }
}

private fun legacyTotal(prefs: Preferences): Long {
    return try {
        prefs[PomodoroWidgetState.KEY_TOTAL_MS_LEGACY]?.toLong() ?: 25 * 60 * 1000L
    } catch (_: Exception) {
        25 * 60 * 1000L
    }
}

const val POMODORO_ACTION_TOGGLE = "com.frerox.toolz.WIDGET_POMO_TOGGLE"
const val POMODORO_ACTION_RESET = "com.frerox.toolz.WIDGET_POMO_RESET"
const val POMODORO_ACTION_SKIP = "com.frerox.toolz.WIDGET_POMO_SKIP"
