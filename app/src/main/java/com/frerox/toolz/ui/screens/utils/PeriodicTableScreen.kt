/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.utils

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground

data class Element(
    val symbol: String,
    val name: String,
    val atomicNumber: Int,
    val weight: Double,
    val category: String,
    val color: Color,
    val description: String,
    val funFact: String,
    val electronConfig: String,
    val meltPoint: Double?,
    val boilingPoint: Double?,
    val phase: String,
    val discoveredBy: String,
    val density: Double?,
    val abundance: String = "Unknown"
)

private fun categoryColor(category: String): Color = when (category) {
    "Alkali Metal" -> Color(0xFFF44336)
    "Alkaline Earth Metal" -> Color(0xFFFF9800)
    "Transition Metal" -> Color(0xFF3F51B5)
    "Post-Transition Metal" -> Color(0xFF607D8B)
    "Metalloid" -> Color(0xFF795548)
    "Reactive Nonmetal" -> Color(0xFF4CAF50)
    "Noble Gas" -> Color(0xFF9C27B0)
    "Halogen" -> Color(0xFF009688)
    "Lanthanide" -> Color(0xFF8D6E63)
    "Actinide" -> Color(0xFFE91E63)
    else -> Color(0xFF607D8B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicTableScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedElement by remember { mutableStateOf<Element?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var compareMode by remember { mutableStateOf(false) }
    val compareElements = remember { mutableStateListOf<Element>() }
    val performanceMode = LocalPerformanceMode.current

    val allElements = remember { getAllElements() }
    val categories = remember { allElements.map { it.category }.distinct() }

    val filteredElements = remember(searchQuery, selectedCategory) {
        allElements.filter { element ->
            val matchesSearch = element.name.contains(searchQuery, ignoreCase = true) ||
                element.symbol.contains(searchQuery, ignoreCase = true) ||
                element.atomicNumber.toString().contains(searchQuery)
            val matchesCategory = selectedCategory == null || element.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent).statusBarsPadding()) {
                ExpressiveTopAppBar(
                    title = stringResource(R.string.st_PeriodicTableScreen_a1b2),
                    subtitle = stringResource(R.string.st_PeriodicTableScreen_indexed, allElements.size),
                    navigationIcon = {
                        ToolzExpressiveIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_WorldClockScreen_c3d4))
                        }
                    },
                    actions = {
                        ToolzExpressiveIconToggleButton(
                            checked = compareMode,
                            onCheckedChange = {
                                compareMode = it
                                compareElements.clear()
                            }
                        ) {
                            Icon(Icons.Rounded.Compare, contentDescription = stringResource(R.string.st_PeriodicTableScreen_c3d4))
                        }
                    }
                )

                ExpressiveSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.st_PeriodicTableScreen_e5f6)) }
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (performanceMode) Modifier else Modifier.horizontalFadingEdges(left = 24.dp, right = 24.dp)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        ExpressiveFilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text(stringResource(R.string.st_PeriodicTableScreen_g7h8)) }
                        )
                    }
                    items(categories) { category ->
                        ExpressiveFilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.uppercase()) }
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(filteredElements, key = { _, element -> element.atomicNumber }) { index, element ->
                    StaggeredEntrance(index = index) {
                        ModernElementCard(
                            element = element,
                            isSelectedForCompare = compareElements.contains(element),
                            modifier = Modifier.animateItem()
                        ) {
                            if (compareMode) {
                                if (compareElements.contains(element)) {
                                    compareElements.remove(element)
                                } else if (compareElements.size < 2) {
                                    compareElements.add(element)
                                }
                            } else {
                                selectedElement = element
                            }
                        }
                    }
                }
            }
        }

        if (selectedElement != null) {
            ElementDetailSheet(
                element = selectedElement!!,
                onDismiss = { selectedElement = null }
            )
        }

        if (compareElements.size == 2) {
            ComparisonSheet(
                element1 = compareElements[0],
                element2 = compareElements[1],
                onDismiss = { compareElements.clear() }
            )
        }
    }
}

