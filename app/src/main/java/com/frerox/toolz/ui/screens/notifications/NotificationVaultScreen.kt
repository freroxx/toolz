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

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.frerox.toolz.ui.screens.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─── Private Helpers ──────────────────────────────────────────────────────────

private val kAppAccentPalette = listOf(
    Color(0xFF6750A4), Color(0xFF984061), Color(0xFF006874),
    Color(0xFF6A6200), Color(0xFF1B6CA8), Color(0xFF006E2C),
    Color(0xFFAD3000), Color(0xFF7D5260), Color(0xFF00629B),
)

private fun String.toAppAccentColor(): Color =
    kAppAccentPalette[abs(hashCode()) % kAppAccentPalette.size]

private fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
    }
}

private fun Long.toFullDateTime(): String =
    SimpleDateFormat("EEEE, MMM d 'at' HH:mm", Locale.getDefault()).format(Date(this))

private fun NotificationEntry.dateGroup(): String {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    return when {
        timestamp >= now - day -> "Today"
        timestamp >= now - 2 * day -> "Yesterday"
        timestamp >= now - 7 * day -> "This Week"
        else -> "Older"
    }
}

private val kDateGroups = listOf("Today", "Yesterday", "This Week", "Older")
private val kDateFilterLabels = listOf("All", "Today", "Yest.", "7 Days")
private val kDateFilterValues = listOf("Anytime", "Today", "Yesterday", "Last 7 Days")

// ─── Main Screen ──────────────────────────────────────────────────────────────

