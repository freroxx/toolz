package com.frerox.toolz.ui.screens.browser

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.ui.screens.search.FaviconDisplay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabManagementScreen(
    onBack: () -> Unit,
    onTabClick: (String, String) -> Unit,
    onNewTab: () -> Unit,
    viewModel: WebViewViewModel = hiltViewModel()
) {
    val tabs by viewModel.tabs.collectAsState(initial = emptyList())
    val activeTabId by viewModel.activeTabId.collectAsState(initial = null)
    
    var selectedTabIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelectMode by remember { derivedStateOf { selectedTabIds.isNotEmpty() } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    if (isMultiSelectMode) {
                        Text("${selectedTabIds.size} Selected", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Tabs", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (isMultiSelectMode) { { selectedTabIds = emptySet() } } else onBack) {
                        Icon(if (isMultiSelectMode) Icons.Default.Close else Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        IconButton(onClick = { 
                            if (selectedTabIds.size == tabs.size) selectedTabIds = emptySet()
                            else selectedTabIds = tabs.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { 
                            viewModel.closeTabs(selectedTabIds)
                            selectedTabIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = onNewTab) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (isMultiSelectMode) MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp) 
                                     else MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                ExtendedFloatingActionButton(
                    onClick = onNewTab,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New Tab") },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        if (tabs.isEmpty()) {
            EmptyTabsView(onNewTab)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize()
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isSelected = selectedTabIds.contains(tab.id)
                    val isActive = tab.id == activeTabId
                    
                    TabCard(
                        tab = tab,
                        isSelected = isSelected,
                        isActive = isActive,
                        isMultiSelectMode = isMultiSelectMode,
                        onClick = { 
                            if (isMultiSelectMode) {
                                selectedTabIds = if (isSelected) selectedTabIds - tab.id else selectedTabIds + tab.id
                            } else {
                                onTabClick(tab.id, tab.url)
                            }
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) {
                                selectedTabIds = setOf(tab.id)
                            }
                        },
                        onClose = { viewModel.closeTab(tab.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyTabsView(onNewTab: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("No open tabs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Open a new tab to start browsing", 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNewTab, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Open New Tab")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabCard(
    tab: TabEntry,
    isSelected: Boolean,
    isActive: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cardColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "cardColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        },
        label = "borderColor"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        border = BorderStroke(2.dp, borderColor),
        modifier = Modifier
            .height(220.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    FaviconDisplay(url = tab.url, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    )
                    if (!isMultiSelectMode) {
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                // Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    if (tab.previewPath != null) {
                        AsyncImage(
                            model = tab.previewPath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                tab.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            
            // Multi-select Indicator
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                        .border(
                            1.dp, 
                            if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else if (isActive) {
                // Active indicator dot
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(8.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
