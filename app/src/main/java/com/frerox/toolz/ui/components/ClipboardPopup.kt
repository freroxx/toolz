package com.frerox.toolz.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.MainActivity
import com.frerox.toolz.ui.screens.clipboard.ClipboardViewModel

@Composable
fun ClipboardPopup(
    onDismiss: () -> Unit,
    onManageHistory: () -> Unit,
    viewModel: ClipboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val latestEntry = entries.firstOrNull()
    
    val haptic = rememberToolzHapticFeedback()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() }
        )

        StaggeredEntrance(index = 0) {
            ExpressiveCard(
                onClick = { },
                modifier = Modifier
                    .width(340.dp)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Recent Clipboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (latestEntry != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ExpressiveStatePill(
                                        text = latestEntry.type,
                                        icon = when (latestEntry.type) {
                                            "URL" -> Icons.Rounded.Link
                                            "PHONE" -> Icons.Rounded.Phone
                                            "EMAIL" -> Icons.Rounded.Email
                                            "CODE" -> Icons.Rounded.Code
                                            "ADDRESS" -> Icons.Rounded.Place
                                            else -> Icons.Rounded.ShortText
                                        },
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    
                                    if (latestEntry.isPinned) {
                                        Icon(
                                            Icons.Rounded.PushPin,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = latestEntry.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (latestEntry.summary != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = latestEntry.summary!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No recent history",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolzExpressiveButton(
                            onClick = {
                                latestEntry?.let { entry ->
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Toolz", entry.content))
                                    haptic.success()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = latestEntry != null,
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy", fontWeight = FontWeight.Bold)
                        }

                        ToolzOutlinedExpressiveButton(
                            onClick = {
                                haptic.click()
                                viewModel.clearAll()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = entries.isNotEmpty(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            haptic.click()
                            val intent = Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                putExtra("navigate_to", "clipboard")
                            }
                            context.startActivity(intent)
                            onManageHistory()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Manage Full History", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
