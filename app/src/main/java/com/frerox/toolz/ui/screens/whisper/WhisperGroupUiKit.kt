/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.whisper

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzLoadingIndicator
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Shared foundations for every group screen (hub, chat, info) so banners,
 * loading, sheets, avatars, fading edges, time formatting and accessibility
 * labels stay identical everywhere.
 *
 * Rules for all group UI:
 * - No `android.widget.Toast` — transient feedback goes through [GroupSnackbarHost].
 * - No `contentDescription = null` on meaningful icons — use [GroupCd].
 * - No raw M3 progress — use [ToolzLoadingIndicator] / wavy indicators.
 * - Long scrolling lists use [groupScrollFade] (fades only where content continues).
 */

// ─────────────────────────────────────────────────────────────────────────────
// Banners / loading / snackbars
// ─────────────────────────────────────────────────────────────────────────────

/** Animated error / notice banner with an optional retry action or dismiss. */
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
    // Keep the last text alive during the exit animation.
    var last by remember { mutableStateOf(message) }
    LaunchedEffect(message) { if (message.isNotBlank()) last = message }
    val shown = message.ifBlank { last }

    AnimatedVisibility(
        visible = visible && message.isNotBlank(),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier,
    ) {
        Surface(
            color = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                )
                if (actionLabel != null && onAction != null) {
                    ToolzExpressiveTextButton(onClick = onAction) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                } else if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = GroupCd.close(), modifier = Modifier.size(18.dp))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

/** Shared degraded-log banner: the event log carries an unverifiable event. */
@Composable
fun GroupDegradedBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.st_Whisper_Groups_Degraded),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
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

/** Snackbar host: the single replacement for every group Toast. */
@Composable
fun GroupSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier)
}

/** Shows a short transient message (fire-and-forget from a scope). */
suspend fun SnackbarHostState.showGroupNotice(message: String) {
    if (message.isBlank()) return
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}

/** Animated wrapper for banners / reveal rows (enter + exit together). */
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

/** Full dead-end panel (chat opened but membership never resolved). */
@Composable
fun GroupUiDeadEnd(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolzTonalExpressiveButton(onClick = onBack) {
                Text(stringResource(R.string.st_Whisper_Groups_CdBack))
            }
            ToolzExpressiveButton(onClick = onRetry) {
                Text(
                    stringResource(R.string.st_Whisper_Groups_Retry),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Icon tile + optional message, used for empty states. */
@Composable
fun GroupEmptyPanel(
    icon: ImageVector,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }
        if (!message.isNullOrBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            ToolzTonalExpressiveButton(onClick = onAction) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fading edges
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Static vertical fading edges. Prefer [groupScrollFade] for lists: this one
 * fades unconditionally, which hides content that is resting at an edge.
 */
fun Modifier.groupFadingEdge(
    top: Boolean = true,
    bottom: Boolean = true,
    fadeHeightDp: Int = 28,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = fadeHeightDp.dp.toPx()
        val h = size.height
        if (fadePx <= 0f || h <= 0f) return@drawWithContent
        val f = (fadePx / h).coerceIn(0f, 0.45f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to (if (top) Color.Transparent else Color.Black),
                f to Color.Black,
                (1f - f) to Color.Black,
                1f to (if (bottom) Color.Transparent else Color.Black),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Scroll-aware fading edges: an edge only fades while there is more content
 * beyond it, and the fade eases in/out. With [reverse] (reverseLayout lists)
 * the visual top maps to `canScrollForward`.
 */
fun Modifier.groupScrollFade(
    state: LazyListState,
    reverse: Boolean = false,
    fade: Dp = 24.dp,
): Modifier = composed {
    val canTop by remember(state, reverse) {
        derivedStateOf { if (reverse) state.canScrollForward else state.canScrollBackward }
    }
    val canBottom by remember(state, reverse) {
        derivedStateOf { if (reverse) state.canScrollBackward else state.canScrollForward }
    }
    val topAmount by animateFloatAsState(if (canTop) 1f else 0f, label = "groupFadeTop")
    val bottomAmount by animateFloatAsState(if (canBottom) 1f else 0f, label = "groupFadeBottom")
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val h = size.height
            val px = fade.toPx()
            if (h <= 0f || px <= 0f) return@drawWithContent
            val f = (px / h).coerceIn(0f, 0.45f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 1f - topAmount),
                    f to Color.Black,
                    (1f - f) to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - bottomAmount),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Accessibility labels
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Images + avatars
// ─────────────────────────────────────────────────────────────────────────────

/** Sampled decode: never inflates a 12 MP photo for a 52 dp avatar. */
internal fun decodeGroupBitmap(bytes: ByteArray, maxDim: Int): android.graphics.Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / (sample * 2) >= maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}.getOrNull()

private object GroupBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

/** Discriminates the fullscreen viewer's honest loading / failure states. */
internal sealed interface GroupBitmapState {
    data object Loading : GroupBitmapState
    data class Ready(val bitmap: ImageBitmap) : GroupBitmapState
    data object Failed : GroupBitmapState
}

/**
 * Decodes off the main thread and caches by content, so lazy items that
 * scroll away and back don't decode again. [reloadKey] lets the viewer retry
 * corrupt bytes without inventing a new cache entry.
 */
@Composable
internal fun rememberGroupBitmapState(
    bytes: ByteArray?,
    maxDim: Int = 1024,
    reloadKey: Int = 0,
): GroupBitmapState {
    val cacheKey = remember(bytes, maxDim) {
        bytes?.let { "${it.size}:${it.contentHashCode()}:$maxDim" }
    }
    val loadKey = remember(cacheKey, reloadKey) { cacheKey to reloadKey }
    val state = produceState<GroupBitmapState>(
        initialValue = when {
            cacheKey == null -> GroupBitmapState.Failed
            else -> GroupBitmapCache.get(cacheKey)
                ?.let { GroupBitmapState.Ready(it) }
                ?: GroupBitmapState.Loading
        },
        loadKey,
    ) {
        if (cacheKey == null || bytes == null) {
            value = GroupBitmapState.Failed
            return@produceState
        }
        value = GroupBitmapState.Loading
        val hit = GroupBitmapCache.get(cacheKey)
        if (hit != null) {
            value = GroupBitmapState.Ready(hit)
            return@produceState
        }
        val decoded = withContext(Dispatchers.Default) {
            decodeGroupBitmap(bytes, maxDim)?.asImageBitmap()
        }
        value = if (decoded != null) {
            GroupBitmapCache.put(cacheKey, decoded)
            GroupBitmapState.Ready(decoded)
        } else {
            GroupBitmapState.Failed
        }
    }
    return state.value
}

/**
 * Existing avatar/list call sites keep their contract: null means no picture,
 * still loading, or undecodable, so the initials fallback stays in place.
 */
@Composable
internal fun rememberGroupBitmap(bytes: ByteArray?, maxDim: Int = 1024): ImageBitmap? =
    (rememberGroupBitmapState(bytes, maxDim) as? GroupBitmapState.Ready)?.bitmap

/**
 * Deterministic group / member avatar: the decrypted picture when given,
 * otherwise the initial on a stable tonal container.
 */
@Composable
fun GroupAvatar(
    name: String,
    groupId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    picture: ByteArray? = null,
) {
    val bitmap = rememberGroupBitmap(picture, if (size >= 80.dp) 512 else 256)
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[(groupId.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "G",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.42f).sp),
                fontWeight = FontWeight.Black,
                color = fg,
            )
        }
    }
}

/** Stable accent for a sender (name label + quote bar) in group timelines. */
@Composable
internal fun senderTint(senderId: String): Color {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    return palette[(senderId.hashCode() and Int.MAX_VALUE) % palette.size]
}

/** Friend row with an animated selection badge (create-group + add-members). */
@Composable
internal fun FriendPickRow(
    friend: WhisperProfile,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            WhisperAvatar(profile = friend, size = 44.dp)
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                friend.effectiveName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (friend.username.isNotBlank() && friend.displayName?.isNotBlank() == true) {
                Text(
                    "@${friend.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheets, rows, dialogs, inputs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modal bottom sheet with an animated close handle: call `close()` to slide
 * out and dismiss (use it from action rows instead of dropping the state).
 */
@Composable
fun GroupSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            content(close)
        }
    }
}

/** Sheet title row (centered-left, headline weight). */
@Composable
fun GroupSheetTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Icon + label (+ supporting) tappable row for sheets and settings lists. */
@Composable
fun GroupActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val iconTint = if (destructive) scheme.error else scheme.onSurfaceVariant
    val textColor = if (destructive) scheme.error else scheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Confirm dialog; destructive confirms use the error colors. */
@Composable
fun GroupConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            ToolzExpressiveTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_Whisper_Cancel))
            }
        },
    )
}

