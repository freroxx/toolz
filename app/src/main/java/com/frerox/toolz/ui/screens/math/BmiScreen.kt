package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmiScreen(
    viewModel: BmiViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "BMI ANALYZER PRO",
                        style         = MaterialTheme.typography.labelMedium,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color         = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick  = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    GenderToggle(state.gender) { viewModel.onGenderChange(it) }
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
                // ── BMI ring card ────────────────────────────────────────
                ResultCard(
                    bmi      = state.bmi,
                    category = state.category,
                    range    = state.healthyRange,
                )

                // ── Goal card ────────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.weightDifference != null,
                    enter   = slideInVertically { -it } + fadeIn(),
                    exit    = slideOutVertically { -it } + fadeOut(),
                ) {
                    state.weightDifference?.let { diff ->
                        GoalCard(
                            difference = diff,
                            unit       = if (state.isKg) "KG" else "LB",
                        )
                    }
                }

                // ── Inputs ───────────────────────────────────────────────
                InputPanel(state = state, viewModel = viewModel)

                // ── Activity level ───────────────────────────────────────
                ActivityPanel(
                    selected  = state.activity,
                    onSelect  = viewModel::onActivityChange,
                )

                // ── Advanced metrics ─────────────────────────────────────
                AnimatedVisibility(
                    visible = state.bmi != null,
                    enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    AdvancedMetrics(state = state)
                }
                
                // ── Nutrition & Hydration ────────────────────────────────
                AnimatedVisibility(
                    visible = state.bmi != null,
                    enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    NutritionSection(state = state)
                }

                // ── BMI classification ───────────────────────────────────
                AnimatedVisibility(
                    visible = state.bmi != null,
                    enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    BmiInfoSection(
                        bmi          = state.bmi ?: 0f,
                        healthyRange = state.healthyRange,
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun GenderToggle(current: Gender, onToggle: (Gender) -> Unit) {
    val isMale = current == Gender.MALE
    Surface(
        onClick   = { onToggle(if (isMale) Gender.FEMALE else Gender.MALE) },
        modifier  = Modifier.padding(end = 8.dp),
        shape     = RoundedCornerShape(16.dp),
        color     = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                targetState = isMale,
                transitionSpec = {
                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                },
                label = "gender_icon"
            ) { male ->
                Icon(
                    if (male) Icons.Rounded.Male else Icons.Rounded.Female,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint     = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                if (isMale) "MALE" else "FEMALE",
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Black,
                color         = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Result card — segmented BMI ring
// ─────────────────────────────────────────────────────────────

@Composable
fun ResultCard(
    bmi: Float?,
    category: String,
    range: Pair<Float, Float>,
) {
    val animatedBmi by animateFloatAsState(
        targetValue   = bmi ?: 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label         = "bmiAnim",
    )
    
    val animatedRangeStart by animateFloatAsState(range.first, label = "rangeStart")
    val animatedRangeEnd by animateFloatAsState(range.second, label = "rangeEnd")

    val color = bmiColor(animatedBmi.takeIf { it > 0f }, animatedRangeStart to animatedRangeEnd)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(32.dp),
        color    = color.copy(alpha = 0.05f),
        border   = BorderStroke(1.5.dp, color.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(240.dp).padding(10.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 22.dp.toPx()
                    val startAngle = 135f
                    val sweepTotal = 270f
                    
                    val minBmi = 10f
                    val maxBmi = 45f
                    val totalRange = maxBmi - minBmi
                    
                    fun getSweep(value: Float) = (value / totalRange) * sweepTotal
                    
                    val underSweep = getSweep(animatedRangeStart - minBmi).coerceIn(0.1f, sweepTotal)
                    val healthySweep = getSweep(animatedRangeEnd - animatedRangeStart).coerceIn(0.1f, sweepTotal - underSweep)
                    val overSweep = getSweep(30f - animatedRangeEnd).coerceIn(0.1f, sweepTotal - underSweep - healthySweep)
                    val obeseSweep = (sweepTotal - underSweep - healthySweep - overSweep).coerceAtLeast(0.1f)

                    // Draw Segments with named parameters
                    drawArc(
                        color = BmiUnderweight.copy(0.15f),
                        startAngle = startAngle,
                        sweepAngle = underSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = BmiHealthy.copy(0.15f),
                        startAngle = startAngle + underSweep,
                        sweepAngle = healthySweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        color = BmiOverweight.copy(0.15f),
                        startAngle = startAngle + underSweep + healthySweep,
                        sweepAngle = overSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        color = BmiObese.copy(0.15f),
                        startAngle = startAngle + underSweep + healthySweep + overSweep,
                        sweepAngle = obeseSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Active indicator
                    if (animatedBmi > 0f) {
                        val activeSweep = ((animatedBmi - minBmi) / totalRange).coerceIn(0f, 1f) * sweepTotal
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        
                        val angle = startAngle + activeSweep
                        val radius = (size.width / 2f) - (strokeWidth / 2f)
                        val x = center.x + radius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                        val y = center.y + radius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                        
                        drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = color, radius = 7.dp.toPx(), center = Offset(x, y))
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = if (bmi == null) "—" else "%.1f".format(animatedBmi),
                        style     = MaterialTheme.typography.displayMedium.copy(
                            fontWeight    = FontWeight.Black,
                            fontSize      = 62.sp,
                            letterSpacing = (-2).sp,
                        ),
                        color     = if (bmi == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f) else color,
                    )
                    Text(
                        "BMI SCORE",
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = color.copy(alpha = 0.7f),
                        letterSpacing = 2.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val categoryColor by animateColorAsState(color, label = "cat_color")
            AnimatedContent(
                targetState  = category.ifEmpty { "ENTER DATA" },
                transitionSpec = {
                    (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
                },
                label = "category",
            ) { label ->
                Surface(
                    color  = categoryColor,
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

    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick { },
        shape    = RoundedCornerShape(24.dp),
        color    = color.copy(alpha = 0.1f),
        border   = BorderStroke(1.5.dp, color.copy(alpha = 0.2f)),
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
                        atIdeal  -> "IDEAL WEIGHT REACHED"
                        needGain -> "GAIN %.1f %s FOR IDEAL".format(abs(difference), unit)
                        else     -> "LOSE %.1f %s FOR IDEAL".format(abs(difference), unit)
                    },
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color      = color,
                )
                Text(
                    "Target: Devine IBW Formula",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Input panel — Fixed Alignment
// ─────────────────────────────────────────────────────────────

@Composable
private fun InputPanel(state: BmiState, viewModel: BmiViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(30.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    InputHeaderContainer("AGE")
                    Spacer(Modifier.height(10.dp))
                    BmiTextField(
                        value         = state.age,
                        onValueChange = viewModel::onAgeChange,
                        icon          = Icons.Rounded.Cake,
                        placeholder   = "YRS",
                        keyboardType  = KeyboardType.Number
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    InputHeaderContainer(
                        label = "WEIGHT",
                        toggle = if (state.isKg) "KG" else "LB",
                        onToggle = { viewModel.toggleUnit(false) }
                    )
                    Spacer(Modifier.height(10.dp))
                    BmiTextField(
                        value         = state.weight,
                        onValueChange = viewModel::onWeightChange,
                        icon          = Icons.Rounded.MonitorWeight,
                        placeholder   = "0"
                    )
                }
            }

            Column {
                InputHeaderContainer(
                    label = "HEIGHT",
                    toggle = if (state.isCm) "CM" else "FT/IN",
                    onToggle = { viewModel.toggleUnit(true) }
                )
                Spacer(Modifier.height(10.dp))
                BmiTextField(
                    value         = state.height,
                    onValueChange = viewModel::onHeightChange,
                    icon          = Icons.Rounded.Height,
                    placeholder   = if (state.isCm) "170" else "5' 11\"",
                    keyboardType  = KeyboardType.Decimal
                )
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
            style         = MaterialTheme.typography.labelMedium,
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
                @Suppress("DEPRECATION")
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
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape         = RoundedCornerShape(16.dp),
        leadingIcon   = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor     = Color.Transparent,
            focusedBorderColor       = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedContainerColor  = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor    = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    )
}

// ─────────────────────────────────────────────────────────────
//  Activity level panel
// ─────────────────────────────────────────────────────────────

@Composable
private fun ActivityPanel(
    selected: ActivityLevel,
    onSelect: (ActivityLevel) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(30.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    "ACTIVITY LEVEL",
                    style         = MaterialTheme.typography.labelMedium,
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                @Suppress("DEPRECATION")
                Text(
                    "× %.3f".format(selected.multiplier),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityLevel.entries.forEach { level ->
                    val isSelected = level == selected
                    val itemScale  by animateFloatAsState(
                        if (isSelected) 1.05f else 1f,
                        spring(Spring.DampingRatioMediumBouncy),
                        label = "act_scale",
                    )
                    Surface(
                        onClick  = { onSelect(level) },
                        modifier = Modifier.weight(1f).scale(itemScale).bouncyClick { },
                        shape    = RoundedCornerShape(14.dp),
                        color    = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border   = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.5f))
                        else null,
                    ) {
                        Column(
                            modifier            = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            @Suppress("DEPRECATION")
                            Text(
                                level.shortLabel,
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                fontSize   = 10.sp,
                                color      = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines   = 1,
                                overflow   = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            @Suppress("DEPRECATION")
            Text(
                selected.label.uppercase(),
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth(),
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Advanced metrics grid
// ─────────────────────────────────────────────────────────────

@Composable
fun AdvancedMetrics(state: BmiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("HEALTH INDICATORS")

        val gridModifier = Modifier.fillMaxWidth()
        val spacing = 12.dp

        Row(gridModifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricCard(
                title    = "BMR",
                value    = state.bmr?.let { "%.0f".format(it) } ?: "--",
                unit     = "KCAL",
                subtitle = "BASAL METABOLISM",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FlashOn
            )
            MetricCard(
                title    = "TDEE",
                value    = state.tdee?.let { "%.0f".format(it) } ?: "--",
                unit     = "KCAL",
                subtitle = "DAILY EXPENDITURE",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.LocalFireDepartment
            )
        }

        Row(gridModifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricCard(
                title    = "BODY FAT",
                value    = state.bfp?.let { "%.1f".format(it) } ?: "--",
                unit     = "%",
                subtitle = "ESTIMATED",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.PieChart
            )
            MetricCard(
                title    = "IDEAL WT",
                value    = state.ibw?.let { "%.1f".format(it) } ?: "--",
                unit     = if (state.isKg) "KG" else "LB",
                subtitle = "DEVINE TARGET",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Flag
            )
        }

        Row(gridModifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricCard(
                title    = "LEAN MASS",
                value    = state.lbm?.let { "%.1f".format(it) } ?: "--",
                unit     = if (state.isKg) "KG" else "LB",
                subtitle = "MUSCLE & BONE",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FitnessCenter
            )
            MetricCard(
                title    = "SURFACE",
                value    = state.bsa?.let { "%.2f".format(it) } ?: "--",
                unit     = "M²",
                subtitle = "BODY AREA",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Layers
            )
        }
        
        Row(gridModifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricCard(
                title    = "NEW BMI",
                value    = state.oxfordBmi?.let { "%.1f".format(it) } ?: "--",
                unit     = "PTS",
                subtitle = "OXFORD FORMULA",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Analytics
            )
            MetricCard(
                title    = "CORPULENCE",
                value    = state.ponderalIndex?.let { "%.1f".format(it) } ?: "--",
                unit     = "KG/M³",
                subtitle = "PONDERAL INDEX",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Compress
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
) {
    Surface(
        modifier = modifier.bouncyClick { },
        shape    = RoundedCornerShape(24.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Suppress("DEPRECATION")
                Text(
                    title,
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp,
                )
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier          = Modifier.padding(vertical = 4.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(4.dp))
                @Suppress("DEPRECATION")
                Text(
                    unit,
                    style      = MaterialTheme.typography.labelSmall,
                    modifier   = Modifier.padding(bottom = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
            }
            @Suppress("DEPRECATION")
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

// ─────────────────────────────────────────────────────────────
//  Nutrition & Hydration
// ─────────────────────────────────────────────────────────────

@Composable
fun NutritionSection(state: BmiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("NUTRITION & HYDRATION")
        
        // Water Intake
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            color    = Color(0xFFE3F2FD),
            border   = BorderStroke(1.dp, Color(0xFF90CAF9))
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
                    @Suppress("DEPRECATION")
                    Text(
                        "WATER INTAKE",
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            color    = MaterialTheme.colorScheme.surfaceContainerLow,
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                @Suppress("DEPRECATION")
                Text(
                    "DAILY MACRONUTRIENTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroItem("PROTEIN", state.protein ?: 0f, Color(0xFFEF5350), Modifier.weight(1f))
                    MacroItem("CARBS", state.carbs ?: 0f, Color(0xFF66BB6A), Modifier.weight(1f))
                    MacroItem("FATS", state.fats ?: 0f, Color(0xFFFFA726), Modifier.weight(1f))
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
        @Suppress("DEPRECATION")
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        Text("%.0fg".format(grams), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

// ─────────────────────────────────────────────────────────────
//  BMI classification list
// ─────────────────────────────────────────────────────────────

@Composable
fun BmiInfoSection(bmi: Float, healthyRange: Pair<Float, Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("WHO CLASSIFICATION")
        BmiCategoryItem("UNDERWEIGHT", "< %.1f".format(healthyRange.first),       BmiUnderweight, bmi < healthyRange.first)
        BmiCategoryItem("NORMAL",      "%.1f – %.1f".format(healthyRange.first, healthyRange.second), BmiHealthy, bmi in healthyRange.first..healthyRange.second)
        BmiCategoryItem("OVERWEIGHT",  "%.1f – 29.9".format(healthyRange.second + 0.1f), BmiOverweight, bmi > healthyRange.second && bmi < 30f)
        BmiCategoryItem("OBESITY",     "≥ 30.0",                                  BmiObese,       bmi >= 30f)
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
    Surface(
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape    = RoundedCornerShape(20.dp),
        color    = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border   = if (isSelected) BorderStroke(2.dp, color.copy(alpha = 0.4f))
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
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
                style      = MaterialTheme.typography.labelMedium,
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
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color      = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp,
        )
    }
}