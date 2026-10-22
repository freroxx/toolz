/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Command palette for Developer Mode. One searchable overlay for console
 * actions and route jumps. The file only renders and filters; every action
 * is a lambda supplied by the screen and stays local.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

data class DevCommand(
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val run: () -> Unit,
)

@Composable
fun CommandPalette(
    visible: Boolean,
    commands: List<DevCommand>,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var executed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val filtered = remember(commands, query) {
        if (query.isBlank()) {
            commands
        } else {
            commands.filter { command ->
                command.title.contains(query, ignoreCase = true) ||
                    command.subtitle.contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        repeat(3) { attempt ->
            if (attempt > 0) delay(120)
            val focused = runCatching { focus.requestFocus() }.getOrDefault(false)
            if (focused) return@LaunchedEffect
        }
    }
    LaunchedEffect(keyboard) {
        delay(120)
        keyboard?.show()
    }
    LaunchedEffect(query, filtered.size) {
        selectedIndex = selectedIndex.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))
    }
    // Key this to the query and selection rather than the command list: the
    // console rebuilds its command catalog on every runtime-stats tick, and
    // restarting this effect each second would yank the list back to the top.
    LaunchedEffect(query, selectedIndex) {
        if (filtered.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex.coerceIn(0, filtered.lastIndex))
        }
    }

    fun execute(command: DevCommand?) {
        if (command == null || executed) return
        executed = true
        keyboard?.hide()
        onDismiss()
        command.run()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val paletteMaxHeight = maxHeight
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = paletteMaxHeight),
            ) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedIndex = 0
                        },
                        placeholder = { Text("Search commands and screens") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { execute(filtered.getOrNull(selectedIndex)) },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Escape -> {
                                        keyboard?.hide()
                                        onDismiss()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        if (filtered.isNotEmpty()) {
                                            selectedIndex = (selectedIndex + 1).coerceAtMost(filtered.lastIndex)
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (filtered.isNotEmpty()) {
                                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                        }
                                        true
                                    }
                                    Key.Enter -> {
                                        execute(filtered.getOrNull(selectedIndex))
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, command -> command.title }) { index, command ->
                            val selected = index == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                    )
                                    .clickable { execute(command) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (command.icon != null) {
                                    Icon(
                                        imageVector = command.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = when {
                                            command.danger -> MaterialTheme.colorScheme.error
                                            selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                    )
                                } else {
                                    Box(Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = command.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            command.danger -> MaterialTheme.colorScheme.error
                                            selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    if (command.subtitle.isNotBlank()) {
                                        Text(
                                            text = command.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                DevEmptyState(
                                    icon = Icons.Rounded.Search,
                                    title = "No matching command",
                                    body = "Try “sandbox”, “logcat”, “report” or a screen name.",
                                )
                            }
                        }
                    }

                    Text(
                        text = "Enter runs the selected action · Esc closes · everything stays on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
