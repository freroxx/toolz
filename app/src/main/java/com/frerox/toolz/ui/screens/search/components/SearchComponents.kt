package com.frerox.toolz.ui.screens.search.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════
//  SEARCH PILL
// ══════════════════════════════════════════════════════════

@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isIncognito: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester    = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }

    // Auto-focus when active flips to true (e.g., triggered from WebView dock)
    LaunchedEffect(active) {
        if (active) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (active)
            MaterialTheme.colorScheme.surfaceContainerHigh
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(200),
        label = "pillBg",
    )

    Surface(
        modifier      = modifier.height(48.dp),
        shape         = RoundedCornerShape(24.dp),
        color         = containerColor,
        border        = if (active) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        tonalElevation = if (active) 0.dp else 2.dp,
    ) {
        Row(
            modifier          = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isIncognito) {
                Icon(
                    Icons.Rounded.VisibilityOff, null,
                    modifier = Modifier.size(17.dp),
                    tint     = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                Icon(
                    Icons.Rounded.Search, null,
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (active) 0.9f else 0.5f),
                )
            }

            BasicTextField(
                value            = query,
                onValueChange    = onQueryChange,
                modifier         = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle        = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine       = true,
                keyboardOptions  = KeyboardOptions(
                    imeAction    = ImeAction.Search,
                    keyboardType = KeyboardType.Uri,
                ),
                keyboardActions  = KeyboardActions(onSearch = { onSearch(query) }),
                cursorBrush      = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox    = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text  = if (isIncognito) "Incognito search…" else "Search or type a URL",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        }
                        inner()
                    }
                },
                interactionSource = interactionSource,
            )

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit    = scaleOut() + fadeOut(),
            ) {
                IconButton(
                    onClick  = { onQueryChange("") },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.Cancel, null,
                        modifier = Modifier.size(17.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            IconButton(
                onClick  = onSettingsClick,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Rounded.Tune, null,
                    modifier = Modifier.size(17.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }

    // Track focus changes → update active state
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        if (isFocused && !active) onActiveChange(true)
    }
}

// ══════════════════════════════════════════════════════════
//  SECURITY STATUS ROW
// ══════════════════════════════════════════════════════════

@Composable
fun SecurityStatusRow(
    adBlockEnabled: Boolean,
    dnsProvider: String,
    isIncognito: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick        = onClick,
        modifier       = modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(14.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecurityDot(color = if (adBlockEnabled) Color(0xFF4CAF50) else Color(0xFFF44336))
            Text(
                "Ad Block",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecurityDot(color = Color(0xFF2196F3))
            Text(
                dnsProvider.take(10).lowercase().replaceFirstChar(Char::uppercase),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isIncognito) {
                SecurityDot(color = MaterialTheme.colorScheme.tertiary)
                Text(
                    "Incognito",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.ChevronRight, null,
                modifier = Modifier.size(14.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun SecurityDot(color: Color) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(color, CircleShape),
    )
}

// ══════════════════════════════════════════════════════════
//  SOURCE ACCENT COLOURS
// ══════════════════════════════════════════════════════════

@Composable
private fun sourceAccentColor(source: String): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha  = if (isDark) 0.85f else 1f
    return when (source.uppercase()) {
        "GOOGLE"            -> Color(0xFF4285F4).copy(alpha = alpha)
        "DDG", "DUCKDUCKGO" -> Color(0xFFDE5833).copy(alpha = alpha)
        "BRAVE"             -> Color(0xFFFF6000).copy(alpha = alpha)
        "BING"              -> Color(0xFF008272).copy(alpha = alpha)
        else                -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    }
}

private fun sourceLabel(source: String): String = when (source.uppercase()) {
    "GOOGLE"            -> "Google"
    "DDG", "DUCKDUCKGO" -> "DDG"
    "BRAVE"             -> "Brave"
    "BING"              -> "Bing"
    else                -> source.take(8)
}

// ══════════════════════════════════════════════════════════
//  SEARCH RESULT CARD
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic            = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed         by interactionSource.collectIsPressedAsState()
    val accentColor       = sourceAccentColor(result.source)

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "cardScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
                onLongClick       = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        shape          = RoundedCornerShape(20.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isPressed) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            // Left accent bar — engine colour gradient
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.3f))
                        ),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Domain + favicon + engine badge row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PrivacyFaviconImage(url = result.url, size = 16.dp)

                    Text(
                        text     = result.displayUrl,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // Engine badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text       = sourceLabel(result.source),
                            style      = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color      = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                // Breadcrumb (optional)
                if (!result.breadcrumb.isNullOrBlank()) {
                    Text(
                        text     = result.breadcrumb,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(2.dp))

                // Title
                Text(
                    text       = result.title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )

                // Date badge (optional)
                if (!result.date.isNullOrBlank()) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Schedule, null,
                            modifier = Modifier.size(10.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                        Text(
                            text  = result.date,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                // Snippet
                if (result.snippet.isNotBlank()) {
                    Text(
                        text       = result.snippet,
                        style      = MaterialTheme.typography.bodySmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines   = 3,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SHIMMER SKELETON  — pixel-matched to SearchResultCard
// ══════════════════════════════════════════════════════════

@Composable
fun SearchShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(6) { i -> ShimmerCard(delayMs = i * 70) }
    }
}

@Composable
private fun ShimmerCard(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "shimmer$delayMs")
    val progress by transition.animateFloat(
        initialValue  = -0.8f,
        targetValue   = 1.8f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress$delayMs",
    )
    val brush = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        startX = progress * 900f,
        endX   = progress * 900f + 500f,
    )

    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Accent bar placeholder
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .defaultMinSize(minHeight = 98.dp)
                    .background(brush, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Domain row: favicon + displayUrl + badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    ShimmerBox(brush, Modifier.size(16.dp).clip(CircleShape))
                    ShimmerBox(brush, Modifier.fillMaxWidth(0.38f).height(10.dp).clip(RoundedCornerShape(5.dp)))
                    Spacer(Modifier.weight(1f))
                    ShimmerBox(brush, Modifier.width(28.dp).height(14.dp).clip(RoundedCornerShape(6.dp)))
                }
                Spacer(Modifier.height(2.dp))
                // Title — 2 lines
                ShimmerBox(brush, Modifier.fillMaxWidth(0.92f).height(14.dp).clip(RoundedCornerShape(6.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.62f).height(14.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(2.dp))
                // Snippet — 3 lines
                ShimmerBox(brush, Modifier.fillMaxWidth(1.00f).height(11.dp).clip(RoundedCornerShape(5.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(1.00f).height(11.dp).clip(RoundedCornerShape(5.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.66f).height(11.dp).clip(RoundedCornerShape(5.dp)))
            }
        }
    }
}

@Composable
private fun ShimmerBox(brush: Brush, modifier: Modifier) {
    Box(modifier = modifier.background(brush))
}

// ══════════════════════════════════════════════════════════
//  SUGGESTION ROW
// ══════════════════════════════════════════════════════════

@Composable
fun SuggestionRow(
    text: String,
    onSearch: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSearch)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Rounded.TrendingUp, null,
            modifier = Modifier.size(18.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            text,
            modifier = Modifier.weight(1f),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FilledIconButton(
            onClick  = onFill,
            modifier = Modifier.size(34.dp),
            colors   = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(Icons.Rounded.NorthWest, null, modifier = Modifier.size(14.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════
//  HISTORY ROW  — with optional substring highlight
// ══════════════════════════════════════════════════════════

@Composable
fun HistoryRow(
    query: String,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
    highlightQuery: String = "",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSearch)
            .padding(start = 20.dp, end = 4.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Rounded.History, null,
            modifier = Modifier.size(18.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )

        // Bold-highlight the matching substring
        val annotated = remember(query, highlightQuery) {
            buildAnnotatedString {
                if (highlightQuery.isBlank()) {
                    append(query)
                } else {
                    val lower    = query.lowercase()
                    val startIdx = lower.indexOf(highlightQuery.lowercase())
                    if (startIdx >= 0) {
                        append(query.substring(0, startIdx))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(query.substring(startIdx, startIdx + highlightQuery.length))
                        }
                        append(query.substring(startIdx + highlightQuery.length))
                    } else {
                        append(query)
                    }
                }
            }
        }

        Text(
            annotated,
            modifier = Modifier.weight(1f),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Close, null,
                modifier = Modifier.size(16.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  QUICK-ACCESS TILE
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickAccessTile(
    title: String,
    url: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.90f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "tileScale",
    )

    Column(
        modifier = modifier
            .width(68.dp)
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
                onLongClick       = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            modifier       = Modifier.size(52.dp),
            shape          = RoundedCornerShape(18.dp),
            color          = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PrivacyFaviconImage(url = url, size = 26.dp)
            }
        }
        Text(
            title,
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AddQuickAccessTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(68.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape    = RoundedCornerShape(18.dp),
            color    = MaterialTheme.colorScheme.surfaceContainerLow,
            border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Add, null,
                    modifier = Modifier.size(22.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Text(
            "Add",
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
        )
    }
}

// ══════════════════════════════════════════════════════════
//  BOOKMARK CHIP
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkChip(
    title: String,
    url: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.94f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "bmScale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
                onLongClick       = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrivacyFaviconImage(url = url, size = 18.dp)
            Text(
                title,
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  PRIVACY-RESPECTING FAVICON
//  Uses DuckDuckGo's icon service instead of Google's
// ══════════════════════════════════════════════════════════

@Composable
fun PrivacyFaviconImage(url: String, size: Dp, modifier: Modifier = Modifier) {
    val faviconUrl = remember(url) {
        runCatching {
            val host = java.net.URI(url).host ?: return@runCatching null
            "https://icons.duckduckgo.com/ip3/$host.ico"
        }.getOrNull()
    }
    Box(
        modifier         = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (faviconUrl != null) {
            AsyncImage(
                model              = faviconUrl,
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Language, null,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

// Backward-compat alias
@Composable
fun FaviconImage(url: String, size: Dp, modifier: Modifier = Modifier) =
    PrivacyFaviconImage(url, size, modifier)

@Composable
fun FaviconDisplay(url: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(4.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        PrivacyFaviconImage(url = url, size = 24.dp, modifier = Modifier.fillMaxSize())
    }
}

// ══════════════════════════════════════════════════════════
//  ERROR / EMPTY STATE  — animated with breathing rings
// ══════════════════════════════════════════════════════════

enum class ErrorType { NO_RESULTS, NETWORK_ERROR, RATE_LIMITED, GENERIC }

@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    errorType: ErrorType = ErrorType.GENERIC,
    modifier: Modifier = Modifier,
) {
    val breathe = rememberInfiniteTransition(label = "breathe")
    val iconScale by breathe.animateFloat(
        initialValue  = 0.90f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "iconScale",
    )
    val ringAlpha by breathe.animateFloat(
        initialValue  = 0.15f,
        targetValue   = 0.40f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "ringAlpha",
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(400)) + scaleIn(tween(400, easing = FastOutSlowInEasing)),
        modifier = modifier,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Layered pulsing rings
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(128.dp),
            ) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .scale(iconScale * 0.92f)
                        .background(errorContainerFor(errorType).copy(alpha = ringAlpha * 0.6f), CircleShape),
                )
                // Mid ring
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(errorContainerFor(errorType).copy(alpha = 0.28f), CircleShape),
                )
                // Icon surface
                Surface(
                    shape    = CircleShape,
                    color    = errorContainerFor(errorType).copy(alpha = 0.62f),
                    modifier = Modifier.size(64.dp).scale(iconScale),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = iconFor(errorType),
                            contentDescription = null,
                            modifier           = Modifier.size(28.dp),
                            tint               = errorTintFor(errorType),
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                title,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                textAlign  = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                message,
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(16.dp),
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (errorType == ErrorType.NETWORK_ERROR)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(retryLabelFor(errorType))
            }
        }
    }
}

@Composable
private fun errorContainerFor(type: ErrorType): Color = when (type) {
    ErrorType.NETWORK_ERROR -> MaterialTheme.colorScheme.errorContainer
    ErrorType.RATE_LIMITED  -> MaterialTheme.colorScheme.tertiaryContainer
    ErrorType.NO_RESULTS    -> MaterialTheme.colorScheme.secondaryContainer
    else                    -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun errorTintFor(type: ErrorType): Color = when (type) {
    ErrorType.NETWORK_ERROR -> MaterialTheme.colorScheme.error
    ErrorType.RATE_LIMITED  -> MaterialTheme.colorScheme.tertiary
    ErrorType.NO_RESULTS    -> MaterialTheme.colorScheme.secondary
    else                    -> MaterialTheme.colorScheme.onSurface
}

private fun iconFor(type: ErrorType) = when (type) {
    ErrorType.NETWORK_ERROR -> Icons.Rounded.WifiOff
    ErrorType.RATE_LIMITED  -> Icons.Rounded.HourglassEmpty
    ErrorType.NO_RESULTS    -> Icons.Rounded.SearchOff
    else                    -> Icons.Rounded.ErrorOutline
}

private fun retryLabelFor(type: ErrorType): String = when (type) {
    ErrorType.NETWORK_ERROR -> "Check connection"
    ErrorType.RATE_LIMITED  -> "Try again later"
    else                    -> "Try again"
}

// ══════════════════════════════════════════════════════════
//  LOAD-MORE FOOTER
// ══════════════════════════════════════════════════════════

@Composable
fun LoadMoreFooter(isLoading: Boolean) {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color       = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  BOOKMARK CARD
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkCard(
    title: String,
    url: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = remember(url) {
        runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
    val surfaceColor = remember(host) {
        val hash = host.hashCode()
        val hue  = (hash % 360).let { if (it < 0) it + 360 else it }.toFloat()
        Color.hsl(hue, 0.25f, 0.45f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "bmCardScale")

    Surface(
        modifier = modifier
            .width(160.dp)
            .height(110.dp)
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
                onLongClick       = onLongClick,
            ),
        shape          = RoundedCornerShape(20.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(listOf(surfaceColor, surfaceColor.copy(alpha = 0.7f)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    host.take(1).uppercase(),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White.copy(alpha = 0.5f),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(Color.White, CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PrivacyFaviconImage(url = url, size = 20.dp)
                }
            }
            Column(
                modifier            = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  FLOATING SEARCH DOCK  V2  — tab strip + WebView pill
// ══════════════════════════════════════════════════════════

@Composable
fun FloatingSearchDock(
    tabCount: Int,
    onManageTabs: () -> Unit,
    onNewTab: () -> Unit,
    // Home mode: pass tab list to show icon strip
    tabs: List<TabEntry> = emptyList(),
    activeTabId: String? = null,
    onTabClick: ((TabEntry) -> Unit)? = null,
    // WebView mode: pass current URL + click handler
    currentUrl: String? = null,
    onSearchClick: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isWebViewMode = onSearchClick != null && currentUrl != null
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.05f,
        targetValue   = 0.20f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label         = "pulseAlpha",
    )

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .height(76.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 25f) {
                        onSwipeDown?.invoke()
                        change.consume()
                    }
                }
            },
        shape           = RoundedCornerShape(38.dp),
        color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation  = 16.dp,
        shadowElevation = 24.dp,
        border          = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier              = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── LEFT: New Tab ────────────────────────────────────────────────
            Surface(
                onClick   = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNewTab()
                },
                shape     = CircleShape,
                color     = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                modifier  = Modifier.size(54.dp),
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add, "New Tab",
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            // ── MIDDLE ───────────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (isWebViewMode) {
                    // WebView: Expressive URL pill
                    Surface(
                        onClick  = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSearchClick?.invoke()
                        },
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                shape = CircleShape,
                            ),
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Search, null,
                                modifier = Modifier.size(20.dp),
                                tint     = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text     = currentUrl?.let { safeHostFromUrl(it) } ?: "Search or type URL",
                                style    = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color    = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    // Home / Search screen: horizontal tab icon strip
                    if (tabs.isNotEmpty()) {
                        LazyRow(
                            modifier              = Modifier.fillMaxSize(),
                            verticalAlignment     = Alignment.CenterVertically,
                            contentPadding        = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = tabs.takeLast(10),
                                key   = { it.id },
                            ) { tab ->
                                val isActive = tab.id == activeTabId
                                val tabScale by animateFloatAsState(
                                    targetValue   = if (isActive) 1.2f else 1.0f,
                                    animationSpec = spring(Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label         = "tabScale_${tab.id}",
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier         = Modifier
                                        .size(44.dp)
                                        .scale(tabScale)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                        .border(
                                            width = if (isActive) 2.5.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTabClick?.invoke(tab)
                                        },
                                ) {
                                    PrivacyFaviconImage(url = tab.url, size = 26.dp)
                                }
                            }
                        }
                    } else {
                        // No tabs yet — subtle drag handle
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }

            // ── RIGHT: Tab count / manager ───────────────────────────────────
            Surface(
                onClick  = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onManageTabs()
                },
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Layers, "Tabs",
                        tint     = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                    if (tabCount > 0) {
                        Surface(
                            shape    = CircleShape,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(22.dp)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text       = if (tabCount > 99) "99" else tabCount.toString(),
                                    style      = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color      = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SHARED HELPERS
// ══════════════════════════════════════════════════════════

internal fun safeHostFromUrl(url: String): String = try {
    java.net.URI(url).host?.removePrefix("www.") ?: url
} catch (_: Exception) { url }

// ══════════════════════════════════════════════════════════
//  SECTION HEADER
// ══════════════════════════════════════════════════════════

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick        = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SELECTABLE FILTER CHIP
// ══════════════════════════════════════════════════════════

@Composable
fun SelectableFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveFilterChip(
        selected    = selected,
        onClick     = onClick,
        label       = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier    = modifier,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
        } else null,
    )
}

// ══════════════════════════════════════════════════════════
//  SETTINGS TOGGLE ROW
// ══════════════════════════════════════════════════════════

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leadingIcon != null) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape    = RoundedCornerShape(12.dp),
                color    = if (checked) MaterialTheme.colorScheme.primaryContainer
                else         MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) { leadingIcon() }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ══════════════════════════════════════════════════════════
//  FADING EDGES
// ══════════════════════════════════════════════════════════

fun Modifier.fadingEdges(fadeSize: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = fadeSize.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                fade / size.height to Color.Black,
                1f - fade / size.height to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
