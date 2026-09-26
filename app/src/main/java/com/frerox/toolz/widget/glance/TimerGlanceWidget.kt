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
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.service.ToolService
import com.frerox.toolz.widget.ui.WidgetAppearance
import com.frerox.toolz.widget.ui.WidgetSizes
import com.frerox.toolz.widget.ui.drawWidgetRing
import com.frerox.toolz.widget.ui.formatWidgetClock
import com.frerox.toolz.widget.ui.readWidgetAppearance
import com.frerox.toolz.widget.ui.timerIsWide
import com.frerox.toolz.widget.ui.widgetBackgroundProvider
import com.frerox.toolz.widget.ui.widgetOuterCornerFor

// ---------------------------------------------------------------------------
//  Timer — M3 Expressive, Responsive (square / wide).
//  Square 150x150: ring + time + play. Wide 300x160: ring + time + controls.
//  Push-driven live: ToolService pushes on start/pause/reset + 5s tick while
//  running; widget interpolates with elapsedRealtime between pushes.
// ---------------------------------------------------------------------------

class TimerGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.TimerSquare, WidgetSizes.TimerWide)
    )
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.TimerSquare, WidgetSizes.TimerWide)
    )
    override val stateDefinition = TimerWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, TimerWidgetStateDefinition, id)
        val storedRemaining = try { prefs[TimerWidgetState.KEY_REMAINING_MS] ?: 0L } catch (_: Exception) { 0L }
        // Display truth: 0 stays 00:00 (never fake 00:01). Progress denominator
        // alone gets the 1L floor so an empty timer doesn't divide by zero.
        val rawTotal = try { prefs[TimerWidgetState.KEY_TOTAL_MS] ?: 0L } catch (_: Exception) { 0L }
        val totalMs = rawTotal.takeIf { it > 0L } ?: storedRemaining.takeIf { it > 0L } ?: 0L
        val progressDenom = totalMs.takeIf { it > 0L } ?: 1L
        val isRunning = try { prefs[TimerWidgetState.KEY_IS_RUNNING] ?: false } catch (_: Exception) { false }
        val isRinging = try { prefs[TimerWidgetState.KEY_IS_RINGING] ?: false } catch (_: Exception) { false }
        val capturedAt = try {
            prefs[TimerWidgetState.KEY_CAPTURED_AT_ELAPSED_MS] ?: SystemClock.elapsedRealtime()
        } catch (_: Exception) { SystemClock.elapsedRealtime() }

        val nowElapsed = SystemClock.elapsedRealtime()
        val liveRemaining = if (isRunning) {
            (storedRemaining - (nowElapsed - capturedAt).coerceAtLeast(0L)).coerceAtLeast(0L)
        } else {
            storedRemaining.coerceAtLeast(0L)
        }
        val progress = (1f - liveRemaining.toFloat() / progressDenom.toFloat()).coerceIn(0f, 1f)

        val appearance: WidgetAppearance = try { readWidgetAppearance(context) } catch (_: Exception) {
            WidgetAppearance()
        }
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val ringColor = appearance.customAccent ?: if (Build.VERSION.SDK_INT >= 31) {
            try {
                context.resources.getColor(android.R.color.system_accent1_200, context.theme)
            } catch (_: Exception) { 0xFF6750A4.toInt() }
        } else 0xFF6750A4.toInt()
        val trackColor = if (night) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()
        val openTimerIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "timer")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val ringBitmap = drawWidgetRing(
            progress = progress,
            ringColor = ringColor,
            trackColor = trackColor,
            sizePx = 192
        )

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val wide = timerIsWide(size)
                val outerBg = widgetBackgroundProvider(appearance) ?: GlanceTheme.colors.surface
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(outerBg)
                        .cornerRadius(widgetOuterCornerFor(size.width, size.height)),
                    contentAlignment = Alignment.Center
                ) {
                    if (wide) {
                        WideTimerContent(
                            displayMs = liveRemaining,
                            totalMs = totalMs,
                            isRunning = isRunning,
                            isRinging = isRinging,
                            ringBitmap = ringBitmap,
                            openTimerIntent = openTimerIntent
                        )
                    } else {
                        SquareTimerContent(
                            displayMs = liveRemaining,
                            isRunning = isRunning,
                            isRinging = isRinging,
                            ringBitmap = ringBitmap,
                            openTimerIntent = openTimerIntent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SquareTimerContent(
    displayMs: Long,
    isRunning: Boolean,
    isRinging: Boolean,
    ringBitmap: android.graphics.Bitmap,
    openTimerIntent: Intent
) {
    val actions = rememberTimerActions()
    val label = when {
        isRinging -> "RINGING"
        isRunning -> "LIVE"
        displayMs > 0L -> "READY"
        else -> "TIMER"
    }
    Box(modifier = GlanceModifier.fillMaxSize().padding(9.dp), contentAlignment = Alignment.Center) {
        Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier.cornerRadius(14.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(7.dp))
            Text(
                text = formatWidgetClock(displayMs),
                modifier = GlanceModifier.clickable(actionStartActivity(openTimerIntent)),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 31.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(9.dp))
            Box(
                modifier = GlanceModifier.size(52.dp).cornerRadius(26.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionSendBroadcast(actions.toggle)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                    ),
                    contentDescription = if (isRunning) "Pause timer" else "Start timer",
                    modifier = GlanceModifier.size(25.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
                )
            }
        }
    }
}

@Composable
private fun WideTimerContent(
    displayMs: Long,
    totalMs: Long,
    isRunning: Boolean,
    isRinging: Boolean,
    ringBitmap: android.graphics.Bitmap,
    openTimerIntent: Intent
) {
    val actions = rememberTimerActions()
    // Single time truth: large text only. Ring center shows progress %.
    val pct = if (totalMs > 0L) {
        ((1f - displayMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) * 100).toInt()
    } else 0
    val statusLabel = when {
        isRinging -> "TIME'S UP"
        isRunning -> "LIVE"
        displayMs > 0L -> "READY"
        else -> "TIMER"
    }
    val statusBg = if (isRinging) GlanceTheme.colors.errorContainer
        else GlanceTheme.colors.primaryContainer
    val statusFg = if (isRinging) GlanceTheme.colors.onErrorContainer
        else GlanceTheme.colors.onPrimaryContainer
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(96.dp)
                .clickable(actionStartActivity(openTimerIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(provider = ImageProvider(ringBitmap), contentDescription = "Timer progress", modifier = GlanceModifier.fillMaxSize())
            Text(
                text = "$pct%",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Box(
                modifier = GlanceModifier.cornerRadius(12.dp).background(statusBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    statusLabel,
                    style = TextStyle(
                        color = statusFg,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                formatWidgetClock(displayMs),
                modifier = GlanceModifier.clickable(actionStartActivity(openTimerIntent)),
                style = TextStyle(
                    color = if (isRinging) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier.size(48.dp).cornerRadius(24.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionSendBroadcast(actions.reset)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_reset),
                        contentDescription = "Reset timer",
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(GlanceModifier.width(10.dp))
                Box(
                    modifier = GlanceModifier.size(52.dp).cornerRadius(26.dp)
                        .background(GlanceTheme.colors.primary)
                        .clickable(actionSendBroadcast(actions.toggle)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(
                            if (isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                        ),
                        contentDescription = if (isRunning) "Pause timer" else "Start timer",
                        modifier = GlanceModifier.size(25.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
                    )
                }
            }
        }
    }
}

private data class TimerWidgetActions(val toggle: Intent, val reset: Intent)

@Composable
private fun rememberTimerActions(): TimerWidgetActions {
    val context = LocalContext.current
    val receiver = ComponentName(context, TimerWidgetReceiver::class.java)
    return TimerWidgetActions(
        toggle = Intent(TIMER_ACTION_TOGGLE).apply { component = receiver },
        reset = Intent(TIMER_ACTION_RESET).apply { component = receiver }
    )
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = when (intent.action) {
            TIMER_ACTION_TOGGLE -> ToolService.ACTION_TIMER_TOGGLE
            TIMER_ACTION_RESET -> ToolService.ACTION_TIMER_STOP
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

const val TIMER_ACTION_TOGGLE = "com.frerox.toolz.WIDGET_TIMER_TOGGLE"
const val TIMER_ACTION_RESET = "com.frerox.toolz.WIDGET_TIMER_RESET"
