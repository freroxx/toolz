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

package com.frerox.toolz.ui.screens.pdf.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.pdf.PdfRenderEngine
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.theme.SquircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared PDF cover — single source of truth for every thumbnail in the app
 * (vault grid/list, recents carousel, reader strip, notes attachments).
 *
 * Dark-aware placeholder, lazy render off the main thread, no hardcoded white
 * cards. Callers own shape/border; the image itself just fills.
 */
@Composable
fun PdfCover(
    uri: Uri,
    renderEngine: PdfRenderEngine?,
    modifier: Modifier = Modifier,
    widthPx: Int = 320,
    shape: Shape = MediumExpressiveShape,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderTint: Color = MaterialTheme.colorScheme.primary,
    /** Bump to force a re-render (e.g. after a file-access grant). */
    reloadKey: Any? = null
) {
    val context = LocalContext.current
    var bitmap by remember(uri, reloadKey) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, widthPx, reloadKey) {
        if (renderEngine != null) {
            bitmap = try {
                withContext(Dispatchers.IO) { renderEngine.renderThumbnail(uri, widthPx) }
            } catch (e: Exception) {
                android.util.Log.w("PdfCover", "thumbnail failed for $uri: ${e.message}")
                null
            }
            if (bitmap == null) {
                android.util.Log.w("PdfCover", "thumbnail unavailable for $uri")
            }
        } else {
            // Fallback for previews without DI (notes legacy path): direct render.
            // Handles file:// via direct FD so app-private copies always preview.
            bitmap = withContext(Dispatchers.IO) {
                try {
                    val pfd = if (uri.scheme == "file") {
                        val f = java.io.File(uri.path ?: return@withContext null)
                        if (!f.isFile || !f.canRead()) return@withContext null
                        android.os.ParcelFileDescriptor.open(
                            f, android.os.ParcelFileDescriptor.MODE_READ_ONLY
                        )
                    } else {
                        context.contentResolver.openFileDescriptor(uri, "r")
                    } ?: return@withContext null
                    pfd.use {
                        android.graphics.pdf.PdfRenderer(it).use { renderer ->
                            if (renderer.pageCount <= 0) return@withContext null
                            renderer.openPage(0).use { page ->
                                val w = 320
                                val h = (w * page.height.toFloat() / page.width.toFloat()).toInt()
                                val bmp = Bitmap.createBitmap(w, h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Icon(
                Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = placeholderTint.copy(alpha = 0.55f)
            )
        }
    }
}

internal fun formatPdfSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1_048_576.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}

internal fun formatPdfDate(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    return try {
        val ms = if (epochSeconds < 1_000_000_000_000L) epochSeconds * 1000 else epochSeconds
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(ms))
    } catch (_: Exception) {
        ""
    }
}
