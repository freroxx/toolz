package com.frerox.toolz.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.focus.CaffeinateViewModel
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.media.catalog.CatalogUiState
import com.frerox.toolz.ui.screens.media.catalog.CatalogViewModel
import com.frerox.toolz.ui.screens.notepad.NotepadViewModel
import com.frerox.toolz.ui.screens.sensors.RecordingState
import com.frerox.toolz.ui.screens.sensors.StepCounterViewModel
import com.frerox.toolz.ui.screens.sensors.StepState
import com.frerox.toolz.ui.screens.sensors.VoiceRecorderViewModel
import com.frerox.toolz.ui.screens.time.PomodoroMode
import com.frerox.toolz.ui.screens.time.PomodoroState
import com.frerox.toolz.ui.screens.time.PomodoroViewModel
import com.frerox.toolz.ui.screens.time.StopwatchState
import com.frerox.toolz.ui.screens.time.StopwatchViewModel
import com.frerox.toolz.ui.screens.time.TimerState
import com.frerox.toolz.ui.screens.time.TimerViewModel
import com.frerox.toolz.ui.screens.todo.TodoViewModel
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.OfflineState
import com.frerox.toolz.util.VibrationManager
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

sealed class PillPage {
    data object Music      : PillPage()
    data object Timer      : PillPage()
    data object Stopwatch  : PillPage()
    data object Pomodoro   : PillPage()
    data object Steps      : PillPage()
    data object Recorder   : PillPage()
    data object Todo       : PillPage()
    data object Caffeinate : PillPage()
    data class  CatalogDownload(val progress: Float, val count: Int) : PillPage()
    data class  Tip(val tip: AppTip) : PillPage()
}

