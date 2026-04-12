package com.frerox.toolz.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.data.search.QuickLinkEntry
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.data.search.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onResultClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFirstTime by viewModel.isFirstTime.collectAsState(initial = false)
    val history by viewModel.history.collectAsState(initial = emptyList())
    val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
    val quickLinks by viewModel.quickLinks.collectAsState(initial = emptyList())
    var showBookmarksAll by remember { mutableStateOf(false) }
    var showDnsSheet by remember { mutableStateOf(false) }
    var showAddQuickLink by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntry?>(null) }
    var editingQuickLink by remember { mutableStateOf<QuickLinkEntry?>(null) }

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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SearchBar(
                        query = uiState.query,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onSearch = { 
                            if (it.contains(".") && !it.contains(" ")) {
                                onResultClick(if (it.startsWith("http")) it else "https://$it")
                            } else {
                                viewModel.onSearch(it)
                            }
                        },
                        active = uiState.active,
                        onActiveChange = { viewModel.onActiveChange(it) },
                        placeholder = { Text("Search or type URL", color = MaterialTheme.colorScheme.outline) },
                        leadingIcon = {
                            IconButton(onClick = if (uiState.active) { { viewModel.onActiveChange(false) } } else onBackClick) {
                                Icon(
                                    imageVector = if (uiState.active) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Search,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                        )
                    ) {
                        // Suggestions/History in SearchBar dropdown
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = uiState.isLoading to uiState.results.isEmpty(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "SearchContent"
            ) { (isLoading, resultsEmpty) ->
                if (isLoading) {
                    SearchShimmer()
                } else if (resultsEmpty) {
                    StartPage(
                        history = history,
                        bookmarks = bookmarks,
                        quickLinks = quickLinks,
                        onQueryClick = { viewModel.onSearch(it) },
                        onBookmarkClick = { onResultClick(it) },
                        onDeleteHistory = { viewModel.deleteHistory(it) },
                        onRemoveBookmark = { viewModel.removeBookmark(it) },
                        onEditBookmark = { editingBookmark = it },
                        onClearHistory = { showClearHistoryConfirm = true },
                        adBlockEnabled = uiState.adBlockEnabled,
                        onAdBlockToggle = { viewModel.toggleAdBlock(it) },
                        onAdBlockLongClick = { showDnsSheet = true },
                        dnsProvider = uiState.dnsProvider,
                        onAddQuickLink = { showAddQuickLink = true },
                        onRemoveQuickLink = { viewModel.removeQuickLink(it) },
                        onEditQuickLink = { editingQuickLink = it },
                        onFillQuery = { viewModel.onQueryChange(it) },
                        onSeeAllBookmarks = { showBookmarksAll = true }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.results, key = { it.url }) { result ->
                            SearchResultCard(
                                result = result,
                                onClick = { onResultClick(result.url) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                                    fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                                    placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartPage(
    history: List<SearchHistoryEntry>,
    bookmarks: List<BookmarkEntry>,
    quickLinks: List<QuickLinkEntry>,
    onQueryClick: (String) -> Unit,
    onBookmarkClick: (String) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onEditBookmark: (BookmarkEntry) -> Unit,
    onClearHistory: () -> Unit,
    adBlockEnabled: Boolean,
    onAdBlockToggle: (Boolean) -> Unit,
    onAdBlockLongClick: () -> Unit,
    dnsProvider: String,
    onAddQuickLink: () -> Unit,
    onRemoveQuickLink: (Long) -> Unit,
    onEditQuickLink: (QuickLinkEntry) -> Unit,
    onFillQuery: (String) -> Unit,
    onSeeAllBookmarks: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Toolz Search",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Private & Secure Browsing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                AdBlockControl(
                    enabled = adBlockEnabled, 
                    onToggle = onAdBlockToggle,
                    onLongClick = onAdBlockLongClick,
                    dnsProvider = dnsProvider
                )
            }
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
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            QuickLinksSection(quickLinks, onQueryClick, onAddQuickLink, onRemoveQuickLink, onEditQuickLink)
        }

        if (history.isNotEmpty()) {
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
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Clear All",
                        modifier = Modifier.clickable { onClearHistory() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(24.dp))
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
            item {
                EmptySearchState()
            }
        }
    }
}

@Composable
fun QuickLinksSection(
    quickLinks: List<QuickLinkEntry>,
    onQueryClick: (String) -> Unit,
    onAddQuickLink: () -> Unit,
    onRemoveQuickLink: (Long) -> Unit,
    onEditQuickLink: (QuickLinkEntry) -> Unit
) {
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
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(quickLinks) { link ->
                QuickLinkItem(
                    link,
                    onClick = { onQueryClick(link.url) },
                    onEdit = { onEditQuickLink(link) },
                    onDelete = { onRemoveQuickLink(link.id) }
                )
            }
            if (quickLinks.isEmpty()) {
                item {
                    Surface(
                        onClick = onAddQuickLink,
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(24.dp),
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
fun QuickLinkItem(link: QuickLinkEntry, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(24.dp))
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
                modifier = Modifier.size(36.dp),
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
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* do nothing or maybe just toggle */ },
                onLongClick = onLongClick
            ),
        border = BorderStroke(
            1.dp, 
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield, 
                        null, 
                        tint = if (enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$dnsProvider DNS Protection", 
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (enabled) "Blocking ads and trackers" else "Protection disabled", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled, 
                onCheckedChange = onToggle,
                thumbContent = {
                    if (enabled) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val providers = listOf("DEFAULT", "ADGUARD", "CLOUDFLARE", "GOOGLE", "CUSTOM")
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                "DNS Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            providers.forEach { provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProviderSelect(provider) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentProvider == provider,
                        onClick = { onProviderSelect(provider) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(provider, style = MaterialTheme.typography.bodyLarge)
                        val description = when(provider) {
                            "ADGUARD" -> "Ad-blocking and tracking protection"
                            "CLOUDFLARE" -> "Privacy-focused and fast (1.1.1.1)"
                            "GOOGLE" -> "Fast and reliable (8.8.8.8)"
                            "CUSTOM" -> "Enter your own DNS server"
                            else -> "Default system DNS"
                        }
                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            if (currentProvider == "CUSTOM") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = customDns,
                    onValueChange = onCustomDnsChange,
                    label = { Text("Custom DNS Server") },
                    placeholder = { Text("e.g. dns.adguard.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
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
                    Text("Recent Custom DNS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentDns.forEach { dns ->
                            InputChip(
                                selected = false,
                                onClick = { onCustomDnsChange(dns) },
                                label = { Text(dns) },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookmarkItem(bookmark: BookmarkEntry, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(84.dp)
                .clip(RoundedCornerShape(24.dp))
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
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaviconDisplay(
                    url = result.url,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.displayUrl,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
        }
    }
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
                    shape = RoundedCornerShape(24.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(24.dp)
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun EmptySearchState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Public,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Browse the web anonymously",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
