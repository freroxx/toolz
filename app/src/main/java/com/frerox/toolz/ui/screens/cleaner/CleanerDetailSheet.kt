package com.frerox.toolz.ui.screens.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerDetailSheet(category: CleanCategory, onToggleItem:(String)->Unit, onToggleDuplicate:(String,String)->Unit, onOpenFile:(String)->Unit, onDismiss:()->Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(category.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)); Text("${category.items.size} items • ${Formatter.formatFileSize(context, category.totalSize)}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                items(category.items, key = { item -> when(item){ is CleanItem.GenericFile->"f_${item.file.path}"; is CleanItem.Corpse->"c_${item.entry.path}"; is CleanItem.Duplicate->"d_${item.group.hash}"; is CleanItem.EmptyDir->"e_${item.entry.path}"; is CleanItem.MediaFile->"m_${item.entry.path}"; is CleanItem.ApkFile->"a_${item.entry.path}"; is CleanItem.AppCache->"ac_${item.entry.packageName}"; is CleanItem.UnusedApp->"u_${item.entry.packageName}" } }) { item ->
                    MinimalDetailRow(item, onToggleItem, onToggleDuplicate, onOpenFile)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MinimalDetailRow(item: CleanItem, onToggleItem:(String)->Unit, onToggleDup:(String,String)->Unit, onOpen:(String)->Unit) {
    val isSelected = when(item){ is CleanItem.GenericFile->item.file.isSelected; is CleanItem.Corpse->item.entry.isSelected; is CleanItem.EmptyDir->item.entry.isSelected; is CleanItem.MediaFile->item.entry.isSelected; is CleanItem.ApkFile->item.entry.isSelected; is CleanItem.AppCache->item.entry.isSelected; is CleanItem.UnusedApp->item.entry.isSelected; is CleanItem.Duplicate->item.group.files.any{it.isSelected} }
    val name = when(item){ is CleanItem.GenericFile->item.file.name; is CleanItem.Corpse->item.entry.packageName; is CleanItem.EmptyDir->item.entry.name; is CleanItem.MediaFile->item.entry.name; is CleanItem.ApkFile->item.entry.name; is CleanItem.AppCache->item.entry.appName; is CleanItem.UnusedApp->item.entry.appName; is CleanItem.Duplicate->item.group.files.firstOrNull()?.path?.substringAfterLast('/') ?: "Duplicate" }
    val path = when(item){ is CleanItem.GenericFile->item.file.path; is CleanItem.Corpse->item.entry.path; is CleanItem.EmptyDir->item.entry.path; is CleanItem.MediaFile->item.entry.path; is CleanItem.ApkFile->item.entry.path; is CleanItem.AppCache->item.entry.packageName; is CleanItem.UnusedApp->item.entry.packageName; is CleanItem.Duplicate->item.group.hash }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isSelected, onCheckedChange = {
                when(item){
                    is CleanItem.GenericFile->onToggleItem(item.file.path)
                    is CleanItem.Corpse->onToggleItem(item.entry.path)
                    is CleanItem.EmptyDir->onToggleItem(item.entry.path)
                    is CleanItem.MediaFile->onToggleItem(item.entry.path)
                    is CleanItem.ApkFile->onToggleItem(item.entry.path)
                    is CleanItem.AppCache->onToggleItem(item.entry.packageName)
                    is CleanItem.UnusedApp->onToggleItem(item.entry.packageName)
                    is CleanItem.Duplicate->{ val f=item.group.files.find{it.isSelected} ?: item.group.files.lastOrNull(); f?.let{ onToggleDup(item.group.hash, it.path)} }
                }
            }, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), maxLines = 1); Text(path, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}
