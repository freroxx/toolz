/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                  TOOLZ — DashboardScreen.kt                                 ║
 * ║            Material 3 Expressive — Full Overhaul                            ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  HANDOFF MEMO (for the next Claude instance)                                ║
 * ║                                                                             ║
 * ║  1. STYLING: All tool cards use SquircleShape (28dp) as the primary        ║
 * ║     container, BouncyShape (32dp) for pinned/recent chips, and              ║
 * ║     SmallExpressiveShape (20dp) for icon badges; backgrounds are all        ║
 * ║     semi-transparent M3 surface roles (surfaceContainerHigh @ 40% alpha)   ║
 * ║     with a 1dp outlineVariant border at 15% opacity for layered depth;      ║
 * ║     the featured carousel uses full tool.color gradient fills over          ║
 * ║     MaterialTheme.shapes.large masked clip cards.                           ║
 * ║                                                                             ║
 * ║  2. CARD LAYOUT: The grid is adaptive — 2 columns on phones (<600dp),      ║
 * ║     3 columns on tablets (≥600dp) via BoxWithConstraints; each grid item    ║
 * ║     is individually wrapped in StaggeredEntrance with delay offset          ║
 * ║     (rowIndex * columns + colIndex) * 40ms and                              ║
 * ║     spring(DampingRatioLowBouncy, StiffnessMediumLow) physics; stat cards  ║
 * ║     show ExpressiveWavyLinearProgressIndicator for battery/storage metrics. ║
 * ║                                                                             ║
 * ║  3. INTERACTIONS: Every tap calls LocalVibrationManager.vibrateClick() for ║
 * ║     primary actions, vibrateTick() for toggles/secondary; a                ║
 * ║     HorizontalFloatingToolbar (exitAlwaysScrollBehavior) anchored at        ║
 * ║     bottom-start handles Settings + contextual actions and collapses on     ║
 * ║     scroll-down; the ToolzConnectedButtonGroup in the content area drives   ║
 * ║     in-session Grid/List mode switching without touching persisted prefs.   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

