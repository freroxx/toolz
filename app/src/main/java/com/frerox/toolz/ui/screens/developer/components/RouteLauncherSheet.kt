/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Searchable screen launcher for Developer Mode. Routes come from the shared
 * DevRoutes catalog, so every visible row is a real, argument-free route.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.screens.developer.DevRouteCategory
import com.frerox.toolz.ui.screens.developer.devRoutes
import com.frerox.toolz.ui.screens.developer.filterDevRoutes
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLauncherSheet(
    onRouteSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val filtered = remember(query) { filterDevRoutes(query) }

    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(Unit) {
        repeat(3) { attempt ->
            if (attempt > 0) delay(140)
            val focused = runCatching { focus.requestFocus() }.getOrDefault(false)
            if (focused) return@LaunchedEffect
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Open a screen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${devRoutes.size} routes · every entry opens without extra arguments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                singleLine = true,
                label = { Text("Search screens") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(14.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        DevEmptyState(
                            icon = Icons.Rounded.Search,
                            title = "No matching screen",
                            body = "Try “battery”, “settings”, “timer” or a route name.",
                        )
                    }
                } else {
                    DevRouteCategory.entries.forEach { category ->
                        val routes = filtered.filter { it.category == category }
                        if (routes.isNotEmpty()) {
                            item(key = "header_${category.name}") {
                                Text(
                                    text = category.label.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                )
                            }
                            items(routes, key = { it.route }) { route ->
                                DevActionRow(
                                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                    title = route.label,
                                    supporting = route.route,
                                    onClick = {
                                        onRouteSelected(route.route)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = "Route jumps do not change sandbox state. Open the target screen to see its fixture.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
