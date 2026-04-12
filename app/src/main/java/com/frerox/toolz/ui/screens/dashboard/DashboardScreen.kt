package com.frerox.toolz.ui.screens.dashboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.time.TimerViewModel
import com.frerox.toolz.ui.screens.time.StopwatchViewModel
import com.frerox.toolz.ui.screens.time.PomodoroViewModel
import com.frerox.toolz.ui.screens.time.PomodoroMode
import com.frerox.toolz.ui.screens.sensors.StepCounterViewModel
import com.frerox.toolz.ui.screens.sensors.StepState
import com.frerox.toolz.ui.screens.sensors.VoiceRecorderViewModel
import com.frerox.toolz.ui.screens.sensors.RecordingState
import com.frerox.toolz.ui.screens.notepad.NotepadViewModel
import com.frerox.toolz.ui.screens.todo.TodoViewModel
import com.frerox.toolz.ui.screens.focus.CaffeinateViewModel
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.VibrationManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    data object Music : PillPage()
    data object Timer : PillPage()
    data object Stopwatch : PillPage()
    data object Pomodoro : PillPage()
    data object Steps : PillPage()
    data object Recorder : PillPage()
    data object Todo : PillPage()
    data object Caffeinate : PillPage()
    data class CatalogDownload(val progress: Float, val count: Int) : PillPage()
    data class Tip(val tip: AppTip) : PillPage()
}

