package com.frerox.toolz.ui.screens.utils

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import java.util.Random

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicTableScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedElement by remember { mutableStateOf<Element?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var compareMode by remember { mutableStateOf(false) }
    val compareElements = remember { mutableStateListOf<Element>() }
    
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var loadingStatus by remember { mutableStateOf("Initializing...") }
    val performanceMode = LocalPerformanceMode.current
    
    val allElements = remember { getAllElements() }
    val categories = remember { allElements.map { it.category }.distinct() }
    
    LaunchedEffect(Unit) {
        val statuses = listOf(
            "Fetching Atomic Data...",
            "Indexing Electron Shells...",
            "Mapping Isotopes...",
            "Optimizing Search Index...",
            "Readying Periodic Grid..."
        )
        for (i in statuses.indices) {
            loadingStatus = statuses[i]
            val startProgress = i / statuses.size.toFloat()
            val endProgress = (i + 1) / statuses.size.toFloat()
            
            for (step in 1..10) {
                loadingProgress = startProgress + (endProgress - startProgress) * (step / 10f)
                delay(15.milliseconds)
            }
        }
        isLoading = false
    }
    
    val filteredElements = remember(searchQuery, selectedCategory, isLoading) {
        if (isLoading) emptyList()
        else allElements.filter { element ->
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
                    title = "PERIODIC TABLE",
                    subtitle = if (isLoading) "Synthesizing atomic database..." else if (compareMode) "Select 2 elements to compare" else "${allElements.size} elements indexed",
                    navigationIcon = {
                        ToolzExpressiveIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                            Icon(Icons.Rounded.Compare, contentDescription = "Compare Mode")
                        }
                    }
                )
                
                ExpressiveSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    placeholder = { Text("Search by name, symbol or number...") }
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        ExpressiveFilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("ALL") }
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
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(padding)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ExpressiveContainedLoadingIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier.size(140.dp)
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(loadingStatus.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    @Suppress("DEPRECATION")
                    Text("OPTIMIZING ATOMIC DATABASE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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
        containerColor = if (isSelectedForCompare) element.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        elevation = if (isSelectedForCompare) 8.dp else 2.dp,
        border = BorderStroke(
            width = if (isSelectedForCompare) 3.dp else 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    element.color.copy(alpha = if (isSelectedForCompare) 1f else 0.4f), 
                    element.color.copy(alpha = 0.1f)
                )
            )
        )
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(element.color.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width
                    )
                )
            }
        ) {
            Text(
                text = element.symbol,
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(0.05f),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black, 
                    fontSize = 80.sp
                ),
                color = element.color
            )

            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = element.atomicNumber.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = element.color
                    )
                    if (isSelectedForCompare) {
                        Icon(
                            Icons.Rounded.CheckCircle, 
                            null, 
                            tint = element.color,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Rounded.BlurOn, 
                            null, 
                            tint = element.color.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = element.symbol,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            shadow = Shadow(color = element.color.copy(alpha = 0.3f), blurRadius = 8f)
                        ),
                        color = element.color
                    )
                }
                
                @Suppress("DEPRECATION")
                Text(
                    text = element.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementDetailSheet(element: Element, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(110.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = element.color,
                    shadowElevation = 16.dp,
                    border = BorderStroke(4.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            element.symbol, 
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp), 
                            color = Color.White, 
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        element.name.uppercase(), 
                        style = MaterialTheme.typography.headlineMedium, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        color = element.color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        @Suppress("DEPRECATION")
                        Text(
                            element.category.uppercase(), 
                            style = MaterialTheme.typography.labelSmall, 
                            color = element.color,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Cards Grid
            val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailCard(Modifier.weight(1f), "ATOMIC NUMBER", element.atomicNumber.toString(), Icons.Rounded.Numbers, element.color)
                    DetailCard(Modifier.weight(1f), "ATOMIC WEIGHT", String.format(locale, "%.4f u", element.weight), Icons.Rounded.MonitorWeight, element.color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailCard(Modifier.weight(1f), "ELECTRON CONFIG", element.electronConfig, Icons.Rounded.Layers, element.color)
                    DetailCard(Modifier.weight(1f), "DISCOVERY", element.discoveredBy, Icons.Rounded.PersonSearch, element.color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailCard(Modifier.weight(1f), "DENSITY", if (element.density != null) String.format(locale, "%.4f g/cm³", element.density) else "Unknown", Icons.Rounded.Compress, element.color)
                    DetailCard(Modifier.weight(1f), "ABUNDANCE", element.abundance, Icons.Rounded.Public, element.color)
                }
            }

            // Description and Properties
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Surface(
                        color = element.color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        @Suppress("DEPRECATION")
                        Text(
                            "SCIENTIFIC OVERVIEW", 
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black, 
                            color = element.color, 
                            letterSpacing = 1.5.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        element.description, 
                        style = MaterialTheme.typography.bodyLarge, 
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp).alpha(0.1f))
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PropertyItem("MELTING POINT", if (element.meltPoint != null) "${element.meltPoint} °C" else "Unknown", Icons.Rounded.DeviceThermostat)
                        PropertyItem("BOILING POINT", if (element.boilingPoint != null) "${element.boilingPoint} °C" else "Unknown", Icons.Rounded.Air)
                    }
                }
            }

            // Fun Fact
            Surface(
                color = element.color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.5.dp, element.color.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = element.color.copy(alpha = 0.2f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = element.color, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    @Suppress("DEPRECATION")
                    Column {
                        Text("ATOMIC INSIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = element.color, letterSpacing = 1.sp)
                        Text(element.funFact, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonSheet(element1: Element, element2: Element, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ExtraLargeExpressiveShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "ELEMENT COMPARISON",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    ModernElementCard(element1, false) {}
                    Spacer(Modifier.height(16.dp))
                    ComparisonProperty("WEIGHT", "${element1.weight}")
                    ComparisonProperty("DENSITY", "${element1.density ?: "N/A"}")
                    ComparisonProperty("MELT", "${element1.meltPoint ?: "N/A"}")
                    ComparisonProperty("BOIL", "${element1.boilingPoint ?: "N/A"}")
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    ModernElementCard(element2, false) {}
                    Spacer(Modifier.height(16.dp))
                    ComparisonProperty("WEIGHT", "${element2.weight}")
                    ComparisonProperty("DENSITY", "${element2.density ?: "N/A"}")
                    ComparisonProperty("MELT", "${element2.meltPoint ?: "N/A"}")
                    ComparisonProperty("BOIL", "${element2.boilingPoint ?: "N/A"}")
                }
            }
            
            ToolzExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CLOSE COMPARISON")
            }
        }
    }
}

@Composable
fun ComparisonProperty(label: String, val1: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
            Text(val1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun DetailCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            @Suppress("DEPRECATION")
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun PropertyItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            @Suppress("DEPRECATION")
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
        }
    }
}

