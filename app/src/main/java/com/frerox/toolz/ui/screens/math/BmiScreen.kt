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
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            ) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("Health Index", fontWeight = FontWeight.Black) },
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Main Result Display
                ResultCard(bmi = state.bmi, category = state.category, range = state.healthyRange)

                // Gender Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Age & Weight Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BmiInput(
                                label = "Age",
                                value = state.age,
                                onValueChange = { viewModel.onAgeChange(it) },
                                icon = Icons.Rounded.Cake,
                                placeholder = "Years",
                                modifier = Modifier.weight(1f)
                            )
                            
                            Column(modifier = Modifier.weight(1.2f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Weight", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        if (state.isKg) "KG" else "LB",
                                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = state.weight,
                                    onValueChange = { viewModel.onWeightChange(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(16.dp),
                                    leadingIcon = { Icon(Icons.Rounded.MonitorWeight, null, tint = MaterialTheme.colorScheme.primary) }
                                )
                            }
                        }

                        // Height Input
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Height", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (state.isCm) "CM" else "FT/IN",
                                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.height,
                                onValueChange = { viewModel.onHeightChange(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(if (state.isCm) "0" else "0' 0\"") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(16.dp),
                                leadingIcon = { Icon(Icons.Rounded.Height, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        }
                        
                        Button(
                            onClick = { viewModel.toggleUnit(true); viewModel.toggleUnit(false) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Sync, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Switch Units")
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
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        )
    }
}

@Composable
fun AdvancedMetrics(state: BmiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Detailed Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "BMR",
                value = String.format("%.0f", state.bmr),
                unit = "kcal/day",
                subtitle = "Basal Metabolism",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "TDEE",
                value = String.format("%.0f", state.tdee),
                unit = "kcal/day",
                subtitle = "Maintenance",
                modifier = Modifier.weight(1f)
            )
        }
        
        MetricCard(
            title = "Ideal Weight",
            value = String.format("%.1f", state.ibw),
            unit = if (state.isKg) "kg" else "lb",
            subtitle = "Based on Devine Formula",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MetricCard(title: String, value: String, unit: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val containerColor = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val icon = if (gender == Gender.MALE) Icons.Rounded.Male else Icons.Rounded.Female

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = gender.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(32.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (animatedBmi / 40f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 12.dp,
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
                    Text("BMI", style = MaterialTheme.typography.labelLarge, color = color.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                color = color,
                shape = CircleShape
            ) {
                Text(
                    text = category.ifEmpty { "Pending Input" },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun BmiInfoSection(bmi: Float, healthyRange: Pair<Float, Float>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Understanding your BMI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        
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
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
            }
            Text(range, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}
