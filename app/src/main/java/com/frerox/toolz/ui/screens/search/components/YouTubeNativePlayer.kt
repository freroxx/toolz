/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Native ExoPlayer for YouTube direct stream playback.
 * Configured with YouTube-compatible HTTP data source headers.
 */
@Composable
fun YouTubeNativePlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    onClose: () -> Unit,
    onError: () -> Unit = {},
    autoPlay: Boolean = true,
) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            ))
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e("YouTubeNativePlayer", "Playback error", error)
                        onError()
                    }
                })
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = autoPlay
            }
    }

    DisposableEffect(streamUrl) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.player = exoPlayer }
        )
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
                Icon(Icons.Rounded.Close, stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_native_stop), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Placeholder with thumbnail + loading when streamUrl not yet resolved.
 */
@Composable
fun YouTubeNativeLoading(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (thumbnailUrl != null) {
            coil3.compose.AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.45f
            )
        }
        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White)
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
                Icon(Icons.Rounded.Close, stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_native_stop_short), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
