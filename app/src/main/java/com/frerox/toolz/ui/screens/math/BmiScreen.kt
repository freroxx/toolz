package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "BMI & BODY INDEX",
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
                    // Gender toggle
                    Surface(
                        onClick   = {
                            viewModel.onGenderChange(
                                if (state.gender == Gender.MALE) Gender.FEMALE else Gender.MALE
                            )
                        },
                        modifier  = Modifier.padding(end = 8.dp),
                        shape     = RoundedCornerShape(14.dp),
                        color     = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                if (state.gender == Gender.MALE) Icons.Rounded.Male
                                else Icons.Rounded.Female,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                if (state.gender == Gender.MALE) "MALE" else "FEMALE",
                                style         = MaterialTheme.typography.labelSmall,
                                fontWeight    = FontWeight.Black,
                                color         = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors   = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        containerColor    = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .fadingEdge(
                    brush  = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                    length = 20.dp,
                )
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
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
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

// ─────────────────────────────────────────────────────────────
//  Result card — animated BMI ring
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
    val color = bmiColor(animatedBmi.takeIf { it > 0f }, range)

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
                modifier         = Modifier.size(190.dp),
            ) {
                // Background track
                CircularProgressIndicator(
                    progress    = { 1f },
                    modifier    = Modifier.fillMaxSize(),
                    strokeWidth = 16.dp,
                    color       = color.copy(alpha = 0.08f),
                    strokeCap   = StrokeCap.Round,
                )
                // Progress arc (BMI / 40 = full scale)
                CircularProgressIndicator(
                    progress    = { (animatedBmi / 40f).coerceIn(0f, 1f) },
                    modifier    = Modifier.fillMaxSize(),
                    strokeWidth = 16.dp,
                    color       = color,
                    trackColor  = Color.Transparent,
                    strokeCap   = StrokeCap.Round,
                )
                // Inner content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = if (bmi == null) "—" else "%.1f".format(animatedBmi),
                        style     = MaterialTheme.typography.displayMedium.copy(
                            fontWeight    = FontWeight.Black,
                            fontSize      = 54.sp,
                            letterSpacing = (-2).sp,
                        ),
                        color     = color,
                    )
                    Text(
                        "BMI",
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = color.copy(alpha = 0.7f),
                        letterSpacing = 3.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Category label
            AnimatedContent(
                targetState  = category.ifEmpty { "ENTER VALUES" },
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "category",
            ) { label ->
                Surface(
                    color  = color,
                    shape  = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text      = label.uppercase(),
                        modifier  = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                        style     = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color     = Color.White,
                        letterSpacing = 1.sp,
                    )
                }
            }

            // BMI scale bar
            Spacer(Modifier.height(20.dp))
            BmiScaleBar(animatedBmi)
        }
    }
}

@Composable
private fun BmiScaleBar(bmi: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Colour segments
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            listOf(BmiUnderweight, BmiHealthy, BmiOverweight, BmiObese).forEach { c ->
                Box(Modifier.weight(1f).fillMaxHeight().background(c.copy(0.6f)))
            }
        }
        // Marker
        val fraction = ((bmi - 10f) / 30f).coerceIn(0f, 1f)  // scale 10–40
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .offset(x = (maxWidth * fraction) - 5.dp)
                    .size(10.dp, 10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Thin", "Healthy", "Over", "Obese").forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
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
    val needGain = difference < -1f        // current weight < ideal = need to gain
    val color = when {
        atIdeal   -> BmiHealthy
        needGain  -> BmiUnderweight
        else      -> BmiOverweight
    }

    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick { },
        shape    = RoundedCornerShape(20.dp),
        color    = color.copy(alpha = 0.08f),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier         = Modifier.size(44.dp).clip(CircleShape).background(color.copy(0.15f)),
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
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    when {
                        atIdeal  -> "AT IDEAL WEIGHT"
                        needGain -> "GAIN %.1f %s TO REACH IDEAL".format(abs(difference), unit)
                        else     -> "LOSE %.1f %s TO REACH IDEAL".format(abs(difference), unit)
                    },
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color      = color,
                )
                Text(
                    "Devine Formula (IBW)",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Input panel
// ─────────────────────────────────────────────────────────────

@Composable
private fun InputPanel(state: BmiState, viewModel: BmiViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(28.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.2f)),
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Age + Weight row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BmiInput(
                    label       = "AGE",
                    value       = state.age,
                    onValueChange = viewModel::onAgeChange,
                    icon        = Icons.Rounded.Cake,
                    placeholder = "YRS",
                    keyboardType = KeyboardType.Number,
                    modifier    = Modifier.weight(1f),
                )

                Column(modifier = Modifier.weight(1.3f)) {
                    InputHeader(
                        label = "WEIGHT",
                        toggle = if (state.isKg) "KG" else "LB",
                        onToggle = { viewModel.toggleUnit(false) },
                    )
                    Spacer(Modifier.height(8.dp))
                    BmiTextField(
                        value         = state.weight,
                        onValueChange = viewModel::onWeightChange,
                        icon          = Icons.Rounded.MonitorWeight,
                        placeholder   = "0",
                    )
                }
            }

            // Height
            Column {
                InputHeader(
                    label    = "HEIGHT",
                    toggle   = if (state.isCm) "CM" else "FT/IN",
                    onToggle = { viewModel.toggleUnit(true) },
                )
                Spacer(Modifier.height(8.dp))
                BmiTextField(
                    value         = state.height,
                    onValueChange = viewModel::onHeightChange,
                    icon          = Icons.Rounded.Height,
                    placeholder   = if (state.isCm) "170" else "5' 11\"",
                    keyboardType  = KeyboardType.Decimal,
                )
            }
        }
    }
}

