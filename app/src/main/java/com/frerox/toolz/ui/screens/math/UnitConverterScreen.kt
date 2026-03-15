package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import kotlinx.coroutines.delay

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
            CenterAlignedTopAppBar(
                title = { 
                    Text("UNIT CONVERTER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fadingEdge(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.02f to Color.Black,
                        0.98f to Color.Black,
                        1f to Color.Transparent
                    ),
                    length = 24.dp
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category Selector
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(ConversionType.entries) { index, type ->
                        val isSelected = state.type == type
                        var itemVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 20L)
                            itemVisible = true
                        }
                        
                        val scale by animateFloatAsState(if (itemVisible) 1f else 0.8f)
                        val alpha by animateFloatAsState(if (itemVisible) 1f else 0f)

                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .bouncyClick { viewModel.onTypeChange(type) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = getIconForType(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = type.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
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
                shape = RoundedCornerShape(40.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    UnitSection(
                        isInput = true,
                        label = "FROM UNIT",
                        value = state.inputValue,
                        unit = state.fromUnit,
                        units = units,
                        onValueChange = { viewModel.onInputValueChange(it) },
                        onUnitChange = { viewModel.onFromUnitChange(it) }
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        HorizontalDivider(modifier = Modifier.alpha(0.1f))
                        Surface(
                            modifier = Modifier
                                .size(60.dp)
                                .shadow(8.dp, CircleShape)
                                .bouncyClick { viewModel.swapUnits() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 8.dp
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Swap",
                                modifier = Modifier.padding(16.dp).size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    UnitSection(
                        isInput = false,
                        label = "TO UNIT",
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
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PRECISION CONVERSION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${state.inputValue} ${state.fromUnit} = ${state.outputValue} ${state.toUnit}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Conversions are based on standard international metric and imperial data models.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
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
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isInput) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            } else {
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
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
                .height(56.dp)
                .width(100.dp)
                .bouncyClick { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    selectedUnit, 
                    style = MaterialTheme.typography.labelLarge, 
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit, fontWeight = FontWeight.Bold) },
                    onClick = {
                        onUnitChange(unit)
                        expanded = false
                    },
                    trailingIcon = if (unit == selectedUnit) {
                        { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
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
