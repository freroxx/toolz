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

package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EquationSolverScreen(
    viewModel: EquationSolverViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }

    EquationSolverScreenContent(
        state = state,
        history = history,
        onBack = onBack,
        onTypeChange = viewModel::onTypeChange,
        onCoefficientSelected = viewModel::onCoefficientSelected,
        onKeyInput = viewModel::onKeyInput,
        onSolve = viewModel::solve,
        onClearAll = viewModel::clear,
        onShowHistory = { showHistory = true },
        onShowSteps = { showSteps = true }
    )

    if (showHistory) {
        HistoryBottomSheet(
            history = history,
            onDismiss = { showHistory = false },
            onSelect = { 
                showHistory = false
            }
        )
    }

    if (showSteps) {
        StepsBottomSheet(
            steps = state.steps,
            onDismiss = { showSteps = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EquationSolverScreenContent(
    state: SolverState,
    history: List<MathHistory>,
    onBack: () -> Unit,
    onTypeChange: (EquationType) -> Unit,
    onCoefficientSelected: (String?) -> Unit,
    onKeyInput: (String) -> Unit,
    onSolve: () -> Unit,
    onClearAll: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSteps: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCoefficientSelected(null) }
            )
    ) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    title = "EQUATION SOLVER",
                    subtitle = "Complex algebraic resolution",
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onShowHistory,
                            modifier = Modifier.padding(end = 12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "History")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = 24.dp)
            ) {
                // Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val statusText = when {
                        state.isSolving -> "Solving..."
                        state.result.isNotEmpty() -> "Solved"
                        else -> "Ready"
                    }
                    val statusIcon = when {
                        state.isSolving -> Icons.Rounded.PlayArrow
                        state.result.isNotEmpty() -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.Edit
                    }
                    val statusColor = when {
                        state.isSolving -> MaterialTheme.colorScheme.primary
                        state.result.isNotEmpty() -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    ExpressiveStatePill(
                        text = statusText,
                        icon = statusIcon,
                        color = statusColor
                    )
                }

                // Type Selector
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(EquationType.entries.size) { index ->
                        val type = EquationType.entries[index]
                        val isSelected = state.selectedType == type
                        StaggeredEntrance(index = index) {
                            ExpressiveFilterChip(
                                selected = isSelected,
                                onClick = { onTypeChange(type) },
                                label = {
                                    Text(
                                        text = type.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                },
                                shape = SmallExpressiveShape
                            )
                        }
                    }
                }

                // Main Input Card
                StaggeredEntrance(index = 2) {
                    ExpressiveCard(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        shape = BouncyShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                        elevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            EquationPreviewWithAnimatedContent(state)
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Box(contentAlignment = Alignment.Center) {
                                // Dynamic inputs based on type
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().alpha(if (state.isSolving) 0.3f else 1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val keys = when (state.selectedType) {
                                        EquationType.LINEAR -> listOf("a", "b")
                                        EquationType.QUADRATIC -> listOf("a", "b", "c")
                                        EquationType.CUBIC -> listOf("a", "b", "c", "d")
                                        EquationType.QUARTIC -> listOf("a", "b", "c", "d", "e")
                                        EquationType.SYSTEM2 -> listOf("a", "b", "c", "d", "e", "f")
                                    }
                                    
                                    keys.forEach { key ->
                                        ExpressiveCoeffInput(
                                            label = key,
                                            value = state.coefficients[key] ?: "",
                                            isSelected = state.selectedCoefficient == key,
                                            onClick = { onCoefficientSelected(key) }
                                        )
                                    }
                                }

                                if (state.isSolving) {
                                    ExpressiveContainedLoadingIndicator(
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Results and Error
                Box(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = state.result.isNotEmpty() && !state.isSolving,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                ExpressiveCard(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    shape = MediumExpressiveShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    elevation = 0.dp
                                ) {
                                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("SOLUTION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        Text(state.result, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                ToolzOutlinedExpressiveButton(
                                    onClick = onShowSteps,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = SmallExpressiveShape,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("HOW TO SOLVE?", fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        AnimatedVisibility(visible = state.error != null) {
                            Text(
                                state.error ?: "", 
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Custom Keypad
                AnimatedVisibility(
                    visible = state.selectedCoefficient != null && !state.isSolving,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    EquationKeypad(
                        onKeyInput = onKeyInput,
                        onSolve = onSolve,
                        onClearAll = onClearAll,
                        constants = state.constants
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun EquationPreviewWithAnimatedContent(state: SolverState) {
    AnimatedContent(
        targetState = state.selectedType,
        transitionSpec = {
            (slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut())
                .using(SizeTransform(clip = false))
        },
        label = "equation_preview"
    ) { type ->
        val text = when (type) {
            EquationType.LINEAR -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }} = 0"
            EquationType.QUADRATIC -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x² + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }}x + ${state.coefficients["c"].orEmpty().ifEmpty { "c" }} = 0"
            EquationType.CUBIC -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x³ + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }}x² + ${state.coefficients["c"].orEmpty().ifEmpty { "c" }}x + ${state.coefficients["d"].orEmpty().ifEmpty { "d" }} = 0"
            EquationType.QUARTIC -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x⁴ + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }}x³ + ${state.coefficients["c"].orEmpty().ifEmpty { "c" }}x² + ${state.coefficients["d"].orEmpty().ifEmpty { "d" }}x + ${state.coefficients["e"].orEmpty().ifEmpty { "e" }} = 0"
            EquationType.SYSTEM2 -> {
                val s1 = "${state.coefficients["a"].orEmpty().ifEmpty { "a1" }}x + ${state.coefficients["b"].orEmpty().ifEmpty { "b1" }}y = ${state.coefficients["c"].orEmpty().ifEmpty { "c1" }}"
                val s2 = "${state.coefficients["d"].orEmpty().ifEmpty { "a2" }}x + ${state.coefficients["e"].orEmpty().ifEmpty { "b2" }}y = ${state.coefficients["f"].orEmpty().ifEmpty { "c2" }}"
                "$s1\n$s2"
            }
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ExpressiveCoeffInput(
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberToolzHapticFeedback()
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        label = "coeff_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "coeff_border"
    )

    Surface(
        onClick = {
            haptic.tick()
            onClick()
        },
        modifier = Modifier.width(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.ifEmpty { "—" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EquationKeypad(
    onKeyInput: (String) -> Unit,
    onSolve: () -> Unit,
    onClearAll: () -> Unit,
    constants: List<ConstantItem>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Constants Carousel
        ExpressiveCarousel(
            items = constants,
            modifier = Modifier.height(72.dp),
            preferredItemWidth = 100.dp,
            contentPadding = PaddingValues(horizontal = 4.dp),
            itemSpacing = 8.dp
        ) { constant ->
            ConstantItemView(constant, onKeyInput)
        }

        // Numeric Keys
        val rows = listOf(
            listOf("7", "8", "9", "BS"),
            listOf("4", "5", "6", "AC"),
            listOf("1", "2", "3", "+/-"),
            listOf("0", ".", "π", "=")
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    KeypadButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "=" -> onSolve()
                                "AC" -> onClearAll()
                                else -> onKeyInput(key)
                            }
                        },
                        isAction = key == "=" || key == "BS" || key == "AC",
                        isClear = key == "AC"
                    )
                }
            }
        }
    }
}

@Composable
fun ConstantItemView(constant: ConstantItem, onKeyInput: (String) -> Unit) {
    val haptic = rememberToolzHapticFeedback()
    var showInfo by remember { mutableStateOf(false) }

    ExpressiveCard(
        onClick = { 
            haptic.tick()
            onKeyInput(constant.value) 
        },
        onLongClick = { 
            haptic.longClick()
            showInfo = !showInfo 
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = SmallExpressiveShape,
        elevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = constant.symbol,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (showInfo) {
                    Text(
                        text = constant.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isAction: Boolean = false,
    isClear: Boolean = false
) {
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        isAction -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    }
    
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isClear -> MaterialTheme.colorScheme.onErrorContainer
        isAction -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .height(56.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "BS") {
                Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = contentColor, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsBottomSheet(
    steps: List<String>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.7f).padding(horizontal = 28.dp).padding(bottom = 48.dp)) {
            Text(
                "SOLUTION STEPS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(steps.size) { index ->
                    Row {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(steps[index], style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    if (index < steps.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 48.dp, top = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    history: List<MathHistory>,
    onDismiss: () -> Unit,
    onSelect: (MathHistory) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.6f).padding(horizontal = 28.dp).padding(bottom = 32.dp)) {
            Text(
                "HISTORY LOG",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { item ->
                    ExpressiveCard(
                        onClick = { onSelect(item) },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MediumExpressiveShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(item.expression, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Result: ${item.result}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EquationSolverPreview() {
    ToolzTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Preview without actual VM logic
            EquationSolverScreenContent(
                state = SolverState(),
                history = emptyList(),
                onBack = {},
                onTypeChange = {},
                onCoefficientSelected = {},
                onKeyInput = {},
                onSolve = {},
                onClearAll = {},
                onShowHistory = {},
                onShowSteps = {}
            )
        }
    }
}
