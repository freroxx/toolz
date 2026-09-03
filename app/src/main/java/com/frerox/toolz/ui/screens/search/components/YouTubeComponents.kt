/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage

/** Extracts a YouTube video id from any watch/short/youtu.be URL shape. */
fun youTubeVideoId(url: String): String? = when {
    url.contains("youtube.com/watch", ignoreCase = true) ->
        url.substringAfter("v=", "").substringBefore("&").takeIf { it.length == 11 }
    url.contains("youtu.be/", ignoreCase = true) ->
        url.substringAfter("youtu.be/", "").substringBefore("?").takeIf { it.length == 11 }
    url.contains("youtube.com/shorts/", ignoreCase = true) ->
        url.substringAfter("shorts/", "").substringBefore("?").takeIf { it.length == 11 }
    else -> null
}

/**
 * Inline YouTube player rendered INSIDE the video result card — it replaces the
 * thumbnail in place, keeping the card's position and size so the list doesn't
 * jump. Autoplays a privacy-enhanced (youtube-nocookie.com) embed.
 *
 * The [onRelease] cleanup is critical: leaving composition (scroll away, tab
 * switch, close) stops the WebView and frees the player, restoring the card to
 * its pre-play state.
 */
@Composable
fun YouTubeInlinePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    thumbnailUrl: String? = null,
    onTryNative: (() -> Unit)? = null,
    onOpenInBrowser: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var isReady by remember(videoId) { mutableStateOf(false) }
    var hasError153 by remember(videoId) { mutableStateOf(false) }
    val thumbUrl = thumbnailUrl?.takeIf { it.isNotBlank() } ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    // Regex for exact Error 153 match — avoids false positives from URLs or log lines
    // that happen to contain the digits "153" (e.g. a CDN path or unrelated error code).
    val error153Regex = remember { Regex("\\b153\\b") }

    val embedHtml = remember(videoId) {
        """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"><style>html,body{margin:0;padding:0;background:#000;overflow:hidden;height:100%;width:100%}iframe{width:100%;height:100%;border:0;position:absolute;top:0;left:0;background:#000}</style></head><body><iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&fs=1&rel=0&controls=1" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen" allowfullscreen frameborder="0"></iframe></body></html>"""
    }

    val webView = remember(videoId) {
        WebView(context).apply {
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(android.graphics.Color.BLACK)
            visibility = android.view.View.VISIBLE
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = false
            }
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            try { CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) } catch (_: Exception) {}

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 90 && !isReady) isReady = true
                }
                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                    try { request?.grant(request.resources) } catch (_: Exception) { try { request?.deny() } catch (_: Exception) {} }
                }
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    val msg = consoleMessage?.message() ?: ""
                    android.util.Log.d("YouTubeEmbed", "console: $msg @ ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}")
                    if (msg.contains("Error 153", ignoreCase = true) || msg.contains("error_code=153")) {
                        android.util.Log.e("YouTubeEmbed", "Detected Error 153 for $videoId")
                        hasError153 = true
                        isReady = true
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!isReady && url != null && url != "about:blank") isReady = true
                    view?.requestFocus()
                }
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        android.util.Log.e("YouTubeEmbed", "onReceivedError ${error?.description} for $videoId")
                        hasError153 = true
                        isReady = true
                    }
                }
                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? = null
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean = false
            }

            loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "utf-8", null)
            post { requestFocus() }
        }
    }

    DisposableEffect(videoId) {
        isReady = false
        hasError153 = false
        onDispose {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.destroy()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
        // Mutually exclusive overlays: hasError153 takes precedence, else transient !isReady placeholder
        // No AsyncImage when isReady && !hasError153 — ensures no thumbnail behind playing video
        if (hasError153) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                    AsyncImage(model = thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(110.dp).background(Color.Black, RoundedCornerShape(12.dp)), alpha = 0.85f)
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_error_title), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_error_sub), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha=0.70f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Button(onClick = { hasError153=false; isReady=false; webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "utf-8", null) }, shape = RoundedCornerShape(12.dp)) { Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_retry), fontWeight = FontWeight.Bold) }
                        if (onOpenInBrowser != null) {
                            androidx.compose.material3.OutlinedButton(onClick = onOpenInBrowser, shape = RoundedCornerShape(12.dp)) { Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_open), color = Color.White) }
                        }
                    }
                    if (onTryNative != null) {
                        androidx.compose.material3.FilledTonalButton(onClick = onTryNative, shape = RoundedCornerShape(12.dp)) { Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_native)) }
                    }
                }
            }
        }
        // Transient placeholder cross-fades out once isReady — slower fade (400ms) prevents the
        // black flash that occurs when the iframe painted frame hasn't GPU-composited yet.
        AnimatedVisibility(
            visible = !isReady && !hasError153,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White.copy(alpha = 0.90f),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
        // Close button: visual size kept at 30dp but wrapped in a 48dp touch-target Box
        // to meet the Android accessibility minimum tap-target requirement.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .size(30.dp),
            ) {
                Icon(Icons.Rounded.Close, "Stop player", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Full-screen YouTube overlay — used by SearchScreen's activeVideoId state when
 * the user taps a video result (before inline card migration). Shows title bar
 * with close + open-in-browser actions and the inline player below.
 * Kept for backward compatibility; new cards use [YouTubeInlinePlayer] directly.
 */
@Composable
fun YouTubeEmbedOverlay(
    videoId: String,
    title: String,
    onDismiss: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = title.ifBlank { "YouTube" },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onOpenInBrowser,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                ) {
                    Text(
                        "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            YouTubeInlinePlayer(
                videoId = videoId,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClose = onDismiss,
            )
        }
    }
}

/**
 * YouTube video download quality sheet — M3 expressive. Ranges 1080p (max) down
 * to 240p (min); the chosen quality routes into the yt-dlp video worker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeDownloadSheet(
    title: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val qualities = listOf(
        "1080p" to stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_q_fullhd),
        "720p" to stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_q_hd),
        "480p" to stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_q_sd),
        "360p" to stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_q_low),
        "240p" to stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_q_saver),
    )
    var isDownloading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Rounded.Download, null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_download_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_choose_quality),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            qualities.forEach { (label, subtitle) ->
                // Gray out the entire row once any download is in progress
                val rowAlpha = if (isDownloading) 0.40f else 1f
                Surface(
                    onClick = { if (!isDownloading) { isDownloading = true; onDownload(label) } },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .alpha(rowAlpha),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.HighQuality, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.Download, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_audio_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val mp3Alpha = if (isDownloading) 0.40f else 1f
            Surface(
                onClick = { if (!isDownloading) { isDownloading = true; onDownload("MP3") } },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .alpha(mp3Alpha),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Rounded.MusicNote, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_mp3), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_mp3_sub),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Rounded.AudioFile, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Size disclaimer — file sizes depend on video duration and cannot be predicted
            Text(
                text = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_yt_filesize_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}
