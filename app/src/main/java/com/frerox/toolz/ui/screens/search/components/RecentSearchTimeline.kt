/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.ui.components.fadingEdges

@Composable
fun RecentSearchTimeline(
    history: List<SearchHistoryEntry>,
    onSearch: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    filterQuery: String = "",
    modifier: Modifier = Modifier
) {
    val filtered = if (filterQuery.isBlank()) history else history.filter { it.query.contains(filterQuery, ignoreCase = true) }
    val grouped = remember(filtered) { groupByTime(filtered) }
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
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).fadingEdges(top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                grouped.forEach { (label, entries) ->
                    item(key = "header_$label") {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                    }
                    items(entries, key = { it.id }) { entry ->
                        RecentTimelineRow(query = entry.query, onClick = { onSearch(entry.query) }, onDelete = { onDelete(entry.id) })
                    }
                }
            }
        }
    }
}
private fun groupByTime(history: List<SearchHistoryEntry>): List<Pair<String, List<SearchHistoryEntry>>> {
    val now = System.currentTimeMillis()
    val groups = mutableMapOf<String, MutableList<SearchHistoryEntry>>()
    val order = listOf("Today", "Yesterday", "This week", "Older")
    history.forEach { e ->
        val diff = now - e.timestamp
        val label = when {
            diff < 24*3600*1000L -> "Today"
            diff < 48*3600*1000L -> "Yesterday"
            diff < 7*24*3600*1000L -> "This week"
            else -> "Older"
        }
        groups.getOrPut(label){ mutableListOf()}.add(e)
    }
    return order.mapNotNull { l -> groups[l]?.let{ l to it} }
}
@Composable
private fun RecentTimelineRow(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(query, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(modifier = Modifier.size(22.dp).clip(CircleShape).clickable{ haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete()}, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
@Composable
private fun EmptyTimeline(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center){ Icon(Icons.AutoMirrored.Rounded.ManageSearch, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
        }
        Text("No recent searches", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Your search history will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}
