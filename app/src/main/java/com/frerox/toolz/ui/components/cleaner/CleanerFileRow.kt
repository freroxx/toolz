package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.video.VideoFrameDecoder
import com.frerox.toolz.data.cleaner.FileEntry
import com.frerox.toolz.ui.theme.LocalVibrationManager

@Composable fun CleanerGenericFileRow(file: FileEntry, onToggle:(String)->Unit, onOpen:(String)->Unit, isSafe:Boolean){
    val v=LocalVibrationManager.current
    val context = LocalContext.current
    Row(modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical=4.dp, horizontal=4.dp), verticalAlignment=Alignment.CenterVertically){
        Checkbox(checked=file.isSelected, onCheckedChange={v?.vibrateClick(); onToggle(file.path)}, modifier=Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Surface(modifier=Modifier.size(40.dp), shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surfaceContainer) {
            Box(contentAlignment=Alignment.Center){
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(file.thumbnailUri ?: file.path).apply {
                        if (file.extension.lowercase() in setOf("mp4","mkv","avi","mov","webm")) {
                            decoderFactory(VideoFrameDecoder.Factory())
                            size(Size(120,120))
                        }
                    }.build(),
                    contentDescription=null,
                    contentScale = ContentScale.Crop,
                    modifier=Modifier.fillMaxSize(),
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ CircularProgressIndicator(Modifier.size(12.dp), strokeWidth=1.5.dp) } },
                    error = { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(16.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.4f)) } }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier=Modifier.weight(1f)){
            Text(file.name, style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp, fontWeight=FontWeight.Medium), maxLines=1, overflow=TextOverflow.Ellipsis)
            Text("${Formatter.formatFileSize(context,file.sizeBytes)} • ${file.extension.uppercase()}", style=MaterialTheme.typography.labelSmall.copy(fontSize=11.sp), color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun FileThumb(file: FileEntry, modifier:Modifier=Modifier){
    val context = LocalContext.current
    Surface(modifier=modifier, shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surfaceContainer){
        Box(contentAlignment=Alignment.Center, modifier=Modifier.fillMaxSize()){
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(file.thumbnailUri ?: file.path).apply {
                    if (file.extension.lowercase() in setOf("mp4","mkv","avi","mov","webm")) decoderFactory(VideoFrameDecoder.Factory())
                }.build(),
                contentDescription=null,
                contentScale = ContentScale.Crop,
                modifier=Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ CircularProgressIndicator(Modifier.size(12.dp), strokeWidth=1.5.dp) } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(16.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.4f)) } }
            )
        }
    }
}