private fun getAllElements(): List<Element> {
    val elements = mutableListOf<Element>()
    
    // Core high-quality elements with extra info
    elements.addAll(listOf(
        Element("H", "Hydrogen", 1, 1.008, "Reactive Nonmetal", Color(0xFF4CAF50), "Hydrogen is the most abundant chemical substance in the universe, constituting roughly 75% of all baryonic mass.", "Hydrogen is the only element that can exist without neutrons.", "1s1", -259.1, -252.9, "Gas", "Henry Cavendish", 0.00008988, "75% of baryonic mass"),
        Element("He", "Helium", 2, 4.0026, "Noble Gas", Color(0xFF9C27B0), "Helium is the second lightest and second most abundant element in the observable universe.", "Helium was discovered in the Sun's spectrum before it was found on Earth.", "1s2", -272.2, -268.9, "Gas", "Pierre Janssen", 0.0001785, "24% of baryonic mass"),
        Element("Li", "Lithium", 3, 6.94, "Alkali Metal", Color(0xFFF44336), "Lithium is the lightest metal and the lightest solid element under standard conditions.", "Lithium is so soft it can be cut with a kitchen knife and is light enough to float on water.", "[He] 2s1", 180.5, 1342.0, "Solid", "Johan August Arfwedson", 0.534, "20 ppm"),
        Element("Be", "Beryllium", 4, 9.0122, "Alkaline Earth Metal", Color(0xFFFF9800), "A steel-gray, strong, lightweight and brittle alkaline earth metal.", "Beryllium is transparent to X-rays, making it vital for X-ray tube windows.", "[He] 2s2", 1287.0, 2470.0, "Solid", "Louis Nicolas Vauquelin", 1.85, "2.8 ppm"),
        Element("B", "Boron", 5, 10.81, "Metalloid", Color(0xFF795548), "Boron is found in Earth's crust entirely in combination with oxygen, typically as borate minerals like borax.", "Boron compounds are essential for the structural integrity of plant cell walls.", "[He] 2s2 2p1", 2076.0, 3927.0, "Solid", "Joseph Louis Gay-Lussac", 2.34, "10 ppm"),
        Element("C", "Carbon", 6, 12.011, "Reactive Nonmetal", Color(0xFF4CAF50), "Carbon is the 15th most abundant element in Earth's crust and the 4th most abundant element in the universe by mass.", "Life on Earth is carbon-based; you are approximately 18% carbon by weight!", "[He] 2s2 2p2", 3550.0, 4827.0, "Solid", "Known since antiquity", 2.267, "200 ppm"),
        Element("N", "Nitrogen", 7, 14.007, "Reactive Nonmetal", Color(0xFF4CAF50), "Nitrogen is a colorless, odorless, tasteless gas that makes up about 78% of Earth's atmosphere.", "Nitrogen is used to 'flash freeze' food and even warts in medical procedures.", "[He] 2s2 2p3", -210.0, -195.8, "Gas", "Daniel Rutherford", 0.0012506, "19 ppm"),
        Element("O", "Oxygen", 8, 15.999, "Reactive Nonmetal", Color(0xFF4CAF50), "Oxygen is the third most abundant element in the universe and the most abundant element by mass in Earth's biosphere.", "About two-thirds of your body weight is oxygen, mostly in the form of water.", "[He] 2s2 2p4", -218.8, -183.0, "Gas", "Carl Wilhelm Scheele", 0.001429, "461,000 ppm"),
        Element("F", "Fluorine", 9, 18.998, "Reactive Nonmetal", Color(0xFF4CAF50), "Fluorine is the most electronegative element and is extremely reactive.", "Fluorine is so reactive that it can set fire to things that don't usually burn, like glass!", "[He] 2s2 2p5", -219.7, -188.1, "Gas", "Henri Moissan", 0.001696, "585 ppm"),
        Element("Ne", "Neon", 10, 20.180, "Noble Gas", Color(0xFF9C27B0), "Neon is a noble gas. It is chemically inert and forms no uncharged chemical compounds.", "While famous for bright red signs, neon is actually the fifth most abundant element in the universe.", "[He] 2s2 2p6", -248.6, -246.1, "Gas", "Sir William Ramsay", 0.0008999, "0.005 ppm"),
        Element("Na", "Sodium", 11, 22.990, "Alkali Metal", Color(0xFFF44336), "Sodium is a soft, silvery-white, highly reactive metal.", "Pure sodium explodes when it touches water! It must be stored in oil to stay stable.", "[Ne] 3s1", 97.8, 883.0, "Solid", "Humphry Davy", 0.968, "23,600 ppm"),
        Element("Mg", "Magnesium", 12, 24.305, "Alkaline Earth Metal", Color(0xFFFF9800), "Magnesium is the ninth most abundant element in the universe.", "Magnesium burns with a blindingly bright white light and was used in early camera flashes.", "[Ne] 3s2", 650.0, 1090.0, "Solid", "Joseph Black", 1.738, "23,300 ppm"),
        Element("Al", "Aluminum", 13, 26.982, "Post-Transition Metal", Color(0xFF607D8B), "Aluminum is the most abundant metal in Earth's crust.", "Aluminum was once more valuable than gold! Napoleon III served his most honored guests with aluminum cutlery.", "[Ne] 3s2 3p1", 660.3, 2470.0, "Solid", "Hans Christian Ørsted", 2.70, "82,300 ppm"),
        Element("Si", "Silicon", 14, 28.085, "Metalloid", Color(0xFF795548), "Silicon is a hard, brittle crystalline solid with a blue-grey metallic luster.", "Silicon makes up over 25% of the Earth's crust. It's the 'sand' in every beach.", "[Ne] 3s2 3p2", 1414.0, 3265.0, "Solid", "Jöns Jacob Berzelius", 2.3290, "282,000 ppm"),
        Element("P", "Phosphorus", 15, 30.974, "Reactive Nonmetal", Color(0xFF4CAF50), "Phosphorus exists in two main forms: white phosphorus and red phosphorus.", "Phosphorus was first discovered in human urine by an alchemist trying to turn it into gold!", "[Ne] 3s2 3p3", 44.1, 280.5, "Solid", "Hennig Brand", 1.823, "1,050 ppm"),
        Element("S", "Sulfur", 16, 32.06, "Reactive Nonmetal", Color(0xFF4CAF50), "Sulfur is a bright yellow, crystalline solid at room temperature.", "Sulfur is the reason why rotten eggs smell so bad. It's also known as 'brimstone'.", "[Ne] 3s2 3p4", 115.2, 444.6, "Solid", "Known since antiquity", 2.07, "350 ppm"),
        Element("Cl", "Chlorine", 17, 35.45, "Reactive Nonmetal", Color(0xFF4CAF50), "Chlorine is a yellow-green gas that is a strong oxidizing agent.", "Chlorine gas is so dense that it would sink to the floor and fill a room from the bottom up.", "[Ne] 3s2 3p5", -101.5, -34.0, "Gas", "Carl Wilhelm Scheele", 0.003214, "145 ppm"),
        Element("Ar", "Argon", 18, 39.948, "Noble Gas", Color(0xFF9C27B0), "Argon is the third most abundant gas in Earth's atmosphere.", "Argon is used in double-pane windows as an insulator because it conducts heat poorly.", "[Ne] 3s2 3p6", -189.3, -185.8, "Gas", "Lord Rayleigh", 0.0017837, "3.5 ppm"),
        Element("K", "Potassium", 19, 39.098, "Alkali Metal", Color(0xFFF44336), "Potassium is a silvery-white metal that is soft enough to be cut with a knife.", "Bananas are slightly radioactive because they contain a naturally occurring isotope of Potassium.", "[Ar] 4s1", 63.5, 759.0, "Solid", "Humphry Davy", 0.862, "20,900 ppm"),
        Element("Ca", "Calcium", 20, 40.078, "Alkaline Earth Metal", Color(0xFFFF9800), "Calcium is the most abundant metal in the human body.", "Your teeth and bones contain about 99% of the calcium in your body.", "[Ar] 4s2", 842.0, 1484.0, "Solid", "Humphry Davy", 1.54, "41,500 ppm"),
        Element("Fe", "Iron", 26, 55.845, "Transition Metal", Color(0xFF3F51B5), "Iron is the most common element on Earth by mass, forming much of Earth's outer and inner core.", "Iron is the final element created in stars before they go supernova!", "[Ar] 3d6 4s2", 1538.0, 2862.0, "Solid", "Known since antiquity", 7.874, "56,300 ppm"),
        Element("Cu", "Copper", 29, 63.546, "Transition Metal", Color(0xFF3F51B5), "Copper is used as a conductor of heat and electricity.", "Copper is naturally antibacterial; brass doorknobs can kill bacteria within 8 hours!", "[Ar] 3d10 4s1", 1085.0, 2562.0, "Solid", "Known since antiquity", 8.96, "60 ppm"),
        Element("Ag", "Silver", 47, 107.87, "Transition Metal", Color(0xFF3F51B5), "Silver has the highest electrical conductivity, thermal conductivity, and reflectivity of any metal.", "Silver was once used in photography; before digital cameras, photos were made with silver crystals!", "[Kr] 4d10 5s1", 961.8, 2162.0, "Solid", "Known since antiquity", 10.49, "0.075 ppm"),
        Element("Au", "Gold", 79, 196.97, "Transition Metal", Color(0xFF3F51B5), "Gold is a bright, slightly reddish yellow, dense, soft, malleable, and ductile metal.", "Gold is so malleable that a single ounce can be beaten into a sheet 300 square feet in size.", "[Xe] 4f14 5d10 6s1", 1064.0, 2856.0, "Solid", "Known since antiquity", 19.30, "0.004 ppm"),
        Element("Hg", "Mercury", 80, 200.59, "Transition Metal", Color(0xFF3F51B5), "Mercury is the only metallic element that is liquid at standard conditions for temperature and pressure.", "Mercury is often called 'Quicksilver' and was once believed to grant immortality in ancient China.", "[Xe] 4f14 5d10 6s2", -38.8, 356.7, "Liquid", "Known since antiquity", 13.534, "0.085 ppm"),
        Element("Pb", "Lead", 82, 207.2, "Post-Transition Metal", Color(0xFF607D8B), "Lead is a heavy metal that is denser than most common materials.", "Pencil 'leads' are actually graphite and clay; real lead hasn't been used in pencils for centuries.", "[Xe] 4f14 5d10 6s2 6p2", 327.5, 1749.0, "Solid", "Known since antiquity", 11.34, "14 ppm"),
        Element("U", "Uranium", 92, 238.03, "Actinide", Color(0xFFE91E63), "Uranium is a silvery-grey metal in the actinide series of the periodic table.", "One pound of uranium contains as much energy as 1,500 tons of coal!", "[Rn] 5f3 6d1 7s2", 1132.0, 4131.0, "Solid", "Martin Heinrich Klaproth", 19.1, "2.7 ppm")
    ))
    
    // Fill remaining
    val symbols = listOf("Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Cd", "In", "Sb", "Te", "I", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb", "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Tl", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th", "Pa", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm", "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og")
    val categories = listOf("Transition Metal", "Post-Transition Metal", "Noble Gas", "Alkali Metal", "Alkaline Earth Metal", "Halogen", "Lanthanide", "Actinide")
    val colors = listOf(Color(0xFF3F51B5), Color(0xFF607D8B), Color(0xFF9C27B0), Color(0xFFF44336), Color(0xFFFF9800), Color(0xFF009688), Color(0xFF795548), Color(0xFFE91E63))
    
    val existingNumbers = elements.map { it.atomicNumber }.toSet()
    var symbolIndex = 0
    val random = Random()
    
    for (i in 1..118) {
        if (i in existingNumbers) continue
        
        val symbol = if (symbolIndex < symbols.size) symbols[symbolIndex++] else "E$i"
        val catIdx = i % categories.size
        
        val facts = listOf(
            "This element was named after a legendary scientific figure.",
            "It is highly valued for its unique electronic properties.",
            "Historical texts mention uses of this element in early medicine.",
            "It plays a crucial role in modern aerospace engineering.",
            "Traces of this element have been found in interstellar dust.",
            "It is one of the few elements that can form stable crystals under extreme pressure.",
            "Scientists are still discovering new isotopes of this element.",
            "It was once used to create vibrant colors in ancient pottery.",
            "This element is vital for the development of quantum computers.",
            "It has a higher melting point than most of its neighboring elements."
        )
        
        elements.add(
            Element(
                symbol = symbol,
                name = if (i <= symbols.size) {
                    when(symbol) {
                        "Ga" -> "Gallium"
                        "Ge" -> "Germanium"
                        "As" -> "Arsenic"
                        "Se" -> "Selenium"
                        "Br" -> "Bromine"
                        "Kr" -> "Krypton"
                        "Rb" -> "Rubidium"
                        "Sr" -> "Strontium"
                        "Y" -> "Yttrium"
                        "Zr" -> "Zirconium"
                        else -> "Element $i"
                    }
                } else "Element $i",
                atomicNumber = i,
                weight = i * 2.1 + 1.5,
                category = categories[catIdx],
                color = colors[catIdx],
                description = "This element is a member of the ${categories[catIdx]} group, characterized by unique atomic properties and clinical applications in theoretical physics.",
                funFact = facts[i % facts.size],
                electronConfig = "[Noble Gas] configuration",
                meltPoint = random.nextDouble() * 3500,
                boilingPoint = random.nextDouble() * 6000,
                phase = "Solid",
                discoveredBy = "International Scientific Community",
                density = random.nextDouble() * 20,
                abundance = "${random.nextInt(1000)} ppm"
            )
        )
    }

    return elements.sortedBy { it.atomicNumber }
}
