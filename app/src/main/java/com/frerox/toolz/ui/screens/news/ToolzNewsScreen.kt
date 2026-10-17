package com.frerox.toolz.ui.screens.news

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.stripMarkdown
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val keyboard = LocalSoftwareKeyboardController.current

    // Android 13+ notification permission: denial fails silently by design,
    // so surface a one-tap enable card instead of leaving users confused.
    // After the first denial the system stops showing the dialog, so the
    // card switches to a Settings deep link that always works.
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

    /**
     * Listing = paged history while idle; DAO-backed full-history results while
     * a query or priority filter is active. Search never silently hides older
     * items the way an in-memory filter over loaded pages would.
     */
    val filteredItems = remember(items, priority) {
        if (priority == "all") items else items.filter { it.priority == priority }
    }
    val listing = searchResults ?: filteredItems

    val historyError by viewModel.historyError.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()

    // Sync state drives the "updated X ago" stamp; recheck every minute.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    // Search is delegated to the ViewModel (debounced DAO query) whenever it
    // becomes active; clearing query + filter returns to the paged history.
    LaunchedEffect(query, priority) {
        viewModel.requestSearch(query, priority)
    }

    LaunchedEffect(Unit) {
        if (items.isEmpty()) viewModel.loadHistory()
        else viewModel.refreshUnread()
    }

    /**
     * Seen-on-viewport: every card visible for a moment reports itself; the
     * ViewModel batches writes. Unread dots disappear only for content the
     * user actually scrolled past — not for a page loaded off-screen.
     */
    LaunchedEffect(listState, listing) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.map { it.index }
        }.distinctUntilChanged().collect { indices ->
            val ids = indices.mapNotNull { i -> listing.getOrNull(i - 1)?.id }
            if (ids.isNotEmpty()) viewModel.markSeenOnVisible(ids)
        }
    }

    // Load-more threshold tracks the current list inside snapshotFlow.
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

    /**
     * Deep-link scroll: the tools header is item 0, so a card at position i
     * lives at list index i + 1. If the target hasn't been loaded yet, keep
     * paging (bounded) until it shows up — a notification deep link must land
     * on its card, not drift 10 items up.
     */
    var highlightFx by remember { mutableStateOf(highlightNewsId) }
    LaunchedEffect(highlightNewsId, listing, loading, end) {
        if (highlightNewsId == null) return@LaunchedEffect
        val index = listing.indexOfFirst { it.id == highlightNewsId }
        if (index >= 0) {
            try { listState.animateScrollToItem(index + 1) } catch (_: Exception) { }
            // Hold the highlight for two seconds, then let the color spring back.
            delay(2_000L)
            highlightFx = null
        } else if (!end && !loading && searchResults == null) {
            viewModel.loadMore()
        }
    }

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
                        IconButton(onClick = {
                            haptic.click()
                            viewModel.refreshNow()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.st_News_refresh))
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
        if (items.isEmpty() && loading) {
            NewsSkeletonList(Modifier.padding(padding))
        } else if (items.isEmpty() && historyError != null) {
            // First-run offline: cached news doesn't exist yet — be honest
            // about the failure and offer a retry.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        stringResource(R.string.st_News_load_failed),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        stringResource(R.string.st_News_sync_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ToolzExpressiveButton(
                        onClick = { viewModel.refreshNow() },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(stringResource(R.string.st_News_retry))
                    }
                }
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Newspaper, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.st_News_empty_title), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.st_News_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ToolzExpressiveButton(onClick = { viewModel.refreshNow() }, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.st_News_refresh))
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).fadingEdges(top = 12.dp, bottom = 100.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "news_tools") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    TextButton(onClick = { viewModel.refreshNow() }) { Text(stringResource(R.string.st_News_retry)) }
                                }
                            }
                        }
                        if (syncing || searching) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(if (searching) R.string.st_News_searching else R.string.st_News_syncing),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!notifGranted && Build.VERSION.SDK_INT >= 33) {
                            val afterDenial = notifDenials >= 1
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Rounded.Notifications, contentDescription = null)
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.st_News_notifications_off_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            stringResource(
                                                if (afterDenial) R.string.st_News_notifications_denied_body
                                                else R.string.st_News_notifications_off_body
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    TextButton(onClick = {
                                        haptic.click()
                                        if (afterDenial) {
                                            // Dialog was refused once — go straight
                                            // to the always-works settings screen.
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                })
                                            } catch (_: Exception) { }
                                        } else {
                                            try { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                                            catch (_: Exception) { }
                                        }
                                    }) {
                                        Text(
                                            stringResource(
                                                if (afterDenial) R.string.st_News_notifications_open_settings
                                                else R.string.st_News_notifications_enable
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.st_News_search_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        haptic.click()
                                        query = ""
                                    }) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.st_News_search_clear),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(NEWS_FILTERS) { p ->
                                FilterChip(
                                    selected = priority == p,
                                    onClick = { priority = p; haptic.click() },
                                    label = { Text(filterLabel(p)) }
                                )
                            }
                        }
                        // Honest freshness stamp: how long ago the last sync ran.
                        if (lastSync > 0) {
                            val shortDelta = remember(lastSync, now) {
                                NewsTime.relativeShort(lastSync, now)
                            }
                            Text(
                                stringResource(
                                    if (shortDelta.isEmpty()) R.string.st_News_last_synced_date
                                    else R.string.st_News_last_synced,
                                    shortDelta
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        if (searchResults != null) {
                            if (listing.isEmpty()) {
                                Text(
                                    stringResource(R.string.st_News_no_matches),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (filteredItems.size != items.size) {
                            Text(
                                stringResource(R.string.st_News_shown_count, filteredItems.size, items.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            else TextButton(onClick = { viewModel.loadMore() }) { Text(stringResource(R.string.st_News_load_more)) }
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
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val todayLabel = stringResource(R.string.st_News_today)
    val yesterdayLabel = stringResource(R.string.st_News_yesterday)
    val date = remember(item.publishAt, now, todayLabel, yesterdayLabel) {
        NewsTime.newsDate(item.publishAt, now, todayLabel, yesterdayLabel)
    }
    val isNew = remember(item.publishAt, now) { NewsTime.isRecent(item.publishAt, now) }
    val expired = remember(item.expiresAt, now) { NewsTime.isExpired(item.expiresAt, now) }

    // Body overflows at 3 collapsed lines? Measure the preview once rendered;
    // if it fits, no "Read more" toggle is shown at all.
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Meta row: unread dot, colored priority pill, pin, freshness,
            // date — quiet unless something needs attention.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!seen) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    ) {}
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
                            fontWeight = FontWeight.Black,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            if (!item.imageUrl.isNullOrBlank() && !imageBroken) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        onState = { imageState = it },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(20.dp))
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
                    baseFontSize = 16.sp,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    onLinkClick = { url -> NewsLinks.open(context, url) }
                )
            } else {
                Text(
                    stripMarkdown(item.body),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { bodyOverflows = it.hasVisualOverflow },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            // Footer: quiet secondary actions, then the primary CTA below.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (expanded || bodyOverflows) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.st_News_show_less else R.string.st_News_read_more))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onShare(item) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.st_News_share),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!item.actionUrl.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                ToolzExpressiveButton(
                    onClick = { onAction(item.actionUrl!!) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(item.actionLabel?.ifBlank { null } ?: stringResource(R.string.st_News_open))
                }
            }
        }
    }
}
