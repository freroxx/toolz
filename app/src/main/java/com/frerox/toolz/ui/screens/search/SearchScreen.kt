package com.frerox.toolz.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.data.search.QuickLinkEntry
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.ui.components.dragDropItem
import com.frerox.toolz.ui.components.rememberDragDropState
import com.frerox.toolz.ui.components.TabFloatingPills
import com.frerox.toolz.ui.screens.search.components.ExpressiveSearchBar
import com.frerox.toolz.ui.screens.search.components.ExpressiveSearchResultCard
import com.frerox.toolz.ui.screens.search.components.ImprovedSearchShimmer
import com.frerox.toolz.ui.screens.search.components.SearchHero
import com.frerox.toolz.ui.screens.search.components.fadingEdges

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onResultClick: (String) -> Unit,
    onManageTabs: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFirstTime by viewModel.isFirstTime.collectAsState(initial = false)
    val history by viewModel.history.collectAsState(initial = emptyList())
    val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
    val quickLinks by viewModel.quickLinks.collectAsState(initial = emptyList())
    var showBookmarksAll by remember { mutableStateOf(false) }
    var showDnsSheet by remember { mutableStateOf(false) }
    var showSecurityStatus by remember { mutableStateOf(false) }
    var showAddQuickLink by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var longPressedResult by remember { mutableStateOf<SearchResult?>(null) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntry?>(null) }
    var editingQuickLink by remember { mutableStateOf<QuickLinkEntry?>(null) }
    var showSearchSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (longPressedResult != null) {
        ResultOptionsSheet(
            result = longPressedResult!!,
            onDismiss = { longPressedResult = null },
            onBookmarkToggle = { viewModel.toggleBookmark(longPressedResult!!) },
            onQuickAccessAdd = { 
                viewModel.addQuickLink(longPressedResult!!.title, longPressedResult!!.url)
                longPressedResult = null
            },
            onShare = {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, longPressedResult!!.url)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Link"))
                longPressedResult = null
            },
            onCopy = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("URL", longPressedResult!!.url)
                clipboard.setPrimaryClip(clip)
                longPressedResult = null
            }
        )
    }

    if (showSearchSettings) {
        SearchSettingsSheet(
            onDismiss = { showSearchSettings = false },
            currentEngine = uiState.searchEngine,
            onEngineSelect = { viewModel.setSearchEngine(it) },
            adBlockEnabled = uiState.adBlockEnabled,
            onAdBlockToggle = { viewModel.toggleAdBlock(it) },
            currentDns = uiState.dnsProvider,
            onDnsClick = { showDnsSheet = true; showSearchSettings = false },
            safeSearch = uiState.safeSearch,
            onSafeSearchToggle = { viewModel.setSafeSearch(it) },
            region = uiState.region,
            onRegionChange = { viewModel.setRegion(it) },
            customEngineUrl = uiState.customEngineUrl,
            onCustomEngineUrlChange = { viewModel.setCustomEngineUrl(it) },
            isIncognito = uiState.isIncognito,
            onToggleIncognito = { viewModel.toggleIncognito(!uiState.isIncognito) },
            autofillEnabled = uiState.searchAutofillEnabled,
            onAutofillToggle = { enabled -> viewModel.toggleAutofill(enabled) }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear all your search history? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDnsSheet) {
        DnsSettingsSheet(
            onDismiss = { showDnsSheet = false },
            currentProvider = uiState.dnsProvider,
            customDns = uiState.customDns,
            recentDns = uiState.recentDns,
            onProviderSelect = { viewModel.setDnsProvider(it) },
            onCustomDnsChange = { viewModel.setCustomDns(it) },
            onRemoveRecentDns = { viewModel.removeRecentDns(it) }
        )
    }

    if (showSecurityStatus) {
        SecurityStatusSheet(
            onDismiss = { showSecurityStatus = false },
            currentProvider = uiState.dnsProvider,
            adBlockEnabled = uiState.adBlockEnabled,
            isIncognito = uiState.isIncognito,
            onAdBlockToggle = { enabled -> viewModel.toggleAdBlock(enabled) },
            onIncognitoToggle = { enabled -> viewModel.toggleIncognito(enabled) },
            onPresetSelect = { preset -> viewModel.setSecurityPreset(preset) },
            onDnsProviderSelect = { provider -> viewModel.setDnsProvider(provider) },
            autofillEnabled = uiState.searchAutofillEnabled,
            onAutofillToggle = { enabled -> viewModel.toggleAutofill(enabled) }
        )
    }

    if (editingBookmark != null) {
        AddQuickLinkDialog(
            titleInitial = editingBookmark?.title ?: "",
            urlInitial = editingBookmark?.url ?: "",
            onDismiss = { editingBookmark = null },
            onConfirm = { title, url ->
                editingBookmark?.let { viewModel.updateBookmark(it.id, title, url) }
                editingBookmark = null
            },
            dialogTitle = "Edit Favorite",
            confirmButtonText = "Save"
        )
    }

    if (editingQuickLink != null) {
        AddQuickLinkDialog(
            titleInitial = editingQuickLink?.title ?: "",
            urlInitial = editingQuickLink?.url ?: "",
            onDismiss = { editingQuickLink = null },
            onConfirm = { title, url ->
                editingQuickLink?.let { viewModel.updateQuickLink(it.id, title, url) }
                editingQuickLink = null
            },
            dialogTitle = "Edit Quick Access",
            confirmButtonText = "Save"
        )
    }

    if (showAddQuickLink) {
        AddQuickLinkDialog(
            onDismiss = { showAddQuickLink = false },
            onConfirm = { title, url ->
                viewModel.addQuickLink(title, url)
                showAddQuickLink = false
            }
        )
    }

    if (showBookmarksAll) {
        AlertDialog(
            onDismissRequest = { showBookmarksAll = false },
            title = { Text("Favorites") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks) { bookmark ->
                        ListItem(
                            headlineContent = { Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(bookmark.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { FaviconDisplay(url = bookmark.url, modifier = Modifier.size(40.dp)) },
                            modifier = Modifier.clickable { 
                                viewModel.openTab(bookmark.url)
                                onResultClick(bookmark.url)
                                showBookmarksAll = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksAll = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (isFirstTime) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFirstTime() },
            title = { Text("Search with Toolz") },
            text = { Text("Toolz Search respects your privacy by using an anonymous proxy for your web searches. Happy browsing!") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissFirstTime() }) {
                    Text("Got it")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = uiState.results.isNotEmpty() || uiState.active || uiState.isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(vertical = 4.dp)
                    ) {
                        ExpressiveSearchBar(
                            query = uiState.query,
                            onQueryChange = { viewModel.onQueryChange(it) },
                            onSearch = {
                                val trimmed = it.trim()
                                if ((trimmed.contains(".") || trimmed.contains("localhost") || trimmed.startsWith("http")) && !trimmed.contains(" ")) {
                                    val targetUrl = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
                                    viewModel.openTab(targetUrl)
                                    onResultClick(targetUrl)
                                } else {
                                    viewModel.onSearch(trimmed)
                                }
                            },
                            active = uiState.active,
                            onActiveChange = { viewModel.onActiveChange(it) },
                            onBackClick = if (uiState.results.isNotEmpty()) { { viewModel.onQueryChange(""); viewModel.onSearch("") } } else onBackClick,
                            onSettingsClick = { showSearchSettings = true },
                            isIncognito = uiState.isIncognito
                        ) {
                            // Suggestions/History in SearchBar dropdown
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                if (uiState.suggestions.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Suggestions",
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(uiState.suggestions) { suggestion ->
                                        ListItem(
                                            headlineContent = { Text(suggestion) },
                                            leadingContent = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.outline) },
                                            modifier = Modifier.clickable { viewModel.onSearch(suggestion) },
                                            trailingContent = {
                                                IconButton(onClick = { viewModel.onQueryChange(suggestion) }) {
                                                    Icon(Icons.Default.ArrowOutward, null, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        )
                                    }
                                }

                                val filteredHistory = history.filter { it.query.contains(uiState.query, ignoreCase = true) }
                                if (filteredHistory.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Recent Searches",
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(filteredHistory) { entry ->
                                        ListItem(
                                            headlineContent = { Text(entry.query) },
                                            leadingContent = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.outline) },
                                            modifier = Modifier.clickable { viewModel.onSearch(entry.query) },
                                            trailingContent = {
                                                IconButton(onClick = { viewModel.deleteHistory(entry.id) }) {
                                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = Triple(uiState.isLoading, uiState.results.isEmpty(), uiState.query.isNotEmpty() || uiState.error != null),
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "SearchContent"
            ) { (isLoading, resultsEmpty, queryNotEmpty) ->
                if (isLoading) {
                    ImprovedSearchShimmer()
                } else if (uiState.error != null) {
                    EmptySearchState(
                        message = uiState.error ?: "No results found",
                        title = "Search Error",
                        onRetry = { viewModel.onSearch(uiState.query) }
                    )
                } else if (resultsEmpty && queryNotEmpty) {
                    EmptySearchState(onRetry = { viewModel.onSearch(uiState.query) })
                } else if (resultsEmpty) {
                    StartPage(
                        history = history,
                        bookmarks = bookmarks,
                        quickLinks = quickLinks,
                        uiState = uiState,
                        onQueryClick = { 
                            viewModel.openTab(it)
                            onResultClick(it) 
                        },
                        onBookmarkClick = { 
                            viewModel.openTab(it)
                            onResultClick(it) 
                        },
                        onDeleteHistory = { viewModel.deleteHistory(it) },
                        onRemoveBookmark = { viewModel.removeBookmark(it) },
                        onEditBookmark = { editingBookmark = it },
                        onClearHistory = { showClearHistoryConfirm = true },
                        adBlockEnabled = uiState.adBlockEnabled,
                        onAdBlockToggle = { viewModel.toggleAdBlock(it) },
                        dnsProvider = uiState.dnsProvider,
                        onAddQuickLink = { showAddQuickLink = true },
                        onRemoveQuickLink = { viewModel.removeQuickLink(it) },
                        onEditQuickLink = { editingQuickLink = it },
                        onFillQuery = { viewModel.onQueryChange(it) },
                        onSeeAllBookmarks = { showBookmarksAll = true },
                        onReorder = { from, to -> viewModel.reorderQuickLinks(from, to) },
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onSearch = { viewModel.onSearch(it) },
                        onActiveChange = { viewModel.onActiveChange(it) },
                        onBackClick = onBackClick,
                        onSettingsClick = { showSearchSettings = true },
                        onSecurityStatusClick = { showSecurityStatus = true },
                        onTabsClick = onManageTabs,
                        onCloseTab = { viewModel.closeTab(it) },
                        onTabClick = { id, url ->
                            viewModel.switchTab(id)
                            onResultClick(url)
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .fadingEdges(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(uiState.results, key = { index, result -> "${result.url}_$index" }) { index, result ->
                            ExpressiveSearchResultCard(
                                result = result,
                                onClick = { 
                                    viewModel.openTab(result.url)
                                    onResultClick(result.url) 
                                },
                                onLongClick = { longPressedResult = result },
                                onBookmarkClick = { viewModel.toggleBookmark(result) },
                                onShareClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, result.url)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Link"))
                                },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(400, delayMillis = index * 50),
                                    placementSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                )
                            )
                        }

                        if (uiState.canLoadMore) {
                            if (uiState.results.size <= 100) {
                                item {
                                    LaunchedEffect(uiState.results.size) {
                                        viewModel.loadMore()
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            } else {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (uiState.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            Button(
                                                onClick = { viewModel.loadMore() },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                                            ) {
                                                Text("Load more")
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "Results tend to be less accurate as you load more",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (uiState.results.isNotEmpty() && !uiState.isLoading) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    FilledTonalButton(onClick = { onBackClick() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Go Back")
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "You've reached the bottom. Congrats, web explorer !",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TabFloatingPills(
                tabs = uiState.tabs,
                activeTabId = uiState.activeTabId,
                onTabClick = { id, url ->
                    viewModel.switchTab(id)
                    onResultClick(url)
                },
                onNewTab = {
                    viewModel.onQueryChange("")
                    viewModel.onActiveChange(true)
                },
                onManageTabs = onManageTabs,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun StartPage(
    history: List<SearchHistoryEntry>,
    bookmarks: List<BookmarkEntry>,
    quickLinks: List<QuickLinkEntry>,
    uiState: SearchUiState,
    onQueryClick: (String) -> Unit,
    onBookmarkClick: (String) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onEditBookmark: (BookmarkEntry) -> Unit,
    onClearHistory: () -> Unit,
    adBlockEnabled: Boolean,
    onAdBlockToggle: (Boolean) -> Unit,
    onSecurityStatusClick: () -> Unit,
    dnsProvider: String,
    onAddQuickLink: () -> Unit,
    onRemoveQuickLink: (Long) -> Unit,
    onEditQuickLink: (QuickLinkEntry) -> Unit,
    onFillQuery: (String) -> Unit,
    onSeeAllBookmarks: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onCloseTab: (String) -> Unit,
    onTabClick: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Return to Dashboard Button - Fixed Top Left
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                .zIndex(10f)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Dashboard", modifier = Modifier.size(20.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fadingEdges(),
            contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SearchHero(
                    userName = uiState.userName
                )
            }

            item {
                ExpressiveSearchBar(
                    query = uiState.query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    active = false,
                    onActiveChange = onActiveChange,
                    onBackClick = onBackClick,
                    onSettingsClick = onSettingsClick,
                    isIncognito = uiState.isIncognito,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            item {
                SecurityStatusCard(
                    dnsProvider = dnsProvider,
                    adBlockEnabled = adBlockEnabled,
                    isIncognito = uiState.isIncognito,
                    onClick = onSecurityStatusClick
                )
            }

        if (bookmarks.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Favorites",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "See All",
                            modifier = Modifier.clickable { onSeeAllBookmarks() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(bookmarks) { bookmark ->
                            BookmarkItem(
                                bookmark,
                                onClick = { onBookmarkClick(bookmark.url) },
                                onEdit = { onEditBookmark(bookmark) },
                                onDelete = { onRemoveBookmark(bookmark.url) }
                            )
                        }
                    }
                }
            }
        }

        item {
            QuickLinksSection(quickLinks, onQueryClick, onAddQuickLink, onRemoveQuickLink, onEditQuickLink, onReorder)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onTabsClick, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Layers, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    Text(
                        "Clear All",
                        modifier = Modifier.clickable { onClearHistory() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Search,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "There's nothing here, start your first search!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(history.take(8)) { entry ->
                ListItem(
                    headlineContent = { Text(entry.query, style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = { 
                        FaviconDisplay(
                            url = if (entry.query.startsWith("http")) entry.query else "https://duckduckgo.com",
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onQueryClick(entry.query) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onFillQuery(entry.query) }) {
                                Icon(Icons.Default.ArrowOutward, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteHistory(entry.id) }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                )
            }
        }

        if (history.isEmpty() && bookmarks.isEmpty()) {
            // item {
            //     EmptySearchState()
            // }
        }
    }
}
}

@Composable
fun SecurityStatusCard(
    dnsProvider: String,
    adBlockEnabled: Boolean,
    isIncognito: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (adBlockEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncognito) Icons.Rounded.VpnLock else Icons.Rounded.Security,
                    contentDescription = null,
                    tint = if (adBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Security Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildString {
                        if (adBlockEnabled) append("Shield Active") else append("Shield Off")
                        append(" • ")
                        append(dnsProvider)
                        if (isIncognito) append(" • Private")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultOptionsSheet(
    result: SearchResult,
    onDismiss: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onQuickAccessAdd: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FaviconDisplay(url = result.url, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        result.displayUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultOptionItem(
                    title = "Copy Link",
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopy
                )
                ResultOptionItem(
                    title = "Share Link",
                    icon = Icons.Default.Share,
                    onClick = onShare
                )
                ResultOptionItem(
                    title = "Add to Favorites",
                    icon = Icons.Default.BookmarkBorder,
                    onClick = { onBookmarkToggle(); onDismiss() }
                )
                ResultOptionItem(
                    title = "Add to Quick Access",
                    icon = Icons.Default.Bolt,
                    onClick = onQuickAccessAdd
                )
            }
        }
    }
}

@Composable
fun ResultOptionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityStatusSheet(
    onDismiss: () -> Unit,
    currentProvider: String,
    adBlockEnabled: Boolean,
    isIncognito: Boolean,
    onAdBlockToggle: (Boolean) -> Unit,
    onIncognitoToggle: (Boolean) -> Unit,
    onPresetSelect: (String) -> Unit,
    onDnsProviderSelect: (String) -> Unit,
    autofillEnabled: Boolean,
    onAutofillToggle: (Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Security Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("LOW", "BASIC", "MAX").forEach { preset ->
                    val isSelected = when (preset) {
                        "LOW" -> !adBlockEnabled || (adBlockEnabled && currentProvider == "ADGUARD" && !isIncognito)
                        "BASIC" -> adBlockEnabled && currentProvider == "CLOUDFLARE" && !isIncognito
                        "MAX" -> adBlockEnabled && currentProvider == "QUAD9" && isIncognito
                        else -> false
                    }
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelect(preset) },
                        label = { Text(preset) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsToggleItem(
                    title = "Ad & Tracker Blocker",
                    subtitle = "Blocks known ad domains and trackers",
                    checked = adBlockEnabled,
                    onCheckedChange = onAdBlockToggle,
                    icon = Icons.Rounded.Shield
                )
                SettingsToggleItem(
                    title = "Incognito Mode",
                    subtitle = "Browse without saving history",
                    checked = isIncognito,
                    onCheckedChange = onIncognitoToggle,
                    icon = Icons.Rounded.VisibilityOff
                )
                SettingsToggleItem(
                    title = "Password Vault Autofill",
                    subtitle = "Automatically fill credentials from your vault",
                    checked = autofillEnabled,
                    onCheckedChange = onAutofillToggle,
                    icon = Icons.Rounded.VpnKey
                )
            }

            // DNS Selection
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "DNS Provider",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val providers = listOf("SYSTEM", "GOOGLE", "CLOUDFLARE", "ADGUARD", "QUAD9")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(providers) { provider ->
                        SuggestionChip(
                            onClick = { onDnsProviderSelect(provider) },
                            label = { Text(provider) },
                            border = if (currentProvider == provider) 
                                     BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                                     else SuggestionChipDefaults.suggestionChipBorder(enabled = true)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun QuickLinksSection(
    quickLinks: List<QuickLinkEntry>,
    onQueryClick: (String) -> Unit,
    onAddQuickLink: () -> Unit,
    onRemoveQuickLink: (Long) -> Unit,
    onEditQuickLink: (QuickLinkEntry) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { from, to ->
        onReorder(from, to)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Quick Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                onClick = onAddQuickLink,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Add Quick Link", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
        }
        
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(quickLinks, key = { _, link -> link.id }) { index, link ->
                QuickLinkItem(
                    link,
                    onClick = { onQueryClick(link.url) },
                    onEdit = { onEditQuickLink(link) },
                    onDelete = { onRemoveQuickLink(link.id) },
                    modifier = Modifier
                        .dragDropItem(index, dragDropState)
                        .zIndex(if (dragDropState.draggingItemIndex == index) 1f else 0f)
                )
            }
            if (quickLinks.isEmpty()) {
                item {
                    Surface(
                        onClick = onAddQuickLink,
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuickLinkItem(
    link: QuickLinkEntry, 
    onClick: () -> Unit, 
    onEdit: () -> Unit, 
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(vertical = 4.dp)
        ) {
            FaviconDisplay(
                url = link.url,
                modifier = Modifier.size(60.dp),
                title = link.title
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                link.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    showMenu = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

@Composable
fun FaviconDisplay(url: String, modifier: Modifier = Modifier, title: String? = null) {
    val domain = try {
        val host = java.net.URI(url).host ?: ""
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (e: Exception) {
        ""
    }
    
    val faviconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$domain"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(if (modifier == Modifier.size(60.dp) || modifier == Modifier.size(64.dp)) 32.dp 
                                           else if (modifier == Modifier.size(26.dp) || modifier == Modifier.size(22.dp)) 14.dp
                                           else 18.dp),
                contentScale = ContentScale.Fit
            )
            // Fallback if domain is empty
            if (domain.isEmpty() && !title.isNullOrEmpty()) {
                Text(
                    title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AdBlockControl(
    enabled: Boolean, 
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
    dnsProvider: String
) {
    Surface(
        onClick = { onToggle(!enabled) },
        shape = RoundedCornerShape(28.dp),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp, 
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth().height(84.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (enabled) Icons.Rounded.Shield else Icons.Rounded.ShieldMoon,
                        null,
                        modifier = Modifier.size(28.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Shield Protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (enabled) "Blocking ads • $dnsProvider" else "Tap to secure your search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                thumbContent = if (enabled) {
                    { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DnsSettingsSheet(
    onDismiss: () -> Unit,
    currentProvider: String,
    customDns: String,
    recentDns: List<String>,
    onProviderSelect: (String) -> Unit,
    onCustomDnsChange: (String) -> Unit,
    onRemoveRecentDns: (String) -> Unit
) {
    val providers = listOf(
        "DEFAULT", "ADGUARD", "ADGUARD_FAMILY", 
        "CLOUDFLARE", "CLOUDFLARE_FAMILY", 
        "GOOGLE", "QUAD9", "OPENDNS", "CLEANBROWSING", "CUSTOM"
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.fillMaxWidth(),
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp)
                .fadingEdges()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "DNS & Privacy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Secure your connection and block ads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            providers.forEach { provider ->
                val isSelected = currentProvider == provider
                Surface(
                    onClick = { onProviderSelect(provider) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(
                        1.dp, 
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = getDnsColor(provider).copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    getDnsIcon(provider), 
                                    null, 
                                    tint = getDnsColor(provider),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                provider.replace("_", " "), 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                getDnsDescription(provider), 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { onProviderSelect(provider) }
                        )
                    }
                }
            }
            
            if (currentProvider == "CUSTOM") {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = customDns,
                    onValueChange = onCustomDnsChange,
                    label = { Text("DoH Endpoint URL") },
                    placeholder = { Text("https://dns.google/dns-query") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    trailingIcon = {
                        if (customDns.isNotEmpty()) {
                            IconButton(onClick = { onCustomDnsChange("") }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                )

                if (recentDns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Recently Used", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentDns.forEach { dns ->
                            InputChip(
                                selected = false,
                                onClick = { onCustomDnsChange(dns) },
                                label = { Text(dns, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp).clickable { onRemoveRecentDns(dns) }
                                    )
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
fun getDnsIcon(provider: String): ImageVector {
    return when(provider) {
        "ADGUARD", "ADGUARD_FAMILY" -> Icons.Default.Shield
        "CLOUDFLARE", "CLOUDFLARE_FAMILY" -> Icons.Default.FlashOn
        "GOOGLE" -> Icons.Default.GTranslate
        "QUAD9" -> Icons.Default.Lock
        "OPENDNS" -> Icons.Default.Security
        "CLEANBROWSING" -> Icons.Default.FilterAlt
        "CUSTOM" -> Icons.Default.Edit
        else -> Icons.Default.Settings
    }
}

@Composable
fun getDnsColor(provider: String): Color {
    return when(provider) {
        "ADGUARD", "ADGUARD_FAMILY" -> Color(0xFF4CAF50)
        "CLOUDFLARE", "CLOUDFLARE_FAMILY" -> Color(0xFFF6821F)
        "GOOGLE" -> Color(0xFF4285F4)
        "QUAD9" -> Color(0xFFD32F2F)
        "OPENDNS" -> Color(0xFF0070BA)
        "CLEANBROWSING" -> Color(0xFF9C27B0)
        "CUSTOM" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
}

fun getDnsDescription(provider: String): String {
    return when(provider) {
        "ADGUARD" -> "Standard ad-blocking and trackers"
        "ADGUARD_FAMILY" -> "Ad-blocking + Adult content filter"
        "CLOUDFLARE" -> "Lightning fast privacy (1.1.1.1)"
        "CLOUDFLARE_FAMILY" -> "Cloudflare + Malware/Adult filter"
        "GOOGLE" -> "Fast, reliable global DNS (8.8.8.8)"
        "QUAD9" -> "Enhanced security against malware"
        "OPENDNS" -> "Reliable phishing protection"
        "CLEANBROWSING" -> "Strict family-safe filtering"
        "CUSTOM" -> "Use a custom DNS-over-HTTPS endpoint"
        else -> "System default DNS settings"
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookmarkItem(bookmark: BookmarkEntry, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(84.dp)
                .clip(RoundedCornerShape(32.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(vertical = 8.dp)
        ) {
            FaviconDisplay(
                url = bookmark.url,
                modifier = Modifier.size(64.dp),
                title = bookmark.title
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                bookmark.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    showMenu = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

@Composable
fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ExpressiveSearchResultCard(result, onClick, onLongClick, onBookmarkClick, onShareClick, modifier)
}

@Composable
fun AddQuickLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    titleInitial: String = "",
    urlInitial: String = "",
    dialogTitle: String = "Add Quick Access",
    confirmButtonText: String = "Add"
) {
    var title by remember { mutableStateOf(titleInitial) }
    var url by remember { mutableStateOf(urlInitial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(32.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (title.isNotBlank() && url.isNotBlank()) {
                        onConfirm(title, if (url.startsWith("http")) url else "https://$url")
                    }
                },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SearchShimmer() {
    ImprovedSearchShimmer()
}

@Composable
fun EmptySearchState(
    message: String = "We couldn't find anything matching your search. Try different keywords or check your connection.",
    title: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (message.contains("found", ignoreCase = true)) Icons.Default.SearchOff else Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            title ?: if (message.contains("found", ignoreCase = true)) "No results found" else "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Try Again", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSettingsSheet(
    onDismiss: () -> Unit,
    currentEngine: String,
    onEngineSelect: (String) -> Unit,
    adBlockEnabled: Boolean,
    onAdBlockToggle: (Boolean) -> Unit,
    currentDns: String,
    onDnsClick: () -> Unit,
    safeSearch: Boolean,
    onSafeSearchToggle: (Boolean) -> Unit,
    region: String,
    onRegionChange: (String) -> Unit,
    customEngineUrl: String,
    onCustomEngineUrlChange: (String) -> Unit,
    isIncognito: Boolean,
    onToggleIncognito: () -> Unit,
    autofillEnabled: Boolean,
    onAutofillToggle: (Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Search Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Expressive Cards for settings
            SettingsExpressiveCard(
                title = "Incognito Mode",
                subtitle = "Private browsing session",
                icon = Icons.Rounded.VpnLock,
                trailingContent = {
                    Switch(checked = isIncognito, onCheckedChange = { onToggleIncognito() })
                }
            )

            SettingsExpressiveCard(
                title = "Vault Autofill",
                subtitle = "Fill credentials from your vault",
                icon = Icons.Rounded.VpnKey,
                trailingContent = {
                    Switch(checked = autofillEnabled, onCheckedChange = onAutofillToggle)
                }
            )

            SettingsExpressiveCard(
                title = "Search Engine",
                subtitle = "Choose your preferred engine",
                icon = Icons.Rounded.Search,
                content = {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        listOf("META", "GOOGLE", "DUCKDUCKGO", "BING", "CUSTOM").forEach { engine ->
                            val isSelected = currentEngine == engine
                            Surface(
                                onClick = { onEngineSelect(engine) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    RadioButton(selected = isSelected, onClick = { onEngineSelect(engine) })
                                    Text(
                                        if (engine == "META") "Meta Search (Deep Search)" else engine.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                        
                        if (currentEngine == "CUSTOM") {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customEngineUrl,
                                onValueChange = onCustomEngineUrlChange,
                                label = { Text("Custom Engine URL") },
                                placeholder = { Text("https://example.com/search?q={query}") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                supportingText = { Text("Use {query} as placeholder") },
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            )

            SettingsExpressiveCard(
                title = "Safe Search",
                subtitle = "Filter explicit content",
                icon = Icons.Rounded.Security,
                trailingContent = {
                    Switch(checked = safeSearch, onCheckedChange = onSafeSearchToggle)
                }
            )

            SettingsExpressiveCard(
                title = "Region & Content",
                subtitle = "Search results local focus",
                icon = Icons.Rounded.Public,
                content = {
                    val regions = listOf(
                        "wt-wt" to "All Regions",
                        "us-en" to "United States",
                        "uk-en" to "United Kingdom",
                        "fr-fr" to "France",
                        "de-de" to "Germany",
                        "jp-jp" to "Japan"
                    )
                    var expandedRegion by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(
                            onClick = { expandedRegion = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(regions.find { it.first == region }?.second ?: "Default", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = expandedRegion, onDismissRequest = { expandedRegion = false }) {
                            regions.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { 
                                        onRegionChange(code)
                                        expandedRegion = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            SettingsExpressiveCard(
                title = "AdBlock Plus",
                subtitle = "Block annoying ads and trackers",
                icon = Icons.Rounded.Shield,
                trailingContent = {
                    Switch(checked = adBlockEnabled, onCheckedChange = onAdBlockToggle)
                }
            )

            SettingsExpressiveCard(
                title = "DNS Provider",
                subtitle = currentDns,
                icon = Icons.Rounded.Dns,
                onClick = onDnsClick,
                trailingContent = {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            )
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsExpressiveCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailingContent?.invoke()
            }
            content?.let {
                it()
            }
        }
    }
}
