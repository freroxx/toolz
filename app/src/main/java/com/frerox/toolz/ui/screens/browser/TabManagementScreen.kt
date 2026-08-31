/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.browser

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.R
import com.frerox.toolz.data.browser.BrowserAddressResolver
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.search.components.PrivacyFaviconImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabManagementScreen(
    onBack: () -> Unit,
    onTabClick: (id: String, url: String) -> Unit,
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val tabs        by viewModel.tabs.collectAsState(initial = emptyList())
    val activeTabId by viewModel.activeTabId.collectAsState(initial = null)
    val haptic = LocalHapticFeedback.current

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showPrivateTabs by remember { mutableStateOf(false) }
    var showCloseAllDialog by remember { mutableStateOf(false) }
    val isMultiSelect by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    val visibleTabs = remember(tabs, showPrivateTabs) {
        tabs.filter { it.isPrivate == showPrivateTabs }
    }
    val normalCount = remember(tabs) { tabs.count { !it.isPrivate } }
    val privateCount = remember(tabs) { tabs.count { it.isPrivate } }

    val openNewTab = {
        val tab = viewModel.addTab("about:blank", isPrivate = showPrivateTabs)
        onTabClick(tab.id, tab.url)
    }

    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = { Text("Close all ${if (showPrivateTabs) "private " else ""}tabs?") },
            text = { Text("This will close all ${visibleTabs.size} open tabs.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseAllDialog = false
                        val idsToClose = visibleTabs.map { it.id }.toSet()
                        viewModel.closeTabs(idsToClose)
                    }
                ) {
                    Text("Close all", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = if (isMultiSelect) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Bar Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = if (isMultiSelect) { { selectedIds = emptySet() } } else onBack,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    if (isMultiSelect) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (isMultiSelect) {
                                Text(
                                    "${selectedIds.size} selected",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    "Open Tabs",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "${tabs.size}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Top Actions
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isMultiSelect) {
                                IconButton(
                                    onClick = {
                                        selectedIds = if (selectedIds.size == visibleTabs.size) emptySet() else visibleTabs.map { it.id }.toSet()
                                    }
                                ) {
                                    Icon(Icons.Rounded.DoneAll, "Select all", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.closeTabs(selectedIds)
                                        selectedIds = emptySet()
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, "Delete selected", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                if (visibleTabs.isNotEmpty()) {
                                    IconButton(onClick = { showCloseAllDialog = true }) {
                                        Icon(Icons.Rounded.DeleteSweep, "Close all tabs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = openNewTab) {
                                    Icon(Icons.Rounded.Add, "New tab", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }

                    // Segmented Normal vs Private Tabs Switcher
                    if (!isMultiSelect) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                                // Normal Tabs
                                Surface(
                                    onClick = { showPrivateTabs = false },
                                    shape = RoundedCornerShape(19.dp),
                                    color = if (!showPrivateTabs) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (!showPrivateTabs) 1.dp else 0.dp,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.Public,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (!showPrivateTabs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Tabs ($normalCount)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (!showPrivateTabs) FontWeight.Bold else FontWeight.Medium,
                                            color = if (!showPrivateTabs) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Private Tabs
                                Surface(
                                    onClick = { showPrivateTabs = true },
                                    shape = RoundedCornerShape(19.dp),
                                    color = if (showPrivateTabs) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (showPrivateTabs) 1.dp else 0.dp,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.VisibilityOff,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (showPrivateTabs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Private ($privateCount)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (showPrivateTabs) FontWeight.Bold else FontWeight.Medium,
                                            color = if (showPrivateTabs) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isMultiSelect) {
                ExtendedFloatingActionButton(
                    onClick = openNewTab,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                    icon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(24.dp)) },
                    text = { Text("New Tab", fontWeight = FontWeight.Bold) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        if (visibleTabs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (showPrivateTabs) Icons.Rounded.VisibilityOff else Icons.Rounded.Tab,
                                null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        if (showPrivateTabs) "No private tabs" else "No open tabs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = openNewTab,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (showPrivateTabs) "Open Private Tab" else "Open New Tab")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleTabs, key = { it.id }) { tab ->
                    val isSelected = selectedIds.contains(tab.id)
                    val isActive = tab.id == activeTabId

                    TabCard(
                        tab = tab,
                        isSelected = isSelected,
                        isActive = isActive,
                        isMultiSelect = isMultiSelect,
                        onClick = {
                            if (isMultiSelect) {
                                selectedIds = if (isSelected) selectedIds - tab.id else selectedIds + tab.id
                            } else {
                                viewModel.switchTab(tab.id)
                                onTabClick(tab.id, tab.url)
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isMultiSelect) selectedIds = setOf(tab.id)
                        },
                        onClose = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.closeTab(tab.id)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: TabEntry,
    isSelected: Boolean,
    isActive: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isActive -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(if (isActive || isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Header Row: Favicon, Domain, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrivacyFaviconImage(url = tab.url, size = 18.dp)
                Text(
                    text = BrowserAddressResolver.displayHost(tab.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (tab.isPrivate) {
                    Icon(
                        Icons.Rounded.VisibilityOff,
                        contentDescription = "Private",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (!isMultiSelect) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Body Area: Thumbnail preview or title card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(8.dp),
                contentAlignment = Alignment.TopStart
            ) {
                if (tab.previewPath != null) {
                    AsyncImage(
                        model = tab.previewPath,
                        contentDescription = tab.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = tab.title.ifBlank { "New Tab" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom active indicator
            if (isActive) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        "Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
