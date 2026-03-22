package com.frerox.toolz.ui.screens.dashboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.ComponentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.time.TimerViewModel
import com.frerox.toolz.ui.screens.time.StopwatchViewModel
import com.frerox.toolz.ui.screens.time.PomodoroViewModel
import com.frerox.toolz.ui.screens.sensors.StepCounterViewModel
import com.frerox.toolz.ui.screens.sensors.VoiceRecorderViewModel
import com.frerox.toolz.ui.screens.sensors.RecordingState
import com.frerox.toolz.ui.screens.notepad.NotepadViewModel
import com.frerox.toolz.ui.screens.todo.TodoViewModel
import com.frerox.toolz.ui.screens.todo.TodoUiState
import com.frerox.toolz.ui.screens.focus.FocusFlowViewModel
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.util.VibrationManager
import com.frerox.toolz.util.ConnectivityObserver
import com.frerox.toolz.util.NetworkConnectivityObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.absoluteValue

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
    object Todo : PillPage()
    object Focus : PillPage()
    object Caffeinate : PillPage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    settingsRepository: SettingsRepository,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val musicViewModel: MusicPlayerViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val timerViewModel: TimerViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val stopwatchViewModel: StopwatchViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val pomodoroViewModel: PomodoroViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val stepViewModel: StepCounterViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val recorderViewModel: VoiceRecorderViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val notepadViewModel: NotepadViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val todoViewModel: TodoViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val focusViewModel: FocusFlowViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val caffeinateViewModel: com.frerox.toolz.ui.screens.focus.CaffeinateViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()

    val musicState by musicViewModel.uiState.collectAsState()
    val timerState by timerViewModel.uiState.collectAsState()
    val stopwatchState by stopwatchViewModel.uiState.collectAsState()
    val pomodoroState by pomodoroViewModel.uiState.collectAsState()
    val stepState by stepViewModel.uiState.collectAsState()
    val recorderState by recorderViewModel.uiState.collectAsState()
    val notes by notepadViewModel.notes.collectAsState()
    val todoState by todoViewModel.uiState.collectAsState()
    val productivityScore by focusViewModel.productivityScore.collectAsState()
    val isCaffeinateActive by caffeinateViewModel.isServiceRunning.collectAsState()

    val showPillSetting by settingsRepository.showToolzPill.collectAsState(initial = true)
    val pillTodoEnabled by settingsRepository.pillTodoEnabled.collectAsState(initial = true)
    val pillFocusEnabled by settingsRepository.pillFocusEnabled.collectAsState(initial = true)
    
    val userName by settingsRepository.userName.collectAsState(initial = "")
    val dashboardView by settingsRepository.dashboardView.collectAsState(initial = "DEFAULT")
    val pinnedTools by settingsRepository.pinnedTools.collectAsState(initial = emptySet())
    val recentTools by settingsRepository.recentTools.collectAsState(initial = emptyList())
    
    val showRecentTools by settingsRepository.showRecentTools.collectAsState(initial = true)
    val showQuickNotes by settingsRepository.showQuickNotes.collectAsState(initial = true)

    val connectivityObserver = remember { NetworkConnectivityObserver(context) }
    val networkStatus by connectivityObserver.observe().collectAsState(initial = ConnectivityObserver.Status.Unavailable)
    val isOnline = networkStatus == ConnectivityObserver.Status.Available

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAiSearching by viewModel.isAiSearching.collectAsStateWithLifecycle()
    val aiSuggestedRoutes by viewModel.aiSuggestedRoutes.collectAsStateWithLifecycle()

    DashboardContent(
        onNavigate = onNavigate,
        settingsRepository = settingsRepository,
        performanceMode = performanceMode,
        vibrationManager = vibrationManager,
        userName = userName,
        dashboardView = dashboardView,
        pinnedTools = pinnedTools,
        recentTools = recentTools,
        showRecentTools = showRecentTools,
        showQuickNotes = showQuickNotes,
        isOnline = isOnline,
        musicState = musicState,
        musicViewModel = musicViewModel,
        timerState = timerState,
        timerViewModel = timerViewModel,
        stopwatchState = stopwatchState,
        stopwatchViewModel = stopwatchViewModel,
        pomodoroState = pomodoroState,
        pomodoroViewModel = pomodoroViewModel,
        stepState = stepState,
        recorderState = recorderState,
        recorderViewModel = recorderViewModel,
        notes = notes,
        todoState = todoState,
        todoViewModel = todoViewModel,
        productivityScore = productivityScore,
        showPillSetting = showPillSetting,
        pillTodoEnabled = pillTodoEnabled,
        pillFocusEnabled = pillFocusEnabled,
        searchQuery = searchQuery,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        isAiSearching = isAiSearching,
        aiSuggestedRoutes = aiSuggestedRoutes,
        isCaffeinateActive = isCaffeinateActive,
        caffeinateViewModel = caffeinateViewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    onNavigate: (String) -> Unit,
    settingsRepository: SettingsRepository,
    performanceMode: Boolean,
    vibrationManager: VibrationManager?,
    userName: String,
    dashboardView: String,
    pinnedTools: Set<String>,
    recentTools: List<String>,
    showRecentTools: Boolean,
    showQuickNotes: Boolean,
    isOnline: Boolean,
    musicState: MusicUiState,
    musicViewModel: MusicPlayerViewModel,
    timerState: com.frerox.toolz.ui.screens.time.TimerState,
    timerViewModel: TimerViewModel,
    stopwatchState: com.frerox.toolz.ui.screens.time.StopwatchState,
    stopwatchViewModel: StopwatchViewModel,
    pomodoroState: com.frerox.toolz.ui.screens.time.PomodoroState,
    pomodoroViewModel: PomodoroViewModel,
    stepState: com.frerox.toolz.ui.screens.sensors.StepState,
    recorderState: RecordingState,
    recorderViewModel: VoiceRecorderViewModel,
    notes: List<Note>,
    todoState: TodoUiState,
    todoViewModel: TodoViewModel,
    productivityScore: Int,
    showPillSetting: Boolean,
    pillTodoEnabled: Boolean,
    pillFocusEnabled: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    isCaffeinateActive: Boolean,
    caffeinateViewModel: com.frerox.toolz.ui.screens.focus.CaffeinateViewModel
) {
    val categories = remember { getCategories() }
    val scope = rememberCoroutineScope()

    val filteredCategories = remember(searchQuery, categories) {
        categories.map { category ->
            category.copy(items = category.items.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            })
        }.filter { it.items.isNotEmpty() }
    }

    val pinnedToolItems = remember(pinnedTools, categories) {
        val allItems = categories.flatMap { it.items }
        pinnedTools.mapNotNull { route -> allItems.find { it.route == route } }
    }

    val recentToolItems = remember(recentTools, categories) {
        val allItems = categories.flatMap { it.items }
        recentTools.mapNotNull { route -> allItems.find { it.route == route } }
    }

    val aiSuggestedToolItems = remember(aiSuggestedRoutes, categories) {
        val allItems = categories.flatMap { it.items }
        aiSuggestedRoutes.mapNotNull { route -> allItems.find { it.route == route } }.distinctBy { it.route }
    }

    val navigateWithRecent = { route: String ->
        vibrationManager?.vibrateClick()
        scope.launch { settingsRepository.addRecentTool(route) }
        onNavigate(route)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                WelcomeHeader(
                    userName = userName,
                    onSettingsClick = { 
                        vibrationManager?.vibrateClick()
                        onNavigate(Screen.Settings.route) 
                    }
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { onSearchQueryChanged(it) },
                    placeholder = { Text(if (isOnline) "Describe your need" else "Search tools...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    leadingIcon = { 
                        if (isAiSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                vibrationManager?.vibrateClick()
                                onSearchQueryChanged("")
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(32.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )

                AnimatedVisibility(
                    visible = aiSuggestedToolItems.isNotEmpty() && searchQuery.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "AI RECOMMENDED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                            letterSpacing = 2.sp
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(aiSuggestedToolItems) { _, tool ->
                                Surface(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .height(72.dp)
                                        .bouncyClick { navigateWithRecent(tool.route) },
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(tool.icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                tool.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "Match found",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
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
                    modifier = Modifier.fillMaxSize().then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 140.dp + padding.calculateBottomPadding(),
                        top = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isOnline && searchQuery.isEmpty()) {
                        item {
                            AiAssistantCard(
                                onClick = { navigateWithRecent(Screen.AiAssistant.route) }
                            )
                        }
                    }

                    if (pinnedToolItems.isNotEmpty() && searchQuery.isEmpty()) {
                        item {
                            SectionHeader("PINNED INSTRUMENTS")
                        }
                        itemsIndexed(pinnedToolItems, key = { _, tool -> "pinned_${tool.route}" }) { index, tool ->
                            val visible = remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(index * 40L)
                                visible.value = true
                            }
                            AnimatedVisibility(
                                visible = visible.value,
                                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 20 })
                            ) {
                                ToolListItem(
                                    tool = tool,
                                    isPinned = true,
                                    onClick = { navigateWithRecent(tool.route) },
                                    onLongClick = {
                                        vibrationManager?.vibrateLongClick()
                                        scope.launch { settingsRepository.togglePinnedTool(tool.route) }
                                    }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    if (showRecentTools && recentToolItems.isNotEmpty() && searchQuery.isEmpty()) {
                        item {
                            SectionHeader("RECENTLY USED")
                        }
                        itemsIndexed(recentToolItems, key = { _, tool -> "recent_${tool.route}" }) { index, tool ->
                            val visible = remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay((pinnedToolItems.size + index) * 40L)
                                visible.value = true
                            }
                            AnimatedVisibility(
                                visible = visible.value,
                                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 20 })
                            ) {
                                ToolListItem(
                                    tool = tool,
                                    isPinned = pinnedTools.contains(tool.route),
                                    onClick = { navigateWithRecent(tool.route) },
                                    onLongClick = {
                                        vibrationManager?.vibrateLongClick()
                                        scope.launch { settingsRepository.togglePinnedTool(tool.route) }
                                    }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    itemsIndexed(filteredTools, key = { _, tool -> tool.route }) { _, tool ->
                        ToolListItem(
                            tool = tool,
                            isPinned = pinnedTools.contains(tool.route),
                            onClick = { navigateWithRecent(tool.route) },
                            onLongClick = {
                                vibrationManager?.vibrateLongClick()
                                scope.launch { settingsRepository.togglePinnedTool(tool.route) }
                            }
                        )
                    }
                    if (filteredTools.isEmpty() && !isAiSearching && aiSuggestedToolItems.isEmpty()) {
                        item { EmptySearchState(searchQuery) }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 140.dp + padding.calculateBottomPadding(),
                        top = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isOnline && searchQuery.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AiAssistantCard(
                                onClick = { navigateWithRecent(Screen.AiAssistant.route) }
                            )
                        }
                    }

                    if (pinnedToolItems.isNotEmpty() && searchQuery.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader("PINNED INSTRUMENTS", topPadding = 8.dp)
                        }
                        itemsIndexed(pinnedToolItems, key = { _, tool -> "pinned_grid_${tool.route}" }) { index, tool ->
                            val visible = remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(index * 40L)
                                visible.value = true
                            }
                            AnimatedVisibility(
                                visible = visible.value,
                                enter = if (performanceMode) fadeIn() else (fadeIn(tween(500)) + scaleIn(initialScale = 0.92f))
                            ) {
                                ImprovedToolCard(
                                    tool = tool,
                                    isPinned = true,
                                    onClick = { navigateWithRecent(tool.route) },
                                    onLongClick = {
                                        vibrationManager?.vibrateLongClick()
                                        scope.launch { settingsRepository.togglePinnedTool(tool.route) }
                                    }
                                )
                            }
                        }
                    }

                    if (showRecentTools && recentToolItems.isNotEmpty() && searchQuery.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader("RECENTLY USED", topPadding = 24.dp)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                itemsIndexed(recentToolItems, key = { _, tool -> "recent_row_${tool.route}" }) { _, tool ->
                                    RecentToolChip(
                                        tool = tool,
                                        onClick = { navigateWithRecent(tool.route) }
                                    )
                                }
                            }
                        }
                    }

                    if (showQuickNotes && notes.isNotEmpty() && searchQuery.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NotepadPreview(notes = notes, onNoteClick = { onNavigate(Screen.Notepad.route) })
                        }
                    }

                    filteredCategories.forEach { category ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = category.title) {
                            SectionHeader(category.title, topPadding = 24.dp)
                        }
                        itemsIndexed(category.items, key = { _, tool -> tool.route + category.title }) { index, tool ->
                            val visible = remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                // Add offset for pinned/recent tools
                                val baseDelay = (pinnedToolItems.size + (if (showRecentTools) 1 else 0)).coerceAtMost(10)
                                delay((baseDelay + index) * 40L)
                                visible.value = true
                            }
                            AnimatedVisibility(
                                visible = visible.value,
                                enter = if (performanceMode) fadeIn() else (fadeIn(tween(500)) + scaleIn(initialScale = 0.92f))
                            ) {
                                ImprovedToolCard(
                                    tool = tool,
                                    isPinned = pinnedTools.contains(tool.route),
                                    onClick = { navigateWithRecent(tool.route) },
                                    onLongClick = {
                                        vibrationManager?.vibrateLongClick()
                                        scope.launch { settingsRepository.togglePinnedTool(tool.route) }
                                    }
                                )
                            }
                        }
                    }

                    if (filteredCategories.isEmpty() && !isAiSearching && aiSuggestedToolItems.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptySearchState(searchQuery)
                        }
                    }
                }
            }

            // Universal App Pill
            val activePages = remember(musicState, timerState, stopwatchState, pomodoroState, stepState, recorderState, todoState, productivityScore, showPillSetting, pillTodoEnabled, pillFocusEnabled) {
                if (!showPillSetting) return@remember emptyList<PillPage>()
                val pages = mutableListOf<PillPage>()
                if (musicState.currentTrack != null) pages.add(PillPage.Music)
                if (recorderState.isRecording) pages.add(PillPage.Recorder)
                if (timerState.isRunning || timerState.remainingTime > 0) pages.add(PillPage.Timer)
                if (stopwatchState.isRunning || stopwatchState.elapsedTime > 0) pages.add(PillPage.Stopwatch)
                if (pomodoroState.isRunning || (try { pomodoroState.remainingTime } catch(e: Exception) { 0L }) > 0) pages.add(PillPage.Pomodoro)
                if (stepState.isEnabledInSettings) pages.add(PillPage.Steps)
                if (pillTodoEnabled && todoState.isSessionActive) pages.add(PillPage.Todo)
                if (pillFocusEnabled) pages.add(PillPage.Focus)
                if (isCaffeinateActive) pages.add(PillPage.Caffeinate)
                pages
            }

            AnimatedVisibility(
                visible = activePages.isNotEmpty(),
                enter = if (performanceMode) fadeIn() else (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(600)) + scaleIn(initialScale = 0.85f, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))),
                exit = if (performanceMode) fadeOut() else (slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(400)) + scaleOut(targetScale = 0.85f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp + padding.calculateBottomPadding())
                    .navigationBarsPadding()
            ) {
                val pagerState = rememberPagerState(pageCount = { activePages.size })

                Surface(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(115.dp)
                        .then(if (performanceMode) Modifier else Modifier.shadow(32.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                        .bouncyClick {
                            vibrationManager?.vibrateClick()
                            val currentRoute = when(activePages[pagerState.currentPage]) {
                                PillPage.Music -> Screen.MusicPlayer.route
                                PillPage.Timer -> Screen.Timer.route
                                PillPage.Stopwatch -> Screen.Stopwatch.route
                                PillPage.Pomodoro -> Screen.Pomodoro.route
                                PillPage.Steps -> Screen.StepCounter.route
                                PillPage.Recorder -> Screen.VoiceRecorder.route
                                PillPage.Todo -> Screen.Todo.route
                                PillPage.Focus -> Screen.FocusFlow.route
                                PillPage.Caffeinate -> Screen.Caffeinate.route
                            }
                            onNavigate(currentRoute)
                        },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (performanceMode) 1f else 0.98f),
                    shape = CircleShape,
                    tonalElevation = 12.dp,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (!performanceMode) {
                                        val pageOffset = (
                                            (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                        ).absoluteValue

                                        alpha = lerp(0.4f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                                        val scale = lerp(0.9f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                }
                        ) {
                            when (activePages[pageIndex]) {
                                PillPage.Music -> MusicPill(musicState, musicViewModel)
                                PillPage.Timer -> TimerPill(timerState, timerViewModel)
                                PillPage.Stopwatch -> StopwatchPill(stopwatchState, stopwatchViewModel)
                                PillPage.Pomodoro -> PomodoroPill(pomodoroState, pomodoroViewModel)
                                PillPage.Steps -> StepsPill(stepState)
                                PillPage.Recorder -> RecorderPill(recorderState, recorderViewModel)
                                PillPage.Todo -> TodoPill(todoState, todoViewModel)
                                PillPage.Focus -> FocusPill(productivityScore)
                                PillPage.Caffeinate -> CaffeinatePill(caffeinateViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiAssistantCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(bottom = 12.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AI ASSISTANT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    "Chat with AI agents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Fast and reliable answers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = topPadding, bottom = 8.dp),
        letterSpacing = 2.sp
    )
}

@Composable
fun RecentToolChip(tool: ToolItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(140.dp)
            .height(64.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(tool.icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                tool.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TodoPill(state: TodoUiState, viewModel: TodoViewModel) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val task = state.tasks.find { it.id == state.sessionTaskId } ?: return
    val doneSub = task.subTasks.count { it.isDone }
    val totalSub = task.subTasks.size
    val progress = if (totalSub > 0) doneSub.toFloat() / totalSub else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (performanceMode) snap() else tween(500),
        label = "todoProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.TaskAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("ACTIVE TASK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (totalSub > 0) {
                    Text("$doneSub/$totalSub MILESTONES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatSessionTime(state.sessionTimeMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                IconButton(onClick = {
                    vibrationManager?.vibrateClick()
                    viewModel.stopSession()
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun FocusPill(score: Int) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                strokeWidth = 6.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Icon(Icons.Rounded.Toll, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("PRODUCTIVITY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(
                "Flow State: $score%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            if (score > 70) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingFlat,
            null,
            tint = if (score > 70) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun CaffeinatePill(viewModel: com.frerox.toolz.ui.screens.focus.CaffeinateViewModel) {
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val vibrationManager = LocalVibrationManager.current
    
    Row(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Coffee, 
                    null, 
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, 
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("CAFFEINATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(
                "ON DRAIN: ACTIVE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = {
                vibrationManager?.vibrateClick()
                viewModel.toggleService()
            },
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun ToolListItem(tool: ToolItem, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isPinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun NotepadPreview(notes: List<Note>, onNoteClick: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
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
            TextButton(onClick = {
                vibrationManager?.vibrateClick()
                onNoteClick()
            }) {
                Text("VIEW ALL", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val pinnedNotes = notes.filter { it.isPinned }
            itemsIndexed(pinnedNotes.take(5), key = { _, note -> note.id }) { _, note ->
                Surface(
                    modifier = Modifier
                        .width(180.dp)
                        .height(120.dp)
                        .bouncyClick(onClick = {
                            vibrationManager?.vibrateClick()
                            onNoteClick()
                        }),
                    shape = RoundedCornerShape(40.dp),
                    color = Color(note.color).copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            note.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            note.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecorderPill(state: RecordingState, viewModel: VoiceRecorderViewModel) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    Row(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                val infiniteTransition = rememberInfiniteTransition(label = "recording")
                val alphaAnim by if (performanceMode) remember { mutableFloatStateOf(1f) } else infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "alpha"
                )
                Icon(
                    Icons.Rounded.Mic,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp).alpha(if (state.isRecording && !state.isPaused) alphaAnim else 1f)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("RECORDING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            Text(
                formatTimeDashboard(state.durationMillis),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        IconButton(
            onClick = {
                vibrationManager?.vibrateClick()
                if (state.isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
            },
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.error, CircleShape)
        ) {
            Icon(
                if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun MusicPill(state: MusicUiState, viewModel: MusicPlayerViewModel) {
    val track = state.currentTrack ?: return
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val duration by viewModel.duration.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by if (performanceMode) remember { mutableFloatStateOf(0f) } else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pillRotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        val targetProgress = if (duration > 0) playbackPosition.toFloat() / duration else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = if (performanceMode) snap() else tween(500, easing = LinearOutSlowInEasing),
            label = "smoothProgress"
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .rotate(if (state.isPlaying && state.rotationEnabled && !performanceMode) rotation else 0f),
                shape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = if (performanceMode) 0.dp else 12.dp
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        letterSpacing = 0.5.sp
                    )

                    if (state.sleepTimerActive && state.sleepTimerRemaining != null) {
                        Text(
                            " • ⏳ ${formatTimeDashboard(state.sleepTimerRemaining)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 4.dp),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    viewModel.togglePlayPause()
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun TimerPill(state: com.frerox.toolz.ui.screens.time.TimerState, viewModel: TimerViewModel) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val initialTime = if (state.initialTime > 0) state.initialTime else 1L
    val progress = ((initialTime - state.remainingTime).toFloat() / initialTime.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (performanceMode) snap() else tween(500, easing = LinearOutSlowInEasing),
        label = "smoothProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("TIMER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                Text(
                    formatTimeDashboard(state.remainingTime),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            IconButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    viewModel.toggleStartStop()
                },
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)
            ) {
                Icon(
                    if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun StopwatchPill(state: com.frerox.toolz.ui.screens.time.StopwatchState, viewModel: StopwatchViewModel) {
    val vibrationManager = LocalVibrationManager.current
    Row(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("STOPWATCH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
            Text(
                formatTimeDashboard(state.elapsedTime),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        IconButton(
            onClick = {
                vibrationManager?.vibrateClick()
                viewModel.toggleStartStop()
            },
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape)
        ) {
            Icon(
                if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun PomodoroPill(state: com.frerox.toolz.ui.screens.time.PomodoroState, viewModel: PomodoroViewModel) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val initialTime = state.mode.minutes * 60 * 1000L
    val progress = ((initialTime - state.remainingTime).toFloat() / initialTime.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (performanceMode) snap() else tween(500, easing = LinearOutSlowInEasing),
        label = "smoothProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AvTimer, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.mode.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                Text(
                    formatTimeDashboard(state.remainingTime),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            IconButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    viewModel.toggleStartStop()
                },
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.error, CircleShape)
            ) {
                Icon(
                    if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp),
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun StepsPill(state: com.frerox.toolz.ui.screens.sensors.StepState) {
    val progress = if (state.goal > 0) (state.steps.toFloat() / state.goal.toFloat()).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                strokeWidth = 8.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("DAILY STEPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            Text(
                "${state.steps}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            Text("GOAL: ${state.goal}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

private fun formatTimeDashboard(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}

private fun formatSessionTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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
            .padding(vertical = 16.dp),
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
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1.5).sp
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ImprovedToolCard(tool: ToolItem, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val performanceMode = LocalPerformanceMode.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!performanceMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(18.dp),
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

                    if (isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = tool.description.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        fontWeight = FontWeight.Black,
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
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(96.dp).alpha(0.15f),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "No tools matching \"$searchQuery\"",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Try a different search term",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DashboardLoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "dashLoading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation },
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary.copy(0.15f),
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round
                )
                CircularProgressIndicator(
                    progress = { 0.2f },
                    modifier = Modifier.fillMaxSize(0.75f).graphicsLayer { rotationZ = -rotation * 2f },
                    strokeWidth = 5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = StrokeCap.Round
                )
                Icon(
                    Icons.Rounded.Bolt,
                    null,
                    modifier = Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "TOOLZ PRO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Initializing instruments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
            )
        }
    }
}

fun getCategories() = listOf(
    ToolCategory(
        "FAVORITES & ESSENTIALS",
        listOf(
            ToolItem("Ai Assistant", Icons.Rounded.AutoAwesome, Screen.AiAssistant.route, "Gemini Flash AI"),
            ToolItem("Focus Flow", Icons.Rounded.Toll, Screen.FocusFlow.route, "Productivity insights"),
            ToolItem("Todo List", Icons.Rounded.TaskAlt, Screen.Todo.route, "Physics task flow"),
            ToolItem("Notification Vault", Icons.Rounded.VerifiedUser, Screen.NotificationVault.route, "Anti-recall logs"),
            ToolItem("Music Player", Icons.Rounded.MusicNote, Screen.MusicPlayer.route, "Audio library"),
            ToolItem("Step Counter", Icons.AutoMirrored.Rounded.DirectionsRun, Screen.StepCounter.route, "Fitness tracker"),
            ToolItem("Caffeinate", Icons.Rounded.Coffee, Screen.Caffeinate.route, "Screen wake tool")
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
            ToolItem("Calendar", Icons.Rounded.CalendarMonth, Screen.Calendar.route, "Time-fluid agenda"),
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
            ToolItem("File Cleaner", Icons.Rounded.CleaningServices, Screen.FileCleaner.route, "Deep clean"),
            ToolItem("Battery Info", Icons.Rounded.BatteryChargingFull, Screen.BatteryInfo.route, "Status"),
            ToolItem("Device Info", Icons.Rounded.Info, Screen.DeviceInfo.route, "Intelligence"),
            ToolItem("BMI Calc", Icons.Rounded.MonitorWeight, Screen.BmiCalculator.route, "Health"),
            ToolItem("Tip Calc", Icons.AutoMirrored.Rounded.ReceiptLong, Screen.TipCalculator.route, "Split bills"),
            ToolItem("Ruler", Icons.Rounded.Straighten, Screen.Ruler.route, "Measure"),
            ToolItem("Flip Coin", Icons.Rounded.Casino, Screen.FlipCoin.route, "Decisions")
        )
    )
)