@Composable
fun ModernElementCard(
    element: Element,
    isSelectedForCompare: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    ExpressiveCard(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .expressiveMorphing(interactionSource),
        shape = BouncyShape,
        containerColor = if (isSelectedForCompare) element.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = if (isSelectedForCompare) 0.dp else 0.dp,
        border = BorderStroke(
            width = if (isSelectedForCompare) 2.dp else 1.dp,
            color = element.color.copy(alpha = if (isSelectedForCompare) 0.9f else 0.24f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = element.atomicNumber.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = element.color
                    )
                    if (isSelectedForCompare) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = element.color,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(element.color.copy(alpha = 0.7f), CircleShape)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = element.symbol,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                        ),
                        color = element.color
                    )
                }

                Text(
                    text = element.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementDetailSheet(element: Element, onDismiss: () -> Unit) {
    val performanceMode = LocalPerformanceMode.current
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 12.dp, bottom = 32.dp))
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = element.color,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            element.symbol,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        element.name.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        color = element.color.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            element.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = element.color,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }
                    Text(
                        text = "${element.atomicNumber} • ${String.format(java.util.Locale.US, "%.3f u", element.weight)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // Cards Grid
            val locale = java.util.Locale.US
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailCard(Modifier.weight(1f), stringResource(R.string.st_PeriodicTableScreen_k1l2), element.atomicNumber.toString(), Icons.Rounded.Numbers, element.color)
                    DetailCard(Modifier.weight(1f), stringResource(R.string.st_PeriodicTableScreen_m3n4), String.format(locale, "%.4f u", element.weight), Icons.Rounded.MonitorWeight, element.color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailCard(Modifier.weight(1f), stringResource(R.string.st_PeriodicTableScreen_o5p6), element.electronConfig, Icons.Rounded.Layers, element.color)
                    DetailCard(Modifier.weight(1f), stringResource(R.string.st_PeriodicTableScreen_q7r8), element.discoveredBy, Icons.Rounded.PersonSearch, element.color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailCard(
                        Modifier.weight(1f),
                        stringResource(R.string.st_PeriodicTableScreen_s9t0),
                        if (element.density != null) String.format(locale, "%.4f g/cm³", element.density) else stringResource(R.string.st_PeriodicTableScreen_unknown),
                        Icons.Rounded.Compress,
                        element.color
                    )
                    DetailCard(Modifier.weight(1f), stringResource(R.string.st_PeriodicTableScreen_u1v2), element.abundance, Icons.Rounded.Public, element.color)
                }
            }

            // Description and Properties
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Surface(
                        color = element.color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.st_PeriodicTableScreen_w3x4),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = element.color,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        element.description,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PropertyItem(
                            stringResource(R.string.st_PeriodicTableScreen_y5z6),
                            if (element.meltPoint != null) "${String.format(locale, "%.1f", element.meltPoint)} °C" else stringResource(R.string.st_PeriodicTableScreen_unknown),
                            Icons.Rounded.DeviceThermostat
                        )
                        PropertyItem(
                            stringResource(R.string.st_PeriodicTableScreen_a7b8),
                            if (element.boilingPoint != null) "${String.format(locale, "%.1f", element.boilingPoint)} °C" else stringResource(R.string.st_PeriodicTableScreen_unknown),
                            Icons.Rounded.Air
                        )
                        PropertyItem(
                            "PHASE",
                            element.phase,
                            Icons.Rounded.Category
                        )
                    }
                }
            }

            // Fun Fact
            Surface(
                color = element.color.copy(alpha = 0.10f),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, element.color.copy(alpha = 0.18f))
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = element.color.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = element.color, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.st_PeriodicTableScreen_c9d0), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = element.color, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(element.funFact, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonSheet(element1: Element, element2: Element, onDismiss: () -> Unit) {
    val performanceMode = LocalPerformanceMode.current
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 12.dp, bottom = 32.dp))
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                stringResource(R.string.st_PeriodicTableScreen_e1f2),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernElementCard(element1, false) {}
                    ComparisonProperty(stringResource(R.string.st_BatteryInfoScreen_y5z6), String.format(java.util.Locale.US, "%.3f u", element1.weight))
                    ComparisonProperty(
                        stringResource(R.string.st_PeriodicTableScreen_s9t0),
                        if (element1.density != null) String.format(java.util.Locale.US, "%.3f g/cm³", element1.density) else "—"
                    )
                    ComparisonProperty(
                        stringResource(R.string.st_PeriodicTableScreen_g3h4),
                        if (element1.meltPoint != null) "${element1.meltPoint} °C" else "—"
                    )
                    ComparisonProperty(
                        stringResource(R.string.st_PeriodicTableScreen_i5j6),
                        if (element1.boilingPoint != null) "${element1.boilingPoint} °C" else "—"
                    )
                    ComparisonProperty("PHASE", element1.phase)
                    ComparisonProperty("CONFIG", element1.electronConfig)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernElementCard(element2, false) {}
                    ComparisonProperty(stringResource(R.string.st_BatteryInfoScreen_y5z6), String.format(java.util.Locale.US, "%.3f u", element2.weight))
                    ComparisonProperty(
                        stringResource(R.string.st_PeriodicTableScreen_s9t0),
                        if (element2.density != null) String.format(java.util.Locale.US, "%.3f g/cm³", element2.density) else "—"
                    )
                    ComparisonProperty(
                        stringResource(R.string.st_PeriodicTableScreen_g3h4),
                        if (element2.meltPoint != null) "${element2.meltPoint} °C" else "—"
                    )
                    ComparisonProperty(
                        stringResource(R.string.st_PeriodicTableScreen_i5j6),
                        if (element2.boilingPoint != null) "${element2.boilingPoint} °C" else "—"
                    )
                    ComparisonProperty("PHASE", element2.phase)
                    ComparisonProperty("CONFIG", element2.electronConfig)
                }
            }

            ToolzExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.st_PeriodicTableScreen_k7l8))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun ComparisonProperty(label: String, val1: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(val1, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DetailCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PropertyItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
        }
    }
}