data class AppTip(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val color: Color
)

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
    timerViewModel: TimerViewModel = hiltViewModel(),
    stopwatchViewModel: StopwatchViewModel = hiltViewModel(),
    pomodoroViewModel: PomodoroViewModel = hiltViewModel(),
    stepsViewModel: StepCounterViewModel = hiltViewModel(),
    recorderViewModel: VoiceRecorderViewModel = hiltViewModel(),
    notepadViewModel: NotepadViewModel = hiltViewModel(),
    todoViewModel: TodoViewModel = hiltViewModel(),
    caffeinateViewModel: CaffeinateViewModel = hiltViewModel(),
    catalogViewModel: com.frerox.toolz.ui.screens.media.catalog.CatalogViewModel = hiltViewModel(),
    settingsRepository: SettingsRepository
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = com.frerox.toolz.ui.theme.LocalPerformanceMode.current

    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val catalogState by catalogViewModel.uiState.collectAsStateWithLifecycle()
    val timerState by timerViewModel.uiState.collectAsStateWithLifecycle()
    val stopwatchState by stopwatchViewModel.uiState.collectAsStateWithLifecycle()
    val pomodoroState by pomodoroViewModel.uiState.collectAsStateWithLifecycle()
    val stepsState by stepsViewModel.uiState.collectAsStateWithLifecycle()
    val recordingState by recorderViewModel.uiState.collectAsStateWithLifecycle()

    val showPillSetting by settingsRepository.showToolzPill.collectAsState(initial = true)
    val fillThePillEnabled by settingsRepository.fillThePillEnabled.collectAsState(initial = true)
    val userName by settingsRepository.userName.collectAsState(initial = "")
    val pinnedTools by settingsRepository.pinnedTools.collectAsState(initial = emptySet())
    val recentTools by settingsRepository.recentTools.collectAsState(initial = emptyList())
    
    val showRecentTools by settingsRepository.showRecentTools.collectAsState(initial = true)
    val showQuickNotes by settingsRepository.showQuickNotes.collectAsState(initial = true)
    val dashboardView by settingsRepository.dashboardView.collectAsState(initial = "DEFAULT")

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAiSearching by viewModel.isAiSearching.collectAsStateWithLifecycle()
    val aiSuggestedRoutes by viewModel.aiSuggestedRoutes.collectAsStateWithLifecycle()

    val updateVersion by viewModel.updateAvailableVersion.collectAsStateWithLifecycle(initialValue = null)
    
    val notes by notepadViewModel.notes.collectAsStateWithLifecycle()
    val categories = viewModel.categories

    val navAction = remember {
        { route: String ->
            viewModel.addRecentTool(route)
            onNavigate(route)
        }
    }

    DashboardContent(
        onNavigate = navAction,
        settingsRepository = settingsRepository,
        performanceMode = performanceMode,
        vibrationManager = vibrationManager,
        userName = userName,
        pinnedTools = pinnedTools,
        recentTools = recentTools,
        showRecentTools = showRecentTools,
        showQuickNotes = showQuickNotes,
        dashboardView = dashboardView,
        musicState = musicState,
        musicViewModel = musicViewModel,
        timerState = timerState,
        timerViewModel = timerViewModel,
        stopwatchState = stopwatchState,
        stopwatchViewModel = stopwatchViewModel,
        pomodoroState = pomodoroState,
        pomodoroViewModel = pomodoroViewModel,
        stepsState = stepsState,
        stepsViewModel = stepsViewModel,
        recordingState = recordingState,
        recorderViewModel = recorderViewModel,
        notepadViewModel = notepadViewModel,
        todoViewModel = todoViewModel,
        caffeinateViewModel = caffeinateViewModel,
        catalogState = catalogState,
        catalogViewModel = catalogViewModel,
        showPillSetting = showPillSetting,
        fillThePillEnabled = fillThePillEnabled,
        searchQuery = searchQuery,
        isAiSearching = isAiSearching,
        aiSuggestedRoutes = aiSuggestedRoutes,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        updateVersion = updateVersion,
        onDismissUpdate = viewModel::dismissUpdate,
        notes = notes,
        categories = categories
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    onNavigate: (String) -> Unit,
    settingsRepository: SettingsRepository,
    performanceMode: Boolean,
    vibrationManager: VibrationManager?,
    userName: String,
    pinnedTools: Set<String>,
    recentTools: List<String>,
    showRecentTools: Boolean,
    showQuickNotes: Boolean,
    dashboardView: String,
    musicState: MusicUiState,
    musicViewModel: MusicPlayerViewModel,
    timerState: com.frerox.toolz.ui.screens.time.TimerState,
    timerViewModel: TimerViewModel,
    stopwatchState: com.frerox.toolz.ui.screens.time.StopwatchState,
    stopwatchViewModel: StopwatchViewModel,
    pomodoroState: com.frerox.toolz.ui.screens.time.PomodoroState,
    pomodoroViewModel: PomodoroViewModel,
    stepsState: StepState,
    stepsViewModel: StepCounterViewModel,
    recordingState: RecordingState,
    recorderViewModel: VoiceRecorderViewModel,
    notepadViewModel: NotepadViewModel,
    todoViewModel: TodoViewModel,
    caffeinateViewModel: CaffeinateViewModel,
    catalogState: com.frerox.toolz.ui.screens.media.catalog.CatalogUiState,
    catalogViewModel: com.frerox.toolz.ui.screens.media.catalog.CatalogViewModel,
    showPillSetting: Boolean,
    fillThePillEnabled: Boolean,
    searchQuery: String,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    onSearchQueryChange: (String) -> Unit,
    updateVersion: String?,
    onDismissUpdate: () -> Unit,
    notes: List<Note>,
    categories: List<ToolCategory>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardTopBar(
                onSettingsClick = { onNavigate(Screen.Settings.route) },
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isAiSearching = isAiSearching,
                aiSuggestedRoutes = aiSuggestedRoutes,
                onNavigate = onNavigate,
                categories = categories
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .fadingEdges(top = 16.dp, bottom = 80.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Update Card
                if (updateVersion != null) {
                    item {
                        Box(modifier = if (!performanceMode) Modifier.animateItem() else Modifier) {
                            UpdateCard(
                                version = updateVersion,
                                onUpdate = { onNavigate(Screen.Update.route) },
                                onDismiss = onDismissUpdate
                            )
                        }
                    }
                }

                // Header
                item {
                    Box(modifier = if (!performanceMode) Modifier.animateItem() else Modifier) {
                        DashboardHeader(userName)
                    }
                }

                // Recent Tools
                if (showRecentTools && recentTools.isNotEmpty()) {
                    item {
                        Box(modifier = if (!performanceMode) Modifier.animateItem() else Modifier) {
                            RecentToolsSection(
                                recentTools = recentTools,
                                categories = categories,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }

                // Quick Access / Pinned
                if (pinnedTools.isNotEmpty()) {
                    item {
                        Box(modifier = if (!performanceMode) Modifier.animateItem() else Modifier) {
                            PinnedToolsSection(
                                pinnedTools = pinnedTools,
                                categories = categories,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }

                // Quick Notes
                if (showQuickNotes && notes.isNotEmpty()) {
                    item(key = "quick_notes_section") {
                        Box(modifier = if (!performanceMode) Modifier.animateItem() else Modifier) {
                            QuickNotesSection(
                                notes = notes,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }

                // Categories
                categories.forEach { category ->
                    item(key = "header_${category.title}") {
                        Box(modifier = if (!performanceMode) Modifier.animateItem() else Modifier) {
                            CategoryHeader(category.title)
                        }
                    }

                    item(key = "layout_${category.title}") {
                        Box(modifier = if (!performanceMode) Modifier.animateItem() else Modifier) {
                            if (dashboardView == "LIST") {
                                CategoryList(
                                    items = category.items,
                                    onNavigate = onNavigate
                                )
                            } else {
                                CategoryGrid(
                                    items = category.items,
                                    onNavigate = onNavigate
                                )
                            }
                        }
                    }
                }
            }
        }

        // Universal Pill
        if (showPillSetting) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                UniversalPill(
                    musicState = musicState,
                    musicViewModel = musicViewModel,
                    timerState = timerState,
                    timerViewModel = timerViewModel,
                    stopwatchState = stopwatchState,
                    stopwatchViewModel = stopwatchViewModel,
                    pomodoroState = pomodoroState,
                    pomodoroViewModel = pomodoroViewModel,
                    stepsState = stepsState,
                    stepsViewModel = stepsViewModel,
                    recordingState = recordingState,
                    recorderViewModel = recorderViewModel,
                    catalogState = catalogState,
                    catalogViewModel = catalogViewModel,
                    todoViewModel = todoViewModel,
                    caffeinateViewModel = caffeinateViewModel,
                    fillThePillEnabled = fillThePillEnabled,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

@Composable
fun UpdateCard(
    version: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .bouncyClick(onClick = onUpdate),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "v$version is ready for you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun DashboardTopBar(
    onSettingsClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    onNavigate: (String) -> Unit,
    categories: List<ToolCategory>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Toolz",
                style = MaterialTheme.typography.headlineLarge.copy(
                    letterSpacing = (-1.5).sp,
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            FilledTonalIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SmartSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            isAiSearching = isAiSearching,
            aiSuggestedRoutes = aiSuggestedRoutes,
            onNavigate = onNavigate,
            categories = categories
        )
    }
}

@Composable
fun SmartSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    onNavigate: (String) -> Unit,
    categories: List<ToolCategory>
) {
    val haptic = LocalView.current
    val performanceMode = com.frerox.toolz.ui.theme.LocalPerformanceMode.current

    // AI breathing sparkle animation
    val infiniteTransition = rememberInfiniteTransition(label = "searchSparkle")
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleScale"
    )
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )

    // Local search results
    val allTools = remember(categories) { categories.flatMap { it.items } }
    val localResults = remember(query, allTools) {
        if (query.isBlank()) emptyList()
        else allTools.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }.take(5)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search field
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            ),
            tonalElevation = 2.dp
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search or ask AI...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                        if (isAiSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                trailingIcon = {
                    AnimatedContent(
                        targetState = query.isNotEmpty(),
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200))).togetherWith(
                                fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150))
                            )
                        },
                        label = "trailingIcon"
                    ) { hasQuery ->
                        if (hasQuery) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Powered",
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (performanceMode) 0.7f else sparkleAlpha
                                ),
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(20.dp)
                                    .graphicsLayer {
                                        if (!performanceMode) {
                                            scaleX = sparkleScale
                                            scaleY = sparkleScale
                                        }
                                    }
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }

        // Local search results dropdown
        AnimatedVisibility(
            visible = localResults.isNotEmpty() && aiSuggestedRoutes.isEmpty(),
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            ElevatedCard(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    localResults.forEach { tool ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClick {
                                    haptic.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onNavigate(tool.route)
                                },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = tool.color.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = tool.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = tool.color
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // AI Suggestions
        AnimatedVisibility(
            visible = aiSuggestedRoutes.isNotEmpty(),
            enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            ElevatedCard(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "SMART SUGGESTIONS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.2.sp
                        )
                    }

                    aiSuggestedRoutes.forEach { route ->
                        val tool = allTools.find { it.route == route }
                        if (tool != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .bouncyClick {
                                        haptic.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onNavigate(route)
                                    },
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(46.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = tool.color.copy(alpha = 0.15f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = tool.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = tool.color
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tool.title,
                                            style = MaterialTheme.typography.bodyLarge,
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
                                        Icons.Rounded.ArrowOutward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(userName: String) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..5 -> "Good Night \uD83C\uDF19"
            in 6..11 -> "Good Morning \uD83D\uDC4B"
            in 12..16 -> "Good Afternoon \u2600\uFE0F"
            else -> "Good Evening \uD83C\uDF05"
        }
    }

    val dateText = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (userName.isBlank()) "Explorer" else userName,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-1.2).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PinnedToolsSection(
    pinnedTools: Set<String>,
    categories: List<ToolCategory>,
    onNavigate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CategoryHeader("QUICK ACCESS")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(pinnedTools.toList()) { route ->
                val tool = categories.flatMap { it.items }.find { it.route == route }
                if (tool != null) {
                    PinnedToolItem(tool, onNavigate)
                }
            }
        }
    }
}

@Composable
fun RecentToolsSection(
    recentTools: List<String>,
    categories: List<ToolCategory>,
    onNavigate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CategoryHeader("RECENTLY USED")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(recentTools.take(8)) { route ->
                val tool = categories.flatMap { it.items }.find { it.route == route }
                if (tool != null) {
                    RecentToolCard(tool, onNavigate)
                }
            }
        }
    }
}

@Composable
fun QuickNotesSection(
    notes: List<Note>,
    onNavigate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CategoryHeader("QUICK NOTES")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(notes.take(5)) { note ->
                QuickNoteItem(note, onNavigate)
            }
            item {
                Surface(
                    modifier = Modifier
                        .size(width = 110.dp, height = 140.dp)
                        .bouncyClick { onNavigate(Screen.Notepad.route) },
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "View All", 
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickNoteItem(note: Note, onNavigate: (String) -> Unit) {
    val noteColor = Color(note.color)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (isDark) {
        noteColor.copy(alpha = 0.25f)
    } else {
        noteColor.copy(alpha = 0.12f)
    }
    
    Card(
        modifier = Modifier
            .size(width = 220.dp, height = 140.dp)
            .bouncyClick { onNavigate(Screen.Notepad.route) },
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(1.5.dp, noteColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PinnedToolItem(tool: ToolItem, onNavigate: (String) -> Unit) {
    Card(
        modifier = Modifier
            .size(width = 160.dp, height = 115.dp)
            .bouncyClick { onNavigate(tool.route) },
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = tool.color.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.2.dp, tool.color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = tool.color.copy(alpha = 0.25f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = tool.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = tool.color,
                letterSpacing = (-0.4).sp
            )
        }
    }
}

@Composable
fun RecentToolCard(tool: ToolItem, onNavigate: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .width(105.dp)
            .height(105.dp)
            .bouncyClick { onNavigate(tool.route) },
        shape = RoundedCornerShape(24.dp),
        color = tool.color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, tool.color.copy(alpha = 0.2f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = tool.color.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = tool.color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CategoryHeader(title: String) {
    Row(
        modifier = Modifier.padding(start = 22.dp, top = 30.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun CategoryGrid(
    items: List<ToolItem>,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
    ) {
        val rows = items.chunked(2)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowItems.forEach { item ->
                    ToolGridItem(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onNavigate = onNavigate
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun CategoryList(
    items: List<ToolItem>,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            ToolListItem(
                item = item,
                onNavigate = onNavigate
            )
        }
    }
}

@Composable
fun ToolListItem(
    item: ToolItem,
    onNavigate: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .bouncyClick { onNavigate(item.route) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = item.color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun ToolGridItem(
    item: ToolItem,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .height(115.dp)
            .bouncyClick { onNavigate(item.route) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = item.color.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = item.color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Rounded.ArrowOutward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun UniversalPill(
    musicState: MusicUiState,
    musicViewModel: MusicPlayerViewModel,
    timerState: com.frerox.toolz.ui.screens.time.TimerState,
    timerViewModel: TimerViewModel,
    stopwatchState: com.frerox.toolz.ui.screens.time.StopwatchState,
    stopwatchViewModel: StopwatchViewModel,
    pomodoroState: com.frerox.toolz.ui.screens.time.PomodoroState,
    pomodoroViewModel: PomodoroViewModel,
    stepsState: StepState,
    stepsViewModel: StepCounterViewModel,
    recordingState: RecordingState,
    recorderViewModel: VoiceRecorderViewModel,
    catalogState: com.frerox.toolz.ui.screens.media.catalog.CatalogUiState,
    catalogViewModel: com.frerox.toolz.ui.screens.media.catalog.CatalogViewModel,
    todoViewModel: TodoViewModel,
    caffeinateViewModel: CaffeinateViewModel,
    fillThePillEnabled: Boolean,
    onNavigate: (String) -> Unit
) {
    val performanceMode = com.frerox.toolz.ui.theme.LocalPerformanceMode.current
    val todoState by todoViewModel.uiState.collectAsStateWithLifecycle()
    val isCaffeinated by caffeinateViewModel.isServiceRunning.collectAsStateWithLifecycle()
    val caffeinateTime by caffeinateViewModel.elapsedTime.collectAsStateWithLifecycle()

    val appTips = remember {
        listOf(
            AppTip("Talk to AI agents", "AI assistant", Icons.Rounded.AutoAwesome, Screen.AiAssistant.route, Color(0xFF9C27B0)),
            AppTip("Write something inspiring", "Notepad", Icons.Rounded.EditNote, Screen.Notepad.route, Color(0xFFFF9800)),
            AppTip("Convert any file", "File converter", Icons.Rounded.Transform, Screen.FileConverter.route, Color(0xFF2196F3)),
            AppTip("Manage your time", "Focus Flow", Icons.Rounded.CenterFocusStrong, Screen.FocusFlow.route, Color(0xFF4CAF50)),
            AppTip("Be ready, anytime", "to do list", Icons.AutoMirrored.Rounded.PlaylistAddCheck, Screen.Todo.route, Color(0xFF673AB7)),
            AppTip("Stop procrastinating and work", "pomodoro", Icons.Rounded.Timer, Screen.Pomodoro.route, Color(0xFFF44336)),
            AppTip("Solve your equations", "Equation solver", Icons.Rounded.Functions, Screen.EquationSolver.route, Color(0xFF3F51B5)),
            AppTip("Customize the app", "Settings", Icons.Rounded.Tune, Screen.Settings.route, Color(0xFF607D8B)),
            AppTip("Make your storage free again", "File Cleaner", Icons.Rounded.CleaningServices, Screen.FileCleaner.route, Color(0xFF00BCD4)),
            AppTip("Take a coffee", "and your screen will never sleep", Icons.Rounded.Coffee, Screen.Caffeinate.route, Color(0xFF795548))
        )
    }

    val pages = remember(musicState, timerState, stopwatchState, pomodoroState, stepsState, recordingState, todoState.tasks, isCaffeinated, catalogState.downloadingTracks, fillThePillEnabled) {
        val list = mutableListOf<PillPage>()
        if (catalogState.downloadingTracks.isNotEmpty()) {
            val avgProgress = catalogState.downloadingTracks.values.average().toFloat()
            list.add(PillPage.CatalogDownload(avgProgress, catalogState.downloadingTracks.size))
        }
        if (musicState.isPlaying || musicState.currentTrack != null) list.add(PillPage.Music)
        if (timerState.isRunning || timerState.remainingTime > 0) list.add(PillPage.Timer)
        if (stopwatchState.isRunning || stopwatchState.elapsedTime > 0) list.add(PillPage.Stopwatch)
        if (pomodoroState.isRunning) list.add(PillPage.Pomodoro)
        if (recordingState.isRecording || recordingState.isPaused) list.add(PillPage.Recorder)
        if (todoState.tasks.isNotEmpty()) list.add(PillPage.Todo)
        if (isCaffeinated) list.add(PillPage.Caffeinate)
        
        // Only show step tracker pill if activated in settings
        if (stepsState.isEnabledInSettings) list.add(PillPage.Steps)

        if (list.isEmpty() && fillThePillEnabled) {
            // Pick 3 random tips as fallback
            appTips.shuffled().take(3).forEach { list.add(PillPage.Tip(it)) }
        }
        list
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val isAnyActive = musicState.isPlaying || timerState.isRunning || stopwatchState.isRunning || pomodoroState.isRunning || recordingState.isRecording || isCaffeinated

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface

    if (pages.isEmpty()) return

    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .height(84.dp)
            .fillMaxWidth()
            .shadow(
                elevation = if (isAnyActive) 20.dp else 10.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = primaryColor.copy(alpha = if (isAnyActive) 0.4f else 0.2f),
                spotColor = primaryColor.copy(alpha = if (isAnyActive) 0.5f else 0.25f)
            ),
        color = surfaceColor.copy(alpha = 0.98f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(
            width = if (isAnyActive) 1.8.dp else 1.2.dp,
            brush = if (isAnyActive && !performanceMode) {
                Brush.sweepGradient(
                    0f to primaryColor.copy(alpha = 0.8f),
                    0.25f to secondaryColor.copy(alpha = 0.5f),
                    0.5f to primaryColor.copy(alpha = 0.2f),
                    0.75f to secondaryColor.copy(alpha = 0.5f),
                    1f to primaryColor.copy(alpha = 0.8f)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.4f),
                        secondaryColor.copy(alpha = 0.2f),
                        primaryColor.copy(alpha = 0.1f)
                    )
                )
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                when (val page = pages[pageIndex]) {
                    is PillPage.Music -> MusicPillContent(musicState, musicViewModel, onNavigate)
                    is PillPage.Timer -> TimerPillContent(timerState, timerViewModel, onNavigate)
                    is PillPage.Stopwatch -> StopwatchPillContent(stopwatchState, stopwatchViewModel, onNavigate)
                    is PillPage.Pomodoro -> PomodoroPillContent(pomodoroState, pomodoroViewModel, onNavigate)
                    is PillPage.Steps -> StepsPillContent(stepsState, stepsViewModel, onNavigate)
                    is PillPage.Recorder -> RecorderPillContent(recordingState, recorderViewModel, onNavigate)
                    is PillPage.Todo -> TodoPillContent(todoState.tasks.firstOrNull(), onNavigate)
                    is PillPage.CatalogDownload -> CatalogDownloadPillContent(page.progress, page.count, onNavigate)
                    is PillPage.Caffeinate -> CaffeinatePillContent(caffeinateTime, onNavigate)
                    is PillPage.Tip -> TipPillContent(page.tip, onNavigate)
                }
            }

            // Enhanced page indicators
            if (pages.size > 1) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pages.size) { iteration ->
                        val active = pagerState.currentPage == iteration
                        val width by animateDpAsState(
                            targetValue = if (active) 22.dp else 6.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "dotWidth"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (active) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            animationSpec = tween(300),
                            label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .size(width = width, height = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TipPillContent(tip: AppTip, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(tip.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = tip.color.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = tip.icon,
                    contentDescription = null,
                    tint = tip.color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tip.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tip.description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(
            onClick = { onNavigate(tip.route) },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun MusicPillContent(state: MusicUiState, viewModel: MusicPlayerViewModel, onNavigate: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "musicPill")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pillRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.MusicPlayer.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            val artShape = when (state.artShape) {
                "CIRCLE" -> CircleShape
                else -> RoundedCornerShape(18.dp)
            }
            AsyncImage(
                model = state.currentTrack?.thumbnailUri,
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f)
                    .clip(artShape),
                contentScale = ContentScale.Crop
            )
            if (state.isPlaying) {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.size(54.dp).clip(artShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.currentTrack?.title ?: "Not Playing",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.currentTrack?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
        }
        FilledTonalIconButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun TimerPillContent(state: com.frerox.toolz.ui.screens.time.TimerState, viewModel: TimerViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.Timer.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val remainingSeconds = state.remainingTime / 1000
            Text(
                text = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            val progress = if (state.initialTime > 0) state.remainingTime.DivideToFloat() / state.initialTime.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = { viewModel.toggleStartStop() },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private fun Long.DivideToFloat(): Float = this.toFloat()

@Composable
fun StopwatchPillContent(state: com.frerox.toolz.ui.screens.time.StopwatchState, viewModel: StopwatchViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.Stopwatch.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = String.format("%02d:%02d.%01d",
                (state.elapsedTime / 60000),
                (state.elapsedTime % 60000) / 1000,
                (state.elapsedTime % 1000) / 100
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        FilledTonalIconButton(
            onClick = { viewModel.toggleStartStop() },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun PomodoroPillContent(state: com.frerox.toolz.ui.screens.time.PomodoroState, viewModel: PomodoroViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.Pomodoro.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AvTimer, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.mode != PomodoroMode.WORK) "Break Time" else "Focusing",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.error
            )
            val remainingSeconds = state.remainingTime / 1000
            Text(
                text = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        FilledTonalIconButton(
            onClick = { viewModel.toggleStartStop() },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun StepsPillContent(state: StepState, viewModel: StepCounterViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.StepCounter.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${state.steps} Steps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            val progress = (state.steps.toFloat() / state.goal.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = { onNavigate(Screen.StepCounter.route) },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RecorderPillContent(state: RecordingState, viewModel: VoiceRecorderViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.VoiceRecorder.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "recording")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (state.isRecording) "Recording..." else "Recording Paused",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        FilledTonalIconButton(
            onClick = { viewModel.stopRecording() },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun TodoPillContent(task: com.frerox.toolz.data.todo.TaskEntry?, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.Todo.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Next Priority",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = task?.title ?: "Nothing planned",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = { onNavigate(Screen.Todo.route) },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun CatalogDownloadPillContent(progress: Float, count: Int, onNavigate: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "downloadPill")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.MusicPlayer.createRoute(2)) } // Navigate to catalog tab (index 2)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp).graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Downloads (MUSIC)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun CaffeinatePillContent(timeMillis: Long, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate(Screen.Caffeinate.route) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Coffee, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Awake Mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Black
            )
            val h = timeMillis / 3600000
            val m = (timeMillis % 3600000) / 60000
            val s = (timeMillis % 60000) / 1000
            Text(
                text = if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = { onNavigate(Screen.Caffeinate.route) },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
