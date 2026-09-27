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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.frerox.toolz.R
import com.frerox.toolz.service.ToolService
import com.frerox.toolz.widget.ui.WidgetAppearance
import com.frerox.toolz.widget.ui.WidgetControlButton
import com.frerox.toolz.widget.ui.WidgetEyebrow
import com.frerox.toolz.widget.ui.WidgetPlayPauseButton
import com.frerox.toolz.widget.ui.WidgetSizes
import com.frerox.toolz.widget.ui.formatStopwatch
import com.frerox.toolz.widget.ui.readWidgetAppearance
import com.frerox.toolz.widget.ui.stopwatchIsWide
import com.frerox.toolz.widget.ui.widgetBackgroundProvider
import com.frerox.toolz.widget.ui.widgetNavIntent
import com.frerox.toolz.widget.ui.widgetOuterCornerFor

// ---------------------------------------------------------------------------
//  Stopwatch — minimal, count-up. Replaces the old Timer widget in place
//  (same provider component, so pinned timers auto-migrate on update).
//  Square 150x150: eyebrow + clock + laps + play.
//  Wide   300x160: clock block + reset / play / lap.
//  Deliberately NO progress ring — the ring belonged to the countdown
//  language; a count-up clock + hairline caption is the whole design.
//  Push-driven live: ToolService pushes on start/pause/reset/lap + 1s tick
//  while running; widget derives now from the elapsedRealtime base anchor.
// ---------------------------------------------------------------------------

class StopwatchGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.StopwatchSquare, WidgetSizes.StopwatchWide)
    )
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.StopwatchSquare, WidgetSizes.StopwatchWide)
    )
    override val stateDefinition = StopwatchWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, StopwatchWidgetStateDefinition, id)
        val base = try { prefs[StopwatchWidgetState.KEY_BASE_ELAPSED_MS] ?: 0L } catch (_: Exception) { 0L }
        val accumulated = try { prefs[StopwatchWidgetState.KEY_ACCUMULATED_MS] ?: 0L } catch (_: Exception) { 0L }
        val isRunning = try { prefs[StopwatchWidgetState.KEY_IS_RUNNING] ?: false } catch (_: Exception) { false }
        val lapCount = try { prefs[StopwatchWidgetState.KEY_LAP_COUNT] ?: 0 } catch (_: Exception) { 0 }
        val lastLapMs = try { prefs[StopwatchWidgetState.KEY_LAST_LAP_MS] ?: 0L } catch (_: Exception) { 0L }

        val nowElapsed = SystemClock.elapsedRealtime()
        val liveElapsed = if (isRunning && base > 0L) {
            (nowElapsed - base).coerceAtLeast(0L)
        } else {
            accumulated.coerceAtLeast(0L)
        }

        val appearance: WidgetAppearance = try { readWidgetAppearance(context) } catch (_: Exception) {
            WidgetAppearance()
        }
        val openStopwatchIntent = widgetNavIntent(context, "stopwatch")

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val wide = stopwatchIsWide(size)
                val outerBg = widgetBackgroundProvider(appearance) ?: GlanceTheme.colors.surface
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(outerBg)
                        .cornerRadius(widgetOuterCornerFor(size.width, size.height)),
                    contentAlignment = Alignment.Center
                ) {
                    if (wide) {
                        WideStopwatchContent(
                            elapsedMs = liveElapsed,
                            isRunning = isRunning,
                            lapCount = lapCount,
                            lastLapMs = lastLapMs,
                            openStopwatchIntent = openStopwatchIntent
                        )
                    } else {
                        SquareStopwatchContent(
                            elapsedMs = liveElapsed,
                            isRunning = isRunning,
                            lapCount = lapCount,
                            openStopwatchIntent = openStopwatchIntent
                        )
                    }
                }
            }
        }
    }
}

private fun stopwatchStatus(isRunning: Boolean, elapsedMs: Long): String = when {
    isRunning -> "LIVE"
    elapsedMs > 0L -> "PAUSED"
    else -> "STOPWATCH"
}

private fun lapCaption(lapCount: Int, lastLapMs: Long): String = when {
    lapCount > 0 && lastLapMs > 0L -> "$lapCount ${if (lapCount == 1) "LAP" else "LAPS"} • LAST ${formatStopwatch(lastLapMs)}"
    lapCount > 0 -> "$lapCount ${if (lapCount == 1) "LAP" else "LAPS"}"
    else -> "NO LAPS YET"
}

