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

package com.frerox.toolz.ui.screens.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─── Type Metadata ────────────────────────────────────────────────────────────

private data class TypeMeta(
    val icon: ImageVector,
    val color: Color,
    val label: String,
)

private val kTypeMeta: Map<String, TypeMeta> @Composable get() = mapOf(
    "URL"      to TypeMeta(Icons.Rounded.Language,           Color(0xFF1A73E8), stringResource(R.string.st_ClipboardScreen_8f1a)),
    "SOCIAL"   to TypeMeta(Icons.Rounded.Public,             Color(0xFF0D47A1), stringResource(R.string.st_ClipboardScreen_3d5b)),
    "PHONE"    to TypeMeta(Icons.Rounded.Call,               Color(0xFF34A853), stringResource(R.string.st_ClipboardScreen_9e2c)),
    "OTP"      to TypeMeta(Icons.Rounded.Lock,               Color(0xFFFFA000), stringResource(R.string.st_ClipboardScreen_1a2b)),
    "EMAIL"    to TypeMeta(Icons.Rounded.Mail,               Color(0xFFEA4335), stringResource(R.string.st_ClipboardScreen_7c4d)),
    "MATHS"    to TypeMeta(Icons.Rounded.Functions,          Color(0xFF9C27B0), stringResource(R.string.st_ClipboardScreen_5f6e)),
    "PERSONAL" to TypeMeta(Icons.Rounded.AutoAwesome,        Color(0xFFE91E63), stringResource(R.string.st_ClipboardScreen_2b8a)),
    "CODE"     to TypeMeta(Icons.Rounded.Terminal,           Color(0xFF00BCD4), stringResource(R.string.st_ClipboardScreen_4d9c)),
    "ADDRESS"  to TypeMeta(Icons.Rounded.Place,              Color(0xFFFF5722), stringResource(R.string.st_ClipboardScreen_6a1b)),
    "CRYPTO"   to TypeMeta(Icons.Rounded.CurrencyBitcoin,    Color(0xFFF7931A), stringResource(R.string.st_ClipboardScreen_1b2c)),
    "TODO"     to TypeMeta(Icons.Rounded.AssignmentTurnedIn, Color(0xFF4CAF50), stringResource(R.string.st_ClipboardScreen_3c4d)),
    "RECIPE"   to TypeMeta(Icons.Rounded.Restaurant,         Color(0xFFFF9800), stringResource(R.string.st_ClipboardScreen_5d6e)),
    "FLIGHT"   to TypeMeta(Icons.Rounded.Flight,             Color(0xFF2196F3), stringResource(R.string.st_ClipboardScreen_7e8f)),
    "EVENT"    to TypeMeta(Icons.Rounded.Event,              Color(0xFF9C27B0), stringResource(R.string.st_ClipboardScreen_9f0a)),
    "QUOTE"    to TypeMeta(Icons.Rounded.FormatQuote,        Color(0xFF607D8B), stringResource(R.string.st_ClipboardScreen_a1b2)),
    "TEXT"     to TypeMeta(Icons.Rounded.Notes,              Color(0xFF78909C), stringResource(R.string.st_ClipboardScreen_c3d4)),
)

@Composable
private fun typeMeta(type: String): TypeMeta =
    kTypeMeta[type] ?: TypeMeta(Icons.Rounded.ContentPaste, MaterialTheme.colorScheme.primary, type)

private fun Long.toRelativeTime(context: android.content.Context): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60_000L      -> context.getString(R.string.st_ClipboardScreen_e5f6)
        diff < 3_600_000L   -> "${diff / 60_000}${context.getString(R.string.st_ClipboardScreen_g7h8)}"
        diff < 86_400_000L  -> "${diff / 3_600_000}${context.getString(R.string.st_ClipboardScreen_i9j0)}"
        diff < 604_800_000L -> "${diff / 86_400_000}${context.getString(R.string.st_ClipboardScreen_k1l2)}"
        else                -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
    }
}

private fun Long.toFullTimestamp(): String =
    SimpleDateFormat("EEE, MMM d 'at' HH:mm", Locale.getDefault()).format(Date(this))

// ─── Root Screen ─────────────────────────────────────────────────────────────

