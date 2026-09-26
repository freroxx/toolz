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
import android.speech.RecognizerIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.frerox.toolz.R
import com.frerox.toolz.widget.ui.WidgetAppearance
import com.frerox.toolz.widget.ui.WidgetSizes
import com.frerox.toolz.widget.ui.readWidgetAppearance
import com.frerox.toolz.widget.ui.toolbarIsExpanded
import com.frerox.toolz.widget.ui.widgetBackgroundProvider
import com.frerox.toolz.widget.ui.widgetNavIntent
import com.frerox.toolz.widget.ui.widgetOuterCornerFor
import kotlinx.coroutines.flow.firstOrNull

// ---------------------------------------------------------------------------
//  Quick Actions toolbar — M3 Expressive, Responsive (compact / expanded).
//  Single 4x1 pill: search field + up to 3 configurable slots.
//  Compact <280dp shows 2 slots, expanded shows 3 (threshold, never ==).
//  Search badge + hint + slots are siblings, never nested clickables.
//  Mic launches system voice recognition; flashlight toggles torch directly
//  via QuickActionsCallback (no app open); QR/pomo/timer deep-link.
// ---------------------------------------------------------------------------

class QuickActionsGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.ToolbarCompact, WidgetSizes.ToolbarExpanded)
    )
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.ToolbarExpanded)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val openSearchIntent = widgetNavIntent(context, "search").apply {
            putExtra("auto_focus_search", true)
        }
        // Real voice search — was a copy of openSearchIntent (did nothing).
        val voiceIntent = try {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search with Toolz")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (_: Exception) {
            openSearchIntent
        }
        val qrIntent = widgetNavIntent(context, "qr_generator")
        val pomodoroIntent = widgetNavIntent(context, "pomodoro")
        val timerIntent = widgetNavIntent(context, "timer")

        val slots = try {
            readToolbarSlots(context)
        } catch (_: Exception) {
            listOf("mic", "flashlight", "qr")
        }
        val appearance: WidgetAppearance = try { readWidgetAppearance(context) } catch (_: Exception) {
            WidgetAppearance()
        }

        provideContent {
            GlanceTheme {
                ToolbarContent(
                    slots = slots,
                    appearance = appearance,
                    openSearchIntent = openSearchIntent,
                    micIntent = voiceIntent,
                    qrIntent = qrIntent,
                    pomodoroIntent = pomodoroIntent,
                    timerIntent = timerIntent
                )
            }
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
        val order = listOf("mic", "flashlight", "qr", "pomodoro", "timer")
        order.filter { slots.contains(it) }.take(3).ifEmpty { listOf("mic", "flashlight", "qr") }
    } catch (_: Exception) {
        listOf("mic", "flashlight", "qr")
    }
}

@Composable
private fun ToolbarContent(
    slots: List<String>,
    appearance: WidgetAppearance,
    openSearchIntent: Intent,
    micIntent: Intent,
    qrIntent: Intent,
    pomodoroIntent: Intent,
    timerIntent: Intent
) {
    // Threshold tier — OneUI hands intermediates during drag; == would clip.
    val isExpanded = toolbarIsExpanded(LocalSize.current)
    val visibleSlots = slots.take(if (isExpanded) 3 else 2)
    val size = LocalSize.current
    val outerBg = widgetBackgroundProvider(appearance) ?: GlanceTheme.colors.surface
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(outerBg)
            .cornerRadius(widgetOuterCornerFor(size.width, size.height)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier.size(48.dp).cornerRadius(24.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity(openSearchIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_search),
                    contentDescription = "Search",
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary)
                )
            }

            Spacer(GlanceModifier.width(12.dp))

            Text(
                text = "Search with Toolz…",
                modifier = GlanceModifier.defaultWeight()
                    .clickable(actionStartActivity(openSearchIntent)),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )

            Spacer(GlanceModifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                visibleSlots.forEachIndexed { index, slot ->
                    if (index > 0) Spacer(GlanceModifier.width(8.dp))
                    when (slot) {
                        "mic" -> SlotButtonIntent(
                            icon = R.drawable.ic_widget_mic,
                            desc = "Voice search",
                            container = GlanceTheme.colors.secondaryContainer,
                            onContainer = GlanceTheme.colors.onSecondaryContainer,
                            intent = micIntent
                        )
                        "flashlight" -> SlotButtonAction(
                            icon = R.drawable.ic_flashlight,
                            desc = "Toggle flashlight",
                            container = GlanceTheme.colors.tertiaryContainer,
                            onContainer = GlanceTheme.colors.onTertiaryContainer,
                            action = actionRunCallback<QuickActionsCallback>(
                                androidx.glance.action.actionParametersOf(
                                    QuickActionsCallback.PARAM_ACTION to QuickActionsCallback.ACTION_FLASHLIGHT
                                )
                            )
                        )
                        "qr" -> SlotButtonIntent(
                            icon = R.drawable.ic_shortcut_qr_generator,
                            desc = "QR scanner",
                            container = GlanceTheme.colors.surfaceVariant,
                            onContainer = GlanceTheme.colors.onSurfaceVariant,
                            intent = qrIntent
                        )
                        "pomodoro" -> SlotButtonIntent(
                            icon = R.drawable.ic_shortcut_pomodoro,
                            desc = "Focus timer",
                            container = GlanceTheme.colors.surfaceVariant,
                            onContainer = GlanceTheme.colors.onSurfaceVariant,
                            intent = pomodoroIntent
                        )
                        "timer" -> SlotButtonIntent(
                            icon = R.drawable.ic_shortcut_timer,
                            desc = "Timer",
                            container = GlanceTheme.colors.surfaceVariant,
                            onContainer = GlanceTheme.colors.onSurfaceVariant,
                            intent = timerIntent
                        )
                        else -> SlotButtonIntent(
                            icon = R.drawable.ic_widget_mic,
                            desc = "Voice search",
                            container = GlanceTheme.colors.secondaryContainer,
                            onContainer = GlanceTheme.colors.onSecondaryContainer,
                            intent = micIntent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotButtonIntent(
    icon: Int,
    desc: String,
    container: androidx.glance.unit.ColorProvider,
    onContainer: androidx.glance.unit.ColorProvider,
    intent: Intent
) {
    Box(
        modifier = GlanceModifier.size(48.dp).cornerRadius(24.dp)
            .background(container)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = desc,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = androidx.glance.ColorFilter.tint(onContainer)
        )
    }
}

@Composable
private fun SlotButtonAction(
    icon: Int,
    desc: String,
    container: androidx.glance.unit.ColorProvider,
    onContainer: androidx.glance.unit.ColorProvider,
    action: Action
) {
    Box(
        modifier = GlanceModifier.size(48.dp).cornerRadius(24.dp)
            .background(container)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = desc,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = androidx.glance.ColorFilter.tint(onContainer)
        )
    }
}
