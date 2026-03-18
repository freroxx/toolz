package com.frerox.toolz.ui.screens.calendar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.ai.AiConfig
import com.frerox.toolz.data.calendar.EventEntry
import com.frerox.toolz.data.calendar.SyncResult
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────
//  Design tokens
// ─────────────────────────────────────────────────────────────

private val RadiusSmall  = 8.dp
private val RadiusMedium = 16.dp
private val RadiusLarge  = 20.dp
private val RadiusXL     = 28.dp

val EventColorPalette = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#3F51B5",
    "#2196F3", "#00BCD4", "#009688", "#4CAF50",
    "#FF9800", "#FF5722", "#795548", "#607D8B"
)

// ─────────────────────────────────────────────────────────────
//  Domain helpers
// ─────────────────────────────────────────────────────────────

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
    return c1.get(Calendar.YEAR)       == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
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

// ─────────────────────────────────────────────────────────────
//  CalendarScreen — root
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract  = ActivityResultContracts.PickVisualMedia(),
        onResult  = { uri ->
            uri?.let { viewModel.setAttachedImage(uriToBitmap(context, it)) }
        }
    )

    val isTodaySelected = isSameDay(uiState.selectedDate, System.currentTimeMillis())

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

        // ── Subtle ambient gradient — skipped in performance mode ──────────
        if (!performanceMode) {
            val inf = rememberInfiniteTransition(label = "bg")
            val bgAlpha by inf.animateFloat(
                initialValue = 0.025f, targetValue = 0.07f,
                animationSpec = infiniteRepeatable(tween(14000), RepeatMode.Reverse), label = "a"
            )
            val mx by inf.animateFloat(
                initialValue = 0f, targetValue = 140f,
                animationSpec = infiniteRepeatable(tween(20000), RepeatMode.Reverse), label = "x"
            )
            val bgPrimary   = MaterialTheme.colorScheme.primary
            val bgSecondary = MaterialTheme.colorScheme.secondary
            Canvas(Modifier.fillMaxSize()) {
                val p = bgPrimary
                val s = bgSecondary
                drawCircle(
                    Brush.radialGradient(
                        listOf(p.copy(alpha = bgAlpha), Color.Transparent),
                        Offset(size.width * 0.9f + mx, size.height * 0.1f),
                        size.minDimension * 1.5f
                    )
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(s.copy(alpha = bgAlpha * 0.6f), Color.Transparent),
                        Offset(size.width * 0.1f - mx * 0.5f, size.height * 0.9f),
                        size.minDimension * 1.2f
                    )
                )
            }
        }

        Scaffold(
            snackbarHost     = { SnackbarHost(snackbarHostState) },
            containerColor   = Color.Transparent,
            topBar           = {
                CalendarTopBar(
                    selectedDate      = uiState.selectedDate,
                    isAcademicMode    = uiState.isAcademicMode,
                    isMonthView       = isMonthView,
                    isTodaySelected   = isTodaySelected,
                    onPrevious        = viewModel::previousMonth,
                    onNext            = viewModel::nextMonth,
                    onToday           = viewModel::goToToday,
                    onMonthPickerClick = { showMonthPicker = true },
                    onToggleView      = { isMonthView = !isMonthView }
                )
            },
            floatingActionButton = {
                CalendarFabs(
                    onAiClick  = { showAiSheet = true },
                    onAddClick = { showAddDialog = true }
                )
            }
        ) { paddingValues ->
            Box(Modifier.padding(paddingValues).fillMaxSize()) {
                AnimatedContent(
                    targetState  = isMonthView,
                    transitionSpec = {
                        val enter = fadeIn(tween(320)) +
                                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) {
                                    if (targetState) -it / 4 else it / 4
                                }
                        val exit = fadeOut(tween(320)) +
                                slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) {
                                    if (targetState) it / 4 else -it / 4
                                }
                        enter togetherWith exit
                    },
                    label = "main_view"
                ) { monthView ->
                    if (monthView) {
                        MonthViewSection(
                            uiState        = uiState,
                            onDateSelected = viewModel::onDateSelected,
                            onLongPress    = { longPressedDate = it },
                            onSwipeLeft    = viewModel::nextMonth,
                            onSwipeRight   = viewModel::previousMonth,
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
                            onEdit        = { editingEvent = it }
                        )
                    }
                }
            }
        }

        // ── Overlay stack ───────────────────────────────────────────────────
        if (showMonthPicker) {
            MonthPickerDialog(
                currentDate  = uiState.selectedDate,
                onDismiss    = { showMonthPicker = false },
                onDateSelected = { y, m -> viewModel.setDate(y, m); showMonthPicker = false }
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
                onConfirm = { updated -> viewModel.updateEvent(updated); editingEvent = null }
            )
        }

        longPressedDate?.let { date ->
            DayDetailSheet(
                date         = date,
                events       = uiState.events.filter { isSameDay(it.timestamp, date) },
                tasks        = uiState.tasks.filter { it.dueDate?.let { d -> isSameDay(d, date) } ?: false },
                onDismiss    = { longPressedDate = null },
                onDeleteEvent = viewModel::deleteEvent,
                onEditEvent  = { editingEvent = it; longPressedDate = null },
                onToggleEvent = viewModel::toggleEventCompletion
            )
        }

        AnimatedVisibility(
            visible = uiState.syncResults.isNotEmpty(),
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut()
        ) {
            SyncConfirmationOverlay(
                results           = uiState.syncResults,
                aiPreference      = uiState.aiReminderPreference,
                onPreferenceChange = viewModel::setAiReminderPreference,
                onResultUpdate    = viewModel::updateSyncResult,
                onConfirm         = viewModel::confirmSync,
                onCancel          = viewModel::cancelSync
            )
        }

        if (uiState.isLoading) LoadingOverlay()

        if (showAiSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAiSheet = false },
                containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation   = 0.dp,
                dragHandle       = {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
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
                    uiState         = uiState,
                    aiPrompt        = aiPrompt,
                    onPromptChange  = { aiPrompt = it },
                    onAttachClick   = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveImage   = { viewModel.setAttachedImage(null) },
                    onConfigSelected = viewModel::switchAiConfig,
                    onProcess       = {
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

// ─────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    onToggleView: () -> Unit
) {
    val monthYear = remember(selectedDate) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(selectedDate))
    }

    Surface(color = Color.Transparent) {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent
            ),
            title = {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusMedium))
                        .clickable(onClick = onMonthPickerClick)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Calendar",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            monthYear,
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.ArrowDropDown, null,
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.primary
                        )
                        if (isAcademicMode) {
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Rounded.School, null,
                                modifier = Modifier.size(12.dp),
                                tint     = MaterialTheme.colorScheme.primary
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
                    TopBarIconButton(Icons.Rounded.ChevronLeft, "Previous", onPrevious)
                    AnimatedVisibility(
                        visible = !isTodaySelected,
                        enter   = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                        exit    = fadeOut() + scaleOut()
                    ) {
                        IconButton(
                            onClick = onToday,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                Icons.Rounded.Today, "Today",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
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
                    TopBarIconButton(Icons.Rounded.ChevronRight, "Next", onNext)
                    IconButton(
                        onClick = onToggleView,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Crossfade(targetState = isMonthView, label = "view_icon") { month ->
                            Icon(
                                if (month) Icons.Rounded.ViewAgenda else Icons.Rounded.CalendarMonth,
                                "Toggle view",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun TopBarIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Icon(icon, desc, modifier = Modifier.size(18.dp))
    }
}

// ─────────────────────────────────────────────────────────────
//  FABs
// ─────────────────────────────────────────────────────────────

@Composable
private fun CalendarFabs(onAiClick: () -> Unit, onAddClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        FloatingActionButton(
            onClick = onAiClick,
            modifier = Modifier.size(48.dp),
            shape    = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor   = MaterialTheme.colorScheme.onTertiaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(2.dp, 4.dp)
        ) {
            Icon(Icons.Rounded.AutoAwesome, "AI Scheduler", modifier = Modifier.size(22.dp))
        }
        LargeFloatingActionButton(
            onClick = onAddClick,
            shape   = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(4.dp, 8.dp)
        ) {
            Icon(Icons.Rounded.Add, "Add event", modifier = Modifier.size(36.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Loading overlay
// ─────────────────────────────────────────────────────────────

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .pointerInput(Unit) { /* consume */ },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape  = RoundedCornerShape(RadiusXL),
            color  = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(40.dp)
            ) {
                val inf = rememberInfiniteTransition(label = "loader")
                val spin by inf.animateFloat(
                    0f, 360f,
                    infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin"
                )
                val pulse by inf.animateFloat(
                    0.9f, 1.1f,
                    infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulse"
                )
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier  = Modifier.fillMaxSize(),
                        strokeWidth = 2.dp,
                        color     = MaterialTheme.colorScheme.primaryContainer
                    )
                    Icon(
                        Icons.Rounded.AutoAwesome, null,
                        modifier = Modifier.size(32.dp).scale(pulse).graphicsLayer { rotationZ = spin },
                        tint     = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("Processing…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "AI is reading your schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Month view section — grid + persistent day panel
// ─────────────────────────────────────────────────────────────

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
                            totalDrag > 70f  -> onSwipeRight()
                            totalDrag < -70f -> onSwipeLeft()
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
        // ── Calendar grid ────────────────────────────────────────────────
        MonthGrid(
            selectedDate   = uiState.selectedDate,
            events         = uiState.events,
            tasks          = uiState.tasks,
            onDateSelected = onDateSelected,
            onLongPress    = onLongPress
        )

        // ── Day panel ────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────
//  Month grid — Column-of-Rows (proper height measurement)
// ─────────────────────────────────────────────────────────────

@Composable
private fun MonthGrid(
    selectedDate: Long,
    events: List<EventEntry>,
    tasks: List<TaskEntry>,
    onDateSelected: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
) {
    val tz   = TimeZone.getDefault()
    val cal  = remember(selectedDate) { Calendar.getInstance(tz).apply { timeInMillis = selectedDate } }
    val days = remember(selectedDate) { cal.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val mon  = cal.get(Calendar.MONTH)
    val yr   = cal.get(Calendar.YEAR)
    val firstDow = remember(selectedDate) {
        Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, yr); set(Calendar.MONTH, mon); set(Calendar.DAY_OF_MONTH, 1)
        }.get(Calendar.DAY_OF_WEEK) - 1
    }
    val numWeeks = (firstDow + days + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Day-of-week header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp)
        ) {
            val labels = listOf("S", "M", "T", "W", "T", "F", "S")
            labels.forEachIndexed { i, label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style       = MaterialTheme.typography.labelSmall,
                        fontWeight  = FontWeight.Bold,
                        color       = when (i) {
                            0, 6 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        },
                        textAlign   = TextAlign.Center
                    )
                }
            }
        }

        HorizontalDivider(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Week rows
        (0 until numWeeks).forEach { weekIndex ->
            Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                (0..6).forEach { dayIndex ->
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

// ─────────────────────────────────────────────────────────────
//  Day cell
// ─────────────────────────────────────────────────────────────

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
    val circleScale by animateFloatAsState(
        targetValue    = if (isSelected) 1f else 0f,
        animationSpec  = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label          = "circle"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isToday    -> MaterialTheme.colorScheme.primary
            else       -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
        },
        label = "text"
    )
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            // Today outline ring (only when not selected)
            if (isToday && !isSelected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.5.dp, primary, CircleShape)
                )
            }
            // Spring-animated selection fill
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
                fontSize   = 13.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Normal,
                color      = textColor
            )
        }

        Spacer(Modifier.height(2.dp))

        // Indicator dots row
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
                            if (isSelected) Color.White.copy(0.75f) else (dot ?: Color.Gray),
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
                            if (isSelected) Color.White.copy(0.55f) else Color(0xFF43A047),
                            RoundedCornerShape(2.dp)  // rect = task, circle = event
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

// ─────────────────────────────────────────────────────────────
//  Day preview panel — always visible, animates on day change
// ─────────────────────────────────────────────────────────────

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
        modifier  = modifier.fillMaxWidth(),
        color     = MaterialTheme.colorScheme.surfaceContainerLow,
        shape     = RoundedCornerShape(topStart = RadiusXL, topEnd = RadiusXL),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Drag handle ──────────────────────────────────────────────
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
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                            CircleShape
                        )
                )
            }

            // ── Animated header + list ───────────────────────────────────
            AnimatedContent(
                targetState = selectedDate,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                },
                modifier = Modifier.weight(1f),
                label    = "day_panel"
            ) { date ->
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                SimpleDateFormat("EEEE", Locale.getDefault())
                                    .format(Date(date))
                                    .uppercase(),
                                style       = MaterialTheme.typography.labelSmall,
                                fontWeight  = FontWeight.Black,
                                color       = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                                    .format(Date(date)),
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val total = events.size + tasks.size
                        if (total > 0) {
                            Surface(
                                color  = MaterialTheme.colorScheme.primaryContainer,
                                shape  = RoundedCornerShape(RadiusMedium)
                            ) {
                                Text(
                                    "$total ${if (total == 1) "item" else "items"}",
                                    style    = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                    )

                    // Content
                    if (events.isEmpty() && tasks.isEmpty()) {
                        PanelEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start  = 16.dp,
                                end    = 16.dp,
                                top    = 12.dp,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(events, key = { it.id }) { event ->
                                CompactEventItem(
                                    event     = event,
                                    onTap     = { onEventEdit(event) },
                                    onToggle  = { onEventToggle(event) },
                                    onDelete  = { onEventDelete(event) }
                                )
                            }
                            items(tasks, key = { it.id }) { task ->
                                CompactTaskItem(task)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Rounded.EventNote, null,
                modifier = Modifier.size(56.dp),
                tint     = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Nothing planned",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap + to add an event",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Compact event item (used in day preview panel)
// ─────────────────────────────────────────────────────────────

@Composable
private fun CompactEventItem(
    event: EventEntry,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val accentColor  = remember(event.subjectColor) { eventColor(event.subjectColor) }
    val resolved     = accentColor ?: MaterialTheme.colorScheme.primary
    val completedAlpha by animateFloatAsState(
        if (event.isCompleted) 0.42f else 1f, tween(250), label = "alpha"
    )
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onTap,
        color   = MaterialTheme.colorScheme.surfaceContainer,
        shape   = RoundedCornerShape(RadiusLarge),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = RadiusLarge, bottomStart = RadiusLarge))
                    .background(resolved.copy(alpha = completedAlpha))
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Icon
                Box(
                    Modifier
                        .size(34.dp)
                        .background(resolved.copy(alpha = 0.1f * completedAlpha), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        eventTypeIcon(event.eventType), null,
                        tint     = resolved.copy(alpha = completedAlpha),
                        modifier = Modifier.size(17.dp)
                    )
                }
                // Text
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
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Schedule, null,
                            modifier = Modifier.size(10.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        )
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp)),
                            style  = MaterialTheme.typography.labelSmall,
                            color  = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                        )
                        Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Surface(
                            color = resolved.copy(alpha = 0.08f * completedAlpha),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                event.eventType,
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = resolved.copy(alpha = completedAlpha),
                                modifier   = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                // Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (event.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint     = if (event.isCompleted) resolved else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }, Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.MoreVert, null,
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = { onTap(); showMenu = false },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { onDelete(); showMenu = false },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
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
    val green = Color(0xFF43A047)
    Surface(
        color  = MaterialTheme.colorScheme.surfaceContainer,
        shape  = RoundedCornerShape(RadiusLarge),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = RadiusLarge, bottomStart = RadiusLarge))
                    .background(green)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(34.dp).background(green.copy(0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Checklist, null, tint = green, modifier = Modifier.size(17.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Task", style = MaterialTheme.typography.labelSmall, color = green.copy(0.7f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Agenda view — full list with day groups
// ─────────────────────────────────────────────────────────────

@Composable
fun AgendaView(
    events: List<EventEntry>,
    tasks: List<TaskEntry>,
    onEventToggle: (EventEntry) -> Unit,
    onDelete: (EventEntry) -> Unit,
    onEdit: (EventEntry) -> Unit,
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.EventNote, null,
                    modifier = Modifier.size(80.dp),
                    tint     = MaterialTheme.colorScheme.outlineVariant.copy(0.4f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No scheduled events",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap + to add one, or use AI Scan",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        state = rememberLazyListState()
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
            }) { _, item ->
                Box(Modifier.padding(bottom = 8.dp)) {
                    when (item) {
                        is CalendarItem.Event -> AgendaEventCard(
                            event     = item.event,
                            onToggle  = { onEventToggle(item.event) },
                            onDelete  = { onDelete(item.event) },
                            onEdit    = { onEdit(item.event) }
                        )
                        is CalendarItem.Task -> AgendaTaskCard(item.task)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaDayHeader(dayMs: Long, isToday: Boolean, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isToday) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "TODAY",
                    style       = MaterialTheme.typography.labelSmall,
                    fontWeight  = FontWeight.Black,
                    color       = MaterialTheme.colorScheme.onPrimary,
                    letterSpacing = 1.sp,
                    modifier    = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Text(
                SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(dayMs)),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
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
            color     = MaterialTheme.colorScheme.outlineVariant.copy(0.4f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape
        ) {
            Text(
                count.toString(),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
    val accentColor = remember(event.subjectColor) { eventColor(event.subjectColor) }
    val resolved    = accentColor ?: MaterialTheme.colorScheme.primary
    val completedAlpha by animateFloatAsState(
        if (event.isCompleted) 0.42f else 1f, tween(250), label = "alpha"
    )
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick  = onToggle,
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        shape    = RoundedCornerShape(RadiusLarge),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = RadiusLarge, bottomStart = RadiusLarge))
                    .background(resolved.copy(alpha = completedAlpha))
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f)
                        )
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp)),
                            style  = MaterialTheme.typography.labelMedium,
                            color  = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f),
                            fontWeight = FontWeight.Medium
                        )
                        Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Surface(
                            color = resolved.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
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
                                tint     = resolved.copy(0.45f)
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
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (event.isCompleted) "Mark Incomplete" else "Mark Complete") },
                            onClick = { onToggle(); showMenu = false },
                            leadingIcon = {
                                Icon(if (event.isCompleted) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle, null)
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
                            onClick = { onDelete(); showMenu = false },
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
    val green = Color(0xFF43A047)
    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        shape    = RoundedCornerShape(RadiusLarge),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(4.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = RadiusLarge, bottomStart = RadiusLarge))
                    .background(green)
            )
            Row(
                Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(44.dp).background(green.copy(0.1f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Checklist, null, tint = green, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Task", style = MaterialTheme.typography.labelSmall, color = green.copy(0.7f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  TaskIndicator (kept for DayDetailSheet compatibility)
// ─────────────────────────────────────────────────────────────

@Composable
fun TaskIndicator(task: TaskEntry) = AgendaTaskCard(task)

// ─────────────────────────────────────────────────────────────
//  Sync confirmation overlay
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(44.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("Review Schedule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
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
                shape  = RoundedCornerShape(RadiusLarge),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Auto-Reminders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALWAYS", "YES", "NOPE").forEach { pref ->
                            FilterChip(
                                selected = aiPreference == pref,
                                onClick  = { onPreferenceChange(pref) },
                                label    = { Text(pref, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(RadiusSmall)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(results) { index, result ->
                    val event = when (result) { is SyncResult.New -> result.event; is SyncResult.Reschedule -> result.updated }
                    var showEdit by remember { mutableStateOf(false) }
                    SyncResultCard(result, onClick = { showEdit = true })
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
                Modifier.fillMaxWidth().padding(vertical = 16.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape    = RoundedCornerShape(RadiusMedium)
                ) { Text("Discard", fontWeight = FontWeight.Bold) }

                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(2f).height(56.dp),
                    shape    = RoundedCornerShape(RadiusMedium)
                ) {
                    Icon(Icons.Rounded.DoneAll, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync All", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SyncResultCard(result: SyncResult, onClick: () -> Unit) {
    val event = when (result) { is SyncResult.New -> result.event; is SyncResult.Reschedule -> result.updated }
    val color = remember(event.subjectColor) { eventColor(event.subjectColor) } ?: MaterialTheme.colorScheme.primary
    val isReschedule = result is SyncResult.Reschedule

    Surface(
        onClick = onClick,
        color   = color.copy(alpha = 0.05f),
        shape   = RoundedCornerShape(RadiusLarge),
        border  = BorderStroke(1.dp, color.copy(alpha = 0.14f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isReschedule) {
                    Spacer(Modifier.height(4.dp))
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "Rescheduled",
                            style    = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
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

// ─────────────────────────────────────────────────────────────
//  Day detail sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.18f), CircleShape))
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(date)).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(date)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                val total = events.size + tasks.size
                if (total > 0) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(RadiusMedium)) {
                        Text(
                            "$total ${if (total == 1) "item" else "items"}",
                            style    = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (events.isEmpty() && tasks.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Inbox, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 500.dp)
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
                        TaskIndicator(task)
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
    val color = remember(event.subjectColor) { eventColor(event.subjectColor) } ?: MaterialTheme.colorScheme.primary

    Surface(
        onClick  = onToggle,
        color    = color.copy(alpha = 0.06f),
        shape    = RoundedCornerShape(RadiusLarge),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (event.isCompleted) color.copy(0.3f) else color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.title,
                    fontWeight     = FontWeight.SemiBold,
                    textDecoration = if (event.isCompleted) TextDecoration.LineThrough else null,
                    color          = if (event.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${event.eventType} · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp))}",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row {
                IconButton(onClick = onEdit, Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                }
                IconButton(onClick = onDelete, Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error.copy(0.65f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  AI Vision sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    var modelExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        // Header row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("AI Scheduler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("Snap a photo or describe your plan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            // Model selector
            Box {
                Surface(
                    onClick = { modelExpanded = true },
                    color   = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape   = RoundedCornerShape(RadiusMedium)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(uiState.currentConfig.ifBlank { "Model" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }, Modifier.width(220.dp)) {
                    uiState.availableConfigs.forEach { config ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(config.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { onConfigSelected(config); modelExpanded = false },
                            leadingIcon = { Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Attached image
        if (uiState.attachedImage != null) {
            Box(
                Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(RadiusLarge))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(RadiusLarge))
            ) {
                androidx.compose.foundation.Image(
                    bitmap       = uiState.attachedImage.asImageBitmap(),
                    contentDescription = "Attached",
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick  = onRemoveImage,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp)
                        .background(Color.Black.copy(0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Surface(
                    Modifier.align(Alignment.BottomStart).padding(10.dp),
                    color  = Color.Black.copy(0.5f),
                    shape  = RoundedCornerShape(RadiusSmall)
                ) {
                    Text("Photo attached", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Prompt field
        OutlinedTextField(
            value       = aiPrompt,
            onValueChange = onPromptChange,
            modifier    = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Exam on Jan 20 at 9am or Deadline Friday 5pm") },
            shape       = RoundedCornerShape(RadiusLarge),
            leadingIcon = {
                IconButton(onClick = onAttachClick) {
                    Icon(Icons.Rounded.AddPhotoAlternate, "Attach", tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors  = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            maxLines = 4,
            minLines = 2
        )

        Spacer(Modifier.height(10.dp))

        // Tip row
        Surface(
            color  = MaterialTheme.colorScheme.primaryContainer.copy(0.25f),
            shape  = RoundedCornerShape(RadiusMedium)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(14.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "AI understands relative dates like 'next Monday' or 'in 2 weeks'. Include a time for more accuracy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick  = onProcess,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(RadiusMedium),
            enabled  = aiPrompt.isNotBlank() || uiState.attachedImage != null
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Process Schedule", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────
//  Shared dialog helpers
// ─────────────────────────────────────────────────────────────

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text,
        style       = MaterialTheme.typography.labelLarge,
        fontWeight  = FontWeight.Bold,
        color       = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier    = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape   = RoundedCornerShape(RadiusMedium),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
            }
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ColorPicker(selectedColor: String, onColorSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        EventColorPalette.forEach { hex ->
            val color = remember(hex) { runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.Gray } }
            val isSelected = selectedColor == hex
            Box(
                modifier = Modifier
                    .size(if (isSelected) 34.dp else 28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface.copy(0.25f), CircleShape)
                        else Modifier
                    )
                    .clickable { onColorSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Add event dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDateMillis: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Long, String, String, Boolean) -> Unit,
) {
    var title            by remember { mutableStateOf("") }
    var description      by remember { mutableStateOf("") }
    var selectedType     by remember { mutableStateOf("GENERAL") }
    var selectedColor    by remember { mutableStateOf(EventColorPalette[5]) }
    var remindersEnabled by remember { mutableStateOf(true) }

    val datePickerState  = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    val timeState        = rememberTimePickerState()
    var showDatePicker   by remember { mutableStateOf(false) }
    var showTimePicker   by remember { mutableStateOf(false) }

    val dateLbl = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(it))
        } ?: "Select date"
    }
    val timeLbl = String.format(Locale.getDefault(), "%02d:%02d", timeState.hour, timeState.minute)
    val types   = listOf("GENERAL", "EXAM", "EVALUATION", "DEADLINE", "BIRTHDAY", "MEETING", "HOLIDAY")

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier   = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape      = RoundedCornerShape(RadiusXL),
        title = {
            Text("New Event", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                OutlinedTextField(
                    value       = title,
                    onValueChange = { title = it },
                    label       = { Text("Event title") },
                    placeholder = { Text("e.g. Math Exam") },
                    shape       = RoundedCornerShape(RadiusMedium),
                    modifier    = Modifier.fillMaxWidth(),
                    singleLine  = true,
                    leadingIcon = { Icon(Icons.Rounded.EditNote, null) }
                )
                // Description
                OutlinedTextField(
                    value       = description,
                    onValueChange = { description = it },
                    label       = { Text("Notes (optional)") },
                    shape       = RoundedCornerShape(RadiusMedium),
                    modifier    = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
                    minLines    = 2,
                    maxLines    = 3
                )
                // Date
                PickerRow(Icons.Rounded.CalendarToday, "Date", dateLbl) { showDatePicker = true }
                // Time
                PickerRow(Icons.Rounded.Schedule, "Time", timeLbl) { showTimePicker = true }
                // Reminders
                Surface(
                    color  = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape  = RoundedCornerShape(RadiusMedium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Reminders", fontWeight = FontWeight.SemiBold)
                                Text("Smart notification before event", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(checked = remindersEnabled, onCheckedChange = { remindersEnabled = it })
                    }
                }
                // Category
                FormSectionLabel("Category")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { type ->
                        FilterChip(
                            selected  = selectedType == type,
                            onClick   = { selectedType = type },
                            label     = { Text(type, fontWeight = FontWeight.Medium) },
                            shape     = RoundedCornerShape(RadiusSmall)
                        )
                    }
                }
                // Color
                FormSectionLabel("Color")
                ColorPicker(selectedColor) { selectedColor = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val datePart = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val ts = Calendar.getInstance().apply {
                        timeInMillis = datePart
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onConfirm(title, description.takeIf { it.isNotBlank() }, ts, selectedType, selectedColor, remindersEnabled)
                },
                enabled  = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(RadiusMedium)
            ) {
                Text("Save Event", fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = { Button(onClick = { showDatePicker = false }, shape = RoundedCornerShape(RadiusMedium)) { Text("OK") } },
            dismissButton    = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
    if (showTimePicker) {
        TimePickerDialog(onDismiss = { showTimePicker = false }, onConfirm = { showTimePicker = false }) {
            TimePicker(state = timeState)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Edit event dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventDialog(
    event: EventEntry,
    onDismiss: () -> Unit,
    onConfirm: (EventEntry) -> Unit,
) {
    var title            by remember { mutableStateOf(event.title) }
    var description      by remember { mutableStateOf(event.description ?: "") }
    var selectedType     by remember { mutableStateOf(event.eventType) }
    var selectedColor    by remember { mutableStateOf(event.subjectColor.ifBlank { EventColorPalette[5] }) }
    var remindersEnabled by remember { mutableStateOf(event.remindersEnabled) }

    val datePickerState  = rememberDatePickerState(initialSelectedDateMillis = event.timestamp)
    val calInit          = remember { Calendar.getInstance().apply { timeInMillis = event.timestamp } }
    val timeState        = rememberTimePickerState(calInit.get(Calendar.HOUR_OF_DAY), calInit.get(Calendar.MINUTE))
    var showDatePicker   by remember { mutableStateOf(false) }
    var showTimePicker   by remember { mutableStateOf(false) }

    val dateLbl = remember(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(it))
        } ?: "Select date"
    }
    val timeLbl = String.format(Locale.getDefault(), "%02d:%02d", timeState.hour, timeState.minute)
    val types   = listOf("GENERAL", "EXAM", "EVALUATION", "DEADLINE", "BIRTHDAY", "MEETING", "HOLIDAY")

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier   = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape      = RoundedCornerShape(RadiusXL),
        title = { Text("Edit Event", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, shape = RoundedCornerShape(RadiusMedium), singleLine = true)
                OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes") }, shape = RoundedCornerShape(RadiusMedium), minLines = 2, maxLines = 3)
                PickerRow(Icons.Rounded.CalendarToday, "Date", dateLbl) { showDatePicker = true }
                PickerRow(Icons.Rounded.Schedule, "Time", timeLbl) { showTimePicker = true }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(RadiusMedium), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Reminders", fontWeight = FontWeight.SemiBold)
                        }
                        Switch(checked = remindersEnabled, onCheckedChange = { remindersEnabled = it })
                    }
                }
                FormSectionLabel("Category")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { type ->
                        FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type, fontWeight = FontWeight.Medium) }, shape = RoundedCornerShape(RadiusSmall))
                    }
                }
                FormSectionLabel("Color")
                ColorPicker(selectedColor) { selectedColor = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val datePart = datePickerState.selectedDateMillis ?: event.timestamp
                    val ts = Calendar.getInstance().apply {
                        timeInMillis = datePart
                        set(Calendar.HOUR_OF_DAY, timeState.hour); set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onConfirm(event.copy(title = title, description = description.takeIf { it.isNotBlank() }, eventType = selectedType, subjectColor = selectedColor, timestamp = ts, remindersEnabled = remindersEnabled))
                },
                enabled  = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(RadiusMedium)
            ) { Text("Save Changes", fontWeight = FontWeight.Black, fontSize = 15.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = { Button(onClick = { showDatePicker = false }, shape = RoundedCornerShape(RadiusMedium)) { Text("OK") } },
            dismissButton    = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
    if (showTimePicker) {
        TimePickerDialog(onDismiss = { showTimePicker = false }, onConfirm = { showTimePicker = false }) {
            TimePicker(state = timeState)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Time picker wrapper dialog
// ─────────────────────────────────────────────────────────────

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(RadiusXL),
        confirmButton    = { Button(onClick = onConfirm, shape = RoundedCornerShape(RadiusMedium)) { Text("Apply") } },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text             = { content() }
    )
}

// ─────────────────────────────────────────────────────────────
//  Month picker dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerDialog(
    currentDate: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int) -> Unit,
) {
    val cal   = remember { Calendar.getInstance().apply { timeInMillis = currentDate } }
    var year  by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    val now   = remember { Calendar.getInstance().get(Calendar.YEAR) }

    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(RadiusXL),
        title = { Text("Jump to Date", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Year selector
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape    = RoundedCornerShape(RadiusLarge),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { year-- }) { Icon(Icons.Rounded.ChevronLeft, "Prev") }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(year.toString(), fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                            if (year == now) {
                                Text("This year", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(0.65f))
                            }
                        }
                        IconButton(onClick = { year++ }) { Icon(Icons.Rounded.ChevronRight, "Next") }
                    }
                }
                // Month grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(168.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    gridItemsIndexed(months) { index: Int, m: String ->
                        FilterChip(
                            selected = month == index,
                            onClick  = { month = index },
                            label    = {
                                Text(
                                    m,
                                    modifier   = Modifier.fillMaxWidth(),
                                    textAlign  = TextAlign.Center,
                                    fontWeight = if (month == index) FontWeight.Black else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(RadiusSmall)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { onDateSelected(year, month) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(RadiusMedium)
            ) { Text("Go", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────
//  Utility
// ─────────────────────────────────────────────────────────────

private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
} catch (e: Exception) { null }