@Composable
fun NotificationVaultScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationVaultViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedDateFilter by viewModel.selectedDateFilter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val hiddenApps by viewModel.hiddenApps.collectAsStateWithLifecycle()
    val appMappings by viewModel.appMappings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val scope = rememberCoroutineScope()
    
    val copiedMsg = stringResource(R.string.st_FileConverterScreen_ctc41)

    // Adaptive navigator
    val navigator = rememberSupportingPaneScaffoldNavigator()
    val isSupportingPaneVisible =
        navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded

    // UI state
    var selectedNotification by remember { mutableStateOf<NotificationEntry?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showHiddenAppsSheet by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf<NotificationEntry?>(null) }
    var showAppDetails by remember { mutableStateOf<AppDetails?>(null) }
    var isPermissionGranted by remember { mutableStateOf(true) }
    val shizukuAuthorized by viewModel.shizukuAuthorized.collectAsStateWithLifecycle()
    var showShizukuSetup by remember { mutableStateOf(false) }

    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showDetailSheet = selectedNotification != null && !isSupportingPaneVisible

    // Refresh Shizuku status on resume
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshShizukuStatus()
    }

    LaunchedEffect(Unit) {
        val listeners = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )
        isPermissionGranted = listeners != null && listeners.contains(context.packageName)
    }

    // When a notification is selected on an expanded layout, navigate to supporting pane
    LaunchedEffect(selectedNotification, isSupportingPaneVisible) {
        if (selectedNotification != null && isSupportingPaneVisible) {
            navigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
        }
    }

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        mainPane = {
            AnimatedPane {
                NotificationFeedPane(
                    notifications = notifications,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    selectedDateFilter = selectedDateFilter,
                    categories = categories,
                    selectedNotification = selectedNotification,
                    isPermissionGranted = isPermissionGranted,
                    showSearchBar = showSearchBar,
                    shizukuAuthorized = shizukuAuthorized,
                    onShizukuClick = {
                        if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()) {
                            // Already authorized, running permission grant is handled in ON_RESUME/refreshShizukuStatus
                            viewModel.refreshShizukuStatus()
                        } else if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAvailable()) {
                            com.frerox.toolz.util.shizuku.ShizukuHelper.requestPermission(1001)
                        } else {
                            showShizukuSetup = true
                        }
                    },
                    onToggleSearch = {
                        vibrationManager?.vibrateTick()
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) viewModel.setSearchQuery("")
                    },
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onCategorySelect = {
                        vibrationManager?.vibrateTick()
                        viewModel.setCategory(it)
                    },
                    onDateFilterSelect = {
                        vibrationManager?.vibrateTick()
                        viewModel.setDateFilter(it)
                    },
                    onNotificationClick = { n ->
                        vibrationManager?.vibrateTick()
                        selectedNotification = n
                        if (isSupportingPaneVisible) {
                            scope.launch { navigator.navigateTo(SupportingPaneScaffoldRole.Supporting) }
                        }
                    },
                    onNotificationLongClick = { n ->
                        vibrationManager?.vibrateLongClick()
                        showContextSheet = n
                    },
                    onDeleteNotification = { id ->
                        vibrationManager?.vibrateLongClick()
                        viewModel.deleteNotification(id)
                        if (selectedNotification?.id == id) selectedNotification = null
                    },
                    onNavigateBack = onNavigateBack,
                    onClearAll = { showClearAllDialog = true },
                    onExport = { showExportDialog = true },
                    onManageCategories = { showCategoryManager = true },
                    onManageHiddenApps = { showHiddenAppsSheet = true },
                    onPermissionClick = {
                        context.startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        )
                    },
                )
            }
        },
        supportingPane = {
            AnimatedPane {
                selectedNotification?.let { n ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        NotificationDetailContent(
                            notification = n,
                            categories = categories,
                            appMappings = appMappings,
                            isInPanel = true,
                            onDismiss = {
                                selectedNotification = null
                                scope.launch { navigator.navigateBack() }
                            },
                            onDelete = {
                                vibrationManager?.vibrateLongClick()
                                viewModel.deleteNotification(n.id)
                                selectedNotification = null
                                scope.launch { navigator.navigateBack() }
                            },
                            onHideApp = {
                                vibrationManager?.vibrateClick()
                                viewModel.hideApp(n.packageName)
                                selectedNotification = null
                                scope.launch { navigator.navigateBack() }
                            },
                            onMapCategory = { cat ->
                                vibrationManager?.vibrateTick()
                                viewModel.mapAppToCategory(n.packageName, cat)
                            },
                            onViewAppDetails = {
                                scope.launch { showAppDetails = viewModel.getAppDetails(n.packageName) }
                            },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Notification", "${n.title}\n${n.text}")
                                )
                                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                } ?: run {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.st_NotificationVaultScreen_san5),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        },
    )

    // ── Bottom sheet: detail on compact screens ────────────────────────────
    if (showDetailSheet) {
        selectedNotification?.let { n ->
            ModalBottomSheet(
                onDismissRequest = { selectedNotification = null },
                sheetState = detailSheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            ) {
                NotificationDetailContent(
                    notification = n,
                    categories = categories,
                    appMappings = appMappings,
                    isInPanel = false,
                    onDismiss = {
                        scope.launch {
                            detailSheetState.hide()
                            selectedNotification = null
                        }
                    },
                    onDelete = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.deleteNotification(n.id)
                        scope.launch {
                            detailSheetState.hide()
                            selectedNotification = null
                        }
                    },
                    onHideApp = {
                        vibrationManager?.vibrateClick()
                        viewModel.hideApp(n.packageName)
                        scope.launch {
                            detailSheetState.hide()
                            selectedNotification = null
                        }
                    },
                    onMapCategory = { cat ->
                        vibrationManager?.vibrateTick()
                        viewModel.mapAppToCategory(n.packageName, cat)
                    },
                    onViewAppDetails = {
                        scope.launch { showAppDetails = viewModel.getAppDetails(n.packageName) }
                    },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Notification", "${n.title}\n${n.text}")
                        )
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    // ── Context menu (long-press) ─────────────────────────────────────────
    showContextSheet?.let { n ->
        NotificationContextSheet(
            notification = n,
            onDismiss = { showContextSheet = null },
            onCopy = {
                showContextSheet = null
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Notification", "${n.title}\n${n.text}"))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onHideApp = {
                showContextSheet = null
                vibrationManager?.vibrateClick()
                viewModel.hideApp(n.packageName)
            },
            onDelete = {
                showContextSheet = null
                vibrationManager?.vibrateLongClick()
                viewModel.deleteNotification(n.id)
            },
            onViewAppDetails = {
                showContextSheet = null
                scope.launch { showAppDetails = viewModel.getAppDetails(n.packageName) }
            },
            onLaunchApp = {
                showContextSheet = null
                vibrationManager?.vibrateClick()
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(n.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    } else {
                        Toast.makeText(context, "App doesn't have a launcher activity", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to launch app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // ── App details panel ─────────────────────────────────────────────────
    showAppDetails?.let { details ->
        AppDetailsSheet(details = details, onDismiss = { showAppDetails = null })
    }

    // ── Clear-all dialog ──────────────────────────────────────────────────
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Clear All Logs", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This will permanently delete all ${notifications.size} captured notifications. " +
                            "This cannot be undone."
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.clearAll()
                        selectedNotification = null
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Clear All", fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = BouncyShape,
        )
    }

    // ── Export dialog ─────────────────────────────────────────────────────
    if (showExportDialog) {
        var exportFormat by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
            title = { Text("Export Logs", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Choose a format to export your ${notifications.size} captured notifications.")
                    ToolzConnectedButtonGroup(
                        selectedIndex = exportFormat,
                        options = listOf("JSON", "TXT"),
                        onOptionSelected = { exportFormat = it },
                    )
                }
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        viewModel.exportLogs(context, if (exportFormat == 0) "JSON" else "TXT")
                        showExportDialog = false
                    },
                ) { Text("Export") }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = BouncyShape,
        )
    }

    // ── Category manager ──────────────────────────────────────────────────
    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = categories,
            onDismiss = { showCategoryManager = false },
            onAddCategory = viewModel::addCategory,
            onRemoveCategory = viewModel::removeCategory,
        )
    }

    // ── Hidden apps sheet ─────────────────────────────────────────────────
    if (showHiddenAppsSheet) {
        HiddenAppsSheet(
            hiddenApps = hiddenApps,
            onDismiss = { showHiddenAppsSheet = false },
            onUnhide = { pkg ->
                vibrationManager?.vibrateTick()
                viewModel.unhideApp(pkg)
            },
        )
    }

    if (showShizukuSetup) {
        com.frerox.toolz.ui.components.ShizukuSetupBottomSheet(
            onDismiss = { showShizukuSetup = false }
        )
    }
}

