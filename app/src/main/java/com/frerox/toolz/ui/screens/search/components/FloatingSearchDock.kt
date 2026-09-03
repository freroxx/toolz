/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.browser.TabEntry

/**
 * Floating pill dock, shown at the bottom of both the home/search screen and
 * the embedded WebView chrome.
 *
 * Two display modes, chosen automatically:
 * - **Home mode** (default): [tabs] renders as a horizontal favicon strip.
 * - **WebView mode**: pass [currentUrl] and [onSearchClick] to instead show
 *   a pulsing URL pill for the active page.
 *
 * Swiping down on the dock invokes [onSwipeDown], if provided (e.g. to
 * dismiss an expanded state).
 */
@Composable
fun FloatingSearchDock(
    tabCount: Int,
    onManageTabs: () -> Unit,
    onNewTab: () -> Unit,
    tabs: List<TabEntry> = emptyList(),
    activeTabId: String? = null,
    onTabClick: ((TabEntry) -> Unit)? = null,
    currentUrl: String? = null,
    onSearchClick: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isWebViewMode = onSearchClick != null && currentUrl != null
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .height(76.dp)
            .fillMaxWidth()
            .pointerInput(onSwipeDown) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 25f) {
                        onSwipeDown?.invoke()
                        change.consume()
                    }
                }
            },
        shape = RoundedCornerShape(38.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 16.dp,
        shadowElevation = 24.dp,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NewTabButton(onNewTab = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onNewTab() })

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (isWebViewMode) {
                    UrlPill(
                        currentUrl = currentUrl,
                        pulseAlpha = pulseAlpha,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSearchClick.invoke()
                        },
                    )
                } else {
                    TabStrip(tabs = tabs, activeTabId = activeTabId, onTabClick = onTabClick, haptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) })
                }
            }

            TabManagerButton(
                tabCount = tabCount,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onManageTabs() },
            )
        }
    }
}

@Composable
private fun NewTabButton(onNewTab: () -> Unit) {
    Surface(
        onClick = onNewTab,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        modifier = Modifier.size(54.dp),
        tonalElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_dock_new_tab), tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun UrlPill(currentUrl: String?, pulseAlpha: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp)
            .border(width = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha), shape = CircleShape),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = currentUrl?.let { safeHostFromUrl(it) } ?: stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_search_hint),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabStrip(
    tabs: List<TabEntry>,
    activeTabId: String?,
    onTabClick: ((TabEntry) -> Unit)?,
    haptic: () -> Unit,
) {
    if (tabs.isEmpty()) {
        // No tabs yet — subtle drag handle in place of the strip.
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape),
        )
        return
    }

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = tabs.takeLast(10), key = { it.id }) { tab ->
            val isActive = tab.id == activeTabId
            val tabScale by animateFloatAsState(
                targetValue = if (isActive) 1.2f else 1.0f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "tabScale_${tab.id}",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .scale(tabScale)
                    .clip(CircleShape)
                    .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .border(
                        width = if (isActive) 2.5.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = CircleShape,
                    )
                    .clickable {
                        haptic()
                        onTabClick?.invoke(tab)
                    },
            ) {
                PrivacyFaviconImage(url = tab.url, size = 26.dp)
            }
        }
    }
}

@Composable
private fun TabManagerButton(tabCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(54.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Layers, stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_dock_tabs), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
            if (tabCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(22.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (tabCount > 99) "99" else tabCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

internal fun safeHostFromUrl(url: String): String = try {
    java.net.URI(url).host?.removePrefix("www.") ?: url
} catch (_: Exception) {
    url
}
