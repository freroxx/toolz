/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.search.SearchHistoryEntry

private const val DAY_MS = 24 * 60 * 60 * 1000L
private val TIME_GROUP_ORDER = listOf("Today", "Yesterday", "This week", "Older")

/**
 * A grouped ("Today" / "Yesterday" / "This week" / "Older") list of past
 * searches, optionally filtered by [filterQuery] as the person types.
 */
@Composable
fun RecentSearchTimeline(
    history: List<SearchHistoryEntry>,
    onSearch: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    filterQuery: String = "",
    modifier: Modifier = Modifier,
) {
    val filtered = remember(history, filterQuery) {
        if (filterQuery.isBlank()) history else history.filter { it.query.contains(filterQuery, ignoreCase = true) }
    }
    val grouped = remember(filtered) { groupByRecency(filtered) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            if (filtered.isNotEmpty()) {
                TextButton(onClick = onClearAll, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Text("Clear all", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (filtered.isEmpty()) {
            EmptyTimeline()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .fadingEdges(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                grouped.forEach { (label, entries) ->
                    item(key = "header_$label") {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        RecentTimelineRow(query = entry.query, onClick = { onSearch(entry.query) }, onDelete = { onDelete(entry.id) })
                    }
                }
            }
        }
    }
}

private fun groupByRecency(history: List<SearchHistoryEntry>): List<Pair<String, List<SearchHistoryEntry>>> {
    val now = System.currentTimeMillis()
    val groups = mutableMapOf<String, MutableList<SearchHistoryEntry>>()
    history.forEach { entry ->
        val label = when (val age = now - entry.timestamp) {
            in 0 until DAY_MS -> "Today"
            in DAY_MS until 2 * DAY_MS -> "Yesterday"
            in 2 * DAY_MS until 7 * DAY_MS -> "This week"
            else -> if (age < 0) "Today" else "Older"
        }
        groups.getOrPut(label) { mutableListOf() }.add(entry)
    }
    return TIME_GROUP_ORDER.mapNotNull { label -> groups[label]?.let { label to it } }
}

@Composable
private fun RecentTimelineRow(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(query, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun EmptyTimeline(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ManageSearch, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
        Text("No recent searches", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Your search history will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}