// ─── Feed Pane ────────────────────────────────────────────────────────────────

@Composable
private fun NotificationFeedPane(
    notifications: List<NotificationEntry>,
    searchQuery: String,
    selectedCategory: String,
    selectedDateFilter: String,
    categories: List<String>,
    selectedNotification: NotificationEntry?,
    isPermissionGranted: Boolean,
    showSearchBar: Boolean,
    shizukuAuthorized: Boolean,
    onShizukuClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onDateFilterSelect: (String) -> Unit,
    onNotificationClick: (NotificationEntry) -> Unit,
    onNotificationLongClick: (NotificationEntry) -> Unit,
    onDeleteNotification: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onClearAll: () -> Unit,
    onExport: () -> Unit,
    onManageCategories: () -> Unit,
    onManageHiddenApps: () -> Unit,
    onPermissionClick: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val listState = rememberLazyListState()
    var collapsedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val selectedDateIndex = kDateFilterValues.indexOf(selectedDateFilter).coerceAtLeast(0)
    val groupedNotifications = remember(notifications) {
        notifications.groupBy { it.dateGroup() }
    }
    val totalApps = remember(notifications) {
        notifications.map { it.packageName }.distinct().size
    }
    val todayCount = remember(notifications) {
        notifications.count { it.timestamp >= System.currentTimeMillis() - 86_400_000L }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            Column {
                ExpressiveTopAppBar(
                    title = stringResource(R.string.st_NotificationVaultScreen_v1),
                    subtitle = if (notifications.isEmpty()) "No captures" else "${notifications.size} archived",
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_FileConverterScreen_b3))
                        }
                    },
                    actions = {
                        // Shizuku
                        IconButton(onClick = onShizukuClick) {
                            Icon(
                                Icons.Rounded.Memory,
                                contentDescription = "Shizuku",
                                tint = if (shizukuAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        // Animated search toggle
                        IconButton(onClick = onToggleSearch) {
                            AnimatedContent(
                                targetState = showSearchBar,
                                transitionSpec = {
                                    (scaleIn(spring(0.5f, Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                                            (scaleOut() + fadeOut())
                                },
                                label = "search_icon",
                            ) { searching ->
                                Icon(
                                    if (searching) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                                    contentDescription = if (searching) "Close search" else "Search",
                                )
                            }
                        }
                        // Export
                        IconButton(onClick = {
                            vibrationManager?.vibrateTick()
                            onExport()
                        }) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = "Export logs")
                        }
                        // Overflow menu
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = {
                                vibrationManager?.vibrateTick()
                                menuExpanded = true
                            }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                shape = MediumExpressiveShape,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Manage Categories") },
                                    leadingIcon = { Icon(Icons.Rounded.Category, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onManageCategories()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Hidden Apps") },
                                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onManageHiddenApps()
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Clear All",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteForever,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onClearAll()
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )

                // ── Animated search bar ────────────────────────────────
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { -it } +
                            fadeIn(tween(200)),
                    exit = slideOutVertically { -it } + fadeOut(tween(150)),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        placeholder = { Text(stringResource(R.string.st_NotificationVaultScreen_sn_hint2)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = SmallExpressiveShape,
                    )
                }

                // ── Date filter connected group ────────────────────────
                ToolzConnectedButtonGroup(
                    selectedIndex = selectedDateIndex,
                    options = kDateFilterLabels,
                    onOptionSelected = { idx ->
                        vibrationManager?.vibrateTick()
                        onDateFilterSelect(kDateFilterValues[idx])
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )

                // ── Category chips ─────────────────────────────────────
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalFadingEdges(left = 12.dp, right = 12.dp)
                        .padding(bottom = 4.dp),
                ) {
                    items(categories, key = { it }) { category ->
                        ExpressiveFilterChip(
                            selected = selectedCategory == category,
                            onClick = { onCategorySelect(category) },
                            label = { Text(category) },
                            leadingIcon = if (selectedCategory == category) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(14.dp)) }
                            } else null,
                        )
                    }
                }

                // ── Permission warning ─────────────────────────────────
                AnimatedVisibility(visible = !isPermissionGranted) {
                    Surface(
                        onClick = onPermissionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = MediumExpressiveShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.SecurityUpdateWarning,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.st_NotificationVaultScreen_ld3),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    stringResource(R.string.st_NotificationVaultScreen_ttgna4),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // ── Stats summary bar ──────────────────────────────────────────
            AnimatedVisibility(
                visible = notifications.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatPill(
                            icon = Icons.Rounded.Notifications,
                            value = notifications.size.toString(),
                            label = "Archived",
                        )
                        StatPill(
                            icon = Icons.Rounded.Today,
                            value = todayCount.toString(),
                            label = "Today",
                        )
                        StatPill(
                            icon = Icons.Rounded.Apps,
                            value = totalApps.toString(),
                            label = "Apps",
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        AnimatedContent(
            targetState = notifications.isEmpty(),
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300), 0.95f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(tween(200), 0.97f))
            },
            label = "feed_content",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) { isEmpty ->
            if (isEmpty) {
                EmptyVaultState(isFiltering = searchQuery.isNotEmpty() || selectedCategory != "All")
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .run {
                            if (!performanceMode) fadingEdges(top = 20.dp, bottom = 20.dp) else this
                        },
                ) {
                    kDateGroups.forEach { group ->
                        val groupItems = groupedNotifications[group] ?: return@forEach
                        if (groupItems.isEmpty()) return@forEach

                        // Sticky group header
                        stickyHeader(key = "header_$group") {
                            val isCollapsed = collapsedGroups.contains(group)
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = 2.dp,
                                onClick = {
                                    vibrationManager?.vibrateTick()
                                    collapsedGroups = if (isCollapsed) collapsedGroups - group else collapsedGroups + group
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        group,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Notifications,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                "${groupItems.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                    IconButton(
                                        onClick = {
                                            vibrationManager?.vibrateTick()
                                            collapsedGroups = if (isCollapsed) collapsedGroups - group else collapsedGroups + group
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (!collapsedGroups.contains(group)) {
                            itemsIndexed(groupItems, key = { _, item -> item.id }) { index, notification ->
                                SwipeToDismissNotification(
                                    notification = notification,
                                    isSelected = selectedNotification?.id == notification.id,
                                    onClick = { onNotificationClick(notification) },
                                    onLongClick = { onNotificationLongClick(notification) },
                                    onDismiss = { onDeleteNotification(notification.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Swipe-to-Dismiss ─────────────────────────────────────────────────────────

@Composable
private fun SwipeToDismissNotification(
    notification: NotificationEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.38f },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val progress = dismissState.progress
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                label = "swipe_color",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.25f else 0.75f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "swipe_icon_scale",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(SquircleShape)
                    .background(color),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(end = 28.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        },
    ) {
        NotificationCard(
            notification = notification,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

// ─── Notification Card ────────────────────────────────────────────────────────

@Composable
private fun NotificationCard(
    notification: NotificationEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val accentColor = notification.packageName.toAppAccentColor()

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_border",
    )

    ExpressiveCard(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = SquircleShape,
        containerColor = if (isSelected)
            MaterialTheme.colorScheme.surfaceContainerHighest
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) BorderStroke(2.dp, borderColor) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Accent left bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .align(Alignment.CenterVertically),
            )

            // App icon with coil
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.12f),
            ) {
                val iconPainter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(
                            try {
                                context.packageManager.getApplicationIcon(notification.packageName)
                            } catch (e: Exception) {
                                null
                            }
                        )
                        .crossfade(true)
                        .build()
                )
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notification.appName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = notification.timestamp.toRelativeTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }

                if (!notification.title.isNullOrBlank()) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!notification.text.isNullOrBlank()) {
                    Text(
                        text = notification.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Category badge
                notification.category
                    ?.takeIf { it.isNotBlank() && it != "General" }
                    ?.let { cat ->
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = cat,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = accentColor,
                            )
                        }
                    }
            }
        }
    }
}

// ─── Notification Detail ──────────────────────────────────────────────────────

@Composable
private fun NotificationDetailContent(
    notification: NotificationEntry,
    categories: List<String>,
    appMappings: Map<String, String>,
    isInPanel: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onHideApp: () -> Unit,
    onMapCategory: (String) -> Unit,
    onViewAppDetails: () -> Unit,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val accentColor = notification.packageName.toAppAccentColor()
    val currentMapping = appMappings[notification.packageName]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(if (isInPanel) 24.dp else 20.dp)
            .then(if (!isInPanel) Modifier.navigationBarsPadding() else Modifier),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        if (isInPanel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.st_NotificationVaultScreen_nd6),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_CatalogScreen_c3))
                }
            }
        }

        // ── App header ─────────────────────────────────────────────────────
        Surface(
            shape = LargeExpressiveShape,
            color = accentColor.copy(alpha = 0.08f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = accentColor.copy(alpha = 0.18f),
                ) {
                    val iconPainter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(
                                try {
                                    context.packageManager.getApplicationIcon(notification.packageName)
                                } catch (e: Exception) {
                                    null
                                }
                            )
                            .crossfade(true)
                            .build()
                    )
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        notification.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        notification.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // App details button
                FilledTonalIconButton(onClick = onViewAppDetails) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.st_NotepadScreen_sel_icon15))
                }
            }
        }

        // ── Notification body ──────────────────────────────────────────────
        Surface(
            shape = LargeExpressiveShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!notification.title.isNullOrBlank()) {
                    Text(
                        notification.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!notification.text.isNullOrBlank()) {
                    Text(
                        notification.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (notification.title.isNullOrBlank() && notification.text.isNullOrBlank()) {
                    Text(
                        "No content available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // ── Metadata ───────────────────────────────────────────────────────
        Surface(
            shape = LargeExpressiveShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetaRow(
                    icon = Icons.Rounded.AccessTime,
                    label = "Received",
                    value = notification.timestamp.toFullDateTime(),
                    accent = accentColor,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                MetaRow(
                    icon = Icons.Rounded.Category,
                    label = "Category",
                    value = notification.category ?: "General",
                    accent = accentColor,
                )
                currentMapping?.let {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    MetaRow(
                        icon = Icons.Rounded.Label,
                        label = "App mapped to",
                        value = it,
                        accent = accentColor,
                    )
                }
            }
        }

        // ── Remap to category ──────────────────────────────────────────────
        Text(
            stringResource(R.string.st_NotificationVaultScreen_matc7),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            categories.filter { it != "All" }.forEach { category ->
                val isMapped = currentMapping == category
                ExpressiveFilterChip(
                    selected = isMapped,
                    onClick = { onMapCategory(category) },
                    label = { Text(category) },
                    leadingIcon = if (isMapped) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(14.dp)) }
                    } else null,
                )
            }
        }

        // ── Action row ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolzOutlinedExpressiveButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_NotificationVaultScreen_c8))
            }
            ToolzOutlinedExpressiveButton(
                onClick = onHideApp,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_NotificationVaultScreen_ha9))
            }
        }
        ToolzExpressiveButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.st_NotificationVaultScreen_dn10), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, label: String, value: String, accent: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─── Context Bottom Sheet (long press) ────────────────────────────────────────

