package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.FileEntry
import com.frerox.toolz.ui.theme.LocalVibrationManager

@Composable fun CleanerGenericFileRow(file: FileEntry, onToggle:(String)->Unit, onOpen:(String)->Unit, isSafe:Boolean){
    val v=LocalVibrationManager.current
    val context = LocalContext.current
    Row(modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).padding(vertical=4.dp, horizontal=4.dp), verticalAlignment=Alignment.CenterVertically){
        CircleSelect(checked=file.isSelected, onToggle={v?.vibrateClick(); onToggle(file.path)})
        Spacer(Modifier.width(12.dp))
        CleanerThumb(data = file.thumbnailUri ?: file.path, ext = file.extension, modifier = Modifier.size(48.dp), corner = 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier=Modifier.weight(1f)){
            Text(file.name, style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp, fontWeight=FontWeight.Medium), maxLines=1, overflow=TextOverflow.Ellipsis)
            Text("${Formatter.formatFileSize(context,file.sizeBytes)} • ${file.extension.uppercase()}", style=MaterialTheme.typography.labelSmall.copy(fontSize=11.sp), color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