@Composable
private fun SquareStopwatchContent(
    elapsedMs: Long,
    isRunning: Boolean,
    lapCount: Int,
    openStopwatchIntent: Intent
) {
    val actions = rememberStopwatchActions()
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetEyebrow(stopwatchStatus(isRunning, elapsedMs))
        Spacer(GlanceModifier.height(7.dp))
        Text(
            text = formatStopwatch(elapsedMs),
            modifier = GlanceModifier.clickable(actionStartActivity(openStopwatchIntent)),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 31.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = if (lapCount > 0) "$lapCount ${if (lapCount == 1) "LAP" else "LAPS"}" else "TAP PLAY TO START",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(9.dp))
        WidgetPlayPauseButton(
            isPlaying = isRunning,
            accentColor = null,
            size = 52.dp,
            iconSize = 25.dp,
            contentDescription = "Start stopwatch",
            onClick = GlanceModifier.clickable(actionSendBroadcast(actions.toggle))
        )
    }
}

@Composable
private fun WideStopwatchContent(
    elapsedMs: Long,
    isRunning: Boolean,
    lapCount: Int,
    lastLapMs: Long,
    openStopwatchIntent: Intent
) {
    val actions = rememberStopwatchActions()
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetEyebrow(stopwatchStatus(isRunning, elapsedMs))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                formatStopwatch(elapsedMs),
                modifier = GlanceModifier.clickable(actionStartActivity(openStopwatchIntent)),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 30.sp, fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                lapCaption(lapCount, lastLapMs),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp, fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetControlButton(
                iconRes = R.drawable.ic_widget_reset,
                contentDescription = "Reset stopwatch",
                enabled = elapsedMs > 0L || lapCount > 0,
                size = 44.dp,
                iconSize = 20.dp,
                clickModifier = GlanceModifier.clickable(actionSendBroadcast(actions.reset))
            )
            Spacer(GlanceModifier.height(8.dp))
            WidgetPlayPauseButton(
                isPlaying = isRunning,
                accentColor = null,
                size = 56.dp,
                iconSize = 26.dp,
                contentDescription = "Start stopwatch",
                onClick = GlanceModifier.clickable(actionSendBroadcast(actions.toggle))
            )
            Spacer(GlanceModifier.height(8.dp))
            WidgetControlButton(
                iconRes = R.drawable.ic_widget_next,
                contentDescription = "Record lap",
                enabled = isRunning,
                size = 44.dp,
                iconSize = 20.dp,
                active = false,
                clickModifier = GlanceModifier.clickable(actionSendBroadcast(actions.lap))
            )
        }
    }
}

private data class StopwatchWidgetActions(val toggle: Intent, val reset: Intent, val lap: Intent)

@Composable
private fun rememberStopwatchActions(): StopwatchWidgetActions {
    val context = LocalContext.current
    // Same component as the old Timer widget — pinned timers migrate in place.
    val receiver = ComponentName(context, TimerWidgetReceiver::class.java)
    return StopwatchWidgetActions(
        toggle = Intent(STOPWATCH_ACTION_TOGGLE).apply { component = receiver },
        reset = Intent(STOPWATCH_ACTION_RESET).apply { component = receiver },
        lap = Intent(STOPWATCH_ACTION_LAP).apply { component = receiver }
    )
}

// ---------------------------------------------------------------------------
//  Receiver — class name kept as TimerWidgetReceiver so existing pinned
//  Timer widgets upgrade to Stopwatch in place (same component, no orphan).
//  Legacy TIMER_ACTION_* broadcasts still map to toggle/reset.
// ---------------------------------------------------------------------------

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StopwatchGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = when (intent.action) {
            STOPWATCH_ACTION_TOGGLE, TIMER_ACTION_TOGGLE -> ToolService.ACTION_STOPWATCH_TOGGLE
            STOPWATCH_ACTION_RESET, TIMER_ACTION_RESET -> ToolService.ACTION_STOPWATCH_STOP
            STOPWATCH_ACTION_LAP -> ToolService.ACTION_STOPWATCH_LAP
            else -> return
        }
        val serviceIntent = Intent(context, ToolService::class.java).apply { this.action = action }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
            try { context.startService(serviceIntent) } catch (_: Exception) {}
        }
    }
}

const val STOPWATCH_ACTION_TOGGLE = "com.frerox.toolz.WIDGET_STOPWATCH_TOGGLE"
const val STOPWATCH_ACTION_RESET = "com.frerox.toolz.WIDGET_STOPWATCH_RESET"
const val STOPWATCH_ACTION_LAP = "com.frerox.toolz.WIDGET_STOPWATCH_LAP"

// Legacy Timer broadcast actions — still honored, mapped to Stopwatch.
const val TIMER_ACTION_TOGGLE = "com.frerox.toolz.WIDGET_TIMER_TOGGLE"
const val TIMER_ACTION_RESET = "com.frerox.toolz.WIDGET_TIMER_RESET"
