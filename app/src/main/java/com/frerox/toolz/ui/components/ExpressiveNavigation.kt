package com.frerox.toolz.ui.components

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlinx.coroutines.launch

@Composable
fun ExpressiveNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit
) {
    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        windowInsets = windowInsets,
        content = content
    )
}

@Composable
fun RowScope.ExpressiveNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedIcon: @Composable () -> Unit = icon,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    interactionSource: MutableInteractionSource? = null,
) {
    val performanceMode = LocalPerformanceMode.current
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            performanceMode -> 1f
            isPressed -> 0.92f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = if (performanceMode) tween(durationMillis = 90) else spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navItemScale",
    )

    NavigationBarItem(
        selected = selected,
        onClick = {
            haptic.tick()
            currentOnClick()
        },
        icon = {
            Box(modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }) {
                if (selected) selectedIcon() else icon()
            }
        },
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        interactionSource = resolvedInteractionSource
    )
}

/**
 * Premium Wide Navigation Rail with expanded/collapsed expressive transitions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzWideNavigationRail(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    items: List<Pair<String, ImageVector>>,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    expandedHeaderTopPadding: Dp = 64.dp,
) {
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val haptic = rememberToolzHapticFeedback()

    WideNavigationRail(
        modifier = modifier,
        state = state,
        header = {
            Column(modifier = Modifier.padding(start = 24.dp, top = expandedHeaderTopPadding)) {
                if (header != null) {
                    header()
                    Spacer(Modifier.height(16.dp))
                }
                IconButton(
                    onClick = {
                        haptic.tick()
                        scope.launch {
                            if (state.targetValue == WideNavigationRailValue.Expanded) {
                                state.collapse()
                            } else {
                                state.expand()
                            }
                        }
                    },
                ) {
                    Icon(
                        if (state.targetValue == WideNavigationRailValue.Expanded)
                            Icons.Rounded.KeyboardDoubleArrowLeft else Icons.Rounded.Menu,
                        contentDescription = "Toggle Rail"
                    )
                }
            }
        },
    ) {
        WideRailItems(
            items = items,
            selectedItem = selectedItem,
            railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
            onItemSelected = onItemSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzModalWideNavigationRail(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    items: List<Pair<String, ImageVector>>,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    expandedHeaderTopPadding: Dp = 64.dp,
) {
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val haptic = rememberToolzHapticFeedback()

    ModalWideNavigationRail(
        modifier = modifier,
        state = state,
        expandedHeaderTopPadding = expandedHeaderTopPadding,
        header = {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                if (header != null) {
                    header()
                    Spacer(Modifier.height(16.dp))
                }
                IconButton(
                    onClick = {
                        haptic.tick()
                        scope.launch {
                            if (state.targetValue == WideNavigationRailValue.Expanded) {
                                state.collapse()
                            } else {
                                state.expand()
                            }
                        }
                    },
                ) {
                    Icon(
                        if (state.targetValue == WideNavigationRailValue.Expanded)
                            Icons.Rounded.KeyboardDoubleArrowLeft else Icons.Rounded.Menu,
                        contentDescription = "Toggle Rail",
                    )
                }
            }
        },
    ) {
        WideRailItems(
            items = items,
            selectedItem = selectedItem,
            railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
            onItemSelected = onItemSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ToolzFloatingToolbar(
    selectedTab: com.frerox.toolz.ui.screens.dashboard.DashboardTab,
    onTabSelected: (com.frerox.toolz.ui.screens.dashboard.DashboardTab) -> Unit,
    modifier: Modifier = Modifier,
    // Universal Pill Props
    musicState: com.frerox.toolz.ui.screens.media.MusicUiState? = null,
    musicViewModel: com.frerox.toolz.ui.screens.media.MusicPlayerViewModel? = null,
    timerState: com.frerox.toolz.ui.screens.time.TimerState? = null,
    timerViewModel: com.frerox.toolz.ui.screens.time.TimerViewModel? = null,
    stopwatchState: com.frerox.toolz.ui.screens.time.StopwatchState? = null,
    stopwatchViewModel: com.frerox.toolz.ui.screens.time.StopwatchViewModel? = null,
    pomodoroState: com.frerox.toolz.ui.screens.time.PomodoroState? = null,
    pomodoroViewModel: com.frerox.toolz.ui.screens.time.PomodoroViewModel? = null,
    stepsState: com.frerox.toolz.ui.screens.sensors.StepState? = null,
    stepsViewModel: com.frerox.toolz.ui.screens.sensors.StepCounterViewModel? = null,
    recordingState: com.frerox.toolz.ui.screens.sensors.RecordingState? = null,
    recorderViewModel: com.frerox.toolz.ui.screens.sensors.VoiceRecorderViewModel? = null,
    catalogState: com.frerox.toolz.ui.screens.media.catalog.CatalogUiState? = null,
    todoViewModel: com.frerox.toolz.ui.screens.todo.TodoViewModel? = null,
    caffeinateViewModel: com.frerox.toolz.ui.screens.focus.CaffeinateViewModel? = null,
    focusViewModel: com.frerox.toolz.ui.screens.focus.FocusFlowViewModel? = null,
    fillThePill: Boolean = false,
    onNavigate: (String) -> Unit = {},
    offlineState: com.frerox.toolz.util.OfflineState = com.frerox.toolz.util.OfflineState.ONLINE,
    settingsRepository: com.frerox.toolz.data.settings.SettingsRepository? = null,
) {
    val haptic = rememberToolzHapticFeedback()
    val performanceMode = LocalPerformanceMode.current

    // Determine if pill content should be shown
    val isCaffeinated by caffeinateViewModel?.isServiceRunning?.collectAsState(false) ?: remember { mutableStateOf(false) }
    
    val flashlightRepository = com.frerox.toolz.MainActivity.LocalFlashlightRepository.current
    val isFlashlightOn by flashlightRepository?.isOn?.collectAsState(false) ?: remember { mutableStateOf(false) }

    val todoState by todoViewModel?.uiState?.collectAsState(null) ?: remember { mutableStateOf(null) }
    val focusScore by focusViewModel?.productivityScore?.collectAsState(0) ?: remember { mutableStateOf(0) }
    
    val pillTodoEnabled by settingsRepository?.pillTodoEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillFocusEnabled by settingsRepository?.pillFocusEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillMusicEnabled by settingsRepository?.pillMusicEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillTimerEnabled by settingsRepository?.pillTimerEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillStopwatchEnabled by settingsRepository?.pillStopwatchEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillPomodoroEnabled by settingsRepository?.pillPomodoroEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillStepsEnabled by settingsRepository?.pillStepsEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillRecorderEnabled by settingsRepository?.pillRecorderEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillCaffeinateEnabled by settingsRepository?.pillCaffeinateEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillFlashlightEnabled by settingsRepository?.pillFlashlightEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }
    val pillCatalogDownloadEnabled by settingsRepository?.pillCatalogDownloadEnabled?.collectAsState(true) ?: remember { mutableStateOf(true) }

    val hasActiveService = (pillMusicEnabled && (musicState?.isPlaying == true || musicState?.currentTrack != null)) ||
                          (pillTimerEnabled && (timerState?.isRunning == true || timerState?.isRinging == true || (timerState?.remainingTime ?: 0L) > 0L)) ||
                          (pillStopwatchEnabled && stopwatchState?.isRunning == true) ||
                          (pillPomodoroEnabled && pomodoroState?.isRunning == true) ||
                          (pillRecorderEnabled && (recordingState?.isRecording == true || recordingState?.isPaused == true)) ||
                          (pillCaffeinateEnabled && isCaffeinated) || 
                          (pillFlashlightEnabled && isFlashlightOn) ||
                          (pillStepsEnabled && stepsState?.isEnabledInSettings == true) ||
                          (pillCatalogDownloadEnabled && catalogState?.downloadingTracks?.isNotEmpty() == true) ||
                          (pillTodoEnabled && todoState?.tasks?.isNotEmpty() == true) ||
                          (pillFocusEnabled && focusScore > 0)

    val showPillContent = hasActiveService || fillThePill

    val toolbarWidth by animateDpAsState(
        targetValue = if (showPillContent) 340.dp else 260.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "toolbarWidth"
    )

    val toolbarHeight by animateDpAsState(
        targetValue = if (showPillContent) 148.dp else 80.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "toolbarHeight"
    )

    Box(
        modifier = modifier
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .width(toolbarWidth)
            .height(toolbarHeight)
            .clip(ExtraLargeExpressiveShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f))
            .border(
                width = if (hasActiveService) 2.dp else 0.dp,
                brush = if (hasActiveService) Brush.sweepGradient(listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.primary
                )) else SolidColor(Color.Transparent),
                shape = ExtraLargeExpressiveShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Pill Content (Top part when expanded)
            AnimatedVisibility(
                visible = showPillContent,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(modifier = Modifier.height(72.dp).fillMaxWidth()) {
                    if (musicState != null && musicViewModel != null && 
                        timerState != null && timerViewModel != null && 
                        stopwatchState != null && stopwatchViewModel != null && 
                        pomodoroState != null && pomodoroViewModel != null && 
                        stepsState != null && stepsViewModel != null && 
                        recordingState != null && recorderViewModel != null && 
                        catalogState != null && todoViewModel != null && 
                        caffeinateViewModel != null) {
                        
                        if (settingsRepository != null) {
                            com.frerox.toolz.ui.screens.dashboard.UniversalPill(
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
                                focusViewModel = focusViewModel ?: hiltViewModel(),
                                fillThePill = fillThePill,
                                onNavigate = onNavigate,
                                offlineState = offlineState,
                                settingsRepository = settingsRepository,
                                modifier = Modifier.fillMaxSize(),
                                isEmbedded = true
                            )
                        }
                    }
                }
            }

            // Navigation Tabs (Bottom part)
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.frerox.toolz.ui.screens.dashboard.DashboardTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    val color by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        animationSpec = tween(300),
                        label = "tabColor"
                    )
                    
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.1f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "tabScale"
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .bouncyClick {
                                haptic.tick()
                                onTabSelected(tab)
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = color,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adaptive Navigation Suite Scaffold for Toolz.
 */
