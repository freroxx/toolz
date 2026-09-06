package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.ui.theme.LocalVibrationManager

/**
 * One flat row per category: toggle | icon | name + size | open.
 * Tapping anywhere (except the toggle) opens the detail sheet — no hidden gestures.
 * Blocked cards explain why inline with a single Fix action.
 */
@Composable
fun CleanerCategoryCard(
    category: CleanCategory,
    totalCleanableBytes: Long = 0L,
    onToggleItem: (String) -> Unit = {},
    onToggleDuplicate: (String, String) -> Unit = { _, _ -> },
    onToggleAll: (Boolean) -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    onOpenSheet: () -> Unit = {},
    onFix: () -> Unit = {}
) {
    val context = LocalContext.current
    val vibration = LocalVibrationManager.current
    val isEmpty = category.items.isEmpty()
    val allSelected = remember(category.items) {
        category.items.isNotEmpty() && category.items.all { item ->
            when (item) {
                is com.frerox.toolz.data.cleaner.CleanItem.GenericFile -> item.file.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.Corpse -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.Duplicate -> item.group.files.filterIndexed { idx, _ -> idx > 0 }.all { it.isSelected }
                is com.frerox.toolz.data.cleaner.CleanItem.EmptyDir -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.MediaFile -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.ApkFile -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.AppCache -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.UnusedApp -> item.entry.isSelected
            }
        }
    }
    val selCount = remember(category.items) { category.selectedCount() }
    val shareFraction = remember(category.totalSize, totalCleanableBytes) {
        if (totalCleanableBytes > 0) (category.totalSize.toFloat() / totalCleanableBytes).coerceIn(0f, 1f) else 0f
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = { vibration?.vibrateClick(); onOpenSheet() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!isEmpty) {
                    CircleSelect(
                        checked = allSelected,
                        onToggle = {
                            onToggleAll(!allSelected)
                            vibration?.vibrateClick()
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(iconFor(category.icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (selCount > 0 && selCount < category.items.size) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "$selCount/${category.items.size}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (category.blockedReason != null && isEmpty) {
                        Text(category.blockedReason!!, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    } else {
                        val itemsText = if (selCount > 0 && !allSelected) {
                            "${Formatter.formatFileSize(context, category.selectedSize)} selected of ${Formatter.formatFileSize(context, category.totalSize)}"
                        } else {
                            when {
                                category.totalSize > 0 -> Formatter.formatFileSize(context, category.totalSize) + " • ${category.items.size} items" + (if (category.truncatedCount > 0) " • +${category.truncatedCount} more" else "")
                                else -> "${category.items.size} items" + (if (category.truncatedCount > 0) " • +${category.truncatedCount} more" else "")
                            }
                        }
                        Text(
                            itemsText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = if (selCount > 0 && !allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (category.blockedReason != null && isEmpty) {
                    TextButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(category.blockedFixLabel ?: "Fix", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                    }
                } else {
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!isEmpty && shareFraction > 0.02f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { shareFraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                )
            }
        }
    }
}

private fun iconFor(name: String): ImageVector = when (name) {
    "DeleteSweep" -> Icons.Rounded.DeleteSweep; "FileCopy" -> Icons.Rounded.FileCopy; "AutoDelete" -> Icons.Rounded.AutoDelete; "Straighten" -> Icons.Rounded.Straighten; "FolderOff" -> Icons.Rounded.FolderOff; "AppSettingsAlt" -> Icons.Rounded.AppSettingsAlt; "Description" -> Icons.Rounded.Description; "Image" -> Icons.Rounded.Image; "Cached" -> Icons.Rounded.Cached; "Android" -> Icons.Rounded.Android; "Collections" -> Icons.Rounded.Collections; "Screenshot" -> Icons.Rounded.Screenshot; "Storage" -> Icons.Rounded.Storage; else -> Icons.Rounded.Folder
}
