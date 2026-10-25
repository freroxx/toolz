package com.frerox.toolz.ui.screens.news

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.R
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveRefreshIndicator
import com.frerox.toolz.ui.components.ExpressiveSearchField
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.components.stripMarkdown
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** Filter chips share the priority codes of the engine. */
private val NEWS_FILTERS = listOf("all", "critical", "feature", "fix", "promo", "info")

@Composable
private fun filterLabel(priority: String): String = when (priority) {
    "all" -> stringResource(R.string.st_News_filter_all)
    "critical" -> stringResource(R.string.st_News_filter_critical)
    "feature" -> stringResource(R.string.st_News_filter_feature)
    "fix" -> stringResource(R.string.st_News_filter_fix)
    "promo" -> stringResource(R.string.st_News_filter_promo)
    else -> stringResource(R.string.st_News_filter_info)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzNewsScreen(
    onBack: () -> Unit,
    highlightNewsId: String? = null,
    viewModel: NewsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    val items by viewModel.history.collectAsState()
    val loading by viewModel.historyLoading.collectAsState()
    val end by viewModel.historyEnd.collectAsState()
    val seenIds by viewModel.seenIds.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("all") }
    var notifDenials by rememberSaveable { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val chipsState = rememberLazyListState()
    val chipsFadeStart by remember { derivedStateOf { chipsState.canScrollBackward } }
    val chipsFadeEnd by remember { derivedStateOf { chipsState.canScrollForward } }
    val keyboard = LocalSoftwareKeyboardController.current

    // Android 13+ notification permission handling.
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT < 33) true else try {
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) { true }
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
        if (!granted) notifDenials++
    }

    val filteredItems = remember(items, priority) {
        if (priority == "all") items else items.filter { it.priority == priority }
    }
    val listing = searchResults ?: filteredItems

    val historyError by viewModel.historyError.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    LaunchedEffect(query, priority) {
        viewModel.requestSearch(query, priority)
    }

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
    }

    // Seen-on-viewport tracking.
    LaunchedEffect(listState, listing) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.map { it.index }
        }.distinctUntilChanged().collect { indices ->
            val ids = indices.mapNotNull { i -> listing.getOrNull(i - 1)?.id }
            if (ids.isNotEmpty()) viewModel.markSeenOnVisible(ids)
        }
    }

    // Incremental pagination.
    LaunchedEffect(listState, listing) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            if (last == null) -1 else last - total
        }
            .distinctUntilChanged()
            .collect { distanceFromEnd ->
                if (searchResults == null && distanceFromEnd >= -3) viewModel.loadMore()
            }
    }

    // Notification deep-link scrolling.
    var highlightFx by remember { mutableStateOf(highlightNewsId) }
    LaunchedEffect(highlightNewsId, listing, loading, end) {
        if (highlightNewsId == null) return@LaunchedEffect
        val index = listing.indexOfFirst { it.id == highlightNewsId }
        if (index >= 0) {
            try { listState.animateScrollToItem(index + 1) } catch (_: Exception) { }
            delay(2_000L)
            highlightFx = null
        } else if (!end && !loading && searchResults == null) {
            viewModel.loadMore()
        }
    }

    val pullState = rememberPullToRefreshState()

    Scaffold(
        modifier = Modifier.toolzBackground(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                ExpressiveTopAppBar(
                    title = stringResource(R.string.st_News_title),
                    subtitle = stringResource(R.string.st_News_subtitle),
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.click()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_News_back))
                        }
                    },
                    actions = {
                        val syncRotation = if (syncing) {
                            val infiniteTransition = rememberInfiniteTransition(label = "newsSyncRotation")
                            infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "syncRotationAngle"
                            )
                        } else null
                        IconButton(onClick = {
                            haptic.click()
                            viewModel.refreshNow()
                        }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.st_News_refresh),
                                modifier = if (syncRotation != null) {
                                    Modifier.graphicsLayer { rotationZ = syncRotation.value }
                                } else Modifier
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { viewModel.refreshNow() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                ExpressiveRefreshIndicator(
                    state = pullState,
                    isRefreshing = syncing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (items.isEmpty() && loading) {
                NewsSkeletonList()
            } else if (items.isEmpty() && historyError != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.padding(22.dp).size(36.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            stringResource(R.string.st_News_load_failed),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.st_News_sync_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        ToolzExpressiveButton(
                            onClick = { viewModel.refreshNow() },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.st_News_retry), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        ) {
                            Icon(
                                Icons.Rounded.Newspaper,
                                contentDescription = null,
                                modifier = Modifier.padding(22.dp).size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            stringResource(R.string.st_News_empty_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.st_News_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        ToolzExpressiveButton(
                            onClick = { viewModel.refreshNow() },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.st_News_refresh), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 12.dp, bottom = 100.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "news_tools") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (historyError != null) {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.st_News_sync_failed),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                        ToolzExpressiveTextButton(onClick = { viewModel.refreshNow() }) {
                                            Text(stringResource(R.string.st_News_retry), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            if (searching) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        stringResource(R.string.st_News_searching),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!notifGranted && Build.VERSION.SDK_INT >= 33) {
                                val afterDenial = notifDenials >= 1
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.Notifications,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.st_News_notifications_off_title),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                stringResource(
                                                    if (afterDenial) R.string.st_News_notifications_denied_body
                                                    else R.string.st_News_notifications_off_body
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                        ToolzExpressiveButton(
                                            onClick = {
                                                haptic.click()
                                                if (afterDenial) {
                                                    try {
                                                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                        })
                                                    } catch (_: Exception) { }
                                                } else {
                                                    try { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                                                    catch (_: Exception) { }
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (afterDenial) R.string.st_News_notifications_open_settings
                                                    else R.string.st_News_notifications_enable
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            ExpressiveSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.st_News_search_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                haptic.click()
                                                query = ""
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.st_News_search_clear),
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onSearch = { keyboard?.hide() },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            LazyRow(
                                state = chipsState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (chipsFadeStart || chipsFadeEnd) {
                                            Modifier.horizontalFadingEdges(
                                                left = if (chipsFadeStart) 16.dp else 0.dp,
                                                right = if (chipsFadeEnd) 16.dp else 0.dp
                                            )
                                        } else Modifier
                                    )
                            ) {
                                items(NEWS_FILTERS) { p ->
                                    ExpressiveFilterChip(
                                        selected = priority == p,
                                        onClick = { priority = p },
                                        leadingIcon = {
                                            val icon = when (p) {
                                                "critical" -> Icons.Rounded.Warning
                                                "feature" -> Icons.Rounded.AutoAwesome
                                                "fix" -> Icons.Rounded.Build
                                                "promo" -> Icons.Rounded.LocalOffer
                                                "info" -> Icons.Rounded.Campaign
                                                else -> Icons.Rounded.Newspaper
                                            }
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                filterLabel(p),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (priority == p) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }
                            val syncDelta = NewsTime.relativeShort(lastSync, now)
                            val syncStamp = when {
                                lastSync <= 0L -> null
                                syncDelta.isNotEmpty() -> stringResource(R.string.st_News_last_synced, syncDelta)
                                else -> {
                                    val syncDate = NewsTime.newsDate(
                                        lastSync,
                                        now,
                                        stringResource(R.string.st_News_today),
                                        stringResource(R.string.st_News_yesterday)
                                    )
                                    if (syncDate.isEmpty()) null
                                    else stringResource(R.string.st_News_last_synced_date, syncDate)
                                }
                            }
                            if (syncStamp != null) {
                                Text(
                                    syncStamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (searchResults != null && listing.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            stringResource(R.string.st_News_no_matches),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        if (query.isNotEmpty() || priority != "all") {
                                            ToolzExpressiveTextButton(
                                                onClick = {
                                                    query = ""
                                                    priority = "all"
                                                }
                                            ) {
                                                Text(stringResource(R.string.st_News_search_clear), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(listing, key = { it.id }, contentType = { "news_card" }) { item ->
                        NewsHistoryCard(
                            item = item,
                            highlighted = item.id == highlightFx,
                            seen = item.id in seenIds,
                            now = now,
                            onAction = { url -> NewsLinks.open(context, url) },
                            onShare = { shareItem -> NewsLinks.share(context, shareItem) }
                        )
                    }
                    if (!end && searchResults == null) {
                        item(key = "load_more") {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                else ToolzExpressiveTextButton(onClick = { viewModel.loadMore() }) {
                                    Text(stringResource(R.string.st_News_load_more), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pulsing placeholder shown while the first page loads — no bare spinner. */
@Composable
fun NewsSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { NewsSkeletonCard() }
    }
}

@Composable
private fun NewsSkeletonCard() {
    val performanceMode = LocalPerformanceMode.current
    val alpha = if (performanceMode) {
        0.6f
    } else {
        val transition = rememberInfiniteTransition(label = "newsSkeleton")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "newsSkeletonAlpha"
        ).value
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
                    modifier = Modifier.size(width = 64.dp, height = 18.dp)
                ) {}
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
                    modifier = Modifier.size(width = 90.dp, height = 12.dp)
                ) {}
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
                modifier = Modifier.size(width = 220.dp, height = 18.dp)
            ) {}
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth().height(12.dp)
            ) {}
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth(0.6f).height(12.dp)
            ) {}
        }
    }
}

@Composable
fun NewsHistoryCard(
    item: NewsEntity,
    highlighted: Boolean,
    seen: Boolean,
    now: Long = System.currentTimeMillis(),
    onAction: (String) -> Unit,
    onShare: (NewsEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val todayLabel = stringResource(R.string.st_News_today)
    val yesterdayLabel = stringResource(R.string.st_News_yesterday)
    val date = remember(item.publishAt, now, todayLabel, yesterdayLabel) {
        NewsTime.newsDate(item.publishAt, now, todayLabel, yesterdayLabel)
    }
    val isNew = remember(item.publishAt, now) { NewsTime.isRecent(item.publishAt, now) }
    val expired = remember(item.expiresAt, now) { NewsTime.isExpired(item.expiresAt, now) }

    var bodyOverflows by rememberSaveable(item.id) { mutableStateOf(false) }

    val cardColor by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "newsHighlight"
    )
    var imageState by remember(item.id) { mutableStateOf<AsyncImagePainter.State?>(null) }
    val imageBroken = imageState is AsyncImagePainter.State.Error
    val imageModel = remember(item.imageUrl, item.id) {
        if (item.imageUrl.isNullOrBlank()) null
        else ImageRequest.Builder(context)
            .data(item.imageUrl)
            .crossfade(true)
            .build()
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(
                enabled = bodyOverflows || expanded,
                onClick = {
                    haptic.click()
                    expanded = !expanded
                }
            )
            .animateContentSize()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Meta row: unread dot, colored priority pill, pin, freshness, date.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!seen) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    ) {}
                } else {
                    // Keep the priority pill aligned whether or not the item is unread.
                    Spacer(Modifier.size(8.dp))
                }
                NewsPriorityChip(item.priority, small = true)
                if (item.pinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = stringResource(R.string.st_News_popup_pinned_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (expired) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            stringResource(R.string.st_News_badge_expired),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (isNew) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            stringResource(R.string.st_News_badge_new),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (date.isNotEmpty()) {
                    Text(
                        date,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.imageUrl.isNullOrBlank() && !imageBroken) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        onState = { imageState = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
            }
            if (imageBroken) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.st_News_image_failed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (expanded) {
                MarkdownContent(
                    markdown = item.body,
                    baseFontSize = 15.sp,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    onLinkClick = { url -> NewsLinks.open(context, url) }
                )
            } else {
                Text(
                    stripMarkdown(item.body),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { bodyOverflows = it.hasVisualOverflow },
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            // Footer: expand/collapse toggle and share icon.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (expanded || bodyOverflows) {
                    ToolzExpressiveTextButton(
                        onClick = { expanded = !expanded },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(if (expanded) R.string.st_News_show_less else R.string.st_News_read_more),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        haptic.click()
                        onShare(item)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.st_News_share),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val actionUrl = item.actionUrl
            if (!actionUrl.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                val isDeepLink = actionUrl.startsWith("toolz://")
                ToolzExpressiveButton(
                    onClick = { onAction(actionUrl) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.actionLabel?.ifBlank { null } ?: stringResource(R.string.st_News_open),
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            if (isDeepLink) Icons.AutoMirrored.Rounded.ArrowForward else Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
