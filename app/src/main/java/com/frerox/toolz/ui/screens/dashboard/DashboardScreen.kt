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

package com.frerox.toolz.ui.screens.dashboard

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.navigation.Screen
import androidx.core.net.toUri
import com.frerox.toolz.ui.screens.focus.CaffeinateViewModel
import com.frerox.toolz.ui.screens.focus.FocusFlowViewModel
import com.frerox.toolz.ui.screens.pdf.PdfViewModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    data object Flashlight : PillPage()
    data object Focus      : PillPage()
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
    pdfViewModel: PdfViewModel               = hiltViewModel(),
    focusViewModel: FocusFlowViewModel       = hiltViewModel(),
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
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val aiResponse   by viewModel.aiResponse.collectAsStateWithLifecycle()
    val aiRoutes       by viewModel.aiSuggestedRoutes.collectAsStateWithLifecycle()
    val stats          by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val updateVersion  by viewModel.updateAvailableVersion.collectAsStateWithLifecycle(null)
    val offlineState   by viewModel.offlineState.collectAsStateWithLifecycle()
    val manualOffline  by viewModel.manualOfflineMode.collectAsStateWithLifecycle()
    val notes          by notepadViewModel.notes.collectAsStateWithLifecycle()
    val categories   by viewModel.categories.collectAsStateWithLifecycle()

    val navigate: (String) -> Unit = remember {
        { route ->
            vibrationManager?.vibrateClick()
            viewModel.addRecentTool(route)
            onNavigate(route)
        }
    }

    val spotlightTool by viewModel.spotlightTool.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabCategories by viewModel.tabCategories.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().toolzBackground()) {
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
            pdfViewModel        = pdfViewModel,
            focusViewModel      = focusViewModel,
            catalogState        = catalogState,
            showPill            = showPill,
            fillThePill         = fillThePill,
            searchQuery         = searchQuery,
            isAiSearching       = isAiSearching,
            isAiThinking        = isAiThinking,
            aiResponse          = aiResponse,
            aiSuggestedRoutes   = aiRoutes,
            onSearchChange      = viewModel::updateSearchQuery,
            onTransferToAi      = { viewModel.transferToAiAssistant { onNavigate(Screen.AiAssistant.route + "?chatId=$it") } },
            updateVersion       = updateVersion,
            onDismissUpdate     = viewModel::dismissUpdate,
            notes               = notes,
            categories          = categories,
            tabCategories       = tabCategories,
            selectedTab         = selectedTab,
            offlineState        = offlineState,
            manualOffline       = manualOffline,
            onToggleOffline     = viewModel::toggleOfflineMode,
            onTogglePerformance = viewModel::togglePerformanceMode,
            stats               = stats,
            spotlightTool       = spotlightTool,
        )

        ToolzFloatingToolbar(
            selectedTab = selectedTab,
            onTabSelected = { viewModel.setSelectedTab(it) },
            modifier = Modifier.align(Alignment.BottomCenter),
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
            todoViewModel = todoViewModel,
            caffeinateViewModel = caffeinateViewModel,
            focusViewModel = focusViewModel,
            fillThePill = fillThePill,
            onNavigate = navigate,
            offlineState = offlineState,
            settingsRepository = settingsRepository,
            showToolzPill = showPill
        )
    }
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
    pdfViewModel: PdfViewModel,
    focusViewModel: FocusFlowViewModel,
    catalogState: CatalogUiState,
    showPill: Boolean,
    fillThePill: Boolean,
    searchQuery: String,
    isAiSearching: Boolean,
    isAiThinking: Boolean,
    aiResponse: String?,
    aiSuggestedRoutes: List<String>,
    onSearchChange: (String) -> Unit,
    onTransferToAi: () -> Unit,
    updateVersion: String?,
    onDismissUpdate: () -> Unit,
    notes: List<Note>,
    categories: List<ToolCategory>,
    tabCategories: List<ToolCategory>,
    selectedTab: DashboardTab,
    offlineState: OfflineState,
    manualOffline: Boolean,
    onToggleOffline: (Boolean) -> Unit,
    onTogglePerformance: (Boolean) -> Unit,
    stats: DashboardStats,
    spotlightTool: ToolItem?,
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode  = LocalPerformanceMode.current

    var showOfflineSheet by remember { mutableStateOf(false) }
    var toolForDetail    by remember { mutableStateOf<ToolItem?>(null) }
    var currentView      by remember(savedView) { mutableStateOf(savedView) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Simplified Top Bar ───────────────────────────────────────────
            DashboardTopBar(
                searchQuery    = searchQuery,
                onSearchChange = onSearchChange,
                isAiSearching  = isAiSearching,
                isAiThinking   = isAiThinking,
                aiResponse     = aiResponse,
                aiRoutes       = aiSuggestedRoutes,
                onNavigate     = onNavigate,
                onTransferToAi = onTransferToAi,
                categories     = categories,
                offlineState   = offlineState,
                manualOffline  = manualOffline,
                onOfflineClick = {
                    vibrationManager?.vibrateTick()
                    if (offlineState == OfflineState.OFFLINE) onToggleOffline(false)
                    else showOfflineSheet = true
                },
                onSettingsClick = { onNavigate(Screen.Settings.route) },
            )

            // ── Tab Content ──────────────────────────────────────────────────
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(400, delayMillis = 100)) + 
                     slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 8 }) togetherWith
                    fadeOut(animationSpec = tween(200))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    DashboardTab.HOME -> {
                        HomeTabContent(
                            onNavigate = onNavigate,
                            onTogglePin = onTogglePin,
                            userName = userName,
                            pinnedTools = pinnedTools,
                            recentTools = recentTools,
                            showRecentTools = showRecentTools,
                            showQuickNotes = showQuickNotes,
                            showDashboardStats = showDashboardStats,
                            stats = stats,
                            spotlightTool = spotlightTool,
                            notes = notes,
                            categories = categories,
                            tabCategories = tabCategories,
                            updateVersion = updateVersion,
                            onDismissUpdate = onDismissUpdate,
                            onToggleOffline = onToggleOffline,
                            onTogglePerformance = onTogglePerformance,
                            offlineState = offlineState,
                            searchQuery = searchQuery,
                            musicViewModel = musicViewModel,
                            pdfViewModel = pdfViewModel
                        )
                    }
                    else -> {
                        TabGridContent(
                            tab = tab,
                            categories = tabCategories,
                            currentView = currentView,
                            onNavigate = onNavigate,
                            onLongClick = { toolForDetail = it },
                            onViewToggle = {
                                vibrationManager?.vibrateTick()
                                currentView = if (currentView == "LIST") "DEFAULT" else "LIST"
                            }
                        )
                    }
                }
            }
        }

        // ── Universal pill ────────────────────────────────────────────────────
        // Removed standalone pill, now integrated into ToolzFloatingToolbar
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

