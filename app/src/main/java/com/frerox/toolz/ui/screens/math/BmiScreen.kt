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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "HEALTH INDEX PRO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onGenderChange(if (state.gender == Gender.MALE) Gender.FEMALE else Gender.MALE) },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            if (state.gender == Gender.MALE) Icons.Rounded.Male else Icons.Rounded.Female, 
                            contentDescription = "Toggle Gender",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge(
                        brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                        length = 24.dp
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Main Result Display
                ResultCard(bmi = state.bmi, category = state.category, range = state.healthyRange)

                // Input Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Age & Weight Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BmiInput(
                                label = "AGE",
                                value = state.age,
                                onValueChange = { viewModel.onAgeChange(it) },
                                icon = Icons.Rounded.Cake,
                                placeholder = "YRS",
                                modifier = Modifier.weight(1f)
                            )
                            
                            Column(modifier = Modifier.weight(1.2f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("WEIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    Spacer(Modifier.weight(1f))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = { viewModel.toggleUnit(false) }
                                    ) {
                                        Text(
                                            if (state.isKg) "KG" else "LB",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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
                                    shape = RoundedCornerShape(16.dp),
                                    leadingIcon = { Icon(Icons.Rounded.MonitorWeight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surface
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
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = { viewModel.toggleUnit(true) }
                                ) {
                                    Text(
                                        if (state.isCm) "CM" else "FT/IN",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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
                                shape = RoundedCornerShape(16.dp),
                                leadingIcon = { Icon(Icons.Rounded.Height, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
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
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
fun AdvancedMetrics(state: BmiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "ADVANCED BODY COMPOSITION", 
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.primary, 
                letterSpacing = 1.5.sp
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard(
                title = "BMR",
                value = String.format("%.0f", state.bmr),
                unit = "KCAL/D",
                subtitle = "BASAL METABOLISM",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "TDEE",
                value = String.format("%.0f", state.tdee),
                unit = "KCAL/D",
                subtitle = "DAILY BURN",
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard(
                title = "BODY FAT",
                value = if (state.bfp != null) String.format("%.1f", state.bfp) else "--",
                unit = "%",
                subtitle = "ESTIMATED FAT",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "IDEAL WT",
                value = String.format("%.1f", state.ibw),
                unit = if (state.isKg) "KG" else "LB",
                subtitle = "OPTIMAL RANGE",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, unit: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
        animatedBmi <= 0f -> MaterialTheme.colorScheme.outline
        animatedBmi < range.first -> Color(0xFF4FC3F7)
        animatedBmi <= range.second -> Color(0xFF66BB6A)
        animatedBmi < 30f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(48.dp),
        color = color.copy(alpha = 0.05f),
        border = BorderStroke(2.dp, color.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 20.dp,
                    color = color.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
                CircularProgressIndicator(
                    progress = { (animatedBmi / 40f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 20.dp,
                    color = color,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (bmi == null) "--" else String.format("%.1f", animatedBmi),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 56.sp,
                            letterSpacing = (-2).sp
                        ),
                        color = color
                    )
                    Text("BMI INDEX", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                color = color,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = category.ifEmpty { "CALCULATING..." }.uppercase(),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "CLASSIFICATION INDEX", 
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.primary, 
                letterSpacing = 1.5.sp
            )
        }
        
        BmiCategoryItem("UNDERWEIGHT", "< ${healthyRange.first}", Color(0xFF4FC3F7), bmi < healthyRange.first)
        BmiCategoryItem("HEALTHY", "${healthyRange.first} - ${healthyRange.second}", Color(0xFF66BB6A), bmi in healthyRange.first..healthyRange.second)
        BmiCategoryItem("OVERWEIGHT", "${healthyRange.second + 0.1f} - 29.9", Color(0xFFFFA726), bmi > healthyRange.second && bmi < 30f)
        BmiCategoryItem("OBESE", "> 30", Color(0xFFEF5350), bmi >= 30f)
    }
}

@Composable
fun BmiCategoryItem(label: String, range: String, color: Color, isSelected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = if (isSelected) BorderStroke(1.5.dp, color.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = if (isSelected) color else MaterialTheme.colorScheme.onSurface)
            }
            Text(range, style = MaterialTheme.typography.labelSmall, color = if (isSelected) color.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        }
    }
}
