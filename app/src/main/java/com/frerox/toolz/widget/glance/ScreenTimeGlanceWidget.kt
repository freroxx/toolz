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
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.text.TextStyle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.widget.ScreenTimeWidgetDrawer
import com.frerox.toolz.widget.WidgetEntryPoint
import com.frerox.toolz.widget.ui.WidgetOuterCorner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
//  Screen Time — Glance migration. Canonical Text+Image + bars.
//  Compact 3x2 ring + total; Expanded adds top-3. Light/dark aware,
//  permission empty-state with CTA, goal from WIDGETS settings.
// ---------------------------------------------------------------------------

class ScreenTimeGlanceWidget : GlanceAppWidget() {

    companion object {
        private val COMPACT = DpSize(180.dp, 140.dp)
        private val EXPANDED = DpSize(300.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED))
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(COMPACT, EXPANDED))

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                ScreenTimeContent(
                    totalLabel = "2h 14m",
                    percentLabel = "56%",
                    hasPermission = true,
                    topApps = listOf(
                        "Toolz" to "48m",
                        "Browser" to "32m",
                        "Music" to "21m"
                    ),
                    topFractions = listOf(0.36f, 0.24f, 0.16f),
                    ringBitmap = null,
                    openFocusIntent = Intent(),
                    openSettingsIntent = Intent()
                )
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val openFocusIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "focus_flow")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openSettingsIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "settings")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        provideContent {
            GlanceTheme {
                ScreenTimeContent(
                    totalLabel = data.totalLabel,
                    percentLabel = data.percentLabel,
                    hasPermission = data.hasPermission,
                    topApps = data.topApps,
                    topFractions = data.topFractions,
                    ringBitmap = data.ringBitmap,
                    openFocusIntent = openFocusIntent,
                    openSettingsIntent = openSettingsIntent
                )
            }
        }
    }

    private data class ScreenTimeData(
        val totalLabel: String,
        val percentLabel: String,
        val hasPermission: Boolean,
        val topApps: List<Pair<String, String>>,
        val topFractions: List<Float>,
        val ringBitmap: android.graphics.Bitmap?
    )

    private suspend fun loadData(context: Context): ScreenTimeData {
        return try {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            val repo = entry.usageStatsRepository()
            if (!repo.hasUsageStatsPermission()) {
                return ScreenTimeData(
                    totalLabel = "—",
                    percentLabel = "0%",
                    hasPermission = false,
                    topApps = emptyList(),
                    topFractions = emptyList(),
                    ringBitmap = null
                )
            }
            val goalMins = try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.frerox.toolz.widget.ui.WidgetAppearanceEntryPoint::class.java
                ).settingsRepository().widgetScreenGoalMins.firstOrNull() ?: 240
            } catch (_: Exception) {
                240
            }.coerceIn(60, 720)
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val apps = repo.queryDailyByEvents(cal.timeInMillis, now)
            val totalMs = apps.sumOf { it.usageTimeMillis }
            val goalMs = goalMins * 60 * 1000L
            val progress = (totalMs.toFloat() / goalMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            val hours = totalMs / 3_600_000
            val minutes = (totalMs % 3_600_000) / 60_000
            val totalLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val (ring, track, bg) = widgetPalette(context, night)
            val ringBitmap = ScreenTimeWidgetDrawer.drawRing(
                progress = progress,
                ringColor = ring,
                trackColor = track,
                bgColor = bg,
                sizePx = 300
            )
            val top = apps.take(3)
            ScreenTimeData(
                totalLabel = totalLabel,
                percentLabel = "${(progress * 100).roundToInt()}%",
                hasPermission = true,
                topApps = top.map {
                    val h = it.usageTimeMillis / 3_600_000
                    val m = (it.usageTimeMillis % 3_600_000) / 60_000
                    it.appName to if (h > 0) "${h}h ${m}m" else "${m}m"
                },
                topFractions = top.map {
                    if (totalMs > 0) it.usageTimeMillis.toFloat() / totalMs else 0f
                },
                ringBitmap = ringBitmap
            )
        } catch (_: Exception) {
            ScreenTimeData("—", "0%", false, emptyList(), emptyList(), null)
        }
    }

    private fun widgetPalette(context: Context, night: Boolean): Triple<Int, Int, Int> {
        return if (Build.VERSION.SDK_INT >= 31) {
            try {
                val ring = context.resources.getColor(android.R.color.system_accent1_200, context.theme)
                // Track/surface adapt to night mode — fixes white-on-white / black-on-black.
                val track = context.resources.getColor(
                    if (night) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_200,
                    context.theme
                )
                val bg = context.resources.getColor(
                    if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50,
                    context.theme
                )
                Triple(ring, track, bg)
            } catch (_: Exception) {
                Triple(0xFF6750A4.toInt(), 0xFFE7E0EC.toInt(), 0xFFFFFBFE.toInt())
            }
        } else {
            if (night) Triple(0xFFD0BCFF.toInt(), 0xFF49454F.toInt(), 0xFF1C1B1F.toInt())
            else Triple(0xFF6750A4.toInt(), 0xFFE7E0EC.toInt(), 0xFFFFFBFE.toInt())
        }
    }
}

@Composable
private fun ScreenTimeContent(
    totalLabel: String,
    percentLabel: String,
    hasPermission: Boolean,
    topApps: List<Pair<String, String>>,
    topFractions: List<Float>,
    ringBitmap: android.graphics.Bitmap?,
    openFocusIntent: Intent,
    openSettingsIntent: Intent
) {
    val size = LocalSize.current
    val expanded = size.width >= 260.dp && size.height >= 170.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(WidgetOuterCorner)
            .clickable(actionStartActivity(openFocusIntent)),
        contentAlignment = Alignment.Center
    ) {
        if (!hasPermission) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Screen Time",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "Grant usage access to see today's stats",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2
                )
                Spacer(GlanceModifier.height(10.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(20.dp)
                        .background(GlanceTheme.colors.primary)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clickable(actionStartActivity(openSettingsIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Grant access",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            return@Box
        }

        Row(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ring + center text.
            Box(
                modifier = GlanceModifier.size(if (expanded) 110.dp else 90.dp),
                contentAlignment = Alignment.Center
            ) {
                if (ringBitmap != null) {
                    Image(
                        provider = ImageProvider(ringBitmap),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        totalLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        percentLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(GlanceModifier.width(14.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "SCREEN TIME",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.height(8.dp))
                if (topApps.isEmpty()) {
                    Text(
                        "No usage yet today",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else {
                    val rows = if (expanded) topApps.size else minOf(2, topApps.size)
                    repeat(rows) { i ->
                        val (name, time) = topApps[i]
                        val frac = topFractions.getOrNull(i) ?: 0f
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                time,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = frac.coerceIn(0f, 1f),
                            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                            color = GlanceTheme.colors.primary,
                            backgroundColor = GlanceTheme.colors.surfaceVariant
                        )
                        if (i < rows - 1) Spacer(GlanceModifier.height(8.dp))
                    }
                }
            }
        }
    }
}

class ScreenTimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScreenTimeGlanceWidget()
}