// ─────────────────────────────────────────────────────────────────────────────
// TAB CONTENTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeTabContent(
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    userName: String,
    pinnedTools: Set<String>,
    recentTools: List<String>,
    showRecentTools: Boolean,
    showQuickNotes: Boolean,
    showDashboardStats: Boolean,
    stats: DashboardStats,
    spotlightTool: ToolItem?,
    notes: List<Note>,
    categories: List<ToolCategory>,
    tabCategories: List<ToolCategory>,
    updateVersion: String?,
    onDismissUpdate: () -> Unit,
    onToggleOffline: (Boolean) -> Unit,
    onTogglePerformance: (Boolean) -> Unit,
    offlineState: OfflineState,
    searchQuery: String,
    musicViewModel: MusicPlayerViewModel,
    pdfViewModel: PdfViewModel
) {
    val performanceMode = LocalPerformanceMode.current
    val allTools = remember(categories) { categories.flatMap { it.items } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .let {
                if (performanceMode) it
                else it.fadingEdges(top = 12.dp, bottom = 120.dp)
            },
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (updateVersion != null) {
            item(key = "update_banner") {
                UpdateBanner(updateVersion, { onNavigate(Screen.Update.route) }, onDismissUpdate)
            }
        }

        item(key = "dashboard_header") {
            DashboardHeader(userName, offlineState, onToggleOffline, onTogglePerformance) 
        }

        if (pinnedTools.isNotEmpty()) {
            item(key = "pinned_section_header") {
                SectionHeader("PINNED")
            }
            item(key = "pinned_grid") {
                val pinnedItems = remember(pinnedTools, allTools) {
                    pinnedTools.mapNotNull { route -> allTools.find { it.route == route } }
                }
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    ToolGridSection(pinnedItems, onNavigate, { onTogglePin(it.route) })
                }
            }
        }

        if (recentTools.isNotEmpty() && showRecentTools) {
            item(key = "recent_section") {
                RecentSection(recentTools, categories, onNavigate)
            }
        }

        if (showDashboardStats) {
            item(key = "stats_row") {
                StatsRow(stats, onNavigate)
            }
        }

        if (showQuickNotes && notes.isNotEmpty()) {
            item(key = "notes_section") {
                NotesSection(notes, onNavigate, musicViewModel, pdfViewModel)
            }
        }

        // Smart Flow tools in Home
        tabCategories.forEachIndexed { ci, cat ->
            item(key = "cat_header_${cat.title}_home") {
                SectionHeader("ESSENTIALS")
            }
            item(key = "cat_body_${cat.title}_home") {
                ToolGridSection(cat.items, onNavigate)
                Spacer(Modifier.height(4.dp))
            }
        }

        item(key = "dashboard_bottom_spacer") { Spacer(Modifier.height(160.dp)) }
    }
}

