package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmiScreen(
    viewModel: BmiViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var isCm by remember { mutableStateOf(true) }
    var isKg by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BMI Calculator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Card
            ResultCard(bmi = state.bmi, category = state.category)

            // Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Height Input
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Height", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = isCm,
                                    onClick = { isCm = true },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) { Text("cm") }
                                SegmentedButton(
                                    selected = !isCm,
                                    onClick = { isCm = false },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) { Text("ft/in") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.height,
                            onValueChange = { viewModel.onHeightChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (isCm) "0" else "0' 0\"") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = MaterialTheme.shapes.large,
                            leadingIcon = { Icon(Icons.Rounded.Height, null) },
                            trailingIcon = { if (isCm) Text("cm", modifier = Modifier.padding(end = 12.dp)) }
                        )
                    }

                    // Weight Input
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Weight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = isKg,
                                    onClick = { isKg = true },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) { Text("kg") }
                                SegmentedButton(
                                    selected = !isKg,
                                    onClick = { isKg = false },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) { Text("lb") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.weight,
                            onValueChange = { viewModel.onWeightChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = MaterialTheme.shapes.large,
                            leadingIcon = { Icon(Icons.Rounded.MonitorWeight, null) },
                            trailingIcon = { if (isKg) Text("kg", modifier = Modifier.padding(end = 12.dp)) }
                        )
                    }
                }
            }

            // Info Section
            if (state.bmi != null) {
                BmiInfoSection(bmi = state.bmi!!)
            }
        }
    }
}

@Composable
fun ResultCard(bmi: Float?, category: String) {
    val animatedBmi by animateFloatAsState(
        targetValue = bmi ?: 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "BmiAnim"
    )

    val color = when {
        animatedBmi < 18.5f -> Color(0xFF4FC3F7) // Underweight - Blue
        animatedBmi < 25f -> Color(0xFF66BB6A)   // Normal - Green
        animatedBmi < 30f -> Color(0xFFFFA726)   // Overweight - Orange
        else -> Color(0xFFEF5350)              // Obese - Red
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (bmi == null) "--" else String.format("%.1f", animatedBmi),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = category.ifEmpty { "Enter your details" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun BmiInfoSection(bmi: Float) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Classification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        BmiCategoryItem("Underweight", "< 18.5", Color(0xFF4FC3F7), bmi < 18.5f)
        BmiCategoryItem("Normal weight", "18.5 - 24.9", Color(0xFF66BB6A), bmi in 18.5f..24.9f)
        BmiCategoryItem("Overweight", "25 - 29.9", Color(0xFFFFA726), bmi in 25f..29.9f)
        BmiCategoryItem("Obese", "> 30", Color(0xFFEF5350), bmi >= 30f)
    }
}

@Composable
fun BmiCategoryItem(label: String, range: String, color: Color, isSelected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) PaddingValues(0.dp).let { null } else null // simplified
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(12.dp))
                Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
            }
            Text(range, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}
