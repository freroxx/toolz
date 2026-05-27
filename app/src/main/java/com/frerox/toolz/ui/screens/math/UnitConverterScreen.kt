package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnitConverterScreen(
    viewModel: UnitConverterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val units = state.availableUnits

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "UNIT CONVERTER",
                subtitle = "Scientific convertions",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 24.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExpressiveCard(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                    elevation = 0.dp
                ) {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.height(300.dp).fadingEdges(top = 16.dp, bottom = 16.dp).padding(16.dp).verticalScroll(scrollState)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            maxItemsInEachRow = 3
                        ) {
                            ConversionType.entries.forEachIndexed { index, type ->
                                val isSelected = state.type == type
                                StaggeredEntrance(index = index) {
                                    ExpressiveCard(
                                        onClick = { viewModel.onTypeChange(type) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(84.dp),
                                        shape = RoundedCornerShape(22.dp),
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)) else null,
                                        elevation = if (isSelected) 6.dp else 0.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = getIconForType(type),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = type.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                fontSize = 10.sp,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                ExpressiveCard(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(40.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(28.dp)) {
                        UnitSection(
                            isInput = true,
                            label = "INPUT PARAMETER",
                            value = state.inputValue,
                            unit = state.fromUnit,
                            units = units,
                            onValueChange = { viewModel.onInputValueChange(it) },
                            onUnitChange = { viewModel.onFromUnitChange(it) }
                        )
                        
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            HorizontalDivider(modifier = Modifier.alpha(0.08f))
                            ExpressiveCard(
                                onClick = { viewModel.swapUnits() },
                                modifier = Modifier.size(68.dp),
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.25f)),
                                elevation = 16.dp
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapVert,
                                        contentDescription = "Swap",
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        UnitSection(
                            isInput = false,
                            label = "CALCULATED OUTPUT",
                            value = state.outputValue,
                            unit = state.toUnit,
                            units = units,
                            onUnitChange = { viewModel.onToUnitChange(it) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                AnimatedVisibility(
                    visible = state.inputValue.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "PRECISION RESULT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "${state.inputValue} ${state.fromUnit} = ${state.outputValue} ${state.toUnit}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                        Text(
                            text = "Engine leverages high-precision IEEE 754 floating-point architecture for all unit calculations.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Black,
                            lineHeight = 16.sp
                        )
                    }
                }
                
                Spacer(Modifier.height(48.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isInput) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            } else {
                Surface(
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
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
                .height(64.dp)
                .width(110.dp)
                .bouncyClick { expanded = true },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
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
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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
                    text = { Text(unit, fontWeight = FontWeight.Bold) },
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
        ConversionType.FORCE -> Icons.Rounded.FitnessCenter
        ConversionType.PRESSURE -> Icons.Rounded.TireRepair
        ConversionType.POWER -> Icons.Rounded.ElectricBolt
        ConversionType.CURRENCY -> Icons.Rounded.Paid
    }
}
