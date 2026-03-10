package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge

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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { 
                        Text("UNIT CONVERTER", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category Selector
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(36.dp),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false
                ) {
                    items(ConversionType.entries) { type ->
                        val isSelected = state.type == type
                        val color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .bouncyClick { viewModel.onTypeChange(type) },
                            shape = RoundedCornerShape(22.dp),
                            color = color,
                            contentColor = contentColor,
                            tonalElevation = if (isSelected) 6.dp else 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = getIconForType(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Conversion UI
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(44.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(28.dp)) {
                    UnitSection(
                        isInput = true,
                        label = "INPUT VALUE",
                        value = state.inputValue,
                        unit = state.fromUnit,
                        units = units,
                        onValueChange = { viewModel.onInputValueChange(it) },
                        onUnitChange = { viewModel.onFromUnitChange(it) }
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        HorizontalDivider(modifier = Modifier.alpha(0.15f))
                        Surface(
                            modifier = Modifier
                                .size(68.dp)
                                .shadow(16.dp, CircleShape)
                                .bouncyClick { viewModel.swapUnits() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 10.dp,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Swap",
                                modifier = Modifier.padding(18.dp).size(34.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    UnitSection(
                        isInput = false,
                        label = "RESULT VALUE",
                        value = state.outputValue,
                        unit = state.toUnit,
                        units = units,
                        onUnitChange = { viewModel.onToUnitChange(it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Result Summary Card
            AnimatedVisibility(
                visible = state.inputValue.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CONVERSION SUMMARY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "${state.inputValue} ${state.fromUnit} = ${state.outputValue} ${state.toUnit}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Text(
                        text = "Standard ISO conversion factors are utilized for professional-grade accuracy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun UnitSection(
    isInput: Boolean,
    label: String,
    value: String,
    unit: String,
    units: List<String>,
    onValueChange: (String) -> Unit = {},
    onUnitChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.primary, 
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isInput) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            } else {
                Surface(
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
                .height(64.dp)
                .width(110.dp)
                .bouncyClick { expanded = true },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    selectedUnit, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.heightIn(max = 350.dp)
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit, fontWeight = FontWeight.Black) },
                    onClick = {
                        onUnitChange(unit)
                        expanded = false
                    },
                    trailingIcon = if (unit == selectedUnit) {
                        { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}

private fun getIconForType(type: ConversionType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        ConversionType.LENGTH -> Icons.Rounded.Straighten
        ConversionType.WEIGHT -> Icons.Rounded.MonitorWeight
        ConversionType.TEMPERATURE -> Icons.Rounded.Thermostat
        ConversionType.VOLUME -> Icons.Rounded.Opacity
        ConversionType.AREA -> Icons.Rounded.Layers
        ConversionType.SPEED -> Icons.Rounded.Speed
        ConversionType.TIME -> Icons.Rounded.Schedule
        ConversionType.DIGITAL_STORAGE -> Icons.Rounded.SdCard
        ConversionType.ENERGY -> Icons.Rounded.Bolt
    }
}
