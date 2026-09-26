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
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.widget.ui.WidgetOuterCorner
import kotlinx.coroutines.flow.firstOrNull

// ---------------------------------------------------------------------------
//  Quick Actions toolbar — canonical Search toolbar + standard toolbar.
//  Single 4x1 pill: search field + up to 3 configurable slots
//  (mic, flashlight, qr, pomodoro, timer). No nested clickable bug:
//  search area and each slot are siblings, never parent/child.
// ---------------------------------------------------------------------------

class QuickActionsGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single
    override val previewSizeMode: PreviewSizeMode = SizeMode.Single

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                ToolbarContent(
                    slots = listOf("mic", "flashlight", "qr"),
                    openSearchIntent = dummyIntent(context, "search"),
                    micIntent = dummyIntent(context, "search"),
                    flashlightIntent = Intent(),
                    qrIntent = dummyIntent(context, "qr_generator"),
                    pomodoroIntent = dummyIntent(context, "pomodoro"),
                    timerIntent = dummyIntent(context, "timer")
                )
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val openSearchIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "search")
            putExtra("auto_focus_search", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val voiceIntent = try {
            Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
                )
            }.takeIf {
                it.resolveActivity(context.packageManager) != null
            } ?: openSearchIntent
        } catch (_: Exception) {
            openSearchIntent
        }
        val flashlightIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "flashlight")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val qrIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "qr_generator")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pomodoroIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "pomodoro")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val timerIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "timer")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val slots = try {
            readToolbarSlots(context)
        } catch (_: Exception) {
            listOf("mic", "flashlight", "qr")
        }

        provideContent {
            GlanceTheme {
                ToolbarContent(
                    slots = slots,
                    openSearchIntent = openSearchIntent,
                    micIntent = voiceIntent,
                    flashlightIntent = flashlightIntent,
                    qrIntent = qrIntent,
                    pomodoroIntent = pomodoroIntent,
                    timerIntent = timerIntent
                )
            }
        }
    }

    private fun dummyIntent(context: Context, route: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", route)
        }
    }
}

private suspend fun readToolbarSlots(context: Context): List<String> {
    return try {
        val repo = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.frerox.toolz.widget.ui.WidgetAppearanceEntryPoint::class.java
        ).settingsRepository()
        val slots = repo.widgetToolbarSlots.firstOrNull() ?: setOf("mic", "flashlight", "qr")
        // Stable order: mic, flashlight, qr, pomodoro, timer. Max 3 shown.
        val order = listOf("mic", "flashlight", "qr", "pomodoro", "timer")
        order.filter { slots.contains(it) }.take(3).ifEmpty { listOf("mic", "flashlight", "qr") }
    } catch (_: Exception) {
        listOf("mic", "flashlight", "qr")
    }
}

@Composable
private fun ToolbarContent(
    slots: List<String>,
    openSearchIntent: Intent,
    micIntent: Intent,
    flashlightIntent: Intent,
    qrIntent: Intent,
    pomodoroIntent: Intent,
    timerIntent: Intent
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(WidgetOuterCorner),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search badge — opens search.
            Box(
                modifier = GlanceModifier.size(40.dp).cornerRadius(20.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity(openSearchIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_search),
                    contentDescription = "Search",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary)
                )
            }

            Spacer(GlanceModifier.width(12.dp))

            // Hint — opens search (sibling, not parent, of slots).
            Text(
                text = "Search with Toolz…",
                modifier = GlanceModifier.defaultWeight()
                    .clickable(actionStartActivity(openSearchIntent)),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )

            Spacer(GlanceModifier.width(8.dp))

            // Slots — siblings, max 3, 48dp touch.
            slots.take(3).forEach { slot ->
                Spacer(GlanceModifier.width(8.dp))
                when (slot) {
                    "mic" -> SlotButton(
                        icon = R.drawable.ic_widget_mic,
                        desc = "Voice Search",
                        container = GlanceTheme.colors.secondaryContainer,
                        onContainer = GlanceTheme.colors.onSecondaryContainer,
                        intent = micIntent,
                        isActivity = true
                    )
                    "flashlight" -> SlotButton(
                        icon = R.drawable.ic_flashlight,
                        desc = "Flashlight",
                        container = GlanceTheme.colors.tertiaryContainer,
                        onContainer = GlanceTheme.colors.onTertiaryContainer,
                        intent = flashlightIntent,
                        isActivity = true
                    )
                    "qr" -> SlotButton(
                        icon = R.drawable.ic_shortcut_qr_generator,
                        desc = "QR scanner",
                        container = GlanceTheme.colors.surfaceVariant,
                        onContainer = GlanceTheme.colors.onSurfaceVariant,
                        intent = qrIntent,
                        isActivity = true
                    )
                    "pomodoro" -> SlotButton(
                        icon = R.drawable.ic_shortcut_pomodoro,
                        desc = "Focus timer",
                        container = GlanceTheme.colors.surfaceVariant,
                        onContainer = GlanceTheme.colors.onSurfaceVariant,
                        intent = pomodoroIntent,
                        isActivity = true
                    )
                    "timer" -> SlotButton(
                        icon = R.drawable.ic_shortcut_timer,
                        desc = "Timer",
                        container = GlanceTheme.colors.surfaceVariant,
                        onContainer = GlanceTheme.colors.onSurfaceVariant,
                        intent = timerIntent,
                        isActivity = true
                    )
                    else -> SlotButton(
                        icon = R.drawable.ic_widget_mic,
                        desc = "Voice Search",
                        container = GlanceTheme.colors.secondaryContainer,
                        onContainer = GlanceTheme.colors.onSecondaryContainer,
                        intent = micIntent,
                        isActivity = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotButton(
    icon: Int,
    desc: String,
    container: androidx.glance.unit.ColorProvider,
    onContainer: androidx.glance.unit.ColorProvider,
    intent: Intent,
    isActivity: Boolean
) {
    val mod = if (isActivity) {
        GlanceModifier.size(48.dp).cornerRadius(24.dp)
            .background(container)
            .clickable(actionStartActivity(intent))
    } else {
        GlanceModifier.size(48.dp).cornerRadius(24.dp)
            .background(container)
            .clickable(actionSendBroadcast(intent))
    }
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = desc,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = androidx.glance.ColorFilter.tint(onContainer)
        )
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsGlanceWidget()
}
