package com.frerox.toolz.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TimerPresetChips(
    timerHistory: List<Pair<Int, Int>>,
    enabled: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onPresetSelected: (minutes: Int, seconds: Int) -> Unit,
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
                timerHistory.take(3)
            } else {
                listOf(Pair(5, 0), Pair(15, 0), Pair(30, 0))
            }

            presets.forEach { (mins, secs) ->
                val label = if (secs > 0) {
                    if (mins > 0) "$mins:${String.format("%02d", secs)}" else "${secs}s"
                } else {
                    "$mins min"
                }

                FilterChip(
                    selected = false,
                    onClick = { if (enabled) onPresetSelected(mins, secs) },
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