@Composable
fun TabGridContent(
    tab: DashboardTab,
    categories: List<ToolCategory>,
    currentView: String,
    onNavigate: (String) -> Unit,
    onLongClick: (ToolItem) -> Unit,
    onViewToggle: () -> Unit,
) {
    val performanceMode = LocalPerformanceMode.current

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .let {
                    if (performanceMode) it
                    else it.fadingEdges(top = 12.dp, bottom = 120.dp)
                },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            categories.forEachIndexed { ci, cat ->
                item(key = "cat_header_${cat.title}") {
                    SectionHeader(cat.title)
                }
                item(key = "cat_body_${cat.title}") {
                    if (currentView == "LIST") {
                        ToolListSection(cat.items, onNavigate, onLongClick)
                    } else {
                        ToolGridSection(cat.items, onNavigate, onLongClick)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            item(key = "tab_bottom_spacer") { Spacer(Modifier.height(160.dp)) }
        }
    }
}

@Composable
fun PinnedCarouselItem(
    tool: ToolItem,
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit
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
        modifier = Modifier.fillMaxSize(),
        shape = MediumExpressiveShape,
        containerColor = tool.color.copy(alpha = 0.12f),
        border = null, // Removed border
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = SmallExpressiveShape,
                color = tool.color.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                tool.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SPOTLIGHT SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpotlightSection(tool: ToolItem, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        SectionHeader("FOR YOU")
        ExpressiveCard(
            onClick = { 
                vibrationManager?.vibrateClick()
                onNavigate(tool.route) 
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = BouncyShape,
            containerColor = tool.color.copy(alpha = 0.15f),
            border = null, // Removed border
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MediumExpressiveShape,
                    color = tool.color.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Spotlight: ${tool.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        tool.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    Icons.Rounded.AutoAwesome,
                    null,
                    tint = tool.color,
                    modifier = Modifier.size(20.dp)
                )
            }
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
    isAiThinking: Boolean,
    aiResponse: String?,
    aiRoutes: List<String>,
    onNavigate: (String) -> Unit,
    onTransferToAi: () -> Unit,
    categories: List<ToolCategory>,
    offlineState: OfflineState,
    manualOffline: Boolean,
    onOfflineClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val isOffline = offlineState == OfflineState.OFFLINE
    val vibrationManager = LocalVibrationManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp), // Reduced vertical padding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmartSearchBar(
                modifier = Modifier.weight(1f),
                query = searchQuery,
                onQueryChange = onSearchChange,
                isAiSearching = isAiSearching,
                isAiThinking = isAiThinking,
                aiResponse = aiResponse,
                aiRoutes = aiRoutes,
                onNavigate = onNavigate,
                onTransferToAi = onTransferToAi,
                categories = categories,
                offlineState = offlineState,
            )

            Spacer(Modifier.width(12.dp))

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.Settings, "Settings", modifier = Modifier.size(20.dp))
            }
        }
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
    isAiThinking: Boolean,
    aiResponse: String?,
    aiRoutes: List<String>,
    onNavigate: (String) -> Unit,
    onTransferToAi: () -> Unit,
    categories: List<ToolCategory>,
    offlineState: OfflineState,
    modifier: Modifier = Modifier,
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

    val isUrl = remember(query) {
        query.trim().let { 
            it.startsWith("http://") || it.startsWith("https://") || 
            (it.contains(".") && !it.contains(" ") && it.length > 3 && it.substringAfterLast(".").all { c -> c.isLetter() })
        }
    }

    val isAiActive = isAiSearching || isAiThinking || aiResponse != null || aiRoutes.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "aiGlow")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * glowIntensity)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isAiActive && !performanceMode) {
                        Modifier.shadow(
                            elevation = 12.dp * glowIntensity,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = MaterialTheme.colorScheme.primary,
                            ambientColor = MaterialTheme.colorScheme.primary
                        )
                    } else Modifier
                )
        ) {
            ExpressiveSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isAiActive && !performanceMode) {
                            Modifier.background(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowColor, Color.Transparent),
                                    radius = 500f
                                ),
                                shape = RoundedCornerShape(28.dp)
                            ).border(
                                width = 1.5.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                ),
                                shape = RoundedCornerShape(28.dp)
                            )
                        } else Modifier
                    ),
                onSearch = { 
                    if (isUrl) {
                        val url = if (query.startsWith("http")) query else "https://$query"
                        onNavigate(Screen.Browser.createRoute(url))
                    }
                },
                placeholder = {
                    Text(
                        text = if (offlineState == OfflineState.OFFLINE) stringResource(R.string.st_DashboardScreen_a1b2) else stringResource(R.string.st_DashboardScreen_c3d4),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Medium,
                    )
                },
                leadingIcon = {
                    if (isAiSearching || isAiThinking) {
                        ExpressiveCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Search, null,
                            tint = if (isAiActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )
        }

        // Local dropdown
        AnimatedVisibility(
            visible = localHits.isNotEmpty() && aiRoutes.isEmpty() && aiResponse == null,
            enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            SearchDropdown(tools = localHits, onNavigate = onNavigate)
        }

        // AI dropdown (Routes)
        AnimatedVisibility(
            visible = aiRoutes.isNotEmpty() && aiResponse == null,
            enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            val aiTools = remember(aiRoutes, allTools) {
                aiRoutes.mapNotNull { r -> allTools.find { it.route == r } }
            }
            SearchDropdown(tools = aiTools, onNavigate = onNavigate, isAi = true)
        }

        // AI conversational response
        AnimatedVisibility(
            visible = aiResponse != null,
            enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            aiResponse?.let { response ->
                AiSearchResponseDropdown(
                    response = response,
                    isThinking = isAiThinking,
                    onTransfer = onTransferToAi,
                    onNavigate = onNavigate
                )
            }
        }

        // URL suggestion
        AnimatedVisibility(
            visible = isUrl && aiResponse == null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            UrlSuggestionRow(query = query, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun UrlSuggestionRow(query: String, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val url = if (query.startsWith("http")) query else "https://$query"
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        onClick = {
            vibrationManager?.vibrateClick()
            onNavigate(Screen.Browser.createRoute(url))
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.st_DashboardScreen_e5f6), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AiSearchResponseDropdown(
    response: String,
    isThinking: Boolean,
    onTransfer: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    
    // Typewriter effect (simplified for markdown)
    var displayedText by remember { mutableStateOf("") }
    LaunchedEffect(response) {
        if (response.length > displayedText.length) {
            response.forEachIndexed { index, _ ->
                if (index >= displayedText.length) {
                    displayedText = response.take(index + 1)
                    kotlinx.coroutines.delay(10) // Speed of typewriter
                }
            }
        } else {
            displayedText = response
        }
    }

    Surface(
        modifier       = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape          = SquircleShape,
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        border         = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.st_DashboardScreen_g7h8),
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                    )
                }
                
                if (!isThinking) {
                    IconButton(
                        onClick = onTransfer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            val segments = remember(displayedText) { parseMarkdownToSegments(displayedText) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                segments.forEach { seg ->
                    MarkdownSegment(
                        seg = seg,
                        baseFontSize = 14.sp,
                        onLinkClick = { url -> onNavigate(Screen.Browser.createRoute(url)) }
                    )
                }
            }

            if (isThinking) {
                ExpressiveTypingDots(color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(12.dp))

            ToolzExpressiveButton(
                onClick = { 
                    vibrationManager?.vibrateClick()
                    onTransfer()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.st_DashboardScreen_i9j0), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
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
                        stringResource(R.string.st_DashboardScreen_k1l2),
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
fun DashboardHeader(
    userName: String, 
    offlineState: OfflineState,
    onToggleOffline: (Boolean) -> Unit,
    onTogglePerformance: (Boolean) -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val greeting = remember {
        val calendar = java.util.Calendar.getInstance()
        when (calendar.get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..5   -> context.getString(R.string.st_DashboardScreen_m3n4)
            in 6..11  -> context.getString(R.string.st_DashboardScreen_o5p6)
            in 12..16 -> context.getString(R.string.st_DashboardScreen_q7r8)
            else      -> context.getString(R.string.st_DashboardScreen_s9t0)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (userName.isBlank()) "Explorer" else userName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.0).sp,
                )
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Offline Toggle
                Surface(
                    onClick = { 
                        vibrationManager?.vibrateTick()
                        onToggleOffline(offlineState != OfflineState.OFFLINE) 
                    },
                    shape = CircleShape,
                    color = if (offlineState == OfflineState.OFFLINE) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                           else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                ) {
                    Icon(
                        if (offlineState == OfflineState.OFFLINE) Icons.Rounded.CloudOff else Icons.Rounded.Cloud,
                        contentDescription = "Offline Mode",
                        modifier = Modifier.padding(8.dp).size(16.dp),
                        tint = if (offlineState == OfflineState.OFFLINE) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                // Discreet Performance Toggle
                Surface(
                    onClick = { 
                        vibrationManager?.vibrateTick()
                        onTogglePerformance(!performanceMode) 
                    },
                    shape = CircleShape,
                    color = if (performanceMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                           else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                ) {
                    Icon(
                        if (performanceMode) Icons.Rounded.Bolt else Icons.Rounded.AutoAwesome,
                        contentDescription = "Performance Mode",
                        modifier = Modifier.padding(8.dp).size(16.dp),
                        tint = if (performanceMode) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val battColor = when {
            stats.isBatteryCharging -> MaterialTheme.colorScheme.primary
            stats.batteryLevel < 20 -> MaterialTheme.colorScheme.error
            else                    -> MaterialTheme.colorScheme.secondary
        }
        StatCard(
            modifier = Modifier.weight(1f),
            label    = stringResource(R.string.st_DashboardScreen_u1v2),
            value    = "${stats.batteryLevel}%",
            sub      = if (stats.isBatteryCharging) stringResource(R.string.st_DashboardScreen_a7b8) else stringResource(R.string.st_DashboardScreen_c9d0),
            icon     = if (stats.isBatteryCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
            color    = battColor,
            progress = stats.batteryLevel / 100f,
            onClick  = { onNavigate(Screen.BatteryInfo.route) },
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label    = stringResource(R.string.st_DashboardScreen_w3x4),
            value    = "${stats.storageAvailableGb.toInt()}GB",
            sub      = stringResource(R.string.st_DashboardScreen_y5z6),
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
        modifier       = modifier.height(110.dp),
        shape          = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        elevation      = 0.dp,
        border         = null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle progress fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(color.copy(alpha = 0.06f))
            )

            Row(
                modifier          = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier            = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(label,
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color         = color.copy(alpha = 0.8f))
                    Text(value,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace)
                    Text(sub,
                        style         = MaterialTheme.typography.labelSmall,
                        color         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight    = FontWeight.Bold)
                }
                
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape    = SquircleShape,
                    color    = color.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                }
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
        SectionHeader(stringResource(R.string.st_DashboardScreen_e1f2))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
            .width(64.dp) // More compact
            .bouncyClick { vibrationManager?.vibrateClick(); onNavigate(tool.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape    = ExtraLargeExpressiveShape,
            color    = tool.color.copy(alpha = 0.1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            tool.title,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
            letterSpacing = 0.5.sp,
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
        SectionHeader(stringResource(R.string.st_DashboardScreen_g3h4))
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
        modifier    = Modifier.size(width = 140.dp, height = 100.dp),
        shape       = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
        border      = BorderStroke(1.5.dp, tool.color.copy(alpha = 0.22f)),
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
fun NotesSection(
    notes: List<Note>, 
    onNavigate: (String) -> Unit,
    musicViewModel: MusicPlayerViewModel,
    pdfViewModel: PdfViewModel
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        SectionHeader(stringResource(R.string.st_DashboardScreen_k7l8))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 2.dp),
        ) {
            items(notes.take(6)) { note ->
                QuickNoteCard(note, onNavigate, musicViewModel, pdfViewModel)
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
                            Text(stringResource(R.string.st_DashboardScreen_i5j6),
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
private fun QuickNoteCard(
    note: Note, 
    onNavigate: (String) -> Unit,
    musicViewModel: MusicPlayerViewModel,
    pdfViewModel: PdfViewModel
) {
    val vibrationManager = LocalVibrationManager.current
    val noteColor = Color(note.color)
    val isDark    = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardAlpha = if (isDark) 0.22f else 0.1f
    
    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

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
        border     = null, // Removed border
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
                    dateFormatter.format(Date(note.timestamp)).uppercase(),
                    style         = MaterialTheme.typography.labelSmall,
                    color         = noteColor.copy(alpha = 0.6f),
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 0.7.sp,
                )
            }

            // Audio & PDF Attachments (Dashboard)
            if (note.attachedAudioUri != null || note.attachedPdfUri != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    note.attachedAudioUri?.let { uri ->
                        AudioAttachmentPill(
                            name = note.attachedAudioName ?: "Audio Note",
                            uri = uri,
                            color = noteColor,
                            musicViewModel = musicViewModel
                        )
                    }
                    note.attachedPdfUri?.let { uri ->
                        PdfAttachmentPill(
                            uri = uri,
                            title = note.title.ifBlank { "Document" },
                            color = noteColor,
                            onNavigate = onNavigate,
                            pdfViewModel = pdfViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioAttachmentPill(
    name: String,
    uri: String,
    color: Color,
    musicViewModel: MusicPlayerViewModel
) {
    val vibrationManager = LocalVibrationManager.current
    Surface(
        onClick = {
            vibrationManager?.vibrateClick()
            musicViewModel.playUri(uri.toUri())
        },
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
        }
    }
}

@Composable
private fun PdfAttachmentPill(
    uri: String,
    title: String,
    color: Color,
    onNavigate: (String) -> Unit,
    pdfViewModel: PdfViewModel
) {
    val vibrationManager = LocalVibrationManager.current
    Surface(
        onClick = {
            vibrationManager?.vibrateClick()
            pdfViewModel.openPdf(uri.toUri(), title)
            onNavigate(Screen.PdfReader.route)
        },
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.PictureAsPdf, null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.st_DashboardScreen_m9n0),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ALL TOOLS HEADER + CATEGORY CHIP FILTER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AllToolsHeader(
    totalTools: Int,
    currentView: String,
    onViewToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(R.string.st_DashboardScreen_o1p2),
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER — two-dot accent
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier          = Modifier.padding(top = 28.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title,
            style         = MaterialTheme.typography.labelLarge, // Smaller, cleaner header
            fontWeight    = FontWeight.Black,
            letterSpacing = 2.sp,
            color         = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
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
                        ToolGridCard(item, Modifier.weight(1f), onNavigate) { onLongClick(item) }
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
        modifier    = modifier.height(110.dp),
        shape       = RoundedCornerShape(24.dp), // More squared for grid
        containerColor = item.color.copy(alpha = 0.08f),
        elevation   = 0.dp,
        border      = null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape    = MediumExpressiveShape,
                    color    = item.color.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = item.color, modifier = Modifier.size(20.dp))
                    }
                }
                Text(item.title,
                    style         = MaterialTheme.typography.labelLarge,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines      = 1,
                    overflow      = TextOverflow.Ellipsis)
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
        items.forEach { item ->
            ToolListCard(item, onNavigate) { onLongClick(item) }
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
        modifier    = Modifier.fillMaxWidth().height(84.dp),
        shape       = ExtraLargeExpressiveShape, // Matching grid cards
        containerColor = item.color.copy(alpha = 0.08f), // Use tool color subtly
        elevation   = 0.dp,
        border      = null,
    ) {
        Row(
            modifier          = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(Modifier.size(52.dp), MediumExpressiveShape, color = item.color.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = item.color, modifier = Modifier.size(26.dp))
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
        shape      = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        elevation  = 0.dp,
        border     = null, // Removed border
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
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(SmallExpressiveShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Column {
                    Text(stringResource(R.string.st_DashboardScreen_q3r4),
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
    focusViewModel: FocusFlowViewModel,
    fillThePill: Boolean,
    onNavigate: (String) -> Unit,
    offlineState: OfflineState,
    settingsRepository: SettingsRepository,
    isEmbedded: Boolean = false,
) {
    val performanceMode  = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val todoState        by todoViewModel.uiState.collectAsStateWithLifecycle()
    val isCaffeinated    by caffeinateViewModel.isServiceRunning.collectAsStateWithLifecycle()
    val caffeinateMs     by caffeinateViewModel.elapsedTime.collectAsStateWithLifecycle()

    val focusScore by focusViewModel.productivityScore.collectAsStateWithLifecycle()
    
    val flashlightRepository = com.frerox.toolz.MainActivity.LocalFlashlightRepository.current
    val isFlashlightOn by flashlightRepository?.isOn?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val flashlightMode by flashlightRepository?.mode?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(com.frerox.toolz.ui.screens.light.FlashlightMode.STEADY) }
    val flashlightBrightness by flashlightRepository?.brightness?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(1.0f) }

    val pillFocusEnabled by settingsRepository.pillFocusEnabled.collectAsState(initial = true)
    val pillTodoEnabled by settingsRepository.pillTodoEnabled.collectAsState(initial = true)
    val pillMusicEnabled by settingsRepository.pillMusicEnabled.collectAsState(initial = true)
    val pillTimerEnabled by settingsRepository.pillTimerEnabled.collectAsState(initial = true)
    val pillStopwatchEnabled by settingsRepository.pillStopwatchEnabled.collectAsState(initial = true)
    val pillPomodoroEnabled by settingsRepository.pillPomodoroEnabled.collectAsState(initial = true)
    val pillStepsEnabled by settingsRepository.pillStepsEnabled.collectAsState(initial = true)
    val pillRecorderEnabled by settingsRepository.pillRecorderEnabled.collectAsState(initial = true)
    val pillCaffeinateEnabled by settingsRepository.pillCaffeinateEnabled.collectAsState(initial = true)
    val pillFlashlightEnabled by settingsRepository.pillFlashlightEnabled.collectAsState(initial = true)
    val pillCatalogDownloadEnabled by settingsRepository.pillCatalogDownloadEnabled.collectAsState(initial = true)

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
        isFlashlightOn, focusScore, pillFocusEnabled, pillTodoEnabled,
        pillMusicEnabled, pillTimerEnabled, pillStopwatchEnabled, pillPomodoroEnabled,
        pillStepsEnabled, pillRecorderEnabled, pillCaffeinateEnabled, pillFlashlightEnabled,
        pillCatalogDownloadEnabled, offlineState, appTips
    ) {
        buildList {
            if (pillCatalogDownloadEnabled && catalogState.downloadingTracks.isNotEmpty())
                add(PillPage.CatalogDownload(
                    catalogState.downloadingTracks.values.average().toFloat(),
                    catalogState.downloadingTracks.size))
            if (pillMusicEnabled && (musicState.isPlaying || musicState.currentTrack != null)) add(PillPage.Music)
            if (pillTimerEnabled && (timerState.isRunning || timerState.remainingTime > 0 || timerState.isRinging)) add(PillPage.Timer)
            if (pillStopwatchEnabled && (stopwatchState.isRunning || stopwatchState.elapsedTime > 0)) add(PillPage.Stopwatch)
            if (pillPomodoroEnabled && pomodoroState.isRunning)                                add(PillPage.Pomodoro)
            if (pillRecorderEnabled && (recordingState.isRecording || recordingState.isPaused))  add(PillPage.Recorder)
            if (pillTodoEnabled && todoState.tasks.isNotEmpty())        add(PillPage.Todo)
            if (pillCaffeinateEnabled && isCaffeinated)                                          add(PillPage.Caffeinate)
            if (pillFlashlightEnabled && isFlashlightOn)                                         add(PillPage.Flashlight)
            if (pillFocusEnabled && focusScore > 0)                     add(PillPage.Focus)
            if (pillStepsEnabled && stepsState.isSensorPresent && stepsState.isEnabledInSettings) add(PillPage.Steps)
            if (isEmpty() && fillThePill) {
                appTips.forEach { add(PillPage.Tip(it)) }
            }
        }
    }

    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.size })
    
    // Auto-scroll to active tools when they start
    LaunchedEffect(pages) {
        val importantIndex = pages.indexOfFirst { 
            when(it) {
                is PillPage.Music -> musicState.isPlaying
                is PillPage.Timer -> timerState.isRunning
                is PillPage.Stopwatch -> stopwatchState.isRunning
                is PillPage.Pomodoro -> pomodoroState.isRunning
                is PillPage.Recorder -> recordingState.isRecording
                is PillPage.Flashlight -> true
                is PillPage.CatalogDownload -> true
                else -> false
            }
        }
        if (importantIndex != -1 && pagerState.currentPage != importantIndex) {
            pagerState.animateScrollToPage(importantIndex)
        }
    }

    val isActive   = (pillMusicEnabled && musicState.isPlaying) || 
            (pillTimerEnabled && timerState.isRunning) ||
            (pillStopwatchEnabled && stopwatchState.isRunning) || 
            (pillPomodoroEnabled && pomodoroState.isRunning) ||
            (pillRecorderEnabled && recordingState.isRecording) || 
            (pillCaffeinateEnabled && isCaffeinated) || 
            (pillFlashlightEnabled && isFlashlightOn) ||
            (pillCatalogDownloadEnabled && catalogState.downloadingTracks.isNotEmpty()) ||
            (pillStepsEnabled && stepsState.isEnabledInSettings && stepsState.motionStatus != "IDLE")

    val activeColor = remember(pagerState.currentPage, pages, musicState) {
        val page = pages.getOrNull(pagerState.currentPage)
        when(page) {
            is PillPage.Music -> musicState.currentTrack?.let { Color(0xFF2196F3) } ?: Color(0xFF2196F3)
            is PillPage.Timer -> Color(0xFF4CAF50)
            is PillPage.Stopwatch -> Color(0xFF00BCD4)
            is PillPage.Pomodoro -> Color(0xFFF44336)
            is PillPage.Steps -> Color(0xFF9C27B0)
            is PillPage.Recorder -> Color(0xFFE91E63)
            is PillPage.Todo -> Color(0xFF673AB7)
            is PillPage.Caffeinate -> Color(0xFF795548)
            is PillPage.Flashlight -> Color(0xFFFFC107)
            is PillPage.Focus -> Color(0xFF4CAF50)
            is PillPage.CatalogDownload -> Color(0xFF2196F3)
            is PillPage.Tip -> page.tip.color
            null -> Color(0xFF2196F3)
        }
    }
    val primary   = activeColor
    val secondary = activeColor.copy(alpha = 0.7f)
    val tertiary  = activeColor.copy(alpha = 0.5f)

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    if (isEmbedded) {
        Box(modifier = modifier) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
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
                            is PillPage.Flashlight      -> FlashlightPillContent(flashlightMode, flashlightBrightness, onNavigate)
                            is PillPage.Focus           -> FocusPillContent(focusScore, onNavigate)
                        }
            }
        }
    } else {
        Surface(
            modifier        = modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .height(92.dp)
                .graphicsLayer {
                    if (isActive && !performanceMode) {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                },
            shape           = ExtraLargeExpressiveShape,
            color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation  = 12.dp,
            shadowElevation = if (performanceMode) 0.dp else 32.dp,
            border          = BorderStroke(
                width = if (isActive) 2.2.dp else 1.8.dp,
                brush = if (isActive && !performanceMode)
                    Brush.sweepGradient(listOf(
                        primary.copy(alpha = 0.9f),
                        secondary.copy(alpha = 0.55f),
                        tertiary.copy(alpha = 0.5f),
                        primary.copy(alpha = 0.9f),
                    ))
                else SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isActive && !performanceMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(primary.copy(alpha = glowAlpha * 0.2f), Color.Transparent),
                                    radius = 400f
                                )
                            )
                    )
                }

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
                            is PillPage.Flashlight      -> FlashlightPillContent(flashlightMode, flashlightBrightness, onNavigate)
                            is PillPage.Focus           -> FocusPillContent(focusScore, onNavigate)
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
}

// ─────────────────────────────────────────────────────────────────────────────
// PILL PAGE CONTENT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FlashlightPillContent(
    mode: com.frerox.toolz.ui.screens.light.FlashlightMode,
    brightness: Float,
    onNavigate: (String) -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    val flashlightRepository = com.frerox.toolz.MainActivity.LocalFlashlightRepository.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            .bouncyClick { onNavigate(Screen.Flashlight.route) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FlashlightOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.st_DashboardScreen_s5t6),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modeLabel = remember(mode) {
                    mode.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                Text(modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    fontWeight = FontWeight.SemiBold)
                
                when (mode) {
                    com.frerox.toolz.ui.screens.light.FlashlightMode.STEADY -> {
                        ToolzWavyLinearProgressIndicator(
                            progress = { brightness },
                            modifier = Modifier.width(48.dp).height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                    com.frerox.toolz.ui.screens.light.FlashlightMode.STROBE -> {
                        ExpressiveTypingDots(
                            modifier = Modifier.height(12.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    com.frerox.toolz.ui.screens.light.FlashlightMode.SOS -> {
                        ExpressivePulseIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    com.frerox.toolz.ui.screens.light.FlashlightMode.DISCO -> {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFFBA68C8) // Vivid purple
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    val entries = com.frerox.toolz.ui.screens.light.FlashlightMode.entries
                    val index = entries.indexOf(mode)
                    val intent = Intent(context, com.frerox.toolz.service.FlashlightService::class.java).apply {
                        action = "com.frerox.toolz.FLASHLIGHT_CYCLE_MODE"
                    }
                    context.startService(intent)
                },
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            FilledTonalIconButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    val intent = Intent(context, com.frerox.toolz.service.FlashlightService::class.java).apply {
                        action = com.frerox.toolz.service.FlashlightService.ACTION_STOP
                    }
                    context.startService(intent)
                },
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun FocusPillContent(score: Int, onNavigate: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            .bouncyClick { vibrationManager?.vibrateTick(); onNavigate(Screen.FocusFlow.route) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.size(52.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
            Icon(Icons.Rounded.Toll, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.st_DashboardScreen_u7v8),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
            Text("Flow Score: $score%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Rounded.ArrowOutward, null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    }
}
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
    val isRinging = state.isRinging
    val pillAccent = if (isRinging) c.error else c.primary
    val pillContainer = if (isRinging) c.errorContainer else c.primaryContainer

    val longPressProgress = remember { Animatable(0f) }
    var isLongPressing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isDismissed by remember { mutableStateOf(false) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isDismissed) 0f else 1f,
        animationSpec = tween(600),
        label = "pillAlpha"
    )

    if (animatedAlpha <= 0f && isDismissed) return

    val scale = 1f - (longPressProgress.value * 0.04f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(animatedAlpha)
            .pointerInput(isRinging) {
                if (isRinging) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            isLongPressing = true
                            val job = scope.launch {
                                longPressProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(800, easing = LinearEasing)
                                )
                                if (longPressProgress.value >= 1f) {
                                    vm.stopRingtone()
                                    isDismissed = true
                                    isLongPressing = false
                                }
                            }
                            
                            var pointerUp = false
                            while (!pointerUp) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) {
                                    pointerUp = true
                                }
                            }

                            isLongPressing = false
                            job.cancel()
                            if (!isDismissed) {
                                scope.launch {
                                    longPressProgress.animateTo(0f, tween(300))
                                }
                            }
                        }
                    }
                } else {
                    detectTapGestures(
                        onTap = { onNavigate(Screen.Timer.route) }
                    )
                }
            }
    ) {
        // Long press progress background fill
        if (longPressProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pillAccent.copy(alpha = 0.15f * longPressProgress.value))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(longPressProgress.value)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                pillAccent.copy(alpha = 0.4f),
                                pillAccent.copy(alpha = 0.0f)
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = SquircleShape,
                color = if (isRinging) c.error else c.primaryContainer.copy(alpha = 0.55f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isRinging) Icons.Rounded.NotificationsActive else Icons.Rounded.Timer,
                        null,
                        tint = if (isRinging) c.onError else c.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                if (isRinging) {
                    Text(
                        "TIME IS UP!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = c.error,
                        letterSpacing = 2.sp
                    )
                    Text(
                        if (isLongPressing) "Hold to dismiss..." else "Long press to stop",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.error.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    val s = state.remainingTime / 1000
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            String.format("%02d:%02d", s / 60, s % 60),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = c.onSurface
                        )
                        if (state.remainingTime > 0) {
                            Text(
                                " left",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = c.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val p = if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime else 0f
                    ToolzWavyLinearProgressIndicator(
                        { p },
                        Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = pillAccent,
                        trackColor = pillAccent.copy(alpha = 0.12f)
                    )
                }
            }

            if (!isRinging) {
                Spacer(Modifier.width(16.dp))
                FilledTonalIconButton(
                    onClick = { vm.toggleStartStop() },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = pillContainer,
                        contentColor = pillAccent
                    )
                ) {
                    Icon(
                        if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.steps}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    String.format(Locale.US, "%.1f %s", state.distanceDisplay, state.distanceUnit),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.outline
                )
            }
            val progress = if (state.goal > 0) (state.steps.toFloat() / state.goal.toFloat()) else 0f
            ExpressiveLinearProgressIndicator(
                { progress.coerceIn(0f, 1f) },
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
            Text(stringResource(R.string.st_DashboardScreen_w9x0),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign  = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.st_DashboardScreen_a7b9),
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
                Text(stringResource(R.string.st_DashboardScreen_y1z2), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.st_DashboardScreen_a3b4), fontWeight = FontWeight.Black,
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
                isAiThinking   = false,
                aiResponse     = null,
                aiRoutes       = emptyList(),
                onNavigate     = {},
                onTransferToAi = {},
                categories     = cats,
                offlineState   = OfflineState.ONLINE,
                manualOffline  = false,
                onOfflineClick = {},
                onSettingsClick = {},
            )
            LazyColumn(
                modifier       = Modifier.weight(1f).fadingEdges(bottom = 100.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                item { DashboardHeader("Explorer", OfflineState.ONLINE, {}, {}) }
                item {
                    StatsRow(DashboardStats(
                        batteryLevel         = 72,
                        isBatteryCharging    = true,
                        storageUsedPercentage = 0.6f,
                        storageAvailableGb   = 28.4,
                    ), {})
                }
                item { AllToolsHeader(cats.sumOf { it.items.size }, "DEFAULT", {}) }
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