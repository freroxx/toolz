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

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────
//  BMI colour palette
// ─────────────────────────────────────────────────────────────

private val BmiUnderweight = Color(0xFF4FC3F7)
private val BmiHealthy     = Color(0xFF66BB6A)
private val BmiOverweight  = Color(0xFFFFA726)
private val BmiObese       = Color(0xFFEF5350)

private fun bmiColor(bmi: Float?, range: Pair<Float, Float>): Color = when {
    bmi == null || bmi <= 0f -> Color(0xFF9E9E9E)
    bmi < range.first        -> BmiUnderweight
    bmi <= range.second      -> BmiHealthy
    bmi < 30f                -> BmiOverweight
    else                     -> BmiObese
}

// ─────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BmiScreen(
    viewModel: BmiViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current

    val currentBmiColor = bmiColor(state.bmi, state.healthyRange)
    val animatedBmiColor by animateColorAsState(
        targetValue = currentBmiColor,
        animationSpec = tween(durationMillis = 600),
        label = "bmiColor"
    )

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_BmiScreen_f1a2),
                subtitle = stringResource(R.string.st_BmiScreen_3d5b),
                navigationIcon = {
                    IconButton(
                        onClick  = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.st_BmiScreen_9e2c))
                    }
                },
                colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        containerColor    = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 20.dp, bottom = 20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── BMI wavy ring card ───────────────────────────────────
                StaggeredEntrance(index = 0) {
                    ResultCard(
                        bmi      = state.bmi,
                        category = state.category,
                        range    = state.healthyRange,
                        insight  = state.insight,
                        color    = animatedBmiColor,
                        gender   = state.gender,
                        age      = state.age
                    )
                }

                // ── Goal card ────────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.weightDifference != null,
                    enter   = slideInVertically { -it } + fadeIn(),
                    exit    = slideOutVertically { -it } + fadeOut(),
                ) {
                    StaggeredEntrance(index = 1) {
                        state.weightDifference?.let { diff ->
                            GoalCard(
                                difference = diff,
                                unit       = if (state.isKg) "KG" else "LB",
                            )
                        }
                    }
                }

                // ── Inputs ───────────────────────────────────────────────
                StaggeredEntrance(index = 2) {
                    InputPanel(state = state, viewModel = viewModel, accentColor = animatedBmiColor)
                }

                // ── Activity level ───────────────────────────────────────
                StaggeredEntrance(index = 3) {
                    ActivityPanel(
                        selected  = state.activity,
                        onSelect  = viewModel::onActivityChange,
                        onTakeQuiz = { viewModel.toggleQuiz(true) },
                        accentColor = animatedBmiColor
                    )
                }

                // ── Advanced metrics ─────────────────────────────────────
                AnimatedVisibility(
                    visible = state.bmi != null,
                    enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        BioImpactSection(state = state, accentColor = animatedBmiColor)
                        AdvancedMetrics(state = state, accentColor = animatedBmiColor)
                    }
                }
                
                // ── Nutrition & Hydration ────────────────────────────────
                AnimatedVisibility(
                    visible = state.bmi != null,
                    enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    StaggeredEntrance(index = 5) {
                        NutritionSection(state = state, accentColor = animatedBmiColor)
                    }
                }

                // ── BMI classification ───────────────────────────────────
                AnimatedVisibility(
                    visible = state.bmi != null,
                    enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    StaggeredEntrance(index = 6) {
                        BmiInfoSection(
                            bmi          = state.bmi ?: 0f,
                            healthyRange = state.healthyRange,
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (state.showQuiz) {
        ActivityQuiz(
            onDismiss = { viewModel.toggleQuiz(false) },
            onComplete = { viewModel.applyQuizResult(it) },
            accentColor = animatedBmiColor
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Result card — Wavy BMI ring
// ─────────────────────────────────────────────────────────────

@Composable
fun ResultCard(
    bmi: Float?,
    category: String,
    range: Pair<Float, Float>,
    insight: String,
    color: Color,
    gender: Gender,
    age: String
) {
    val animatedBmi by animateFloatAsState(
        targetValue   = bmi ?: 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label         = "bmiAnim",
    )

    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(32.dp),
        containerColor    = color.copy(alpha = 0.08f),
        border   = BorderStroke(2.dp, color.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(220.dp),
            ) {
                ToolzWavyCircularProgressIndicator(
                    progress = { (animatedBmi / 45f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color    = color,
                    trackColor = color.copy(alpha = 0.1f)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = if (bmi == null) "—" else "%.1f".format(animatedBmi),
                        style     = MaterialTheme.typography.displayMedium.copy(
                            fontWeight    = FontWeight.Black,
                            fontSize      = 64.sp,
                            letterSpacing = (-2).sp,
                        ),
                        color     = if (bmi == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f) else color,
                    )
                    Text(
                        stringResource(R.string.st_BmiScreen_1a2b),
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = color.copy(alpha = 0.6f),
                        letterSpacing = 2.sp,
                    )
                    if (bmi != null) {
                        Text(
                            "Profile: ${age.ifEmpty { "2" }}y ${gender.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = color.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState  = category.ifEmpty { stringResource(R.string.st_BmiScreen_7c4d) },
                transitionSpec = {
                    (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
                },
                label = "category",
            ) { label ->
                Surface(
                    color  = color,
                    shape  = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text      = label.uppercase(),
                        modifier  = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        style     = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color     = Color.White,
                        letterSpacing = 1.5.sp,
                    )
                }
            }
            
            if (insight.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Goal card
// ─────────────────────────────────────────────────────────────

@Composable
fun GoalCard(difference: Float, unit: String) {
    val atIdeal = abs(difference) < 1f
    val needGain = difference < -1f
    val color = when {
        atIdeal   -> BmiHealthy
        needGain  -> BmiUnderweight
        else      -> BmiOverweight
    }

    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(24.dp),
        containerColor    = color.copy(alpha = 0.1f),
        border   = BorderStroke(1.5.dp, color.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier         = Modifier.size(48.dp).clip(CircleShape).background(color.copy(0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        atIdeal  -> Icons.Rounded.CheckCircle
                        needGain -> Icons.Rounded.ArrowUpward
                        else     -> Icons.Rounded.ArrowDownward
                    },
                    null,
                    tint     = color,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    when {
                        atIdeal  -> stringResource(R.string.st_BmiScreen_5f6e)
                        needGain -> "GAIN %.1f %s FOR IDEAL".format(abs(difference), unit)
                        else     -> "LOSE %.1f %s FOR IDEAL".format(abs(difference), unit)
                    },
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color      = color,
                )
                Text(
                    stringResource(R.string.st_BmiScreen_2b8a),
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Input panel — Expressive Sliders
// ─────────────────────────────────────────────────────────────

@Composable
private fun InputPanel(state: BmiState, viewModel: BmiViewModel, accentColor: Color) {
    val haptic = rememberToolzHapticFeedback()
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(32.dp),
        containerColor    = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
        border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
        elevation = 0.dp
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Bio-Data: Age & Gender
            Column {
                SectionLabel(stringResource(R.string.st_BmiScreen_4d9c))
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.st_BmiScreen_6a1b), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        BmiTextField(
                            value         = state.age,
                            onValueChange = viewModel::onAgeChange,
                            icon          = Icons.Rounded.Cake,
                            placeholder   = stringResource(R.string.st_BmiScreen_1b2c),
                            keyboardType  = androidx.compose.ui.text.input.KeyboardType.Number,
                            accentColor   = accentColor
                        )
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.st_BmiScreen_3c4d), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        GenderToggleInPanel(state.gender) { viewModel.onGenderChange(it) }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f))

            // Weight Slider & Input
            Column {
                InputHeaderContainer(
                    label = stringResource(R.string.st_BmiScreen_5d6e),
                    toggle = if (state.isKg) "KG" else "LB",
                    onToggle = { viewModel.toggleUnit(false) }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val weightVal = state.weight.toFloatOrNull() ?: 0f
                    val weightRange = if (state.isKg) 30f..250f else 65f..550f
                    
                    Box(modifier = Modifier.weight(1f)) {
                        ExpressiveSlider(
                            value = weightVal.coerceIn(weightRange.start, weightRange.endInclusive),
                            onValueChange = { viewModel.onWeightChange("%.1f".format(it)) },
                            valueRange = weightRange,
                            colors = SliderDefaults.colors(
                                activeTrackColor = accentColor,
                                thumbColor = accentColor
                            )
                        )
                    }
                    
                    OutlinedTextField(
                        value = state.weight,
                        onValueChange = viewModel::onWeightChange,
                        modifier = Modifier.width(90.dp),
                        label = { Text(stringResource(R.string.st_BmiScreen_7e8f), style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = accentColor.copy(0.3f)
                        )
                    )
                }
            }

            // Height Slider & Input
            Column {
                InputHeaderContainer(
                    label = stringResource(R.string.st_BmiScreen_9f0a),
                    toggle = if (state.isCm) "CM" else "FT/IN",
                    onToggle = { viewModel.toggleUnit(true) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val heightVal = if (state.isCm) state.height.toFloatOrNull() ?: 170f else BmiViewModel.parseImperialHeight(state.height)
                    val heightRange = if (state.isCm) 100f..250f else 40f..100f
                    
                    Box(modifier = Modifier.weight(1f)) {
                        ExpressiveSlider(
                            value = heightVal.coerceIn(heightRange.start, heightRange.endInclusive),
                            onValueChange = { viewModel.onHeightSliderChange(it) },
                            valueRange = heightRange,
                            colors = SliderDefaults.colors(
                                activeTrackColor = accentColor,
                                thumbColor = accentColor
                            )
                        )
                    }

                    OutlinedTextField(
                        value = state.height,
                        onValueChange = viewModel::onHeightChange,
                        modifier = Modifier.width(90.dp),
                        label = { Text(stringResource(R.string.st_BmiScreen_7e8f), style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = accentColor.copy(0.3f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun GenderToggleInPanel(current: Gender, onToggle: (Gender) -> Unit) {
    val haptic = rememberToolzHapticFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(Gender.MALE, Gender.FEMALE).forEach { gender ->
            val isSelected = current == gender
            val color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .bouncyClick { 
                        haptic.tick()
                        onToggle(gender) 
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (gender == Gender.MALE) Icons.Rounded.Male else Icons.Rounded.Female,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )
                    Text(
                        gender.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun InputHeaderContainer(
    label: String,
    toggle: String? = null,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.height(28.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style         = MaterialTheme.typography.labelSmall,
            fontWeight    = FontWeight.Black,
            color         = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
        if (toggle != null && onToggle != null) {
            Spacer(Modifier.weight(1f))
            Surface(
                onClick  = onToggle,
                color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape    = RoundedCornerShape(8.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Text(
                    toggle,
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BmiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
    accentColor: Color
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        shape         = RoundedCornerShape(16.dp),
        leadingIcon   = {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor     = Color.Transparent,
            focusedBorderColor       = accentColor.copy(alpha = 0.5f),
            unfocusedContainerColor  = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor    = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    )
}

// ─────────────────────────────────────────────────────────────
//  Activity level panel — Vertical Descriptive Cards
// ─────────────────────────────────────────────────────────────

@Composable
private fun ActivityPanel(
    selected: ActivityLevel,
    onSelect: (ActivityLevel) -> Unit,
    onTakeQuiz: () -> Unit,
    accentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel(stringResource(R.string.st_BmiScreen_a1b2))
            ToolzOutlinedExpressiveButton(
                onClick = onTakeQuiz,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_BmiScreen_c3d4), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityLevel.entries.forEach { level ->
                val isSelected = level == selected
                ActivityLevelCard(
                    level = level,
                    isSelected = isSelected,
                    onSelect = { onSelect(level) },
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
fun ActivityLevelCard(
    level: ActivityLevel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    accentColor: Color
) {
    val scale by animateFloatAsState(if (isSelected) 1.02f else 1f, label = "levelScale")
    val alpha by animateFloatAsState(if (isSelected) 1f else 0.7f, label = "levelAlpha")
    val haptic = rememberToolzHapticFeedback()

    ExpressiveCard(
        onClick = {
            haptic.click()
            onSelect()
        },
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(24.dp),
        containerColor = if (isSelected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
        border = if (isSelected) BorderStroke(2.dp, accentColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
        elevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp).graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (level) {
                        ActivityLevel.SEDENTARY -> Icons.Rounded.Desk
                        ActivityLevel.LIGHT -> Icons.Rounded.DirectionsWalk
                        ActivityLevel.MODERATE -> Icons.Rounded.FitnessCenter
                        ActivityLevel.ACTIVE -> Icons.Rounded.DirectionsRun
                        ActivityLevel.EXTREME -> Icons.Rounded.Bolt
                    },
                    null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = level.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = level.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = if (isSelected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "×%.2f".format(level.multiplier),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Advanced metrics grid
// ─────────────────────────────────────────────────────────────

@Composable
fun BioImpactSection(state: BmiState, accentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.st_BmiScreen_e5f6))
        
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            containerColor = accentColor.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
            elevation = 0.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImpactItem(
                        label = stringResource(R.string.st_BmiScreen_g7h8),
                        value = "${if (state.genderBmrOffset >= 0) "+" else ""}${state.genderBmrOffset.toInt()} kcal",
                        icon = if (state.gender == Gender.MALE) Icons.Rounded.Male else Icons.Rounded.Female,
                        accentColor = accentColor
                    )
                    VerticalDivider(modifier = Modifier.height(30.dp), color = accentColor.copy(alpha = 0.1f))
                    ImpactItem(
                        label = stringResource(R.string.st_BmiScreen_i9j0),
                        value = "${if (state.genderBfpOffset >= 0) "+" else ""}${state.genderBfpOffset}%",
                        icon = Icons.Rounded.PieChart,
                        accentColor = accentColor
                    )
                }
                
                HorizontalDivider(color = accentColor.copy(alpha = 0.1f))
                
                val ageImpact = if ((state.age.toIntOrNull() ?: 0) >= 65) "Geriatric Optimized" else "Standard Adult"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.History, null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Age Strategy: $ageImpact",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ImpactItem(label: String, value: String, icon: ImageVector, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = accentColor)
        }
    }
}

@Composable
fun AdvancedMetrics(state: BmiState, accentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel(stringResource(R.string.st_BmiScreen_k1l2))

        val gridModifier = Modifier.fillMaxWidth()
        val spacing = 12.dp

        Row(gridModifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricCard(
                title    = stringResource(R.string.st_BmiScreen_m3n4),
                value    = state.bmr?.let { "%.0f".format(it) } ?: "--",
                unit     = "KCAL",
                subtitle = stringResource(R.string.st_BmiScreen_o5p6),
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FlashOn,
                accentColor = accentColor,
                impactValue = state.genderBmrOffset,
                impactLabel = "Gender"
            )
            MetricCard(
                title    = stringResource(R.string.st_BmiScreen_q7r8),
                value    = state.tdee?.let { "%.0f".format(it) } ?: "--",
                unit     = "KCAL",
                subtitle = stringResource(R.string.st_BmiScreen_s9t0),
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.LocalFireDepartment,
                accentColor = accentColor
            )
        }

        Row(gridModifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricCard(
                title    = stringResource(R.string.st_BmiScreen_u1v2),
                value    = state.bfp?.let { "%.1f".format(it) } ?: "--",
                unit     = "%",
                subtitle = stringResource(R.string.st_BmiScreen_w3x4),
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.PieChart,
                accentColor = accentColor,
                impactValue = state.genderBfpOffset,
                impactLabel = "Gender"
            )
            MetricCard(
                title    = stringResource(R.string.st_BmiScreen_y5z6),
                value    = state.ibw?.let { "%.1f".format(it) } ?: "--",
                unit     = if (state.isKg) "KG" else "LB",
                subtitle = stringResource(R.string.st_BmiScreen_a7b8),
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Flag,
                accentColor = accentColor
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color,
    impactValue: Float? = null,
    impactLabel: String? = null
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape    = RoundedCornerShape(24.dp),
        containerColor    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
        elevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Black,
                    color         = accentColor,
                    letterSpacing = 0.5.sp,
                )
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = accentColor.copy(alpha = 0.5f)
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier          = Modifier.padding(vertical = 4.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style      = MaterialTheme.typography.labelSmall,
                    modifier   = Modifier.padding(bottom = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color      = accentColor.copy(alpha = 0.8f),
                )
            }
            
            if (impactValue != null && impactLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (impactValue >= 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        null,
                        modifier = Modifier.size(10.dp),
                        tint = if (impactValue >= 0) BmiOverweight else BmiUnderweight
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$impactLabel: ${if (impactValue >= 0) "+" else ""}${"%.0f".format(impactValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                    )
                }
            } else {
                Text(
                    subtitle,
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Nutrition & Hydration
// ─────────────────────────────────────────────────────────────

@Composable
fun NutritionSection(state: BmiState, accentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel(stringResource(R.string.st_BmiScreen_c9d0))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Maintenance
            CalorieGoalCard(
                label = stringResource(R.string.st_BmiScreen_e1f2),
                value = state.tdee?.toInt() ?: 0,
                color = accentColor,
                modifier = Modifier.weight(1f)
            )
            // Weight Loss
            CalorieGoalCard(
                label = stringResource(R.string.st_BmiScreen_g3h4),
                value = state.lossCalories?.toInt() ?: 0,
                color = BmiUnderweight,
                modifier = Modifier.weight(1f)
            )
            // Weight Gain
            CalorieGoalCard(
                label = stringResource(R.string.st_BmiScreen_i5j6),
                value = state.gainCalories?.toInt() ?: 0,
                color = BmiOverweight,
                modifier = Modifier.weight(1f)
            )
        }

        // Water Intake
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            containerColor    = Color(0xFFE3F2FD),
            border   = BorderStroke(1.dp, Color(0xFF90CAF9)),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFBBDEFB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = Color(0xFF1976D2))
                }
                Column {
                    Text(
                        stringResource(R.string.st_BmiScreen_k7l8),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        "%.1f LITERS / DAY".format(state.waterIntake ?: 0f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0D47A1)
                    )
                }
            }
        }

        // Macros
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            containerColor    = MaterialTheme.colorScheme.surfaceContainerLow,
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
            elevation = 0.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.st_BmiScreen_m9n0),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroItem(stringResource(R.string.st_BmiScreen_o1p2), state.protein ?: 0f, Color(0xFFEF5350), Modifier.weight(1f))
                    MacroItem(stringResource(R.string.st_BmiScreen_q3r4), state.carbs ?: 0f, Color(0xFF66BB6A), Modifier.weight(1f))
                    MacroItem(stringResource(R.string.st_BmiScreen_s5t6), state.fats ?: 0f, Color(0xFFFFA726), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MacroItem(label: String, grams: Float, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        Text("%.0fg".format(grams), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CalorieGoalCard(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color.copy(0.7f))
            Text(
                text = if (value > 0) value.toString() else "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text("KCAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color.copy(0.5f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  BMI classification list
// ─────────────────────────────────────────────────────────────

@Composable
fun BmiInfoSection(bmi: Float, healthyRange: Pair<Float, Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.st_BmiScreen_u7v8))
        BmiCategoryItem(stringResource(R.string.st_BmiScreen_w9x0), "< %.1f".format(healthyRange.first),       BmiUnderweight, bmi < healthyRange.first)
        BmiCategoryItem(stringResource(R.string.st_BmiScreen_a1b3),      "%.1f – %.1f".format(healthyRange.first, healthyRange.second), BmiHealthy, bmi in healthyRange.first..healthyRange.second)
        BmiCategoryItem(stringResource(R.string.st_BmiScreen_c3d5),  "%.1f – 29.9".format(healthyRange.second + 0.1f), BmiOverweight, bmi > healthyRange.second && bmi < 30f)
        BmiCategoryItem(stringResource(R.string.st_BmiScreen_e5f7),     "≥ 30.0",                                  BmiObese,       bmi >= 30f)
    }
}

@Composable
fun BmiCategoryItem(
    label: String,
    range: String,
    color: Color,
    isSelected: Boolean,
) {
    val scale by animateFloatAsState(
        if (isSelected) 1.02f else 1f,
        spring(Spring.DampingRatioMediumBouncy),
        label = "cat_scale",
    )
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape    = RoundedCornerShape(20.dp),
        containerColor    = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border   = if (isSelected) BorderStroke(2.dp, color.copy(alpha = 0.4f))
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
        elevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .size(if (isSelected) 14.dp else 12.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = if (isSelected) 1f else 0.4f))
                        .then(if (isSelected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                )
                Text(
                    label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    color      = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                range,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                color      = if (isSelected) color.copy(0.9f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Shared helper
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Text(
            text,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color      = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Activity Quiz Component — ModalBottomSheet for 10x UX
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityQuiz(
    onDismiss: () -> Unit,
    onComplete: (Int) -> Unit,
    accentColor: Color
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var totalScore by remember { mutableIntStateOf(0) }

    val questions = listOf(
        QuizQuestion(
            stringResource(R.string.st_BmiScreen_g7h9),
            stringResource(R.string.st_BmiScreen_i9j1),
            Icons.Rounded.Work,
            listOf(
                QuizOption(stringResource(R.string.st_BmiScreen_k1l3), Icons.Rounded.DesktopMac, 0),
                QuizOption(stringResource(R.string.st_BmiScreen_m3n5), Icons.Rounded.EmojiPeople, 1),
                QuizOption(stringResource(R.string.st_BmiScreen_o5p7), Icons.Rounded.DirectionsWalk, 2),
                QuizOption(stringResource(R.string.st_BmiScreen_q7r9), Icons.Rounded.Construction, 3)
            )
        ),
        QuizQuestion(
            stringResource(R.string.st_BmiScreen_s9t1),
            stringResource(R.string.st_BmiScreen_u1v3),
            Icons.Rounded.FitnessCenter,
            listOf(
                QuizOption(stringResource(R.string.st_BmiScreen_w3x5), Icons.Rounded.SentimentNeutral, 0),
                QuizOption(stringResource(R.string.st_BmiScreen_y5z7), Icons.Rounded.Hiking, 1),
                QuizOption(stringResource(R.string.st_BmiScreen_a7b9), Icons.Rounded.SportsHandball, 2),
                QuizOption(stringResource(R.string.st_BmiScreen_c9d1), Icons.Rounded.SportsScore, 3)
            )
        ),
        QuizQuestion(
            stringResource(R.string.st_BmiScreen_e1f3),
            stringResource(R.string.st_BmiScreen_g3h5),
            Icons.Rounded.DirectionsRun,
            listOf(
                QuizOption("< 5,000", Icons.Rounded.VerticalAlignBottom, 0),
                QuizOption("5k - 10k", Icons.Rounded.DirectionsRun, 1),
                QuizOption("10k - 15k", Icons.Rounded.Bolt, 2),
                QuizOption("> 15,000", Icons.Rounded.AutoAwesome, 3)
            )
        ),
        QuizQuestion(
            stringResource(R.string.st_BmiScreen_i5j7),
            stringResource(R.string.st_BmiScreen_k7l9_v2),
            Icons.Rounded.Commute,
            listOf(
                QuizOption(stringResource(R.string.st_BmiScreen_m9n1), Icons.Rounded.Elevator, 0),
                QuizOption(stringResource(R.string.st_BmiScreen_o1p3), Icons.Rounded.Map, 1),
                QuizOption(stringResource(R.string.st_BmiScreen_q3r5), Icons.Rounded.Home, 2),
                QuizOption(stringResource(R.string.st_BmiScreen_s5t7), Icons.Rounded.Terrain, 3)
            )
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(questions[currentStep].icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
                Text(
                    questions[currentStep].label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    letterSpacing = 2.sp
                )
            }
            
            Spacer(Modifier.height(16.dp))

            // Progress
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / questions.size },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = accentColor,
                trackColor = accentColor.copy(0.1f)
            )

            Spacer(Modifier.height(32.dp))

            // Question Text
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "qText"
            ) { step ->
                Text(
                    text = questions[step].text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                questions[currentStep].options.forEach { option ->
                    ExpressiveCard(
                        onClick = {
                            totalScore += option.points
                            if (currentStep < questions.size - 1) {
                                currentStep++
                            } else {
                                onComplete(totalScore)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, accentColor.copy(0.1f)),
                        elevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(accentColor.copy(0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(option.icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.ChevronRight, null, tint = accentColor.copy(0.3f))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_BmiScreen_u7v9), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

data class QuizQuestion(val label: String, val text: String, val icon: ImageVector, val options: List<QuizOption>)
data class QuizOption(val label: String, val icon: ImageVector, val points: Int)
