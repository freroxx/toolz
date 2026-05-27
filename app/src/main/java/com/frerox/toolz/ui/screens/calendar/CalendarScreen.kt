@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.frerox.toolz.ui.screens.calendar

import com.frerox.toolz.ui.theme.toolzBackground
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.ai.AiConfig
import com.frerox.toolz.data.calendar.EventEntry
import com.frerox.toolz.data.calendar.SyncResult
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzLargeExtendedFloatingActionButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 1 ─ Design tokens & helpers
// ─────────────────────────────────────────────────────────────────────────────

val EventColorPalette = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#3F51B5",
    "#2196F3", "#00BCD4", "#009688", "#4CAF50",
    "#FF9800", "#FF5722", "#795548", "#607D8B"
)

private val TaskGreen = Color(0xFF43A047)

sealed class CalendarItem {
    abstract val timestamp: Long
    data class Event(val event: EventEntry) : CalendarItem() {
        override val timestamp: Long = event.timestamp
    }
    data class Task(val task: TaskEntry) : CalendarItem() {
        override val timestamp: Long = task.dueDate ?: 0L
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(t: Long): Boolean {
    val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val target = Calendar.getInstance().apply { timeInMillis = t }
    return yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

private fun eventColor(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

private fun eventTypeIcon(type: String): ImageVector = when (type) {
    "EXAM"       -> Icons.AutoMirrored.Rounded.Assignment
    "EVALUATION" -> Icons.Rounded.Assessment
    "BIRTHDAY"   -> Icons.Rounded.Cake
    "DEADLINE"   -> Icons.Rounded.PriorityHigh
    "MEETING"    -> Icons.Rounded.Groups
    "HOLIDAY"    -> Icons.Rounded.BeachAccess
    else         -> Icons.Rounded.Event
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
// SECTION 2 ─ Root screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState        by viewModel.uiState.collectAsState()
    var isMonthView    by remember { mutableStateOf(true) }
    var showAiSheet    by remember { mutableStateOf(false) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var aiPrompt       by remember { mutableStateOf("") }
    var editingEvent   by remember { mutableStateOf<EventEntry?>(null) }
    var longPressedDate by remember { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context           = LocalContext.current
    val performanceMode   = LocalPerformanceMode.current
    val vibrationManager  = LocalVibrationManager.current
    val scope             = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.setAttachedImage(uriToBitmap(context, it)) }
        }
    )

    val isTodaySelected = isSameDay(uiState.selectedDate, System.currentTimeMillis())
    val scrollBehavior  = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Expressive background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Scaffold(
            modifier         = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost     = { SnackbarHost(snackbarHostState) },
            containerColor   = Color.Transparent,
            topBar = {
                CalendarTopBar(
                    selectedDate       = uiState.selectedDate,
                    isAcademicMode     = uiState.isAcademicMode,
                    isMonthView        = isMonthView,
                    isTodaySelected    = isTodaySelected,
                    onPrevious         = {
                        vibrationManager?.vibrateTick()
                        viewModel.previousMonth()
                    },
                    onNext             = {
                        vibrationManager?.vibrateTick()
                        viewModel.nextMonth()
                    },
                    onToday            = {
                        vibrationManager?.vibrateClick()
                        viewModel.goToToday()
                    },
                    onMonthPickerClick = { showMonthPicker = true },
                    onToggleView       = {
                        vibrationManager?.vibrateTick()
                        isMonthView = !isMonthView
                    },
                    scrollBehavior     = scrollBehavior
                )
            },
            floatingActionButton = {
                CalendarFabMenu(
                    offlineMode   = uiState.offlineModeEnabled,
                    onAiClick     = { showAiSheet = true },
                    onAddClick    = { showAddDialog = true }
                )
            }
        ) { paddingValues ->
            Box(
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                AnimatedContent(
                    targetState  = isMonthView,
                    transitionSpec = {
                        val enter = fadeIn(tween(320)) + slideInVertically(
                            tween(360, easing = FastOutSlowInEasing)
                        ) { if (targetState) -it / 6 else it / 6 }
                        val exit = fadeOut(tween(200)) + slideOutVertically(
                            tween(280, easing = FastOutSlowInEasing)
                        ) { if (targetState) it / 6 else -it / 6 }
                        enter togetherWith exit
                    },
                    label = "main_view"
                ) { monthView ->
                    if (monthView) {
                        MonthViewSection(
                            uiState        = uiState,
                            onDateSelected = viewModel::onDateSelected,
                            onLongPress    = {
                                vibrationManager?.vibrateTick()
                                longPressedDate = it
                            },
                            onSwipeLeft    = {
                                vibrationManager?.vibrateTick()
                                viewModel.nextMonth()
                            },
                            onSwipeRight   = {
                                vibrationManager?.vibrateTick()
                                viewModel.previousMonth()
                            },
                            onEventToggle  = viewModel::toggleEventCompletion,
                            onEventEdit    = { editingEvent = it },
                            onEventDelete  = viewModel::deleteEvent
                        )
                    } else {
                        AgendaView(
                            events        = uiState.events,
                            tasks         = uiState.tasks,
                            onEventToggle = viewModel::toggleEventCompletion,
                            onDelete      = viewModel::deleteEvent,
                            onEdit        = { editingEvent = it },
                            offlineMode   = uiState.offlineModeEnabled
                        )
                    }
                }
            }
        }

        // ── Overlay stack ─────────────────────────────────────────────────────
        if (showMonthPicker) {
            MonthPickerDialog(
                currentDate    = uiState.selectedDate,
                onDismiss      = { showMonthPicker = false },
                onDateSelected = { y, m ->
                    viewModel.setDate(y, m)
                    showMonthPicker = false
                }
            )
        }

        if (showAddDialog) {
            AddEventDialog(
                initialDateMillis = uiState.selectedDate,
                onDismiss         = { showAddDialog = false },
                onConfirm         = { title, desc, ts, type, color, reminders ->
                    viewModel.addManualEvent(title, desc, ts, type, color, reminders)
                    showAddDialog = false
                }
            )
        }

        editingEvent?.let { ev ->
            EditEventDialog(
                event     = ev,
                onDismiss = { editingEvent = null },
                onConfirm = { updated ->
                    viewModel.updateEvent(updated)
                    editingEvent = null
                }
            )
        }

        longPressedDate?.let { date ->
            DayDetailSheet(
                date          = date,
                events        = uiState.events.filter { isSameDay(it.timestamp, date) },
                tasks         = uiState.tasks.filter { it.dueDate?.let { d -> isSameDay(d, date) } ?: false },
                onDismiss     = { longPressedDate = null },
                onDeleteEvent = viewModel::deleteEvent,
                onEditEvent   = { editingEvent = it; longPressedDate = null },
                onToggleEvent = viewModel::toggleEventCompletion
            )
        }

        // Sync confirmation overlay
        AnimatedVisibility(
            visible = uiState.syncResults.isNotEmpty(),
            enter   = slideInVertically(spring(dampingRatio = 0.7f)) { it } + fadeIn(),
            exit    = slideOutVertically(tween(280)) { it } + fadeOut(tween(220))
        ) {
            SyncConfirmationOverlay(
                results            = uiState.syncResults,
                aiPreference       = uiState.aiReminderPreference,
                onPreferenceChange = viewModel::setAiReminderPreference,
                onResultUpdate     = viewModel::updateSyncResult,
                onConfirm          = viewModel::confirmSync,
                onCancel           = viewModel::cancelSync
            )
        }

        // Loading overlay
        AnimatedVisibility(
            visible = uiState.isLoading,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            LoadingOverlay()
        }

        // AI sheet
        if (showAiSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showAiSheet = false },
                sheetState       = sheetState,
                containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation   = 0.dp,
                shape            = MediumExpressiveShape,
                dragHandle = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(36.dp, 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                    }
                }
            ) {
                AiVisionSheetContent(
                    uiState          = uiState,
                    aiPrompt         = aiPrompt,
                    onPromptChange   = { aiPrompt = it },
                    onAttachClick    = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveImage    = { viewModel.setAttachedImage(null) },
                    onConfigSelected = viewModel::switchAiConfig,
                    onProcess        = {
                        viewModel.processAiPrompt(aiPrompt)
                        showAiSheet = false
                        aiPrompt = ""
                    }
                )
            }
        }

