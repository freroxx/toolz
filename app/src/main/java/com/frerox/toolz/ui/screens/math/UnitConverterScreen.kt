package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(
    viewModel: UnitConverterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val units = state.availableUnits

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unit Converter", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category Selector Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ConversionType.entries) { type ->
                    val isSelected = state.type == type
                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .bouncyClick { viewModel.onTypeChange(type) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Conversion UI
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    UnitInputSection(
                        label = "FROM",
                        value = state.inputValue,
                        unit = state.fromUnit,
                        units = units,
                        onValueChange = { viewModel.onInputValueChange(it) },
                        onUnitChange = { viewModel.onFromUnitChange(it) }
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .offset(y = 0.dp)
                                .bouncyClick { viewModel.swapUnits() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 4.dp
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Swap",
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    UnitOutputSection(
                        label = "TO",
                        value = state.outputValue,
                        unit = state.toUnit,
                        units = units,
                        onUnitChange = { viewModel.onToUnitChange(it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Quick Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = "Conversions are calculated based on standard ISO units.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun UnitInputSection(
    label: String,
    value: String,
    unit: String,
    units: List<String>,
    onValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
            UnitDropDownSelector(unit, units, onUnitChange)
        }
    }
}

@Composable
fun UnitOutputSection(
    label: String,
    value: String,
    unit: String,
    units: List<String>,
    onUnitChange: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            UnitDropDownSelector(unit, units, onUnitChange)
        }
    }
}

@Composable
fun UnitDropDownSelector(
    selectedUnit: String,
    units: List<String>,
    onUnitChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable { expanded = true }
                .background(MaterialTheme.colorScheme.secondaryContainer),
            color = androidx.compose.ui.graphics.Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedUnit, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onUnitChange(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}
