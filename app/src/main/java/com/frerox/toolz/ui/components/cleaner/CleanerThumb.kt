package com.frerox.toolz.ui.components.cleaner

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.video.VideoFrameDecoder
import com.frerox.toolz.data.cleaner.FileEntry

private val IMAGE_EXT = setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif")
private val VIDEO_EXT = setOf("mp4","mkv","avi","mov","webm","flv")

fun isPreviewable(ext: String) = ext.lowercase() in IMAGE_EXT || ext.lowercase() in VIDEO_EXT

/**
 * Shared thumbnail: real Coil image/video-frame preview when possible,
 * expressive tinted file-type tile otherwise. Used by every cleaner row
 * so lists never show blank boxes.
 */
@Composable
fun CleanerThumb(
    data: Any?,
    ext: String,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp
) {
    val e = ext.lowercase()
    Surface(modifier = modifier, shape = RoundedCornerShape(corner), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (data != null && isPreviewable(e)) {
                val context = LocalContext.current
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(data).apply {
                        if (e in VIDEO_EXT) {
                            decoderFactory(VideoFrameDecoder.Factory())
                            size(Size(160, 160))
                        }
                    }.build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) } },
                    error = { TypeTile(e) }
                )
            } else {
                TypeTile(e)
            }
        }
    }
}

@Composable
private fun TypeTile(ext: String) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = when (ext.lowercase()) {
        in IMAGE_EXT -> Icons.Rounded.Image to cs.primary
        in VIDEO_EXT -> Icons.Rounded.VideoFile to cs.tertiary
        in setOf("mp3","wav","m4a","ogg","flac","aac") -> Icons.Rounded.AudioFile to cs.secondary
        "apk","apks","xapk" -> Icons.Rounded.Android to cs.primary
        "zip","rar","7z","tar","gz","apks","xapk" -> Icons.Rounded.Archive to cs.tertiary
        "pdf","doc","docx","xls","xlsx","ppt","pptx","txt" -> Icons.Rounded.Description to cs.secondary
        else -> Icons.Rounded.InsertDriveFile to cs.onSurfaceVariant.copy(alpha = 0.55f)
    }
    Surface(shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.13f), modifier = Modifier.size(30.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp), tint = tint) }
    }
}

/** Real launcher icon for an installed package, cached per composition. */
@Composable
fun AppIconThumb(packageName: String, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val context = LocalContext.current
    val drawable: Drawable? = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(corner), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (drawable != null) {
                AsyncImage(model = drawable, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Android, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun FolderThumb(modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    Surface(modifier = modifier, shape = RoundedCornerShape(corner), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.13f), modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Folder, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.tertiary) }
            }
        }
    }
}

/** Back-compat wrappers used by existing rows. */
@Composable fun FileThumb(file: FileEntry, modifier: Modifier = Modifier) {
    CleanerThumb(data = file.thumbnailUri ?: file.path, ext = file.extension, modifier = modifier)
}
