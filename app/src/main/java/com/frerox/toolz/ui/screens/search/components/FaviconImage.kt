/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.browser.FaviconResolver

/**
 * Multi-tier favicon resolution that avoids leaking every visited domain to
 * a third-party favicon service:
 *
 * 1. An already-cached bitmap for [url]'s host, if the browser has one.
 * 2. The site's own `/favicon.ico`, fetched directly (no third-party proxy).
 * 3. A generic globe glyph if neither is available.
 */
@Composable
fun PrivacyFaviconImage(url: String, size: Dp, modifier: Modifier = Modifier) {
    val cachedBitmap = remember(url) { FaviconResolver.getCachedIcon(url) }
    val directUrl = remember(url) { FaviconResolver.directFaviconUrl(url) }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            cachedBitmap != null -> Image(
                bitmap = cachedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            directUrl != null -> AsyncImage(
                model = directUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = rememberVectorPainter(Icons.Rounded.Language),
                fallback = rememberVectorPainter(Icons.Rounded.Language),
            )
            else -> Icon(
                Icons.Rounded.Language, null,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

/** @suppress kept for source compatibility with older call sites; prefer [PrivacyFaviconImage]. */
@Deprecated("Use PrivacyFaviconImage directly", ReplaceWith("PrivacyFaviconImage(url, size, modifier)"))
@Composable
fun FaviconImage(url: String, size: Dp, modifier: Modifier = Modifier) =
    PrivacyFaviconImage(url, size, modifier)

/** A boxed, rounded-corner favicon — e.g. for tab strips or list leading icons. */
@Composable
fun FaviconDisplay(url: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        PrivacyFaviconImage(url = url, size = 24.dp, modifier = Modifier.fillMaxSize())
    }
}