package com.frerox.toolz.ui.screens.dashboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.FloatingToolbarExitDirection.Companion.Bottom
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
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
// DATA CLASSES
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// DASHBOARD SCREEN ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
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
    catalogViewModel: CatalogViewModel = hiltViewModel(),
    settingsRepository: SettingsRepository
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current

    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val catalogState by catalogViewModel.uiState.collectAsStateWithLifecycle()
    val timerState by timerViewModel.uiState.collectAsStateWithLifecycle()
    val stopwatchState by stopwatchViewModel.uiState.collectAsStateWithLifecycle()
    val pomodoroState by pomodoroViewModel.uiState.collectAsStateWithLifecycle()
    val stepsState by stepsViewModel.uiState.collectAsStateWithLifecycle()
    val recordingState by recorderViewModel.uiState.collectAsStateWithLifecycle()

    val showPillSetting by settingsRepository.showToolzPill.collectAsState(initial = true)
    val hapticEnabled by settingsRepository.hapticFeedback.collectAsState(initial = true)
    val fillThePillEnabled by settingsRepository.fillThePillEnabled.collectAsState(initial = true)
    val userName by settingsRepository.userName.collectAsState(initial = "")
    val pinnedTools by settingsRepository.pinnedTools.collectAsState(initial = emptySet())
    val recentTools by settingsRepository.recentTools.collectAsState(initial = emptyList())
    val showRecentTools by settingsRepository.showRecentTools.collectAsState(initial = true)
    val showQuickNotes by settingsRepository.showQuickNotes.collectAsState(initial = true)
    val showDashboardStats by settingsRepository.showDashboardStats.collectAsState(initial = false)
    val dashboardView by settingsRepository.dashboardView.collectAsState(initial = "DEFAULT")

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAiSearching by viewModel.isAiSearching.collectAsStateWithLifecycle()
    val aiSuggestedRoutes by viewModel.aiSuggestedRoutes.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val updateVersion by viewModel.updateAvailableVersion.collectAsStateWithLifecycle(initialValue = null)
    val offlineState by viewModel.offlineState.collectAsStateWithLifecycle()
    val notes by notepadViewModel.notes.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val navAction = remember {
        { route: String ->
            vibrationManager?.vibrateClick()
            viewModel.addRecentTool(route)
            onNavigate(route)
        }
    }

    DashboardContent(
        onNavigate = navAction,
        onTogglePin = {
            vibrationManager?.vibrateTick()
            viewModel.togglePinnedTool(it)
        },
        performanceMode = performanceMode,
        vibrationManager = vibrationManager,
        userName = userName,
        pinnedTools = pinnedTools,
        recentTools = recentTools,
        showRecentTools = showRecentTools,
        showQuickNotes = showQuickNotes,
        showDashboardStats = showDashboardStats,
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
        todoViewModel = todoViewModel,
        caffeinateViewModel = caffeinateViewModel,
        catalogState = catalogState,
        catalogViewModel = catalogViewModel,
        showPillSetting = showPillSetting,
        fillThePillEnabled = fillThePillEnabled,
        searchQuery = searchQuery,
        isAiSearching = isAiSearching,
        aiSuggestedRoutes = aiSuggestedRoutes,
        onSearchQueryChange = viewModel::updateSearchQuery,
        updateVersion = updateVersion,
        onDismissUpdate = viewModel::dismissUpdate,
        notes = notes,
        categories = categories,
        offlineState = offlineState,
        onToggleOfflineMode = viewModel::toggleOfflineMode,
        stats = stats
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DASHBOARD CONTENT — PRIMARY LAYOUT (OVERHAULED)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun DashboardContent(
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    performanceMode: Boolean,
    vibrationManager: VibrationManager?,
    userName: String,
    pinnedTools: Set<String>,
    recentTools: List<String>,
    showRecentTools: Boolean,
    showQuickNotes: Boolean,
    showDashboardStats: Boolean,
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
    todoViewModel: TodoViewModel,
    caffeinateViewModel: CaffeinateViewModel,
    catalogState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    showPillSetting: Boolean,
    fillThePillEnabled: Boolean,
    searchQuery: String,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    onSearchQueryChange: (String) -> Unit,
    updateVersion: String?,
    onDismissUpdate: () -> Unit,
    notes: List<Note>,
    categories: List<ToolCategory>,
    offlineState: OfflineState,
    onToggleOfflineMode: (Boolean) -> Unit,
    stats: DashboardStats
) {
    var showOfflineModal by remember { mutableStateOf(false) }
    var selectedToolForDetail by remember { mutableStateOf<ToolItem?>(null) }

    // In-session view mode override — driven by the connected button group in the list
    var currentView by remember(dashboardView) { mutableStateOf(dashboardView) }

    // Floating toolbar scroll behavior: collapses on scroll-down, expands on scroll-up
    val floatingToolbarBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = Bottom)
    val toolbarExpanded by remember {
        derivedStateOf {
            val state = floatingToolbarBehavior.state
            if (state.offsetLimit != 0f) state.offset / state.offsetLimit < 0.5f else true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top App Bar + Search ──────────────────────────────────────────
            DashboardTopBar(
                onSettingsClick = { onNavigate(Screen.Settings.route) },
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isAiSearching = isAiSearching,
                aiSuggestedRoutes = aiSuggestedRoutes,
                onNavigate = onNavigate,
                categories = categories,
                offlineState = offlineState
            )

            // ── Main Scrollable Content ───────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .then(
                        if (performanceMode) Modifier
                        else Modifier.fadingEdges(top = 16.dp, bottom = 130.dp)
                    )
                    .nestedScroll(floatingToolbarBehavior),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {

                // Update banner
                if (updateVersion != null) {
                    item(key = "update_card") {
                        StaggeredEntrance(index = 0) {
                            UpdateCardExpressive(
                                version = updateVersion,
                                onUpdate = { onNavigate(Screen.Update.route) },
                                onDismiss = onDismissUpdate
                            )
                        }
                    }
                }

                // Greeting + user name header
                item(key = "header") {
                    StaggeredEntrance(index = 1) {
                        DashboardHeaderExpressive(
                            userName = userName,
                            vibrationManager = vibrationManager,
                            offlineState = offlineState,
                            onOfflinePillClick = { showOfflineModal = true }
                        )
                    }
                }

                // Device stats (battery + storage)
                if (showDashboardStats) {
                    item(key = "stats") {
                        StaggeredEntrance(index = 2) {
                            QuickStatsRowExpressive(stats = stats, onNavigate = onNavigate)
                        }
                    }
                }

                // Featured tools carousel
                if (categories.isNotEmpty()) {
                    item(key = "featured_carousel") {
                        StaggeredEntrance(index = 3) {
                            FeaturedToolsSection(categories = categories, onNavigate = onNavigate)
                        }
                    }
                }

                // Recent tools hub
                if (showRecentTools && recentTools.isNotEmpty()) {
                    item(key = "recent_tools") {
                        StaggeredEntrance(index = 4) {
                            RecentToolsSectionExpressive(
                                recentTools = recentTools,
                                categories = categories,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }

                // Quick-access pinned tools
                if (pinnedTools.isNotEmpty()) {
                    item(key = "pinned_tools") {
                        StaggeredEntrance(index = 5) {
                            PinnedToolsSectionExpressive(
                                pinnedTools = pinnedTools,
                                categories = categories,
                                onTogglePin = onTogglePin,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }

                // Smart notes strip
                if (showQuickNotes && notes.isNotEmpty()) {
                    item(key = "quick_notes") {
                        StaggeredEntrance(index = 6) {
                            QuickNotesSectionExpressive(
                                notes = notes,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }

                // View-mode toggle + "ALL TOOLS" title
                item(key = "view_toggle_header") {
                    StaggeredEntrance(index = 7) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ALL TOOLS",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${categories.sumOf { it.items.size }} utilities",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            ToolzConnectedButtonGroup(
                                selectedIndex = if (currentView == "LIST") 1 else 0,
                                options = listOf("Grid", "List"),
                                onOptionSelected = { idx ->
                                    vibrationManager?.vibrateTick()
                                    currentView = if (idx == 0) "DEFAULT" else "LIST"
                                },
                                modifier = Modifier.width(148.dp)
                            )
                        }
                    }
                }

                // Category sections — header + grid/list per category
                categories.forEachIndexed { catIndex, category ->
                    item(key = "cat_header_${category.title}") {
                        StaggeredEntrance(index = catIndex + 8) {
                            CategoryHeaderExpressive(title = category.title)
                        }
                    }

                    item(key = "cat_content_${category.title}") {
                        // No StaggeredEntrance here; individual grid items handle their own stagger
                        if (currentView == "LIST") {
                            CategoryListExpressive(
                                items = category.items,
                                vibrationManager = vibrationManager,
                                onNavigate = onNavigate,
                                onToolLongClick = { selectedToolForDetail = it }
                            )
                        } else {
                            CategoryGridExpressive(
                                items = category.items,
                                vibrationManager = vibrationManager,
                                onNavigate = onNavigate,
                                onToolLongClick = { selectedToolForDetail = it }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                item(key = "bottom_spacer") { Spacer(Modifier.height(170.dp)) }
            }
        }

        // ── Floating Horizontal Toolbar (Settings & Contextual Actions) ───────
        // Anchored bottom-start, collapses on scroll-down via exitAlwaysScrollBehavior.
        // Uses HorizontalFloatingToolbar directly to access containerFluidShape.
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        HorizontalFloatingToolbar(
            expanded = toolbarExpanded,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 108.dp)
                .navigationBarsPadding(),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            scrollBehavior = floatingToolbarBehavior,
            leadingContent = {
                // Always-visible FAB anchor: Settings
                FilledIconButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        onNavigate(Screen.Settings.route)
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        ) {
            // Expanded toolbar body — contextual quick actions
            AnimatedContent(
                targetState = offlineState == OfflineState.OFFLINE,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                },
                label = "offlineActionTransition"
            ) { isOffline ->
                IconButton(
                    onClick = {
                        vibrationManager?.vibrateTick()
                        if (isOffline) onToggleOfflineMode(false) else showOfflineModal = true
                    }
                ) {
                    Icon(
                        imageVector = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.Cloud,
                        contentDescription = if (isOffline) "Go Online" else "Offline Mode",
                        tint = if (isOffline) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            IconButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    onNavigate(Screen.BatteryInfo.route)
                }
            ) {
                Icon(
                    imageVector = if (stats.isBatteryCharging)
                        Icons.Rounded.BatteryChargingFull else Icons.Rounded.Battery5Bar,
                    contentDescription = "Battery — ${stats.batteryLevel}%",
                    tint = when {
                        stats.isBatteryCharging -> MaterialTheme.colorScheme.primary
                        stats.batteryLevel < 20 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    }
                )
            }

            IconButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    onNavigate(Screen.AiAssistant.route)
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "AI Assistant",
                    tint = if (offlineState == OfflineState.OFFLINE)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── Universal Pill ────────────────────────────────────────────────────
        if (showPillSetting) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
            ) {
                UniversalPillExpressive(
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
                    onNavigate = onNavigate,
                    offlineState = offlineState
                )
            }
        }

        // ── Modals & Dialogs ──────────────────────────────────────────────────
        if (showOfflineModal) {
            OfflineModeBottomSheet(
                vibrationManager = vibrationManager,
                onDismiss = { showOfflineModal = false },
                onGoOnline = {
                    vibrationManager?.vibrateClick()
                    onToggleOfflineMode(false)
                    showOfflineModal = false
                }
            )
        }

        selectedToolForDetail?.let { tool ->
            ToolDetailDialogExpressive(
                tool = tool,
                isPinned = pinnedTools.contains(tool.route),
                onDismiss = { selectedToolForDetail = null },
                onNavigate = {
                    selectedToolForDetail = null
                    onNavigate(it)
                },
                onTogglePin = { onTogglePin(tool.route) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP APP BAR + SMART SEARCH
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardTopBar(
    onSettingsClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    onNavigate: (String) -> Unit,
    categories: List<ToolCategory>,
    offlineState: OfflineState = OfflineState.ONLINE
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        ExpressiveTopAppBar(
            title = "Toolz",
            subtitle = when (offlineState) {
                OfflineState.OFFLINE -> "Offline-first mode active"
                OfflineState.ONLINE -> "Modern utility suite"
            },
            actions = {
                FilledTonalIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(52.dp)
                        .padding(end = 8.dp),
                    shape = SmallExpressiveShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            largeFlexible = false,
            titleHorizontalAlignment = Alignment.Start
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            SmartSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                isAiSearching = isAiSearching,
                aiSuggestedRoutes = aiSuggestedRoutes,
                onNavigate = onNavigate,
                categories = categories,
                offlineState = offlineState
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isAiSearching: Boolean,
    aiSuggestedRoutes: List<String>,
    onNavigate: (String) -> Unit,
    categories: List<ToolCategory>,
    offlineState: OfflineState = OfflineState.ONLINE
) {
    val haptic = LocalView.current
    val performanceMode = LocalPerformanceMode.current
    val allTools = remember(categories) { categories.flatMap { it.items } }
    val localResults = remember(query, categories) {
        if (query.isBlank()) emptyList()
        else allTools.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }.take(5)
    }

    // Animated AI sparkle
    val infiniteTransition = rememberInfiniteTransition(label = "searchSparkle")
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleScale"
    )
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = SquircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            border = BorderStroke(
                width = 1.dp,
                color = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            ),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isAiSearching) {
                        ExpressiveCircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text field
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField: @Composable () -> Unit ->
                        Box {
                            if (query.isEmpty()) {
                                AnimatedContent(
                                    targetState = offlineState == OfflineState.OFFLINE,
                                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                                    label = "placeholderAnim"
                                ) { isOffline ->
                                    Text(
                                        text = if (isOffline) "Search local tools..." else "Search or ask AI...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            innerTextField()
                        }
                    }
                )

                // Trailing icon: clear or AI sparkle
                AnimatedContent(
                    targetState = query.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)))
                            .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                    },
                    label = "trailingIcon"
                ) { hasQuery ->
                    if (hasQuery) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (offlineState == OfflineState.ONLINE) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI-powered",
                            tint = MaterialTheme.colorScheme.primary.copy(
                                alpha = if (performanceMode) 0.8f else sparkleAlpha
                            ),
                            modifier = Modifier
                                .padding(end = 4.dp)
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
            }
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
                shape = SquircleShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
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
                            shape = SmallExpressiveShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = SmallExpressiveShape,
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

        // AI suggestions dropdown
        AnimatedVisibility(
            visible = aiSuggestedRoutes.isNotEmpty(),
            enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            ElevatedCard(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                shape = SquircleShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = SmallExpressiveShape,
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
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = SmallExpressiveShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(46.dp),
                                        shape = SmallExpressiveShape,
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

// ─────────────────────────────────────────────────────────────────────────────
// DASHBOARD HEADER — Greeting + Date
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DashboardHeaderExpressive(
    userName: String,
    vibrationManager: VibrationManager? = null,
    offlineState: OfflineState = OfflineState.ONLINE,
    onOfflinePillClick: () -> Unit = {}
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..5 -> "GOOD NIGHT"
            in 6..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
    }
    val dateText = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
        } catch (_: Exception) {
            SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (userName.isBlank()) "Explorer" else userName,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dateText.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black
                )
            }

            if (offlineState == OfflineState.OFFLINE) {
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        onOfflinePillClick()
                    },
                    shape = BouncyShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "OFFLINE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FEATURED TOOLS CAROUSEL — NEW (ExpressiveCarousel)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturedToolsSection(
    categories: List<ToolCategory>,
    onNavigate: (String) -> Unit
) {
    // Pick the first item from each category as a "featured" representative
    val featuredTools = remember(categories) {
        categories.flatMap { it.items.take(1) }.take(6)
    }
    if (featuredTools.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        CategoryHeaderExpressive(title = "FEATURED")
        Spacer(modifier = Modifier.height(4.dp))
        ExpressiveCarousel(
            items = featuredTools,
            preferredItemWidth = 196.dp,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { tool ->
            FeaturedToolCarouselItem(tool = tool, onNavigate = onNavigate)
        }
    }
}

@Composable
fun FeaturedToolCarouselItem(
    tool: ToolItem,
    onNavigate: (String) -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current

    // Subtle inner glow animation on the icon
    val infiniteTransition = rememberInfiniteTransition(label = "featuredGlow")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (performanceMode) 1f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        tool.color.copy(alpha = 0.92f),
                        tool.color.copy(alpha = 0.55f)
                    )
                )
            )
            .bouncyClick {
                vibrationManager?.vibrateClick()
                onNavigate(tool.route)
            }
            .padding(20.dp)
    ) {
        // Decorative background circle
        Box(
            modifier = Modifier
                .size(90.dp)
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-16).dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon container
            Surface(
                modifier = Modifier.size(58.dp),
                shape = MediumExpressiveShape,
                color = Color.White.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                }
            }

            // Tool info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tool.title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }
        }

        // Arrow indicator
        Icon(
            imageVector = Icons.Rounded.ArrowOutward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUICK STATS — Battery + Storage with wavy progress
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuickStatsRowExpressive(stats: DashboardStats, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCardExpressive(
            modifier = Modifier.weight(1f),
            title = "BATTERY",
            value = "${stats.batteryLevel}%",
            subValue = if (stats.isBatteryCharging) "CHARGING" else "DISCHARGING",
            icon = if (stats.isBatteryCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Battery5Bar,
            color = when {
                stats.isBatteryCharging -> MaterialTheme.colorScheme.primary
                stats.batteryLevel < 20 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            },
            progress = stats.batteryLevel / 100f,
            onClick = { onNavigate(Screen.BatteryInfo.route) }
        )
        StatCardExpressive(
            modifier = Modifier.weight(1f),
            title = "STORAGE",
            value = "${stats.storageAvailableGb.toInt()}GB",
            subValue = "AVAILABLE",
            icon = Icons.Rounded.Storage,
            color = MaterialTheme.colorScheme.primary,
            progress = stats.storageUsedPercentage,
            onClick = { onNavigate(Screen.DeviceInfo.route) }
        )
    }
}

@Composable
fun StatCardExpressive(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    color: Color,
    progress: Float,
    onClick: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = {
            vibrationManager?.vibrateClick()
            onClick()
        },
        modifier = modifier.height(134.dp),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = SmallExpressiveShape,
                    color = color.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                ToolzWavyLinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = color,
                    trackColor = color.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RECENTLY USED SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecentToolsSectionExpressive(
    recentTools: List<String>,
    categories: List<ToolCategory>,
    onNavigate: (String) -> Unit
) {
    val allTools = remember(categories) { categories.flatMap { it.items } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        CategoryHeaderExpressive("RECENTLY USED")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(recentTools.take(8)) { route ->
                val tool = allTools.find { it.route == route }
                if (tool != null) RecentToolCardExpressive(tool, onNavigate)
            }
        }
    }
}

@Composable
fun RecentToolCardExpressive(tool: ToolItem, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = {
            vibrationManager?.vibrateClick()
            onNavigate(tool.route)
        },
        modifier = Modifier.size(width = 112.dp, height = 112.dp),
        shape = BouncyShape,
        containerColor = tool.color.copy(alpha = 0.1f),
        border = BorderStroke(1.2.dp, tool.color.copy(alpha = 0.18f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = SmallExpressiveShape,
                color = tool.color.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = tool.color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.3.sp,
                lineHeight = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PINNED TOOLS SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PinnedToolsSectionExpressive(
    pinnedTools: Set<String>,
    categories: List<ToolCategory>,
    onTogglePin: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val allTools = remember(categories) { categories.flatMap { it.items } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        CategoryHeaderExpressive("QUICK ACCESS")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(pinnedTools.toList()) { route ->
                val tool = allTools.find { it.route == route }
                if (tool != null) PinnedToolItemExpressive(tool, onTogglePin, onNavigate)
            }
        }
    }
}

@Composable
fun PinnedToolItemExpressive(
    tool: ToolItem,
    onTogglePin: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = {
            vibrationManager?.vibrateClick()
            onNavigate(tool.route)
        },
        onLongClick = {
            vibrationManager?.vibrateLongClick()
            onTogglePin(tool.route)
        },
        modifier = Modifier.size(width = 172.dp, height = 122.dp),
        shape = SquircleShape,
        containerColor = tool.color.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, tool.color.copy(alpha = 0.25f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = SmallExpressiveShape,
                    color = tool.color.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = tool.color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    tint = tool.color.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = tool.title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = tool.color,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUICK NOTES SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuickNotesSectionExpressive(
    notes: List<Note>,
    onNavigate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        CategoryHeaderExpressive("SMART NOTES")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(notes.take(6)) { note ->
                QuickNoteItemExpressive(note = note, onNavigate = onNavigate)
            }
            item {
                Surface(
                    modifier = Modifier
                        .size(width = 116.dp, height = 176.dp)
                        .bouncyClick { onNavigate(Screen.Notepad.route) },
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Text(
                                "VIEW ALL",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickNoteItemExpressive(note: Note, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val noteColor = Color(note.color)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerAlpha = if (isDark) 0.25f else 0.12f

    val cardWidth = when {
        note.attachedAudioUri != null -> 286.dp
        note.attachedImageUri != null -> 252.dp
        else -> 226.dp
    }
    val cardHeight = when {
        note.attachedImageUri != null -> 196.dp
        else -> 176.dp
    }

    val cardShape = when {
        note.attachedAudioUri != null -> RoundedCornerShape(32.dp, 22.dp, 32.dp, 22.dp)
        note.attachedImageUri != null -> RoundedCornerShape(28.dp, 16.dp, 28.dp, 16.dp)
        else -> SquircleShape
    }

    ExpressiveCard(
        onClick = {
            vibrationManager?.vibrateClick()
            onNavigate("${Screen.Notepad.route}?initialNoteId=${note.id}")
        },
        modifier = Modifier.size(width = cardWidth, height = cardHeight),
        shape = cardShape,
        containerColor = noteColor.copy(alpha = containerAlpha),
        border = BorderStroke(1.5.dp, noteColor.copy(alpha = 0.35f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = note.title.ifBlank { "UNTITLED" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        letterSpacing = (-0.5).sp
                    )
                    if (note.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = noteColor
                        )
                    }
                }

                note.attachedImageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = if (note.attachedImageUri != null) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (note.attachedAudioUri != null) Icon(Icons.Rounded.Mic, null, modifier = Modifier.size(13.dp), tint = noteColor)
                    if (note.attachedImageUri != null) Icon(Icons.Rounded.Image, null, modifier = Modifier.size(13.dp), tint = noteColor)
                }
                Text(
                    text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(note.timestamp)).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = noteColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CATEGORY HEADER — Animated accent line
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CategoryHeaderExpressive(title: String) {
    Row(
        modifier = Modifier.padding(top = 28.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated two-segment accent bar
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Surface(
                modifier = Modifier
                    .width(24.dp)
                    .height(5.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {}
            Surface(
                modifier = Modifier
                    .width(8.dp)
                    .height(5.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ) {}
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.8.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ADAPTIVE CATEGORY GRID — 2 columns phone / 3 columns tablet + per-item stagger
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CategoryGridExpressive(
    items: List<ToolItem>,
    vibrationManager: VibrationManager?,
    onNavigate: (String) -> Unit,
    onToolLongClick: (ToolItem) -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 3 else 2
        val rows = items.chunked(columns)

        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEachIndexed { colIndex, item ->
                        val staggerIndex = rowIndex * columns + colIndex
                        StaggeredEntrance(
                            index = staggerIndex,
                            modifier = Modifier.weight(1f),
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 380,
                                    delayMillis = staggerIndex * 45
                                )
                            ) + slideInVertically(
                                initialOffsetY = { 28 },
                                animationSpec = tween(420, delayMillis = staggerIndex * 45)
                            ) + scaleIn(
                                initialScale = 0.90f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        ) {
                            ToolGridItemExpressive(
                                item = item,
                                modifier = Modifier.fillMaxWidth(),
                                vibrationManager = vibrationManager,
                                onNavigate = onNavigate,
                                onLongClick = { onToolLongClick(item) }
                            )
                        }
                    }
                    // Fill trailing empty slot in last row
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CATEGORY LIST VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CategoryListExpressive(
    items: List<ToolItem>,
    vibrationManager: VibrationManager?,
    onNavigate: (String) -> Unit,
    onToolLongClick: (ToolItem) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items.forEachIndexed { index, item ->
            StaggeredEntrance(index = index) {
                ToolListItemExpressive(
                    item = item,
                    vibrationManager = vibrationManager,
                    onNavigate = onNavigate,
                    onLongClick = { onToolLongClick(item) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL CARD — GRID VARIANT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ToolGridItemExpressive(
    item: ToolItem,
    modifier: Modifier = Modifier,
    vibrationManager: VibrationManager?,
    onNavigate: (String) -> Unit,
    onLongClick: () -> Unit = {}
) {
    ExpressiveCard(
        onClick = {
            vibrationManager?.vibrateClick()
            onNavigate(item.route)
        },
        onLongClick = {
            vibrationManager?.vibrateLongClick()
            onLongClick()
        },
        modifier = modifier.height(132.dp),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = SmallExpressiveShape,
                    color = item.color.copy(alpha = 0.14f)
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
                    modifier = Modifier
                        .size(17.dp)
                        .alpha(0.22f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL CARD — LIST VARIANT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ToolListItemExpressive(
    item: ToolItem,
    vibrationManager: VibrationManager?,
    onNavigate: (String) -> Unit,
    onLongClick: () -> Unit = {}
) {
    ExpressiveCard(
        onClick = {
            vibrationManager?.vibrateClick()
            onNavigate(item.route)
        },
        onLongClick = {
            vibrationManager?.vibrateLongClick()
            onLongClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = SmallExpressiveShape,
                color = item.color.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.color,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier
                    .size(15.dp)
                    .alpha(0.28f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UNIVERSAL PILL — Live activity hub at bottom center
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UniversalPillExpressive(
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
    catalogViewModel: CatalogViewModel,
    todoViewModel: TodoViewModel,
    caffeinateViewModel: CaffeinateViewModel,
    fillThePillEnabled: Boolean,
    onNavigate: (String) -> Unit,
    offlineState: OfflineState = OfflineState.ONLINE
) {
    val performanceMode = LocalPerformanceMode.current
    val todoState by todoViewModel.uiState.collectAsStateWithLifecycle()
    val isCaffeinated by caffeinateViewModel.isServiceRunning.collectAsStateWithLifecycle()
    val caffeinateTime by caffeinateViewModel.elapsedTime.collectAsStateWithLifecycle()

    val appTips = remember(offlineState) {
        listOfNotNull(
            if (offlineState == OfflineState.ONLINE)
                AppTip("Talk to AI agents", "With AI assistant", Icons.Rounded.AutoAwesome, Screen.AiAssistant.route, Color(0xFF9C27B0))
            else null,
            AppTip("Express yourself", "Use the Notepad", Icons.Rounded.EditNote, Screen.Notepad.route, Color(0xFFFF9800)),
            AppTip("Convert any file", "100% Local File converter", Icons.Rounded.Transform, Screen.FileConverter.route, Color(0xFF2196F3)),
            AppTip("Manage your time", "Focus Flow", Icons.Rounded.CenterFocusStrong, Screen.FocusFlow.route, Color(0xFF4CAF50)),
            AppTip("Be ready, anytime", "Your To do list", Icons.AutoMirrored.Rounded.PlaylistAddCheck, Screen.Todo.route, Color(0xFF673AB7)),
            AppTip("Stop procrastinating", "Pomodoro", Icons.Rounded.Timer, Screen.Pomodoro.route, Color(0xFFF44336)),
            AppTip("Solve your equations", "With the Equation solver", Icons.Rounded.Functions, Screen.EquationSolver.route, Color(0xFF3F51B5)),
            AppTip("Customize the app", "In Settings", Icons.Rounded.Tune, Screen.Settings.route, Color(0xFF607D8B)),
            AppTip("Clean up junk", "File Cleaner", Icons.Rounded.CleaningServices, Screen.FileCleaner.route, Color(0xFF00BCD4)),
            AppTip("Network Tweaks", "Check latency & router", Icons.Rounded.NetworkCheck, Screen.NetworkPowerSuite.route, Color(0xFF1976D2)),
            AppTip("Take a coffee", "And your screen will never sleep", Icons.Rounded.Coffee, Screen.Caffeinate.route, Color(0xFF795548))
        )
    }

    val pages = remember(
        musicState, timerState, stopwatchState, pomodoroState, stepsState,
        recordingState, todoState.tasks, isCaffeinated, catalogState.downloadingTracks, fillThePillEnabled
    ) {
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
        if (stepsState.isEnabledInSettings) list.add(PillPage.Steps)
        if (list.isEmpty() && fillThePillEnabled) {
            appTips.shuffled().take(3).forEach { list.add(PillPage.Tip(it)) }
        }
        list
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isAnyActive = musicState.isPlaying || timerState.isRunning ||
            stopwatchState.isRunning || pomodoroState.isRunning ||
            recordingState.isRecording || isCaffeinated

    if (pages.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .height(94.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = SquircleShape,
        tonalElevation = 12.dp,
        shadowElevation = if (performanceMode) 0.dp else 32.dp,
        border = BorderStroke(
            width = if (isAnyActive) 2.dp else 1.5.dp,
            brush = if (isAnyActive && !performanceMode) {
                Brush.sweepGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.9f),
                        secondaryColor.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                        primaryColor.copy(alpha = 0.9f)
                    )
                )
            } else SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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

            // Spring-animated pill page dots
            if (pages.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 9.dp),
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
                            targetValue = if (active) primaryColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                            animationSpec = tween(280),
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

// ─────────────────────────────────────────────────────────────────────────────
// PILL PAGE CONTENT COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

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
        Surface(modifier = Modifier.size(52.dp), shape = SmallExpressiveShape, color = tip.color.copy(alpha = 0.15f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = tip.icon, contentDescription = null, tint = tip.color, modifier = Modifier.size(28.dp))
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
        IconButton(onClick = { onNavigate(tip.route) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun MusicPillContent(state: MusicUiState, viewModel: MusicPlayerViewModel, onNavigate: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "musicPill")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(12000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "albumRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.MusicPlayer.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            val artShape = when (state.artShape) {
                "CIRCLE" -> CircleShape
                else -> RoundedCornerShape(20.dp)
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
                Surface(color = Color.Black.copy(alpha = 0.4f), modifier = Modifier.size(54.dp).clip(artShape)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.currentTrack?.title ?: "Not Playing", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.currentTrack?.artist ?: "Unknown Artist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        }
        FilledTonalIconButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier.size(48.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun TimerPillContent(state: com.frerox.toolz.ui.screens.time.TimerState, viewModel: TimerViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.Timer.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val remainingSec = state.remainingTime / 1000
            Text(String.format("%02d:%02d", remainingSec / 60, remainingSec % 60), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            val progress = if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime.toFloat() else 0f
            ExpressiveLinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(onClick = { viewModel.toggleStartStop() }, modifier = Modifier.size(48.dp), shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Icon(if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun StopwatchPillContent(state: com.frerox.toolz.ui.screens.time.StopwatchState, viewModel: StopwatchViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.Stopwatch.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = String.format("%02d:%02d.%01d", state.elapsedTime / 60000, (state.elapsedTime % 60000) / 1000, (state.elapsedTime % 1000) / 100),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface
        )
        FilledTonalIconButton(onClick = { viewModel.toggleStartStop() }, modifier = Modifier.size(48.dp), shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Icon(if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun PomodoroPillContent(state: com.frerox.toolz.ui.screens.time.PomodoroState, viewModel: PomodoroViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.Pomodoro.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AvTimer, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (state.mode != PomodoroMode.WORK) "Break Time" else "Focusing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            val remSec = state.remainingTime / 1000
            Text(String.format("%02d:%02d", remSec / 60, remSec % 60), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
        FilledTonalIconButton(onClick = { viewModel.toggleStartStop() }, modifier = Modifier.size(48.dp), shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Icon(if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun StepsPillContent(state: StepState, viewModel: StepCounterViewModel, onNavigate: (String) -> Unit) {
    val goalSteps = 10_000
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.StepCounter.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("STEPS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Text("${state.steps}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            ExpressiveLinearProgressIndicator(
                progress = { (state.steps.toFloat() / goalSteps).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(onClick = { onNavigate(Screen.StepCounter.route) }, modifier = Modifier.size(48.dp), shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))) {
            Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun RecorderPillContent(state: RecordingState, viewModel: VoiceRecorderViewModel, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.VoiceRecorder.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            val infiniteTransition = rememberInfiniteTransition(label = "recBlip")
            val blipAlpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.3f,
                animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "blipAlpha"
            )
            Box(modifier = Modifier.size(18.dp).graphicsLayer { alpha = blipAlpha }.background(MaterialTheme.colorScheme.error, CircleShape))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(if (state.isRecording) "Recording..." else "Recording Paused", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        FilledTonalIconButton(onClick = { viewModel.stopRecording() }, modifier = Modifier.size(48.dp), shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun TodoPillContent(task: com.frerox.toolz.data.todo.TaskEntry?, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.Todo.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.TaskAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Next Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Text(task?.title ?: "Nothing planned", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(onClick = { onNavigate(Screen.Todo.route) }, modifier = Modifier.size(48.dp), shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))) {
            Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun CatalogDownloadPillContent(progress: Float, count: Int, onNavigate: (String) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "downloadPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.MusicPlayer.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FileDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Downloads (MUSIC)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveLinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Spacer(Modifier.width(12.dp))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) {
                Text(count.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onNavigate(Screen.Caffeinate.route) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Coffee, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Awake Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
            val h = timeMillis / 3600000; val m = (timeMillis % 3600000) / 60000; val s = (timeMillis % 60000) / 1000
            Text(if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(onClick = { onNavigate(Screen.Caffeinate.route) }, modifier = Modifier.size(48.dp), shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))) {
            Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UPDATE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UpdateCardExpressive(version: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { vibrationManager?.vibrateClick(); onUpdate() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        shape = SquircleShape,
        elevation = 0.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text("NEW VERSION READY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer, letterSpacing = 1.5.sp)
                    Text("Upgrade to v$version now", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
            IconButton(onClick = { vibrationManager?.vibrateClick(); onDismiss() }, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))) {
                Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOOL DETAIL DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailDialogExpressive(
    tool: ToolItem,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onTogglePin: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(56.dp), shape = SmallExpressiveShape, color = tool.color.copy(alpha = 0.15f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(28.dp)) }
                }
                Spacer(Modifier.width(20.dp))
                Text(tool.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(tool.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                Surface(
                    onClick = { vibrationManager?.vibrateTick(); onTogglePin() },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = BouncyShape,
                    color = if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Rounded.PushPin, null, tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (isPinned) "Pinned to Quick Access" else "Pin to Quick Access", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isPinned, onCheckedChange = { vibrationManager?.vibrateTick(); onTogglePin() })
                    }
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = { vibrationManager?.vibrateClick(); onNavigate(tool.route) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = BouncyShape
            ) {
                Text("OPEN UTILITY", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("DISMISS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        },
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// OFFLINE MODE BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineModeBottomSheet(
    vibrationManager: VibrationManager?,
    onDismiss: () -> Unit,
    onGoOnline: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Offline Mode Active", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text("AI Assistant and Web Search are hidden to ensure 100% privacy, save battery, and reduce data usage.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            ToolzExpressiveButton(onClick = onGoOnline, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Icon(Icons.Rounded.Cloud, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("GO ONLINE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("STAY OFFLINE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PRIVATE HELPER
// ─────────────────────────────────────────────────────────────────────────────

private fun Long.toProgressFloat(): Float = this.toFloat()

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEWS — Light + Dark
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Dashboard — Light",
    showBackground = true,
    backgroundColor = 0xFFF8F9FA
)
@Composable
private fun DashboardScreenPreviewLight() {
    ToolzTheme(darkTheme = false) {
        _DashboardPreviewScaffold()
    }
}

@Preview(
    name = "Dashboard — Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or
            android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun DashboardScreenPreviewDark() {
    ToolzTheme(darkTheme = true) {
        _DashboardPreviewScaffold()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun _DashboardPreviewScaffold() {
    val sampleCategories = listOf(
        ToolCategory(
            "SMART FLOW & AI",
            listOf(
                ToolItem("Ai Assistant", Icons.Rounded.AutoAwesome, "ai", "Gemini Flash AI", Color(0xFF8E24AA)),
                ToolItem("Focus Flow", Icons.Rounded.Toll, "focus", "Flow insights", Color(0xFF1976D2)),
                ToolItem("Todo List", Icons.Rounded.TaskAlt, "todo", "Physics tasks", Color(0xFF43A047)),
                ToolItem("Notepad", Icons.Rounded.Description, "notepad", "Quick notes", Color(0xFFFDD835))
            )
        ),
        ToolCategory(
            "SENSORS & VISION",
            listOf(
                ToolItem("Compass", Icons.Rounded.Explore, "compass", "Navigation", Color(0xFF00897B)),
                ToolItem("Speedometer", Icons.Rounded.Speed, "speed", "GPS Speed", Color(0xFF1976D2)),
                ToolItem("Bubble Level", Icons.Rounded.Architecture, "level", "Leveling", Color(0xFF7CB342)),
                ToolItem("Altimeter", Icons.Rounded.Terrain, "altimeter", "Altitude", Color(0xFF795548))
            )
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Preview top bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOOLZ", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                            Text("Modern utility suite", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                        }
                        FilledTonalIconButton(onClick = {}, shape = SmallExpressiveShape) {
                            Icon(Icons.Rounded.Settings, null)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = SquircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Search or ask AI...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fadingEdges(top = 8.dp, bottom = 100.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                item {
                    DashboardHeaderExpressive(userName = "Explorer", offlineState = OfflineState.ONLINE)
                }
                item {
                    FeaturedToolsSection(categories = sampleCategories, onNavigate = {})
                }
                sampleCategories.forEach { category ->
                    item { CategoryHeaderExpressive(category.title) }
                    item {
                        CategoryGridExpressive(
                            items = category.items,
                            vibrationManager = null,
                            onNavigate = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }

        // Preview: static pill at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .height(94.dp)
                .fillMaxWidth(),
            shape = SquircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 10.dp,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(modifier = Modifier.size(52.dp), shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp)) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Talk to AI agents", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text("With AI assistant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
            }
        }
    }
}