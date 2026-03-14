package com.frerox.toolz.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.ComponentActivity
import coil3.compose.AsyncImage
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.time.TimerViewModel
import com.frerox.toolz.ui.screens.time.StopwatchViewModel
import com.frerox.toolz.ui.screens.time.PomodoroViewModel
import com.frerox.toolz.ui.screens.sensors.StepCounterViewModel
import com.frerox.toolz.ui.screens.sensors.VoiceRecorderViewModel
import com.frerox.toolz.ui.screens.sensors.RecordingState
import com.frerox.toolz.ui.screens.notepad.NotepadViewModel
import kotlinx.coroutines.delay
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
    object Recorder : PillPage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    settingsRepository: SettingsRepository
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    val headerAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(800), label = "")
    val headerOffset by animateDpAsState(if (visible) 0.dp else (-20).dp, spring(Spring.DampingRatioMediumBouncy), label = "")

    val contentAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(1000, delayMillis = 200), label = "")
    val contentScale by animateFloatAsState(if (visible) 1f else 0.98f, spring(Spring.DampingRatioLowBouncy), label = "")

    val tools = remember { getCategories().flatMap { it.items } }
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    val musicViewModel: MusicPlayerViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val timerViewModel: TimerViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val stopwatchViewModel: StopwatchViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val pomodoroViewModel: PomodoroViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val stepViewModel: StepCounterViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val recorderViewModel: VoiceRecorderViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val notepadViewModel: NotepadViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()

    var searchQuery by remember { mutableStateOf("") }
    val categories = remember { getCategories() }
    val musicState by musicViewModel.uiState.collectAsState()
    val timerState by timerViewModel.uiState.collectAsState()
    val stopwatchState by stopwatchViewModel.uiState.collectAsState()
    val pomodoroState by pomodoroViewModel.uiState.collectAsState()
    val stepState by stepViewModel.uiState.collectAsState()
    val recorderState by recorderViewModel.uiState.collectAsState()
    val notes by notepadViewModel.notes.collectAsState()
    
    val showPillSetting by settingsRepository.showToolzPill.collectAsState(initial = true)
    val userName by settingsRepository.userName.collectAsState(initial = "")
    val dashboardView by settingsRepository.dashboardView.collectAsState(initial = "DEFAULT")

    val filteredCategories = remember(searchQuery, categories) {
        categories.map { category ->
            category.copy(items = category.items.filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.description.contains(searchQuery, ignoreCase = true) 
            })
        }.filter { it.items.isNotEmpty() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        alpha = headerAlpha
                        translationY = headerOffset.toPx()
                    }
            ) {
                WelcomeHeader(
                    userName = userName,
                    onSettingsClick = { onNavigate(Screen.Settings.route) }
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 30+ precision tools...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
        ) {
            if (dashboardView == "LIST") {
                val allTools = remember(categories) { 
                    categories.flatMap { it.items }.distinctBy { it.route }.sortedBy { it.title } 
                }
                val filteredTools = remember(searchQuery, allTools) {
                    allTools.filter { 
                        it.title.contains(searchQuery, ignoreCase = true) || 
                        it.description.contains(searchQuery, ignoreCase = true) 
                    }
                }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdge(
                            brush = Brush.verticalGradient(0f to Color.Transparent, 0.02f to Color.Black, 0.98f to Color.Black, 1f to Color.Transparent),
                            length = 32.dp
                        ),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 150.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTools, key = { it.route }) { tool ->
                        ToolListItem(tool = tool, onClick = { onNavigate(tool.route) })
                    }
                    if (filteredTools.isEmpty()) {
                        item { EmptySearchState(searchQuery) }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdge(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.02f to Color.Black,
                                0.98f to Color.Black,
                                1f to Color.Transparent
                            ),
                            length = 32.dp
                        ),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 150.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quick Notepad Preview
                    if (notes.isNotEmpty() && searchQuery.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NotepadPreview(notes = notes, onNoteClick = { onNavigate(Screen.Notepad.route) })
                        }
                    }

                    filteredCategories.forEach { category ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = category.title) {
                            Surface(
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                        items(category.items, key = { it.route + category.title }) { tool ->
                            ImprovedToolCard(tool = tool, onClick = { onNavigate(tool.route) })
                        }
                    }
                    
                    if (filteredCategories.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptySearchState(searchQuery)
                        }
                    }
                }
            }

            // Universal App Pill
            val activePages = remember(musicState, timerState, stopwatchState, pomodoroState, stepState, recorderState, showPillSetting) {
                if (!showPillSetting) return@remember emptyList<PillPage>()
                val pages = mutableListOf<PillPage>()
                if (musicState.currentTrack != null) pages.add(PillPage.Music)
                if (recorderState.isRecording) pages.add(PillPage.Recorder)
                if (timerState.isRunning || timerState.remainingTime > 0) pages.add(PillPage.Timer)
                if (stopwatchState.isRunning || stopwatchState.elapsedTime > 0) pages.add(PillPage.Stopwatch)
                if (pomodoroState.isRunning || pomodoroTimeLeft(pomodoroState) > 0) pages.add(PillPage.Pomodoro)
                if (stepState.isEnabledInSettings) pages.add(PillPage.Steps) 
                pages
            }

            AnimatedVisibility(
                visible = activePages.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it + 200 }) + fadeIn() + scaleIn(initialScale = 0.8f),
                exit = slideOutVertically(targetOffsetY = { it + 200 }) + fadeOut() + scaleOut(targetScale = 0.8f),
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
                        .shadow(32.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        .bouncyClick { 
                            val currentRoute = when(activePages[pagerState.currentPage]) {
                                PillPage.Music -> Screen.MusicPlayer.route
                                PillPage.Timer -> Screen.Timer.route
                                PillPage.Stopwatch -> Screen.Stopwatch.route
                                PillPage.Pomodoro -> Screen.Pomodoro.route
                                PillPage.Steps -> Screen.StepCounter.route
                                PillPage.Recorder -> Screen.VoiceRecorder.route
                            }
                            onNavigate(currentRoute)
                        },
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    tonalElevation = 12.dp,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val pageOffset = (
                                        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                    ).absoluteValue
                                    
                                    alpha = lerp(
                                        start = 0.4f,
                                        stop = 1f,
                                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                    )
                                    scaleX = lerp(
                                        start = 0.9f,
                                        stop = 1f,
                                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                    )
                                    scaleY = lerp(
                                        start = 0.9f,
                                        stop = 1f,
                                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                    )
                                }
                                .animateContentSize()
                        ) {
                            when (activePages[pageIndex]) {
                                PillPage.Music -> MusicPill(musicState, musicViewModel)
                                PillPage.Timer -> TimerPill(timerState, timerViewModel)
                                PillPage.Stopwatch -> StopwatchPill(stopwatchState, stopwatchViewModel)
                                PillPage.Pomodoro -> PomodoroPill(pomodoroState, pomodoroViewModel)
                                PillPage.Steps -> StepsPill(stepState)
                                PillPage.Recorder -> RecorderPill(recorderState, recorderViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pomodoroTimeLeft(state: com.frerox.toolz.ui.screens.time.PomodoroState): Long {
    return try { state.remainingTime } catch(e: Exception) { 0L }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

private val Float.absoluteValue: Float get() = if (this < 0) -this else this

@Composable
fun ToolListItem(tool: ToolItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun NotepadPreview(notes: List<Note>, onNoteClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "QUICK NOTES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            TextButton(onClick = onNoteClick) {
                Text("VIEW ALL", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val pinnedNotes = notes.filter { it.isPinned }
            items(pinnedNotes.take(5)) { note ->
                Surface(
                    modifier = Modifier
                        .width(160.dp)
                        .height(110.dp)
                        .bouncyClick(onClick = onNoteClick),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(note.color).copy(alpha = 0.9f),
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            note.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            note.content,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecorderPill(state: RecordingState, viewModel: VoiceRecorderViewModel) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                val infiniteTransition = rememberInfiniteTransition(label = "recording")
                val alphaAnim by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "alpha"
                )
                Icon(
                    Icons.Rounded.Mic, 
                    null, 
                    tint = MaterialTheme.colorScheme.error, 
                    modifier = Modifier.size(24.dp).alpha(if (state.isRecording && !state.isPaused) alphaAnim else 1f)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("RECORDING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            Text(
                formatTimeDashboard(state.durationMillis),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { viewModel.stopRecording(true) },
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Rounded.Save, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = { if (state.isPaused) viewModel.resumeRecording() else viewModel.pauseRecording() },
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.error, CircleShape)
            ) {
                Icon(
                    if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(22.dp)
                )
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
        val targetProgress = if (state.duration > 0) state.playbackPosition.toFloat() / state.duration else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(500, easing = LinearOutSlowInEasing),
            label = "smoothProgress"
        )
        
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(68.dp)
                    .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f),
                shape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.width(20.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), CircleShape)
                        .bouncyClick { viewModel.togglePlayPause() }
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                IconButton(
                    onClick = { viewModel.stop() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), CircleShape)
                        .bouncyClick { viewModel.stop() }
                ) {
                    Icon(
                        Icons.Rounded.Stop,
                        null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
    val initialTime = if (state.initialTime > 0) state.initialTime else 1L
    val progress = ((initialTime - state.remainingTime).toFloat() / initialTime.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "smoothProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("TIMER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                Text(
                    formatTimeDashboard(state.remainingTime),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = { viewModel.toggleStartStop() },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)
                ) {
                    Icon(
                        if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = Color.Transparent
        )
    }
}

@Composable
fun StopwatchPill(state: com.frerox.toolz.ui.screens.time.StopwatchState, viewModel: StopwatchViewModel) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("STOPWATCH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
            Text(
                formatTimeDashboard(state.elapsedTime),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { viewModel.reset() },
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            IconButton(
                onClick = { viewModel.toggleStartStop() },
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape)
            ) {
                Icon(
                    if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun PomodoroPill(state: com.frerox.toolz.ui.screens.time.PomodoroState, viewModel: PomodoroViewModel) {
    val initialTime = state.mode.minutes * 60 * 1000L
    val progress = ((initialTime - state.remainingTime).toFloat() / initialTime.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "smoothProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AvTimer, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.mode.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                Text(
                    formatTimeDashboard(state.remainingTime),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = { viewModel.toggleStartStop() },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(
                        if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp),
            color = MaterialTheme.colorScheme.error,
            trackColor = Color.Transparent
        )
    }
}

@Composable
fun StepsPill(state: com.frerox.toolz.ui.screens.sensors.StepState) {
    val progress = if (state.goal > 0) (state.steps.toFloat() / state.goal.toFloat()).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                strokeWidth = 6.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("DAILY STEPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            Text(
                "${state.steps}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            Text("GOAL: ${state.goal}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
fun WelcomeHeader(
    userName: String,
    onSettingsClick: () -> Unit
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    val displayName = userName.ifBlank { "Explorer" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "$greeting, $displayName".uppercase(),
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
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ImprovedToolCard(tool: ToolItem, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "entranceScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "entranceAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
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
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = tool.description.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySearchState(searchQuery: String) {
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
            "No tools found for \"$searchQuery\"",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Try adjusting your search criteria",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

private fun getCategories() = listOf(
    ToolCategory(
        "FAVORITES & ESSENTIALS",
        listOf(
            ToolItem("Focus Flow", Icons.Rounded.Toll, Screen.FocusFlow.route, "Productivity insights"),
            ToolItem("Notification Vault", Icons.Rounded.VerifiedUser, Screen.NotificationVault.route, "Anti-recall logs"),
            ToolItem("Music Player", Icons.Rounded.MusicNote, Screen.MusicPlayer.route, "Audio library"),
            ToolItem("Step Counter", Icons.AutoMirrored.Rounded.DirectionsRun, Screen.StepCounter.route, "Fitness tracker")
        )
    ),
    ToolCategory(
        "UTILITIES & TOOLS",
        listOf(
            ToolItem("Calculator", Icons.Rounded.Calculate, Screen.Calculator.route, "Standard math"),
            ToolItem("Unit Converter", Icons.Rounded.SyncAlt, Screen.UnitConverter.route, "Instant conversion"),
            ToolItem("Clipboard", Icons.Rounded.ContentPaste, Screen.Clipboard.route, "Smart copy history"),
            ToolItem("Flashlight", Icons.Rounded.FlashlightOn, Screen.Flashlight.route, "Light tools"),
            ToolItem("Bubble Level", Icons.Rounded.Architecture, Screen.BubbleLevel.route, "Precision leveling"),
            ToolItem("Password Gen", Icons.Rounded.Password, Screen.PasswordGenerator.route, "Secure keys")
        )
    ),
    ToolCategory(
        "TIME & PRODUCTIVITY",
        listOf(
            ToolItem("Timer", Icons.Rounded.Timer, Screen.Timer.route, "Countdown"),
            ToolItem("Pomodoro", Icons.Rounded.AvTimer, Screen.Pomodoro.route, "Deep focus"),
            ToolItem("Stopwatch", Icons.Rounded.History, Screen.Stopwatch.route, "Laps"),
            ToolItem("World Clock", Icons.Rounded.Public, Screen.WorldClock.route, "Global time"),
            ToolItem("Notepad", Icons.Rounded.Description, Screen.Notepad.route, "Quick notes")
        )
    ),
    ToolCategory(
        "MEDIA & DOCUMENTS",
        listOf(
            ToolItem("PDF Reader", Icons.Rounded.PictureAsPdf, Screen.PdfReader.route, "View documents"),
            ToolItem("Scanner", Icons.Rounded.QrCodeScanner, Screen.Scanner.route, "QR / Barcode"),
            ToolItem("Voice Recorder", Icons.Rounded.Mic, Screen.VoiceRecorder.route, "Audio memo"),
            ToolItem("Magnifier", Icons.Rounded.ZoomIn, Screen.Magnifier.route, "Camera zoom")
        )
    ),
    ToolCategory(
        "SCIENCE & SENSORS",
        listOf(
            ToolItem("Compass", Icons.Rounded.Explore, Screen.Compass.route, "Navigation"),
            ToolItem("Light Meter", Icons.Rounded.LightMode, Screen.LightMeter.route, "Lux measure"),
            ToolItem("Periodic Table", Icons.Rounded.Science, Screen.PeriodicTable.route, "Atomic data"),
            ToolItem("Equation Solver", Icons.Rounded.Calculate, Screen.EquationSolver.route, "Scientific math"),
            ToolItem("Speedometer", Icons.Rounded.Speed, Screen.Speedometer.route, "GPS Speed"),
            ToolItem("Altimeter", Icons.Rounded.FilterHdr, Screen.Altimeter.route, "Altitude")
        )
    ),
    ToolCategory(
        "SYSTEM & HEALTH",
        listOf(
            ToolItem("Battery Info", Icons.Rounded.BatteryChargingFull, Screen.BatteryInfo.route, "Status"),
            ToolItem("BMI Calc", Icons.Rounded.MonitorWeight, Screen.BmiCalculator.route, "Health"),
            ToolItem("Tip Calc", Icons.AutoMirrored.Rounded.ReceiptLong, Screen.TipCalculator.route, "Split bills"),
            ToolItem("Ruler", Icons.Rounded.Straighten, Screen.Ruler.route, "Measure"),
            ToolItem("Flip Coin", Icons.Rounded.Casino, Screen.FlipCoin.route, "Decisions")
        )
    )
)