data class AppTip(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val color: Color,
)

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel            = hiltViewModel(),
    musicViewModel: MusicPlayerViewModel     = hiltViewModel(),
    timerViewModel: TimerViewModel           = hiltViewModel(),
    stopwatchViewModel: StopwatchViewModel   = hiltViewModel(),
    pomodoroViewModel: PomodoroViewModel     = hiltViewModel(),
    stepsViewModel: StepCounterViewModel     = hiltViewModel(),
    recorderViewModel: VoiceRecorderViewModel = hiltViewModel(),
    notepadViewModel: NotepadViewModel       = hiltViewModel(),
    todoViewModel: TodoViewModel             = hiltViewModel(),
    caffeinateViewModel: CaffeinateViewModel = hiltViewModel(),
    catalogViewModel: CatalogViewModel       = hiltViewModel(),
    settingsRepository: SettingsRepository,
) {
    val vibrationManager = LocalVibrationManager.current
    val musicState      by musicViewModel.uiState.collectAsStateWithLifecycle()
    val catalogState    by catalogViewModel.uiState.collectAsStateWithLifecycle()
    val timerState      by timerViewModel.uiState.collectAsStateWithLifecycle()
    val stopwatchState  by stopwatchViewModel.uiState.collectAsStateWithLifecycle()
    val pomodoroState   by pomodoroViewModel.uiState.collectAsStateWithLifecycle()
    val stepsState      by stepsViewModel.uiState.collectAsStateWithLifecycle()
    val recordingState  by recorderViewModel.uiState.collectAsStateWithLifecycle()

    val showPill     by settingsRepository.showToolzPill.collectAsState(true)
    val fillThePill  by settingsRepository.fillThePillEnabled.collectAsState(true)
    val userName     by settingsRepository.userName.collectAsState("")
    val pinnedTools  by settingsRepository.pinnedTools.collectAsState(emptySet())
    val recentTools  by settingsRepository.recentTools.collectAsState(emptyList())
    val showRecent   by settingsRepository.showRecentTools.collectAsState(true)
    val showNotes    by settingsRepository.showQuickNotes.collectAsState(true)
    val showStats    by settingsRepository.showDashboardStats.collectAsState(false)
    val savedView    by settingsRepository.dashboardView.collectAsState("DEFAULT")

    val searchQuery  by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAiSearching by viewModel.isAiSearching.collectAsStateWithLifecycle()
    val aiRoutes     by viewModel.aiSuggestedRoutes.collectAsStateWithLifecycle()
    val stats        by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val updateVersion by viewModel.updateAvailableVersion.collectAsStateWithLifecycle(null)
    val offlineState by viewModel.offlineState.collectAsStateWithLifecycle()
    val notes        by notepadViewModel.notes.collectAsStateWithLifecycle()
    val categories   by viewModel.categories.collectAsStateWithLifecycle()

    val navigate: (String) -> Unit = remember {
        { route ->
            vibrationManager?.vibrateClick()
            viewModel.addRecentTool(route)
            onNavigate(route)
        }
    }

    DashboardContent(
        onNavigate          = navigate,
        onTogglePin         = { vibrationManager?.vibrateTick(); viewModel.togglePinnedTool(it) },
        userName            = userName,
        pinnedTools         = pinnedTools,
        recentTools         = recentTools,
        showRecentTools     = showRecent,
        showQuickNotes      = showNotes,
        showDashboardStats  = showStats,
        savedView           = savedView,
        musicState          = musicState,
        musicViewModel      = musicViewModel,
        timerState          = timerState,
        timerViewModel      = timerViewModel,
        stopwatchState      = stopwatchState,
        stopwatchViewModel  = stopwatchViewModel,
        pomodoroState       = pomodoroState,
        pomodoroViewModel   = pomodoroViewModel,
        stepsState          = stepsState,
        stepsViewModel      = stepsViewModel,
        recordingState      = recordingState,
        recorderViewModel   = recorderViewModel,
        todoViewModel       = todoViewModel,
        caffeinateViewModel = caffeinateViewModel,
        catalogState        = catalogState,
        showPill            = showPill,
        fillThePill         = fillThePill,
        searchQuery         = searchQuery,
        isAiSearching       = isAiSearching,
        aiSuggestedRoutes   = aiRoutes,
        onSearchChange      = viewModel::updateSearchQuery,
        updateVersion       = updateVersion,
        onDismissUpdate     = viewModel::dismissUpdate,
        notes               = notes,
        categories          = categories,
        offlineState        = offlineState,
        onToggleOffline     = viewModel::toggleOfflineMode,
        stats               = stats,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PRIMARY CONTENT
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardContent(
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    userName: String,
    pinnedTools: Set<String>,
    recentTools: List<String>,
    showRecentTools: Boolean,
    showQuickNotes: Boolean,
    showDashboardStats: Boolean,
    savedView: String,
    musicState: MusicUiState,
    musicViewModel: MusicPlayerViewModel,
    timerState: TimerState,
    timerViewModel: TimerViewModel,
    stopwatchState: StopwatchState,
    stopwatchViewModel: StopwatchViewModel,
    pomodoroState: PomodoroState,
    pomodoroViewModel: PomodoroViewModel,
    stepsState: StepState,
    stepsViewModel: StepCounterViewModel,
    recordingState: RecordingState,
    recorderViewModel: VoiceRecorderViewModel,
    todoViewModel: TodoViewModel,
    caffeinateViewModel: CaffeinateViewModel,
    catalogState: CatalogUiState,
    showPill: Boolean,
    fillThePill: Boolean,
    searchQuery: String,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    onSearchChange: (String) -> Unit,
    updateVersion: String?,
    onDismissUpdate: () -> Unit,
    notes: List<Note>,
    categories: List<ToolCategory>,
    offlineState: OfflineState,
    onToggleOffline: (Boolean) -> Unit,
    stats: DashboardStats,
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode  = LocalPerformanceMode.current

    var showOfflineSheet by remember { mutableStateOf(false) }
    var toolForDetail    by remember { mutableStateOf<ToolItem?>(null) }
    var currentView      by remember(savedView) { mutableStateOf(savedView) }
    var selectedCatIndex by remember { mutableIntStateOf(CATEGORY_ALL) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) selectedCatIndex = CATEGORY_ALL
    }

    val visibleCategories = remember(categories, selectedCatIndex, searchQuery) {
        when {
            searchQuery.isNotBlank() -> categories
            selectedCatIndex == CATEGORY_ALL -> categories
            else -> listOfNotNull(categories.getOrNull(selectedCatIndex))
        }
    }

    Box(modifier = Modifier.fillMaxSize().toolzBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            DashboardTopBar(
                searchQuery    = searchQuery,
                onSearchChange = onSearchChange,
                isAiSearching  = isAiSearching,
                aiRoutes       = aiSuggestedRoutes,
                onNavigate     = onNavigate,
                categories     = categories,
                offlineState   = offlineState,
                onOfflineClick = {
                    vibrationManager?.vibrateTick()
                    if (offlineState == OfflineState.OFFLINE) onToggleOffline(false)
                    else showOfflineSheet = true
                },
                onSettingsClick = { onNavigate(Screen.Settings.route) },
            )

            // ── Scrollable body ───────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .let {
                        if (performanceMode) it
                        else it.fadingEdges(top = 12.dp, bottom = 120.dp)
                    },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {

                if (updateVersion != null) {
                    item(key = "update") {
                        StaggeredEntrance(0) {
                            UpdateBanner(updateVersion, { onNavigate(Screen.Update.route) }, onDismissUpdate)
                        }
                    }
                }

                item(key = "header") {
                    StaggeredEntrance(1) { DashboardHeader(userName, offlineState) }
                }

                if (showDashboardStats) {
                    item(key = "stats") {
                        StaggeredEntrance(2) { StatsRow(stats, onNavigate) }
                    }
                }

                if (showRecentTools && recentTools.isNotEmpty()) {
                    item(key = "recent") {
                        StaggeredEntrance(3) {
                            RecentSection(recentTools, categories, onNavigate)
                        }
                    }
                }

                if (pinnedTools.isNotEmpty()) {
                    item(key = "pinned") {
                        StaggeredEntrance(4) {
                            PinnedSection(pinnedTools, categories, onTogglePin, onNavigate)
                        }
                    }
                }

                if (showQuickNotes && notes.isNotEmpty()) {
                    item(key = "notes") {
                        StaggeredEntrance(5) { NotesSection(notes, onNavigate) }
                    }
                }

                item(key = "tools_header") {
                    StaggeredEntrance(6) {
                        AllToolsHeader(
                            categories       = categories,
                            selectedCatIndex = selectedCatIndex,
                            onCatSelect      = { vibrationManager?.vibrateTick(); selectedCatIndex = it },
                            currentView      = currentView,
                            onViewToggle     = {
                                vibrationManager?.vibrateTick()
                                currentView = if (currentView == "LIST") "DEFAULT" else "LIST"
                            },
                        )
                    }
                }

                visibleCategories.forEachIndexed { ci, cat ->
                    item(key = "cat_hdr_${cat.title}") {
                        StaggeredEntrance(ci + 7) { SectionHeader(cat.title) }
                    }
                    item(key = "cat_body_${cat.title}") {
                        if (currentView == "LIST") {
                            ToolListSection(cat.items, onNavigate, { toolForDetail = it })
                        } else {
                            ToolGridSection(cat.items, onNavigate, { toolForDetail = it })
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                item(key = "bottom_space") { Spacer(Modifier.height(160.dp)) }
            }
        }

        // ── Universal pill ────────────────────────────────────────────────────
        if (showPill) {
            UniversalPill(
                modifier            = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .navigationBarsPadding(),
                musicState          = musicState,
                musicViewModel      = musicViewModel,
                timerState          = timerState,
                timerViewModel      = timerViewModel,
                stopwatchState      = stopwatchState,
                stopwatchViewModel  = stopwatchViewModel,
                pomodoroState       = pomodoroState,
                pomodoroViewModel   = pomodoroViewModel,
                stepsState          = stepsState,
                stepsViewModel      = stepsViewModel,
                recordingState      = recordingState,
                recorderViewModel   = recorderViewModel,
                catalogState        = catalogState,
                todoViewModel       = todoViewModel,
                caffeinateViewModel = caffeinateViewModel,
                fillThePill         = fillThePill,
                onNavigate          = onNavigate,
                offlineState        = offlineState,
            )
        }

        if (showOfflineSheet) {
            OfflineSheet(onDismiss = { showOfflineSheet = false },
                onGoOnline = { onToggleOffline(false); showOfflineSheet = false })
        }

        toolForDetail?.let { tool ->
            ToolDetailSheet(
                tool        = tool,
                isPinned    = pinnedTools.contains(tool.route),
                onDismiss   = { toolForDetail = null },
                onNavigate  = { toolForDetail = null; onNavigate(it) },
                onTogglePin = { onTogglePin(tool.route) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isAiSearching: Boolean,
    aiRoutes: List<String>,
    onNavigate: (String) -> Unit,
    categories: List<ToolCategory>,
    offlineState: OfflineState,
    onOfflineClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val isOffline = offlineState == OfflineState.OFFLINE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "TOOLZ",
                    style         = MaterialTheme.typography.headlineLarge,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                )
                AnimatedContent(
                    targetState = isOffline,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { -it / 2 })
                            .togetherWith(fadeOut(tween(160)))
                    },
                    label = "topBarSubtitle",
                ) { offline ->
                    Text(
                        text       = if (offline) "Offline · local only" else "Modern utility suite",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (offline)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        letterSpacing = 0.2.sp,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Offline toggle — AnimatedContent for container-color morph
                AnimatedContent(
                    targetState = isOffline,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn())
                            .togetherWith(scaleOut() + fadeOut())
                    },
                    label = "offlineToggleBtn",
                ) { offline ->
                    FilledTonalIconButton(
                        onClick  = onOfflineClick,
                        modifier = Modifier.size(46.dp),
                        shape    = SmallExpressiveShape,
                        colors   = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (offline)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (offline)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector        = if (offline) Icons.Rounded.CloudOff else Icons.Rounded.Cloud,
                            contentDescription = if (offline) "Offline — tap to go online" else "Go offline",
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick  = onSettingsClick,
                    modifier = Modifier.size(46.dp),
                    shape    = SmallExpressiveShape,
                    colors   = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(Icons.Rounded.Settings, "Settings", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        SmartSearchBar(
            query         = searchQuery,
            onQueryChange = onSearchChange,
            isAiSearching = isAiSearching,
            aiRoutes      = aiRoutes,
            onNavigate    = onNavigate,
            categories    = categories,
            offlineState  = offlineState,
        )

        Spacer(Modifier.height(4.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SMART SEARCH BAR
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmartSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isAiSearching: Boolean,
    aiRoutes: List<String>,
    onNavigate: (String) -> Unit,
    categories: List<ToolCategory>,
    offlineState: OfflineState,
) {
    val performanceMode = LocalPerformanceMode.current
    val allTools  = remember(categories) { categories.flatMap { it.items } }
    val localHits = remember(query, allTools) {
        if (query.isBlank()) emptyList()
        else allTools.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }.take(5)
    }

    val sparkle = rememberInfiniteTransition(label = "sparkle")
    val sparkleAlpha by sparkle.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label        = "sAlpha",
    )

    val borderAnim by animateColorAsState(
        targetValue   = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        animationSpec = tween(240),
        label         = "searchBorder",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier       = Modifier.fillMaxWidth().height(58.dp),
            shape          = SquircleShape,
            color          = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            tonalElevation = 1.dp,
            border         = BorderStroke(1.dp, borderAnim),
        ) {
            Row(
                modifier          = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                    if (isAiSearching) {
                        ExpressiveCircularProgressIndicator(
                            modifier   = Modifier.size(20.dp),
                            color      = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Search, null,
                            tint     = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                BasicTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                AnimatedContent(
                                    targetState  = offlineState == OfflineState.OFFLINE,
                                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                                    label        = "phAnim",
                                ) { offline ->
                                    Text(
                                        text  = if (offline) "Search local tools…" else "Search or ask AI…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            inner()
                        }
                    },
                )

                AnimatedContent(
                    targetState = query.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.82f, animationSpec = tween(180)))
                            .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.82f, animationSpec = tween(140)))
                    },
                    label = "trailingSearch",
                ) { hasQuery ->
                    if (hasQuery) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, "Clear",
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (offlineState == OfflineState.ONLINE) {
                        Icon(
                            Icons.Rounded.AutoAwesome, null,
                            tint     = MaterialTheme.colorScheme.primary.copy(
                                alpha = if (performanceMode) 0.65f else sparkleAlpha),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Local dropdown
        AnimatedVisibility(
            visible = localHits.isNotEmpty() && aiRoutes.isEmpty(),
            enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            SearchDropdown(tools = localHits, onNavigate = onNavigate)
        }

        // AI dropdown
        AnimatedVisibility(
            visible = aiRoutes.isNotEmpty(),
            enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            val aiTools = remember(aiRoutes, allTools) {
                aiRoutes.mapNotNull { r -> allTools.find { it.route == r } }
            }
            SearchDropdown(tools = aiTools, onNavigate = onNavigate, isAi = true)
        }
    }
}

@Composable
private fun SearchDropdown(
    tools: List<ToolItem>,
    onNavigate: (String) -> Unit,
    isAi: Boolean = false,
) {
    Surface(
        modifier       = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape          = SquircleShape,
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        border         = BorderStroke(
            1.dp,
            if (isAi) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            if (isAi) {
                Row(
                    modifier              = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp))
                    Text(
                        "AI SUGGESTIONS",
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                    )
                }
            }
            tools.forEachIndexed { i, tool ->
                SearchRow(tool = tool, onClick = { onNavigate(tool.route) })
                if (i < tools.lastIndex) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                )
            }
        }
    }
}

@Composable
private fun SearchRow(tool: ToolItem, onClick: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .bouncyClick { vibrationManager?.vibrateTick(); onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(38.dp), SmallExpressiveShape, color = tool.color.copy(alpha = 0.12f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tool.title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black)
            Text(tool.description,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ArrowOutward, null,
            modifier = Modifier.size(15.dp),
            tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GREETING HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DashboardHeader(userName: String, offlineState: OfflineState) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..5   -> "GOOD NIGHT"
            in 6..11  -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else      -> "GOOD EVENING"
        }
    }
    val date = remember {
        try { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")) }
        catch (_: Exception) { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp)) {
        Text(
            greeting,
            style         = MaterialTheme.typography.labelMedium,
            fontWeight    = FontWeight.Black,
            color         = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.5.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text          = if (userName.isBlank()) "Explorer" else userName,
            style         = MaterialTheme.typography.displaySmall,
            fontWeight    = FontWeight.Black,
            letterSpacing = (-1.5).sp,
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            shape  = BouncyShape,
            color  = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
        ) {
            Text(
                text          = date.uppercase(),
                modifier      = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Black,
                color         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                letterSpacing = 1.3.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STATS ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatsRow(stats: DashboardStats, onNavigate: (String) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val battColor = when {
            stats.isBatteryCharging -> MaterialTheme.colorScheme.primary
            stats.batteryLevel < 20 -> MaterialTheme.colorScheme.error
            else                    -> MaterialTheme.colorScheme.secondary
        }
        StatCard(
            modifier = Modifier.weight(1f),
            label    = "BATTERY",
            value    = "${stats.batteryLevel}%",
            sub      = if (stats.isBatteryCharging) "CHARGING" else "DRAINING",
            icon     = if (stats.isBatteryCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Battery5Bar,
            color    = battColor,
            progress = stats.batteryLevel / 100f,
            onClick  = { onNavigate(Screen.BatteryInfo.route) },
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label    = "STORAGE",
            value    = "${stats.storageAvailableGb.toInt()}GB",
            sub      = "FREE",
            icon     = Icons.Rounded.Storage,
            color    = MaterialTheme.colorScheme.tertiary,
            progress = 1f - stats.storageUsedPercentage,
            onClick  = { onNavigate(Screen.DeviceInfo.route) },
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    color: Color,
    progress: Float,
    onClick: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick        = { vibrationManager?.vibrateClick(); onClick() },
        modifier       = modifier.height(124.dp),
        shape          = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        elevation      = 0.dp,
        border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.13f)),
    ) {
        Row(
            modifier          = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier            = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(Modifier.size(32.dp), SmallExpressiveShape, color = color.copy(alpha = 0.13f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(label,
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                }
                Column {
                    Text(value,
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace)
                    Text(sub,
                        style         = MaterialTheme.typography.labelSmall,
                        color         = color.copy(alpha = 0.8f),
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 0.8.sp)
                }
            }
            // Wavy circular progress
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                ToolzWavyCircularProgressIndicator(
                    progress   = { progress.coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxSize(),
                    color      = color,
                    trackColor = color.copy(alpha = 0.1f),
                )
                Text(
                    "${(progress.coerceIn(0f, 1f) * 100).toInt()}",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color      = color,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RECENT TOOLS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecentSection(
    recentTools: List<String>,
    categories: List<ToolCategory>,
    onNavigate: (String) -> Unit,
) {
    val all = remember(categories) { categories.flatMap { it.items } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        SectionHeader("RECENTLY USED")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 2.dp),
        ) {
            items(recentTools.take(8)) { route ->
                val tool = all.find { it.route == route } ?: return@items
                RecentItem(tool, onNavigate)
            }
        }
    }
}

@Composable
private fun RecentItem(tool: ToolItem, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Column(
        modifier            = Modifier
            .width(72.dp)
            .bouncyClick { vibrationManager?.vibrateClick(); onNavigate(tool.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape    = SquircleShape,
            color    = tool.color.copy(alpha = 0.12f),
            border   = BorderStroke(1.2.dp, tool.color.copy(alpha = 0.2f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            tool.title,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
            lineHeight = 13.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PINNED TOOLS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PinnedSection(
    pinnedTools: Set<String>,
    categories: List<ToolCategory>,
    onTogglePin: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val all = remember(categories) { categories.flatMap { it.items } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        SectionHeader("QUICK ACCESS")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 2.dp),
        ) {
            items(pinnedTools.toList()) { route ->
                val tool = all.find { it.route == route } ?: return@items
                PinnedItem(tool, onTogglePin, onNavigate)
            }
        }
    }
}

@Composable
private fun PinnedItem(
    tool: ToolItem,
    onTogglePin: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick     = { vibrationManager?.vibrateClick(); onNavigate(tool.route) },
        onLongClick = { vibrationManager?.vibrateLongClick(); onTogglePin(tool.route) },
        modifier    = Modifier.size(width = 136.dp, height = 96.dp),
        shape       = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
        border      = BorderStroke(1.2.dp, tool.color.copy(alpha = 0.2f)),
        elevation   = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Color accent bar
            Box(Modifier.fillMaxWidth().height(4.dp).background(tool.color.copy(alpha = 0.8f)))
            Column(
                modifier            = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Surface(Modifier.size(36.dp), SmallExpressiveShape, color = tool.color.copy(alpha = 0.14f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(18.dp))
                        }
                    }
                    Icon(Icons.Rounded.PushPin, null,
                        modifier = Modifier.size(11.dp),
                        tint     = tool.color.copy(alpha = 0.4f))
                }
                Text(tool.title,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTES SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NotesSection(notes: List<Note>, onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        SectionHeader("SMART NOTES")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 2.dp),
        ) {
            items(notes.take(6)) { note ->
                QuickNoteCard(note, onNavigate)
            }
            item {
                Surface(
                    modifier = Modifier
                        .size(width = 104.dp, height = 176.dp)
                        .bouncyClick { onNavigate(Screen.Notepad.route) },
                    shape    = SquircleShape,
                    color    = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.36f),
                    border   = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape    = CircleShape,
                                color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp))
                                }
                            }
                            Text("VIEW ALL",
                                style         = MaterialTheme.typography.labelMedium,
                                fontWeight    = FontWeight.Black,
                                color         = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNoteCard(note: Note, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val noteColor = Color(note.color)
    val isDark    = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardAlpha = if (isDark) 0.22f else 0.1f

    val cardW = when {
        note.attachedAudioUri != null -> 280.dp
        note.attachedImageUri != null -> 248.dp
        else -> 220.dp
    }
    val cardH = if (note.attachedImageUri != null) 196.dp else 176.dp
    val shape = when {
        note.attachedAudioUri != null -> RoundedCornerShape(32.dp, 20.dp, 32.dp, 20.dp)
        note.attachedImageUri != null -> RoundedCornerShape(28.dp, 14.dp, 28.dp, 14.dp)
        else -> SquircleShape
    }

    ExpressiveCard(
        onClick    = { vibrationManager?.vibrateClick(); onNavigate("${Screen.Notepad.route}?initialNoteId=${note.id}") },
        modifier   = Modifier.size(width = cardW, height = cardH),
        shape      = shape,
        containerColor = noteColor.copy(alpha = cardAlpha),
        border     = BorderStroke(1.5.dp, noteColor.copy(alpha = 0.3f)),
        elevation  = 0.dp,
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top,
                ) {
                    Text(
                        note.title.ifBlank { "UNTITLED" },
                        style         = MaterialTheme.typography.titleMedium,
                        fontWeight    = FontWeight.Black,
                        maxLines      = 1,
                        overflow      = TextOverflow.Ellipsis,
                        modifier      = Modifier.weight(1f),
                        letterSpacing = (-0.5).sp,
                    )
                    if (note.isPinned) {
                        Icon(Icons.Rounded.PushPin, null,
                            modifier = Modifier.size(13.dp), tint = noteColor)
                    }
                }
                note.attachedImageUri?.let { uri ->
                    AsyncImage(
                        model        = uri,
                        contentDescription = null,
                        modifier     = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    note.content,
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines   = if (note.attachedImageUri != null) 2 else 4,
                    overflow   = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp,
                )
            }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (note.attachedAudioUri != null) Icon(Icons.Rounded.Mic, null,
                        modifier = Modifier.size(12.dp), tint = noteColor)
                    if (note.attachedImageUri != null) Icon(Icons.Rounded.Image, null,
                        modifier = Modifier.size(12.dp), tint = noteColor)
                }
                Text(
                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(note.timestamp)).uppercase(),
                    style         = MaterialTheme.typography.labelSmall,
                    color         = noteColor.copy(alpha = 0.6f),
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 0.7.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ALL TOOLS HEADER + CATEGORY CHIP FILTER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AllToolsHeader(
    categories: List<ToolCategory>,
    selectedCatIndex: Int,
    onCatSelect: (Int) -> Unit,
    currentView: String,
    onViewToggle: () -> Unit,
) {
    val totalTools = remember(categories) { categories.sumOf { it.items.size } }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ALL TOOLS",
                    style         = MaterialTheme.typography.titleLarge,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 1.5.sp)
                Text("$totalTools utilities",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    fontWeight = FontWeight.Bold)
            }
            FilledTonalIconButton(
                onClick  = onViewToggle,
                modifier = Modifier.size(40.dp),
                shape    = SmallExpressiveShape,
                colors   = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AnimatedContent(
                    targetState = currentView == "LIST",
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                            .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120)))
                    },
                    label = "viewIcon",
                ) { isList ->
                    Icon(
                        if (isList) Icons.Rounded.GridView else Icons.Rounded.ViewList,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding        = PaddingValues(horizontal = 2.dp),
        ) {
            item {
                CategoryChip("All", selectedCatIndex == CATEGORY_ALL) { onCatSelect(CATEGORY_ALL) }
            }
            items(categories.size) { i ->
                val label = categories[i].title.split(" & ", " ").first().let {
                    if (it.length > 8) it.take(7) + "…" else it
                }
                CategoryChip(label, selectedCatIndex == i) { onCatSelect(i) }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    ExpressiveFilterChip(
        selected = selected,
        onClick  = onClick,
        label    = {
            Text(label,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                letterSpacing = 0.2.sp)
        },
        shape  = BouncyShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled             = true,
            selected            = selected,
            borderColor         = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            selectedBorderColor = Color.Transparent,
        ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER — two-dot accent
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier          = Modifier.padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Surface(Modifier.size(width = 22.dp, height = 5.dp), CircleShape,
                color = MaterialTheme.colorScheme.primary) {}
            Surface(Modifier.size(width = 7.dp, height = 5.dp), CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)) {}
        }
        Spacer(Modifier.width(12.dp))
        Text(title,
            style         = MaterialTheme.typography.titleSmall,
            fontWeight    = FontWeight.Black,
            letterSpacing = 1.6.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL GRID — left-accent-strip cards, 2/3-col responsive
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ToolGridSection(
    items: List<ToolItem>,
    onNavigate: (String) -> Unit,
    onLongClick: (ToolItem) -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cols = if (maxWidth >= 600.dp) 3 else 2
        Column(modifier = Modifier.fillMaxWidth()) {
            items.chunked(cols).forEachIndexed { ri, row ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEachIndexed { ci, item ->
                        val si = ri * cols + ci
                        StaggeredEntrance(
                            index    = si,
                            modifier = Modifier.weight(1f),
                            enter    = fadeIn(tween(360, si * 42)) +
                                    slideInVertically(tween(400, si * 42)) { 28 } +
                                    scaleIn(
                                        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                                        initialScale  = 0.90f,
                                    ),
                        ) {
                            ToolGridCard(item, Modifier.fillMaxWidth(), onNavigate) { onLongClick(item) }
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
                if (ri < items.chunked(cols).lastIndex) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun ToolGridCard(
    item: ToolItem,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
    onLongClick: () -> Unit = {},
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick     = { vibrationManager?.vibrateClick(); onNavigate(item.route) },
        onLongClick = { vibrationManager?.vibrateLongClick(); onLongClick() },
        modifier    = modifier.height(128.dp),
        shape       = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
        elevation   = 0.dp,
        border      = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.13f)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left accent strip
            Box(Modifier.width(4.dp).fillMaxHeight().background(item.color.copy(alpha = 0.78f)))
            Column(
                modifier            = Modifier.weight(1f).fillMaxHeight().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top,
                ) {
                    Surface(Modifier.size(44.dp), SmallExpressiveShape, color = item.color.copy(alpha = 0.13f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = item.color, modifier = Modifier.size(23.dp))
                        }
                    }
                    Icon(Icons.Rounded.ArrowOutward, null,
                        modifier = Modifier.size(14.dp).alpha(0.2f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title,
                        style         = MaterialTheme.typography.labelLarge,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 0.3.sp,
                        maxLines      = 1,
                        overflow      = TextOverflow.Ellipsis)
                    Text(item.description,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL LIST — full-width rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ToolListSection(
    items: List<ToolItem>,
    onNavigate: (String) -> Unit,
    onLongClick: (ToolItem) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { i, item ->
            StaggeredEntrance(i) {
                ToolListCard(item, onNavigate) { onLongClick(item) }
            }
        }
    }
}

@Composable
fun ToolListCard(
    item: ToolItem,
    onNavigate: (String) -> Unit,
    onLongClick: () -> Unit = {},
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick     = { vibrationManager?.vibrateClick(); onNavigate(item.route) },
        onLongClick = { vibrationManager?.vibrateLongClick(); onLongClick() },
        modifier    = Modifier.fillMaxWidth().height(80.dp),
        shape       = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
        elevation   = 0.dp,
        border      = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.13f)),
    ) {
        Row(
            modifier          = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(Modifier.size(50.dp), SmallExpressiveShape, color = item.color.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = item.color, modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(item.description,
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null,
                modifier = Modifier.size(14.dp).alpha(0.26f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UPDATE BANNER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UpdateBanner(version: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick    = { vibrationManager?.vibrateClick(); onUpdate() },
        modifier   = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape      = SquircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        elevation  = 0.dp,
        border     = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier              = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(Modifier.size(48.dp), SmallExpressiveShape, color = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null,
                            modifier = Modifier.size(24.dp),
                            tint     = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Column {
                    Text("NEW VERSION READY",
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = MaterialTheme.colorScheme.onPrimaryContainer,
                        letterSpacing = 1.2.sp)
                    Text("Upgrade to v$version",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
            }
            IconButton(
                onClick  = { vibrationManager?.vibrateClick(); onDismiss() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)),
            ) {
                Icon(Icons.Rounded.Close, null,
                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UNIVERSAL PILL
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UniversalPill(
    modifier: Modifier = Modifier,
    musicState: MusicUiState,
    musicViewModel: MusicPlayerViewModel,
    timerState: TimerState,
    timerViewModel: TimerViewModel,
    stopwatchState: StopwatchState,
    stopwatchViewModel: StopwatchViewModel,
    pomodoroState: PomodoroState,
    pomodoroViewModel: PomodoroViewModel,
    stepsState: StepState,
    stepsViewModel: StepCounterViewModel,
    recordingState: RecordingState,
    recorderViewModel: VoiceRecorderViewModel,
    catalogState: CatalogUiState,
    todoViewModel: TodoViewModel,
    caffeinateViewModel: CaffeinateViewModel,
    fillThePill: Boolean,
    onNavigate: (String) -> Unit,
    offlineState: OfflineState,
) {
    val performanceMode  = LocalPerformanceMode.current
    val todoState        by todoViewModel.uiState.collectAsStateWithLifecycle()
    val isCaffeinated    by caffeinateViewModel.isServiceRunning.collectAsStateWithLifecycle()
    val caffeinateMs     by caffeinateViewModel.elapsedTime.collectAsStateWithLifecycle()

    val appTips = remember(offlineState) {
        listOfNotNull(
            if (offlineState == OfflineState.ONLINE)
                AppTip("Talk to AI agents", "AI Assistant", Icons.Rounded.AutoAwesome,
                    Screen.AiAssistant.route, Color(0xFF9C27B0))
            else null,
            AppTip("Express yourself",  "Notepad",       Icons.Rounded.EditNote,           Screen.Notepad.route,        Color(0xFFFF9800)),
            AppTip("Convert any file",  "File Converter",Icons.Rounded.Transform,           Screen.FileConverter.route,  Color(0xFF2196F3)),
            AppTip("Manage your time",  "Focus Flow",    Icons.Rounded.CenterFocusStrong,   Screen.FocusFlow.route,      Color(0xFF4CAF50)),
            AppTip("Stay on track",     "To-Do List",    Icons.AutoMirrored.Rounded.PlaylistAddCheck, Screen.Todo.route, Color(0xFF673AB7)),
            AppTip("Deep work session", "Pomodoro",      Icons.Rounded.AvTimer,             Screen.Pomodoro.route,       Color(0xFFF44336)),
            AppTip("Free up space",     "File Cleaner",  Icons.Rounded.CleaningServices,    Screen.FileCleaner.route,    Color(0xFF00BCD4)),
            AppTip("Screen stays on",   "Caffeinate",    Icons.Rounded.Coffee,              Screen.Caffeinate.route,     Color(0xFF795548)),
        )
    }

    val pages = remember(
        musicState, timerState, stopwatchState, pomodoroState, stepsState,
        recordingState, todoState.tasks, isCaffeinated,
        catalogState.downloadingTracks, fillThePill,
    ) {
        buildList {
            if (catalogState.downloadingTracks.isNotEmpty())
                add(PillPage.CatalogDownload(
                    catalogState.downloadingTracks.values.average().toFloat(),
                    catalogState.downloadingTracks.size))
            if (musicState.isPlaying || musicState.currentTrack != null) add(PillPage.Music)
            if (timerState.isRunning || timerState.remainingTime > 0)   add(PillPage.Timer)
            if (stopwatchState.isRunning || stopwatchState.elapsedTime > 0) add(PillPage.Stopwatch)
            if (pomodoroState.isRunning)                                add(PillPage.Pomodoro)
            if (recordingState.isRecording || recordingState.isPaused)  add(PillPage.Recorder)
            if (todoState.tasks.isNotEmpty())                           add(PillPage.Todo)
            if (isCaffeinated)                                          add(PillPage.Caffeinate)
            if (stepsState.isEnabledInSettings)                         add(PillPage.Steps)
            if (isEmpty() && fillThePill)
                appTips.shuffled().take(3).forEach { add(PillPage.Tip(it)) }
        }
    }

    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isActive   = musicState.isPlaying || timerState.isRunning ||
            stopwatchState.isRunning || pomodoroState.isRunning ||
            recordingState.isRecording || isCaffeinated

    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary  = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier        = modifier.padding(horizontal = 18.dp).fillMaxWidth().height(88.dp),
        shape           = SquircleShape,
        color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation  = 12.dp,
        shadowElevation = if (performanceMode) 0.dp else 28.dp,
        border          = BorderStroke(
            width = if (isActive) 2.dp else 1.5.dp,
            brush = if (isActive && !performanceMode)
                Brush.sweepGradient(listOf(
                    primary.copy(alpha = 0.9f),
                    secondary.copy(alpha = 0.55f),
                    tertiary.copy(alpha = 0.5f),
                    primary.copy(alpha = 0.9f),
                ))
            else SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (pages.size > 1) 14.dp else 0.dp),
            ) { pi ->
                when (val page = pages[pi]) {
                    is PillPage.Music           -> MusicPillContent(musicState, musicViewModel, onNavigate)
                    is PillPage.Timer           -> TimerPillContent(timerState, timerViewModel, onNavigate)
                    is PillPage.Stopwatch       -> StopwatchPillContent(stopwatchState, stopwatchViewModel, onNavigate)
                    is PillPage.Pomodoro        -> PomodoroPillContent(pomodoroState, pomodoroViewModel, onNavigate)
                    is PillPage.Steps           -> StepsPillContent(stepsState, stepsViewModel, onNavigate)
                    is PillPage.Recorder        -> RecorderPillContent(recordingState, recorderViewModel, onNavigate)
                    is PillPage.Todo            -> TodoPillContent(todoState.tasks.firstOrNull(), onNavigate)
                    is PillPage.CatalogDownload -> CatalogDownloadPillContent(page.progress, page.count, onNavigate)
                    is PillPage.Caffeinate      -> CaffeinatePillContent(caffeinateMs, onNavigate)
                    is PillPage.Tip             -> TipPillContent(page.tip, onNavigate)
                }
            }

            // Spring-animated dot indicator
            if (pages.size > 1) {
                Row(
                    modifier              = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pages.size) { i ->
                        val active = pagerState.currentPage == i
                        val dotWidth by animateDpAsState(
                            targetValue   = if (active) 20.dp else 5.dp,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                            label         = "dotW$i",
                        )
                        val dotColor by animateColorAsState(
                            targetValue   = if (active) primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            animationSpec = tween(260),
                            label         = "dotC$i",
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .size(width = dotWidth, height = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PILL PAGE CONTENT
// ─────────────────────────────────────────────────────────────────────────────

// Shared icon container used by each pill page
@Composable
private fun PillIcon(color: Color, icon: ImageVector) {
    Surface(
        modifier = Modifier.size(50.dp),
        shape    = RoundedCornerShape(22.dp),
        color    = color.copy(alpha = 0.55f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun TipPillContent(tip: AppTip, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Row(
        modifier          = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            .bouncyClick { vibrationManager?.vibrateTick(); onNavigate(tip.route) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(50.dp), SmallExpressiveShape, color = tip.color.copy(alpha = 0.14f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tip.icon, null, tint = tip.color, modifier = Modifier.size(25.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tip.title,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis)
            Text(tip.description,
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Rounded.ArrowOutward, null,
            modifier = Modifier.size(19.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    }
}

@Composable
fun MusicPillContent(state: MusicUiState, vm: MusicPlayerViewModel, onNavigate: (String) -> Unit) {
    val tr  = rememberInfiniteTransition(label = "albumRot")
    val rot by tr.animateFloat(0f, 360f,
        infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "rot")
    val c   = MaterialTheme.colorScheme
    Row(
        modifier          = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            .bouncyClick { onNavigate(Screen.MusicPlayer.route) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val artShape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(18.dp)
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model        = state.currentTrack?.thumbnailUri,
                contentDescription = null,
                modifier     = Modifier.size(52.dp)
                    .rotate(if (state.isPlaying && state.rotationEnabled) rot else 0f)
                    .clip(artShape),
                contentScale = ContentScale.Crop,
            )
            if (state.isPlaying) {
                Surface(color = Color.Black.copy(alpha = 0.38f),
                    modifier = Modifier.size(52.dp).clip(artShape)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.currentTrack?.title ?: "Not Playing",
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.currentTrack?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurface.copy(alpha = 0.62f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        }
        FilledTonalIconButton(
            onClick  = { vm.togglePlayPause() },
            modifier = Modifier.size(46.dp), shape = CircleShape,
            colors   = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.primaryContainer),
        ) {
            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                tint = c.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun TimerPillContent(state: TimerState, vm: TimerViewModel, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.Timer.route) }, verticalAlignment = Alignment.CenterVertically) {
        PillIcon(c.primaryContainer, Icons.Rounded.Timer)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val s = state.remainingTime / 1000
            Text(String.format("%02d:%02d", s / 60, s % 60),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            val p = if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime else 0f
            ExpressiveLinearProgressIndicator({ p },
                Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = c.primary, trackColor = c.primary.copy(alpha = 0.13f))
        }
        Spacer(Modifier.width(10.dp))
        FilledTonalIconButton(onClick = { vm.toggleStartStop() }, modifier = Modifier.size(46.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.primaryContainer)) {
            Icon(if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                tint = c.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun StopwatchPillContent(state: StopwatchState, vm: StopwatchViewModel, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.Stopwatch.route) }, verticalAlignment = Alignment.CenterVertically) {
        PillIcon(c.tertiaryContainer, Icons.Rounded.Timer)
        Spacer(Modifier.width(14.dp))
        Text(
            String.format("%02d:%02d.%01d",
                state.elapsedTime / 60000, (state.elapsedTime % 60000) / 1000, (state.elapsedTime % 1000) / 100),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { vm.toggleStartStop() }, modifier = Modifier.size(46.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.tertiaryContainer)) {
            Icon(if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                tint = c.tertiary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun PomodoroPillContent(state: PomodoroState, vm: PomodoroViewModel, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.Pomodoro.route) }, verticalAlignment = Alignment.CenterVertically) {
        PillIcon(c.errorContainer, Icons.Rounded.AvTimer)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (state.mode != PomodoroMode.WORK) "Break Time" else "Deep Focus",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = c.error)
            val s = state.remainingTime / 1000
            Text(String.format("%02d:%02d", s / 60, s % 60),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
        FilledTonalIconButton(onClick = { vm.toggleStartStop() }, modifier = Modifier.size(46.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.errorContainer)) {
            Icon(if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                tint = c.error, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun StepsPillContent(state: StepState, vm: StepCounterViewModel, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.StepCounter.route) }, verticalAlignment = Alignment.CenterVertically) {
        PillIcon(c.primaryContainer, Icons.AutoMirrored.Rounded.DirectionsRun)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${state.steps}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            ExpressiveLinearProgressIndicator(
                { (state.steps / 10_000f).coerceIn(0f, 1f) },
                Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = c.primary, trackColor = c.primary.copy(alpha = 0.13f))
        }
        Spacer(Modifier.width(10.dp))
        FilledTonalIconButton(onClick = { onNavigate(Screen.StepCounter.route) }, modifier = Modifier.size(46.dp),
            shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = c.primaryContainer.copy(alpha = 0.55f))) {
            Icon(Icons.Rounded.ArrowOutward, null, tint = c.primary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun RecorderPillContent(state: RecordingState, vm: VoiceRecorderViewModel, onNavigate: (String) -> Unit) {
    val c   = MaterialTheme.colorScheme
    val tr  = rememberInfiniteTransition(label = "recBlip")
    val bAlpha by tr.animateFloat(1f, 0.25f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "blip")
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.VoiceRecorder.route) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).background(c.errorContainer.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(18.dp).graphicsLayer { alpha = bAlpha }.background(c.error, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Text(if (state.isRecording) "Recording…" else "Paused",
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { vm.stopRecording() }, modifier = Modifier.size(46.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.errorContainer)) {
            Icon(Icons.Rounded.Stop, null, tint = c.error, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun TodoPillContent(task: com.frerox.toolz.data.todo.TaskEntry?, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.Todo.route) }, verticalAlignment = Alignment.CenterVertically) {
        PillIcon(c.primaryContainer, Icons.Rounded.TaskAlt)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Next Priority", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black, color = c.primary)
            Text(task?.title ?: "Nothing planned", style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalIconButton(onClick = { onNavigate(Screen.Todo.route) }, modifier = Modifier.size(46.dp),
            shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = c.primaryContainer.copy(alpha = 0.55f))) {
            Icon(Icons.Rounded.ArrowOutward, null, tint = c.primary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun CatalogDownloadPillContent(progress: Float, count: Int, onNavigate: (String) -> Unit) {
    val c   = MaterialTheme.colorScheme
    val tr  = rememberInfiniteTransition(label = "dlPulse")
    val pls by tr.animateFloat(1f, 1.12f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.MusicPlayer.route) }, verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(50.dp).graphicsLayer { scaleX = pls; scaleY = pls },
            RoundedCornerShape(22.dp), color = c.primaryContainer.copy(alpha = 0.55f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FileDownload, null, tint = c.primary, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("DOWNLOADING", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black, color = c.primary, letterSpacing = 0.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveLinearProgressIndicator({ progress },
                    Modifier.weight(1f).height(4.dp).clip(CircleShape),
                    color = c.primary, trackColor = c.primary.copy(alpha = 0.13f))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(10.dp))
        Surface(Modifier.size(28.dp), CircleShape, color = c.primary) {
            Box(contentAlignment = Alignment.Center) {
                Text(count.toString(), color = c.onPrimary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun CaffeinatePillContent(ms: Long, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        .bouncyClick { onNavigate(Screen.Caffeinate.route) }, verticalAlignment = Alignment.CenterVertically) {
        PillIcon(c.secondaryContainer, Icons.Rounded.Coffee)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Awake Mode", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black, color = c.secondary)
            val h = ms / 3600000; val m = (ms % 3600000) / 60000; val s = (ms % 60000) / 1000
            Text(if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
        FilledTonalIconButton(onClick = { onNavigate(Screen.Caffeinate.route) }, modifier = Modifier.size(46.dp),
            shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = c.secondaryContainer.copy(alpha = 0.55f))) {
            Icon(Icons.Rounded.ArrowOutward, null, tint = c.secondary, modifier = Modifier.size(22.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL DETAIL BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailSheet(
    tool: ToolItem,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onTogglePin: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle       = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Icon + title row
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier              = Modifier.padding(bottom = 24.dp),
            ) {
                Surface(Modifier.size(64.dp), MediumExpressiveShape, color = tool.color.copy(alpha = 0.13f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(32.dp))
                    }
                }
                Column {
                    Text(tool.title,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black)
                    Text(tool.description,
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Medium)
                }
            }

            // Pin toggle
            Surface(
                onClick  = { vibrationManager?.vibrateTick(); onTogglePin() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape    = SquircleShape,
                color    = if (isPinned)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border   = BorderStroke(
                    1.dp,
                    if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
                ),
            ) {
                Row(
                    modifier              = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            Icons.Rounded.PushPin, null,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (isPinned) "Pinned to Quick Access" else "Pin to Quick Access",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color      = if (isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked         = isPinned,
                        onCheckedChange = { vibrationManager?.vibrateTick(); onTogglePin() },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Open button
            ToolzExpressiveButton(
                onClick  = { vibrationManager?.vibrateClick(); onNavigate(tool.route) },
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape    = SquircleShape,
            ) {
                Text("OPEN UTILITY", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.size(18.dp))
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("DISMISS", fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OFFLINE BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSheet(onDismiss: () -> Unit, onGoOnline: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle       = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f))
            )
        },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Surface(Modifier.size(80.dp), MediumExpressiveShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CloudOff, null,
                        modifier = Modifier.size(40.dp),
                        tint     = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Offline Mode Active",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign  = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "AI Assistant and Web Search are hidden to ensure 100% privacy, save battery, and reduce data usage.",
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(32.dp))
            ToolzExpressiveButton(
                onClick  = { vibrationManager?.vibrateClick(); onGoOnline() },
                modifier = Modifier.fillMaxWidth().height(62.dp),
            ) {
                Icon(Icons.Rounded.Cloud, null)
                Spacer(Modifier.width(10.dp))
                Text("GO ONLINE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("STAY OFFLINE", fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEWS
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Dashboard — Light", showBackground = true, backgroundColor = 0xFFF8F9FA)
@Composable private fun DashboardPreviewLight() { ToolzTheme(darkTheme = false) { _PreviewScaffold() } }

@Preview(
    name = "Dashboard — Dark", showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_NIGHT_MASK,
)
@Composable private fun DashboardPreviewDark() { ToolzTheme(darkTheme = true) { _PreviewScaffold() } }

@Composable
private fun _PreviewScaffold() {
    val cats = listOf(
        ToolCategory("SMART FLOW & AI", listOf(
            ToolItem("AI Assistant",  Icons.Rounded.AutoAwesome, "ai",    "Gemini Flash AI",  Color(0xFF8E24AA)),
            ToolItem("Focus Flow",    Icons.Rounded.Toll,        "focus", "Flow insights",    Color(0xFF1976D2)),
            ToolItem("Todo List",     Icons.Rounded.TaskAlt,     "todo",  "Bouncy tasks",     Color(0xFF43A047)),
            ToolItem("Notepad",       Icons.Rounded.Description, "note",  "Quick notes",      Color(0xFFFDD835)),
        )),
        ToolCategory("SENSORS & VISION", listOf(
            ToolItem("Compass",       Icons.Rounded.Explore,     "compass","Navigation",      Color(0xFF00897B)),
            ToolItem("Speedometer",   Icons.Rounded.Speed,       "speed", "GPS Speed",        Color(0xFF1976D2)),
            ToolItem("Bubble Level",  Icons.Rounded.Architecture,"level", "Leveling",         Color(0xFF7CB342)),
            ToolItem("Altimeter",     Icons.Rounded.Terrain,     "alt",   "Altitude",         Color(0xFF795548)),
        )),
    )
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardTopBar(
                searchQuery    = "",
                onSearchChange = {},
                isAiSearching  = false,
                aiRoutes       = emptyList(),
                onNavigate     = {},
                categories     = cats,
                offlineState   = OfflineState.ONLINE,
                onOfflineClick = {},
                onSettingsClick = {},
            )
            LazyColumn(
                modifier       = Modifier.weight(1f).fadingEdges(bottom = 100.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                item { DashboardHeader("Explorer", OfflineState.ONLINE) }
                item {
                    StatsRow(DashboardStats(
                        batteryLevel         = 72,
                        isBatteryCharging    = true,
                        storageUsedPercentage = 0.6f,
                        storageAvailableGb   = 28.4,
                    ), {})
                }
                item { AllToolsHeader(cats, CATEGORY_ALL, {}, "DEFAULT", {}) }
                cats.forEach { cat ->
                    item { SectionHeader(cat.title) }
                    item { ToolGridSection(cat.items, {}); Spacer(Modifier.height(4.dp)) }
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
        // Static pill preview
        Surface(
            modifier        = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .fillMaxWidth()
                .height(88.dp),
            shape           = SquircleShape,
            color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation  = 12.dp,
            border          = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        ) {
            Row(
                modifier          = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(Modifier.size(50.dp), SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null,
                            tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(25.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Talk to AI agents",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                    Text("AI Assistant",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Icon(Icons.Rounded.ArrowOutward, null,
                    modifier = Modifier.size(19.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}