private fun getAllElements(): List<Element> = listOf(
    Element("H", "Hydrogen", 1, 1.008, "Reactive Nonmetal", categoryColor("Reactive Nonmetal"), "Lightest and most abundant element in the universe.", "Makes up ~75% of baryonic mass and fuels stars via fusion.", "1s¹", -259.16, -252.87, "Gas", "Cavendish (1766)", 0.00008988, "1400 ppm"),
    Element("He", "Helium", 2, 4.0026, "Noble Gas", categoryColor("Noble Gas"), "Inert, second-lightest element, non-flammable.", "First discovered in the Sun's spectrum before Earth.", "1s²", -272.2, -268.93, "Gas", "Janssen/Lockyer/Ramsay (1868-1895)", 0.0001786, "0.008 ppm"),
    Element("Li", "Lithium", 3, 6.94, "Alkali Metal", categoryColor("Alkali Metal"), "Soft, lightest metal, highly reactive.", "Core of rechargeable lithium-ion batteries.", "[He] 2s¹", 180.5, 1342.0, "Solid", "Arfwedson (1817)", 0.534, "20 ppm"),
    Element("Be", "Beryllium", 4, 9.0122, "Alkaline Earth Metal", categoryColor("Alkaline Earth Metal"), "Hard, lightweight, high melting point.", "Transparent to X-rays; used in X-ray windows.", "[He] 2s²", 1287.0, 2469.0, "Solid", "Vauquelin (1798)", 1.85, "2.8 ppm"),
    Element("B", "Boron", 5, 10.81, "Metalloid", categoryColor("Metalloid"), "Hard, black-brown metalloid.", "In fiberglass and heat-resistant borosilicate glass.", "[He] 2s² 2p¹", 2075.0, 4000.0, "Solid", "Gay-Lussac et al. (1808)", 2.34, "10 ppm"),
    Element("C", "Carbon", 6, 12.011, "Reactive Nonmetal", categoryColor("Reactive Nonmetal"), "Basis of organic chemistry and life.", "Diamond is hardest natural substance; graphite conducts.", "[He] 2s² 2p²", 3550.0, 4027.0, "Solid", "Prehistoric", 2.267, "200 ppm"),
    Element("N", "Nitrogen", 7, 14.007, "Reactive Nonmetal", categoryColor("Reactive Nonmetal"), "78% of Earth's atmosphere, inert diatomic gas.", "Essential for amino acids and DNA.", "[He] 2s² 2p³", -210.0, -195.79, "Gas", "Rutherford (1772)", 0.001251, "19 ppm"),
    Element("O", "Oxygen", 8, 15.999, "Reactive Nonmetal", categoryColor("Reactive Nonmetal"), "Essential for respiration and combustion.", "21% of atmosphere; most abundant in Earth's crust by mass.", "[He] 2s² 2p⁴", -218.79, -182.96, "Gas", "Priestley/Scheele (1774)", 0.001429, "461000 ppm"),
    Element("F", "Fluorine", 9, 18.998, "Halogen", categoryColor("Halogen"), "Most reactive nonmetal, pale yellow gas.", "In toothpaste as fluoride to prevent cavities.", "[He] 2s² 2p⁵", -219.67, -188.11, "Gas", "Moissan (1886)", 0.001696, "585 ppm"),
    Element("Ne", "Neon", 10, 20.180, "Noble Gas", categoryColor("Noble Gas"), "Inert noble gas glowing orange-red when electrified.", "Used in neon signs and cryogenic refrigeration.", "[He] 2s² 2p⁶", -248.59, -246.08, "Gas", "Ramsay/Travers (1898)", 0.0009, "0.005 ppm"),
    Element("Na", "Sodium", 11, 22.99, "Alkali Metal", categoryColor("Alkali Metal"), "Soft, reactive, silvery-white alkali metal.", "Common as table salt (NaCl).", "[Ne] 3s¹", 97.72, 883.0, "Solid", "Davy (1807)", 0.968, "23600 ppm"),
    Element("Mg", "Magnesium", 12, 24.305, "Alkaline Earth Metal", categoryColor("Alkaline Earth Metal"), "Light, strong, silvery-white metal.", "Essential for chlorophyll and human health.", "[Ne] 3s²", 650.0, 1090.0, "Solid", "Davy (1808)", 1.738, "23300 ppm"),
    Element("Al", "Aluminium", 13, 26.982, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Light, corrosion-resistant, abundant metal.", "Most abundant metal in Earth's crust.", "[Ne] 3s² 3p¹", 660.32, 2470.0, "Solid", "Ørsted (1825)", 2.70, "82300 ppm"),
    Element("Si", "Silicon", 14, 28.085, "Metalloid", categoryColor("Metalloid"), "Hard, brittle metalloid, semiconductor cornerstone.", "Second most abundant element in Earth's crust.", "[Ne] 3s² 3p²", 1414.0, 3265.0, "Solid", "Berzelius (1823)", 2.329, "282000 ppm"),
    Element("P", "Phosphorus", 15, 30.974, "Reactive Nonmetal", categoryColor("Reactive Nonmetal"), "Glows in the dark (white P), essential for life.", "Vital for DNA, ATP, and bones.", "[Ne] 3s² 3p³", 44.15, 280.5, "Solid", "Brand (1669)", 1.823, "1050 ppm"),
    Element("S", "Sulfur", 16, 32.06, "Reactive Nonmetal", categoryColor("Reactive Nonmetal"), "Yellow brittle nonmetal, distinct odor.", "Known for rotten-egg smell as H₂S.", "[Ne] 3s² 3p⁴", 115.21, 444.6, "Solid", "Prehistoric", 2.07, "350 ppm"),
    Element("Cl", "Chlorine", 17, 35.45, "Halogen", categoryColor("Halogen"), "Greenish gas, strong oxidizer and disinfectant.", "Purifies drinking water worldwide.", "[Ne] 3s² 3p⁵", -101.5, -34.04, "Gas", "Scheele (1774)", 0.0032, "145 ppm"),
    Element("Ar", "Argon", 18, 39.948, "Noble Gas", categoryColor("Noble Gas"), "Inert, ~1% of atmosphere, colorless.", "Used in light bulbs and welding shields.", "[Ne] 3s² 3p⁶", -189.35, -185.85, "Gas", "Rayleigh/Ramsay (1894)", 0.001784, "3.5 ppm"),
    Element("K", "Potassium", 19, 39.098, "Alkali Metal", categoryColor("Alkali Metal"), "Soft, reacts violently with water.", "Vital nerve electrolyte.", "[Ar] 4s¹", 63.5, 759.0, "Solid", "Davy (1807)", 0.862, "20900 ppm"),
    Element("Ca", "Calcium", 20, 40.078, "Alkaline Earth Metal", categoryColor("Alkaline Earth Metal"), "Essential for bones, teeth, and shells.", "5th most abundant element in crust.", "[Ar] 4s²", 842.0, 1484.0, "Solid", "Davy (1808)", 1.55, "41500 ppm"),
    Element("Sc", "Scandium", 21, 44.956, "Transition Metal", categoryColor("Transition Metal"), "Rare, soft silvery metal.", "Used in aerospace alloys and high-intensity lamps.", "[Ar] 3d¹ 4s²", 1541.0, 2830.0, "Solid", "Nilson (1879)", 2.985, "22 ppm"),
    Element("Ti", "Titanium", 22, 47.867, "Transition Metal", categoryColor("Transition Metal"), "Strong, light, corrosion-proof.", "Biocompatible for medical implants.", "[Ar] 3d² 4s²", 1668.0, 3287.0, "Solid", "Gregor (1791)", 4.506, "5650 ppm"),
    Element("V", "Vanadium", 23, 50.942, "Transition Metal", categoryColor("Transition Metal"), "Hard, silvery-grey, corrosion-resistant.", "Strengthens steel dramatically.", "[Ar] 3d³ 4s²", 1910.0, 3407.0, "Solid", "del Río (1801)", 6.11, "120 ppm"),
    Element("Cr", "Chromium", 24, 51.996, "Transition Metal", categoryColor("Transition Metal"), "Lustrous, hard, stainless steel key.", "Gives rubies their red color.", "[Ar] 3d⁵ 4s¹", 1907.0, 2671.0, "Solid", "Vauquelin (1797)", 7.15, "102 ppm"),
    Element("Mn", "Manganese", 25, 54.938, "Transition Metal", categoryColor("Transition Metal"), "Hard, brittle, essential trace element.", "Used in batteries and steel.", "[Ar] 3d⁵ 4s²", 1246.0, 2061.0, "Solid", "Gahn (1774)", 7.21, "950 ppm"),
    Element("Fe", "Iron", 26, 55.845, "Transition Metal", categoryColor("Transition Metal"), "Most used metal, magnetic and abundant.", "Carries oxygen in blood as hemoglobin.", "[Ar] 3d⁶ 4s²", 1538.0, 2861.0, "Solid", "Prehistoric", 7.874, "56300 ppm"),
    Element("Co", "Cobalt", 27, 58.933, "Transition Metal", categoryColor("Transition Metal"), "Hard, bluish-grey, ferromagnetic.", "In vitamin B12 and superalloys.", "[Ar] 3d⁷ 4s²", 1495.0, 2927.0, "Solid", "Brandt (1735)", 8.90, "25 ppm"),
    Element("Ni", "Nickel", 28, 58.693, "Transition Metal", categoryColor("Transition Metal"), "Corrosion-resistant, silvery-white.", "Used in coins and stainless steel.", "[Ar] 3d⁸ 4s²", 1455.0, 2913.0, "Solid", "Cronstedt (1751)", 8.908, "84 ppm"),
    Element("Cu", "Copper", 29, 63.546, "Transition Metal", categoryColor("Transition Metal"), "Reddish, excellent conductor.", "Used for 10,000 years; Bronze Age.", "[Ar] 3d¹⁰ 4s¹", 1084.62, 2562.0, "Solid", "Prehistoric", 8.96, "60 ppm"),
    Element("Zn", "Zinc", 30, 65.38, "Transition Metal", categoryColor("Transition Metal"), "Bluish-white, galvanizes iron.", "Essential for immunity and enzymes.", "[Ar] 3d¹⁰ 4s²", 419.53, 907.0, "Solid", "Marggraf (1746)", 7.14, "70 ppm"),
    Element("Ga", "Gallium", 31, 69.723, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Melts in your hand at 29.8°C.", "Used in LEDs and semiconductors.", "[Ar] 3d¹⁰ 4s² 4p¹", 29.76, 2204.0, "Solid", "Lecoq de Boisbaudran (1875)", 5.91, "19 ppm"),
    Element("Ge", "Germanium", 32, 72.630, "Metalloid", categoryColor("Metalloid"), "Brittle metalloid, semiconductor predecessor to Si.", "In fiber optics and infrared lenses.", "[Ar] 3d¹⁰ 4s² 4p²", 938.25, 2833.0, "Solid", "Winkler (1886)", 5.323, "1.5 ppm"),
    Element("As", "Arsenic", 33, 74.922, "Metalloid", categoryColor("Metalloid"), "Brittle metalloid, infamous toxic.", "Was the poison of kings.", "[Ar] 3d¹⁰ 4s² 4p³", 817.0, 614.0, "Solid", "Albertus Magnus (1250)", 5.727, "1.8 ppm"),
    Element("Se", "Selenium", 34, 78.971, "Reactive Nonmetal", categoryColor("Reactive Nonmetal"), "Photoconductive, essential trace element.", "In photocopiers and solar cells.", "[Ar] 3d¹⁰ 4s² 4p⁴", 221.0, 685.0, "Solid", "Berzelius (1817)", 4.81, "0.05 ppm"),
    Element("Br", "Bromine", 35, 79.904, "Halogen", categoryColor("Halogen"), "Only liquid halogen, reddish fume.", "Red-brown volatile liquid.", "[Ar] 3d¹⁰ 4s² 4p⁵", -7.2, 58.8, "Liquid", "Balard (1826)", 3.1028, "2.4 ppm"),
    Element("Kr", "Krypton", 36, 83.798, "Noble Gas", categoryColor("Noble Gas"), "Inert, used in flash lamps.", "Name means 'hidden' in Greek.", "[Ar] 3d¹⁰ 4s² 4p⁶", -157.36, -153.22, "Gas", "Ramsay/Travers (1898)", 0.00375, "0.0001 ppm"),
    Element("Rb", "Rubidium", 37, 85.468, "Alkali Metal", categoryColor("Alkali Metal"), "Soft, silvery, ignites in air.", "In atomic clocks and GPS.", "[Kr] 5s¹", 39.31, 688.0, "Solid", "Bunsen/Kirchhoff (1861)", 1.532, "90 ppm"),
    Element("Sr", "Strontium", 38, 87.62, "Alkaline Earth Metal", categoryColor("Alkaline Earth Metal"), "Soft, reactive, burns crimson.", "Red fireworks and flares.", "[Kr] 5s²", 777.0, 1377.0, "Solid", "Davy (1808)", 2.63, "370 ppm"),
    Element("Y", "Yttrium", 39, 88.906, "Transition Metal", categoryColor("Transition Metal"), "Silvery, high-temp superconductor component.", "In YAG lasers and TV phosphors.", "[Kr] 4d¹ 5s²", 1526.0, 2930.0, "Solid", "Gadolin (1794)", 4.472, "33 ppm"),
    Element("Zr", "Zirconium", 40, 91.224, "Transition Metal", categoryColor("Transition Metal"), "Corrosion-proof, nuclear cladding.", "In nuclear fuel rods.", "[Kr] 4d² 5s²", 1855.0, 4409.0, "Solid", "Klaproth (1789)", 6.52, "165 ppm"),
    Element("Nb", "Niobium", 41, 92.906, "Transition Metal", categoryColor("Transition Metal"), "Superconducting, bluish.", "In MRI superconducting magnets.", "[Kr] 4d⁴ 5s¹", 2477.0, 4744.0, "Solid", "Hatchett (1801)", 8.57, "20 ppm"),
    Element("Mo", "Molybdenum", 42, 95.95, "Transition Metal", categoryColor("Transition Metal"), "Very hard, high melting point.", "Hardens steel for cutting tools.", "[Kr] 4d⁵ 5s¹", 2623.0, 4639.0, "Solid", "Scheele (1778)", 10.28, "1.2 ppm"),
    Element("Tc", "Technetium", 43, 98.0, "Transition Metal", categoryColor("Transition Metal"), "First synthetic element, radioactive.", "In medical imaging (Tc-99m).", "[Kr] 4d⁵ 5s²", 2157.0, 4265.0, "Solid", "Perrier/Segrè (1937)", 11.0, "trace"),
    Element("Ru", "Ruthenium", 44, 101.07, "Transition Metal", categoryColor("Transition Metal"), "Hard, catalytic, platinum group.", "In chip resistors and catalysts.", "[Kr] 4d⁷ 5s¹", 2334.0, 4150.0, "Solid", "Klaus (1844)", 12.45, "0.001 ppm"),
    Element("Rh", "Rhodium", 45, 102.91, "Transition Metal", categoryColor("Transition Metal"), "Most expensive precious metal, silvery.", "In catalytic converters.", "[Kr] 4d⁸ 5s¹", 1964.0, 3695.0, "Solid", "Wollaston (1803)", 12.41, "0.001 ppm"),
    Element("Pd", "Palladium", 46, 106.42, "Transition Metal", categoryColor("Transition Metal"), "Absorbs hydrogen 900× volume.", "In hydrogen storage and autocatalysts.", "[Kr] 4d¹⁰", 1554.9, 2963.0, "Solid", "Wollaston (1803)", 12.02, "0.015 ppm"),
    Element("Ag", "Silver", 47, 107.87, "Transition Metal", categoryColor("Transition Metal"), "Best electrical and thermal conductor.", "Sterling silver is 92.5% Ag.", "[Kr] 4d¹⁰ 5s¹", 961.78, 2162.0, "Solid", "Prehistoric", 10.49, "0.075 ppm"),
    Element("Cd", "Cadmium", 48, 112.41, "Transition Metal", categoryColor("Transition Metal"), "Soft, bluish-white, toxic.", "In NiCd batteries; yellow pigment.", "[Kr] 4d¹⁰ 5s²", 321.07, 767.0, "Solid", "Stromeyer (1817)", 8.65, "0.15 ppm"),
    Element("In", "Indium", 49, 114.82, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Soft, seals vacuum, low melt.", "In touchscreens as ITO.", "[Kr] 4d¹⁰ 5s² 5p¹", 156.6, 2072.0, "Solid", "Reich/Richter (1863)", 7.31, "0.25 ppm"),
    Element("Sn", "Tin", 50, 118.71, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Soft, malleable, bronze component.", "Tin whistles and solder.", "[Kr] 4d¹⁰ 5s² 5p²", 231.93, 2602.0, "Solid", "Prehistoric", 7.365, "2.3 ppm"),
    Element("Sb", "Antimony", 51, 121.76, "Metalloid", categoryColor("Metalloid"), "Brittle, flame-retardant.", "In ancient kohl eyeliner.", "[Kr] 4d¹⁰ 5s² 5p³", 630.63, 1587.0, "Solid", "Early historic", 6.697, "0.2 ppm"),
    Element("Te", "Tellurium", 52, 127.60, "Metalloid", categoryColor("Metalloid"), "Brittle, semiconductor, rare.", "Garlic breath if ingested.", "[Kr] 4d¹⁰ 5s² 5p⁴", 449.5, 988.0, "Solid", "von Reichenstein (1782)", 6.24, "0.001 ppm"),
    Element("I", "Iodine", 53, 126.90, "Halogen", categoryColor("Halogen"), "Violet subliming solid, antiseptic.", "Essential for thyroid hormones.", "[Kr] 4d¹⁰ 5s² 5p⁵", 113.7, 184.3, "Solid", "Courtois (1811)", 4.933, "0.45 ppm"),
    Element("Xe", "Xenon", 54, 131.29, "Noble Gas", categoryColor("Noble Gas"), "Dense, bright flash, anesthetic.", "In ion thrusters and lamps.", "[Kr] 4d¹⁰ 5s² 5p⁶", -111.8, -108.1, "Gas", "Ramsay/Travers (1898)", 0.0059, "0.00003 ppm"),
    Element("Cs", "Caesium", 55, 132.91, "Alkali Metal", categoryColor("Alkali Metal"), "Most reactive stable metal, golden.", "Defines the second (atomic clock).", "[Xe] 6s¹", 28.44, 671.0, "Solid", "Bunsen/Kirchhoff (1860)", 1.93, "3 ppm"),
    Element("Ba", "Barium", 56, 137.33, "Alkaline Earth Metal", categoryColor("Alkaline Earth Metal"), "Dense, green flame, reactive.", "In medical barium meal X-rays.", "[Xe] 6s²", 727.0, 1845.0, "Solid", "Davy (1808)", 3.51, "425 ppm"),
    Element("La", "Lanthanum", 57, 138.91, "Lanthanide", categoryColor("Lanthanide"), "Soft, first lanthanide, reactive.", "In camera lenses and catalysts.", "[Xe] 5d¹ 6s²", 920.0, 3464.0, "Solid", "Mosander (1839)", 6.162, "39 ppm"),
    Element("Ce", "Cerium", 58, 140.12, "Lanthanide", categoryColor("Lanthanide"), "Most abundant lanthanide, pyrophoric.", "In lighter flints (ferrocerium).", "[Xe] 4f¹ 5d¹ 6s²", 798.0, 3443.0, "Solid", "Berzelius/Hisinger (1803)", 6.77, "66.5 ppm"),
    Element("Pr", "Praseodymium", 59, 140.91, "Lanthanide", categoryColor("Lanthanide"), "Green salts, high magnetism.", "In aircraft alloys and magnets.", "[Xe] 4f³ 6s²", 931.0, 3290.0, "Solid", "von Welsbach (1885)", 6.77, "9.2 ppm"),
    Element("Nd", "Neodymium", 60, 144.24, "Lanthanide", categoryColor("Lanthanide"), "Strongest permanent magnets.", "In headphones and wind turbines.", "[Xe] 4f⁴ 6s²", 1021.0, 3100.0, "Solid", "von Welsbach (1885)", 7.01, "41.5 ppm"),
    Element("Pm", "Promethium", 61, 145.0, "Lanthanide", categoryColor("Lanthanide"), "Only radioactive lanthanide.", "In luminous paint and atomic batteries.", "[Xe] 4f⁵ 6s²", 1042.0, 3000.0, "Solid", "Marinsky et al. (1945)", 7.26, "trace"),
    Element("Sm", "Samarium", 62, 150.36, "Lanthanide", categoryColor("Lanthanide"), "Strong magnets, neutron absorber.", "In samarium-cobalt magnets.", "[Xe] 4f⁶ 6s²", 1072.0, 1803.0, "Solid", "Lecoq de Boisbaudran (1879)", 7.52, "7.05 ppm"),
    Element("Eu", "Europium", 63, 151.96, "Lanthanide", categoryColor("Lanthanide"), "Bright red phosphor, reactive.", "In anti-counterfeit Euro banknotes.", "[Xe] 4f⁷ 6s²", 822.0, 1529.0, "Solid", "Demarçay (1901)", 5.244, "2 ppm"),
    Element("Gd", "Gadolinium", 64, 157.25, "Lanthanide", categoryColor("Lanthanide"), "Ferromagnetic, MRI contrast.", "Strong neutron capture.", "[Xe] 4f⁷ 5d¹ 6s²", 1312.0, 3250.0, "Solid", "de Marignac (1880)", 7.90, "6.2 ppm"),
    Element("Tb", "Terbium", 65, 158.93, "Lanthanide", categoryColor("Lanthanide"), "Green phosphor, magneto-strictive.", "In displays and sonar.", "[Xe] 4f⁹ 6s²", 1356.0, 3128.0, "Solid", "Mosander (1843)", 8.23, "1.2 ppm"),
    Element("Dy", "Dysprosium", 66, 162.50, "Lanthanide", categoryColor("Lanthanide"), "High magnetic moment, soft.", "In wind turbines and data storage.", "[Xe] 4f¹⁰ 6s²", 1412.0, 2567.0, "Solid", "Lecoq de Boisbaudran (1886)", 8.54, "5.2 ppm"),
    Element("Ho", "Holmium", 67, 164.93, "Lanthanide", categoryColor("Lanthanide"), "Most magnetic element.", "In lasers and magnets.", "[Xe] 4f¹¹ 6s²", 1474.0, 2695.0, "Solid", "Cleve (1878)", 8.79, "1.3 ppm"),
    Element("Er", "Erbium", 68, 167.26, "Lanthanide", categoryColor("Lanthanide"), "Pink, fiber amplifier.", "In optical fiber communications.", "[Xe] 4f¹² 6s²", 1529.0, 2868.0, "Solid", "Mosander (1843)", 9.066, "3.5 ppm"),
    Element("Tm", "Thulium", 69, 168.93, "Lanthanide", categoryColor("Lanthanide"), "Rarest lanthanide, silvery.", "In portable X-rays.", "[Xe] 4f¹³ 6s²", 1545.0, 1950.0, "Solid", "Cleve (1879)", 9.32, "0.52 ppm"),
    Element("Yb", "Ytterbium", 70, 173.05, "Lanthanide", categoryColor("Lanthanide"), "Atomic clock, soft.", "In stainless steels and lasers.", "[Xe] 4f¹⁴ 6s²", 824.0, 1196.0, "Solid", "de Marignac (1878)", 6.90, "3.2 ppm"),
    Element("Lu", "Lutetium", 71, 174.97, "Lanthanide", categoryColor("Lanthanide"), "Hardest lanthanide, dense.", "In PET scanners and catalysts.", "[Xe] 4f¹⁴ 5d¹ 6s²", 1652.0, 3402.0, "Solid", "Urbain/von Welsbach (1907)", 9.841, "0.8 ppm"),
    Element("Hf", "Hafnium", 72, 178.49, "Transition Metal", categoryColor("Transition Metal"), "Control rods, high corrosion.", "Nearly identical to zirconium.", "[Xe] 4f¹⁴ 5d² 6s²", 2233.0, 4603.0, "Solid", "Coster/Hevesy (1923)", 13.31, "3 ppm"),
    Element("Ta", "Tantalum", 73, 180.95, "Transition Metal", categoryColor("Transition Metal"), "Acid-proof, dense, blue-grey.", "In capacitors and implants.", "[Xe] 4f¹⁴ 5d³ 6s²", 3017.0, 5458.0, "Solid", "Ekeberg (1802)", 16.69, "2 ppm"),
    Element("W", "Tungsten", 74, 183.84, "Transition Metal", categoryColor("Transition Metal"), "Highest melting point of all elements.", "Filaments and cutting tools.", "[Xe] 4f¹⁴ 5d⁴ 6s²", 3422.0, 5555.0, "Solid", "Elhuyar (1783)", 19.25, "1.25 ppm"),
    Element("Re", "Rhenium", 75, 186.21, "Transition Metal", categoryColor("Transition Metal"), "Second-highest melting point, catalytic.", "In jet engines and catalysts.", "[Xe] 4f¹⁴ 5d⁵ 6s²", 3186.0, 5596.0, "Solid", "Noddack et al. (1925)", 21.02, "0.0007 ppm"),
    Element("Os", "Osmium", 76, 190.23, "Transition Metal", categoryColor("Transition Metal"), "Densest known element.", "Fountain-pen tip alloy.", "[Xe] 4f¹⁴ 5d⁶ 6s²", 3033.0, 5012.0, "Solid", "Tennant (1803)", 22.59, "0.0015 ppm"),
    Element("Ir", "Iridium", 77, 192.22, "Transition Metal", categoryColor("Transition Metal"), "Most corrosion-resistant, dense.", "In spark plugs and crucibles.", "[Xe] 4f¹⁴ 5d⁷ 6s²", 2446.0, 4428.0, "Solid", "Tennant (1803)", 22.56, "0.001 ppm"),
    Element("Pt", "Platinum", 78, 195.08, "Transition Metal", categoryColor("Transition Metal"), "Noble, catalytic, malleable.", "In chemotherapy and converters.", "[Xe] 4f¹⁴ 5d⁹ 6s¹", 1768.3, 3825.0, "Solid", "Ulloa (1735)", 21.45, "0.005 ppm"),
    Element("Au", "Gold", 79, 196.97, "Transition Metal", categoryColor("Transition Metal"), "Inert, lustrous, most malleable.", "Currency for millennia.", "[Xe] 4f¹⁴ 5d¹⁰ 6s¹", 1064.18, 2856.0, "Solid", "Prehistoric", 19.30, "0.004 ppm"),
    Element("Hg", "Mercury", 80, 200.59, "Transition Metal", categoryColor("Transition Metal"), "Only liquid metal at STP, silvery.", "In barometers; highly toxic.", "[Xe] 4f¹⁴ 5d¹⁰ 6s²", -38.83, 356.73, "Liquid", "Prehistoric", 13.534, "0.085 ppm"),
    Element("Tl", "Thallium", 81, 204.38, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Soft, toxic, grey.", "Former rodenticide, now regulated.", "[Xe] 4f¹⁴ 5d¹⁰ 6s² 6p¹", 304.0, 1473.0, "Solid", "Crookes (1861)", 11.85, "0.85 ppm"),
    Element("Pb", "Lead", 82, 207.2, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Heavy, soft, shields radiation.", "In batteries; toxic legacy.", "[Xe] 4f¹⁴ 5d¹⁰ 6s² 6p²", 327.5, 1749.0, "Solid", "Prehistoric", 11.34, "14 ppm"),
    Element("Bi", "Bismuth", 83, 208.98, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Iridescent, brittle, low toxicity.", "Active ingredient in Pepto-Bismol.", "[Xe] 4f¹⁴ 5d¹⁰ 6s² 6p³", 271.5, 1564.0, "Solid", "Geoffroy (1753)", 9.78, "0.0085 ppm"),
    Element("Po", "Polonium", 84, 209.0, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Highly radioactive, alpha emitter.", "First element discovered by Curie.", "[Xe] 4f¹⁴ 5d¹⁰ 6s² 6p⁴", 254.0, 962.0, "Solid", "Curie (1898)", 9.196, "trace"),
    Element("At", "Astatine", 85, 210.0, "Halogen", categoryColor("Halogen"), "Rarest natural element, radioactive.", "Only ~30 g in Earth's crust at once.", "[Xe] 4f¹⁴ 5d¹⁰ 6s² 6p⁵", 302.0, 337.0, "Solid", "Corson et al. (1940)", 7.0, "trace"),
    Element("Rn", "Radon", 86, 222.0, "Noble Gas", categoryColor("Noble Gas"), "Radioactive noble gas, colorless.", "Lung hazard in basements.", "[Xe] 4f¹⁴ 5d¹⁰ 6s² 6p⁶", -71.0, -61.7, "Gas", "Dorn (1900)", 0.00973, "trace"),
    Element("Fr", "Francium", 87, 223.0, "Alkali Metal", categoryColor("Alkali Metal"), "Most unstable alkali, extremely rare.", "Only ~30 g on Earth at any time.", "[Rn] 7s¹", 27.0, 677.0, "Solid", "Perey (1939)", 1.87, "trace"),
    Element("Ra", "Radium", 88, 226.0, "Alkaline Earth Metal", categoryColor("Alkaline Earth Metal"), "Radioactive, glows pale blue.", "Marie Curie's famous element.", "[Rn] 7s²", 700.0, 1737.0, "Solid", "Curie (1898)", 5.5, "trace"),
    Element("Ac", "Actinium", 89, 227.0, "Actinide", categoryColor("Actinide"), "Radioactive, glows blue in dark.", "Neutron source.", "[Rn] 6d¹ 7s²", 1227.0, 3200.0, "Solid", "Debierne (1899)", 10.0, "trace"),
    Element("Th", "Thorium", 90, 232.04, "Actinide", categoryColor("Actinide"), "Fertile nuclear fuel, abundant.", "Primordial heat source in mantle.", "[Rn] 6d² 7s²", 1750.0, 4788.0, "Solid", "Berzelius (1829)", 11.72, "9.6 ppm"),
    Element("Pa", "Protactinium", 91, 231.04, "Actinide", categoryColor("Actinide"), "Extremely rare and toxic.", "325 mg isolated in 1961.", "[Rn] 5f² 6d¹ 7s²", 1572.0, 4000.0, "Solid", "Hahn/Meitner (1917)", 15.37, "trace"),
    Element("U", "Uranium", 92, 238.03, "Actinide", categoryColor("Actinide"), "Heavy, weakly radioactive fuel.", "Depleted U is dense armor piercing.", "[Rn] 5f³ 6d¹ 7s²", 1132.5, 4131.0, "Solid", "Peligot (1841)", 19.1, "2.7 ppm"),
    Element("Np", "Neptunium", 93, 237.0, "Actinide", categoryColor("Actinide"), "First synthetic transuranium.", "By-product in reactors.", "[Rn] 5f⁴ 6d¹ 7s²", 644.0, 4000.0, "Solid", "McMillan/Abelson (1940)", 20.45, "trace"),
    Element("Pu", "Plutonium", 94, 244.0, "Actinide", categoryColor("Actinide"), "Warhead fuel, six allotropes.", "Warm to touch from alpha decay.", "[Rn] 5f⁶ 7s²", 640.0, 3228.0, "Solid", "Seaborg et al. (1940)", 19.84, "trace"),
    Element("Am", "Americium", 95, 243.0, "Actinide", categoryColor("Actinide"), "In every smoke detector.", "Named for America.", "[Rn] 5f⁷ 7s²", 1176.0, 2607.0, "Solid", "Seaborg et al. (1944)", 12.0, "trace"),
    Element("Cm", "Curium", 96, 247.0, "Actinide", categoryColor("Actinide"), "Glows purple, highly radioactive.", "Honors Pierre & Marie Curie.", "[Rn] 5f⁷ 6d¹ 7s²", 1345.0, 3110.0, "Solid", "Seaborg et al. (1944)", 13.51, "trace"),
    Element("Bk", "Berkelium", 97, 247.0, "Actinide", categoryColor("Actinide"), "Only ~1 g ever produced.", "Named for Berkeley.", "[Rn] 5f⁹ 7s²", 986.0, 2627.0, "Solid", "Seaborg et al. (1949)", 14.78, "trace"),
    Element("Cf", "Californium", 98, 251.0, "Actinide", categoryColor("Actinide"), "Strong neutron emitter.", "Starts reactors; 1 mg costs millions.", "[Rn] 5f¹⁰ 7s²", 900.0, 1472.0, "Solid", "Seaborg et al. (1950)", 15.1, "trace"),
    Element("Es", "Einsteinium", 99, 252.0, "Actinide", categoryColor("Actinide"), "From H-bomb debris.", "Honors Albert Einstein.", "[Rn] 5f¹¹ 7s²", 860.0, 996.0, "Solid", "Ghiorso et al. (1952)", 8.84, "trace"),
    Element("Fm", "Fermium", 100, 257.0, "Actinide", categoryColor("Actinide"), "No stable isotope, synthetic.", "Honors Enrico Fermi.", "[Rn] 5f¹² 7s²", 1527.0, null, "Solid", "Ghiorso et al. (1952)", 9.7, "trace"),
    Element("Md", "Mendelevium", 101, 258.0, "Actinide", categoryColor("Actinide"), "Made one atom at a time.", "Honors Dmitri Mendeleev.", "[Rn] 5f¹³ 7s²", 827.0, null, "Solid", "Ghiorso et al. (1955)", 10.3, "synthetic"),
    Element("No", "Nobelium", 102, 259.0, "Actinide", categoryColor("Actinide"), "Contested discovery, synthetic.", "Honors Alfred Nobel.", "[Rn] 5f¹⁴ 7s²", 827.0, null, "Solid", "Ghiorso et al. (1958)", 9.9, "synthetic"),
    Element("Lr", "Lawrencium", 103, 262.0, "Actinide", categoryColor("Actinide"), "Last actinide, synthetic.", "Honors Ernest Lawrence.", "[Rn] 5f¹⁴ 7s1 7p1", 1627.0, null, "Solid", "Ghiorso et al. (1961)", 15.6, "synthetic"),
    Element("Rf", "Rutherfordium", 104, 267.0, "Transition Metal", categoryColor("Transition Metal"), "Superheavy, seconds half-life.", "Honors Ernest Rutherford.", "[Rn] 5f¹⁴ 6d² 7s²", 2100.0, 5500.0, "Solid", "Ghiorso et al. (1964)", 23.2, "synthetic"),
    Element("Db", "Dubnium", 105, 268.0, "Transition Metal", categoryColor("Transition Metal"), "Joint Russia/US discovery.", "Named for Dubna.", "[Rn] 5f¹⁴ 6d³ 7s²", null, null, "Solid", "Flerov/Ghiorso (1967)", 29.0, "synthetic"),
    Element("Sg", "Seaborgium", 106, 269.0, "Transition Metal", categoryColor("Transition Metal"), "Honors living chemist Glenn Seaborg.", "Few atoms ever made.", "[Rn] 5f¹⁴ 6d⁴ 7s²", null, null, "Solid", "Ghiorso et al. (1974)", 35.0, "synthetic"),
    Element("Bh", "Bohrium", 107, 270.0, "Transition Metal", categoryColor("Transition Metal"), "Millisecond half-life.", "Honors Niels Bohr.", "[Rn] 5f¹⁴ 6d⁵ 7s²", null, null, "Solid", "Münzenberg et al. (1981)", 37.0, "synthetic"),
    Element("Hs", "Hassium", 108, 269.0, "Transition Metal", categoryColor("Transition Metal"), "Hesse state honor, 16 ms.", "Synthetic superheavy.", "[Rn] 5f¹⁴ 6d⁶ 7s²", null, null, "Solid", "Münzenberg et al. (1984)", 41.0, "synthetic"),
    Element("Mt", "Meitnerium", 109, 278.0, "Transition Metal", categoryColor("Transition Metal"), "Honors Lise Meitner, 7.6 s.", "Made by cold fusion.", "[Rn] 5f¹⁴ 6d⁷ 7s²", null, null, "Solid", "Münzenberg et al. (1982)", 37.4, "synthetic"),
    Element("Ds", "Darmstadtium", 110, 281.0, "Transition Metal", categoryColor("Transition Metal"), "Darmstadt honor, ~11 s.", "Superheavy synthetic.", "[Rn] 5f¹⁴ 6d⁸ 7s²", null, null, "Solid", "Hofmann et al. (1994)", 34.8, "synthetic"),
    Element("Rg", "Roentgenium", 111, 282.0, "Transition Metal", categoryColor("Transition Metal"), "Honors Wilhelm Röntgen, 26 s.", "Synthetic, one atom at a time.", "[Rn] 5f¹⁴ 6d⁹ 7s²", null, null, "Solid", "Hofmann et al. (1994)", 28.7, "synthetic"),
    Element("Cn", "Copernicium", 112, 285.0, "Transition Metal", categoryColor("Transition Metal"), "Honors Copernicus, liquid predicted.", "Synthetic, volatile.", "[Rn] 5f¹⁴ 6d¹⁰ 7s²", null, null, "Solid", "Hofmann et al. (1996)", 14.0, "synthetic"),
    Element("Nh", "Nihonium", 113, 286.0, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "First element discovered in Asia (Japan).", "Nihon means Japan.", "[Rn] 5f¹⁴ 6d¹⁰ 7s² 7p¹", 430.0, 1100.0, "Solid", "Morita et al. (2004)", 16.0, "synthetic"),
    Element("Fl", "Flerovium", 114, 289.0, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Gas-like at STP, Flerov Lab.", "Synthetic, 1.9 s.", "[Rn] 5f¹⁴ 6d¹⁰ 7s² 7p²", 67.0, 147.0, "Solid", "Oganessian et al. (1999)", 14.0, "synthetic"),
    Element("Mc", "Moscovium", 115, 290.0, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Moscow honor, 220 ms.", "Superheavy p-block.", "[Rn] 5f¹⁴ 6d¹⁰ 7s² 7p³", 400.0, 1100.0, "Solid", "Oganessian et al. (2003)", 13.5, "synthetic"),
    Element("Lv", "Livermorium", 116, 293.0, "Post-Transition Metal", categoryColor("Post-Transition Metal"), "Livermore honor, 60 ms.", "Few atoms made.", "[Rn] 5f¹⁴ 6d¹⁰ 7s² 7p⁴", 364.0, 762.0, "Solid", "Oganessian et al. (2000)", 12.9, "synthetic"),
    Element("Ts", "Tennessine", 117, 294.0, "Halogen", categoryColor("Halogen"), "Tennessee honor, 78 ms.", "Second-heaviest halogen.", "[Rn] 5f¹⁴ 6d¹⁰ 7s² 7p⁵", 450.0, 610.0, "Solid", "Oganessian et al. (2010)", 7.2, "synthetic"),
    Element("Og", "Oganesson", 118, 294.0, "Noble Gas", categoryColor("Noble Gas"), "Heaviest element, semiconductor predicted.", "Honors Yuri Oganessian (living).", "[Rn] 5f¹⁴ 6d¹⁰ 7s² 7p⁶", 52.0, 320.0, "Gas", "Oganessian et al. (2002)", 5.0, "synthetic")
)
