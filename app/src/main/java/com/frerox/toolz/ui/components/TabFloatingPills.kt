package com.frerox.toolz.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.ui.screens.search.FaviconDisplay
import kotlin.math.roundToInt

@Composable
fun TabFloatingPills(
    tabs: List<TabEntry>,
    activeTabId: String?,
    onTabClick: (String, String) -> Unit,
    onNewTab: () -> Unit,
    onManageTabs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHidden by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val barHeight = 64.dp 
    val barHeightPx = with(density) { barHeight.toPx() }
    
    var offsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = if (isHidden) barHeightPx + 160f else offsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "offsetY"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + 80.dp), 
        contentAlignment = Alignment.BottomCenter
    ) {
        // Arrow handle to show when hidden
        AnimatedVisibility(
            visible = isHidden,
            enter = fadeIn() + scaleIn(initialScale = 0.8f) + slideInVertically { it },
            exit = fadeOut() + scaleOut(targetScale = 0.8f) + slideOutVertically { it }
        ) {
            Surface(
                onClick = { isHidden = false },
                shape = CircleShape, // Changed to Circle for premium look
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(48.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp, 
                        null, 
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Horizontal Tab Bar - Wider and sleeker
        Box(
            modifier = Modifier
                .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (!isHidden) {
                            offsetY = (offsetY + delta).coerceIn(0f, barHeightPx)
                        }
                    },
                    onDragStopped = { velocity ->
                        if (offsetY > barHeightPx / 4 || velocity > 400f) {
                            isHidden = true
                        }
                        offsetY = 0f
                    }
                )
                .fillMaxWidth(0.92f) // Wider profile
                .padding(bottom = 16.dp) 
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp).copy(alpha = 0.98f),
                tonalElevation = 12.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    // New Tab Button - Simple & Modern
                    Surface(
                        onClick = onNewTab,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add, 
                                null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Tabs List - More refined
                    Box(modifier = Modifier.weight(1f)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0f to Color.Transparent,
                                            0.05f to Color.Black,
                                            0.95f to Color.Black,
                                            1f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            items(tabs, key = { it.id }) { tab ->
                                val isSelected = tab.id == activeTabId
                                PremiumPillTabItem(
                                    tab = tab,
                                    isSelected = isSelected,
                                    onClick = { onTabClick(tab.id, tab.url) }
                                )
                            }
                        }
                    }

                    // Manage Tabs Button - Simple & Modern
                    Surface(
                        onClick = onManageTabs,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.GridView,
                                null, 
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumPillTabItem(
    tab: TabEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(300),
        label = "bgColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "contentColor"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
        ),
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            FaviconDisplay(
                url = tab.url, 
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(4.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}
