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

package com.frerox.toolz.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * T-P2-01: shared preset chips with parity to TimerScreen local presets.
 * Filters >0, locale-aware labels, long-press edit, a11y descriptions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerPresetChips(
    timerHistory: List<Pair<Int, Int>>,
    enabled: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onPresetSelected: (minutes: Int, seconds: Int) -> Unit,
    onPresetLongClick: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Quick presets",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val presets = if (timerHistory.isNotEmpty()) {
                timerHistory.filter { (m, s) -> m > 0 || s > 0 }.take(3)
            } else {
                listOf(Pair(5, 0), Pair(15, 0), Pair(30, 0))
            }.ifEmpty { listOf(Pair(5, 0), Pair(15, 0), Pair(30, 0)) }

            presets.forEachIndexed { index, (mins, secs) ->
                val label = if (secs > 0) {
                    if (mins > 0) {
                        String.format(Locale.getDefault(), "%d:%02d", mins, secs)
                    } else {
                        String.format(Locale.getDefault(), "%ds", secs)
                    }
                } else {
                    String.format(Locale.getDefault(), "%d min", mins)
                }

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .combinedClickable(
                            enabled = enabled,
                            onClick = { onPresetSelected(mins, secs) },
                            onLongClick = onPresetLongClick?.let { cb -> { cb(index) } },
                        )
                        .semantics {
                            contentDescription = "Preset $label"
                        },
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                            )
                        },
                        enabled = enabled,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}
