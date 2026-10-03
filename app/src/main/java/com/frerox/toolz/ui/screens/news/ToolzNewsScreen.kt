package com.frerox.toolz.ui.screens.news

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.stripMarkdown
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (items.isEmpty()) viewModel.loadHistory()
        else viewModel.refreshUnread()
    }

    // Deep-link: scroll to the highlighted item once it is loaded.
    LaunchedEffect(items, highlightNewsId) {
        if (highlightNewsId != null) {
            val index = items.indexOfFirst { it.id == highlightNewsId }
            if (index >= 0) {
                try { listState.scrollToItem(index) } catch (_: Exception) { }
            }
        }
    }

    LaunchedEffect(listState) {
        // Read totalItemsCount inside snapshotFlow so the threshold tracks the
        // current list (reading the `items` state here would capture a stale value).
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            if (last == null) -1 else last - total
        }
            .distinctUntilChanged()
            .collect { distanceFromEnd ->
                if (distanceFromEnd >= -3) viewModel.loadMore()
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
                    title = "Toolz News",
                    subtitle = "Announcements & changelog",
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.click()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            haptic.click()
                            viewModel.refreshNow()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Check for news")
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading news…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Newspaper, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("You're all caught up", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("Announcements from the team will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ToolzExpressiveButton(onClick = { viewModel.refreshNow() }, modifier = Modifier.height(48.dp)) {
                        Text("Check for news")
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
                items(items, key = { it.id }, contentType = { "news_card" }) { item ->
                    NewsHistoryCard(
                        item = item,
                        highlighted = item.id == highlightNewsId,
                        seen = item.id in seenIds,
                        onAction = { url ->
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: Exception) { }
                        }
                    )
                }
                if (!end) {
                    item(key = "load_more") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            else TextButton(onClick = { viewModel.loadMore() }) { Text("Load more") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsHistoryCard(
    item: NewsEntity,
    highlighted: Boolean,
    seen: Boolean,
    onAction: (String) -> Unit
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    val date = remember(item.publishAt) {
        try {
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                .format(Instant.ofEpochMilli(item.publishAt).atZone(ZoneId.systemDefault()))
        } catch (_: Exception) { "" }
    }
    val isNew = remember(item.publishAt) {
        System.currentTimeMillis() - item.publishAt < 7 * 24 * 60 * 60 * 1000L
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!seen) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(8.dp)) {}
                }
                AssistChip(onClick = {}, label = { Text(item.priority.uppercase()) },
                    leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(14.dp)) })
                if (isNew) {
                    AssistChip(onClick = {}, label = { Text("NEW") })
                }
                Spacer(Modifier.weight(1f))
                Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            if (!item.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(20.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            if (expanded) {
                MarkdownContent(
                    markdown = item.body,
                    baseFontSize = 16.sp,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                )
            } else {
                Text(
                    stripMarkdown(item.body), maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Read more")
            }
            if (!item.actionUrl.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                ToolzExpressiveButton(
                    onClick = { onAction(item.actionUrl!!) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(item.actionLabel?.ifBlank { "Open" } ?: "Open")
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
