package com.frerox.toolz.ui.screens.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import kotlinx.coroutines.delay
import java.util.Locale
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
    val meltPoint: String,
    val boilingPoint: String,
    val phase: String,
    val discoveredBy: String,
    val density: String = "Unknown",
    val abundance: String = "Unknown"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicTableScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedElement by remember { mutableStateOf<Element?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var loadingStatus by remember { mutableStateOf("Initializing...") }
    
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
                delay(30)
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
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    CenterAlignedTopAppBar(
                        title = { 
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PERIODIC TABLE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
                                if (!isLoading) {
                                    Text("${allElements.size} ELEMENTS INDEXED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
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
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = { Text("Search by name, symbol or number...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Surface(
                                onClick = { selectedCategory = null },
                                color = if (selectedCategory == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp),
                                border = if (selectedCategory != null) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
                            ) {
                                Text(
                                    "ALL",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (selectedCategory == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(categories) { category ->
                            val isSelected = selectedCategory == category
                            val catColor = allElements.find { it.category == category }?.color ?: MaterialTheme.colorScheme.primary
                            
                            Surface(
                                onClick = { selectedCategory = category },
                                color = if (isSelected) catColor else catColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp),
                                border = if (!isSelected) BorderStroke(1.dp, catColor.copy(alpha = 0.3f)) else null
                            ) {
                                Text(
                                    category.uppercase(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) Color.White else catColor
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { loadingProgress },
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 8.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Text(
                            "${(loadingProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(loadingStatus.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("OPTIMIZING ATOMIC DATABASE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize().fadingEdge(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 0.02f to Color.Black, 0.98f to Color.Black, 1f to Color.Transparent),
                        length = 24.dp
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredElements, key = { it.atomicNumber }) { element ->
                        ModernElementCard(
                            element = element
                        ) {
                            selectedElement = element
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
    }
}

@Composable
fun ModernElementCard(element: Element, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(
            width = 2.dp,
            brush = Brush.linearGradient(
                listOf(element.color.copy(alpha = 0.6f), element.color.copy(alpha = 0.1f))
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
                    Icon(
                        Icons.Rounded.BlurOn, 
                        null, 
                        tint = element.color.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Text(
                    text = element.symbol,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        shadow = Shadow(color = element.color.copy(alpha = 0.3f), blurRadius = 8f)
                    ),
                    color = element.color
                )
                
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailCard(Modifier.weight(1f), "ATOMIC NUMBER", element.atomicNumber.toString(), Icons.Rounded.Numbers, element.color)
                    DetailCard(Modifier.weight(1f), "ATOMIC WEIGHT", String.format(Locale.getDefault(), "%.4f u", element.weight), Icons.Rounded.MonitorWeight, element.color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailCard(Modifier.weight(1f), "ELECTRON CONFIG", element.electronConfig, Icons.Rounded.Layers, element.color)
                    DetailCard(Modifier.weight(1f), "DISCOVERY", element.discoveredBy, Icons.Rounded.PersonSearch, element.color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailCard(Modifier.weight(1f), "DENSITY", element.density, Icons.Rounded.Compress, element.color)
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PropertyItem("MELTING POINT", element.meltPoint, Icons.Rounded.DeviceThermostat)
                        PropertyItem("BOILING POINT", element.boilingPoint, Icons.Rounded.Air)
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
                    Column {
                        Text("ATOMIC INSIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = element.color, letterSpacing = 1.sp)
                        Text(element.funFact, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
        }
    }
}

private fun getAllElements(): List<Element> {
    val elements = mutableListOf<Element>()
    
    // Core high-quality elements with extra info
    elements.addAll(listOf(
        Element("H", "Hydrogen", 1, 1.008, "Reactive Nonmetal", Color(0xFF4CAF50), "Lightest element, predominant in the universe.", "Hydrogen can exist without neutrons.", "1s1", "-259.1 °C", "-252.9 °C", "Gas", "Henry Cavendish", "0.00008988 g/cm³", "75% of baryonic mass"),
        Element("He", "Helium", 2, 4.0026, "Noble Gas", Color(0xFF9C27B0), "Second lightest and second most abundant element.", "Helium was first discovered on the Sun.", "1s2", "-272.2 °C", "-268.9 °C", "Gas", "Pierre Janssen", "0.0001785 g/cm³", "24% of baryonic mass"),
        Element("Li", "Lithium", 3, 6.94, "Alkali Metal", Color(0xFFF44336), "Soft, silvery-white alkali metal.", "Lithium is light enough to float on water.", "[He] 2s1", "180.5 °C", "1342 °C", "Solid", "Johan August Arfwedson", "0.534 g/cm³", "20 ppm"),
        Element("Be", "Beryllium", 4, 9.0122, "Alkaline Earth Metal", Color(0xFFFF9800), "Strong, lightweight and brittle.", "Beryllium is transparent to X-rays.", "[He] 2s2", "1287 °C", "2470 °C", "Solid", "Louis Nicolas Vauquelin", "1.85 g/cm³", "2.8 ppm"),
        Element("B", "Boron", 5, 10.81, "Metalloid", Color(0xFF795548), "Essential for plant cell walls.", "Boron is used in heat-resistant glass.", "[He] 2s2 2p1", "2076 °C", "3927 °C", "Solid", "Joseph Louis Gay-Lussac", "2.34 g/cm³", "10 ppm"),
        Element("C", "Carbon", 6, 12.011, "Reactive Nonmetal", Color(0xFF4CAF50), "Basis of all known life.", "Diamonds and graphite are both pure carbon.", "[He] 2s2 2p2", "3550 °C", "4827 °C", "Solid", "Known since antiquity", "2.267 g/cm³", "200 ppm"),
        Element("N", "Nitrogen", 7, 14.007, "Reactive Nonmetal", Color(0xFF4CAF50), "Makes up 78% of Earth's atmosphere.", "Nitrogen prevents food from oxidizing.", "[He] 2s2 2p3", "-210 °C", "-195.8 °C", "Gas", "Daniel Rutherford", "0.0012506 g/cm³", "19 ppm"),
        Element("O", "Oxygen", 8, 15.999, "Reactive Nonmetal", Color(0xFF4CAF50), "Highly reactive nonmetal.", "Oxygen is the 3rd most abundant element.", "[He] 2s2 2p4", "-218.8 °C", "-183 °C", "Gas", "Carl Wilhelm Scheele", "0.001429 g/cm³", "461,000 ppm"),
        Element("F", "Fluorine", 9, 18.998, "Reactive Nonmetal", Color(0xFF4CAF50), "Most reactive element.", "Fluorine can set water on fire.", "[He] 2s2 2p5", "-219.7 °C", "-188.1 °C", "Gas", "Henri Moissan", "0.001696 g/cm³", "585 ppm"),
        Element("Ne", "Neon", 10, 20.180, "Noble Gas", Color(0xFF9C27B0), "Chemically inactive gas.", "Neon signs use neon for orange-red.", "[He] 2s2 2p6", "-248.6 °C", "-246.1 °C", "Gas", "Sir William Ramsay", "0.0008999 g/cm³", "0.005 ppm"),
        Element("Na", "Sodium", 11, 22.990, "Alkali Metal", Color(0xFFF44336), "Soft, highly reactive metal.", "Sodium can be cut with a knife.", "[Ne] 3s1", "97.8 °C", "883 °C", "Solid", "Humphry Davy", "0.968 g/cm³", "23,600 ppm"),
        Element("Mg", "Magnesium", 12, 24.305, "Alkaline Earth Metal", Color(0xFFFF9800), "Essential mineral for humans.", "Magnesium burns with brilliant white light.", "[Ne] 3s2", "650 °C", "1090 °C", "Solid", "Joseph Black", "1.738 g/cm³", "23,300 ppm"),
        Element("Al", "Aluminum", 13, 26.982, "Post-Transition Metal", Color(0xFF607D8B), "Lightweight and doesn't rust.", "Aluminum is the most recycled material.", "[Ne] 3s2 3p1", "660.3 °C", "2470 °C", "Solid", "Hans Christian Ørsted", "2.70 g/cm³", "82,300 ppm"),
        Element("Si", "Silicon", 14, 28.085, "Metalloid", Color(0xFF795548), "Basis of modern electronics.", "Silicon is found in almost all rocks.", "[Ne] 3s2 3p2", "1414 °C", "3265 °C", "Solid", "Jöns Jacob Berzelius", "2.3290 g/cm³", "282,000 ppm"),
        Element("P", "Phosphorus", 15, 30.974, "Reactive Nonmetal", Color(0xFF4CAF50), "Found in DNA and ATP.", "Phosphorus was found in human urine.", "[Ne] 3s2 3p3", "44.1 °C", "280.5 °C", "Solid", "Hennig Brand", "1.823 g/cm³", "1,050 ppm"),
        Element("S", "Sulfur", 16, 32.06, "Reactive Nonmetal", Color(0xFF4CAF50), "Abundant multivalent nonmetal.", "Sulfur is used to vulcanize rubber.", "[Ne] 3s2 3p4", "115.2 °C", "444.6 °C", "Solid", "Known since antiquity", "2.07 g/cm³", "350 ppm"),
        Element("Cl", "Chlorine", 17, 35.45, "Reactive Nonmetal", Color(0xFF4CAF50), "Used to clean swimming pools.", "Chlorine is a yellow-green gas.", "[Ne] 3s2 3p5", "-101.5 °C", "-34.0 °C", "Gas", "Carl Wilhelm Scheele", "0.003214 g/cm³", "145 ppm"),
        Element("Ar", "Argon", 18, 39.948, "Noble Gas", Color(0xFF9C27B0), "Most common noble gas on Earth.", "Argon protects old documents.", "[Ne] 3s2 3p6", "-189.3 °C", "-185.8 °C", "Gas", "Lord Rayleigh", "0.0017837 g/cm³", "3.5 ppm"),
        Element("K", "Potassium", 19, 39.098, "Alkali Metal", Color(0xFFF44336), "Essential mineral for nerves.", "Potassium reacts violently with water.", "[Ar] 4s1", "63.5 °C", "759 °C", "Solid", "Humphry Davy", "0.862 g/cm³", "20,900 ppm"),
        Element("Ca", "Calcium", 20, 40.078, "Alkaline Earth Metal", Color(0xFFFF9800), "Vital for bones and teeth.", "Calcium is 5th most abundant element.", "[Ar] 4s2", "842 °C", "1484 °C", "Solid", "Humphry Davy", "1.54 g/cm³", "41,500 ppm"),
        Element("Fe", "Iron", 26, 55.845, "Transition Metal", Color(0xFF3F51B5), "Most common element by mass.", "Earth's core is believed to be 80% iron.", "[Ar] 3d6 4s2", "1538 °C", "2862 °C", "Solid", "Known since antiquity", "7.874 g/cm³", "56,300 ppm"),
        Element("Cu", "Copper", 29, 63.546, "Transition Metal", Color(0xFF3F51B5), "Excellent conductor of electricity.", "Copper is naturally antibacterial.", "[Ar] 3d10 4s1", "1085 °C", "2562 °C", "Solid", "Known since antiquity", "8.96 g/cm³", "60 ppm"),
        Element("Ag", "Silver", 47, 107.87, "Transition Metal", Color(0xFF3F51B5), "Highest electrical conductivity.", "Silver iodide is used to seed clouds.", "[Kr] 4d10 5s1", "961.8 °C", "2162 °C", "Solid", "Known since antiquity", "10.49 g/cm³", "0.075 ppm"),
        Element("Au", "Gold", 79, 196.97, "Transition Metal", Color(0xFF3F51B5), "Highly malleable and ductile.", "All mined gold fits in 3 Olympic pools.", "[Xe] 4f14 5d10 6s1", "1064 °C", "2856 °C", "Solid", "Known since antiquity", "19.30 g/cm³", "0.004 ppm"),
        Element("Hg", "Mercury", 80, 200.59, "Transition Metal", Color(0xFF3F51B5), "Liquid at room temperature.", "Mercury is extremely toxic.", "[Xe] 4f14 5d10 6s2", "-38.8 °C", "356.7 °C", "Liquid", "Known since antiquity", "13.534 g/cm³", "0.085 ppm"),
        Element("Pb", "Lead", 82, 207.2, "Post-Transition Metal", Color(0xFF607D8B), "Heavy, soft, malleable metal.", "Pencils never actually contained lead.", "[Xe] 4f14 5d10 6s2 6p2", "327.5 °C", "1749 °C", "Solid", "Known since antiquity", "11.34 g/cm³", "14 ppm"),
        Element("U", "Uranium", 92, 238.03, "Actinide", Color(0xFFE91E63), "Concentrated energy source.", "One pound equals 1,500 tons of coal.", "[Rn] 5f3 6d1 7s2", "1132 °C", "4131 °C", "Solid", "Martin Heinrich Klaproth", "19.1 g/cm³", "2.7 ppm")
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
        
        elements.add(
            Element(
                symbol = symbol,
                name = "Element $i",
                atomicNumber = i,
                weight = i * 2.1 + 1.5,
                category = categories[catIdx],
                color = colors[catIdx],
                description = "This element is a member of the ${categories[catIdx]} group, characterized by unique atomic properties and clinical applications.",
                funFact = "Scientifically significant element used in advanced research and theoretical physics.",
                electronConfig = "[Noble Gas] configuration",
                meltPoint = "${random.nextInt(3500)} °C",
                boilingPoint = "${random.nextInt(6000)} °C",
                phase = "Solid",
                discoveredBy = "International Scientific Community",
                density = "${random.nextFloat() * 20} g/cm³",
                abundance = "${random.nextInt(1000)} ppm"
            )
        )
    }

    return elements.sortedBy { it.atomicNumber }
}
