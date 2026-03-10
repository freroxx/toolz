package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmiScreen(
    viewModel: BmiViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("HEALTH INDEX", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .fadingEdge(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.05f to Color.Black,
                    0.95f to Color.Black,
                    1f to Color.Transparent
                ),
                length = 24.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Main Result Display
                ResultCard(bmi = state.bmi, category = state.category, range = state.healthyRange)

                // Gender Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GenderCard(
                        gender = Gender.MALE,
                        isSelected = state.gender == Gender.MALE,
                        onClick = { viewModel.onGenderChange(Gender.MALE) },
                        modifier = Modifier.weight(1f)
                    )
                    GenderCard(
                        gender = Gender.FEMALE,
                        isSelected = state.gender == Gender.FEMALE,
                        onClick = { viewModel.onGenderChange(Gender.FEMALE) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Input Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        // Age & Weight Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            BmiInput(
                                label = "AGE",
                                value = state.age,
                                onValueChange = { viewModel.onAgeChange(it) },
                                icon = Icons.Rounded.Cake,
                                placeholder = "Yrs",
                                modifier = Modifier.weight(1f)
                            )
                            
                            Column(modifier = Modifier.weight(1.3f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("WEIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    Spacer(Modifier.weight(1f))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            if (state.isKg) "KG" else "LB",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = state.weight,
                                    onValueChange = { viewModel.onWeightChange(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(20.dp),
                                    leadingIcon = { Icon(Icons.Rounded.MonitorWeight, null, tint = MaterialTheme.colorScheme.primary) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }

                        // Height Input
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("HEIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        if (state.isCm) "CM" else "FT/IN",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = state.height,
                                onValueChange = { viewModel.onHeightChange(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(if (state.isCm) "0" else "0' 0\"") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(20.dp),
                                leadingIcon = { Icon(Icons.Rounded.Height, null, tint = MaterialTheme.colorScheme.primary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                )
                            )
                        }
                        
                        FilledTonalButton(
                            onClick = { viewModel.toggleUnit(true); viewModel.toggleUnit(false) },
                            modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick {},
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("SWITCH UNITS", fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Advanced Metrics Section
                if (state.bmi != null) {
                    AdvancedMetrics(state)
                }

                // Classification Info
                if (state.bmi != null) {
                    BmiInfoSection(bmi = state.bmi!!, healthyRange = state.healthyRange)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(20.dp),
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun AdvancedMetrics(state: BmiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("DETAILED METRICS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard(
                title = "BMR",
                value = String.format("%.0f", state.bmr),
                unit = "kcal/d",
                subtitle = "Metabolism",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "TDEE",
                value = String.format("%.0f", state.tdee),
                unit = "kcal/d",
                subtitle = "Daily Load",
                modifier = Modifier.weight(1f)
            )
        }
        
        MetricCard(
            title = "Ideal Weight",
            value = String.format("%.1f", state.ibw),
            unit = if (state.isKg) "kg" else "lb",
            subtitle = "Optimal Weight Range",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MetricCard(title: String, value: String, unit: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp), fontWeight = FontWeight.Bold)
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GenderCard(
    gender: Gender,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (gender == Gender.MALE) Color(0xFF2196F3) else Color(0xFFE91E63)
    val containerColor = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val icon = if (gender == Gender.MALE) Icons.Rounded.Male else Icons.Rounded.Female

    Surface(
        onClick = onClick,
        modifier = modifier.bouncyClick {},
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = gender.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ResultCard(bmi: Float?, category: String, range: Pair<Float, Float>) {
    val animatedBmi by animateFloatAsState(
        targetValue = bmi ?: 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "BmiAnim"
    )

    val color = when {
        animatedBmi < range.first -> Color(0xFF4FC3F7)
        animatedBmi <= range.second -> Color(0xFF66BB6A)
        animatedBmi < 30f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(44.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.25f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (animatedBmi / 40f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(150.dp),
                    strokeWidth = 14.dp,
                    color = color,
                    trackColor = color.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (bmi == null) "--" else String.format("%.1f", animatedBmi),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                    Text("BMI INDEX", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                color = color,
                shape = CircleShape
            ) {
                Text(
                    text = category.ifEmpty { "PENDING DATA" }.uppercase(),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun BmiInfoSection(bmi: Float, healthyRange: Pair<Float, Float>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("CLASSIFICATION INFO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        
        BmiCategoryItem("Underweight", "< ${healthyRange.first}", Color(0xFF4FC3F7), bmi < healthyRange.first)
        BmiCategoryItem("Normal Weight", "${healthyRange.first} - ${healthyRange.second}", Color(0xFF66BB6A), bmi in healthyRange.first..healthyRange.second)
        BmiCategoryItem("Overweight", "${healthyRange.second + 0.1f} - 29.9", Color(0xFFFFA726), bmi > healthyRange.second && bmi < 30f)
        BmiCategoryItem("Obesity", "> 30", Color(0xFFEF5350), bmi >= 30f)
    }
}

@Composable
fun BmiCategoryItem(label: String, range: String, color: Color, isSelected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, color = if (isSelected) color else MaterialTheme.colorScheme.onSurface)
            }
            Text(range, style = MaterialTheme.typography.labelMedium, color = if (isSelected) color.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Black)
        }
    }
}
