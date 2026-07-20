package com.frerox.toolz.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.data.search.QuickLinkEntry
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.ui.screens.search.components.*
import java.util.Calendar

// ══════════════════════════════════════════════════════════
//  ROOT SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onResultClick: (url: String) -> Unit,
    onManageTabs: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState     by viewModel.uiState.collectAsState()
    val history     by viewModel.history.collectAsState(initial = emptyList())
    val bookmarks   by viewModel.bookmarks.collectAsState(initial = emptyList())
    val quickLinks  by viewModel.quickLinks.collectAsState(initial = emptyList())
    val isFirstTime by viewModel.isFirstTime.collectAsState(initial = false)
    val context     = LocalContext.current

    // Sheet / dialog flags
    var showSearchSettings     by remember { mutableStateOf(false) }
    var showSecuritySheet      by remember { mutableStateOf(false) }
    var showDnsSheet           by remember { mutableStateOf(false) }
    var showEngineSheet        by remember { mutableStateOf(false) }
    var showAddQuickLink       by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksAll       by remember { mutableStateOf(false) }
    var longPressedResult      by remember { mutableStateOf<SearchResult?>(null) }
    var editingBookmark        by remember { mutableStateOf<BookmarkEntry?>(null) }
    var editingQuickLink       by remember { mutableStateOf<QuickLinkEntry?>(null) }
    var showNextDnsWarning     by remember { mutableStateOf(false) }

    // Whether the suggestion/history dropdown is visible
    val showDropdown = uiState.isActive &&
            (uiState.suggestions.isNotEmpty() || history.isNotEmpty())

    // ── Sheets & dialogs ──────────────────────────────────

    if (showSearchSettings) {
        SearchSettingsSheet(
            onDismiss           = { showSearchSettings = false },
            currentEngine       = uiState.searchEngine,
            onEngineClick       = { showEngineSheet = true; showSearchSettings = false },
            adBlockEnabled      = uiState.adBlockEnabled,
            onAdBlockToggle     = viewModel::toggleAdBlock,
            currentDns          = uiState.dnsProvider,
            onDnsClick          = { showDnsSheet = true; showSearchSettings = false },
            safeSearch          = uiState.safeSearch,
            onSafeSearchToggle  = viewModel::setSafeSearch,
            isIncognito         = uiState.isIncognito,
            onIncognitoToggle   = { viewModel.toggleIncognito(!uiState.isIncognito) },
            autofillEnabled     = uiState.searchAutofillEnabled,
            onAutofillToggle    = viewModel::toggleAutofill,
        )
    }

    if (showEngineSheet) {
        SearchEngineSheet(
            onDismiss      = { showEngineSheet = false },
            currentEngine  = uiState.searchEngine,
            onEngineSelect = { viewModel.setSearchEngine(it); showEngineSheet = false }
        )
    }

    if (showSecuritySheet) {
        SecuritySheet(
            onDismiss           = { showSecuritySheet = false },
            currentProvider     = uiState.dnsProvider,
            adBlockEnabled      = uiState.adBlockEnabled,
            isIncognito         = uiState.isIncognito,
            autofillEnabled     = uiState.searchAutofillEnabled,
            onAdBlockToggle     = viewModel::toggleAdBlock,
            onIncognitoToggle   = viewModel::toggleIncognito,
            onAutofillToggle    = viewModel::toggleAutofill,
            onPresetSelect      = viewModel::setSecurityPreset,
            onDnsClick          = { showDnsSheet = true; showSecuritySheet = false },
            onCustomizeAdBlock  = {
                showSecuritySheet = false
                onResultClick(com.frerox.toolz.ui.navigation.Screen.AdBlockSettings.route)
            }
        )
    }

    if (showDnsSheet) {
        DnsSheet(
            onDismiss           = { showDnsSheet = false },
            currentProvider     = uiState.dnsProvider,
            customDns           = uiState.customDns,
            onProviderSelect    = { provider ->
                if (provider == "NEXTDNS" && uiState.nextDnsId.isBlank()) {
                    showNextDnsWarning = true
                } else {
                    viewModel.setDnsProvider(provider)
                }
            },
            onCustomDnsChange   = viewModel::setCustomDns,
            benchmarks          = uiState.dnsBenchmarks,
            isBenchmarking      = uiState.isBenchmarkingDns,
            onRunBenchmark      = { viewModel.runDnsBenchmark() },
            onApplyFastest      = { viewModel.applyFastestDns() }
        )
    }

    if (showAddQuickLink) {
        QuickLinkDialog(
            onDismiss = { showAddQuickLink = false },
            onConfirm = { t, u -> viewModel.addQuickLink(t, u); showAddQuickLink = false },
        )
    }

    editingQuickLink?.let { ql ->
        QuickLinkDialog(
            titleInitial = ql.title,
            urlInitial   = ql.url,
            dialogTitle  = "Edit quick link",
            onDismiss    = { editingQuickLink = null },
            onConfirm    = { t, u -> viewModel.updateQuickLink(ql.id, t, u); editingQuickLink = null },
        )
    }

    editingBookmark?.let { bm ->
        QuickLinkDialog(
            titleInitial = bm.title,
            urlInitial   = bm.url,
            dialogTitle  = "Edit bookmark",
            onDismiss    = { editingBookmark = null },
            onConfirm    = { t, u -> viewModel.updateBookmark(bm.id, t, u); editingBookmark = null },
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon   = { Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title  = { Text("Clear history?") },
            text   = { Text("All saved searches will be removed.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearHistory(); showClearHistoryDialog = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(24.dp),
        )
    }

    if (showBookmarksAll) {
        AllBookmarksSheet(
            bookmarks       = bookmarks,
            onDismiss       = { showBookmarksAll = false },
            onBookmarkClick = { url -> viewModel.openTab(url); onResultClick(url) },
            onEdit          = { editingBookmark = it; showBookmarksAll = false },
            onDelete        = { viewModel.removeBookmark(it) },
        )
    }

    longPressedResult?.let { result ->
        ResultActionsSheet(
            result           = result,
            onDismiss        = { longPressedResult = null },
            onBookmarkToggle = { viewModel.toggleBookmark(result); longPressedResult = null },
            onShare          = {
                context.startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, result.url)
                        }, "Share"
                    )
                )
                longPressedResult = null
            },
            onCopy = {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", result.url))
                longPressedResult = null
            },
        )
    }

    if (showNextDnsWarning) {
        AlertDialog(
            onDismissRequest = { showNextDnsWarning = false },
            icon = { Icon(Icons.Rounded.NotificationImportant, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("NextDNS Not Configured") },
            text = { Text("To use NextDNS, you must first provide your Configuration ID in the Ad Block settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showNextDnsWarning = false
                        showDnsSheet = false
                        showSearchSettings = false
                        onResultClick(com.frerox.toolz.ui.navigation.Screen.AdBlockSettings.route)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Go to Setup") }
            },
            dismissButton = {
                TextButton(onClick = { showNextDnsWarning = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (isFirstTime) {
        OnboardingDialog(onDismiss = { viewModel.dismissFirstTime() })
    }

    // ── Main layout ───────────────────────────────────────

    // Dim overlay while dropdown is open
    val dimAlpha by animateFloatAsState(
        targetValue   = if (showDropdown) 0.35f else 0f,
        animationSpec = tween(200),
        label         = "dimAlpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Page content (start / results / loading / error)
        Column(modifier = Modifier.fillMaxSize()) {

            // Top chrome: status bar padding + search pill + security row
            Surface(
                modifier       = Modifier.fillMaxWidth(),
                shape          = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                color          = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Back + search pill row
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Back button when in results
                        AnimatedVisibility(
                            visible = (uiState.results.isNotEmpty() || uiState.phase == SearchPhase.Loading) && !uiState.isActive,
                            enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                            exit    = scaleOut() + fadeOut(),
                        ) {
                            FilledIconButton(
                                onClick = { viewModel.clearSearch() },
                                modifier = Modifier.size(40.dp),
                                colors   = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor   = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack, null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        SearchPill(
                            query         = uiState.query,
                            onQueryChange = viewModel::onQueryChange,
                            onSearch      = { raw ->
                                val q = raw.trim()
                                if (q.isEmpty()) return@SearchPill
                                val looksLikeUrl = (q.contains(".") || q.startsWith("http")) && !q.contains(" ")
                                if (looksLikeUrl) {
                                    val url = if (q.startsWith("http")) q else "https://$q"
                                    viewModel.openTab(url)
                                    onResultClick(url)
                                } else {
                                    viewModel.onSearch(q)
                                }
                                viewModel.onActiveChange(false)
                            },
                            active        = uiState.isActive,
                            onActiveChange = viewModel::onActiveChange,
                            onBackClick   = onBackClick,
                            onSettingsClick = { showSearchSettings = true },
                            isIncognito   = uiState.isIncognito,
                            modifier      = Modifier.weight(1f),
                        )
                    }

                    // Security row (hidden while focused)
                    AnimatedVisibility(
                        visible = !uiState.isActive,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        SecurityStatusRow(
                            adBlockEnabled = uiState.adBlockEnabled,
                            dnsProvider    = uiState.dnsProvider,
                            isIncognito    = uiState.isIncognito,
                            latency        = uiState.dnsBenchmarks[uiState.dnsProvider.lowercase()],
                            onClick        = { showSecuritySheet = true },
                        )
                    }
                }
            }

            // Content area
            Box(modifier = Modifier.weight(1f)) {
                PageContent(
                    uiState         = uiState,
                    history         = history,
                    bookmarks       = bookmarks,
                    quickLinks      = quickLinks,
                    onResultClick   = { result ->
                        viewModel.openTab(result.url)
                        onResultClick(result.url)
                    },
                    onLongPress     = { longPressedResult = it },
                    onLoadMore      = viewModel::loadMore,
                    onBackClick     = onBackClick,
                    onUrlOpen       = { url -> viewModel.openTab(url); onResultClick(url) },
                    onAddQuickLink  = { showAddQuickLink = true },
                    onEditQuickLink = { editingQuickLink = it },
                    onEditBookmark  = { editingBookmark = it },
                    onRemoveBookmark = viewModel::removeBookmark,
                    onDeleteHistory = viewModel::deleteHistory,
                    onClearHistory  = { showClearHistoryDialog = true },
                    onSeeAllBookmarks = { showBookmarksAll = true },
                    onRetry         = viewModel::retrySearch,
                    onSearch        = viewModel::onSearch,
                )

                // Floating tab pill — bottom-center
                androidx.compose.animation.AnimatedVisibility(
                    visible  = !uiState.isActive,
                    enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                ) {
                    FloatingSearchDock(
                        tabCount = uiState.tabs.size,
                        onManageTabs = onManageTabs,
                        onNewTab = { viewModel.clearSearch(); viewModel.onActiveChange(true) }
                    )
                }
            }
        }

        // ── Dim behind dropdown
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        viewModel.onActiveChange(false)
                    }
            )
        }

        // ── Dropdown overlay (suggestions + history)
        AnimatedVisibility(
            visible  = showDropdown,
            enter    = expandVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn(tween(150)),
            exit     = shrinkVertically(tween(150)) + fadeOut(tween(100)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                // offset below the top chrome — estimate 120dp for status+pill+security
                .padding(top = 120.dp)
                .padding(horizontal = 12.dp),
        ) {
            Surface(
                shape          = RoundedCornerShape(20.dp),
                color          = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (uiState.suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        uiState.suggestions.take(6).forEachIndexed { i, s ->
                            SuggestionRow(
                                text     = s,
                                onSearch = { viewModel.onSearch(s); viewModel.onActiveChange(false) },
                                onFill   = { viewModel.onQueryChange(s) },
                            )
                            if (i < uiState.suggestions.lastIndex.coerceAtMost(5)) {
                                HorizontalDivider(
                                    modifier  = Modifier.padding(horizontal = 20.dp),
                                    thickness = 0.5.dp,
                                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    val filteredHistory = history.filter {
                        uiState.query.isBlank() || it.query.contains(uiState.query, ignoreCase = true)
                    }
                    if (filteredHistory.isNotEmpty()) {
                        if (uiState.suggestions.isNotEmpty()) {
                            HorizontalDivider(
                                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 1.dp,
                            )
                        }
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recent",
                                style      = MaterialTheme.typography.labelMedium,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick        = { viewModel.clearHistory() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    "Clear all",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        filteredHistory.take(5).forEachIndexed { _, entry ->
                            HistoryRow(
                                query    = entry.query,
                                onSearch = { viewModel.onSearch(entry.query); viewModel.onActiveChange(false) },
                                onDelete = { viewModel.deleteHistory(entry.id) },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  PAGE CONTENT  — state machine with Crossfade
// ══════════════════════════════════════════════════════════

@Composable
private fun PageContent(
    uiState: SearchUiState,
    history: List<SearchHistoryEntry>,
    bookmarks: List<BookmarkEntry>,
    quickLinks: List<QuickLinkEntry>,
    onResultClick: (SearchResult) -> Unit,
    onLongPress: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    onBackClick: () -> Unit,
    onUrlOpen: (String) -> Unit,
    onAddQuickLink: () -> Unit,
    onEditQuickLink: (QuickLinkEntry) -> Unit,
    onEditBookmark: (BookmarkEntry) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onSeeAllBookmarks: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val screenState = when {
        uiState.phase == SearchPhase.Loading                               -> "loading"
        uiState.results.isNotEmpty()                                       -> "results"
        uiState.phase == SearchPhase.Results && uiState.error != null      -> "error"
        uiState.phase == SearchPhase.Results && uiState.results.isEmpty()  -> "error"
        else                                                               -> "home"
    }

    Crossfade(
        targetState   = screenState,
        animationSpec = tween(260),
        label         = "pageState",
    ) { state ->
        when (state) {
            "loading" -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                SearchShimmer()
            }

            "error" -> Box(Modifier.fillMaxSize()) {
                ErrorState(
                    title   = if (uiState.error != null) "Search error" else "No results",
                    message = uiState.error?.userMessage() ?: "Nothing found for \"${uiState.query}\"",
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            "results" -> ResultsPage(
                uiState      = uiState,
                onResultClick = onResultClick,
                onLongPress  = onLongPress,
                onLoadMore   = onLoadMore,
                onBackClick  = onBackClick,
            )

            else -> HomePage(
                uiState             = uiState,
                history             = history,
                bookmarks           = bookmarks,
                quickLinks          = quickLinks,
                onUrlOpen           = onUrlOpen,
                onAddQuickLink      = onAddQuickLink,
                onEditQuickLink     = onEditQuickLink,
                onEditBookmark      = onEditBookmark,
                onRemoveBookmark    = onRemoveBookmark,
                onDeleteHistory     = onDeleteHistory,
                onClearHistory      = onClearHistory,
                onSeeAllBookmarks   = onSeeAllBookmarks,
                onSearch            = onSearch,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  HOME PAGE
// ══════════════════════════════════════════════════════════

@Composable
private fun HomePage(
    uiState: SearchUiState,
    history: List<SearchHistoryEntry>,
    bookmarks: List<BookmarkEntry>,
    quickLinks: List<QuickLinkEntry>,
    onUrlOpen: (String) -> Unit,
    onAddQuickLink: () -> Unit,
    onEditQuickLink: (QuickLinkEntry) -> Unit,
    onEditBookmark: (BookmarkEntry) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onSeeAllBookmarks: () -> Unit,
    onSearch: (String) -> Unit,
) {
    LazyColumn(
        modifier        = Modifier
            .fillMaxSize()
            .fadingEdges(),
        contentPadding  = PaddingValues(
            top    = 20.dp,
            bottom = 120.dp,
            start  = 20.dp,
            end    = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // ── Greeting ─────────────────────────────────────
        item(key = "greeting") {
            GreetingHeader(userName = uiState.userName)
        }

        // ── Quick access ─────────────────────────────────
        item(key = "quickLinks") {
            QuickLinksSection(
                quickLinks     = quickLinks,
                onTileClick    = { onUrlOpen(it) },
                onAddClick     = onAddQuickLink,
                onEditClick    = { onEditQuickLink(it) },
            )
        }

        // ── Bookmarks ────────────────────────────────────
        if (bookmarks.isNotEmpty()) {
            item(key = "bookmarks") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title       = "Bookmarks",
                        actionLabel = if (bookmarks.size > 3) "See all" else null,
                        onAction    = onSeeAllBookmarks,
                    )
                    LazyRow(
                        contentPadding        = PaddingValues(end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            BookmarkCard(
                                title = bm.title,
                                url = bm.url,
                                onClick = { onUrlOpen(bm.url) },
                                onLongClick = { onEditBookmark(bm) }
                            )
                        }
                    }
                }
            }
        }

        // ── Recent history ────────────────────────────────
        if (history.isNotEmpty()) {
            item(key = "historyHeader") {
                SectionHeader(
                    title       = "Recent searches",
                    actionLabel = "Clear all",
                    onAction    = onClearHistory,
                )
            }
            items(history.take(6), key = { "h_${it.id}" }) { entry ->
                HistoryRow(
                    query    = entry.query,
                    onSearch = { onSearch(entry.query) },
                    onDelete = { onDeleteHistory(entry.id) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        } else {
            item(key = "emptyHistory") {
                EmptyHistory()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  GREETING HEADER
// ══════════════════════════════════════════════════════════

@Composable
private fun GreetingHeader(userName: String) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 5  -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else      -> "Good evening"
    }
    val name = if (userName.isNotBlank()) ", $userName" else ""

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(500)) + slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec  = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$greeting$name",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Where would you like to go?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  QUICK LINKS SECTION
// ══════════════════════════════════════════════════════════

@Composable
private fun QuickLinksSection(
    quickLinks: List<QuickLinkEntry>,
    onTileClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (QuickLinkEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Quick access")
        LazyRow(
            contentPadding        = PaddingValues(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(quickLinks, key = { it.id }) { ql ->
                QuickAccessTile(
                    title       = ql.title,
                    url         = ql.url,
                    onClick     = { onTileClick(ql.url) },
                    onLongClick = { onEditClick(ql) },
                )
            }
            item { AddQuickAccessTile(onClick = onAddClick) }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  EMPTY HISTORY
// ══════════════════════════════════════════════════════════

@Composable
private fun EmptyHistory() {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape    = RoundedCornerShape(24.dp),
            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ManageSearch, null,
                    modifier = Modifier.size(32.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Text(
            "Start your first search",
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
        )
    }
}

// ══════════════════════════════════════════════════════════
//  RESULTS PAGE
// ══════════════════════════════════════════════════════════

@Composable
private fun ResultsPage(
    uiState: SearchUiState,
    onResultClick: (SearchResult) -> Unit,
    onLongPress: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    onBackClick: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Trigger load-more when 4 items from end
    LaunchedEffect(listState) {
        snapshotFlow {
            val last  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            last >= total - 4
        }.collect { nearEnd ->
            if (nearEnd && uiState.canLoadMore && uiState.phase != SearchPhase.LoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyColumn(
        state           = listState,
        modifier        = Modifier
            .fillMaxSize()
            .fadingEdges(),
        contentPadding  = PaddingValues(
            start  = 16.dp,
            top    = 12.dp,
            end    = 16.dp,
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "resultsCount") {
            Text(
                "${uiState.results.size} results",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }

        itemsIndexed(
            items = uiState.results,
            key   = { index, result -> "${result.url}_$index" },
        ) { index, result ->
            SearchResultCard(
                result      = result,
                onClick     = { onResultClick(result) },
                onLongClick = { onLongPress(result) },
                modifier    = Modifier.animateItem(
                    fadeInSpec    = tween(300, delayMillis = (index * 35).coerceAtMost(400)),
                    placementSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
                ),
            )
        }

        // Footer
        item(key = "footer") {
            when {
                uiState.phase == SearchPhase.LoadingMore -> {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color       = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                uiState.canLoadMore && uiState.results.size >= 100 -> {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onLoadMore,
                            shape   = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Load more results")
                        }
                        Text(
                            "Results may be less relevant the further you go",
                            style  = MaterialTheme.typography.labelSmall,
                            color  = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
                !uiState.canLoadMore && uiState.results.isNotEmpty() -> {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "You've reached the end",
                            style  = MaterialTheme.typography.bodyMedium,
                            color  = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        OutlinedButton(
                            onClick = onBackClick,
                            shape   = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack, null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Back to home")
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  TAB COUNT FAB
// ══════════════════════════════════════════════════════════


// ══════════════════════════════════════════════════════════
//  BOTTOM SHEETS
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSettingsSheet(
    onDismiss: () -> Unit,
    currentEngine: String,
    onEngineClick: () -> Unit,
    adBlockEnabled: Boolean,
    onAdBlockToggle: (Boolean) -> Unit,
    currentDns: String,
    onDnsClick: () -> Unit,
    safeSearch: Boolean,
    onSafeSearchToggle: (Boolean) -> Unit,
    isIncognito: Boolean,
    onIncognitoToggle: () -> Unit,
    autofillEnabled: Boolean,
    onAutofillToggle: (Boolean) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Search settings",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            // Engine Selection Card
            Surface(
                onClick        = onEngineClick,
                shape          = RoundedCornerShape(20.dp),
                color          = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier       = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Search, null,
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Search Engine",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            currentEngine.lowercase().replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            SettingsToggleRow(
                title    = "Safe search",
                subtitle = "Filter explicit content",
                checked  = safeSearch,
                onCheckedChange = onSafeSearchToggle,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.FamilyRestroom, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (safeSearch) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingsToggleRow(
                title    = "Ad & tracker blocking",
                subtitle = "Block 200+ ad domains",
                checked  = adBlockEnabled,
                onCheckedChange = onAdBlockToggle,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Shield, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (adBlockEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingsToggleRow(
                title    = "Incognito mode",
                subtitle = "Don't save search history",
                checked  = isIncognito,
                onCheckedChange = { onIncognitoToggle() },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.VisibilityOff, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isIncognito) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingsToggleRow(
                title    = "Password autofill",
                subtitle = "Fill credentials with biometrics",
                checked  = autofillEnabled,
                onCheckedChange = onAutofillToggle,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Key, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (autofillEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            // DNS row — tappable link
            Surface(
                onClick        = onDnsClick,
                shape          = RoundedCornerShape(16.dp),
                color          = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier       = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Dns, null,
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DNS Provider",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            currentDns.lowercase().replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecuritySheet(
    onDismiss: () -> Unit,
    currentProvider: String,
    adBlockEnabled: Boolean,
    isIncognito: Boolean,
    autofillEnabled: Boolean,
    onAdBlockToggle: (Boolean) -> Unit,
    onIncognitoToggle: (Boolean) -> Unit,
    onAutofillToggle: (Boolean) -> Unit,
    onPresetSelect: (String) -> Unit,
    onDnsClick: () -> Unit,
    onCustomizeAdBlock: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Privacy & Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // Presets
            Text("Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LOW" to "Standard", "BASIC" to "Enhanced", "MAX" to "Maximum").forEach { (k, l) ->
                    FilledTonalButton(
                        onClick   = { onPresetSelect(k) },
                        shape     = RoundedCornerShape(14.dp),
                        modifier  = Modifier.weight(1f),
                    ) { Text(l, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            Column {
                SettingsToggleRow("Ad & tracker blocking", "200+ blocked domains", adBlockEnabled, onAdBlockToggle,
                    leadingIcon = { Icon(Icons.Rounded.Shield, null, Modifier.size(20.dp), tint = if (adBlockEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) })
                
                AnimatedVisibility(visible = adBlockEnabled) {
                    TextButton(
                        onClick = onCustomizeAdBlock,
                        modifier = Modifier.padding(start = 54.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Customize blocklists", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            SettingsToggleRow("Incognito mode", "No history saved", isIncognito, onIncognitoToggle,
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(20.dp), tint = if (isIncognito) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) })
            SettingsToggleRow("Password autofill", "Biometric verification", autofillEnabled, onAutofillToggle,
                leadingIcon = { Icon(Icons.Rounded.Key, null, Modifier.size(20.dp), tint = if (autofillEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) })

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            // DNS Selection Card
            Surface(
                onClick        = onDnsClick,
                shape          = RoundedCornerShape(20.dp),
                color          = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier       = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Dns, null,
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DNS Provider",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            currentProvider.lowercase().replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchEngineSheet(
    onDismiss: () -> Unit,
    currentEngine: String,
    onEngineSelect: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Search Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Choose your preferred search engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            val engines = listOf(
                "DUCKDUCKGO" to ("DuckDuckGo" to "Privacy-focused, no tracking"),
                "BRAVE"      to ("Brave" to "Fast, private results"),
                "GOOGLE"     to ("Google" to "World's most popular search"),
                "BING"       to ("Bing" to "Microsoft's search engine"),
                "STARTPAGE"  to ("Startpage" to "Google results with total privacy"),
                "SWISSCOWS"  to ("Swisscows" to "Family-friendly, private search"),
                "ECOSIA"     to ("Ecosia" to "Plants trees while you search"),
                "META"       to ("All Engines" to "Combined results from multiple sources"),
            )

            engines.forEach { entry ->
                val key = entry.first
                val pair = entry.second
                val name = pair.first
                val desc = pair.second
                val selected = currentEngine == key
                Surface(
                    onClick        = { onEngineSelect(key) },
                    shape          = RoundedCornerShape(16.dp),
                    color          = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier       = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    when(key) {
                                        "GOOGLE" -> Icons.Rounded.Search
                                        "BRAVE" -> Icons.Rounded.Shield
                                        "DUCKDUCKGO" -> Icons.Rounded.VisibilityOff
                                        "ECOSIA" -> Icons.Rounded.Park
                                        "SWISSCOWS" -> Icons.Rounded.FamilyRestroom
                                        "STARTPAGE" -> Icons.Rounded.Lock
                                        else -> Icons.Rounded.Language
                                    },
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                name,
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color      = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DnsSheet(
    onDismiss: () -> Unit,
    currentProvider: String,
    customDns: String,
    onProviderSelect: (String) -> Unit,
    onCustomDnsChange: (String) -> Unit,
    benchmarks: Map<String, Long?> = emptyMap(),
    isBenchmarking: Boolean = false,
    onRunBenchmark: () -> Unit = {},
    onApplyFastest: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DNS over HTTPS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Encrypt DNS lookups to prevent tracking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                IconButton(
                    onClick = onRunBenchmark,
                    enabled = !isBenchmarking,
                    colors = IconButtonDefaults.filledTonalIconButtonColors()
                ) {
                    if (isBenchmarking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Speed, "Benchmark")
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))

            // Fastest / Auto Option
            Surface(
                onClick        = onApplyFastest,
                shape          = RoundedCornerShape(20.dp),
                color          = if (isBenchmarking) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                                 else MaterialTheme.colorScheme.primaryContainer,
                modifier       = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto (Fastest)",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Automatically switch to the lowest latency provider",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    if (isBenchmarking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val providers = listOf(
                "SYSTEM"            to ("System default" to "No encryption"),
                "ADGUARD"           to ("AdGuard" to "Ad + tracking protection"),
                "ADGUARD_FAMILY"    to ("AdGuard Family" to "Adult content filter"),
                "CLOUDFLARE"        to ("Cloudflare" to "Fast, privacy-focused"),
                "CLOUDFLARE_FAMILY" to ("Cloudflare Family" to "Malware + adult filter"),
                "GOOGLE"            to ("Google Public" to "Reliable global anycast"),
                "QUAD9"             to ("Quad9" to "Security-focused"),
                "NEXTDNS"           to ("NextDNS" to "Customisable filtering"),
                "MULLVAD_EXTENDED"  to ("Mullvad Extended" to "Aggressive ad blocking"),
                "CONTROLD"          to ("Control D" to "Flexible filtering"),
                "CLEANBROWSING_SECURITY" to ("CleanBrowsing" to "Hardened security filter"),
                "CUSTOM"            to ("Custom URL" to "Your own DoH resolver"),
            )
            
            providers.forEach { entry ->
                val key = entry.first
                val pair = entry.second
                val name = pair.first
                val desc = pair.second
                val selected = currentProvider == key
                val latency = benchmarks[key.lowercase()]
                
                Surface(
                    onClick        = { onProviderSelect(key) },
                    shape          = RoundedCornerShape(16.dp),
                    color          = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier       = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    name,
                                    style      = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                
                                if (latency != null) {
                                    val color = when {
                                        latency < 50  -> Color(0xFF4CAF50)
                                        latency < 150 -> Color(0xFFFFC107)
                                        else          -> Color(0xFFF44336)
                                    }
                                    Text(
                                        "${latency}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            if (currentProvider == "CUSTOM") {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = customDns,
                    onValueChange = onCustomDnsChange,
                    label         = { Text("DoH URL or Hostname") },
                    placeholder   = { Text("e.g. dns.example.com") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(16.dp),
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultActionsSheet(
    result: SearchResult,
    onDismiss: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Result preview header
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.padding(bottom = 16.dp),
            ) {
                FaviconImage(url = result.url, size = 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(result.displayUrl, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            ActionRow(Icons.Rounded.BookmarkAdd, "Save to bookmarks", onBookmarkToggle)
            ActionRow(Icons.Rounded.Share, "Share link", onShare)
            ActionRow(Icons.Rounded.ContentCopy, "Copy URL", onCopy)
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllBookmarksSheet(
    bookmarks: List<BookmarkEntry>,
    onDismiss: () -> Unit,
    onBookmarkClick: (String) -> Unit,
    onEdit: (BookmarkEntry) -> Unit,
    onDelete: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text(
                "All bookmarks",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier       = Modifier.heightIn(max = 480.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(bookmarks, key = { it.id }) { bm ->
                    Surface(
                        onClick  = { onBookmarkClick(bm.url) },
                        shape    = RoundedCornerShape(16.dp),
                        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier              = Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FaviconImage(url = bm.url, size = 28.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bm.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.MoreVert, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                DropdownMenu(showMenu, { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)) }, onClick = { onEdit(bm); showMenu = false })
                                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(bm.url); showMenu = false })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════
//  ONBOARDING DIALOG
// ══════════════════════════════════════════════════════════

@Composable
private fun OnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
        title = { Text("Private by default", fontWeight = FontWeight.Bold) },
        text  = {
            Text(
                "All searches are routed through an anonymous proxy with built-in ad blocking and DNS-over-HTTPS. No tracking, no profiling.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("Get started") }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

// ══════════════════════════════════════════════════════════
//  ADD / EDIT DIALOG
// ══════════════════════════════════════════════════════════

@Composable
fun QuickLinkDialog(
    titleInitial: String = "",
    urlInitial:   String = "",
    dialogTitle:  String = "Add quick link",
    onDismiss:    () -> Unit,
    onConfirm:    (title: String, url: String) -> Unit,
) {
    var title by remember { mutableStateOf(titleInitial) }
    var url   by remember { mutableStateOf(urlInitial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title  = { Text(dialogTitle, fontWeight = FontWeight.SemiBold) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Title") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp),
                    modifier      = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = url,
                    onValueChange = { url = it },
                    label         = { Text("URL") },
                    placeholder   = { Text("https://…") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp),
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(title.trim(), url.trim()) },
                enabled  = title.isNotBlank() && url.isNotBlank(),
                shape    = RoundedCornerShape(14.dp),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
    )
}