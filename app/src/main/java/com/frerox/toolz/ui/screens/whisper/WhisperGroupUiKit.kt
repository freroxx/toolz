/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzLoadingIndicator

/**
 * Phase-3 groups UI foundations: every group screen builds from these pieces
 * so banners, loading, empty states, snackbars, fading edges and accessibility
 * labels stay identical across hub, chat and info.
 *
 * Rules for all group UI:
 * - No `android.widget.Toast` — transient feedback goes through [GroupSnackbar].
 * - No `contentDescription = null` on meaningful icons — use [GroupCd].
 * - No raw M3 progress — use [ToolzLoadingIndicator] / wavy indicators.
 * - Long vertical lists get [groupFadingEdge].
 */

/** Animated error / notice banner with an optional retry action. */
@Composable
fun GroupUiBanner(
    message: String,
    visible: Boolean,
    isError: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && message.isNotBlank(),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier,
    ) {
        Surface(
            color = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (actionLabel != null && onAction != null) {
                    ToolzExpressiveTextButton(onClick = onAction) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                } else if (onDismiss != null) {
                    ToolzExpressiveTextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.st_Whisper_Cancel))
                    }
                }
            }
        }
    }
}

/** Centered loading state (expressive indicator, optional label). */
@Composable
fun GroupUiCenterLoading(
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolzLoadingIndicator()
            if (!label.isNullOrBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Small inline spinner for buttons and list footers. */
@Composable
fun GroupUiInlineLoading(modifier: Modifier = Modifier) {
    ToolzLoadingIndicator(modifier = modifier.size(24.dp))
}

/** Snackbar host + helper: the single replacement for every group Toast. */
@Composable
fun GroupSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier)
}

/** Shows a short transient message (fire-and-forget from a scope). */
suspend fun SnackbarHostState.showGroupNotice(message: String) {
    if (message.isBlank()) return
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}

/**
 * Vertical fading edges for long scrollable group content (timeline, member
 * lists, gallery). Content fades under the top/bottom chrome instead of
 * cutting off hard.
 */
fun Modifier.groupFadingEdge(
    top: Boolean = true,
    bottom: Boolean = true,
    fadeHeightDp: Int = 28,
): Modifier = this
    .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = fadeHeightDp.dp.toPx()
        val h = size.height.toFloat()
        if (fadePx <= 0f || h <= 0f) return@drawWithContent
        val stops = buildList {
            if (top) {
                add(0f to Color.Transparent)
                add((fadePx / h).coerceIn(0f, 0.5f) to Color.Black)
            } else {
                add(0f to Color.Black)
            }
            if (bottom) {
                add((1f - fadePx / h).coerceIn(0.5f, 1f) to Color.Black)
                add(1f to Color.Transparent)
            } else {
                add(1f to Color.Black)
            }
        }
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = stops.toTypedArray(),
                startY = 0f,
                endY = h,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Animated wrapper for dialog/dropdown content (enter + exit together). */
@Composable
fun GroupAnimatedReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier,
        content = content,
    )
}

/**
 * Central accessibility labels for every group icon/action. Screens must use
 * these — never `contentDescription = null` on a meaningful control.
 */
object GroupCd {
    @Composable fun back(): String = stringResource(R.string.st_Whisper_Groups_CdBack)
    @Composable fun search(): String = stringResource(R.string.st_Whisper_Groups_CdSearch)
    @Composable fun close(): String = stringResource(R.string.st_Whisper_Groups_CdClose)
    @Composable fun clear(): String = stringResource(R.string.st_Whisper_Groups_CdClear)
    @Composable fun createPoll(): String = stringResource(R.string.st_Whisper_Groups_CdCreatePoll)
    @Composable fun mute(): String = stringResource(R.string.st_Whisper_Groups_CdMute)
    @Composable fun unmute(): String = stringResource(R.string.st_Whisper_Groups_CdUnmute)
    @Composable fun refresh(): String = stringResource(R.string.st_Whisper_Groups_CdRefresh)
    @Composable fun info(): String = stringResource(R.string.st_Whisper_Groups_CdInfo)
    @Composable fun attach(): String = stringResource(R.string.st_Whisper_Groups_CdAttach)
    @Composable fun send(): String = stringResource(R.string.st_Whisper_Groups_CdSend)
    @Composable fun picture(): String = stringResource(R.string.st_Whisper_Groups_CdPicture)
    @Composable fun save(): String = stringResource(R.string.st_Whisper_Groups_CdSave)
    @Composable fun more(): String = stringResource(R.string.st_Whisper_Groups_CdMore)
    @Composable fun approve(): String = stringResource(R.string.st_Whisper_Groups_Approve)
    @Composable fun deny(): String = stringResource(R.string.st_Whisper_Groups_Deny)
    @Composable fun latest(): String = stringResource(R.string.st_Whisper_Groups_CdLatest)
}
