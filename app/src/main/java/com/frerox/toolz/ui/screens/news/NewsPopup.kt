package com.frerox.toolz.ui.screens.news

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.frerox.toolz.data.news.NewsConstants
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class PriorityStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
private fun priorityStyle(priority: String): PriorityStyle {
    val scheme = MaterialTheme.colorScheme
    return when (priority) {
        "critical" -> PriorityStyle("Critical", Icons.Rounded.Warning, scheme.errorContainer, scheme.onErrorContainer)
        "feature" -> PriorityStyle("Feature", Icons.Rounded.AutoAwesome, scheme.primaryContainer, scheme.onPrimaryContainer)
        "fix" -> PriorityStyle("Fix", Icons.Rounded.Build, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        "promo" -> PriorityStyle("Promo", Icons.Rounded.LocalOffer, scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> PriorityStyle("Info", Icons.Rounded.Campaign, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
}

private fun formatNewsPopupDate(publishAt: Long): String {
    return try {
        DateTimeFormatter.ofPattern("MMM d, yyyy")
            .format(Instant.ofEpochMilli(publishAt).atZone(ZoneId.systemDefault()))
    } catch (_: Exception) {
        ""
    }
}

/**
 * Toolz News announcement popup — centered expressive card, same language as
 * the PurgeShot and Clipboard popups (scrim + 36dp card + drag handle).
 *
 * Hierarchy is strict: one filled primary action, one tonal "Later", quiet
 * text links in the footer. Dismiss semantics are preserved:
 * X = dismiss forever, Later/swipe/scrim = snooze 24h, CTA = open + close.
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
    val blocking = item.requiresAction && item.priority == "critical"
    val canSoftClose = item.dismissible && !blocking
    val style = priorityStyle(item.priority)
    val date = remember(item.publishAt) { formatNewsPopupDate(item.publishAt) }
    var imageState by remember(item.id) { mutableStateOf<AsyncImagePainter.State?>(null) }
    val imageBroken = imageState is AsyncImagePainter.State.Error
    val hasImage = !item.imageUrl.isNullOrBlank() && !imageBroken
    val hasAction = !item.actionUrl.isNullOrBlank()
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
        }
    }

    fun share() {
        try {
            val link = "${NewsConstants.WEBSITE_NEWS_URL}#news-${item.id}"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, item.title)
                putExtra(Intent.EXTRA_TEXT, "${item.title}\n\n$link")
            }
            context.startActivity(Intent.createChooser(send, "Share news"))
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .semantics { contentDescription = "Announcement: ${item.title}" },
        contentAlignment = Alignment.Center
    ) {
        // Scrim tap = gentle snooze, never a permanent dismiss.
        if (canSoftClose) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLater
                    )
            )
        }
        Box(
            modifier = if (canSoftClose) {
                Modifier
                    .offset(y = dragOffsetY.dp)
                    .graphicsLayer {
                        alpha = (1f - (kotlin.math.abs(dragOffsetY) / 520f)).coerceIn(0.6f, 1f)
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (kotlin.math.abs(dragOffsetY) > 120) {
                                    haptic.tick()
                                    onLater()
                                } else dragOffsetY = 0f
                            }
                        ) { _, dragAmount -> dragOffsetY += dragAmount * 0.55f }
                    }
            } else Modifier
        ) {
            ExpressiveCard(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 440.dp)
                    .heightIn(max = 600.dp),
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
                        .verticalScroll(rememberScrollState()),
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
                                style.label,
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
                                contentDescription = "Pinned announcement",
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
                                    contentDescription = "Dismiss announcement",
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
                                model = item.imageUrl,
                                contentDescription = item.title,
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
                        onLinkClick = ::openUrl
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
                                    "News is off, but critical alerts always come through.",
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
                                    openUrl(item.actionUrl!!)
                                    onAction()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .semantics { contentDescription = item.actionLabel?.ifBlank { "Open" } ?: "Open" },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    if (deepLink) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.actionLabel?.ifBlank { "Open" } ?: "Open",
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
                                    "Got it",
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
                                    "Later",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else if (blocking) {
                            ToolzExpressiveTextButton(
                                onClick = onLater,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Later", fontWeight = FontWeight.SemiBold)
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
                            Text("All news", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = ::share, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = "Share announcement",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ToolzExpressiveTextButton(
                            onClick = {
                                openUrl("${NewsConstants.WEBSITE_NEWS_URL}#news-${item.id}")
                            }
                        ) {
                            Text("Website", fontWeight = FontWeight.SemiBold)
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
