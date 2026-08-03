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

package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
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
    onBatterySaveChange: (Boolean) -> Unit,
    stepSensitivity: Int,
    onStepSensitivityChange: (Int) -> Unit,
    stepEngineMode: String,
    onStepEngineModeChange: (String) -> Unit
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val isStrict = stepEngineMode == "STRICT"
    val engineColor = if (isStrict) Color(0xFF4FC3F7) else Color(0xFF81C784)

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Tracker Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Customize your tracking experience",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedContent(targetState = isStrict, label = "engineBadge") { strict ->
                    val badgeColor = if (strict) Color(0xFF4FC3F7) else Color(0xFF81C784)
                    Surface(shape = CircleShape, color = badgeColor.copy(alpha = 0.12f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(7.dp).background(badgeColor, CircleShape))
                            Text(
                                text = if (strict) "STRICT" else "SIMPLE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = badgeColor
                            )
                        }
                    }
                }
            }

            // ── 1. Detection Engine ───────────────────────────────────────────
            SettingsSection(title = "Detection Engine", icon = Icons.Rounded.Memory) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpressiveCard(
                            onClick = { onStepEngineModeChange("STRICT") },
                            modifier = Modifier.weight(1f),
                            shape = MediumExpressiveShape,
                            containerColor = if (isStrict) Color(0xFF4FC3F7).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isStrict) BorderStroke(1.dp, Color(0xFF4FC3F7)) else null,
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Shield, null, tint = if (isStrict) Color(0xFF4FC3F7) else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Strict", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (isStrict) Color(0xFF4FC3F7) else MaterialTheme.colorScheme.onSurface)
                                Text("High accuracy. Requires GPS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                            }
                        }
                        
                        ExpressiveCard(
                            onClick = { onStepEngineModeChange("SIMPLE") },
                            modifier = Modifier.weight(1f),
                            shape = MediumExpressiveShape,
                            containerColor = if (!isStrict) Color(0xFF81C784).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (!isStrict) BorderStroke(1.dp, Color(0xFF81C784)) else null,
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Speed, null, tint = if (!isStrict) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Simple", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (!isStrict) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurface)
                                Text("Instant peak detection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                            }
                        }
                    }

                    // Sensitivity
                    ExpressiveCard(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Tune, null, tint = engineColor, modifier = Modifier.size(18.dp))
                                    Text("Sensitivity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Surface(shape = CircleShape, color = engineColor.copy(alpha = 0.1f)) {
                                    Text(
                                        text = when {
                                            stepSensitivity <= 33 -> "LOW"
                                            stepSensitivity <= 67 -> "MED"
                                            else -> "HIGH"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = engineColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            
                            ToolzConnectedButtonGroup(
                                selectedIndex = when {
                                    stepSensitivity <= 33 -> 0
                                    stepSensitivity <= 67 -> 1
                                    else -> 2
                                },
                                options = listOf("Low", "Medium", "High"),
                                onOptionSelected = { idx ->
                                    onStepSensitivityChange(when (idx) { 0 -> 20; 1 -> 50; else -> 80 })
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── 2. Hardware & Sensors ─────────────────────────────────────────
            SettingsSection(title = "Hardware & Sensors", icon = Icons.Rounded.SettingsInputAntenna) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("High Precision", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                if (isStrict) {
                                    Surface(shape = CircleShape, color = Color(0xFF4FC3F7).copy(alpha = 0.12f)) {
                                        Text(
                                            "REQUIRED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF4FC3F7),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                if (isStrict) "Locked ON for strict speed validation"
                                else "Use GPS to verify strides and measure distance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        ExpressiveSwitch(
                            checked = if (isStrict) true else useGps,
                            onCheckedChange = if (isStrict) { _ -> } else onUseGpsChange,
                            enabled = !isStrict
                        )
                    }

                    // Battery Saver
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart Battery Saver", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Throttles sensors when stationary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        ExpressiveSwitch(
                            checked = batterySaveEnabled,
                            onCheckedChange = onBatterySaveChange
                        )
                    }

                    // Measurement System
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpressiveCard(
                            onClick = { onMeasurementSystemChange("Metric") },
                            modifier = Modifier.weight(1f),
                            shape = MediumExpressiveShape,
                            containerColor = if (measurementSystem == "Metric") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (measurementSystem == "Metric") BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Metric", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                                Text("cm, km, kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ExpressiveCard(
                            onClick = { onMeasurementSystemChange("Imperial") },
                            modifier = Modifier.weight(1f),
                            shape = MediumExpressiveShape,
                            containerColor = if (measurementSystem == "Imperial") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (measurementSystem == "Imperial") BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Imperial", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                                Text("in, mi, lb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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

            // ── 3. Target & Body ──────────────────────────────────────────────
            SettingsSection(title = "Target & Body", icon = Icons.Rounded.EmojiEvents) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ExpressiveCard(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        shape = MediumExpressiveShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Daily Step Target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    Text("Set your personal daily goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AnimatedContent(
                                    targetState = stepGoal,
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                        } else {
                                            (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                                        }.using(SizeTransform(clip = false))
                                    },
                                    label = "GoalAnimation"
                                ) { goal ->
                                    Text(
                                        text = "%,d".format(goal),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            ExpressiveSlider(
                                value = stepGoal.toFloat().coerceIn(100f, 50000f),
                                onValueChange = {
                                    onStepGoalChange(it.toInt())
                                },
                                valueRange = 100f..50000f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("50,000", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ParameterItem(
                        label = if (measurementSystem == "Metric") "Step Length (cm)" else "Step Length (in)",
                        value = stepLength,
                        range = 30..150,
                        onValueChange = onStepLengthChange
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = if (measurementSystem == "Metric")
                                "Tip: stride ≈ height (cm) × 0.415 for walking. Most adults: 65–80 cm."
                            else
                                "Tip: stride ≈ height (in) × 0.415 for walking. Most adults: 26–32 in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    ParameterItem(
                        label = "Calories Burn (per 1k)",
                        value = caloriesPer1k,
                        range = 10..100,
                        onValueChange = onCaloriesPer1kChange
                    )
                }
            }

            // ── 4. AI Fitness Agent ───────────────────────────────────────────
            SettingsSection(title = "AI Fitness Agent", icon = Icons.Rounded.AutoAwesome) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable AI Coach", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Personalized fitness advice & motivation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
                            Text("Personality Config", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            AiConfigDropdown(label = "Tone", selected = aiTone, options = listOf("Professional", "Casual", "Strict", "Funny"), onSelected = onAiToneChange)
                            AiConfigDropdown(label = "Mood", selected = aiMood, options = listOf("Encouraging", "Competitive", "Calm", "Energetic"), onSelected = onAiMoodChange)
                            AiConfigDropdown(label = "Style", selected = aiStyle, options = listOf("Concise", "Detailed", "Poetic", "Aggressive"), onSelected = onAiStyleChange)
                        }
                    }
                }
            }

            // ── 5. Data Retention ─────────────────────────────────────────────
            SettingsSection(title = "Privacy & Storage", icon = Icons.Rounded.Storage) {
                ExpressiveCard(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    shape = LargeExpressiveShape,
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                            Column {
                                Text("Step History Retention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Text("Control how long your data is kept", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        val options = listOf("7d", "30d", "1y", "Forever")
                        ToolzConnectedButtonGroup(
                            selectedIndex = options.indexOf(retention).coerceAtLeast(0),
                            options = options.map { if (it == "Forever") "∞" else it.uppercase() },
                            onOptionSelected = { onRetentionChange(options[it]) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (retention == "Forever") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDone, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Maximum data availability", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = SmallExpressiveShape
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
