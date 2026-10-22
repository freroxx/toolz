/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Sandboxes tab: one searchable, grouped list for all 18 fixture controls.
 * A row opens the configurator; the switch turns that sandbox on or off.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.screens.developer.components.DevEmptyState
import com.frerox.toolz.ui.screens.developer.components.DevInlineNotice
import com.frerox.toolz.ui.screens.developer.components.DevSectionTitle
import com.frerox.toolz.ui.screens.developer.components.SpoofGroupHeader
import com.frerox.toolz.ui.screens.developer.components.SpoofToolTile

@Composable
fun DeveloperSandboxesTab(
    spoofs: Map<DevToolId, Boolean>,
    params: SpoofParams,
    onToggleDevMode: (Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onClearAll: () -> Unit,
    onToggleTool: (DevToolId, Boolean) -> Unit,
    onConfigure: (DevToolId) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var activeOnly by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGroup = remember(groupName) {
        DevToolGroup.entries.firstOrNull { it.name == groupName }
    }
    val activeTools = remember(spoofs) {
        DevToolId.entries.filterTo(linkedSetOf()) { spoofs[it] == true }
    }
    val filtered = remember(query, selectedGroup, activeOnly, activeTools) {
        filterSandboxTools(
            query = query,
            group = selectedGroup,
            activeOnly = activeOnly,
            activeTools = activeTools,
        )
    }
    val groups = DevToolGroup.entries.mapNotNull { group ->
        filtered.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { group to it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "master") {
            DeveloperMasterCard(
                activeCount = activeTools.size,
                onToggle = onToggleDevMode,
                onEnableAll = onEnableAll,
                onClearAll = onClearAll,
            )
        }

        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text("Search sandboxes") },
                placeholder = { Text("Battery, network, clipboard…") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }

        item(key = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = selectedGroup == null && !activeOnly,
                    onClick = {
                        groupName = null
                        activeOnly = false
                    },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = activeOnly,
                    onClick = {
                        activeOnly = !activeOnly
                        groupName = null
                    },
                    label = { Text("Active") },
                )
                DevToolGroup.entries.forEach { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = {
                            groupName = if (selectedGroup == group) null else group.name
                            activeOnly = false
                        },
                        label = { Text(group.title) },
                    )
                }
            }
        }

        item(key = "summary") {
            DevSectionTitle(
                title = "Sandboxes",
                supporting = "${filtered.size} of ${DevToolId.entries.size} shown · ${activeTools.size} active",
            )
        }

        if (filtered.isEmpty()) {
            item(key = "empty") {
                DevEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No sandboxes match",
                    body = when {
                        activeOnly && query.isNotBlank() ->
                            "No active sandbox matches “$query”. Show all to search every sandbox."
                        activeOnly -> "No sandboxes are active. Show all to turn one on."
                        query.isNotBlank() && selectedGroup != null ->
                            "No sandbox in ${selectedGroup.title} matches “$query”."
                        query.isNotBlank() -> "No sandbox matches “$query”. Try a different word."
                        selectedGroup != null ->
                            "No sandboxes in ${selectedGroup.title} match. Show all to browse every group."
                        else -> "Clear the filters to see every sandbox."
                    },
                    actionLabel = "Show all",
                    onAction = {
                        query = ""
                        activeOnly = false
                        groupName = null
                    },
                )
            }
        } else {
            groups.forEach { (group, tools) ->
                item(key = "header_${group.name}") {
                    SpoofGroupHeader(
                        title = group.title,
                        activeCount = DevToolId.entries.count { it.group == group && spoofs[it] == true },
                        totalCount = DevToolId.entries.count { it.group == group },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(tools, key = { it.prefKey }) { tool ->
                    SpoofToolTile(
                        tool = tool,
                        active = spoofs[tool] == true,
                        summary = spoofFixtureSummary(tool, params),
                        onOpen = { onConfigure(tool) },
                        onToggle = { enabled -> onToggleTool(tool, enabled) },
                    )
                }
            }
        }

        item(key = "note") {
            DevInlineNotice(
                icon = Icons.Rounded.Science,
                text = "Fixture values replace tool output in memory. They never write to Room, files, the clipboard or the network.",
            )
        }
    }
}
