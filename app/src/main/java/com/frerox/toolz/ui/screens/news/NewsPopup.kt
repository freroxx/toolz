package com.frerox.toolz.ui.screens.news

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzNewsPopup(
    item: NewsEntity,
    masterNewsEnabled: Boolean,
    onAction: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    var showCriticalInfo by remember { mutableStateOf(false) }
    val blocking = item.requiresAction && item.priority == "critical"

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(item.priority.uppercase(), fontWeight = FontWeight.Black) },
                    leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                if (item.pinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                // Critical-bypass explainer: only when master is OFF and this critical still showed.
                if (item.priority == "critical" && !masterNewsEnabled) {
                    IconButton(onClick = {
                        haptic.click()
                        showCriticalInfo = true
                    }) {
                        Icon(Icons.Rounded.Info, contentDescription = "Why am I seeing this?")
                    }
                }
                if (item.dismissible && !blocking) {
                    IconButton(onClick = {
                        haptic.click()
                        onDismiss()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            if (!item.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
            Spacer(Modifier.height(12.dp))
            MarkdownContent(
                markdown = item.body,
                baseFontSize = 17.sp,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                onLinkClick = { url ->
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: Exception) { }
                }
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!item.actionUrl.isNullOrBlank()) {
                    ToolzExpressiveButton(
                        onClick = {
                            haptic.click()
                            try {
                                // https:// opens in browser, toolz:// deep-links into the app.
                                context.startActivity(Intent(Intent.ACTION_VIEW, item.actionUrl!!.toUri()))
                            } catch (_: Exception) { }
                            onAction()
                        },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(item.actionLabel?.ifBlank { "Open" } ?: "Open", fontWeight = FontWeight.Bold)
                    }
                }
                if (item.dismissible && !blocking) {
                    ToolzExpressiveButton(
                        onClick = {
                            haptic.click()
                            onLater()
                        },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("Later", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        haptic.click()
                        onViewAll()
                    }
                ) {
                    Text("View all news →")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        haptic.click()
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    com.frerox.toolz.data.news.NewsConstants.WEBSITE_NEWS_URL.toUri()
                                )
                            )
                        } catch (_: Exception) { }
                    }
                ) {
                    Text("Read on website ↗")
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }

    if (blocking) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
            title = { Text(item.title, fontWeight = FontWeight.Black) },
            text = { sheetContent() },
            confirmButton = {
                ToolzExpressiveButton(onClick = {
                    haptic.success()
                    onAction()
                }) { Text(item.actionLabel?.ifBlank { "Got it" } ?: "Got it", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.click()
                    onLater()
                }) { Text("Later") }
            }
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = { if (item.dismissible) onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            sheetContent()
        }
    }

    if (showCriticalInfo) {
        AlertDialog(
            onDismissRequest = { showCriticalInfo = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = { Text("Why am I seeing this?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Critical announcements always show, even when Toolz News is turned off, " +
                        "so you never miss outages or security notices. " +
                        "Re-enable news in Settings → Toolz News to get everything again."
                )
            },
            confirmButton = {
                TextButton(onClick = { showCriticalInfo = false }) { Text("Got it") }
            }
        )
    }
}
