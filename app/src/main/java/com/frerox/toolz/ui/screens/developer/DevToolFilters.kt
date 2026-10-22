/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Pure search/group filter used by the Sandboxes tab.
 */

package com.frerox.toolz.ui.screens.developer

import com.frerox.toolz.data.developer.DevToolId

fun filterSandboxTools(
    tools: List<DevToolId> = DevToolId.entries,
    query: String = "",
    group: DevToolGroup? = null,
    activeOnly: Boolean = false,
    activeTools: Set<DevToolId> = emptySet(),
): List<DevToolId> {
    val trimmed = query.trim()
    return tools.asSequence()
        .filter { group == null || it.group == group }
        .filter { !activeOnly || it in activeTools }
        .filter { tool ->
            trimmed.isEmpty() ||
                tool.title.contains(trimmed, ignoreCase = true) ||
                tool.subtitle.contains(trimmed, ignoreCase = true) ||
                tool.networkPolicy.label.contains(trimmed, ignoreCase = true) ||
                tool.group.title.contains(trimmed, ignoreCase = true)
        }
        .toList()
}
