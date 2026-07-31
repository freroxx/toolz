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
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
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
        modifier      = modifier.height(56.dp),
        shape         = RoundedCornerShape(28.dp),
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
    latency: Long? = null,
) {
    val isNextDns = dnsProvider == "NEXTDNS"
    val isProtected = adBlockEnabled || isNextDns

    Surface(
        onClick        = onClick,
        modifier       = modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(28.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecurityDot(color = if (isProtected) Color(0xFF4CAF50) else Color(0xFFF44336))
            
            Text(
                text = if (isNextDns) "NextDNS Protection" else "Ad Block Enabled",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isProtected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )

            VerticalDivider(modifier = Modifier.height(14.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Icon(
                if (isNextDns) Icons.Rounded.Dns else Icons.Rounded.Shield,
                null,
                modifier = Modifier.size(16.dp),
                tint = if (isProtected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    dnsProvider.lowercase().replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                if (latency != null) {
                    val color = when {
                        latency < 50  -> Color(0xFF4CAF50)
                        latency < 150 -> Color(0xFFFFC107)
                        else          -> Color(0xFFF44336)
                    }
                    Text(
                        "${latency}ms",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        fontWeight = FontWeight.Bold,
                        color = color.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 4.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    )
                }
            }

            if (isIncognito) {
                SecurityDot(color = MaterialTheme.colorScheme.tertiary)
                Text(
                    "Private",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            Icon(
                Icons.Rounded.ChevronRight, null,
                modifier = Modifier.size(16.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
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
internal fun sourceAccentColor(source: String): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha  = if (isDark) 0.85f else 1f
    return when (source.uppercase()) {
        "GOOGLE"               -> Color(0xFF4285F4).copy(alpha = alpha)
        "DDG", "DUCKDUCKGO"   -> Color(0xFFDE5833).copy(alpha = alpha)
        "BRAVE"                -> Color(0xFFFF6000).copy(alpha = alpha)
        "BING"                 -> Color(0xFF008272).copy(alpha = alpha)
        "ECOSIA"               -> Color(0xFF2B8A2B).copy(alpha = alpha)
        "SWISSCOWS"            -> Color(0xFFD9253E).copy(alpha = alpha)
        "STARTPAGE"            -> Color(0xFF3F4EAE).copy(alpha = alpha)
        else                   -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    }
}

private fun sourceLabel(source: String): String = when (source.uppercase()) {
    "GOOGLE"               -> "Google"
    "DDG", "DUCKDUCKGO"   -> "DuckDuckGo"
    "BRAVE"                -> "Brave"
    "BING"                 -> "Bing"
    "ECOSIA"               -> "Ecosia"
    "SWISSCOWS"            -> "Swisscows"
    "STARTPAGE"            -> "Startpage"
    "WEB"                  -> "Web"
    else                   -> source.take(10).replaceFirstChar { it.uppercase() }
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

                    // Engine / Consensus badge
                    if (result.engines.size >= 2) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text       = "Found on ${result.engines.joinToString(", ")}",
                                style      = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else {
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
//  SYNTHETIC CATEGORY CARD (Images / Videos)
// ══════════════════════════════════════════════════════════

/**
 * Full-width card shown for Image and Video category results.
 * Since search engines render these in JavaScript, we provide
 * a direct "open in browser" link to the native search UI.
 */
@Composable
fun SyntheticCategoryCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = sourceAccentColor(result.source)
    val isVideo     = result.url.contains("video") || result.url.contains("vid=")

    ExpressiveCard(
        onClick        = onClick,
        modifier       = modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(24.dp),
        containerColor = accentColor.copy(alpha = 0.08f),
        contentColor   = MaterialTheme.colorScheme.onSurface,
        elevation      = 0.dp,
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.OndemandVideo else Icons.Rounded.Image,
                            contentDescription = null,
                            tint     = accentColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        result.title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        result.displayUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor.copy(alpha = 0.8f),
                    )
                }
            }

            Text(
                result.snippet,
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            ToolzExpressiveButton(
                onClick  = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open ${if (isVideo) "Videos" else "Images"} in Browser")
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

@OptIn(ExperimentalLayoutApi::class)
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
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Rounded.History, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )

        val annotated = remember(query, highlightQuery) {
            buildAnnotatedString {
                if (highlightQuery.isBlank()) {
                    append(query)
                } else {
                    val lower = query.lowercase()
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Close, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
fun RecentSearchSection(
    history: List<com.frerox.toolz.data.search.SearchHistoryEntry>,
    onSearch: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Recent searches",
            actionLabel = if (history.isNotEmpty()) "Clear all" else null,
            onAction = onClearAll,
        )

        if (history.isEmpty()) {
            EmptyHistory()
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.take(12).forEach { entry ->
                    RecentSearchChip(
                        query = entry.query,
                        onClick = { onSearch(entry.query) },
                        onDelete = { onDelete(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentSearchChip(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.History,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = query,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ManageSearch, null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Text(
            "Start your first search",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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

enum class ErrorType { NO_RESULTS, OFFLINE, DNS_ERROR, NETWORK_ERROR, RATE_LIMITED, GENERIC }

@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    errorType: ErrorType = ErrorType.GENERIC,
    onReturnToDashboard: (() -> Unit)? = null,
    onOpenDnsSettings: (() -> Unit)? = null,
    onOpenEngineSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(300)) + slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 4 },
        modifier = modifier,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Animated icon cluster
            val breathe = rememberInfiniteTransition(label = "breathe")
            val pulse by breathe.animateFloat(
                initialValue  = 0.92f,
                targetValue   = 1.06f,
                animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label         = "pulse",
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(110.dp),
            ) {
                // Outer halo
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulse)
                        .background(
                            errorContainerFor(errorType).copy(alpha = 0.18f),
                            CircleShape,
                        ),
                )
                // Icon surface
                Surface(
                    shape    = CircleShape,
                    color    = errorContainerFor(errorType).copy(alpha = 0.55f),
                    modifier = Modifier.size(68.dp).scale(pulse * 0.97f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = iconFor(errorType),
                            contentDescription = null,
                            modifier           = Modifier.size(30.dp),
                            tint               = errorTintFor(errorType),
                        )
                    }
                }
            }

            // Title + message inside an ExpressiveCard
            ExpressiveCard(
                onClick          = {},
                modifier         = Modifier.fillMaxWidth(),
                shape            = RoundedCornerShape(28.dp),
                containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor     = MaterialTheme.colorScheme.onSurface,
                elevation        = 0.dp,
            ) {
                Column(
                    modifier            = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        textAlign  = TextAlign.Center,
                    )
                    Text(
                        message,
                        style      = MaterialTheme.typography.bodySmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign  = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }

            // Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier            = Modifier.fillMaxWidth(),
            ) {
                // Primary contextual action
                when {
                    errorType == ErrorType.OFFLINE && onReturnToDashboard != null -> {
                        ToolzExpressiveButton(
                            onClick  = onReturnToDashboard,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Go home")
                        }
                    }
                    errorType == ErrorType.DNS_ERROR && onOpenDnsSettings != null -> {
                        ToolzExpressiveButton(
                            onClick  = onOpenDnsSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.Rounded.Dns, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fix DNS settings")
                        }
                    }
                    errorType == ErrorType.RATE_LIMITED && onOpenEngineSettings != null -> {
                        ToolzExpressiveButton(
                            onClick  = onOpenEngineSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Switch search engine")
                        }
                    }
                    else -> {}
                }

                // Retry / secondary action
                ToolzOutlinedExpressiveButton(
                    onClick  = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(retryLabelFor(errorType))
                }
            }
        }
    }
}

@Composable
private fun errorContainerFor(type: ErrorType): Color = when (type) {
    ErrorType.OFFLINE, ErrorType.NETWORK_ERROR -> MaterialTheme.colorScheme.errorContainer
    ErrorType.DNS_ERROR, ErrorType.RATE_LIMITED -> MaterialTheme.colorScheme.tertiaryContainer
    ErrorType.NO_RESULTS    -> MaterialTheme.colorScheme.secondaryContainer
    else                    -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun errorTintFor(type: ErrorType): Color = when (type) {
    ErrorType.OFFLINE, ErrorType.NETWORK_ERROR -> MaterialTheme.colorScheme.error
    ErrorType.DNS_ERROR, ErrorType.RATE_LIMITED -> MaterialTheme.colorScheme.tertiary
    ErrorType.NO_RESULTS    -> MaterialTheme.colorScheme.secondary
    else                    -> MaterialTheme.colorScheme.onSurface
}

private fun iconFor(type: ErrorType) = when (type) {
    ErrorType.OFFLINE, ErrorType.NETWORK_ERROR -> Icons.Rounded.CloudOff
    ErrorType.DNS_ERROR     -> Icons.Rounded.Dns
    ErrorType.RATE_LIMITED  -> Icons.Rounded.HourglassEmpty
    ErrorType.NO_RESULTS    -> Icons.Rounded.SearchOff
    else                    -> Icons.Rounded.ErrorOutline
}

private fun retryLabelFor(type: ErrorType): String = when (type) {
    ErrorType.OFFLINE       -> "Retry connection"
    ErrorType.NETWORK_ERROR -> "Check connection"
    ErrorType.DNS_ERROR     -> "Retry DNS query"
    ErrorType.RATE_LIMITED  -> "Try again"
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

// ══════════════════════════════════════════════════════════
//  INSTANT MATH UTILITY CARD
// ══════════════════════════════════════════════════════════

@Composable
fun InstantMathCard(
    mathResult: com.frerox.toolz.ui.screens.search.MathResult,
    onCopy: (String) -> Unit,
    onOpenCalculator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.frerox.toolz.ui.components.ExpressiveCard(
        onClick = onOpenCalculator,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Instant Calculation",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Local",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${mathResult.expression} = ${mathResult.result}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                com.frerox.toolz.ui.components.ToolzExpressiveButton(
                    onClick = onOpenCalculator,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Calculator")
                }

                com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton(
                    onClick = { onCopy(mathResult.result) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SEARCH CATEGORY FILTER CHIPS
// ══════════════════════════════════════════════════════════

@Composable
fun SearchCategoryChips(
    selectedCategory: com.frerox.toolz.data.search.SearchCategory,
    onCategorySelected: (com.frerox.toolz.data.search.SearchCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(com.frerox.toolz.data.search.SearchCategory.entries.toTypedArray()) { cat ->
            val isSelected = cat == selectedCategory
            val label = when (cat) {
                com.frerox.toolz.data.search.SearchCategory.ALL -> "All"
                com.frerox.toolz.data.search.SearchCategory.IMAGES -> "Images"
                com.frerox.toolz.data.search.SearchCategory.NEWS -> "News"
                com.frerox.toolz.data.search.SearchCategory.VIDEOS -> "Videos"
            }
            val icon = when (cat) {
                com.frerox.toolz.data.search.SearchCategory.ALL -> Icons.Rounded.Search
                com.frerox.toolz.data.search.SearchCategory.IMAGES -> Icons.Rounded.Image
                com.frerox.toolz.data.search.SearchCategory.NEWS -> Icons.Rounded.Newspaper
                com.frerox.toolz.data.search.SearchCategory.VIDEOS -> Icons.Rounded.OndemandVideo
            }

            ExpressiveFilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(cat) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
fun InlineSearchWebView(
    result: com.frerox.toolz.data.search.SearchResult,
    category: com.frerox.toolz.data.search.SearchCategory,
    adBlockEnabled: Boolean,
    onOpenInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = sourceAccentColor(result.source)
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

    ExpressiveCard(
        onClick = { /* WebView handles interaction */ },
        modifier = modifier
            .fillMaxWidth()
            .height(520.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Site Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrivacyFaviconImage(url = result.url, size = 22.dp)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = result.displayUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (result.engines.size >= 2) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    ) {
                        Icon(
                            Icons.Rounded.AutoFixHigh,
                            null,
                            modifier = Modifier.padding(6.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                ToolzTonalExpressiveIconButton(
                    onClick = { onOpenInBrowser(result.url) },
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                }
            }

            // WebView Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                databaseEnabled = true
                            }
                            webViewClient = object : com.frerox.toolz.util.network.AdBlockWebViewClient(
                                adBlockEnabled = { adBlockEnabled }
                            ) {
                                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                }
                            }
                            loadUrl(result.url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
                        color = accentColor,
                        trackColor = Color.Transparent
                    )
                }
            }
            
            // Bottom Action Bar (Subtle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (category == com.frerox.toolz.data.search.SearchCategory.VIDEOS) 
                            Icons.Rounded.OndemandVideo else Icons.Rounded.Image,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (category == com.frerox.toolz.data.search.SearchCategory.VIDEOS) "Video Result" else "Image Result",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                
                ToolzExpressiveTextButton(
                    onClick = { onOpenInBrowser(result.url) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("View Full Site", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