@Composable
private fun NotificationContextSheet(
    notification: NotificationEntry,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onHideApp: () -> Unit,
    onDelete: () -> Unit,
    onViewAppDetails: () -> Unit,
    onLaunchApp: () -> Unit,
) {
    val context = LocalContext.current
    val accentColor = notification.packageName.toAppAccentColor()
    val vibrationManager = LocalVibrationManager.current
    val sheetState = rememberModalBottomSheetState()

    val menuItems = listOf(
        Triple("Copy content", Icons.Rounded.ContentCopy, onCopy),
        Triple("View app stats", Icons.Rounded.BarChart, onViewAppDetails),
        Triple("Launch app", Icons.AutoMirrored.Rounded.Launch, onLaunchApp),
        Triple("Hide this app", Icons.Rounded.VisibilityOff, onHideApp),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.12f),
                ) {
                    val iconPainter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(
                                try {
                                    context.packageManager.getApplicationIcon(notification.packageName)
                                } catch (e: Exception) {
                                    null
                                }
                            )
                            .crossfade(true)
                            .build()
                    )
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column {
                    Text(
                        notification.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        notification.title ?: "Notification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // Menu items
            menuItems.forEachIndexed { idx, (label, icon, action) ->
                StaggeredEntrance(index = idx) {
                    Surface(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            action()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MediumExpressiveShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Text(
                                label,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Destructive delete
            Spacer(Modifier.height(4.dp))
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateLongClick()
                    onDelete()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete", fontWeight = FontWeight.Black)
            }
        }
    }
}

// ─── App Details Sheet ────────────────────────────────────────────────────────

@Composable
fun AppDetailsSheet(details: AppDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val accentColor = details.packageName.toAppAccentColor()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lastTime = details.lastNotification?.timestamp?.toFullDateTime() ?: "—"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App header
            Surface(
                shape = LargeExpressiveShape,
                color = accentColor.copy(alpha = 0.08f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = accentColor.copy(alpha = 0.15f),
                    ) {
                        val iconPainter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(context)
                                .data(
                                    try {
                                        context.packageManager.getApplicationIcon(details.packageName)
                                    } catch (e: Exception) {
                                        null
                                    }
                                )
                                .crossfade(true)
                                .build()
                        )
                        Image(
                            painter = iconPainter,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            details.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            details.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Total Archived",
                    value = "${details.totalNotifications}",
                    icon = Icons.Rounded.Notifications,
                    accent = accentColor,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Last Received",
                    value = lastTime.take(12),
                    icon = Icons.Rounded.AccessTime,
                    accent = accentColor,
                )
            }

            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
) {
    Surface(
        modifier = modifier,
        shape = MediumExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Category Manager Dialog ──────────────────────────────────────────────────

@Composable
private fun CategoryManagerDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    var newCategoryText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Category, contentDescription = null) },
        title = { Text("Manage Categories", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Add new category
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newCategoryText,
                        onValueChange = { newCategoryText = it },
                        placeholder = { Text("New category…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = SmallExpressiveShape,
                    )
                    ToolzExpressiveIconButton(
                        onClick = {
                            val trimmed = newCategoryText.trim()
                            if (trimmed.isNotBlank()) {
                                vibrationManager?.vibrateTick()
                                onAddCategory(trimmed)
                                newCategoryText = ""
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add category")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Existing categories
                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            category,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (category == "All" || category == "General") FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (category != "All" && category != "General") {
                            IconButton(
                                onClick = {
                                    vibrationManager?.vibrateTick()
                                    onRemoveCategory(category)
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(onClick = onDismiss) { Text("Done") }
        },
        shape = BouncyShape,
    )
}

// ─── Hidden Apps Sheet ────────────────────────────────────────────────────────

@Composable
private fun HiddenAppsSheet(
    hiddenApps: Set<String>,
    onDismiss: () -> Unit,
    onUnhide: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Hidden Apps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (hiddenApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Visibility,
                            null,
                            Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No hidden apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                hiddenApps.forEachIndexed { index, packageName ->
                    StaggeredEntrance(index = index) {
                        Surface(
                            shape = MediumExpressiveShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { onUnhide(packageName) }) {
                                    Text("Unhide", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyVaultState(isFiltering: Boolean) {
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "empty_state")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "empty_pulse",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        if (!performanceMode) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    },
                shape = ExtraLargeExpressiveShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                ),
            ) {
                Icon(
                    if (isFiltering) Icons.Rounded.SearchOff else Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier.padding(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = if (isFiltering) stringResource(R.string.st_NotepadScreen_nmf22) else stringResource(R.string.st_NotificationVaultScreen_vis11),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isFiltering)
                    stringResource(R.string.st_NotepadScreen_tadk25)
                else
                    stringResource(R.string.st_NotificationVaultScreen_inwbcaah12),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// ─── Stats Pill ───────────────────────────────────────────────────────────────

@Composable
private fun StatPill(icon: ImageVector, value: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private fun fakeSampleNotifications() = listOf(
    NotificationEntry(
        1L, "com.whatsapp", "WhatsApp",
        "Alice", "Are you coming to the meetup tonight?",
        System.currentTimeMillis() - 120_000L, "Social",
    ),
    NotificationEntry(
        2L, "com.google.android.gm", "Gmail",
        "Q3 Report Ready", "Your Q3 performance report has been generated and is ready for review.",
        System.currentTimeMillis() - 3_600_000L, "Work",
    ),
    NotificationEntry(
        3L, "com.spotify.music", "Spotify",
        "New Release Friday", "Check out new albums from your favourite artists.",
        System.currentTimeMillis() - 86_400_000L * 2, "General",
    ),
    NotificationEntry(
        4L, "com.bankapp", "My Bank",
        "Transaction Alert", "You spent \$42.00 at Coffee Co.",
        System.currentTimeMillis() - 86_400_000L * 4, "Finance",
    ),
)

@Preview(name = "Feed · Light", showBackground = true)
@Composable
private fun FeedLightPreview() {
    ToolzTheme {
        NotificationFeedPane(
            notifications = fakeSampleNotifications(),
            searchQuery = "",
            selectedCategory = "All",
            selectedDateFilter = "Anytime",
            categories = listOf("All", "Social", "Finance", "Work", "General"),
            selectedNotification = null,
            isPermissionGranted = true,
            showSearchBar = false,
            shizukuAuthorized = true,
            onShizukuClick = {},
            onToggleSearch = {},
            onSearchQueryChange = {},
            onCategorySelect = {},
            onDateFilterSelect = {},
            onNotificationClick = {},
            onNotificationLongClick = {},
            onDeleteNotification = {},
            onNavigateBack = {},
            onClearAll = {},
            onExport = {},
            onManageCategories = {},
            onManageHiddenApps = {},
            onPermissionClick = {},
        )
    }
}

@Preview(name = "Feed · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedDarkPreview() {
    ToolzTheme(darkTheme = true) {
        NotificationFeedPane(
            notifications = fakeSampleNotifications(),
            searchQuery = "",
            selectedCategory = "All",
            selectedDateFilter = "Anytime",
            categories = listOf("All", "Social", "Finance", "Work", "General"),
            selectedNotification = null,
            isPermissionGranted = false,
            showSearchBar = true,
            shizukuAuthorized = true,
            onShizukuClick = {},
            onToggleSearch = {},
            onSearchQueryChange = {},
            onCategorySelect = {},
            onDateFilterSelect = {},
            onNotificationClick = {},
            onNotificationLongClick = {},
            onDeleteNotification = {},
            onNavigateBack = {},
            onClearAll = {},
            onExport = {},
            onManageCategories = {},
            onManageHiddenApps = {},
            onPermissionClick = {},
        )
    }
}

@Preview(name = "Empty State · Light", showBackground = true)
@Composable
private fun EmptyLightPreview() {
    ToolzTheme { EmptyVaultState(isFiltering = false) }
}

@Preview(name = "Empty State · Searching", showBackground = true)
@Composable
private fun EmptyFilteringPreview() {
    ToolzTheme { EmptyVaultState(isFiltering = true) }
}

@Preview(name = "Notification Card · Light", showBackground = true)
@Composable
private fun CardLightPreview() {
    ToolzTheme {
        Box(Modifier.padding(16.dp)) {
            NotificationCard(
                notification = fakeSampleNotifications().first(),
                isSelected = false,
                onClick = {},
                onLongClick = {},
            )
        }
    }
}

@Preview(name = "Notification Card · Selected", showBackground = true)
@Composable
private fun CardSelectedPreview() {
    ToolzTheme {
        Box(Modifier.padding(16.dp)) {
            NotificationCard(
                notification = fakeSampleNotifications()[1],
                isSelected = true,
                onClick = {},
                onLongClick = {},
            )
        }
    }
}