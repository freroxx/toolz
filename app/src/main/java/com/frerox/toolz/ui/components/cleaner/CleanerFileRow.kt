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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
// import coil3.video.VideoFrameDecoder
import com.frerox.toolz.data.cleaner.FileEntry
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.theme.LocalVibrationManager

@Composable fun CleanerGenericFileRow(file: FileEntry, onToggle:(String)->Unit, onOpen:(String)->Unit, isSafe:Boolean){
    val v=LocalVibrationManager.current; val accent=if(isSafe) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    Row(modifier=Modifier.fillMaxWidth().clip(MediumExpressiveShape).clickable{ v?.vibrateClick(); onToggle(file.path) }.padding(8.dp), verticalAlignment=Alignment.CenterVertically){
        Checkbox(checked=file.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(file.path)}, colors=CheckboxDefaults.colors(checkedColor=accent))
        FileThumb(file, Modifier.size(44.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier=Modifier.weight(1f)){ Text(file.name, style=MaterialTheme.typography.bodySmall, fontWeight=FontWeight.Medium, maxLines=1, overflow=TextOverflow.Ellipsis); Text("${Formatter.formatFileSize(LocalContext.current,file.sizeBytes)} • ${file.extension.uppercase()}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)}
        IconButton(onClick={v?.vibrateClick(); onOpen(file.path)}, modifier=Modifier.size(36.dp)){ Icon(Icons.Rounded.Visibility,null,Modifier.size(16.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))}
    }
}
@Composable fun FileThumb(file: FileEntry, modifier:Modifier=Modifier){
    val context=LocalContext.current; val isVideo=file.extension.lowercase() in listOf("mp4","mkv","avi","mov","webm","flv")
    Surface(modifier=modifier, shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surfaceContainerHighest){
        Box(contentAlignment=Alignment.Center){
            SubcomposeAsyncImage(model=ImageRequest.Builder(context).data(file.thumbnailUri?:file.path).crossfade(true).build(), contentDescription=null, contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize(), loading={Box(Modifier.fillMaxSize(),Alignment.Center){ CircularProgressIndicator(Modifier.size(14.dp), strokeWidth=2.dp)}}, error={Box(Modifier.fillMaxSize(),Alignment.Center){ Icon(Icons.Rounded.InsertDriveFile,null,Modifier.size(18.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.35f))}})
        }
    }
}