@Composable
private fun InputHeader(label: String, toggle: String, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style         = MaterialTheme.typography.labelSmall,
            fontWeight    = FontWeight.Black,
            color         = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Surface(
            onClick  = onToggle,
            color    = MaterialTheme.colorScheme.primaryContainer,
            shape    = RoundedCornerShape(8.dp),
        ) {
            Text(
                toggle,
                modifier   = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun BmiInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style         = MaterialTheme.typography.labelSmall,
            fontWeight    = FontWeight.Black,
            color         = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        BmiTextField(value, onValueChange, icon, placeholder, keyboardType)
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
        placeholder   = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape         = RoundedCornerShape(14.dp),
        leadingIcon   = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor     = Color.Transparent,
            focusedBorderColor       = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedContainerColor  = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor    = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        singleLine = true,
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
        shape    = RoundedCornerShape(28.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.2f)),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    "ACTIVITY LEVEL",
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "× %.3f".format(selected.multiplier),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityLevel.entries.forEach { level ->
                    val isSelected = level == selected
                    val itemScale  by animateFloatAsState(
                        if (isSelected) 1.04f else 1f,
                        spring(Spring.DampingRatioMediumBouncy),
                        label = "act_scale",
                    )
                    Surface(
                        onClick  = { onSelect(level) },
                        modifier = Modifier.weight(1f).scale(itemScale).bouncyClick { },
                        shape    = RoundedCornerShape(12.dp),
                        color    = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border   = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.4f))
                        else null,
                    ) {
                        Column(
                            modifier            = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                level.shortLabel,
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                fontSize   = 9.sp,
                                color      = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines   = 1,
                                overflow   = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
            // Selected label
            Spacer(Modifier.height(6.dp))
            Text(
                selected.label,
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Advanced metrics grid
// ─────────────────────────────────────────────────────────────

@Composable
fun AdvancedMetrics(state: BmiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("BODY COMPOSITION")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title    = "BMR",
                value    = state.bmr?.let { "%.0f".format(it) } ?: "--",
                unit     = "KCAL/DAY",
                subtitle = "AT REST",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title    = "TDEE",
                value    = state.tdee?.let { "%.0f".format(it) } ?: "--",
                unit     = "KCAL/DAY",
                subtitle = "${state.activity.label.uppercase()}",
                modifier = Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title    = "BODY FAT",
                value    = state.bfp?.let { "%.1f".format(it) } ?: "--",
                unit     = "%",
                subtitle = "DEURENBERG",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title    = "IDEAL WT",
                value    = state.ibw?.let { "%.1f".format(it) } ?: "--",
                unit     = if (state.isKg) "KG" else "LB",
                subtitle = "DEVINE FORMULA",
                modifier = Modifier.weight(1f),
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.bouncyClick { },
        shape    = RoundedCornerShape(22.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Black,
                color         = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier          = Modifier.padding(vertical = 4.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    style      = MaterialTheme.typography.labelSmall,
                    modifier   = Modifier.padding(bottom = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                subtitle,
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  BMI classification list
// ─────────────────────────────────────────────────────────────

@Composable
fun BmiInfoSection(bmi: Float, healthyRange: Pair<Float, Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("BMI CLASSIFICATION")
        BmiCategoryItem("UNDERWEIGHT", "< %.1f".format(healthyRange.first),       BmiUnderweight, bmi < healthyRange.first)
        BmiCategoryItem("HEALTHY",     "%.1f – %.1f".format(healthyRange.first, healthyRange.second), BmiHealthy, bmi in healthyRange.first..healthyRange.second)
        BmiCategoryItem("OVERWEIGHT",  "%.1f – 29.9".format(healthyRange.second + 0.1f), BmiOverweight, bmi > healthyRange.second && bmi < 30f)
        BmiCategoryItem("OBESE",       "≥ 30.0",                                  BmiObese,       bmi >= 30f)
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
        shape    = RoundedCornerShape(18.dp),
        color    = if (isSelected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border   = if (isSelected) BorderStroke(1.5.dp, color.copy(alpha = 0.3f))
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.15f)),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(if (isSelected) 12.dp else 10.dp)
                        .clip(CircleShape)
                        .background(color.copy(if (isSelected) 1f else 0.5f))
                )
                Text(
                    label,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    color      = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                range,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                color      = if (isSelected) color.copy(0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
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
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color  = MaterialTheme.colorScheme.primaryContainer.copy(0.5f),
            shape  = RoundedCornerShape(6.dp),
        ) {
            Text(
                text,
                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                letterSpacing = 1.sp,
            )
        }
    }
}