/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Logcat diagnostic stream: level chips, color-coded lines, live tail,
 * substring filter, copy/share, and privileged Shizuku ADB shell support.
 *
 * When Shizuku is authorized, commands execute as UID 2000 (shell), allowing
 * unrestricted logcat streaming across the app or full system without permission drops.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.screens.developer.LogcatScope

private val levelRegex = Regex("""(?:\s|\b)([VDIWEF])[/ ]""")

private fun levelColor(level: Char?, fallback: Color): Color = when (level) {
    'E' -> Color(0xFFFF6B6B)
    'F' -> Color(0xFFFF5252)
    'W' -> Color(0xFFFFB74D)
    'I' -> Color(0xFF81C784)
    'D' -> Color(0xFF64B5F6)
    'V' -> Color(0xFFB0BEC5)
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
        fontSize = 11.sp,
        softWrap = false,
        lineHeight = 16.sp,
    )
}

@Composable
fun LogcatViewer(
    lines: List<String>,
    tail: Boolean,
    onTail: (Boolean) -> Unit,
    shizukuActive: Boolean,
    forceShizuku: Boolean,
    onToggleForceShizuku: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    scope: LogcatScope,
    onScopeChange: (LogcatScope) -> Unit,
    onRefresh: () -> Unit,
    onTestLog: () -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit,
    onCopyWarningsAndErrors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedLevel by remember { mutableStateOf<Char?>('A') } // 'A' = All
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filtered = remember(lines, selectedLevel, query) {
        lines.filter { line ->
            val lvl = levelRegex.find(line)?.groupValues?.getOrNull(1)?.first() ?: 'I'
            val matchesLevel = when (selectedLevel) {
                'A', null -> true
                'V' -> true
                'D' -> lvl in "DIWEF"
                'I' -> lvl in "IWEF"
                'W' -> lvl in "WEF"
                'E' -> lvl in "EF"
                else -> true
            }
            matchesLevel && (query.isBlank() || line.contains(query, ignoreCase = true))
        }
    }

    LaunchedEffect(filtered.size, tail) {
        if (tail && filtered.isNotEmpty()) {
            listState.scrollToItem((filtered.size - 1).coerceAtLeast(0))
        }
    }

    Column(modifier.fillMaxWidth()) {
        // ── Top Status & Action Bar ──────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Live indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (tail) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (tail) "Live (${filtered.size} lines)" else "Paused (${filtered.size} lines)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            // Copy Warnings & Errors action
            IconButton(
                onClick = onCopyWarningsAndErrors,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = "Copy Warnings & Errors",
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Copy All
            IconButton(
                onClick = onCopyAll,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy all logs",
                    modifier = Modifier.size(18.dp),
                )
            }

            // Emit Test Log
            IconButton(
                onClick = onTestLog,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Emit test log",
                    modifier = Modifier.size(18.dp),
                )
            }

            // Refresh
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(18.dp),
                )
            }

            // Clear
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Search & Filter Row ───────────────────────────────────────────────
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Filter logcat by tag, msg, pid…", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear query", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(8.dp))

        // ── Level Selector Chips + Scope ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                'A' to "All",
                'V' to "Verbose",
                'D' to "Debug",
                'I' to "Info",
                'W' to "Warn",
                'E' to "Error",
            ).forEach { (lvl, label) ->
                FilterChip(
                    selected = selectedLevel == lvl,
                    onClick = { selectedLevel = lvl },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedLevel == lvl) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (lvl) {
                            'W' -> Color(0xFFFFB74D).copy(alpha = 0.25f)
                            'E' -> Color(0xFFFF6B6B).copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                )
            }

            if (shizukuActive) {
                FilterChip(
                    selected = forceShizuku,
                    onClick = { onToggleForceShizuku(!forceShizuku) },
                    label = { Text(if (forceShizuku) "Shizuku Privileged" else "Standard", fontSize = 11.sp) },
                )
                if (forceShizuku) {
                    LogcatScope.entries.forEach { s ->
                        FilterChip(
                            selected = scope == s,
                            onClick = { onScopeChange(s) },
                            label = { Text(s.title, fontSize = 11.sp) },
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onRequestShizuku,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Enable Shizuku", fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Terminal Logcat Box (Spacious 340dp) ──────────────────────────────
        DevMonoBox(modifier = Modifier.fillMaxWidth().height(340.dp)) {
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (lines.isEmpty()) "Waiting for log events… Tap '+' to emit test log."
                        else "No logs match the current filter.",
                        color = Color(0xFF90A4AE),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(state = listState) {
                        items(filtered.size) { i ->
                            LogLine(filtered[i], Color(0xFFCFD8DC))
                        }
                    }
                }
            }
        }
    }
}
