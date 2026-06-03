package com.frerox.toolz.ui.screens.time.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.time.PomodoroState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PomodoroSettingsBottomSheet(
    state: PomodoroState,
    activeColor: Color,
    onDismiss: () -> Unit,
    onWorkMinutesChanged: (Int) -> Unit,
    onShortBreakMinutesChanged: (Int) -> Unit,
    onLongBreakMinutesChanged: (Int) -> Unit,
    onGoalChanged: (Int) -> Unit,
    onAutoStartChanged: (Boolean) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onShowQuotesChanged: (Boolean) -> Unit,
    onQuotesChanged: (String) -> Unit,
    onAiFormat: () -> Unit,
    onResetQuotes: () -> Unit,
    onResetGoal: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Pomodoro Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )

            // Durations
            SettingsSection(title = "Durations", icon = Icons.Rounded.Timer, activeColor = activeColor) {
                DurationSlider(
                    label = "Focus",
                    value = state.workMinutes,
                    onValueChange = onWorkMinutesChanged,
                    range = 1f..60f,
                    activeColor = activeColor
                )
                DurationSlider(
                    label = "Short Break",
                    value = state.shortBreakMinutes,
                    onValueChange = onShortBreakMinutesChanged,
                    range = 1f..15f,
                    activeColor = activeColor
                )
                DurationSlider(
                    label = "Long Break",
                    value = state.longBreakMinutes,
                    onValueChange = onLongBreakMinutesChanged,
                    range = 5f..45f,
                    activeColor = activeColor
                )
            }

            // Options
            SettingsSection(title = "Options", icon = Icons.Rounded.Settings, activeColor = activeColor) {
                PreferenceRow(
                    title = "Sessions Goal",
                    subtitle = "${state.sessionsGoal} focus sessions",
                    icon = Icons.Rounded.Flag,
                    activeColor = activeColor
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onGoalChanged(state.sessionsGoal - 1) }) {
                            Icon(Icons.Rounded.Remove, null)
                        }
                        Text(
                            text = state.sessionsGoal.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { onGoalChanged(state.sessionsGoal + 1) }) {
                            Icon(Icons.Rounded.Add, null)
                        }
                    }
                }

                ToggleRow(
                    title = "Auto Start Next",
                    checked = state.autoStartNext,
                    onCheckedChange = onAutoStartChanged
                )
                
                ToggleRow(
                    title = "Keep Screen On",
                    checked = state.keepScreenOn,
                    onCheckedChange = onKeepScreenOnChanged
                )

                ToolzOutlinedExpressiveButton(
                    onClick = onResetGoal,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SmallExpressiveShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Daily Goal Progress")
                }
            }

            // Quotes
            SettingsSection(title = "Focus Insights", icon = Icons.Rounded.FormatQuote, activeColor = activeColor) {
                ToggleRow(
                    title = "Show Quotes",
                    checked = state.showQuotes,
                    onCheckedChange = onShowQuotesChanged
                )

                if (state.showQuotes) {
                    var editingQuotes by remember(state.quotes) { mutableStateOf(state.quotes) }
                    
                    OutlinedTextField(
                        value = editingQuotes,
                        onValueChange = { editingQuotes = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        label = { Text("Custom Quotes") },
                        shape = SmallExpressiveShape
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolzExpressiveButton(
                            onClick = { onQuotesChanged(editingQuotes) },
                            modifier = Modifier.weight(1f),
                            shape = SmallExpressiveShape
                        ) {
                            Text("Save List")
                        }
                        
                        if (!state.offlineMode) {
                            ToolzOutlinedExpressiveButton(
                                onClick = onAiFormat,
                                modifier = Modifier.weight(1f),
                                enabled = !state.isFormattingQuotes,
                                shape = SmallExpressiveShape
                            ) {
                                if (state.isFormattingQuotes) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("AI Format")
                                }
                            }
                        }
                    }
                    
                    ToolzOutlinedExpressiveButton(
                        onClick = onResetQuotes,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset to Original")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = activeColor, modifier = Modifier.size(20.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = activeColor,
                letterSpacing = 1.sp
            )
        }
        content()
    }
}

@Composable
private fun DurationSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    activeColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("${value}m", style = MaterialTheme.typography.bodySmall, color = activeColor, fontWeight = FontWeight.Black)
        }
        ExpressiveSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = activeColor.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                color = activeColor.copy(alpha = 0.1f),
                shape = SmallExpressiveShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = activeColor, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        ExpressiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
