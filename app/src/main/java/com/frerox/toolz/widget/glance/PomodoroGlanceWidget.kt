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
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.widget.ui.WidgetAppearance
import com.frerox.toolz.widget.ui.WidgetControlButton
import com.frerox.toolz.widget.ui.WidgetEyebrow
import com.frerox.toolz.widget.ui.WidgetPlayPauseButton
import com.frerox.toolz.widget.ui.WidgetSizes
import com.frerox.toolz.widget.ui.formatWidgetClock
import com.frerox.toolz.widget.ui.pomoTier
import com.frerox.toolz.widget.ui.readWidgetAppearance
import com.frerox.toolz.widget.ui.widgetBackgroundProvider
import com.frerox.toolz.widget.ui.widgetOuterCornerFor

// ---------------------------------------------------------------------------
//  Pomodoro — minimal. Eyebrow + clock + hairline progress + session
//  caption + tonal controls. No bitmap rings, no manual system-accent
//  lookups: pure GlanceTheme so light/dark + Samsung/OneUI/Nothing all
//  render identically. Responsive (square / wide / tall); outer never
//  clickable, clock opens the app.
// ---------------------------------------------------------------------------

class PomodoroGlanceWidget : GlanceAppWidget() {

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
        val progress = (1f - liveRemaining.toFloat() / totalMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

        val appearance: WidgetAppearance = try { readWidgetAppearance(context) } catch (_: Exception) {
            WidgetAppearance()
        }

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
                            progress = progress,
                            displayMs = liveRemaining, openPomodoroIntent = openPomodoroIntent
                        )
                        1 -> TallPomodoroContent(
                            mode = mode, isRunning = isRunning,
                            sessionsDone = sessionsDone, sessionsGoal = sessionsGoal,
                            progress = progress,
                            displayMs = liveRemaining, openPomodoroIntent = openPomodoroIntent
                        )
                        else -> CompactPomodoroContent(
                            mode = mode, isRunning = isRunning,
                            sessionsDone = sessionsDone, sessionsGoal = sessionsGoal,
                            progress = progress,
                            displayMs = liveRemaining, openPomodoroIntent = openPomodoroIntent
                        )
                    }
                }
            }
        }
    }
}

fun formatWidgetMillis(ms: Long): String = formatWidgetClock(ms)

private fun phaseLabel(mode: String) = when (mode) {
    "SHORT_BREAK" -> "SHORT BREAK"
    "LONG_BREAK" -> "LONG BREAK"
    else -> "FOCUS"
}

@Composable
private fun PhaseEyebrow(mode: String, isRunning: Boolean) {
    val work = mode == "WORK"
    WidgetEyebrow(
        text = "${phaseLabel(mode)} • ${if (isRunning) "LIVE" else "READY"}",
        container = if (work) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.tertiaryContainer,
        content = if (work) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onTertiaryContainer
    )
}

@Composable
private fun SessionCaption(sessionsDone: Int, sessionsGoal: Int) {
    Text(
        text = "SESSION $sessionsDone OF $sessionsGoal",
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

@Composable
private fun TimeProgress(progress: Float) {
    LinearProgressIndicator(
        progress = progress,
        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant
    )
}

@Composable
private fun PomodoroControls(isRunning: Boolean, large: Boolean = false) {
    val actions = rememberPomodoroActions()
    val playSize = if (large) 56.dp else 52.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetControlButton(
            iconRes = R.drawable.ic_widget_reset,
            contentDescription = "Reset Pomodoro",
            enabled = true,
            size = 48.dp,
            iconSize = 20.dp,
            clickModifier = GlanceModifier.clickable(actionSendBroadcast(actions.reset))
        )
        Spacer(GlanceModifier.width(10.dp))
        WidgetPlayPauseButton(
            isPlaying = isRunning,
            accentColor = null,
            size = playSize,
            iconSize = 25.dp,
            contentDescription = "Start Pomodoro",
            onClick = GlanceModifier.clickable(actionSendBroadcast(actions.toggle))
        )
        Spacer(GlanceModifier.width(10.dp))
        WidgetControlButton(
            iconRes = R.drawable.ic_widget_next,
            contentDescription = "Skip Pomodoro phase",
            enabled = true,
            size = 48.dp,
            iconSize = 20.dp,
            clickModifier = GlanceModifier.clickable(actionSendBroadcast(actions.skip))
        )
    }
}

@Composable
private fun CompactPomodoroContent(
    mode: String,
    isRunning: Boolean,
    sessionsDone: Int,
    sessionsGoal: Int,
    progress: Float,
    displayMs: Long,
    openPomodoroIntent: Intent
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhaseEyebrow(mode = mode, isRunning = isRunning)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = formatWidgetClock(displayMs),
            modifier = GlanceModifier.clickable(actionStartActivity(openPomodoroIntent)),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(4.dp))
        SessionCaption(sessionsDone = sessionsDone, sessionsGoal = sessionsGoal)
        Spacer(GlanceModifier.height(6.dp))
        Box(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TimeProgress(progress = progress)
        }
        Spacer(GlanceModifier.height(10.dp))
        PomodoroControls(isRunning = isRunning)
    }
}

@Composable
private fun TallPomodoroContent(
    mode: String,
    isRunning: Boolean,
    sessionsDone: Int,
    sessionsGoal: Int,
    progress: Float,
    displayMs: Long,
    openPomodoroIntent: Intent
) {
    // 2x3 portrait: clock on top, hairline + sessions, controls below.
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhaseEyebrow(mode = mode, isRunning = isRunning)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = formatWidgetClock(displayMs),
            modifier = GlanceModifier.clickable(actionStartActivity(openPomodoroIntent)),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(6.dp))
        TimeProgress(progress = progress)
        Spacer(GlanceModifier.height(6.dp))
        SessionCaption(sessionsDone = sessionsDone, sessionsGoal = sessionsGoal)
        Spacer(GlanceModifier.height(12.dp))
        PomodoroControls(isRunning = isRunning)
    }
}

@Composable
private fun ExpandedPomodoroContent(
    mode: String,
    isRunning: Boolean,
    sessionsDone: Int,
    sessionsGoal: Int,
    progress: Float,
    displayMs: Long,
    openPomodoroIntent: Intent
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            PhaseEyebrow(mode = mode, isRunning = isRunning)
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = formatWidgetClock(displayMs),
                modifier = GlanceModifier.clickable(actionStartActivity(openPomodoroIntent)),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(6.dp))
            TimeProgress(progress = progress)
            Spacer(GlanceModifier.height(6.dp))
            SessionCaption(sessionsDone = sessionsDone, sessionsGoal = sessionsGoal)
        }
        Spacer(GlanceModifier.width(14.dp))
        PomodoroControls(isRunning = isRunning, large = true)
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
