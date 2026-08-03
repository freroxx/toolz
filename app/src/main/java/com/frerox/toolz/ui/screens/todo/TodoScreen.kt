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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.frerox.toolz.ui.screens.todo

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.todo.SubTask
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 1 ─ Constants & Utilities
// ─────────────────────────────────────────────────────────────────────────────

private enum class TaskFilter(@StringRes val label: Int) { ALL(R.string.st_TodoScreen_filter_all), TODAY(R.string.st_TodoScreen_filter_today), UPCOMING(R.string.st_TodoScreen_filter_next) }

private val PriorityColors = listOf(
    Color(0xFF9E9E9E), // 1 · None
    Color(0xFF4CAF50), // 2 · Low
    Color(0xFF2196F3), // 3 · Medium
    Color(0xFFFF9800), // 4 · High
    Color(0xFFF44336), // 5 · Critical
)

@StringRes
private val PriorityLabels: List<Int> = listOf(R.string.st_TodoScreen_priority_none, R.string.st_TodoScreen_priority_low, R.string.st_TodoScreen_priority_medium, R.string.st_TodoScreen_priority_high, R.string.st_TodoScreen_priority_critical)

private fun formatDueDate(ts: Long): String {
    val diff = ts - System.currentTimeMillis()
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        diff < 0 && days < -1 -> "${abs(days)}d overdue"
        diff < 0 -> "Overdue"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days < 7 -> "In ${days}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

private fun dueDateUrgencyColor(ts: Long): Color? {
    val hours = TimeUnit.MILLISECONDS.toHours(ts - System.currentTimeMillis())
    return when {
        hours < 0 -> Color(0xFFF44336)
        hours < 24 -> Color(0xFFFF9800)
        hours < 72 -> Color(0xFFFFC107)
        else -> null
    }
}

private fun formatSessionTime(millis: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(millis)
    val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun isToday(ts: Long): Boolean {
    val t = Calendar.getInstance().apply { timeInMillis = ts }
    val n = Calendar.getInstance()
    return t.get(Calendar.YEAR) == n.get(Calendar.YEAR) &&
            t.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR)
}

private fun isUpcoming(ts: Long): Boolean {
    val days = TimeUnit.MILLISECONDS.toDays(ts - System.currentTimeMillis())
    return days in 1..13
}

private val bouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val mediumSpring = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = Spring.StiffnessMedium
)

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 2 ─ Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TodoScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: TodoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val vibrationManager = LocalVibrationManager.current

    var selectedFilter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }
    var selectedTaskForEdit by remember { mutableStateOf<TaskEntry?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCompletedSection by rememberSaveable { mutableStateOf(true) }

    val filteredTasks by remember(uiState.tasks, selectedFilter) {
        derivedStateOf {
            when (selectedFilter) {
                TaskFilter.ALL -> uiState.tasks
                TaskFilter.TODAY -> uiState.tasks.filter { it.dueDate?.let(::isToday) == true }
                TaskFilter.UPCOMING -> uiState.tasks.filter { it.dueDate?.let(::isUpcoming) == true }
            }
        }
    }

    val totalCount = uiState.tasks.size + uiState.completedToday.size
    val completionFraction by animateFloatAsState(
        targetValue = if (totalCount == 0) 0f else uiState.completedToday.size.toFloat() / totalCount,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "completionFraction"
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_TodoScreen_a1b2),
                subtitle = "${uiState.completedToday.size} of $totalCount done today",
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.st_TodoScreen_c3d4)
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.st_TodoScreen_e5f6))
                        }
                        SortDropdownMenu(
                            expanded = showSortMenu,
                            currentOrder = uiState.sortOrder,
                            onDismiss = { showSortMenu = false },
                            onOrderSelected = {
                                viewModel.setSortOrder(it)
                                showSortMenu = false
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            QuickAddBar(
                categories = uiState.categories,
                onAddTask = { title, category, priority, dueDate ->
                    viewModel.addTask(title, null, category, priority, dueDate)
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Progress header ───────────────────────────────────────────────
            TaskProgressHeader(
                completionFraction = completionFraction,
                completedCount = uiState.completedToday.size,
                totalCount = totalCount,
                isSessionActive = uiState.isSessionActive,
                sessionTimeMillis = uiState.sessionTimeMillis,
                sessionTaskId = uiState.sessionTaskId,
                allTasks = uiState.tasks + uiState.completedToday,
                onStopSession = { viewModel.stopSession() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Filter bar ────────────────────────────────────────────────────
            ToolzConnectedButtonGroup(
                selectedIndex = selectedFilter.ordinal,
                options = TaskFilter.entries.map { stringResource(it.label) },
                onOptionSelected = { selectedFilter = TaskFilter.entries[it] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Spacer(Modifier.height(4.dp))

            // ── Main task list ────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 24.dp, bottom = 64.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── Active tasks ──
                    if (filteredTasks.isEmpty()) {
                        item(key = "empty_placeholder") {
                            EmptyTasksPlaceholder(filter = selectedFilter)
                        }
                    } else {
                        items(filteredTasks, key = { it.id }) { task ->
                            StaggeredEntrance(
                                index = filteredTasks.indexOf(task),
                                modifier = Modifier.animateItem(
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            ) {
                                ActiveTaskCard(
                                    task = task,
                                    isSessionTask = task.id == uiState.sessionTaskId,
                                    isSessionActive = uiState.isSessionActive,
                                    onToggleComplete = {
                                        vibrationManager?.vibrateClick()
                                        viewModel.toggleTaskCompletion(task)
                                    },
                                    onToggleSubTask = { subId ->
                                        vibrationManager?.vibrateTick()
                                        viewModel.toggleSubTask(task, subId)
                                    },
                                    onDelete = {
                                        vibrationManager?.vibrateTick()
                                        viewModel.deleteTask(task)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Task deleted")
                                        }
                                    },
                                    onStartSession = { viewModel.startSession(task.id) },
                                    onStopSession = { viewModel.stopSession() },
                                    onCardClick = { selectedTaskForEdit = task }
                                )
                            }
                        }
                    }

                    // ── Completed section header ──
                    if (uiState.completedToday.isNotEmpty()) {
                        item(key = "completed_header") {
                            CompletedSectionHeader(
                                count = uiState.completedToday.size,
                                expanded = showCompletedSection,
                                onToggle = {
                                    vibrationManager?.vibrateTick()
                                    showCompletedSection = !showCompletedSection
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        if (showCompletedSection) {
                            items(uiState.completedToday, key = { "done_${it.id}" }) { task ->
                                StaggeredEntrance(
                                    index = uiState.completedToday.indexOf(task),
                                    modifier = Modifier.animateItem()
                                ) {
                                    CompletedTaskCard(
                                        task = task,
                                        onToggleComplete = {
                                            vibrationManager?.vibrateClick()
                                            viewModel.toggleTaskCompletion(task)
                                        },
                                        onDelete = {
                                            vibrationManager?.vibrateTick()
                                            viewModel.deleteTask(task)
                                        },
                                        onCardClick = { selectedTaskForEdit = task }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Task detail/edit sheet ────────────────────────────────────────────────
    selectedTaskForEdit?.let { task ->
        TaskDetailSheet(
            task = task,
            categories = uiState.categories,
            isSessionTask = task.id == uiState.sessionTaskId,
            isSessionActive = uiState.isSessionActive,
            onDismiss = { selectedTaskForEdit = null },
            onSaveTask = { viewModel.updateTask(it) },
            onDeleteTask = {
                viewModel.deleteTask(task)
                selectedTaskForEdit = null
            },
            onToggleSubTask = { subId -> viewModel.toggleSubTask(task, subId) },
            onStartSession = { viewModel.startSession(task.id) },
            onStopSession = { viewModel.stopSession() },
            onAddToCalendar = { viewModel.addToCalendar(task) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 3 ─ Progress Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TaskProgressHeader(
    completionFraction: Float,
    completedCount: Int,
    totalCount: Int,
    isSessionActive: Boolean,
    sessionTimeMillis: Long,
    sessionTaskId: Int?,
    allTasks: List<TaskEntry>,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerAlpha by animateFloatAsState(
        targetValue = if (completionFraction >= 1f && totalCount > 0) 0.55f else 0.28f,
        animationSpec = mediumSpring,
        label = "headerAlpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BouncyShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = containerAlpha),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = when {
                            totalCount == 0 -> stringResource(R.string.st_TodoScreen_g7h8)
                            completionFraction >= 1f -> stringResource(R.string.st_TodoScreen_i9j0)
                            completedCount == 0 -> stringResource(R.string.st_TodoScreen_k1l2)
                            else -> "$completedCount done, ${totalCount - completedCount} to go"
                        },
                        transitionSpec = {
                            fadeIn(tween(300)) + slideInVertically { -it / 2 } togetherWith
                                    fadeOut(tween(200))
                        },
                        label = "headerTitle"
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = stringResource(R.string.st_TodoScreen_m3n4),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Circular wavy progress ring
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
                    ToolzWavyCircularProgressIndicator(
                        progress = { completionFraction },
                        modifier = Modifier.size(58.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "${(completionFraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Wavy linear progress bar
            ToolzWavyLinearProgressIndicator(
                progress = { completionFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )

            // Active session banner (slides in)
            AnimatedVisibility(
                visible = isSessionActive,
                enter = expandVertically(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = shrinkVertically(tween(200)) + fadeOut()
            ) {
                SessionBanner(
                    sessionTimeMillis = sessionTimeMillis,
                    taskTitle = allTasks.find { it.id == sessionTaskId }?.title ?: "Unknown task",
                    onStop = onStopSession,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionBanner(
    sessionTimeMillis: Long,
    taskTitle: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vibrationManager = LocalVibrationManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SmallExpressiveShape,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = taskTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSessionTime(sessionTimeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold
                )
            }
            FilledTonalIconButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    onStop()
                },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.st_TodoScreen_o5p6), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 4 ─ Active Task Card (with swipe-to-dismiss)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveTaskCard(
    task: TaskEntry,
    isSessionTask: Boolean,
    isSessionActive: Boolean,
    onToggleComplete: () -> Unit,
    onToggleSubTask: (String) -> Unit,
    onDelete: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onCardClick: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    var subtasksExpanded by rememberSaveable { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                vibrationManager?.vibrateTick()
                onDelete()
                true
            } else false
        },
        positionalThreshold = { it * 0.38f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDismissBackground(dismissState.targetValue) },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        TaskCardSurface(
            task = task,
            isSessionTask = isSessionTask,
            isSessionActive = isSessionActive,
            subtasksExpanded = subtasksExpanded,
            onToggleComplete = onToggleComplete,
            onToggleSubTask = onToggleSubTask,
            onExpandSubtasks = { subtasksExpanded = !subtasksExpanded },
            onStartSession = onStartSession,
            onStopSession = onStopSession,
            onCardClick = onCardClick
        )
    }
}

@Composable
private fun SwipeDismissBackground(targetValue: SwipeToDismissBoxValue) {
    val bgColor by animateColorAsState(
        targetValue = when (targetValue) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        animationSpec = tween(220),
        label = "swipeBg"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.4f,
        animationSpec = mediumSpring,
        label = "swipeIconAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.8f,
        animationSpec = bouncySpring,
        label = "swipeIconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(SquircleShape)
            .background(bgColor)
            .padding(end = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            Icons.Rounded.Delete,
            contentDescription = stringResource(R.string.st_TodoScreen_q7r8),
            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = iconAlpha),
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        )
    }
}

@Composable
private fun TaskCardSurface(
    task: TaskEntry,
    isSessionTask: Boolean,
    isSessionActive: Boolean,
    subtasksExpanded: Boolean,
    onToggleComplete: () -> Unit,
    onToggleSubTask: (String) -> Unit,
    onExpandSubtasks: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onCardClick: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    // Animate card state on completion (not used here – card only for active tasks)
    val priorityColor = PriorityColors.getOrElse(task.priority - 1) { PriorityColors[0] }
    val urgencyColor = task.dueDate?.let(::dueDateUrgencyColor)

    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
        if (isSessionTask) 6.dp else 2.dp
    )
    val sessionBorderAlpha by animateFloatAsState(
        targetValue = if (isSessionTask) 0.8f else 0f,
        animationSpec = if (performanceMode) tween(120) else spring(dampingRatio = 0.6f),
        label = "sessionBorderAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape)
            .then(
                if (isSessionTask) Modifier.border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = sessionBorderAlpha),
                    shape = SquircleShape
                ) else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick
            ),
        shape = SquircleShape,
        color = cardColor,
        tonalElevation = if (performanceMode) 0.dp else if (isSessionTask) 4.dp else 1.dp,
        shadowElevation = if (performanceMode) 0.dp else if (isSessionTask) 3.dp else 0.5.dp
    ) {
        Column(modifier = Modifier.animateContentSize(spring(dampingRatio = 0.65f))) {
            // ── Priority stripe ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        color = priorityColor.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            )

            // ── Card body ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox
                AnimatedCheckbox(
                    checked = false,
                    onCheck = onToggleComplete,
                    color = priorityColor
                )

                Spacer(Modifier.width(10.dp))

                // Title + metadata column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!task.description.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryBadge(label = task.category)

                        task.dueDate?.let { due ->
                            DueDateBadge(
                                label = formatDueDate(due),
                                urgencyColor = urgencyColor
                            )
                        }

                        if (task.subTasks.isNotEmpty()) {
                            SubtaskCountBadge(
                                done = task.subTasks.count { it.isDone },
                                total = task.subTasks.size,
                                expanded = subtasksExpanded,
                                onToggle = {
                                    vibrationManager?.vibrateTick()
                                    onExpandSubtasks()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Session / action button
                SessionIconButton(
                    isSessionTask = isSessionTask,
                    isSessionActive = isSessionActive,
                    onStartSession = onStartSession,
                    onStopSession = onStopSession
                )
            }

            // ── Subtasks (collapsible) ────────────────────────────────────────
            AnimatedVisibility(
                visible = subtasksExpanded && task.subTasks.isNotEmpty(),
                enter = expandVertically(spring(dampingRatio = 0.7f)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 56.dp, end = 16.dp, bottom = 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    task.subTasks.forEach { sub ->
                        SubTaskRow(
                            subTask = sub,
                            onToggle = { onToggleSubTask(sub.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedCheckbox(
    checked: Boolean,
    onCheck: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !performanceMode) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "checkPressScale"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { scaleX = pressScale * scale.value; scaleY = pressScale * scale.value }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    scope.launch {
                        if (!performanceMode) {
                            scale.animateTo(
                                targetValue = 1.35f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            scale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                        onCheck()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (checked) color.copy(alpha = 0.2f) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.7f)),
            modifier = Modifier.size(26.dp)
        ) {
            AnimatedVisibility(
                visible = checked,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionIconButton(
    isSessionTask: Boolean,
    isSessionActive: Boolean,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    val showStop = isSessionTask && isSessionActive
    val performanceMode = LocalPerformanceMode.current

    AnimatedContent(
        targetState = showStop,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn()) togetherWith
                    (scaleOut(tween(150)) + fadeOut(tween(150)))
        },
        label = "sessionBtn"
    ) { stop ->
        if (stop) {
            FilledIconButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    onStopSession()
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.st_TodoScreen_o5p6), modifier = Modifier.size(18.dp))
            }
        } else {
            FilledTonalIconButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    onStartSession()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.st_TodoScreen_w5x6), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 5 ─ Sub-task row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubTaskRow(subTask: SubTask, onToggle: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current

    val checkScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val strikeAlpha by animateFloatAsState(
        targetValue = if (subTask.isDone) 1f else 0f,
        animationSpec = if (performanceMode) tween(120) else spring(dampingRatio = 0.65f),
        label = "subStrikeAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallExpressiveShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                vibrationManager?.vibrateTick()
                scope.launch {
                    if (!performanceMode) {
                        checkScale.animateTo(
                            0.8f,
                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                        )
                        checkScale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                    onToggle()
                }
            }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (subTask.isDone) MaterialTheme.colorScheme.primary.copy(0.15f) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (subTask.isDone) MaterialTheme.colorScheme.primary.copy(0.6f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { scaleX = checkScale.value; scaleY = checkScale.value }
        ) {
            AnimatedVisibility(
                visible = subTask.isDone,
                enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Text(
            text = subTask.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (subTask.isDone)
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            else
                MaterialTheme.colorScheme.onSurface,
            textDecoration = if (subTask.isDone) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.graphicsLayer { alpha = if (subTask.isDone) 0.55f else 1f }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 6 ─ Completed task card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompletedTaskCard(
    task: TaskEntry,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onCardClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart },
        positionalThreshold = { it * 0.4f }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDismissBackground(dismissState.targetValue) },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmallExpressiveShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCardClick
                ),
            shape = SmallExpressiveShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp).copy(alpha = 0.6f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Filled check icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleComplete
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.st_TodoScreen_s9t0),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CategoryBadge(label = task.category, alpha = 0.55f)
                }

                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.st_TodoScreen_u1v2),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 7 ─ Completed section header & empty placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompletedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "Completed today ($count)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) stringResource(R.string.st_TodoScreen_w3x4) else stringResource(R.string.st_TodoScreen_y5z6),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun EmptyTasksPlaceholder(filter: TaskFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = when (filter) {
                TaskFilter.ALL -> "✨"
                TaskFilter.TODAY -> "📅"
                TaskFilter.UPCOMING -> "🗓️"
            },
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = when (filter) {
                TaskFilter.ALL -> stringResource(R.string.st_TodoScreen_a7b8)
                TaskFilter.TODAY -> stringResource(R.string.st_TodoScreen_c9d0)
                TaskFilter.UPCOMING -> stringResource(R.string.st_TodoScreen_e1f2)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when (filter) {
                TaskFilter.ALL -> stringResource(R.string.st_TodoScreen_g3h4)
                TaskFilter.TODAY -> stringResource(R.string.st_TodoScreen_i5j6)
                TaskFilter.UPCOMING -> stringResource(R.string.st_TodoScreen_k7l8)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 8 ─ Quick-add bar (morphing input)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickAddBar(
    categories: List<String>,
    onAddTask: (title: String, category: String, priority: Int, dueDate: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var title by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Personal") }
    var selectedPriority by remember { mutableIntStateOf(3) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }

    val isExpanded = isFocused || title.isNotEmpty()

    // Morphing corner radius
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 20.dp else 32.dp,
        animationSpec = if (performanceMode) tween(120) else spring(dampingRatio = 0.6f),
        label = "quickAddCorner"
    )
    val surfaceElevation by animateDpAsState(
        targetValue = if (isExpanded) 8.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "quickAddElevation"
    )

    fun submit() {
        if (title.isBlank()) return
        vibrationManager?.vibrateClick()
        onAddTask(title.trim(), selectedCategory, selectedPriority, selectedDueDate)
        title = ""
        selectedDueDate = null
        selectedPriority = 3
        isFocused = false
        focusManager.clearFocus()
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (performanceMode) 0.dp else surfaceElevation,
        tonalElevation = if (performanceMode) 2.dp else 0.dp
    ) {
        Column(modifier = Modifier.animateContentSize(spring(dampingRatio = 0.65f))) {

            // ── Input row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val addIconScale by animateFloatAsState(
                    targetValue = if (isExpanded) 0.85f else 1f,
                    animationSpec = bouncySpring,
                    label = "addIconScale"
                )
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { scaleX = addIconScale; scaleY = addIconScale }
                )

                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.st_TodoScreen_m9n0),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            inner()
                        }
                    }
                )

                // Submit / clear button
                AnimatedVisibility(
                    visible = title.isNotEmpty(),
                    enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150))
                ) {
                    ToolzExpressiveIconButton(
                        onClick = { submit() },
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.st_TodoScreen_o1p2), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Expanded options ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(spring(dampingRatio = 0.65f)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp, end = 16.dp, bottom = 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    // ── Priority selector ─────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Flag,
                            contentDescription = stringResource(R.string.st_TodoScreen_q3r4),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        (1..5).forEach { p ->
                            val isSelected = selectedPriority == p
                            val pColor = PriorityColors[p - 1]
                            val pScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1f,
                                animationSpec = spring(Spring.DampingRatioLowBouncy),
                                label = "pScale_$p"
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) pColor.copy(alpha = 0.22f) else pColor.copy(alpha = 0.10f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) pColor else pColor.copy(0.4f)
                                ),
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer { scaleX = pScale; scaleY = pScale }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        vibrationManager?.vibrateTick()
                                        selectedPriority = p
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = p.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                        color = if (isSelected) pColor else pColor.copy(0.7f)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Date picker toggle
                        val dateLabel = selectedDueDate?.let { formatDueDate(it) } ?: stringResource(R.string.st_TodoScreen_s5t6)
                        ExpressiveFilterChip(
                            selected = selectedDueDate != null,
                            onClick = {
                                vibrationManager?.vibrateTick()
                                showDatePicker = true
                            },
                            label = {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(14.dp))
                            },
                            trailingIcon = if (selectedDueDate != null) {
                                {
                                    Icon(
                                        Icons.Rounded.Close, null,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { selectedDueDate = null }
                                    )
                                }
                            } else null,
                            shape = SmallExpressiveShape
                        )
                    }

                    // ── Category chips ────────────────────────────────────────
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        items(categories) { cat ->
                            ExpressiveFilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    vibrationManager?.vibrateTick()
                                    selectedCategory = cat
                                },
                                label = {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                shape = SmallExpressiveShape
                            )
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDueDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDueDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text(stringResource(R.string.st_TodoScreen_u7v8)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.st_TodoScreen_w9x0)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 9 ─ Task Detail & Edit Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TaskDetailSheet(
    task: TaskEntry,
    categories: List<String>,
    isSessionTask: Boolean,
    isSessionActive: Boolean,
    onDismiss: () -> Unit,
    onSaveTask: (TaskEntry) -> Unit,
    onDeleteTask: () -> Unit,
    onToggleSubTask: (String) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onAddToCalendar: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Local edit state
    var editTitle by rememberSaveable { mutableStateOf(task.title) }
    var editDescription by rememberSaveable { mutableStateOf(task.description ?: "") }
    var editCategory by rememberSaveable { mutableStateOf(task.category) }
    var editPriority by rememberSaveable { mutableIntStateOf(task.priority) }
    var editDueDate by remember { mutableStateOf<Long?>(task.dueDate) }
    var newSubTaskText by remember { mutableStateOf("") }
    var editSubTasks by remember { mutableStateOf(task.subTasks) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun save() {
        if (editTitle.isBlank()) return
        onSaveTask(
            task.copy(
                title = editTitle.trim(),
                description = editDescription.trim().ifBlank { null },
                category = editCategory,
                priority = editPriority,
                dueDate = editDueDate,
                subTasks = editSubTasks
            )
        )
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // ── Sheet header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (task.isCompleted) stringResource(R.string.st_TodoScreen_y1z2) else stringResource(R.string.st_TodoScreen_a3b4),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.st_TodoScreen_q7r8),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }

            // ── Title field ───────────────────────────────────────────────────
            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = { Text(stringResource(R.string.st_TodoScreen_c5d6)) },
                modifier = Modifier.fillMaxWidth(),
                shape = SmallExpressiveShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            Spacer(Modifier.height(10.dp))

            // ── Description field ─────────────────────────────────────────────
            OutlinedTextField(
                value = editDescription,
                onValueChange = { editDescription = it },
                label = { Text(stringResource(R.string.st_TodoScreen_e7f8)) },
                modifier = Modifier.fillMaxWidth(),
                shape = SmallExpressiveShape,
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            Spacer(Modifier.height(14.dp))

            // ── Priority row ──────────────────────────────────────────────────
            Text(
                stringResource(R.string.st_TodoScreen_q3r4),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { p ->
                    val isSelected = editPriority == p
                    val pColor = PriorityColors[p - 1]
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1f,
                        animationSpec = spring(Spring.DampingRatioLowBouncy),
                        label = "sheetPriority_$p"
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(SmallExpressiveShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                vibrationManager?.vibrateTick()
                                editPriority = p
                            },
                        shape = SmallExpressiveShape,
                        color = if (isSelected) pColor.copy(alpha = 0.2f) else pColor.copy(alpha = 0.07f),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) pColor else pColor.copy(0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(pColor, CircleShape)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(PriorityLabels[p - 1]),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) pColor else pColor.copy(0.7f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Category & Due date row ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.st_TodoScreen_g9h0),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            ExpressiveFilterChip(
                                selected = editCategory == cat,
                                onClick = {
                                    vibrationManager?.vibrateTick()
                                    editCategory = cat
                                },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                shape = CircleShape
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Due date row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.st_TodoScreen_i1j2),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExpressiveFilterChip(
                    selected = editDueDate != null,
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            text = editDueDate?.let(::formatDueDate) ?: stringResource(R.string.st_TodoScreen_k3l4),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(14.dp)) },
                    trailingIcon = if (editDueDate != null) {
                        { Icon(Icons.Rounded.Close, null, modifier = Modifier
                            .size(14.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { editDueDate = null }) }
                    } else null,
                    shape = CircleShape
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Sub-tasks ─────────────────────────────────────────────────────
            if (editSubTasks.isNotEmpty() || true) {
                Text(
                    "Sub-tasks (${editSubTasks.count { it.isDone }}/${editSubTasks.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                editSubTasks.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SubTaskRow(
                            subTask = sub,
                            onToggle = {
                                editSubTasks = editSubTasks.map {
                                    if (it.id == sub.id) it.copy(isDone = !it.isDone) else it
                                }
                                onToggleSubTask(sub.id)
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { editSubTasks = editSubTasks.filter { it.id != sub.id } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.st_TodoScreen_m5n6),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Add new sub-task input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newSubTaskText,
                        onValueChange = { newSubTaskText = it },
                        placeholder = { Text(stringResource(R.string.st_TodoScreen_o7p8), style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f),
                        shape = SmallExpressiveShape,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newSubTaskText.isNotBlank()) {
                                editSubTasks = editSubTasks + SubTask(
                                    id = UUID.randomUUID().toString(),
                                    title = newSubTaskText.trim(),
                                    isDone = false
                                )
                                newSubTaskText = ""
                            }
                        })
                    )
                    AnimatedVisibility(
                        visible = newSubTaskText.isNotBlank(),
                        enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                editSubTasks = editSubTasks + SubTask(
                                    id = UUID.randomUUID().toString(),
                                    title = newSubTaskText.trim(),
                                    isDone = false
                                )
                                newSubTaskText = ""
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.st_TodoScreen_q9r0), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // ── Action buttons ────────────────────────────────────────────────
            ToolzExpressiveButton(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.st_TodoScreen_s1t2), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolzOutlinedExpressiveButton(
                    onClick = {
                        vibrationManager?.vibrateTick()
                        if (isSessionTask && isSessionActive) onStopSession()
                        else onStartSession()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isSessionTask && isSessionActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isSessionTask && isSessionActive) stringResource(R.string.st_TodoScreen_u3v4) else stringResource(R.string.st_TodoScreen_w5x6),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                ToolzOutlinedExpressiveButton(
                    onClick = {
                        vibrationManager?.vibrateTick()
                        onAddToCalendar()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.st_TodoScreen_y7z8), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        BasicAlertDialog(onDismissRequest = { showDeleteConfirm = false }) {
            Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.st_TodoScreen_a9b0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.st_TodoScreen_c1d2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolzOutlinedExpressiveButton(
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.st_TodoScreen_w9x0)) }
                        ToolzExpressiveButton(
                            onClick = { onDeleteTask(); showDeleteConfirm = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) { Text(stringResource(R.string.st_TodoScreen_e3f4), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = editDueDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { editDueDate = state.selectedDateMillis; showDatePicker = false }) {
                    Text(stringResource(R.string.st_TodoScreen_u7v8))
                }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.st_TodoScreen_w9x0)) } }
        ) { DatePicker(state = state) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 10 ─ Badge composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(label: String, alpha: Float = 1f) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha * 0.6f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DueDateBadge(label: String, urgencyColor: Color?) {
    val color = urgencyColor ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    Surface(
        shape = CircleShape,
        color = (urgencyColor ?: Color.Transparent).copy(alpha = 0.12f),
        border = if (urgencyColor != null) androidx.compose.foundation.BorderStroke(
            0.8.dp, urgencyColor.copy(alpha = 0.5f)
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = color, modifier = Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SubtaskCountBadge(done: Int, total: Int, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onToggle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "$done/$total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = "Toggle sub-tasks",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 11 ─ Sort Dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentOrder: TaskSortOrder,
    onDismiss: () -> Unit,
    onOrderSelected: (TaskSortOrder) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = SmallExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = stringResource(R.string.st_TodoScreen_g5h6),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
        TaskSortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = when (order) {
                            TaskSortOrder.URGENCY -> stringResource(R.string.st_TodoScreen_i7j8)
                            TaskSortOrder.PRIORITY -> stringResource(R.string.st_TodoScreen_q3r4)
                            TaskSortOrder.DATE_ADDED -> stringResource(R.string.st_TodoScreen_k9l0)
                            TaskSortOrder.DUE_DATE -> stringResource(R.string.st_TodoScreen_m1n2)
                        },
                        fontWeight = if (order == currentOrder) FontWeight.Bold else FontWeight.Normal,
                        color = if (order == currentOrder) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = { onOrderSelected(order) },
                trailingIcon = if (order == currentOrder) {
                    {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 12 ─ Previews
// ─────────────────────────────────────────────────────────────────────────────

private class SampleTaskProvider : PreviewParameterProvider<TaskEntry> {
    private val sampleSubTasks = listOf(
        SubTask(id = "1", title = "Research frameworks", isDone = true),
        SubTask(id = "2", title = "Write first draft", isDone = false),
        SubTask(id = "3", title = "Review with team", isDone = false)
    )

    override val values = sequenceOf(
        TaskEntry(
            id = 1,
            title = "Design new onboarding flow",
            description = "Create wireframes and user journey maps for the revamped sign-up.",
            category = "Dev",
            priority = 4,
            dueDate = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(18),
            subTasks = sampleSubTasks,
            isCompleted = false
        ),
        TaskEntry(
            id = 2,
            title = "Morning jog — 5km target",
            description = null,
            category = "Fitness",
            priority = 2,
            dueDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1),
            subTasks = emptyList(),
            isCompleted = false
        ),
        TaskEntry(
            id = 3,
            title = "Submit quarterly report",
            description = "Compile metrics and attach board summary.",
            category = "Work",
            priority = 5,
            dueDate = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2),
            subTasks = emptyList(),
            isCompleted = false
        )
    )
}

@Preview(
    name = "TodoScreen — Light",
    showBackground = true,
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "TodoScreen — Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun TodoScreenPreview() {
    ToolzTheme {
        // Preview uses the standalone composables only (no ViewModel DI)
        val sampleTasks = SampleTaskProvider().values.toList()
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                QuickAddBar(
                    categories = listOf("Personal", "Dev", "Work", "Fitness"),
                    onAddTask = { _, _, _, _ -> },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TaskProgressHeader(
                    completionFraction = 0.4f,
                    completedCount = 2,
                    totalCount = 5,
                    isSessionActive = true,
                    sessionTimeMillis = TimeUnit.MINUTES.toMillis(23) + TimeUnit.SECONDS.toMillis(41),
                    sessionTaskId = 1,
                    allTasks = sampleTasks,
                    onStopSession = {},
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ToolzConnectedButtonGroup(
                    selectedIndex = 0,
                    options = listOf("All", "Today", "Next"),
                    onOptionSelected = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 24.dp, bottom = 64.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sampleTasks, key = { it.id }) { task ->
                        StaggeredEntrance(index = sampleTasks.indexOf(task)) {
                            TaskCardSurface(
                                task = task,
                                isSessionTask = task.id == 1,
                                isSessionActive = true,
                                subtasksExpanded = task.id == 1,
                                onToggleComplete = {},
                                onToggleSubTask = {},
                                onExpandSubtasks = {},
                                onStartSession = {},
                                onStopSession = {},
                                onCardClick = {}
                            )
                        }
                    }

                    item(key = "completed_header") {
                        CompletedSectionHeader(count = 2, expanded = true, onToggle = {})
                    }

                    item(key = "completed_1") {
                        CompletedTaskCard(
                            task = sampleTasks.first().copy(isCompleted = true, title = "Read research paper"),
                            onToggleComplete = {},
                            onDelete = {},
                            onCardClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "QuickAddBar — Expanded", showBackground = true)
@Composable
private fun QuickAddBarPreview() {
    ToolzTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            QuickAddBar(
                categories = listOf("Personal", "Dev", "Work", "Fitness", "Shopping"),
                onAddTask = { _, _, _, _ -> },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "Task cards — Light")
@Preview(
    name = "Task cards — Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun TaskCardPreview(@PreviewParameter(SampleTaskProvider::class) task: TaskEntry) {
    ToolzTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            TaskCardSurface(
                task = task,
                isSessionTask = task.id == 1,
                isSessionActive = task.id == 1,
                subtasksExpanded = task.subTasks.isNotEmpty(),
                onToggleComplete = {},
                onToggleSubTask = {},
                onExpandSubtasks = {},
                onStartSession = {},
                onStopSession = {},
                onCardClick = {}
            )
        }
    }
}

@Preview(name = "Progress Header — Light", showBackground = true)
@Composable
private fun ProgressHeaderPreview() {
    ToolzTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            TaskProgressHeader(
                completionFraction = 0.67f,
                completedCount = 4,
                totalCount = 6,
                isSessionActive = true,
                sessionTimeMillis = TimeUnit.MINUTES.toMillis(12),
                sessionTaskId = 1,
                allTasks = SampleTaskProvider().values.toList(),
                onStopSession = {}
            )
        }
    }
}