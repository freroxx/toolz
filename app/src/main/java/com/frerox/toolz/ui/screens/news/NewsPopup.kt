package com.frerox.toolz.ui.screens.news

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.R
import com.frerox.toolz.data.news.NewsConstants
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import kotlinx.coroutines.launch

internal data class NewsPriorityStyle(
    val labelRes: Int,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
internal fun newsPriorityStyle(priority: String): NewsPriorityStyle {
    val scheme = MaterialTheme.colorScheme
    return when (priority) {
        "critical" -> NewsPriorityStyle(R.string.st_News_priority_critical, Icons.Rounded.Warning, scheme.errorContainer, scheme.onErrorContainer)
        "feature" -> NewsPriorityStyle(R.string.st_News_priority_feature, Icons.Rounded.AutoAwesome, scheme.primaryContainer, scheme.onPrimaryContainer)
        "fix" -> NewsPriorityStyle(R.string.st_News_priority_fix, Icons.Rounded.Build, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        "promo" -> NewsPriorityStyle(R.string.st_News_priority_promo, Icons.Rounded.LocalOffer, scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> NewsPriorityStyle(R.string.st_News_priority_info, Icons.Rounded.Campaign, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
}

/** Tinted priority pill shared by the popup header and history cards. */
@Composable
internal fun NewsPriorityChip(priority: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val style = newsPriorityStyle(priority)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = style.container
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (small) 8.dp else 10.dp,
                vertical = if (small) 3.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                style.icon,
                contentDescription = null,
                tint = style.content,
                modifier = Modifier.size(if (small) 11.dp else 13.dp)
            )
            Text(
                stringResource(style.labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = style.content
            )
        }
    }
}

private const val DRAG_EXIT_DISTANCE_PX = 2400f

/**
 * Toolz News announcement popup — centered expressive card, same language as
 * the PurgeShot and Clipboard popups (scrim + 36dp card + drag handle).
 *
 * Hierarchy is strict: one filled primary action, one tonal "Later", quiet
 * text links in the footer. Dismiss semantics are preserved:
 * X = dismiss forever, Later/swipe/scrim/back = snooze 24h, CTA = open + close.
 * System back participates: dismissible cards snooze; blocking cards
 * (critical + requiresAction) swallow it. The fullscreen scrim always blocks
 * input below. Pull-to-snooze works even while the body scrolls — a nested
 * scroll connection intercepts only past the content's top edge, and release
 * settles with a spring or flies out with a haptic.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzNewsPopup(
    item: NewsEntity,
    masterNewsEnabled: Boolean,
    onAction: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    val scope = rememberCoroutineScope()
    val currentOnLater by rememberUpdatedState(onLater)
    val blocking = item.requiresAction && item.priority == "critical"
    val canSoftClose = item.dismissible && !blocking
    val style = newsPriorityStyle(item.priority)
    val now = remember(item.id) { System.currentTimeMillis() }
    val todayLabel = stringResource(R.string.st_News_today)
    val yesterdayLabel = stringResource(R.string.st_News_yesterday)
    val date = remember(item.publishAt, now, todayLabel, yesterdayLabel) {
        NewsTime.newsDate(item.publishAt, now, todayLabel, yesterdayLabel)
    }
    var imageState by remember(item.id) { mutableStateOf<AsyncImagePainter.State?>(null) }
    val imageBroken = imageState is AsyncImagePainter.State.Error
    val hasImage = !item.imageUrl.isNullOrBlank() && !imageBroken
    val hasAction = !item.actionUrl.isNullOrBlank()
    val imageModel = remember(item.imageUrl, item.id) {
        if (item.imageUrl.isNullOrBlank()) null
        else ImageRequest.Builder(context)
            .data(item.imageUrl)
            .crossfade(true)
            .build()
    }

    // Pull-down physics: drag translates the card while nested content stays
    // scrollable; release either springs back to rest or flies out and snoozes.
    val pullThresholdPx = with(LocalDensity.current) { 132.dp.toPx() }
    val dragOffset = remember(item.id) { Animatable(0f) }
    val scrollState = rememberScrollState()

    LaunchedEffect(item.id) { dragOffset.snapTo(0f) }

    val pullConnection = remember(canSoftClose, item.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canSoftClose || source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy <= 0f) return Offset.Zero
                // Only pull once the inner scroll has nothing behind its top
                // edge (at rest) — otherwise the content owns the gesture.
                if (scrollState.value > 0) return Offset.Zero
                scope.launch { dragOffset.snapTo(dragOffset.value + dy) }
                return Offset(0f, dy)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pulled = dragOffset.value
                if (pulled > pullThresholdPx) {
                    haptic.tick()
                    dragOffset.animateTo(
                        DRAG_EXIT_DISTANCE_PX,
                        tween(160, easing = CubicBezierEasing(0.35f, 0f, 0.85f, 0.2f))
                    )
                    currentOnLater()
                    return available
                }
                if (pulled != 0f) {
                    dragOffset.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }

    // Back is part of the modal contract: snooze when allowed; swallowed for
    // blocking cards so nothing behind the scrim reacts either.
    BackHandler(enabled = true) {
        if (canSoftClose) onLater()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = context.getString(R.string.st_News_popup_announcement, item.title)
            },
        contentAlignment = Alignment.Center
    ) {
        // Scrim tap = gentle snooze for dismissible cards; for blocking cards
        // it only swallows the tap so the dashboard never reacts underneath.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (canSoftClose) onLater() }
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset.value
                    alpha = (1f - (dragOffset.value / 1400f)).coerceIn(0.55f, 1f)
                }
        ) {
            ExpressiveCard(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 440.dp)
                    .heightIn(max = 600.dp)
                    .nestedScroll(pullConnection),
                shape = RoundedCornerShape(36.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                elevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            .align(Alignment.CenterHorizontally)
                    )

                    // Header: priority tile + label/date + pinned + close.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = style.container
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    style.icon,
                                    contentDescription = null,
                                    tint = style.content,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                stringResource(style.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = style.content
                            )
                            if (date.isNotEmpty()) {
                                Text(
                                    date,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (item.pinned) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = stringResource(R.string.st_News_popup_pinned_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (canSoftClose) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.st_News_popup_dismiss_desc),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (hasImage) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onState = { imageState = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                            )
                        }
                    }

                    MarkdownContent(
                        markdown = item.body,
                        baseFontSize = 15.sp,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        onLinkClick = { url -> NewsLinks.open(context, url) }
                    )

                    // Critical bypass, explained inline — no hidden dialog.
                    if (item.priority == "critical" && !masterNewsEnabled) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    stringResource(R.string.st_News_popup_critical_note),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Actions: exactly one filled primary, optional tonal Later.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasAction) {
                            val deepLink = item.actionUrl!!.startsWith("toolz://")
                            ToolzExpressiveButton(
                                onClick = {
                                    NewsLinks.open(context, item.actionUrl)
                                    onAction()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    if (deepLink) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.actionLabel?.ifBlank { null }
                                        ?: context.getString(R.string.st_News_open),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else {
                            ToolzExpressiveButton(
                                onClick = { if (item.dismissible) onDismiss() else onAction() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.st_News_popup_got_it),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        if (canSoftClose) {
                            ToolzTonalExpressiveButton(
                                onClick = onLater,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.st_News_popup_later),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else if (blocking) {
                            ToolzExpressiveTextButton(
                                onClick = onLater,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(stringResource(R.string.st_News_popup_later), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Footer: quiet navigation links, never competing buttons.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ToolzExpressiveTextButton(onClick = onViewAll) {
                            Text(stringResource(R.string.st_News_popup_all_news), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { NewsLinks.share(context, item) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.st_News_popup_share_desc),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ToolzExpressiveTextButton(
                            onClick = {
                                NewsLinks.open(
                                    context,
                                    "${NewsConstants.WEBSITE_NEWS_URL}#news-${item.id}"
                                )
                            }
                        ) {
                            Text(stringResource(R.string.st_News_popup_website), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
