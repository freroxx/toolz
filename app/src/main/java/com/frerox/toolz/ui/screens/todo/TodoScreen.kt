package com.frerox.toolz.ui.screens.todo

import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frerox.toolz.data.todo.SubTask
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntry?>(null) }
    var viewingTaskId by remember { mutableStateOf<Int?>(null) }
    val viewingTask = remember(viewingTaskId, state.tasks, state.completedToday) {
        (state.tasks + state.completedToday).find { it.id == viewingTaskId }
    }
    var expandedTaskIds by remember { mutableStateOf(setOf<Int>()) }
    var showBrainDump by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "SessionBreathing")
    val sessionPulse by if (performanceMode) remember { mutableFloatStateOf(0.1f) } else infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "Pulse"
    )

    val sessionBgColor by animateColorAsState(
        targetValue = if (state.isSessionActive) MaterialTheme.colorScheme.primary.copy(alpha = sessionPulse) 
                      else Color.Transparent,
        animationSpec = if (performanceMode) snap() else tween(2000),
        label = "SessionBg"
    )

    Box(modifier = Modifier.fillMaxSize().background(sessionBgColor)) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TASK FLOW", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium)
                            if (state.isSessionActive) {
                                AnimatedVisibility(
                                    visible = state.isSessionActive,
                                    enter = if (performanceMode) fadeIn() else fadeIn() + expandVertically(),
                                    exit = if (performanceMode) fadeOut() else fadeOut() + shrinkVertically()
                                ) {
                                    Text(
                                        "ACTIVE • ${formatSessionTime(state.sessionTimeMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { 
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showSortMenu = true 
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                TaskSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = { if (state.sortOrder == order) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                }
                            }
                        }
                        if (state.isSessionActive) {
                            IconButton(onClick = { 
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.stopSession() 
                            }) {
                                Icon(Icons.Rounded.StopCircle, "Stop session", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { 
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        editingTask = null
                        showEditor = true 
                    },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    showBrainDump = true 
                                }
                            )
                        },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("NEW TASK", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                TaskSummaryHeader(activeCount = state.tasks.size, completedToday = state.completedToday.size)

                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize().then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    if (state.tasks.isEmpty() && state.completedToday.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            EmptyTasksState(onAddClick = { 
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showEditor = true 
                            })
                        }
                    } else {
                        items(state.tasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                isSessionTarget = state.sessionTaskId == task.id,
                                performanceMode = performanceMode,
                                isExpanded = expandedTaskIds.contains(task.id),
                                onToggle = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.toggleTaskCompletion(task) 
                                },
                                onClick = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewingTaskId = task.id 
                                },
                                onLongClick = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    editingTask = task
                                    showEditor = true 
                                },
                                onStartSession = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.startSession(task.id) 
                                },
                                onExpandToggle = {
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    expandedTaskIds = if (expandedTaskIds.contains(task.id)) {
                                        expandedTaskIds - task.id
                                    } else {
                                        expandedTaskIds + task.id
                                    }
                                },
                                onToggleSubtask = { subId ->
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.toggleSubTask(task, subId)
                                },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = if (performanceMode) snap() else spring(),
                                    fadeOutSpec = if (performanceMode) snap() else spring(),
                                    placementSpec = if (performanceMode) snap() else spring()
                                )
                            )
                        }
                        
                        if (state.completedToday.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Text(
                                    "COMPLETED TODAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                                    letterSpacing = 2.sp
                                )
                            }
                            items(state.completedToday, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    isSessionTarget = false,
                                    performanceMode = performanceMode,
                                    isExpanded = expandedTaskIds.contains(task.id),
                                    onToggle = { 
                                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.toggleTaskCompletion(task) 
                                    },
                                    onClick = { 
                                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewingTaskId = task.id 
                                    },
                                    onLongClick = { },
                                    onStartSession = { },
                                    onExpandToggle = {
                                        expandedTaskIds = if (expandedTaskIds.contains(task.id)) {
                                            expandedTaskIds - task.id
                                        } else {
                                            expandedTaskIds + task.id
                                        }
                                    },
                                    onToggleSubtask = { subId ->
                                        viewModel.toggleSubTask(task, subId)
                                    },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = if (performanceMode) snap() else spring(),
                                        fadeOutSpec = if (performanceMode) snap() else spring(),
                                        placementSpec = if (performanceMode) snap() else spring()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showEditor) {
            TaskEditorSheet(
                task = editingTask,
                categories = state.categories,
                onDismiss = { showEditor = false },
                onSave = { title, desc, cat, priority, dueDate, subtasks ->
                    if (editingTask == null) {
                        viewModel.addTask(title, desc, cat, priority, dueDate, subtasks)
                    } else {
                        viewModel.updateTask(editingTask!!.copy(
                            title = title,
                            description = desc,
                            category = cat,
                            priority = priority,
                            dueDate = dueDate,
                            subTasks = subtasks
                        ))
                    }
                    showEditor = false
                },
                onDelete = {
                    editingTask?.let { viewModel.deleteTask(it) }
                    showEditor = false
                },
                onAddCategory = { viewModel.addCategory(it) }
            )
        }

        if (showBrainDump) {
            BrainDumpOverlay(
                onDismiss = { showBrainDump = false },
                performanceMode = performanceMode,
                onConfirm = { title ->
                    viewModel.addTask(title = title, category = "General", priority = 3)
                    showBrainDump = false
                }
            )
        }

        AnimatedVisibility(
            visible = viewingTask != null,
            enter = if (performanceMode) fadeIn() else fadeIn() + scaleIn(initialScale = 0.9f) + slideInVertically { it / 4 },
            exit = if (performanceMode) fadeOut() else fadeOut() + scaleOut(targetScale = 0.9f) + slideOutVertically { it / 4 }
        ) {
            viewingTask?.let { task ->
                TaskDetailOverlay(
                    task = task,
                    onDismiss = { viewingTaskId = null },
                    onToggleSubtask = { subId -> 
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.toggleSubTask(task, subId) 
                    },
                    onToggleComplete = { 
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.toggleTaskCompletion(task)
                        viewingTaskId = null
                    },
                    onStartSession = {
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.startSession(task.id)
                        viewingTaskId = null
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyTasksState(onAddClick: () -> Unit) {
    val performanceMode = LocalPerformanceMode.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.AssignmentTurnedIn,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        @Suppress("DEPRECATION")
        Text(
            "ALL CLEAR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No active tasks detected. Deploy a new objective to start your workflow.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(56.dp).padding(horizontal = 32.dp)
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(12.dp))
            Text("CREATE TASK", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun TaskSummaryHeader(activeCount: Int, completedToday: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SummaryCard(
            label = "PENDING",
            count = activeCount.toString(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.PendingActions
        )
        SummaryCard(
            label = "RECORDS",
            count = completedToday.toString(),
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.TaskAlt
        )
    }
}

@Composable
fun SummaryCard(label: String, count: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(8.dp))
                @Suppress("DEPRECATION")
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(count, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
fun TaskCard(
    task: TaskEntry,
    isSessionTarget: Boolean,
    performanceMode: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStartSession: () -> Unit,
    onExpandToggle: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CardGlow")
    
    val glowAlpha by if (performanceMode) remember { mutableFloatStateOf(0f) } else infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "Glow"
    )

    val breathingScale by if (performanceMode) remember { mutableFloatStateOf(1f) } else infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "Breathing"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isSessionTarget && !performanceMode) breathingScale else 1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(32.dp),
        color = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else if (isSessionTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isSessionTarget) 2.dp else 1.5.dp,
            if (isSessionTarget) MaterialTheme.colorScheme.primary
            else if (task.priority == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        shadowElevation = if ((task.priority == 1 || isSessionTarget) && !performanceMode) 8.dp else 0.dp
    ) {
        Box {
            if ((task.priority == 1 || isSessionTarget) && !task.isCompleted && !performanceMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(task.category)
                    Spacer(Modifier.width(12.dp))
                    PriorityBadge(task.priority)
                    Spacer(Modifier.weight(1f))
                    if (!task.isCompleted) {
                        IconButton(onClick = onStartSession, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isSessionTarget) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (task.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint = if (task.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )

                if (!task.description.isNullOrBlank() && !task.isCompleted) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (task.subTasks.isNotEmpty()) {
                            val doneCount = task.subTasks.count { it.isDone }
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                onClick = onExpandToggle
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("$doneCount/${task.subTasks.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        if (task.dueDate != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = DateUtils.getRelativeTimeSpanString(task.dueDate).toString().lowercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded && task.subTasks.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        task.subTasks.forEach { sub ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .pointerInput(Unit) {
                                        detectTapGestures { onToggleSubtask(sub.id) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    if (sub.isDone) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    null,
                                    tint = if (sub.isDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    sub.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (sub.isDone) FontWeight.Normal else FontWeight.Bold,
                                    textDecoration = if (sub.isDone) TextDecoration.LineThrough else null,
                                    color = if (sub.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
fun PriorityBadge(priority: Int) {
    val color = when(priority) {
        1 -> Color(0xFFD32F2F)
        2 -> Color(0xFFF44336)
        3 -> MaterialTheme.colorScheme.primary
        4 -> Color(0xFF8BC34A)
        else -> Color(0xFF9E9E9E)
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = "P$priority",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    task: TaskEntry?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, Int, Long?, List<SubTask>) -> Unit,
    onDelete: () -> Unit,
    onAddCategory: (String) -> Unit
) {
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var category by remember { mutableStateOf(task?.category ?: categories.firstOrNull() ?: "General") }
    var priority by remember { mutableStateOf(task?.priority ?: 3) }
    var subTasks by remember { mutableStateOf(task?.subTasks ?: emptyList()) }
    var dueDate by remember { mutableStateOf(task?.dueDate) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newSubtaskTitle by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (task == null) "NEW OBJECTIVE" else "MODIFY OBJECTIVE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                if (task != null) {
                    IconButton(
                        onClick = {
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onDelete() 
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("TASK IDENTIFIER") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("ANALYTICS / DETAILS") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                maxLines = 3
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
                onClick = { 
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showDatePicker = true 
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        @Suppress("DEPRECATION")
                        Text("DEADLINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = dueDate?.let { DateUtils.formatDateTime(LocalContext.current, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR) } ?: "NO TEMPORAL LIMIT",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (dueDate != null) {
                        IconButton(onClick = { 
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            dueDate = null 
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    @Suppress("DEPRECATION")
                    Text("CLASSIFICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { 
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showAddCategory = true 
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { 
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                category = cat 
                            },
                            label = { Text(cat.uppercase(), fontWeight = FontWeight.Black, fontSize = 10.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            Column {
                @Suppress("DEPRECATION")
                Text("PRIORITY INDEX", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { p ->
                        val isSelected = priority == p
                        val pColor = when(p) {
                            1 -> Color(0xFFD32F2F)
                            2 -> Color(0xFFF44336)
                            3 -> MaterialTheme.colorScheme.primary
                            4 -> Color(0xFF8BC34A)
                            else -> Color(0xFF9E9E9E)
                        }
                        Surface(
                            onClick = { 
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                priority = p 
                            },
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) pColor else pColor.copy(alpha = 0.1f),
                            border = BorderStroke(1.5.dp, if (isSelected) Color.White.copy(alpha = 0.3f) else pColor.copy(alpha = 0.2f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(p.toString(), color = if (isSelected) Color.White else pColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            Column {
                @Suppress("DEPRECATION")
                Text("SUB-STRUCTURE CHECKLIST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                subTasks.forEach { sub ->
                    Surface(
                        modifier = Modifier.padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                            Checkbox(checked = sub.isDone, onCheckedChange = { isDone ->
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                subTasks = subTasks.map { if (it.id == sub.id) it.copy(isDone = isDone) else it }
                            })
                            Text(sub.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { 
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                subTasks = subTasks.filter { it.id != sub.id } 
                            }) {
                                Icon(Icons.Rounded.RemoveCircleOutline, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = newSubtaskTitle,
                    onValueChange = { newSubtaskTitle = it },
                    placeholder = { Text("NEW SUB-OBJECTIVE...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (newSubtaskTitle.isNotBlank()) {
                                subTasks = subTasks + SubTask(UUID.randomUUID().toString(), newSubtaskTitle)
                                newSubtaskTitle = ""
                            }
                        }) {
                            Icon(Icons.Rounded.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (newSubtaskTitle.isNotBlank()) {
                            subTasks = subTasks + SubTask(UUID.randomUUID().toString(), newSubtaskTitle)
                            newSubtaskTitle = ""
                        }
                        focusManager.clearFocus()
                    })
                )
            }

            Button(
                onClick = { 
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (title.isNotBlank()) onSave(title, description.takeIf { it.isNotBlank() }, category, priority, dueDate, subTasks) 
                },
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("DEPLOY OBJECTIVE", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    dueDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("INDEX", fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showDatePicker = false 
                }) { Text("CANCEL", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(32.dp)
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAddCategory) {
        AlertDialog(
            onDismissRequest = { showAddCategory = false },
            title = { Text("NEW CLASSIFICATION", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("Identifier name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName)
                        category = newCategoryName
                        newCategoryName = ""
                        showAddCategory = false
                    }
                }, shape = RoundedCornerShape(16.dp)) { Text("ADD", fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showAddCategory = false 
                }) { Text("CANCEL", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(32.dp)
        )
    }
}

@Composable
fun TaskDetailOverlay(
    task: TaskEntry,
    onDismiss: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    onToggleComplete: () -> Unit,
    onStartSession: () -> Unit
) {
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(task.category)
                    Spacer(Modifier.width(12.dp))
                    PriorityBadge(task.priority)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss() 
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }

                Column {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (task.dueDate != null) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                @Suppress("DEPRECATION")
                                Text(
                                    text = DateUtils.getRelativeTimeSpanString(task.dueDate).toString().uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                if (!task.description.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Notes, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                task.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                if (task.subTasks.isNotEmpty()) {
                    @Suppress("DEPRECATION")
                    Text("OBJECTIVE COMPONENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        task.subTasks.forEach { sub ->
                            Surface(
                                onClick = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onToggleSubtask(sub.id) 
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (sub.isDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                border = if (sub.isDone) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Icon(
                                        if (sub.isDone) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                        null,
                                        tint = if (sub.isDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        sub.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (sub.isDone) TextDecoration.LineThrough else null,
                                        color = if (sub.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (sub.isDone) FontWeight.Normal else FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!task.isCompleted) {
                        Button(
                            onClick = {
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onStartSession() 
                            },
                            modifier = Modifier.weight(1f).height(60.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ENGAGE", fontWeight = FontWeight.Black)
                        }
                    }

                    Button(
                        onClick = {
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleComplete() 
                        },
                        modifier = Modifier.weight(1.2f).height(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                            contentColor = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(if (task.isCompleted) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (task.isCompleted) "RESTORE" else "RESOLVE", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun BrainDumpOverlay(
    onDismiss: () -> Unit,
    performanceMode: Boolean,
    onConfirm: (String) -> Unit
) {
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .then(if (performanceMode) Modifier else Modifier.blur(12.dp))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (performanceMode) 0.dp else 24.dp
        ) {
            Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    @Suppress("DEPRECATION")
                    Text("NEURAL QUICK ADD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("What's next?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (text.isNotBlank()) onConfirm(text)
                        focusManager.clearFocus()
                    }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )

                Button(
                    onClick = { 
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (text.isNotBlank()) onConfirm(text) 
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("DEPLOY TASK", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private fun formatSessionTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun CategoryBadge(category: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = category.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
    }
}
