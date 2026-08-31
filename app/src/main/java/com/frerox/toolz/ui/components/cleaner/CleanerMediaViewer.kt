package com.frerox.toolz.ui.components.cleaner

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun CleanerMediaViewer(filePath: String, onDismiss: () -> Unit) {
    val file = remember(filePath) { File(filePath) }
    val ext = file.extension.lowercase()
    val isImage = ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif")
    val isVideo = ext in setOf("mp4","mkv","avi","mov","webm","flv")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(android.text.format.Formatter.formatFileSize(LocalContext.current, file.length()), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isImage -> {
                            AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        isVideo -> {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        setVideoPath(file.absolutePath)
                                        val mc = android.widget.MediaController(ctx); mc.setAnchorView(this); setMediaController(mc)
                                        setOnPreparedListener { it.isLooping = false; start() }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            // fallback for docs: show icon
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.6f))
                                Text("No preview", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
                }
            }
        }
    }
}
