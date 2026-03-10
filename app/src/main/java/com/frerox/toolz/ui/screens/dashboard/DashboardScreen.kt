package com.frerox.toolz.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.ComponentActivity
import coil.compose.AsyncImage
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.time.TimerViewModel
import com.frerox.toolz.ui.screens.time.StopwatchViewModel
import com.frerox.toolz.ui.screens.time.PomodoroViewModel
import com.frerox.toolz.ui.screens.sensors.StepCounterViewModel
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

sealed class PillPage {
    object Music : PillPage()
    object Timer : PillPage()
    object Stopwatch : PillPage()
    object Pomodoro : PillPage()
    object Steps : PillPage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    val musicViewModel: MusicPlayerViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val timerViewModel: TimerViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val stopwatchViewModel: StopwatchViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val pomodoroViewModel: PomodoroViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val stepViewModel: StepCounterViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()

    var searchQuery by remember { mutableStateOf("") }
    val categories = remember { getCategories() }
    val musicState by musicViewModel.uiState.collectAsState()
    val timerState by timerViewModel.uiState.collectAsState()
    val stopwatchState by stopwatchViewModel.uiState.collectAsState()
    val pomodoroState by pomodoroViewModel.uiState.collectAsState()
    val stepState by stepViewModel.uiState.collectAsState()
    
    val filteredCategories = categories.map { category ->
        category.copy(items = category.items.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.description.contains(searchQuery, ignoreCase = true) 
        })
    }.filter { it.items.isNotEmpty() }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    WelcomeHeader(onSettingsClick = { onNavigate(Screen.Settings.route) })
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search 30+ precision tools...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = { 
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
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
                    ),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                filteredCategories.forEach { category ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp),
                            letterSpacing = 2.sp
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

            // Universal App Pill
            val activePages = remember(musicState, timerState, stopwatchState, pomodoroState, stepState) {
                val pages = mutableListOf<PillPage>()
                if (musicState.currentTrack != null) pages.add(PillPage.Music)
                if (timerState.isRunning || timerState.isPaused) pages.add(PillPage.Timer)
                if (stopwatchState.isRunning) pages.add(PillPage.Stopwatch)
                if (pomodoroState.isRunning) pages.add(PillPage.Pomodoro)
                pages.add(PillPage.Steps) // Always show steps
                pages
            }

            AnimatedVisibility(
                visible = activePages.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                val pagerState = rememberPagerState(pageCount = { activePages.size })
                
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(100.dp)
                        .shadow(32.dp, RoundedCornerShape(50.dp))
                        .bouncyClick { 
                            val currentRoute = when(activePages[pagerState.currentPage]) {
                                PillPage.Music -> Screen.MusicPlayer.route
                                PillPage.Timer -> Screen.Timer.route
                                PillPage.Stopwatch -> Screen.Stopwatch.route
                                PillPage.Pomodoro -> Screen.Pomodoro.route
                                PillPage.Steps -> Screen.StepCounter.route
                            }
                            onNavigate(currentRoute)
                        },
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
                    shape = RoundedCornerShape(50.dp),
                    tonalElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        when (activePages[pageIndex]) {
                            PillPage.Music -> MusicPill(musicState, musicViewModel)
                            PillPage.Timer -> TimerPill(timerState, timerViewModel)
                            PillPage.Stopwatch -> StopwatchPill(stopwatchState, stopwatchViewModel)
                            PillPage.Pomodoro -> PomodoroPill(pomodoroState, pomodoroViewModel)
                            PillPage.Steps -> StepsPill(stepState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicPill(state: com.frerox.toolz.ui.screens.media.MusicUiState, viewModel: MusicPlayerViewModel) {
    val track = state.currentTrack ?: return
    
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pillRotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        val targetProgress = if (state.duration > 0) state.progress.toFloat() / state.duration else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(500, easing = LinearOutSlowInEasing),
            label = "smoothProgress"
        )
        
        // Background Progress Fill
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        )

        Row(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f),
                shape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    letterSpacing = 0.5.sp
                )
            }
            
            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent
        )
    }
}

@Composable
fun TimerPill(state: com.frerox.toolz.ui.screens.time.TimerState, viewModel: TimerViewModel) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("TIMER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
            Text(
                formatTimeDashboard(state.remainingTime),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        IconButton(
            onClick = { viewModel.toggleStartStop() },
            modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)
        ) {
            Icon(
                if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
fun StopwatchPill(state: com.frerox.toolz.ui.screens.time.StopwatchState, viewModel: StopwatchViewModel) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("STOPWATCH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
            Text(
                formatTimeDashboard(state.elapsedTime),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        IconButton(
            onClick = { viewModel.toggleStartStop() },
            modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape)
        ) {
            Icon(
                if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
fun PomodoroPill(state: com.frerox.toolz.ui.screens.time.PomodoroState, viewModel: PomodoroViewModel) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AvTimer, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.mode.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            Text(
                formatTimeDashboard(state.remainingTime),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        IconButton(
            onClick = { viewModel.toggleStartStop() },
            modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.error, CircleShape)
        ) {
            Icon(
                if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
fun StepsPill(state: com.frerox.toolz.ui.screens.sensors.StepState) {
    val progress = if (state.goal > 0) (state.steps.toFloat() / state.goal.toFloat()).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                strokeWidth = 7.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("DAILY STEPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            Text(
                "${state.steps}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            Text("GOAL: ${state.goal}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(0.6f))
        }
    }
}

private fun formatTimeDashboard(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
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
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Toolz Pro",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1).sp
            )
        }
        
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp))
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
            .height(140.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                
                Column {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = tool.description.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
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
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp).alpha(0.1f),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "No tools found for \"$query\"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            "Try adjusting your search criteria",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
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
