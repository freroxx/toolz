package com.frerox.toolz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.screens.dashboard.DashboardViewModel
import com.frerox.toolz.ui.screens.dashboard.ToolItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickToolzBottomSheet(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val pinnedTools by viewModel.pinnedTools.collectAsState(initial = emptySet())
    val recentTools by viewModel.recentTools.collectAsState(initial = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 8.dp)
                    .size(48.dp, 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "Quick Access",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            if (pinnedTools.isNotEmpty()) {
                ExpressiveStatePill(
                    text = "Pinned",
                    icon = Icons.Rounded.PushPin,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(items = pinnedTools.toList()) { index, route ->
                        val tool = categories.flatMap { it.items }.find { it.route == route }
                        if (tool != null) {
                            StaggeredEntrance(index = index) {
                                QuickToolItem(tool, onNavigate)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (recentTools.isNotEmpty()) {
                ExpressiveStatePill(
                    text = "Recent",
                    icon = Icons.Rounded.History,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(items = recentTools.take(6)) { index, route ->
                        val tool = categories.flatMap { it.items }.find { it.route == route }
                        if (tool != null) {
                            StaggeredEntrance(index = index + 3) {
                                QuickToolItem(tool, onNavigate)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            ExpressiveCard(
                onClick = {},
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Long press tools in the dashboard to pin them here for rapid access.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QuickToolItem(tool: ToolItem, onNavigate: (String) -> Unit) {
    val haptic = rememberToolzHapticFeedback()
    
    ExpressiveCard(
        onClick = {
            haptic.click()
            onNavigate(tool.route)
        },
        modifier = Modifier.size(width = 110.dp, height = 120.dp),
        containerColor = tool.color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tool.color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = tool.color.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = tool.color,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
