package com.frerox.toolz.ui.components.cleaner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.SubcomposeAsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CleanerMediaViewer(
    filePath: String,
    isSelected: Boolean? = null,
    onToggleSelect: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val file = remember(filePath) { File(filePath) }
    val ext = file.extension.lowercase()
    val isImage = ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    val isVideo = ext in setOf("mp4", "mkv", "avi", "mov", "webm", "flv")

    val resolution = remember(filePath, isImage) {
        if (!isImage || !file.exists()) ""
        else {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(filePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}×${opts.outHeight}" else ""
            } catch (_: Exception) { "" }
        }
    }

    val meta = remember(filePath, resolution) {
        val size = try { Formatter.formatFileSize(context, file.length()) } catch (_: Exception) { "" }
        val date = try { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(file.lastModified())) } catch (_: Exception) { "" }
        listOf(resolution, size, ext.uppercase(), date).filter { it.isNotBlank() }.joinToString("  •  ")
    }

    fun contentUri(): Uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse { Uri.fromFile(file) }

    fun copyPath() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("File path", filePath))
        Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
    }

    // Zoom & Pan state for images
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight()
        ) {
            Column(Modifier.padding(16.dp)) {
                // Header
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CleanerThumb(data = if (isImage || isVideo) file.absolutePath else null, ext = ext, modifier = Modifier.size(46.dp), corner = 15.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(meta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { copyPath() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy path", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Media Preview Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isImage -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (scale > 1f) {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                } else {
                                                    scale = 2.5f
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            if (scale > 1f) {
                                                val maxX = 300f * (scale - 1f)
                                                val maxY = 300f * (scale - 1f)
                                                offset = Offset(
                                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                                )
                                            } else {
                                                offset = Offset.Zero
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        ),
                                    contentScale = ContentScale.Fit,
                                    loading = { CircularProgressIndicator(Modifier.size(28.dp), color = Color.White.copy(alpha = 0.8f), strokeWidth = 3.dp) },
                                    error = { Text("Couldn't load image", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                            if (scale > 1f) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                ) {
                                    Text(
                                        "${(scale * 100).toInt()}%",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        isVideo -> {
                            DisposableEffect(filePath) {
                                var vv: android.widget.VideoView? = null
                                onDispose { try { vv?.stopPlayback() } catch (_: Exception) {} }
                            }
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { c ->
                                    android.widget.VideoView(c).apply {
                                        setVideoPath(file.absolutePath)
                                        val mc = android.widget.MediaController(c); mc.setAnchorView(this); setMediaController(mc)
                                        setOnPreparedListener { it.isLooping = false; start() }
                                        setOnErrorListener { _, _, _ -> true }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                onRelease = { vv -> try { vv.stopPlayback() } catch (_: Exception) {} }
                            )
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CleanerThumb(data = null, ext = ext, modifier = Modifier.size(72.dp), corner = 22.dp)
                                Text("No preview for this file type", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected != null && onToggleSelect != null) {
                        FilledTonalButton(
                            onClick = onToggleSelect,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(
                                if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSelected) "Selected" else "Select", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val uri = contentUri()
                                val mime = context.contentResolver.getType(uri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Share"))
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Rounded.IosShare, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val uri = contentUri()
                                val mime = context.contentResolver.getType(uri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}