        uiState.errorMessage?.let { msg ->
            LaunchedEffect(msg) { snackbarHostState.showSnackbar(msg) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 3 ─ Ambient background
// ─────────────────────────────────────────────────────────────────────────────



// ─────────────────────────────────────────────────────────────────────────────
// SECTION 4 ─ Top app bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarTopBar(
    selectedDate: Long,
    isAcademicMode: Boolean,
    isMonthView: Boolean,
    isTodaySelected: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onMonthPickerClick: () -> Unit,
    onToggleView: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
) {
    val monthYear = remember(selectedDate) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(selectedDate))
    }

    ExpressiveTopAppBar(
        title = {
            // Clickable month-year pill
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pillScale by animateFloatAsState(
                targetValue = if (isPressed) 0.94f else 1f,
                animationSpec = spring(Spring.DampingRatioLowBouncy),
                label = "pillScale"
            )

            Surface(
                onClick = onMonthPickerClick,
                shape   = SmallExpressiveShape,
                color   = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.7f),
                modifier = Modifier.graphicsLayer { scaleX = pillScale; scaleY = pillScale }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AnimatedContent(
                        targetState = monthYear,
                        transitionSpec = {
                            fadeIn(tween(200)) + slideInVertically { -it / 2 } togetherWith
                                    fadeOut(tween(150))
                        },
                        label = "monthYear"
                    ) { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        Icons.Default.ArrowDropDown, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (isAcademicMode) {
                        Icon(
                            Icons.Rounded.School, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        },
        navigationIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExpressiveNavIconButton(icon = Icons.Rounded.ChevronLeft, desc = "Previous month", onClick = onPrevious)
                // Today button — bounces in when off-today
                AnimatedVisibility(
                    visible = !isTodaySelected,
                    enter   = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(200)),
                    exit    = scaleOut(tween(180)) + fadeOut(tween(150))
                ) {
                    ToolzExpressiveIconButton(
                        onClick = onToday,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Today, "Today", modifier = Modifier.size(17.dp))
                    }
                }
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExpressiveNavIconButton(icon = Icons.Rounded.ChevronRight, desc = "Next month", onClick = onNext)
                // View toggle
                val toggleScale = remember { Animatable(1f) }
                val toggleScope = rememberCoroutineScope()
                Surface(
                    onClick = {
                        toggleScope.launch {
                            toggleScale.animateTo(0.88f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                            toggleScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy))
                        }
                        onToggleView()
                    },
                    shape   = SmallExpressiveShape,
                    color   = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { scaleX = toggleScale.value; scaleY = toggleScale.value }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Crossfade(targetState = isMonthView, label = "view_icon") { month ->
                            Icon(
                                if (month) Icons.Rounded.ViewAgenda else Icons.Rounded.CalendarMonth,
                                "Toggle view",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor         = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        )
    )
}

@Composable
private fun ExpressiveNavIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Surface(
        onClick = {
            scope.launch {
                scale.animateTo(0.85f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                scale.animateTo(1f, spring(Spring.DampingRatioLowBouncy))
            }
            vibrationManager?.vibrateTick()
            onClick()
        },
        shape   = SmallExpressiveShape,
        color   = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, desc, modifier = Modifier.size(17.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 5 ─ Expressive FAB menu
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarFabMenu(
    offlineMode: Boolean,
    onAiClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    var expanded by remember { mutableStateOf(false) }

    if (offlineMode) {
        // Single FAB when offline
        ToolzLargeExtendedFloatingActionButton(
            onClick  = onAddClick,
            icon     = { Icon(Icons.Rounded.Add, null) },
            text     = { Text("New Event", fontWeight = FontWeight.Bold) }
        )
        return
    }

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked         = expanded,
                onCheckedChange = {
                    vibrationManager?.vibrateTick()
                    expanded = it
                }
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                    }
                }
                Icon(
                    painter     = rememberVectorPainter(imageVector),
                    contentDescription = "Calendar actions",
                    modifier    = Modifier.animateIcon(
                        { checkedProgress }
                    )
                )
            }
        }
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                vibrationManager?.vibrateClick()
                expanded = false
                onAiClick()
            },
            icon = { Icon(Icons.Rounded.AutoAwesome, null) },
            text = { 
                Text(
                    "AI Scheduler", 
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                vibrationManager?.vibrateClick()
                expanded = false
                onAddClick()
            },
            icon = { Icon(Icons.Rounded.CalendarMonth, null) },
            text = { 
                Text(
                    "New Event", 
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 6 ─ Loading overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .pointerInput(Unit) { /* consume all touch */ },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape           = SquircleShape,
            color           = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 12.dp,
            tonalElevation  = 4.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(48.dp, 40.dp)
            ) {
                val inf   = rememberInfiniteTransition(label = "loader")
                val pulse by inf.animateFloat(
                    0.88f, 1.12f,
                    infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulse"
                )
                val spin by inf.animateFloat(
                    0f, 360f,
                    infiniteRepeatable(tween(10000, easing = LinearEasing)),
                    label = "spin"
                )
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    ToolzWavyCircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Icon(
                        Icons.Rounded.AutoAwesome, null,
                        modifier = Modifier
                            .size(34.dp)
                            .scale(pulse)
                            .graphicsLayer {
                                rotationZ = spin
                            },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Processing…",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "AI is reading your schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 7 ─ Month view (grid + day panel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MonthViewSection(
    uiState: CalendarUiState,
    onDateSelected: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onEventToggle: (EventEntry) -> Unit,
    onEventEdit: (EventEntry) -> Unit,
    onEventDelete: (EventEntry) -> Unit,
) {
    val selectedDayEvents = remember(uiState.events, uiState.selectedDate) {
        uiState.events.filter { isSameDay(it.timestamp, uiState.selectedDate) }
            .sortedBy { it.timestamp }
    }
    val selectedDayTasks = remember(uiState.tasks, uiState.selectedDate) {
        uiState.tasks.filter { it.dueDate?.let { d -> isSameDay(d, uiState.selectedDate) } ?: false }
    }

    var totalDrag by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onSwipeLeft, onSwipeRight) {
                detectHorizontalDragGestures(
                    onDragStart  = { totalDrag = 0f },
                    onDragEnd    = {
                        when {
                            totalDrag > 80f  -> onSwipeRight()
                            totalDrag < -80f -> onSwipeLeft()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        totalDrag += delta
                    }
                )
            }
    ) {
        // ── Month grid ────────────────────────────────────────────────────────
        MonthGrid(
            selectedDate   = uiState.selectedDate,
            events         = uiState.events,
            tasks          = uiState.tasks,
            onDateSelected = onDateSelected,
            onLongPress    = onLongPress
        )

        // ── Persistent day panel ──────────────────────────────────────────────
        DayPreviewPanel(
            selectedDate  = uiState.selectedDate,
            events        = selectedDayEvents,
            tasks         = selectedDayTasks,
            onEventToggle = onEventToggle,
            onEventEdit   = onEventEdit,
            onEventDelete = onEventDelete,
            modifier      = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 8 ─ Month grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonthGrid(
    selectedDate: Long,
    events: List<EventEntry>,
    tasks: List<TaskEntry>,
    onDateSelected: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
) {
    val tz   = java.util.TimeZone.getDefault()
    val cal  = remember(selectedDate) { Calendar.getInstance(tz).apply { timeInMillis = selectedDate } }
    val days = remember(selectedDate) { cal.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val mon  = cal.get(Calendar.MONTH)
    val yr   = cal.get(Calendar.YEAR)
    val firstDow = remember(selectedDate) {
        Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, yr)
            set(Calendar.MONTH, mon)
            set(Calendar.DAY_OF_MONTH, 1)
        }.get(Calendar.DAY_OF_WEEK) - 1
    }
    val numWeeks = (firstDow + days + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        // Day-of-week header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp)
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color      = when (i) {
                            0, 6 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 2.dp),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )

        // Week rows
        for (weekIndex in 0 until numWeeks) {
            Row(modifier = Modifier.fillMaxWidth().height(54.dp)) {
                for (dayIndex in 0..6) {
                    val cellIndex = weekIndex * 7 + dayIndex
                    val day       = cellIndex - firstDow + 1
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (day in 1..days) {
                            val dateMs = remember(day, mon, yr) {
                                Calendar.getInstance(tz).apply {
                                    set(Calendar.YEAR, yr)
                                    set(Calendar.MONTH, mon)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }
                            val dayEvents = remember(events, dateMs) {
                                events.filter { isSameDay(it.timestamp, dateMs) }
                            }
                            val hasTasks  = remember(tasks, dateMs) {
                                tasks.any { it.dueDate?.let { d -> isSameDay(d, dateMs) } ?: false }
                            }
                            DayCell(
                                day         = day,
                                isSelected  = isSameDay(dateMs, selectedDate),
                                isToday     = isSameDay(dateMs, System.currentTimeMillis()),
                                dayEvents   = dayEvents,
                                hasTasks    = hasTasks,
                                onClick     = { onDateSelected(dateMs) },
                                onLongPress = { onLongPress(dateMs) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 9 ─ Day cell — expressive selection spring
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    dayEvents: List<EventEntry>,
    hasTasks: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode  = LocalPerformanceMode.current

    val circleScale by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0f,
        animationSpec = if (performanceMode) tween(120)
        else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "cell_circle"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isToday    -> MaterialTheme.colorScheme.primary
            else       -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
        },
        animationSpec = tween(180),
        label = "cell_text"
    )
    val containerScale = remember { Animatable(1f) }
    val scope          = rememberCoroutineScope()
    val primary        = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = containerScale.value; scaleY = containerScale.value }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        vibrationManager?.vibrateTick()
                        if (!performanceMode) {
                            scope.launch {
                                containerScale.animateTo(0.88f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                                containerScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy))
                            }
                        }
                        onClick()
                    },
                    onLongPress = { onLongPress() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            // Today outline ring (when not selected)
            if (isToday && !isSelected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.5.dp, primary, CircleShape)
                )
            }
            // Bouncy selection fill
            if (circleScale > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .scale(circleScale)
                        .background(primary, CircleShape)
                )
            }
            Text(
                day.toString(),
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color      = textColor
            )
        }

        Spacer(Modifier.height(3.dp))

        // Event dots + task pill
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
            modifier              = Modifier.height(5.dp)
        ) {
            dayEvents.take(2).forEach { event ->
                val dot = remember(event.subjectColor) { eventColor(event.subjectColor) }
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .size(4.dp)
                        .background(
                            if (isSelected) Color.White.copy(0.8f) else (dot ?: Color.Gray),
                            CircleShape
                        )
                )
            }
            if (hasTasks) {
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .size(width = 7.dp, height = 4.dp)
                        .background(
                            if (isSelected) Color.White.copy(0.6f) else TaskGreen,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
            if (dayEvents.size > 2) {
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .size(4.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(0.55f),
                            CircleShape
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 10 ─ Day preview panel (bottom half of month view)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayPreviewPanel(
    selectedDate: Long,
    events: List<EventEntry>,
    tasks: List<TaskEntry>,
    onEventToggle: (EventEntry) -> Unit,
    onEventEdit: (EventEntry) -> Unit,
    onEventDelete: (EventEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier       = modifier.fillMaxWidth(),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        shape          = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(32.dp, 3.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
                            CircleShape
                        )
                )
            }

            // Animated panel content on date change
            AnimatedContent(
                targetState = selectedDate,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 10 }) togetherWith
                            (fadeOut(tween(160)))
                },
                modifier = Modifier.weight(1f),
                label    = "day_panel"
            ) { date ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(date)).uppercase(),
                                style         = MaterialTheme.typography.labelSmall,
                                fontWeight    = FontWeight.ExtraBold,
                                color         = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.8.sp
                            )
                            Text(
                                SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(date)),
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val total = events.size + tasks.size
                        if (total > 0) {
                            Surface(
                                color  = MaterialTheme.colorScheme.primaryContainer,
                                shape  = SmallExpressiveShape
                            ) {
                                Text(
                                    "$total ${if (total == 1) "item" else "items"}",
                                    style    = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(0.4f)
                    )

                    if (events.isEmpty() && tasks.isEmpty()) {
                        PanelEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdges(top = 0.dp, bottom = 48.dp),
                            contentPadding = PaddingValues(
                                start  = 16.dp,
                                end    = 16.dp,
                                top    = 10.dp,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(events, key = { it.id }) { event ->
                                StaggeredEntrance(
                                    index = events.indexOf(event),
                                    modifier = Modifier.animateItem()
                                ) {
                                    CompactEventItem(
                                        event    = event,
                                        onTap    = { onEventEdit(event) },
                                        onToggle = { onEventToggle(event) },
                                        onDelete = { onEventDelete(event) }
                                    )
                                }
                            }
                            items(tasks, key = { it.id }) { task ->
                                StaggeredEntrance(
                                    index = events.size + tasks.indexOf(task),
                                    modifier = Modifier.animateItem()
                                ) {
                                    CompactTaskItem(task)
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
private fun PanelEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("📅", style = MaterialTheme.typography.displaySmall)
            Text(
                "Nothing planned",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f)
            )
            Text(
                "Tap + to add an event",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(0.55f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 11 ─ Compact day-panel event item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactEventItem(
    event: EventEntry,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val accentColor  = remember(event.subjectColor) { eventColor(event.subjectColor) }
    val resolved     = accentColor ?: MaterialTheme.colorScheme.primary
    val completedAlpha by animateFloatAsState(
        targetValue   = if (event.isCompleted) 0.42f else 1f,
        animationSpec = tween(250),
        label         = "compact_alpha"
    )
    var showMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart },
        positionalThreshold = { it * 0.38f }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            vibrationManager?.vibrateTick()
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val bgColor by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(0.5f),
                tween(180), label = "swipeBg"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(SmallExpressiveShape)
                    .background(bgColor)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Surface(
            onClick = onTap,
            color   = MaterialTheme.colorScheme.surfaceContainer,
            shape   = SmallExpressiveShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Left color bar
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                        .background(resolved.copy(alpha = completedAlpha))
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(resolved.copy(alpha = 0.1f * completedAlpha), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            eventTypeIcon(event.eventType), null,
                            tint     = resolved.copy(alpha = completedAlpha),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            event.title,
                            style           = MaterialTheme.typography.bodyMedium,
                            fontWeight      = FontWeight.SemiBold,
                            textDecoration  = if (event.isCompleted) TextDecoration.LineThrough else null,
                            color           = MaterialTheme.colorScheme.onSurface.copy(alpha = completedAlpha),
                            maxLines        = 1,
                            overflow        = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Schedule, null,
                                modifier = Modifier.size(10.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                            )
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                            )
                            Box(
                                Modifier
                                    .size(3.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            Surface(
                                color = resolved.copy(alpha = 0.08f * completedAlpha),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    event.eventType,
                                    style    = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color    = resolved.copy(alpha = completedAlpha),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    // Toggle button
                    FilledTonalIconButton(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            onToggle()
                        },
                        modifier = Modifier.size(30.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (event.isCompleted)
                                resolved.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (event.isCompleted) resolved
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Icon(
                            if (event.isCompleted) Icons.Rounded.CheckCircle
                            else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // More menu
                    Box {
                        IconButton(onClick = { showMenu = true }, Modifier.size(30.dp)) {
                            Icon(
                                Icons.Rounded.MoreVert, null,
                                modifier = Modifier.size(14.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = SmallExpressiveShape
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = { onTap(); showMenu = false },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp)) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { onDelete(); showMenu = false },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTaskItem(task: TaskEntry) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainer,
        shape    = SmallExpressiveShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(TaskGreen)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(32.dp).background(TaskGreen.copy(0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Checklist, null, tint = TaskGreen, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        "Task",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TaskGreen.copy(0.75f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 12 ─ Agenda view
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AgendaView(
    events: List<EventEntry>,
    tasks: List<TaskEntry>,
    onEventToggle: (EventEntry) -> Unit,
    onDelete: (EventEntry) -> Unit,
    onEdit: (EventEntry) -> Unit,
    offlineMode: Boolean = false,
) {
    val itemsByDay = remember(events, tasks) {
        (events.map { CalendarItem.Event(it) } +
                tasks.filter { it.dueDate != null }.map { CalendarItem.Task(it) })
            .sortedBy { it.timestamp }
            .groupBy {
                Calendar.getInstance().apply {
                    timeInMillis = it.timestamp
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
    }

    if (itemsByDay.isEmpty()) {
        AgendaEmptyState(offlineMode)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 80.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsByDay.forEach { (dayMs, dayItems) ->
            val isToday = isSameDay(dayMs, System.currentTimeMillis())
            item(key = "h_$dayMs") {
                AgendaDayHeader(dayMs, isToday, dayItems.size)
            }
            itemsIndexed(dayItems, key = { _, item ->
                when (item) {
                    is CalendarItem.Event -> "ev_${item.event.id}"
                    is CalendarItem.Task  -> "tk_${item.task.id}"
                }
            }) { index, item ->
                StaggeredEntrance(
                    index = index,
                    modifier = Modifier
                        .animateItem(
                            placementSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                        )
                        .padding(bottom = 8.dp)
                ) {
                    when (item) {
                        is CalendarItem.Event -> AgendaEventCard(
                            event    = item.event,
                            onToggle = { onEventToggle(item.event) },
                            onDelete = { onDelete(item.event) },
                            onEdit   = { onEdit(item.event) }
                        )
                        is CalendarItem.Task  -> AgendaTaskCard(item.task)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaEmptyState(offlineMode: Boolean) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🗓️", style = MaterialTheme.typography.displayMedium)
            Text(
                "No scheduled events",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (offlineMode) "Tap + to add one" else "Tap + to add one, or use AI Scan",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AgendaDayHeader(dayMs: Long, isToday: Boolean, count: Int) {
    val isYesterday = remember(dayMs) { isYesterday(dayMs) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isToday || isYesterday) {
            Surface(
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                shape = SmallExpressiveShape
            ) {
                Text(
                    if (isToday) "TODAY" else "YESTERDAY",
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                    letterSpacing = 1.2.sp,
                    modifier      = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Text(
                SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(dayMs)),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        } else {
            Text(
                SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(dayMs)),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            Modifier.weight(1f),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(0.35f)
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
            Text(
                count.toString(),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun AgendaEventCard(
    event: EventEntry,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val accentColor = remember(event.subjectColor) { eventColor(event.subjectColor) }
    val resolved    = accentColor ?: MaterialTheme.colorScheme.primary
    val completedAlpha by animateFloatAsState(
        targetValue   = if (event.isCompleted) 0.42f else 1f,
        animationSpec = tween(250),
        label         = "agenda_alpha"
    )
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick  = onToggle,
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        shape    = SquircleShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(resolved.copy(alpha = completedAlpha))
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Type icon box
                Box(
                    Modifier
                        .size(44.dp)
                        .background(resolved.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        eventTypeIcon(event.eventType), null,
                        tint     = resolved.copy(alpha = completedAlpha),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        event.title,
                        style          = MaterialTheme.typography.bodyLarge,
                        fontWeight     = FontWeight.SemiBold,
                        textDecoration = if (event.isCompleted) TextDecoration.LineThrough else null,
                        color          = MaterialTheme.colorScheme.onSurface.copy(alpha = completedAlpha)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Schedule, null,
                            modifier = Modifier.size(12.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        )
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp)),
                            style      = MaterialTheme.typography.labelMedium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f),
                            fontWeight = FontWeight.Medium
                        )
                        Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Surface(color = resolved.copy(alpha = 0.09f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                event.eventType,
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = resolved.copy(alpha = completedAlpha),
                                modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        if (event.remindersEnabled && !event.isCompleted) {
                            Icon(
                                Icons.Rounded.NotificationsActive, null,
                                modifier = Modifier.size(11.dp),
                                tint     = resolved.copy(0.5f)
                            )
                        }
                    }
                    if (!event.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            event.description,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }, Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.MoreVert, null,
                            modifier = Modifier.size(17.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f)
                        )
                    }
                    DropdownMenu(
                        expanded         = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape            = SmallExpressiveShape
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (event.isCompleted) "Mark Incomplete" else "Mark Complete") },
                            onClick = {
                                vibrationManager?.vibrateTick()
                                onToggle(); showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (event.isCompleted) Icons.Rounded.RadioButtonUnchecked
                                    else Icons.Rounded.CheckCircle,
                                    null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { onEdit(); showMenu = false },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                vibrationManager?.vibrateTick()
                                onDelete(); showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgendaTaskCard(task: TaskEntry) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        shape    = SquircleShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(TaskGreen)
            )
            Row(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(44.dp).background(TaskGreen.copy(0.1f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Checklist, null, tint = TaskGreen, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Surface(color = TaskGreen.copy(0.12f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "Task · ${task.category}",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = TaskGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

fun TaskIndicator(task: TaskEntry) = Unit // alias kept for compatibility

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 13 ─ Sync confirmation overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SyncConfirmationOverlay(
    results: List<SyncResult>,
    aiPreference: String,
    onPreferenceChange: (String) -> Unit,
    onResultUpdate: (Int, EventEntry) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = MediumExpressiveShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.AutoAwesome, null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "Review Schedule",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${results.size} ${if (results.size == 1) "event" else "events"} extracted by AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Reminder preference
            Surface(
                color  = MaterialTheme.colorScheme.surfaceContainerLow,
                shape  = SquircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.35f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsActive, null,
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Auto-Reminders",
                            fontWeight = FontWeight.ExtraBold,
                            style      = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ToolzConnectedButtonGroup(
                        selectedIndex = listOf("ALWAYS", "YES", "NOPE").indexOf(aiPreference).coerceAtLeast(0),
                        options       = listOf("Always", "Ask", "Never"),
                        onOptionSelected = { idx ->
                            onPreferenceChange(listOf("ALWAYS", "YES", "NOPE")[idx])
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding      = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(results) { index, result ->
                    val event = when (result) {
                        is SyncResult.New         -> result.event
                        is SyncResult.Reschedule  -> result.updated
                    }
                    var showEdit by remember { mutableStateOf(false) }
                    StaggeredEntrance(index = index) {
                        SyncResultCard(result, onClick = { showEdit = true })
                    }
                    if (showEdit) {
                        EditEventDialog(
                            event     = event,
                            onDismiss = { showEdit = false },
                            onConfirm = { updated -> onResultUpdate(index, updated); showEdit = false }
                        )
                    }
                }
            }

            // Bottom actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolzOutlinedExpressiveButton(
                    onClick  = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Discard", fontWeight = FontWeight.Bold) }

                ToolzExpressiveButton(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(2f).height(56.dp)
                ) {
                    Icon(Icons.Rounded.DoneAll, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync All", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun SyncResultCard(result: SyncResult, onClick: () -> Unit) {
    val event       = when (result) {
        is SyncResult.New        -> result.event
        is SyncResult.Reschedule -> result.updated
    }
    val color        = remember(event.subjectColor) { eventColor(event.subjectColor) } ?: MaterialTheme.colorScheme.primary
    val isReschedule = result is SyncResult.Reschedule
    val vibrationManager = LocalVibrationManager.current

    Surface(
        onClick = {
            vibrationManager?.vibrateTick()
            onClick()
        },
        color   = color.copy(alpha = 0.05f),
        shape   = SquircleShape,
        border  = BorderStroke(1.dp, color.copy(alpha = 0.14f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).background(color.copy(0.1f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isReschedule) Icons.Rounded.Update else Icons.Rounded.AddCircleOutline,
                    null, tint = color, modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()).format(Date(event.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isReschedule) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Rescheduled",
                            style    = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color    = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(17.dp), tint = color.copy(0.6f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 14 ─ Day detail bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DayDetailSheet(
    date: Long,
    events: List<EventEntry>,
    tasks: List<TaskEntry>,
    onDismiss: () -> Unit,
    onDeleteEvent: (EventEntry) -> Unit,
    onEditEvent: (EventEntry) -> Unit,
    onToggleEvent: (EventEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = MediumExpressiveShape,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(36.dp, 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.18f), CircleShape)
                )
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Bottom
            ) {
                Column {
                    Text(
                        SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(date)).uppercase(),
                        style         = MaterialTheme.typography.labelMedium,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(date)),
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                val total = events.size + tasks.size
                if (total > 0) {
                    Surface(
                        color  = MaterialTheme.colorScheme.primaryContainer,
                        shape  = SmallExpressiveShape
                    ) {
                        Text(
                            "$total ${if (total == 1) "item" else "items"}",
                            style    = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (events.isEmpty() && tasks.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Inbox, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Text("Nothing scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier            = Modifier.heightIn(max = 500.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        SmallEventItem(
                            event    = event,
                            onToggle = { onToggleEvent(event) },
                            onDelete = { onDeleteEvent(event) },
                            onEdit   = { onEditEvent(event) }
                        )
                    }
                    items(tasks, key = { it.id }) { task ->
                        AgendaTaskCard(task)
                    }
                }
            }
        }
    }
}

@Composable
fun SmallEventItem(
    event: EventEntry,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val color = remember(event.subjectColor) { eventColor(event.subjectColor) } ?: MaterialTheme.colorScheme.primary

    Surface(
        onClick  = onToggle,
        color    = color.copy(alpha = 0.06f),
        shape    = SmallExpressiveShape,
        border   = BorderStroke(1.dp, color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(10.dp)
                    .background(if (event.isCompleted) color.copy(0.3f) else color, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.title,
                    fontWeight     = FontWeight.SemiBold,
                    textDecoration = if (event.isCompleted) TextDecoration.LineThrough else null,
                    color          = if (event.isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${event.eventType} · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp))}",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row {
                IconButton(onClick = onEdit, Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                }
                IconButton(onClick = {
                    vibrationManager?.vibrateTick()
                    onDelete()
                }, Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error.copy(0.65f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 15 ─ AI Vision sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiVisionSheetContent(
    uiState: CalendarUiState,
    aiPrompt: String,
    onPromptChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onRemoveImage: () -> Unit,
    onConfigSelected: (AiConfig) -> Unit,
    onProcess: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    var modelExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MediumExpressiveShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.AutoAwesome, null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text("AI Scheduler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Snap a photo or describe your plan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Model selector pill
            Box {
                Surface(
                    onClick = { modelExpanded = true },
                    color   = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape   = SmallExpressiveShape
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            uiState.currentConfig.ifBlank { "Model" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(
                    expanded         = modelExpanded,
                    onDismissRequest = { modelExpanded = false },
                    modifier         = Modifier.widthIn(min = 220.dp),
                    shape            = SmallExpressiveShape
                ) {
                    uiState.availableConfigs.forEach { config ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(config.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${config.provider} · ${config.model}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onConfigSelected(config)
                                modelExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }

        // Attached image preview
        AnimatedVisibility(
            visible = uiState.attachedImage != null,
            enter   = expandVertically(spring(dampingRatio = 0.65f)) + fadeIn(),
            exit    = shrinkVertically(tween(200)) + fadeOut()
        ) {
            uiState.attachedImage?.let { bmp ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(SquircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape)
                ) {
                    Image(
                        bitmap             = bmp.asImageBitmap(),
                        contentDescription = "Attached photo",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                    // Remove button
                    FilledIconButton(
                        onClick  = onRemoveImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(0.55f),
                            contentColor   = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                    }
                    Surface(
                        Modifier.align(Alignment.BottomStart).padding(10.dp),
                        color = Color.Black.copy(0.5f),
                        shape = SmallExpressiveShape
                    ) {
                        Text(
                            "Photo attached",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Prompt field
        OutlinedTextField(
            value         = aiPrompt,
            onValueChange = onPromptChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = { Text("Describe your event or schedule…") },
            shape         = SquircleShape,
            leadingIcon   = {
                ToolzExpressiveIconButton(
                    onClick = onAttachClick,
                    colors  = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor   = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, "Attach photo")
                }
            },
            colors  = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            maxLines = 4,
            minLines = 2
        )

        // Tip banner
        Surface(
            color  = MaterialTheme.colorScheme.primaryContainer.copy(0.25f),
            shape  = SmallExpressiveShape
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Lightbulb, null,
                    modifier = Modifier.size(14.dp).padding(top = 1.dp),
                    tint     = MaterialTheme.colorScheme.primary
                )
                Text(
                    "AI understands relative dates like 'next Monday' or 'in 2 weeks'. Include a time for better accuracy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Process button
        ToolzExpressiveButton(
            onClick  = {
                vibrationManager?.vibrateClick()
                onProcess()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled  = aiPrompt.isNotBlank() || uiState.attachedImage != null
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Process Schedule", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 16 ─ Shared form helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text,
        style      = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier   = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    Surface(
        onClick = {
            vibrationManager?.vibrateTick()
            onClick()
        },
        color    = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        shape    = SmallExpressiveShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                value,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ColorPicker(selectedColor: String, onColorSelect: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        EventColorPalette.forEach { hex ->
            val color      = remember(hex) { runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.Gray } }
            val isSelected = selectedColor == hex
            val scale by animateFloatAsState(
                targetValue   = if (isSelected) 1.25f else 1f,
                animationSpec = spring(Spring.DampingRatioLowBouncy),
                label         = "colorDot_$hex"
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface.copy(0.25f), CircleShape)
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        vibrationManager?.vibrateTick()
                        onColorSelect(hex)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 17 ─ Add / Edit event dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AddEventDialog(
    initialDateMillis: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Long, String, String, Boolean) -> Unit,
) {
    var title            by rememberSaveable { mutableStateOf("") }
    var description      by rememberSaveable { mutableStateOf("") }
    var selectedType     by rememberSaveable { mutableStateOf("GENERAL") }
    var selectedColor    by rememberSaveable { mutableStateOf(EventColorPalette[5]) }
    var remindersEnabled by rememberSaveable { mutableStateOf(true) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    val timeState       = rememberTimePickerState()
    var showDatePicker  by remember { mutableStateOf(false) }
    var showTimePicker  by remember { mutableStateOf(false) }

    val dateLbl = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(it))
        } ?: "Select date"
    }
    val timeLbl = String.format(Locale.getDefault(), "%02d:%02d", timeState.hour, timeState.minute)
    val types   = listOf("GENERAL", "EXAM", "EVALUATION", "DEADLINE", "BIRTHDAY", "MEETING", "HOLIDAY")

    EventFormDialog(
        titleLabel     = "New Event",
        title          = title,
        onTitleChange  = { title = it },
        description    = description,
        onDescChange   = { description = it },
        dateLbl        = dateLbl,
        timeLbl        = timeLbl,
        onDateClick    = { showDatePicker = true },
        onTimeClick    = { showTimePicker = true },
        reminders      = remindersEnabled,
        onRemindersChange = { remindersEnabled = it },
        types          = types,
        selectedType   = selectedType,
        onTypeSelect   = { selectedType = it },
        selectedColor  = selectedColor,
        onColorSelect  = { selectedColor = it },
        confirmLabel   = "Save Event",
        canConfirm     = title.isNotBlank(),
        onDismiss      = onDismiss,
        onConfirm      = {
            val datePart = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
            val ts = Calendar.getInstance().apply {
                timeInMillis = datePart
                set(Calendar.HOUR_OF_DAY, timeState.hour)
                set(Calendar.MINUTE, timeState.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onConfirm(title, description.takeIf { it.isNotBlank() }, ts, selectedType, selectedColor, remindersEnabled)
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                ToolzExpressiveButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton    = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
    if (showTimePicker) {
        TimePickerDialog(onDismiss = { showTimePicker = false }, onConfirm = { showTimePicker = false }) {
            TimePicker(state = timeState)
        }
    }
}

@Composable
fun EditEventDialog(
    event: EventEntry,
    onDismiss: () -> Unit,
    onConfirm: (EventEntry) -> Unit,
) {
    var title            by rememberSaveable { mutableStateOf(event.title) }
    var description      by rememberSaveable { mutableStateOf(event.description ?: "") }
    var selectedType     by rememberSaveable { mutableStateOf(event.eventType) }
    var selectedColor    by rememberSaveable { mutableStateOf(event.subjectColor.ifBlank { EventColorPalette[5] }) }
    var remindersEnabled by rememberSaveable { mutableStateOf(event.remindersEnabled) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = event.timestamp)
    val calInit         = remember { Calendar.getInstance().apply { timeInMillis = event.timestamp } }
    val timeState       = rememberTimePickerState(calInit.get(Calendar.HOUR_OF_DAY), calInit.get(Calendar.MINUTE))
    var showDatePicker  by remember { mutableStateOf(false) }
    var showTimePicker  by remember { mutableStateOf(false) }

    val dateLbl = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(it))
        } ?: "Select date"
    }
    val timeLbl = String.format(Locale.getDefault(), "%02d:%02d", timeState.hour, timeState.minute)
    val types   = listOf("GENERAL", "EXAM", "EVALUATION", "DEADLINE", "BIRTHDAY", "MEETING", "HOLIDAY")

    EventFormDialog(
        titleLabel     = "Edit Event",
        title          = title,
        onTitleChange  = { title = it },
        description    = description,
        onDescChange   = { description = it },
        dateLbl        = dateLbl,
        timeLbl        = timeLbl,
        onDateClick    = { showDatePicker = true },
        onTimeClick    = { showTimePicker = true },
        reminders      = remindersEnabled,
        onRemindersChange = { remindersEnabled = it },
        types          = types,
        selectedType   = selectedType,
        onTypeSelect   = { selectedType = it },
        selectedColor  = selectedColor,
        onColorSelect  = { selectedColor = it },
        confirmLabel   = "Save Changes",
        canConfirm     = title.isNotBlank(),
        onDismiss      = onDismiss,
        onConfirm      = {
            val datePart = datePickerState.selectedDateMillis ?: event.timestamp
            val ts = Calendar.getInstance().apply {
                timeInMillis = datePart
                set(Calendar.HOUR_OF_DAY, timeState.hour)
                set(Calendar.MINUTE, timeState.minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onConfirm(event.copy(
                title          = title,
                description    = description.takeIf { it.isNotBlank() },
                eventType      = selectedType,
                subjectColor   = selectedColor,
                timestamp      = ts,
                remindersEnabled = remindersEnabled
            ))
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = { ToolzExpressiveButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton    = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
    if (showTimePicker) {
        TimePickerDialog(onDismiss = { showTimePicker = false }, onConfirm = { showTimePicker = false }) {
            TimePicker(state = timeState)
        }
    }
}

/** Shared form layout used by both Add and Edit dialogs. */
@Composable
private fun EventFormDialog(
    titleLabel: String,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescChange: (String) -> Unit,
    dateLbl: String,
    timeLbl: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    reminders: Boolean,
    onRemindersChange: (Boolean) -> Unit,
    types: List<String>,
    selectedType: String,
    onTypeSelect: (String) -> Unit,
    selectedColor: String,
    onColorSelect: (String) -> Unit,
    confirmLabel: String,
    canConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape          = SquircleShape,
            color          = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog title
                Text(
                    titleLabel,
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )

                // Title field
                OutlinedTextField(
                    value         = title,
                    onValueChange = onTitleChange,
                    label         = { Text("Event title") },
                    placeholder   = { Text("e.g. Math Exam") },
                    shape         = SmallExpressiveShape,
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Rounded.EditNote, null) }
                )

                // Description field
                OutlinedTextField(
                    value         = description,
                    onValueChange = onDescChange,
                    label         = { Text("Notes (optional)") },
                    shape         = SmallExpressiveShape,
                    modifier      = Modifier.fillMaxWidth(),
                    leadingIcon   = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
                    minLines      = 2,
                    maxLines      = 3
                )

                // Date & time pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            onDateClick()
                        },
                        color    = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        shape    = SmallExpressiveShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(Icons.Rounded.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateLbl, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Surface(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            onTimeClick()
                        },
                        color    = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        shape    = SmallExpressiveShape,
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(timeLbl, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Reminders toggle
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    shape    = SmallExpressiveShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Reminders", fontWeight = FontWeight.SemiBold)
                                Text("Smart notification before event", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Expressive switch from ExpressiveInputs
                        com.frerox.toolz.ui.components.ExpressiveSwitch(
                            checked = reminders,
                            onCheckedChange = { onRemindersChange(it) }
                        )
                    }
                }

                // Category row
                FormSectionLabel("Category")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { type ->
                        ExpressiveFilterChip(
                            selected  = selectedType == type,
                            onClick   = { onTypeSelect(type) },
                            label     = {
                                Text(type, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
                            },
                            shape = SmallExpressiveShape
                        )
                    }
                }

                // Color picker
                FormSectionLabel("Color")
                ColorPicker(selectedColor = selectedColor, onColorSelect = onColorSelect)

                // Actions
                Spacer(Modifier.height(4.dp))
                ToolzExpressiveButton(
                    onClick  = {
                        vibrationManager?.vibrateClick()
                        onConfirm()
                    },
                    enabled  = canConfirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 18 ─ Time picker wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape          = SquircleShape,
            color          = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolzOutlinedExpressiveButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    ToolzExpressiveButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 19 ─ Month picker dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MonthPickerDialog(
    currentDate: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int) -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val cal   = remember { Calendar.getInstance().apply { timeInMillis = currentDate } }
    var year  by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    val now   = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier       = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape          = SquircleShape,
            color          = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Jump to Date",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )

                // Year selector
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    shape    = SmallExpressiveShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        ExpressiveNavIconButton(Icons.Rounded.ChevronLeft, "Previous year") {
                            vibrationManager?.vibrateTick()
                            year--
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedContent(
                                targetState = year,
                                transitionSpec = {
                                    if (targetState > initialState)
                                        (slideInVertically { -it } + fadeIn()) togetherWith slideOutVertically { it } + fadeOut()
                                    else
                                        (slideInVertically { it } + fadeIn()) togetherWith slideOutVertically { -it } + fadeOut()
                                },
                                label = "yearAnim"
                            ) { y ->
                                Text(
                                    y.toString(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 22.sp,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (year == now) {
                                Text(
                                    "This year",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(0.65f)
                                )
                            }
                        }
                        ExpressiveNavIconButton(Icons.Rounded.ChevronRight, "Next year") {
                            vibrationManager?.vibrateTick()
                            year++
                        }
                    }
                }

                // Month grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(168.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    gridItemsIndexed(months) { index, m ->
                        ExpressiveFilterChip(
                            selected = month == index,
                            onClick  = {
                                vibrationManager?.vibrateTick()
                                month = index
                            },
                            label = {
                                Text(
                                    m,
                                    modifier   = Modifier.fillMaxWidth(),
                                    textAlign  = TextAlign.Center,
                                    fontWeight = if (month == index) FontWeight.ExtraBold else FontWeight.Normal
                                )
                            },
                            shape = SmallExpressiveShape
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolzOutlinedExpressiveButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) { Text("Cancel") }
                    ToolzExpressiveButton(
                        onClick  = { onDateSelected(year, month) },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) { Text("Go", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 20 ─ Utility
// ─────────────────────────────────────────────────────────────────────────────

private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
} catch (e: Exception) { null }

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 21 ─ Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "CalendarScreen — Light",
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "CalendarScreen — Dark",
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CalendarScreenPreview() {
    ToolzTheme {
        val now = System.currentTimeMillis()
        val sampleEvents = listOf(
            EventEntry(
                id = 1, title = "Math Exam", description = "Chapter 7–9",
                timestamp = now + 3_600_000L * 3, eventType = "EXAM",
                subjectColor = "#3F51B5", remindersEnabled = true, isCompleted = false
            ),
            EventEntry(
                id = 2, title = "Team Standup", description = null,
                timestamp = now + 3_600_000L * 9, eventType = "MEETING",
                subjectColor = "#009688", remindersEnabled = false, isCompleted = true
            ),
            EventEntry(
                id = 3, title = "Project Deadline", description = "Submit final report",
                timestamp = now + 3_600_000L * 17, eventType = "DEADLINE",
                subjectColor = "#F44336", remindersEnabled = true, isCompleted = false
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(64.dp))
                MonthGrid(
                    selectedDate   = now,
                    events         = sampleEvents,
                    tasks          = emptyList(),
                    onDateSelected = {},
                    onLongPress    = {}
                )
                DayPreviewPanel(
                    selectedDate  = now,
                    events        = sampleEvents,
                    tasks         = emptyList(),
                    onEventToggle = {},
                    onEventEdit   = {},
                    onEventDelete = {},
                    modifier      = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(name = "AgendaView — Light", showBackground = true)
@Preview(
    name = "AgendaView — Dark", showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun AgendaViewPreview() {
    ToolzTheme {
        val now = System.currentTimeMillis()
        val events = listOf(
            EventEntry(id = 1, title = "Math Exam", description = null, timestamp = now + 3_600_000, eventType = "EXAM", subjectColor = "#3F51B5", isCompleted = false, remindersEnabled = true),
            EventEntry(id = 2, title = "Birthday Party", description = "Bring cake!", timestamp = now + 86_400_000L * 2, eventType = "BIRTHDAY", subjectColor = "#E91E63", isCompleted = false, remindersEnabled = false),
            EventEntry(id = 3, title = "Dentist", description = null, timestamp = now + 86_400_000L * 5, eventType = "GENERAL", subjectColor = "#00BCD4", isCompleted = true, remindersEnabled = true)
        )
        AgendaView(
            events        = events,
            tasks         = emptyList(),
            onEventToggle = {},
            onDelete      = {},
            onEdit        = {}
        )
    }
}

@Preview(name = "Event Cards — Light", showBackground = true)
@Composable
private fun EventCardPreview() {
    ToolzTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val now = System.currentTimeMillis()
            listOf(
                EventEntry(id = 1, title = "Physics Exam", description = "Final exam covers all chapters", timestamp = now + 7_200_000, eventType = "EXAM", subjectColor = "#3F51B5", isCompleted = false, remindersEnabled = true),
                EventEntry(id = 2, title = "Birthday Party", description = "Bring cake!", timestamp = now + 3_600_000, eventType = "BIRTHDAY", subjectColor = "#E91E63", isCompleted = true, remindersEnabled = false),
                EventEntry(id = 3, title = "Deploy Deadline", description = null, timestamp = now - 3_600_000, eventType = "DEADLINE", subjectColor = "#F44336", isCompleted = false, remindersEnabled = true)
            ).forEach { event ->
                AgendaEventCard(event = event, onToggle = {}, onDelete = {}, onEdit = {})
            }
        }
    }
}

@Preview(name = "Month Picker Dialog", showBackground = true)
@Composable
private fun MonthPickerPreview() {
    ToolzTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            MonthPickerDialog(
                currentDate    = System.currentTimeMillis(),
                onDismiss      = {},
                onDateSelected = { _, _ -> }
            )
        }
    }
}