@Composable
fun ToolzNavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    content: @Composable () -> Unit,
) {
    val layoutType = NavigationSuiteScaffoldDefaults
        .calculateFromAdaptiveInfo(windowAdaptiveInfo)

    NavigationSuiteScaffold(
        navigationSuiteItems = navigationSuiteItems,
        layoutType = layoutType,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = modifier,
        content = content
    )
}

/**
 * Floating Action Button Menu for complex multi-action entries.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFabMenu(
    items: List<Triple<String, ImageVector, () -> Unit>>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberToolzHapticFeedback()

    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = {
                    haptic.tick()
                    expanded = it
                },
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                    }
                }
                Icon(
                    painter = rememberVectorPainter(imageVector),
                    contentDescription = contentDescription,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    haptic.click()
                    expanded = false
                    item.third()
                },
                icon = { Icon(item.second, contentDescription = null) },
                text = {
                    Text(
                        text = item.first,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WideRailItems(
    items: List<Pair<String, ImageVector>>,
    selectedItem: Int,
    railExpanded: Boolean,
    onItemSelected: (Int) -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)
    items.forEachIndexed { index, item ->
        WideNavigationRailItem(
            railExpanded = railExpanded,
            icon = { Icon(item.second, contentDescription = item.first) },
            label = { Text(item.first) },
            selected = selectedItem == index,
            onClick = {
                haptic.click()
                currentOnItemSelected(index)
            },
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpressiveNavigationPreview() {
    ToolzTheme(dynamicColor = false) {
        Row(modifier = Modifier.fillMaxSize()) {
            ToolzWideNavigationRail(
                selectedItem = 0,
                onItemSelected = {},
                items = listOf("Dashboard" to Icons.Rounded.Dashboard, "Settings" to Icons.Rounded.Settings),
                header = { Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(32.dp)) }
            )

            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Add Item", Icons.Rounded.Add, {}),
                            Triple("Save", Icons.Rounded.Save, {})
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    ExpressiveNavigationBar {
                        ExpressiveNavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            label = { Text("Profile") }
                        )
                        ExpressiveNavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolzNavigationRail(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationRailDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    header: @Composable (ColumnScope.() -> Unit)? = null,
    windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        header = header,
        windowInsets = windowInsets,
        content = content,
    )
}

@Composable
fun ToolzNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationRailItemColors = NavigationRailItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    NavigationRailItem(
        selected = selected,
        onClick = {
            haptic.click()
            currentOnClick()
        },
        icon = icon,
        modifier = modifier.expressivePressScale(resolvedInteractionSource, enabled),
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        interactionSource = resolvedInteractionSource,
    )
}
