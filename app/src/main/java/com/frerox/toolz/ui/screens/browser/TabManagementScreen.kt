/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.search.components.FaviconDisplay

// ─── Accent Colors ────────────────────────────────────────────────────────────

private val ElectricViolet    = Color(0xFF7B6EF6)
private val ElectricVioletDim = Color(0xFF4A3FB8)
private val NeonCyan          = Color(0xFF38F5D4)
private val DangerRed         = Color(0xFFFF4D6A)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabManagementScreen(
    onBack: () -> Unit,
    onTabClick: (id: String, url: String) -> Unit,
    onNewTab: () -> Unit,
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val tabs        by viewModel.tabs.collectAsState(initial = emptyList())
    val activeTabId by viewModel.activeTabId.collectAsState(initial = null)
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelect by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    // Animate header background on multi-select
    val topBarColor by animateColorAsState(
        targetValue   = if (isMultiSelect)
            MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label         = "topBarColor",
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Box(
                modifier = Modifier
                    .clip(ExtraLargeExpressiveShape)
                    .background(topBarColor)
            ) {
                Column {
                    ExpressiveTopAppBar(
                        title = {
                            AnimatedContent(
                                targetState  = isMultiSelect,
                                transitionSpec = {
                                    (fadeIn() + slideInVertically { -it / 2 }) togetherWith
                                            (fadeOut() + slideOutVertically { it / 2 })
                                },
                                label = "tabTitle",
                            ) { multiSelect ->
                                if (multiSelect) {
                                    Text(
                                        "${selectedIds.size} selected",
                                        fontWeight = FontWeight.Black,
                                        color      = ElectricViolet,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                } else {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            "Tabs",
                                            fontWeight = FontWeight.Black,
                                            style      = MaterialTheme.typography.headlineLarge,
                                            letterSpacing = (-1).sp
                                        )
                                        if (tabs.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = ElectricViolet.copy(alpha = 0.15f),
                                            ) {
                                                Text(
                                                    "${tabs.size}",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    style      = MaterialTheme.typography.labelLarge,
                                                    color      = ElectricViolet,
                                                    fontWeight = FontWeight.Black,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = if (isMultiSelect) { { selectedIds = emptySet() } } else onBack
                            ) {
                                AnimatedContent(
                                    targetState = isMultiSelect,
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                                                (scaleOut(targetScale = 0.7f) + fadeOut())
                                    },
                                    label = "navIcon",
                                ) { multiSelect ->
                                    Icon(
                                        if (multiSelect) Icons.Rounded.Close else Icons.Rounded.Close,
                                        contentDescription = if (multiSelect) "Cancel selection" else "Close",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            AnimatedContent(
                                targetState  = isMultiSelect,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label        = "topActions",
                            ) { multiSelect ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (multiSelect) {
                                        IconButton(onClick = {
                                            selectedIds = if (selectedIds.size == tabs.size)
                                                emptySet()
                                            else
                                                tabs.map { it.id }.toSet()
                                        }) {
                                            Icon(
                                                Icons.Rounded.DoneAll,
                                                contentDescription = "Select all",
                                                tint = ElectricViolet,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            viewModel.closeTabs(selectedIds)
                                            selectedIds = emptySet()
                                        }) {
                                            Icon(
                                                Icons.Rounded.DeleteSweep,
                                                contentDescription = "Close selected",
                                                tint = DangerRed,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = onNewTab) {
                                            Icon(Icons.Rounded.Add, contentDescription = "New tab", tint = ElectricViolet, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        largeFlexible = true,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    )

                    // Multi-select progress bar
                    AnimatedVisibility(
                        visible = isMultiSelect,
                        enter   = expandVertically(),
                        exit    = shrinkVertically(),
                    ) {
                        ExpressiveLinearProgressIndicator(
                            progress = { if (tabs.isEmpty()) 0f else selectedIds.size.toFloat() / tabs.size },
                            modifier  = Modifier.fillMaxWidth().height(4.dp),
                            color     = ElectricViolet,
                            trackColor = ElectricViolet.copy(alpha = 0.12f),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isMultiSelect,
                enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit    = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick        = onNewTab,
                    icon           = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(28.dp)) },
                    text           = { Text("New tab", fontWeight = FontWeight.Black) },
                    shape          = ExtraLargeExpressiveShape,
                    containerColor = ElectricViolet,
                    contentColor   = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        if (tabs.isEmpty()) {
            EmptyTabsView(
                onNewTab = onNewTab,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns             = GridCells.Fixed(2),
                contentPadding      = PaddingValues(
                    start  = 16.dp, end = 16.dp,
                    top    = 16.dp,
                    bottom = padding.calculateBottomPadding() + 100.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                modifier              = Modifier
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxSize(),
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                    val isSelected = selectedIds.contains(tab.id)
                    val isActive   = tab.id == activeTabId

                    PremiumTabCard(
                        tab           = tab,
                        index         = index,
                        isSelected    = isSelected,
                        isActive      = isActive,
                        isMultiSelect = isMultiSelect,
                        onClick       = {
                            if (isMultiSelect) {
                                selectedIds = if (isSelected) selectedIds - tab.id else selectedIds + tab.id
                            } else {
                                onTabClick(tab.id, tab.url)
                            }
                        },
                        onLongClick   = {
                            if (!isMultiSelect) selectedIds = setOf(tab.id)
                        },
                        onClose       = { viewModel.closeTab(tab.id) },
                    )
                }
            }
        }
    }
}

// ─── Premium Tab Card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumTabCard(
    tab: TabEntry,
    index: Int,
    isSelected: Boolean,
    isActive: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }

    // Card visual states
    val borderColor by animateColorAsState(
        targetValue   = when {
            isSelected -> ElectricViolet
            isActive   -> ElectricViolet.copy(alpha = 0.6f)
            else       -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        },
        animationSpec = tween(300),
        label         = "tabBorderColor",
    )

    val cardColor by animateColorAsState(
        targetValue   = when {
            isSelected -> ElectricViolet.copy(alpha = 0.12f)
            isActive   -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else       -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(300),
        label         = "tabCardColor",
    )

    StaggeredEntrance(index = index) {
        Box(
            modifier = Modifier
                .height(260.dp)
                .fillMaxWidth()
        ) {
            ExpressiveCard(
                onClick = onClick,
                onLongClick = {
                    haptic.longClick()
                    onLongClick()
                },
                shape = LargeExpressiveShape,
                containerColor = cardColor,
                border = BorderStroke(if (isSelected || isActive) 2.dp else 1.dp, borderColor),
                modifier = Modifier
                    .fillMaxSize()
                    .expressiveMorphing(interactionSource)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Header row ────────────────────────────────────────────────
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FaviconDisplay(
                            url      = tab.url,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            tab.title,
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isActive) FontWeight.Black else FontWeight.ExtraBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f),
                            color      = if (isActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                        )
                        if (!isMultiSelect) {
                            IconButton(
                                onClick  = { haptic.tick(); onClose() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    // ── Preview area ──────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .clip(MediumExpressiveShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    ) {
                        if (tab.previewPath != null) {
                            AsyncImage(
                                model              = tab.previewPath,
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize(),
                                contentScale       = ContentScale.Crop,
                            )
                            // Gradient overlay on preview for readability
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                                        )
                                    )
                            )
                        } else {
                            // URL text fallback
                            Column(
                                modifier            = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Rounded.Public,
                                    null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .alpha(0.3f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    tab.url,
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    fontSize  = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── Active indicator dot (bottom-end) ─────────────────────────────
            AnimatedVisibility(
                visible = isActive && !isMultiSelect,
                enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit    = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                ActiveDot()
            }

            // ── Multi-select checkbox (top-end) ───────────────────────────────
            AnimatedVisibility(
                visible  = isMultiSelect,
                enter    = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit     = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            ) {
                MultiSelectIndicator(isSelected = isSelected)
            }
        }
    }
}

// ─── Active Dot ───────────────────────────────────────────────────────────────

@Composable
private fun ActiveDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "activePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.5f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulseScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
        // Outer pulse
        Box(
            modifier = Modifier
                .size(14.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(ElectricViolet.copy(alpha = 0.3f))
        )
        // Solid core
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(NeonCyan, ElectricViolet))
                )
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
    }
}

// ─── Multi-select Checkbox ────────────────────────────────────────────────────

@Composable
private fun MultiSelectIndicator(isSelected: Boolean) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0.9f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "checkScale",
    )

    Box(
        modifier = Modifier
            .size(22.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isSelected) ElectricViolet
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .border(
                BorderStroke(
                    width = 1.5.dp,
                    color = if (isSelected) ElectricViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = isSelected,
            enter   = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
            exit    = scaleOut() + fadeOut(),
        ) {
            Icon(
                Icons.Rounded.Check,
                null,
                modifier = Modifier.size(14.dp),
                tint     = Color.White,
            )
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyTabsView(
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier        = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500)) + scaleIn(initialScale = 0.92f, animationSpec = spring(Spring.DampingRatioMediumBouncy)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier            = Modifier.padding(40.dp),
            ) {
                // Icon container
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape    = ExtraLargeExpressiveShape,
                    color    = ElectricViolet.copy(alpha = 0.1f),
                    border   = BorderStroke(1.5.dp, ElectricViolet.copy(alpha = 0.2f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Layers,
                            null,
                            modifier = Modifier.size(56.dp),
                            tint     = ElectricViolet.copy(alpha = 0.8f),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier            = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "No open tabs",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Start a new tab to begin browsing",
                        style    = MaterialTheme.typography.bodyLarge,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onNewTab,
                    shape   = LargeExpressiveShape,
                    colors  = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    modifier = Modifier.height(56.dp).padding(horizontal = 24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Open new tab", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}