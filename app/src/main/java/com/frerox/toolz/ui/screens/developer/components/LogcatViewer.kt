/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Logcat v2: level chips, color-coded lines, live tail, substring filter,
 * copy/share. Reads stay local (logcat -d) — nothing is uploaded.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.screens.developer.components.DevBtn

private val levelRegex = Regex("""\s([VDIWEF])\s""")

private fun levelColor(level: Char?, fallback: Color): Color = when (level) {
    'E' -> Color(0xFFEF8A8A)
    'F' -> Color(0xFFEF6D6D)
    'W' -> Color(0xFFF3B95F)
    'I' -> Color(0xFF8FE0A8)
    'D' -> Color(0xFF8FB7F2)
    'V' -> Color(0xFFB9C4D0)
    else -> fallback
}

@Composable
private fun LogLine(text: String, defaultColor: Color) {
    val level = remember(text) { levelRegex.find(text)?.groupValues?.getOrNull(1)?.first() }
    val color = levelColor(level, defaultColor)
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        softWrap = false,
    )
}

@Composable
fun LogcatViewer(
    lines: List<String>,
    tail: Boolean,
    onTail: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var levelFilter by remember { mutableStateOf(setOf('V', 'D', 'I', 'W', 'E', 'F')) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filtered = remember(lines, levelFilter, query) {
        lines.filter { line ->
            val lvl = levelRegex.find(line)?.groupValues?.getOrNull(1)?.first() ?: 'I'
            lvl in levelFilter &&
                (query.isBlank() || line.contains(query, ignoreCase = true))
        }
    }

    // Live tail: keep pinned to bottom when new lines arrive.
    LaunchedEffect(filtered.size, tail) {
        if (tail) listState.scrollToItem((filtered.size - 1).coerceAtLeast(0))
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter lines…", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh log") }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf('V' to "Ver", 'D' to "Dbg", 'I' to "Info", 'W' to "Warn", 'E' to "Err", 'F' to "Ftl").forEach { (l, lbl) ->
                FilterChip(
                    selected = l in levelFilter,
                    onClick = {
                        levelFilter = if (l in levelFilter) levelFilter - l
                        else (levelFilter + l).sorted().toSet()
                    },
                    label = { Text(lbl, fontSize = 11.sp) },
                )
            }
            Spacer(Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live", modifier = Modifier.padding(start = 6.dp, end = 4.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Switch(checked = tail, onCheckedChange = onTail)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(10.dp),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    if (lines.isEmpty()) "(empty — tap Refresh or enable Live tail)" else "(no lines match the filter)",
                    color = Color(0xFF8FA0AE),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            } else {
                SelectionContainer {
                    // LazyColumn keeps 300 long lines cheap + enables tailing.
                    androidx.compose.foundation.lazy.LazyColumn(state = listState) {
                        items(filtered.size) { i ->
                            LogLine(filtered[i], Color(0xFFB9C4D0))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DevBtn("Copy all", Icons.Rounded.ContentCopy) { onCopyAll() }
            DevBtn("Clear logcat", Icons.Rounded.Delete) { onClear() }
        }
    }
}