@Composable
fun ClipboardScreen(
    viewModel: ClipboardViewModel,
    onBack: () -> Unit,
    onConvertToTask: (String) -> Unit = {},
) {
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val entries         by viewModel.entries.collectAsStateWithLifecycle()
    val isSummarizingId by viewModel.isSummarizing.collectAsStateWithLifecycle()
    val isAiSearching   by viewModel.isAiSearching.collectAsStateWithLifecycle()
    val searchQuery     by viewModel.searchQuery.collectAsStateWithLifecycle()
    val offlineMode     by viewModel.offlineModeEnabled.collectAsStateWithLifecycle()
    val shizukuAuthorized by viewModel.shizukuAuthorized.collectAsStateWithLifecycle()

    val context         = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val scope           = rememberCoroutineScope()

    val groups = remember(filteredEntries) { viewModel.groupedEntries(filteredEntries) }

    // Adaptive pane navigator
    val navigator = rememberSupportingPaneScaffoldNavigator()
    val isSupportingPaneVisible =
        navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded

    // UI state
    var selectedEntry by remember { mutableStateOf<ClipboardEntry?>(null) }
    var activeTypeFilter by remember { mutableStateOf<String?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf<ClipboardEntry?>(null) }
    var showShizukuSetup by remember { mutableStateOf(false) }

    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showDetailSheet = selectedEntry != null && !isSupportingPaneVisible

    // Refresh on resume
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshClipboard()
    }

    // Navigate to supporting pane when entry selected on wide layout
    LaunchedEffect(selectedEntry, isSupportingPaneVisible) {
        if (selectedEntry != null && isSupportingPaneVisible) {
            navigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
        }
    }

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    // Filtered list accounting for type filter
    val displayGroups = remember(groups, activeTypeFilter) {
        if (activeTypeFilter == null) groups
        else groups.map { g ->
            g.copy(entries = g.entries.filter { it.type == activeTypeFilter })
        }.filter { it.entries.isNotEmpty() }
    }

    val distinctTypes = remember(entries) {
        entries.map { it.type }.distinct().sorted()
    }

    // Helpers: copy action
    fun copyToClipboard(entry: ClipboardEntry) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Toolz Clip", entry.content))
        vibrationManager?.vibrateClick()
        Toast.makeText(context, context.getString(R.string.st_ClipboardScreen_m3n4), Toast.LENGTH_SHORT).show()
    }

    SupportingPaneScaffold(
        directive  = navigator.scaffoldDirective,
        value      = navigator.scaffoldValue,
        mainPane   = {
            AnimatedPane {
                ClipboardFeedPane(
                    groups               = displayGroups,
                    allEntries           = entries,
                    distinctTypes        = distinctTypes,
                    activeTypeFilter     = activeTypeFilter,
                    isSummarizingId      = isSummarizingId,
                    isAiSearching        = isAiSearching,
                    searchQuery          = searchQuery,
                    offlineMode          = offlineMode,
                    selectedEntry        = selectedEntry,
                    showSearchBar        = showSearchBar,
                    shizukuAuthorized    = shizukuAuthorized,
                    onBack               = onBack,
                    onToggleSearch       = {
                        vibrationManager?.vibrateTick()
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) viewModel.onSearchQueryChanged("")
                    },
                    onSearchQueryChange  = viewModel::onSearchQueryChanged,
                    onTypeFilterChange   = { type ->
                        vibrationManager?.vibrateTick()
                        activeTypeFilter = if (type == "ALL" || activeTypeFilter == type) null else type
                    },
                    onEntryClick         = { entry ->
                        vibrationManager?.vibrateTick()
                        selectedEntry = entry
                        if (isSupportingPaneVisible) {
                            scope.launch { navigator.navigateTo(SupportingPaneScaffoldRole.Supporting) }
                        }
                    },
                    onEntryLongClick     = { entry ->
                        vibrationManager?.vibrateLongClick()
                        showContextSheet = entry
                    },
                    onCopy               = ::copyToClipboard,
                    onDelete             = { viewModel.deleteEntry(it) },
                    onPin                = { viewModel.togglePin(it.id) },
                    onSummarize          = { viewModel.summarizeEntry(it) },
                    onConvertToTask      = onConvertToTask,
                    onContextualAction   = { action, entry ->
                        if (action == "convert_to_task") onConvertToTask(entry.content)
                        else handleContextualAction(context, action, entry)
                    },
                    onClearAll           = { showClearDialog = true },
                    onRefresh            = { viewModel.categorizeAllWithAi() },
                    onShizukuClick       = {
                        if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()) {
                            // OK
                        } else if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAvailable()) {
                            com.frerox.toolz.util.shizuku.ShizukuHelper.requestPermission(1001)
                        } else {
                            showShizukuSetup = true
                        }
                    }
                )
            }
        },
        supportingPane = {
            AnimatedPane {
                selectedEntry?.let { entry ->
                    Surface(
                        modifier  = Modifier.fillMaxSize(),
                        color     = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ClipboardDetailContent(
                            entry         = entry,
                            isSummarizing = isSummarizingId == entry.id,
                            offlineMode   = offlineMode,
                            isInPanel     = true,
                            onDismiss     = {
                                selectedEntry = null
                                scope.launch { navigator.navigateBack() }
                            },
                            onCopy        = { copyToClipboard(entry) },
                            onDelete      = {
                                viewModel.deleteEntry(entry)
                                selectedEntry = null
                                scope.launch { navigator.navigateBack() }
                            },
                            onPin         = { viewModel.togglePin(entry.id) },
                            onSummarize   = { viewModel.summarizeEntry(entry) },
                            onAction      = { action ->
                                if (action == "convert_to_task") onConvertToTask(entry.content)
                                else handleContextualAction(context, action, entry)
                            },
                        )
                    }
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.ContentPaste, null,
                            Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Select an entry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        },
    )

    // ── Detail sheet (compact) ─────────────────────────────────────────────
    if (showDetailSheet) {
        selectedEntry?.let { entry ->
            ModalBottomSheet(
                onDismissRequest = { selectedEntry = null },
                sheetState       = detailSheetState,
                containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
                shape            = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            ) {
                ClipboardDetailContent(
                    entry         = entry,
                    isSummarizing = isSummarizingId == entry.id,
                    offlineMode   = offlineMode,
                    isInPanel     = false,
                    onDismiss     = {
                        scope.launch {
                            detailSheetState.hide()
                            selectedEntry = null
                        }
                    },
                    onCopy    = { copyToClipboard(entry) },
                    onDelete  = {
                        viewModel.deleteEntry(entry)
                        scope.launch {
                            detailSheetState.hide()
                            selectedEntry = null
                        }
                    },
                    onPin       = { viewModel.togglePin(entry.id) },
                    onSummarize = { viewModel.summarizeEntry(entry) },
                    onAction    = { action ->
                        if (action == "convert_to_task") onConvertToTask(entry.content)
                        else handleContextualAction(context, action, entry)
                    },
                )
            }
        }
    }

    // ── Context menu (long-press) ─────────────────────────────────────────
    showContextSheet?.let { entry ->
        ClipboardContextSheet(
            entry       = entry,
            isSummarizing = isSummarizingId == entry.id,
            offlineMode = offlineMode,
            onDismiss   = { showContextSheet = null },
            onCopy      = { showContextSheet = null; copyToClipboard(entry) },
            onPin       = { showContextSheet = null; viewModel.togglePin(entry.id) },
            onDelete    = { showContextSheet = null; viewModel.deleteEntry(entry) },
            onSummarize = { showContextSheet = null; viewModel.summarizeEntry(entry) },
            onAction    = { action ->
                showContextSheet = null
                if (action == "convert_to_task") onConvertToTask(entry.content)
                else handleContextualAction(context, action, entry)
            },
        )
    }

    if (showShizukuSetup) {
        com.frerox.toolz.ui.components.ShizukuSetupBottomSheet(
            onDismiss = { showShizukuSetup = false }
        )
    }

    // ── Clear all dialog ──────────────────────────────────────────────────
    if (showClearDialog) {
        val pinnedCount = entries.count { it.isPinned }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon  = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.st_ClipboardScreen_g3h5), fontWeight = FontWeight.Black) },
            text  = {
                Text(
                    stringResource(R.string.st_ClipboardScreen_i5j7) + " ${entries.size - pinnedCount} " + stringResource(R.string.st_ClipboardScreen_k7l9) + " " +
                            "$pinnedCount " + stringResource(R.string.st_ClipboardScreen_m9n1) + "${if (pinnedCount != 1) "s" else ""} " + stringResource(R.string.st_ClipboardScreen_o1p3)
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.st_ClipboardScreen_q3r5), fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.st_ClipboardScreen_s5t7))
                }
            },
            shape = BouncyShape,
        )
    }
}

