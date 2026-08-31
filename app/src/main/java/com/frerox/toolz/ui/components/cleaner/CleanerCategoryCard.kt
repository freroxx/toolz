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

package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.theme.LocalVibrationManager

private val kSuccess = Color(0xFF4CAF50)
private val kSuccessDim = Color(0xFF2E7D32)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CleanerCategoryCard(category: CleanCategory, onToggleItem: (String)->Unit, onToggleDuplicate: (String,String)->Unit, onOpenFile: (String)->Unit, onLongPress: ()->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val vibration = LocalVibrationManager.current
    val borderColor by animateColorAsState(if (expanded) MaterialTheme.colorScheme.primary.copy(alpha=0.3f) else if (category.isSafeToClean && (category.totalSize>0 || category.items.isNotEmpty())) kSuccess.copy(alpha=0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.15f), spring(Spring.DampingRatioMediumBouncy), label="catBorderV2")
    val surfaceColor by animateColorAsState(if (category.isSafeToClean && (category.totalSize>0 || category.items.isNotEmpty())) kSuccess.copy(alpha=0.05f) else MaterialTheme.colorScheme.surfaceContainerHigh, label="catSurfV2")
    Surface(modifier=Modifier.fillMaxWidth(), shape=SquircleShape, color=surfaceColor, border=BorderStroke(1.dp, borderColor)) {
        Column {
            Row(modifier=Modifier.fillMaxWidth().combinedClickable(onClick={ vibration?.vibrateClick(); if (category.totalSize>0 || category.items.isNotEmpty()) expanded=!expanded }, onLongClick={ vibration?.vibrateLongClick(); onLongPress() }).padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
                val iconAccent = if (category.totalSize>0 || category.items.isNotEmpty()) { if (category.isSafeToClean) kSuccess else MaterialTheme.colorScheme.primary } else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(modifier=Modifier.size(48.dp), shape=RoundedCornerShape(16.dp), color=iconAccent.copy(alpha=0.14f)) { Box(contentAlignment=Alignment.Center) { Icon(iconForCategory(category.icon), null, Modifier.size(24.dp), tint=iconAccent) } }
                Spacer(Modifier.width(14.dp))
                Column(modifier=Modifier.weight(1f)) {
                    Text(category.name, style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.Bold)
                    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(if (category.totalSize>0) Formatter.formatFileSize(context, category.totalSize) else "${category.items.size} items", style=MaterialTheme.typography.labelMedium, color=if (category.totalSize>0 || category.items.isNotEmpty()) iconAccent else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight=FontWeight.Bold)
                        if (category.isSafeToClean && (category.totalSize>0 || category.items.isNotEmpty())) {
                            Surface(shape=CircleShape, color=kSuccess.copy(alpha=0.18f)) { Text("SAFE", modifier=Modifier.padding(horizontal=7.dp, vertical=1.dp), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Black, color=kSuccessDim) }
                        }
                    }
                    if (category.description!=null) Text(category.description, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
                }
                if (category.items.isNotEmpty()) {
                    Surface(shape=CircleShape, color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.padding(end=4.dp)) { Text("${category.items.size}", modifier=Modifier.padding(horizontal=8.dp, vertical=3.dp), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Black, color=MaterialTheme.colorScheme.onSecondaryContainer) }
                }
                val rot by animateFloatAsState(if (expanded) 180f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label="expRotV2")
                Icon(Icons.Rounded.ExpandMore, null, tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f), modifier=Modifier.size(22.dp).graphicsLayer { rotationZ=rot })
            }
            AnimatedVisibility(visible=expanded, enter=expandVertically(spring(Spring.DampingRatioMediumBouncy))+fadeIn(), exit=shrinkVertically()+fadeOut()) {
                Column(modifier=Modifier.padding(start=8.dp, end=8.dp, bottom=12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    val preview=remember(category.items){ category.items.take(4) }
                    preview.forEach { item ->
                        when (item) {
                            is CleanItem.GenericFile -> CleanerGenericFileRowCompact(file=item.file, onToggle=onToggleItem, onOpen=onOpenFile, isSafe=category.isSafeToClean)
                            is CleanItem.Corpse -> CleanerCorpseRowCompact(entry=item.entry, onToggle=onToggleItem)
                            is CleanItem.Duplicate -> DuplicatePreviewRow(group=item.group, onToggle=onToggleDuplicate, onOpen=onOpenFile)
                            is CleanItem.EmptyDir -> CleanerEmptyDirRowCompact(entry=item.entry, onToggle=onToggleItem)
                            is CleanItem.MediaFile -> CleanerMediaRowCompact(entry=item.entry, onToggle=onToggleItem, onOpen=onOpenFile)
                            is CleanItem.ApkFile -> CleanerApkRowCompact(entry=item.entry, onToggle=onToggleItem)
                            is CleanItem.AppCache -> CleanerAppCacheRowCompact(entry=item.entry, onToggle=onToggleItem)
                            is CleanItem.UnusedApp -> UnusedPreviewRow(entry=item.entry, onToggle=onToggleItem)
                        }
                    }
                    if (category.items.size>preview.size) {
                        TextButton(onClick={ vibration?.vibrateTick(); onLongPress() }, modifier=Modifier.fillMaxWidth()) { Text("View all ${category.items.size} items", fontWeight=FontWeight.Bold, style=MaterialTheme.typography.labelMedium); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
}
@Composable private fun CleanerGenericFileRowCompact(file: com.frerox.toolz.data.cleaner.FileEntry, onToggle:(String)->Unit, onOpen:(String)->Unit, isSafe:Boolean) {
    val vibration=LocalVibrationManager.current; val accent=if(isSafe) kSuccess else MaterialTheme.colorScheme.primary
    Row(modifier=Modifier.fillMaxWidth().padding(6.dp), verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked=file.isSelected, onCheckedChange={ vibration?.vibrateClick(); onToggle(file.path) }, colors=CheckboxDefaults.colors(checkedColor=accent))
        Spacer(Modifier.width(8.dp))
        Column(modifier=Modifier.weight(1f)) { Text(file.name, style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.Medium, maxLines=1); Text("${Formatter.formatFileSize(LocalContext.current, file.sizeBytes)} • ${file.extension.uppercase()}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick={ vibration?.vibrateClick(); onOpen(file.path) }, modifier=Modifier.size(32.dp)){ Icon(Icons.Rounded.Visibility, null, Modifier.size(16.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)) }
    }
}
@Composable private fun CleanerCorpseRowCompact(entry: com.frerox.toolz.data.cleaner.CorpseEntry, onToggle:(String)->Unit) {
    val v=LocalVibrationManager.current
    Row(modifier=Modifier.fillMaxWidth().padding(6.dp), verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=entry.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(entry.path)}, colors=CheckboxDefaults.colors(checkedColor=kSuccess)); Spacer(Modifier.width(8.dp)); Column(modifier=Modifier.weight(1f)){ Text(entry.packageName, style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.Medium, maxLines=1); Text("${Formatter.formatFileSize(LocalContext.current, entry.sizeBytes)} • ${entry.type.name}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)}}
}
@Composable private fun CleanerEmptyDirRowCompact(entry: com.frerox.toolz.data.cleaner.EmptyDirEntry, onToggle:(String)->Unit){ val v=LocalVibrationManager.current; Row(modifier=Modifier.fillMaxWidth().padding(6.dp), verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=entry.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(entry.path)}); Spacer(Modifier.width(8.dp)); Text(entry.path, style=MaterialTheme.typography.bodySmall, maxLines=1, modifier=Modifier.weight(1f))}}
@Composable private fun CleanerMediaRowCompact(entry: com.frerox.toolz.data.cleaner.MediaEntry, onToggle:(String)->Unit, onOpen:(String)->Unit){ val v=LocalVibrationManager.current; Row(modifier=Modifier.fillMaxWidth().padding(6.dp), verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=entry.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(entry.path)}); Spacer(Modifier.width(8.dp)); Column(modifier=Modifier.weight(1f)){ Text(entry.name, style=MaterialTheme.typography.bodySmall, maxLines=1); Text("${Formatter.formatFileSize(LocalContext.current, entry.sizeBytes)} • ${entry.type.name}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)}; IconButton(onClick={onOpen(entry.path)}, modifier=Modifier.size(32.dp)){ Icon(Icons.Rounded.Visibility, null, Modifier.size(16.dp))}}}
@Composable private fun CleanerApkRowCompact(entry: com.frerox.toolz.data.cleaner.ApkEntry, onToggle:(String)->Unit){ val v=LocalVibrationManager.current; Row(modifier=Modifier.fillMaxWidth().padding(6.dp), verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=entry.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(entry.path)}); Spacer(Modifier.width(8.dp)); Column(modifier=Modifier.weight(1f)){ Text(entry.name, style=MaterialTheme.typography.bodySmall, maxLines=1); Text("${Formatter.formatFileSize(LocalContext.current, entry.sizeBytes)} ${entry.packageName?:""}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)}}}
@Composable private fun CleanerAppCacheRowCompact(entry: com.frerox.toolz.data.cleaner.AppCacheEntry, onToggle:(String)->Unit){ val v=LocalVibrationManager.current; Row(modifier=Modifier.fillMaxWidth().padding(6.dp), verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=entry.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(entry.packageName)}); Spacer(Modifier.width(8.dp)); Column(modifier=Modifier.weight(1f)){ Text(entry.appName, style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.Medium, maxLines=1); Text(Formatter.formatFileSize(LocalContext.current, entry.cacheBytes), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable private fun DuplicatePreviewRow(group: com.frerox.toolz.data.cleaner.DuplicateGroup, onToggle:(String,String)->Unit, onOpen:(String)->Unit){ val v=LocalVibrationManager.current; Column(modifier=Modifier.fillMaxWidth().padding(6.dp)){ Text(group.files.firstOrNull()?.path?.substringAfterLast('/')?:"Duplicate", style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.Bold, maxLines=1); Text("${group.files.size} copies • ${Formatter.formatFileSize(LocalContext.current, group.sizeBytes)}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(4.dp)); group.files.take(2).forEachIndexed{ idx,f-> Row(verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=f.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(group.hash,f.path)}, modifier=Modifier.size(20.dp), colors=CheckboxDefaults.colors(checkedColor=MaterialTheme.colorScheme.error)); Spacer(Modifier.width(6.dp)); Text(f.path, style=MaterialTheme.typography.labelSmall, maxLines=1, modifier=Modifier.weight(1f), color=if(idx==0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)}}}}
@Composable private fun UnusedPreviewRow(entry: com.frerox.toolz.data.cleaner.UnusedAppEntry, onToggle:(String)->Unit){ val v=LocalVibrationManager.current; Row(modifier=Modifier.fillMaxWidth().padding(6.dp), verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=entry.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(entry.packageName)}); Spacer(Modifier.width(8.dp)); Text(entry.appName, style=MaterialTheme.typography.bodySmall, modifier=Modifier.weight(1f), maxLines=1)}}
private fun iconForCategory(name:String): androidx.compose.ui.graphics.vector.ImageVector = when(name){ "DeleteSweep"->Icons.Rounded.DeleteSweep; "FileCopy"->Icons.Rounded.FileCopy; "AutoDelete"->Icons.Rounded.AutoDelete; "Straighten"->Icons.Rounded.Straighten; "FolderOff"->Icons.Rounded.FolderOff; "AppSettingsAlt"->Icons.Rounded.AppSettingsAlt; "Description"->Icons.Rounded.Description; "Image"->Icons.Rounded.Image; "Cached"->Icons.Rounded.Cached; "Android"->Icons.Rounded.Android; "Collections"->Icons.Rounded.Collections; else->Icons.Rounded.Folder }
