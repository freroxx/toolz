package com.frerox.toolz.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import java.util.*

data class ToolCategory(
    val title: String,
    val items: List<ToolItem>
)

data class ToolItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val description: String,
    val color: Color = Color.Unspecified
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    musicViewModel: MusicPlayerViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val categories = remember { getCategories() }
    val musicState by musicViewModel.uiState.collectAsState()
    
    val filteredCategories = categories.map { category ->
        category.copy(items = category.items.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.description.contains(searchQuery, ignoreCase = true) 
        })
    }.filter { it.items.isNotEmpty() }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                WelcomeHeader(onSettingsClick = { onNavigate(Screen.Settings.route) })
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 30+ tools...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { 
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
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
                        length = 16.dp
                    ),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                filteredCategories.forEach { category ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                            letterSpacing = 1.sp
                        )
                    }
                    items(category.items) { tool ->
                        ImprovedToolCard(tool = tool, onClick = { onNavigate(tool.route) })
                    }
                }
                
                if (filteredCategories.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptySearchState(searchQuery)
                    }
                }
            }

            // Now Playing Pill
            AnimatedVisibility(
                visible = musicState.currentTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                musicState.currentTrack?.let { track ->
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .height(72.dp)
                            .shadow(12.dp, RoundedCornerShape(36.dp))
                            .bouncyClick { onNavigate(Screen.MusicPlayer.route) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                AsyncImage(
                                    model = track.thumbnailUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    track.artist ?: "Unknown Artist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            IconButton(
                                onClick = { musicViewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    if (musicState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeHeader(onSettingsClick: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Toolz Pro",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
fun ImprovedToolCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Column {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No tools found for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            "Try a different keyword",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        )
    }
}

private fun getCategories() = listOf(
    ToolCategory(
        "FAVORITES",
        listOf(
            ToolItem("Music Player", Icons.Rounded.MusicNote, Screen.MusicPlayer.route, "Audio library"),
            ToolItem("Step Counter", Icons.AutoMirrored.Rounded.DirectionsRun, Screen.StepCounter.route, "Fitness tracker"),
            ToolItem("Notepad", Icons.Rounded.NoteAlt, Screen.Notepad.route, "Quick notes"),
            ToolItem("Periodic Table", Icons.Rounded.Science, Screen.PeriodicTable.route, "Atomic data")
        )
    ),
    ToolCategory(
        "TIME & FOCUS",
        listOf(
            ToolItem("Timer", Icons.Rounded.Timer, Screen.Timer.route, "Countdown"),
            ToolItem("Stopwatch", Icons.Rounded.History, Screen.Stopwatch.route, "Laps"),
            ToolItem("Pomodoro", Icons.Rounded.AvTimer, Screen.Pomodoro.route, "Productivity"),
            ToolItem("World Clock", Icons.Rounded.Public, Screen.WorldClock.route, "Time zones")
        )
    ),
    ToolCategory(
        "MEDIA & OPTICS",
        listOf(
            ToolItem("Scanner", Icons.Rounded.QrCodeScanner, Screen.Scanner.route, "QR / Barcode"),
            ToolItem("Magnifier", Icons.Rounded.ZoomIn, Screen.Magnifier.route, "Camera zoom"),
            ToolItem("Color Picker", Icons.Rounded.Colorize, Screen.ColorPicker.route, "Hex code"),
            ToolItem("Voice Recorder", Icons.Rounded.Mic, Screen.VoiceRecorder.route, "Audio memo")
        )
    ),
    ToolCategory(
        "UTILITIES",
        listOf(
            ToolItem("Compass", Icons.Rounded.Explore, Screen.Compass.route, "Navigation"),
            ToolItem("Bubble Level", Icons.Rounded.Architecture, Screen.BubbleLevel.route, "Leveling"),
            ToolItem("Speedometer", Icons.Rounded.Speed, Screen.Speedometer.route, "GPS Speed"),
            ToolItem("Altimeter", Icons.Rounded.FilterHdr, Screen.Altimeter.route, "Altitude")
        )
    ),
    ToolCategory(
        "CALCULATORS",
        listOf(
            ToolItem("Calculator", Icons.Rounded.Calculate, Screen.Calculator.route, "Math"),
            ToolItem("Unit Converter", Icons.Rounded.SyncAlt, Screen.UnitConverter.route, "Units"),
            ToolItem("Tip Calc", Icons.AutoMirrored.Rounded.ReceiptLong, Screen.TipCalculator.route, "Split"),
            ToolItem("BMI Calc", Icons.Rounded.MonitorWeight, Screen.BmiCalculator.route, "Health")
        )
    ),
    ToolCategory(
        "SYSTEM",
        listOf(
            ToolItem("Battery Info", Icons.Rounded.BatteryChargingFull, Screen.BatteryInfo.route, "Status"),
            ToolItem("Password Gen", Icons.Rounded.Password, Screen.PasswordGenerator.route, "Security"),
            ToolItem("Ruler", Icons.Rounded.Straighten, Screen.Ruler.route, "Measure"),
            ToolItem("Flashlight", Icons.Rounded.FlashlightOn, Screen.Flashlight.route, "Light tools"),
            ToolItem("Flip Coin", Icons.Rounded.Casino, Screen.FlipCoin.route, "Decisions")
        )
    )
)
