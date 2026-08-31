package com.frerox.toolz.ui.screens.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.browser.BrowserAddressResolver
import com.frerox.toolz.data.browser.BrowserHistoryItem
import com.frerox.toolz.data.browser.BrowserReadingItem
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.ui.screens.search.components.PrivacyFaviconImage

/** A first-class internal new-tab page; it never relies on a remote web start page. */
@Composable
fun BrowserStartPage(
    isPrivate: Boolean,
    bookmarks: List<BookmarkEntry>,
    history: List<BrowserHistoryItem>,
    readingList: List<BrowserReadingItem>,
    onFocusAddress: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onNewPrivateTab: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.surface),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onFocusAddress,
                    shape = RoundedCornerShape(28.dp),
                    color = if (isPrivate) colors.inverseSurface else colors.primaryContainer,
                    contentColor = if (isPrivate) colors.inverseOnSurface else colors.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(shape = CircleShape, color = if (isPrivate) colors.inversePrimary.copy(alpha = .18f) else colors.primary.copy(alpha = .16f), modifier = Modifier.size(48.dp)) {
                            Icon(if (isPrivate) Icons.Rounded.VisibilityOff else Icons.Rounded.Language, null, modifier = Modifier.padding(12.dp), tint = if (isPrivate) colors.inversePrimary else colors.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (isPrivate) "Private tab" else "Toolz Browser", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(if (isPrivate) "Nothing from this tab goes into history" else "Search or enter a web address", style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = .72f))
                        }
                        Icon(Icons.Rounded.Search, null)
                    }
                }
                if (!isPrivate) {
                    Text("Built for a quieter, more intentional web.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }

        if (!isPrivate) item {
            StartPageActionRow(onFocusAddress = onFocusAddress, onNewPrivateTab = onNewPrivateTab)
        }

        if (!isPrivate && bookmarks.isNotEmpty()) item {
            StartPageRail(
                heading = "Saved sites",
                icon = Icons.Rounded.Bookmark,
                entries = bookmarks.take(10).map { it.title to it.url },
                onOpenUrl = onOpenUrl,
            )
        }

        if (!isPrivate && history.isNotEmpty()) item {
            StartPageRail(
                heading = "Pick up where you left off",
                icon = Icons.Rounded.AutoStories,
                // Only the single most recent visit
                entries = history.take(1).map { it.title to it.url },
                onOpenUrl = onOpenUrl,
            )
        }

        if (!isPrivate && readingList.isNotEmpty()) item {
            StartPageRail(
                heading = "Read later",
                icon = Icons.Rounded.AutoStories,
                entries = readingList.take(10).map { it.title to it.url },
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

@Composable
private fun StartPageActionRow(onFocusAddress: () -> Unit, onNewPrivateTab: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StartPageAction("Search the web", Icons.Rounded.Search, onFocusAddress, Modifier.weight(1f))
        StartPageAction("Private tab", Icons.Rounded.VisibilityOff, onNewPrivateTab, Modifier.weight(1f))
    }
}

@Composable
private fun StartPageAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.height(88.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StartPageRail(heading: String, icon: androidx.compose.ui.graphics.vector.ImageVector, entries: List<Pair<String, String>>, onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.second }) { (title, url) ->
                Surface(onClick = { onOpenUrl(url) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(176.dp).height(88.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrivacyFaviconImage(url, 22.dp)
                        Column(Modifier.weight(1f)) {
                            Text(title.ifBlank { BrowserAddressResolver.displayHost(url) }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(BrowserAddressResolver.displayHost(url), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