/** Pill search field used by chat search, friend pickers and member search. */
@Composable
fun GroupSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = GroupCd.search(),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = GroupCd.clear(), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Section label used above grouped lists on the info screen. */
@Composable
fun GroupSectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Connected-list corner shape: large outer corners, tight inner corners. */
internal fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

/** Keeps the last non-empty list so exit animations don't collapse to blank text. */
@Composable
internal fun <T> rememberLastNonEmpty(list: List<T>): List<T> {
    var last by remember { mutableStateOf(list) }
    LaunchedEffect(list) { if (list.isNotEmpty()) last = list }
    return list.ifEmpty { last }
}

// ─────────────────────────────────────────────────────────────────────────────
// Time formatting (all in the device time zone + locale)
// ─────────────────────────────────────────────────────────────────────────────

internal fun parseGroupInstant(iso: String): ZonedDateTime? = runCatching {
    java.time.OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
}.getOrNull()

/** Local clock time for a message bubble (e.g. 14:05 / 2:05 PM). */
internal fun formatGroupClock(iso: String): String {
    val z = parseGroupInstant(iso) ?: return ""
    return runCatching { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(z) }.getOrDefault("")
}

/** Row timestamp: clock today, Yesterday, else a short local date. */
@Composable
internal fun formatGroupListTime(iso: String): String {
    val yesterday = stringResource(R.string.st_Whisper_Groups_Yesterday)
    val z = parseGroupInstant(iso) ?: return ""
    val today = LocalDate.now()
    val date = z.toLocalDate()
    return runCatching {
        when {
            date == today -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(z)
            date == today.minusDays(1) -> yesterday
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(z)
        }
    }.getOrDefault("")
}

/** Day divider label for a yyyy-MM-dd local date key. */
@Composable
internal fun groupDayLabel(day: String): String {
    val today = stringResource(R.string.st_Whisper_Groups_Today)
    val yesterday = stringResource(R.string.st_Whisper_Groups_Yesterday)
    if (day.isBlank()) return ""
    val d = runCatching { LocalDate.parse(day) }.getOrNull() ?: return ""
    val now = LocalDate.now()
    return when (d) {
        now -> today
        now.minusDays(1) -> yesterday
        else -> runCatching {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(d)
        }.getOrDefault("")
    }
}