// ─── Feed Pane ────────────────────────────────────────────────────────────────

@Composable
private fun ClipboardFeedPane(
    groups            : List<ClipboardGroup>,
    allEntries        : List<ClipboardEntry>,
    distinctTypes     : List<String>,
    activeTypeFilter  : String?,
    isSummarizingId   : Int?,
    isAiSearching     : Boolean,
    searchQuery       : String,
    offlineMode       : Boolean,
    selectedEntry     : ClipboardEntry?,
    showSearchBar     : Boolean,
    shizukuAuthorized : Boolean,
    onBack            : () -> Unit,
    onToggleSearch    : () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onEntryClick      : (ClipboardEntry) -> Unit,
    onEntryLongClick  : (ClipboardEntry) -> Unit,
    onCopy            : (ClipboardEntry) -> Unit,
    onDelete          : (ClipboardEntry) -> Unit,
    onPin             : (ClipboardEntry) -> Unit,
    onSummarize       : (ClipboardEntry) -> Unit,
    onConvertToTask   : (String) -> Unit,
    onContextualAction: (String, ClipboardEntry) -> Unit,
    onClearAll        : () -> Unit,
    onRefresh         : () -> Unit,
    onShizukuClick    : () -> Unit,
) {
    val context          = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val performanceMode  = LocalPerformanceMode.current
    val listState        = rememberLazyListState()
    var collapsedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    val scrollBehavior   = TopAppBarDefaults.enterAlwaysScrollBehavior(
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            Column {
                ExpressiveTopAppBar(
                    title    = "Clipboard",
                    subtitle = when {
                        isAiSearching -> "AI searching…"
                        allEntries.isEmpty() -> stringResource(R.string.st_ClipboardScreen_o5p6)
                        else -> "${allEntries.size} " + if (allEntries.size != 1) stringResource(R.string.st_ClipboardScreen_s9t0) else stringResource(R.string.st_ClipboardScreen_q7r8)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                        // Refresh
                        IconButton(onClick = {
                            vibrationManager?.vibrateTick()
                            onRefresh()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                        // Search toggle
                        IconButton(onClick = onToggleSearch) {
                            AnimatedContent(
                                targetState = showSearchBar,
                                transitionSpec = {
                                    (scaleIn(spring(0.5f, Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                                            (scaleOut() + fadeOut())
                                },
                                label = "search_toggle",
                            ) { active ->
                                Icon(
                                    if (active) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                                    contentDescription = if (active) stringResource(R.string.st_ClipboardScreen_u1v2) else stringResource(R.string.st_ClipboardScreen_w3x4),
                                    tint = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Clear all
                        AnimatedVisibility(visible = allEntries.isNotEmpty()) {
                            IconButton(onClick = {
                                vibrationManager?.vibrateTick()
                                onClearAll()
                            }) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear all")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )

                // ── AI search progress ─────────────────────────────────────
                AnimatedVisibility(
                    visible = isAiSearching,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit  = fadeOut(tween(200)) + shrinkVertically(),
                ) {
                    ExpressiveWavyLinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(3.dp),
                        color      = MaterialTheme.colorScheme.tertiary,
                        trackColor = Color.Transparent,
                    )
                }

                // ── Search bar ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { -it } +
                            fadeIn(tween(200)),
                    exit  = slideOutVertically { -it } + fadeOut(tween(150)),
                ) {
                    OutlinedTextField(
                        value          = searchQuery,
                        onValueChange  = onSearchQueryChange,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder    = {
                            Text(if (offlineMode) stringResource(R.string.st_ClipboardScreen_y5z6) else stringResource(R.string.st_ClipboardScreen_a7b8))
                        },
                        leadingIcon    = {
                            Icon(
                                if (isAiSearching) Icons.Rounded.AutoAwesome else Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isAiSearching) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon   = {
                            AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            }
                        },
                        singleLine     = true,
                        shape          = SmallExpressiveShape,
                        colors         = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = if (isAiSearching) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }

                // ── Type filter chips ──────────────────────────────────────
                AnimatedVisibility(visible = distinctTypes.size > 1) {
                    val metaAll = typeMeta("ALL")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalFadingEdges(left = 12.dp, right = 12.dp),
                    ) {
                        item {
                            ExpressiveFilterChip(
                                selected = activeTypeFilter == null,
                                onClick = { onTypeFilterChange("ALL") },
                                label = { Text(stringResource(R.string.st_ClipboardScreen_c9d0), fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AllInclusive, null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            )
                        }
                        items(distinctTypes, key = { it }) { type ->
                            val meta = typeMeta(type)
                            ExpressiveFilterChip(
                                selected   = activeTypeFilter == type,
                                onClick    = { onTypeFilterChange(type) },
                                label      = {
                                    Text(
                                        meta.label,
                                        fontWeight = if (activeTypeFilter == type) FontWeight.Black else FontWeight.Medium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        meta.icon, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (activeTypeFilter == type) meta.color
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = meta.color.copy(alpha = 0.2f),
                                    selectedLabelColor     = meta.color,
                                    selectedLeadingIconColor = meta.color
                                ),
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Stats summary bar
            AnimatedVisibility(
                visible = allEntries.isNotEmpty(),
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    shape = ExtraLargeExpressiveShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        val pinnedCount = allEntries.count { it.isPinned }
                        val aiCount     = allEntries.count { it.isAiProcessed }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StatPill(Icons.Rounded.ContentPaste, "${allEntries.size}", if (allEntries.size != 1) stringResource(R.string.st_ClipboardScreen_s9t0) else stringResource(R.string.st_ClipboardScreen_q7r8))
                            if (pinnedCount > 0)
                                StatPill(Icons.Rounded.PushPin, "$pinnedCount", stringResource(R.string.st_ClipboardScreen_e1f2))
                            if (aiCount > 0)
                                StatPill(Icons.Rounded.AutoAwesome, "$aiCount", "AI")
                        }

                        // Small refresh indicator or action could go here
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        AnimatedContent(
            targetState = allEntries.isEmpty(),
            transitionSpec = {
                (fadeIn(tween(280)) + scaleIn(tween(280), 0.95f)) togetherWith
                        (fadeOut(tween(180)) + scaleOut(tween(180), 0.97f))
            },
            label = "feed_content",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) { isEmpty ->
            if (isEmpty) {
                EmptyClipboardState()
            } else {
                LazyColumn(
                    state           = listState,
                    contentPadding  = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier        = Modifier
                        .fillMaxSize()
                        .run {
                            if (!performanceMode) fadingEdges(top = 20.dp, bottom = 20.dp) else this
                        },
                ) {
                    if (groups.isEmpty() && searchQuery.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Rounded.SearchOff, null,
                                        Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.st_ClipboardScreen_g3h4) + " \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }

                    groups.forEach { group ->
                        // Group header
                        stickyHeader(key = "header_${group.label}") {
                            val isCollapsed = collapsedGroups.contains(group.label)
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = 2.dp,
                                onClick = {
                                    vibrationManager?.vibrateTick()
                                    collapsedGroups = if (isCollapsed) collapsedGroups - group.label else collapsedGroups + group.label
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
                                        group.label,
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
                                                Icons.Rounded.ContentPaste,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                "${group.entries.size}",
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
                                            collapsedGroups = if (isCollapsed) collapsedGroups - group.label else collapsedGroups + group.label
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

                        if (!collapsedGroups.contains(group.label)) {
                            items(group.entries, key = { it.id }) { entry ->
                                SwipeToDismissClipEntry(
                                    entry       = entry,
                                    isSelected  = selectedEntry?.id == entry.id,
                                    isSummarizing = isSummarizingId == entry.id,
                                    offlineMode = offlineMode,
                                    onClick     = { onEntryClick(entry) },
                                    onLongClick = { onEntryLongClick(entry) },
                                    onCopy      = { onCopy(entry) },
                                    onPin       = { onPin(entry) },
                                    onDelete    = { onDelete(entry) },
                                    onSummarize = { onSummarize(entry) },
                                    onAction    = { action ->
                                        if (action == "convert_to_task") onConvertToTask(entry.content)
                                        else onContextualAction(action, entry)
                                    },
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ─── Swipe-to-Dismiss ────────────────────────────────────────────────────────

@Composable
private fun SwipeToDismissClipEntry(
    entry        : ClipboardEntry,
    isSelected   : Boolean,
    isSummarizing: Boolean,
    offlineMode  : Boolean,
    onClick      : () -> Unit,
    onLongClick  : () -> Unit,
    onCopy       : () -> Unit,
    onPin        : () -> Unit,
    onDelete     : () -> Unit,
    onSummarize  : () -> Unit,
    onAction     : (String) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { it * 0.38f },
    )

    SwipeToDismissBox(
        state                      = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else                             -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                label = "swipe_bg",
            )
            val scale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.2f else 0.7f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
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
                    contentDescription = stringResource(R.string.st_ClipboardScreen_m9n0),
                    tint     = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 28.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                )
            }
        },
    ) {
        ClipboardCard(
            entry         = entry,
            isSelected    = isSelected,
            isSummarizing = isSummarizing,
            offlineMode   = offlineMode,
            onClick       = onClick,
            onLongClick   = onLongClick,
            onCopy        = onCopy,
            onPin         = onPin,
            onSummarize   = onSummarize,
            onAction      = onAction,
        )
    }
}

// ─── Clipboard Card ───────────────────────────────────────────────────────────

@Composable
private fun ClipboardCard(
    entry        : ClipboardEntry,
    isSelected   : Boolean,
    isSummarizing: Boolean,
    offlineMode  : Boolean,
    onClick      : () -> Unit,
    onLongClick  : () -> Unit,
    onCopy       : () -> Unit,
    onPin        : () -> Unit,
    onSummarize  : () -> Unit,
    onAction     : (String) -> Unit,
) {
    val context = LocalContext.current
    val meta = typeMeta(entry.type)
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "card_border",
    )

    ExpressiveCard(
        onClick     = onClick,
        onLongClick = onLongClick,
        shape       = SquircleShape,
        containerColor = if (isSelected)
            MaterialTheme.colorScheme.surfaceContainerHighest
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) BorderStroke(2.dp, borderColor) else null,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Header row: type icon + label + pin + timestamp ────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth(),
            ) {
                // Type icon
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape    = RoundedCornerShape(14.dp),
                    color    = meta.color.copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (entry.type == "COLOR") {
                            val swatch = try {
                                Color(android.graphics.Color.parseColor(entry.content.trim()))
                            } catch (_: Exception) { meta.color }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(swatch),
                            )
                        } else {
                            Icon(meta.icon, null, Modifier.size(20.dp), tint = meta.color)
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Type label + AI badge
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            meta.label,
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color      = meta.color,
                        )
                        if (entry.isAiProcessed) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    "AI",
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color      = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                    Text(
                        entry.timestamp.toRelativeTime(context),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }

                // Pin toggle
                IconButton(
                    onClick  = onPin,
                    modifier = Modifier.size(32.dp),
                ) {
                    val pinScale by animateFloatAsState(
                        targetValue   = if (entry.isPinned) 1.15f else 1f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label         = "pin_scale",
                    )
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = if (entry.isPinned) stringResource(R.string.st_ClipboardScreen_o1p2) else stringResource(R.string.st_ClipboardScreen_q3r4),
                        tint     = if (entry.isPinned) meta.color
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp).graphicsLayer {
                            scaleX = pinScale; scaleY = pinScale
                            rotationZ = if (entry.isPinned) 0f else 30f
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Content ────────────────────────────────────────────────────
            Text(
                text     = entry.content,
                style    = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (entry.type == "CODE") FontFamily.Monospace else FontFamily.Default,
                    lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
                maxLines = if (entry.summary != null) 2 else 4,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onSurface,
            )

            // ── AI summary bubble ──────────────────────────────────────────
            AnimatedVisibility(
                visible = entry.summary != null || isSummarizing,
                enter   = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier  = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isSummarizing) {
                            ExpressiveContainedLoadingIndicator(
                                modifier = Modifier.size(16.dp),
                                color    = MaterialTheme.colorScheme.tertiary,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.AutoAwesome, null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Text(
                            text  = if (isSummarizing) stringResource(R.string.st_ClipboardScreen_s5t6) else entry.summary ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSummarizing) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Quick action chips ─────────────────────────────────────────
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
            ) {
                QuickChip(
                    label = stringResource(R.string.st_ClipboardScreen_o5p7),
                    icon  = Icons.Rounded.ContentCopy,
                    onClick = onCopy,
                )
                if (entry.summary == null && !offlineMode) {
                    QuickChip(
                        label     = if (isSummarizing) stringResource(R.string.st_ClipboardScreen_u7v8) else stringResource(R.string.st_ClipboardScreen_w9x0),
                        icon      = Icons.Rounded.AutoAwesome,
                        isAi      = true,
                        isLoading = isSummarizing,
                        onClick   = onSummarize,
                    )
                }
                QuickChip(
                    label   = stringResource(R.string.st_ClipboardScreen_a1b3),
                    icon    = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    onClick = { onAction("convert_to_task") },
                )
                QuickChip(
                    label   = stringResource(R.string.st_ClipboardScreen_c3d5),
                    icon    = Icons.Rounded.Share,
                    onClick = { onAction("share") },
                )
                // Contextual
                when (entry.type) {
                    "PHONE"         -> {
                        QuickChip(stringResource(R.string.st_ClipboardScreen_e5f7),  Icons.Rounded.Call)               { onAction("call") }
                        QuickChip(stringResource(R.string.st_ClipboardScreen_g7h9), Icons.Rounded.Textsms)         { onAction("whatsapp") }
                    }
                    "URL", "SOCIAL" -> QuickChip(stringResource(R.string.st_ClipboardScreen_i9j1), Icons.Rounded.OpenInBrowser) { onAction("open_url") }
                    "EMAIL"         -> QuickChip(stringResource(R.string.st_ClipboardScreen_7c4d), Icons.Rounded.Email)          { onAction("email") }
                    "CRYPTO"        -> QuickChip(stringResource(R.string.st_ClipboardScreen_k1l3), Icons.Rounded.TravelExplore) { onAction("open_url") }
                }
            }
        }
    }
}

// ─── Quick action chip ────────────────────────────────────────────────────────

@Composable
private fun QuickChip(
    label    : String,
    icon     : ImageVector,
    isAi     : Boolean = false,
    isLoading: Boolean = false,
    onClick  : () -> Unit,
) {
    val accentColor = if (isAi) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary
    val bgColor     = if (isAi) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f)
    else MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        onClick = if (isLoading) ({}) else onClick,
        shape   = RoundedCornerShape(10.dp),
        color   = bgColor,
        modifier = Modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color       = accentColor,
                )
            } else {
                Icon(icon, null, Modifier.size(12.dp), tint = accentColor)
            }
            Text(
                label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = accentColor,
            )
        }
    }
}

// ─── Clipboard Detail ─────────────────────────────────────────────────────────

@Composable
private fun ClipboardDetailContent(
    entry        : ClipboardEntry,
    isSummarizing: Boolean,
    offlineMode  : Boolean,
    isInPanel    : Boolean,
    onDismiss    : () -> Unit,
    onCopy       : () -> Unit,
    onDelete     : () -> Unit,
    onPin        : () -> Unit,
    onSummarize  : () -> Unit,
    onAction     : (String) -> Unit,
) {
    val meta = typeMeta(entry.type)
    val vibrationManager = LocalVibrationManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(if (isInPanel) 24.dp else 20.dp)
            .then(if (!isInPanel) Modifier.navigationBarsPadding() else Modifier),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Panel header
        if (isInPanel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.st_ClipboardScreen_m3n5), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_TabManagementScreen_1a2b))
                }
            }
        }

        // Type header
        Surface(
            shape = LargeExpressiveShape,
            color = meta.color.copy(alpha = 0.08f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape    = RoundedCornerShape(18.dp),
                    color    = meta.color.copy(alpha = 0.18f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(meta.icon, null, Modifier.size(24.dp), tint = meta.color)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(meta.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        entry.timestamp.toFullTimestamp(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Pin
                FilledTonalIconButton(onClick = onPin) {
                    Icon(
                        Icons.Rounded.PushPin, null,
                        tint = if (entry.isPinned) meta.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Selectable content
        Surface(
            shape = LargeExpressiveShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SelectionContainer {
                Text(
                    text     = entry.content,
                    style    = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (entry.type == "CODE") FontFamily.Monospace else FontFamily.Default,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                )
            }
        }

        // AI summary
        AnimatedVisibility(
            visible = entry.summary != null || isSummarizing,
            enter   = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
        ) {
            Surface(
                shape  = LargeExpressiveShape,
                color  = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isSummarizing) {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color    = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    }
                    Text(
                        text  = if (isSummarizing) "Generating summary…" else entry.summary ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSummarizing) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolzExpressiveButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_ClipboardScreen_o5p7))
            }
            if (entry.summary == null && !offlineMode) {
                ToolzOutlinedExpressiveButton(
                    onClick  = onSummarize,
                    modifier = Modifier.weight(1f),
                    enabled  = !isSummarizing,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSummarizing) "…" else stringResource(R.string.st_ClipboardScreen_q7r9))
                }
            }
        }

        // Contextual action buttons
        val contextActions = buildList<Triple<String, ImageVector, String>> {
            add(Triple(stringResource(R.string.st_ClipboardScreen_c3d5),          Icons.Rounded.Share,           "share"))
            add(Triple(stringResource(R.string.st_ClipboardScreen_s9t1),        Icons.AutoMirrored.Rounded.PlaylistAdd, "convert_to_task"))
            when (entry.type) {
                "PHONE"         -> { add(Triple(stringResource(R.string.st_ClipboardScreen_e5f7), Icons.Rounded.Call, "call")); add(Triple(stringResource(R.string.st_ClipboardScreen_g7h9), Icons.Rounded.Textsms, "whatsapp")) }
                "URL", "SOCIAL" -> add(Triple(stringResource(R.string.st_ClipboardScreen_u1v3), Icons.Rounded.OpenInBrowser, "open_url"))
                "EMAIL"         -> add(Triple(stringResource(R.string.st_ClipboardScreen_w3x5), Icons.Rounded.Email, "email"))
                "CRYPTO"        -> add(Triple(stringResource(R.string.st_ClipboardScreen_k1l3), Icons.Rounded.TravelExplore, "open_url"))
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
        ) {
            contextActions.forEach { (label, icon, action) ->
                ToolzOutlinedExpressiveButton(
                    onClick = { vibrationManager?.vibrateTick(); onAction(action) },
                ) {
                    Icon(icon, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(label)
                }
            }
        }

        ToolzExpressiveButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor   = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.st_CleanerScreen_a1b2), fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Context Bottom Sheet ─────────────────────────────────────────────────────

@Composable
private fun ClipboardContextSheet(
    entry        : ClipboardEntry,
    isSummarizing: Boolean,
    offlineMode  : Boolean,
    onDismiss    : () -> Unit,
    onCopy       : () -> Unit,
    onPin        : () -> Unit,
    onDelete     : () -> Unit,
    onSummarize  : () -> Unit,
    onAction     : (String) -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val meta   = typeMeta(entry.type)
    val sheetState = rememberModalBottomSheetState()

    val menuItems = buildList<Triple<String, ImageVector, () -> Unit>> {
        add(Triple(stringResource(R.string.st_ClipboardScreen_o5p7),              Icons.Rounded.ContentCopy)           { onCopy() })
        add(Triple(if (entry.isPinned) stringResource(R.string.st_ClipboardScreen_o1p2) else stringResource(R.string.st_ClipboardScreen_q3r4), Icons.Rounded.PushPin) { onPin() })
        add(Triple(stringResource(R.string.st_ClipboardScreen_c3d5),             Icons.Rounded.Share)                 { onAction("share") })
        add(Triple(stringResource(R.string.st_ClipboardScreen_y5z7),   Icons.AutoMirrored.Rounded.PlaylistAdd) { onAction("convert_to_task") })
        if (entry.summary == null && !offlineMode)
            add(Triple(stringResource(R.string.st_ClipboardScreen_a7b9),  Icons.Rounded.AutoAwesome)           { onSummarize() })
        when (entry.type) {
            "PHONE"         -> { add(Triple(stringResource(R.string.st_ClipboardScreen_e5f7), Icons.Rounded.Call) { onAction("call") }); add(Triple(stringResource(R.string.st_ClipboardScreen_g7h9), Icons.Rounded.Textsms) { onAction("whatsapp") }) }
            "URL", "SOCIAL" -> add(Triple(stringResource(R.string.st_ClipboardScreen_u1v3), Icons.Rounded.OpenInBrowser) { onAction("open_url") })
            "EMAIL"         -> add(Triple(stringResource(R.string.st_ClipboardScreen_w3x5), Icons.Rounded.Email)        { onAction("email") })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        shape            = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Preview
            Surface(
                shape    = MediumExpressiveShape,
                color    = meta.color.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(meta.icon, null, Modifier.size(20.dp), tint = meta.color)
                    Text(
                        entry.content,
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(
                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            menuItems.forEachIndexed { idx, (label, icon, action) ->
                StaggeredEntrance(index = idx) {
                    Surface(
                        onClick  = {
                            vibrationManager?.vibrateTick()
                            action()
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape    = MediumExpressiveShape,
                        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(icon, null, Modifier.size(18.dp), tint = meta.color)
                            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            ToolzExpressiveButton(
                onClick  = {
                    vibrationManager?.vibrateLongClick()
                    onDelete()
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_CleanerScreen_a1b2), fontWeight = FontWeight.Black)
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyClipboardState() {
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(108.dp)
                    .graphicsLayer {
                        if (!performanceMode) { scaleX = pulse; scaleY = pulse }
                    },
                shape  = ExtraLargeExpressiveShape,
                color  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {
                Icon(
                    Icons.Rounded.ContentPaste, null,
                    Modifier.padding(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.st_ClipboardScreen_c9d1),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.st_ClipboardScreen_e1f3),
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign  = TextAlign.Center,
            )
        }
    }
}

// ─── Stats Pill ───────────────────────────────────────────────────────────────

@Composable
private fun StatPill(icon: ImageVector, value: String, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            icon, null,
            Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Contextual Action Handler ────────────────────────────────────────────────

private fun handleContextualAction(context: Context, action: String, entry: ClipboardEntry) {
    when (action) {
        "call" -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.content.trim()}")))
        "whatsapp" -> {
            runCatching {
                val phone = entry.content.trim().replace("[^\\d+]".toRegex(), "")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")))
            }.onFailure { Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show() }
        }
        "open_url" -> {
            runCatching {
                val raw = entry.content.trim()
                val url = when {
                    raw.startsWith("http")    -> raw
                    raw.startsWith("www.")    -> "https://$raw"
                    entry.type == "SOCIAL"    -> "https://$raw"
                    entry.type == "CRYPTO" && raw.startsWith("0x") ->
                        "https://etherscan.io/address/$raw"
                    entry.type == "CRYPTO"    ->
                        "https://www.blockchain.com/explorer/addresses/btc/$raw"
                    else                      -> "https://www.google.com/search?q=${Uri.encode(raw)}"
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure { Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show() }
        }
        "email" -> context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${entry.content.trim()}")))
        "share" -> context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, entry.content) },
            "Share via",
        ))
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private fun fakeSampleEntries() = listOf(
    ClipboardEntry(
        id = 1,
        content = "https://developer.android.com/jetpack/compose",
        timestamp = System.currentTimeMillis() - 60_000L,
        type = "URL",
        summary = "Android Compose docs",
        isPinned = true
    ),
    ClipboardEntry(
        id = 2,
        content = "+1 650 253 0000",
        timestamp = System.currentTimeMillis() - 600_000L,
        type = "PHONE",
        isPinned = false
    ),
    ClipboardEntry(
        id = 3,
        content = "fun greet(name: String) = println(\"Hello, \$name\")",
        timestamp = System.currentTimeMillis() - 3_600_000L,
        type = "CODE",
        summary = "Kotlin greeting function",
        isAiProcessed = true
    ),
    ClipboardEntry(
        id = 4,
        content = "Your OTP is 847 293. Valid for 5 minutes.",
        timestamp = System.currentTimeMillis() - 86_400_000L,
        type = "OTP",
        summary = "One-time password"
    ),
    ClipboardEntry(
        id = 5,
        content = "1 cup flour, 2 eggs, 200ml milk — whisk together.",
        timestamp = System.currentTimeMillis() - 172_800_000L,
        type = "RECIPE"
    ),
)

@Preview(name = "Feed · Light", showBackground = true)
@Composable
private fun FeedLightPreview() {
    ToolzTheme {
        val fakeGroups = listOf(
            ClipboardGroup("Pinned",  fakeSampleEntries().filter { it.isPinned }),
            ClipboardGroup("Today",     fakeSampleEntries().filter { !it.isPinned }.take(2)),
            ClipboardGroup("Yesterday", fakeSampleEntries().filter { !it.isPinned }.drop(2)),
        )
        ClipboardFeedPane(
            groups             = fakeGroups,
            allEntries         = fakeSampleEntries(),
            distinctTypes      = listOf("URL","PHONE","CODE","OTP","RECIPE"),
            activeTypeFilter   = null,
            isSummarizingId    = null,
            isAiSearching      = false,
            searchQuery        = "",
            offlineMode        = false,
            selectedEntry      = null,
            showSearchBar      = false,
            shizukuAuthorized  = false,
            onBack             = {},
            onToggleSearch     = {},
            onSearchQueryChange= {},
            onTypeFilterChange = {},
            onEntryClick       = {},
            onEntryLongClick   = {},
            onCopy             = {},
            onDelete           = {},
            onPin              = {},
            onSummarize        = {},
            onConvertToTask    = {},
            onContextualAction = { _, _ -> },
            onClearAll         = {},
            onRefresh          = {},
            onShizukuClick     = {},
        )
    }
}

@Preview(name = "Feed · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedDarkPreview() {
    ToolzTheme(darkTheme = true) {
        val groups = listOf(ClipboardGroup("Today", fakeSampleEntries()))
        ClipboardFeedPane(
            groups             = groups,
            allEntries         = fakeSampleEntries(),
            distinctTypes      = listOf("URL","PHONE","CODE","OTP","RECIPE"),
            activeTypeFilter   = "CODE",
            isSummarizingId    = 3,
            isAiSearching      = true,
            searchQuery        = "kotlin",
            offlineMode        = false,
            selectedEntry      = null,
            showSearchBar      = true,
            shizukuAuthorized  = true,
            onBack             = {},
            onToggleSearch     = {},
            onSearchQueryChange= {},
            onTypeFilterChange = {},
            onEntryClick       = {},
            onEntryLongClick   = {},
            onCopy             = {},
            onDelete           = {},
            onPin              = {},
            onSummarize        = {},
            onConvertToTask    = {},
            onContextualAction = { _, _ -> },
            onClearAll         = {},
            onRefresh          = {},
            onShizukuClick     = {},
        )
    }
}

@Preview(name = "Card · Light", showBackground = true)
@Composable
private fun CardLightPreview() {
    ToolzTheme {
        Box(Modifier.padding(16.dp)) {
            ClipboardCard(
                entry         = fakeSampleEntries()[2],
                isSelected    = false,
                isSummarizing = false,
                offlineMode   = false,
                onClick       = {},
                onLongClick   = {},
                onCopy        = {},
                onPin         = {},
                onSummarize   = {},
                onAction      = {},
            )
        }
    }
}

@Preview(name = "Card · Selected", showBackground = true)
@Composable
private fun CardSelectedPreview() {
    ToolzTheme {
        Box(Modifier.padding(16.dp)) {
            ClipboardCard(
                entry         = fakeSampleEntries()[0],
                isSelected    = true,
                isSummarizing = false,
                offlineMode   = false,
                onClick       = {},
                onLongClick   = {},
                onCopy        = {},
                onPin         = {},
                onSummarize   = {},
                onAction      = {},
            )
        }
    }
}

@Preview(name = "Empty State", showBackground = true)
@Composable
private fun EmptyPreview() {
    ToolzTheme { EmptyClipboardState() }
}