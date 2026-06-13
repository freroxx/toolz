package com.frerox.toolz.ui.screens.sensors

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StepCounterSettingsBottomSheet(
    onDismissRequest: () -> Unit,
    stepGoal: Int,
    onStepGoalChange: (Int) -> Unit,
    retention: String,
    onRetentionChange: (String) -> Unit,
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    aiProvider: String,
    onAiProviderChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    aiTone: String,
    onAiToneChange: (String) -> Unit,
    aiMood: String,
    onAiMoodChange: (String) -> Unit,
    aiStyle: String,
    onAiStyleChange: (String) -> Unit,
    stepLength: Int,
    onStepLengthChange: (Int) -> Unit,
    caloriesPer1k: Int,
    onCaloriesPer1kChange: (Int) -> Unit,
    availableProviders: List<String>,
    measurementSystem: String,
    onMeasurementSystemChange: (String) -> Unit,
    useGps: Boolean,
    onUseGpsChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    batterySaveEnabled: Boolean,
    onBatterySaveChange: (Boolean) -> Unit
) {
    var goalText by remember { mutableStateOf(stepGoal.toString()) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Tracker Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Customize your flagship experience",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Target & Body Section
            SettingsSection(title = "Target & Body", icon = Icons.Rounded.EmojiEvents) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Daily Step Target", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = goalText,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() } && it.length <= 6) {
                                    goalText = it
                                    it.toIntOrNull()?.let { goal -> onStepGoalChange(goal) }
                                }
                            },
                            modifier = Modifier.width(120.dp).height(56.dp),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            ),
                            singleLine = true,
                            shape = SquircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                    ExpressiveSlider(
                        value = stepGoal.toFloat().coerceIn(100f, 50000f),
                        onValueChange = { 
                            val g = it.toInt()
                            onStepGoalChange(g)
                            goalText = g.toString()
                        },
                        valueRange = 100f..50000f
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    
                    ParameterItem(
                        label = if (measurementSystem == "Metric") "Step Length (cm)" else "Step Length (in)",
                        value = stepLength,
                        range = 30..150,
                        onValueChange = onStepLengthChange
                    )
                    ParameterItem(
                        label = "Calories Burn (per 1k)",
                        value = caloriesPer1k,
                        range = 10..100,
                        onValueChange = onCaloriesPer1kChange
                    )
                }
            }

            // Hardware & Sensors Section
            SettingsSection(title = "Hardware & Sensors", icon = Icons.Rounded.SettingsInputAntenna) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Precision Distance", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Use GPS to verify strides and distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        ExpressiveSwitch(checked = useGps, onCheckedChange = onUseGpsChange)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart Battery Saver", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Throttle sensors when inactive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        ExpressiveSwitch(checked = batterySaveEnabled, onCheckedChange = onBatterySaveChange)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Measurement System", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val systems = listOf("Metric", "Imperial")
                        ToolzConnectedButtonGroup(
                            selectedIndex = systems.indexOf(measurementSystem).coerceAtLeast(0),
                            options = systems,
                            onOptionSelected = { onMeasurementSystemChange(systems[it]) }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Step Notifications", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Daily progress & goal alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        ExpressiveSwitch(checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange)
                    }
                }
            }

            // AI Fitness Agent Section
            SettingsSection(title = "AI Fitness Agent", icon = Icons.Rounded.AutoAwesome) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable AI Coach", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Personalized fitness advice & motivation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        ExpressiveSwitch(checked = aiEnabled, onCheckedChange = onAiEnabledChange)
                    }

                    if (aiEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        if (availableProviders.isEmpty()) {
                            Text(
                                "No AI providers configured. Please add an API key in App Settings.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            AiConfigDropdown(
                                label = "Provider",
                                selected = if (availableProviders.contains(aiProvider)) aiProvider else availableProviders.first(),
                                options = availableProviders,
                                onSelected = onAiProviderChange
                            )
                            AiConfigDropdown(
                                label = "Model",
                                selected = aiModel,
                                options = AiSettingsHelper.getModels(aiProvider),
                                onSelected = onAiModelChange
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            Text(text = "Personality Config", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            
                            val tones = listOf("Professional", "Casual", "Strict", "Funny")
                            AiConfigDropdown(label = "Tone", selected = aiTone, options = tones, onSelected = onAiToneChange)
                            
                            val moods = listOf("Encouraging", "Competitive", "Calm", "Energetic")
                            AiConfigDropdown(label = "Mood", selected = aiMood, options = moods, onSelected = onAiMoodChange)
                            
                            val styles = listOf("Concise", "Detailed", "Poetic", "Aggressive")
                            AiConfigDropdown(label = "Style", selected = aiStyle, options = styles, onSelected = onAiStyleChange)
                        }
                    }
                }
            }
            
            // Retention Section
            ExpressiveCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = SquircleShape,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Text(text = "Data Retention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    val options = listOf("7d", "30d", "1y", "Forever")
                    ToolzConnectedButtonGroup(
                        selectedIndex = options.indexOf(retention).coerceAtLeast(0),
                        options = options,
                        onOptionSelected = { onRetentionChange(options[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MediumExpressiveShape,
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun ParameterItem(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        ExpressiveSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiConfigDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = SmallExpressiveShape
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
