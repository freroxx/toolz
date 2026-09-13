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

package com.frerox.toolz.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.ai.AiConfig
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.DeepDiveState
import com.frerox.toolz.data.search.SearchResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object AiDesign {
    @Composable fun glassColor() = if (isSystemInDarkTheme())
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    else
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)

    @Composable fun glassBorder() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    @Composable fun surfaceColor() = MaterialTheme.colorScheme.surface
    @Composable fun cardColor()    = MaterialTheme.colorScheme.surfaceContainer

    @Composable fun textColor(alpha: Float = 1f) =
        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    fun providerColor(provider: String) = when (provider) {
        "Gemini"   -> Color(0xFF1A73E8)
        "ChatGPT"  -> Color(0xFF10A37F)
        "Claude"   -> Color(0xFFD97757)
        "DeepSeek" -> Color(0xFF007BFF)
        "Groq"     -> Color(0xFFF55036)
        "OpenRouter" -> Color(0xFF8E55EA)
        "OpenCode Zen" -> Color(0xFF6366F1)
        "OpenCode Go" -> Color(0xFF0EA5E9)
        else       -> null
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedChatBubble(
    message: AiMessage,
    currentConfig: AiConfig? = null,
    performanceMode: Boolean = false,
    isCoach: Boolean = false,
    onRegenerate: ((Int) -> Unit)? = null,
    onLinkClick: (String) -> Unit = {},
    onLongPress: (AiMessage) -> Unit = {},
    onShowSources: ((AiMessage) -> Unit)? = null,
    onDeepDive: ((AiMessage) -> Unit)? = null,
    onDismissDeepDive: ((AiMessage) -> Unit)? = null,
) {
    val isUser   = message.isUser
    val segments = parseMarkdownToSegments(message.text)

    val sources = remember(message.searchSources) {
        if (message.searchSources.isNullOrBlank()) emptyList<SearchResult>()
        else runCatching {
            val moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val type  = Types.newParameterizedType(List::class.java, SearchResult::class.java)
            moshi.adapter<List<SearchResult>>(type).fromJson(message.searchSources) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    var showReactions by remember { mutableStateOf(false) }

    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = if (performanceMode) fadeIn()
        else fadeIn(tween(380)) + scaleIn(initialScale = 0.88f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + slideInVertically(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), initialOffsetY = { 24 }),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Row(
                verticalAlignment    = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 14.dp),
            ) {
                if (!isUser) {
                    if (isCoach) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.AutoAwesome, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        AiAvatar(currentConfig, 34.dp, performanceMode = performanceMode)
                    }
                }

                val bubbleShape = if (isUser) {
                    RoundedCornerShape(topStart = 22.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
                } else {
                    RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
                }

                Surface(
                    shape  = bubbleShape,
                    color  = if (isUser) MaterialTheme.colorScheme.primary else AiDesign.glassColor(),
                    border = if (isUser) null else BorderStroke(1.dp, AiDesign.glassBorder()),
                    shadowElevation = if (performanceMode) 0.dp else 5.dp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .bouncyClick(
                            onClick = { showReactions = !showReactions },
                            onLongClick = { onLongPress(message) },
                        ),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        segments.forEach { seg ->
                            MarkdownSegment(
                                seg           = seg,
                                baseFontSize  = 16.sp,
                                modifier      = Modifier.padding(vertical = 3.dp),
                                textColor     = if (isUser) MaterialTheme.colorScheme.onPrimary else AiDesign.textColor(),
                                onLinkClick   = onLinkClick,
                            )
                        }

                        if (sources.isNotEmpty() && onShowSources != null) {
                            HorizontalDivider(
                                Modifier.padding(vertical = 10.dp), 0.5.dp,
                                (if (isUser) MaterialTheme.colorScheme.onPrimary else AiDesign.textColor()).copy(alpha = 0.12f),
                            )
                            SourcesPill(sources = sources, isUser = isUser, onClick = { onShowSources(message) })
                        }
                        // Timestamp footer — subtle, helps scan long chats
                        Text(
                            remember(message.timestamp) {
                                val diff = System.currentTimeMillis() - message.timestamp
                                when {
                                    diff < 60_000L -> "just now"
                                    diff < 3_600_000L -> "${diff / 60_000L}m ago"
                                    diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
                                    else -> java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(message.timestamp))
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (isUser) MaterialTheme.colorScheme.onPrimary else AiDesign.textColor()).copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 6.dp).align(Alignment.End),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showReactions && !isUser,
                enter   = expandVertically(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                exit    = shrinkVertically(tween(180)) + fadeOut(),
                modifier = Modifier.padding(start = 58.dp, top = 4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Only actions that do something: regenerate + copy/menu.
                    // (Thumbs up/down collected nothing — removed, no dead UI.)
                    listOf("🔁", "📋").forEachIndexed { i, emoji ->
                        Surface(
                            onClick = {
                                when (i) {
                                    0 -> onRegenerate?.invoke(message.id)
                                    1 -> onLongPress(message)
                                }
                                showReactions = false
                            },
                            shape = MediumExpressiveShape,
                            color = AiDesign.glassColor(),
                            border = BorderStroke(1.dp, AiDesign.glassBorder()),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            if (!isUser && message.canDeepDive && onDeepDive != null) {
                AnimatedContent(
                    targetState = message.deepDiveState,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "deepDive",
                    modifier = Modifier.padding(start = 58.dp, end = 14.dp, top = 6.dp),
                ) { ddState ->
                    when (ddState) {
                        DeepDiveState.PENDING -> Surface(
                            onClick = { onDeepDive(message) },
                            shape = MediumExpressiveShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                                Text("Deep Dive — search all sources", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DeepDiveState.IN_PROGRESS -> Surface(
                            shape = MediumExpressiveShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolzWavyCircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
                                Text("Diving deep…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedAiInputBar(
    inputText: String,
    isLoading: Boolean,
    selectedImage: Bitmap? = null,
    supportsVision: Boolean = false,
    performanceMode: Boolean = false,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAttach: (() -> Unit)? = null,
    onRemoveImage: (() -> Unit)? = null,
    aiSearchEnabled: Boolean = true,
    aiSearchIconVisible: Boolean = true,
    onToggleAiSearch: (() -> Unit)? = null,
    placeholder: String = "Ask anything…",
    modifier: Modifier = Modifier
) {
    // Photo attach is offered only to vision-capable models. There is no
    // document path (no dead menu items) — unsupported models get no button.
    val showAttach = supportsVision && onAttach != null
    val canSend      = inputText.isNotBlank() || selectedImage != null
    val isIdle       = inputText.isEmpty() && !isLoading && selectedImage == null

    val infiniteTransition = rememberInfiniteTransition(label = "inputGlow")
    val glowAlpha by if (isIdle && !performanceMode) {
        infiniteTransition.animateFloat(0.04f, 0.18f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "glow")
    } else remember { mutableStateOf(0f) }

    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.88f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "sendScale",
    )

    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, AiDesign.surfaceColor().copy(alpha = 0.95f), AiDesign.surfaceColor()),
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        AnimatedVisibility(
            visible = selectedImage != null,
            enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
            exit  = scaleOut(tween(220)) + fadeOut(),
        ) {
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .size(72.dp)
                    .clip(MediumExpressiveShape)
                    .border(1.dp, AiDesign.glassBorder(), MediumExpressiveShape),
            ) {
                AsyncImage(
                    model = selectedImage, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                Surface(
                    onClick = onRemoveImage ?: {},
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(12.dp), tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(visible = inputText.length > 200) {
            Text(
                text = "${inputText.length} chars",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, end = 4.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (inputText.length > 800) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
            shape = BouncyShape,
            color = AiDesign.glassColor(),
            tonalElevation = if (performanceMode) 0.dp else 6.dp,
            shadowElevation = if (performanceMode) 0.dp else 10.dp,
            border = BorderStroke(
                width = if (isLoading) 2.dp else 1.dp,
                brush = when {
                    isLoading && !performanceMode -> Brush.sweepGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.primary,
                        )
                    )
                    isIdle && !performanceMode -> Brush.sweepGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                        )
                    )
                    else -> SolidColor(AiDesign.glassBorder())
                }
            ),
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showAttach) {
                    IconButton(
                        onClick = { onAttach?.invoke() },
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(Icons.Rounded.Add, "Attach photo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = AiDesign.textColor(),
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    ),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (inputText.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = AiDesign.textColor(0.38f),
                            )
                        }
                        inner()
                    },
                )

                if (aiSearchIconVisible && onToggleAiSearch != null) {
                    // Revamped web-search toggle: labeled pill, ON by default, clear affordance.
                    // ON = primary pill with globe + "Search"; OFF = ghost with PublicOff.
                    val searchBg by animateColorAsState(
                        targetValue = if (aiSearchEnabled) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        label = "searchBg",
                    )
                    val searchFg by animateColorAsState(
                        targetValue = if (aiSearchEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else AiDesign.textColor(0.45f),
                        label = "searchFg",
                    )
                    val searchScale by animateFloatAsState(
                        targetValue = if (aiSearchEnabled) 1f else 0.94f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label = "searchScale",
                    )
                    Surface(
                        onClick = onToggleAiSearch,
                        shape = MediumExpressiveShape,
                        color = searchBg,
                        border = BorderStroke(
                            1.dp,
                            if (aiSearchEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else AiDesign.textColor(0.14f)
                        ),
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .graphicsLayer { scaleX = searchScale; scaleY = searchScale },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = if (aiSearchEnabled) Icons.Rounded.Language else Icons.Rounded.PublicOff,
                                contentDescription = if (aiSearchEnabled) "Web search ON — tap to disable" else "Web search OFF — tap to enable",
                                tint = searchFg,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                if (aiSearchEnabled) "Search" else "Off",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = searchFg,
                            )
                            if (aiSearchEnabled) {
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Box(
                                        Modifier.size(6.dp).background(
                                            MaterialTheme.colorScheme.primary, CircleShape
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Box(Modifier.padding(end = 4.dp)) {
                    AnimatedContent(
                        targetState = isLoading,
                        transitionSpec = {
                            (scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn()) togetherWith (scaleOut(tween(180)) + fadeOut())
                        },
                        label = "sendStop",
                    ) { loading ->
                        if (loading) {
                            Surface(
                                onClick = onCancel,
                                modifier = Modifier.size(42.dp),
                                shape = MediumExpressiveShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            val sendColor by animateColorAsState(
                                targetValue = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                label = "sendColor",
                            )
                            Surface(
                                onClick = onSend,
                                modifier = Modifier.size(42.dp).graphicsLayer { scaleX = sendScale; scaleY = sendScale },
                                shape = MediumExpressiveShape,
                                color = sendColor,
                                enabled = canSend,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.ArrowUpward, null,
                                        Modifier.size(22.dp),
                                        tint = if (canSend) Color.White else AiDesign.textColor(0.18f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SourcesPill(sources: List<SearchResult>, isUser: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MediumExpressiveShape,
        modifier = Modifier.defaultMinSize(minHeight = 40.dp),
        color   = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border  = BorderStroke(
            0.5.dp,
            if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                sources.take(4).forEach { SourceFavicon(url = it.url, size = 16.dp) }
            }
            Text(
                "${sources.size} sources",
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(13.dp), tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun SourceFavicon(url: String, size: Dp) {
    val domain = runCatching { java.net.URI(url).host?.removePrefix("www.") ?: "" }.getOrElse { "" }
    Surface(modifier = Modifier.size(size), shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))) {
        AsyncImage(model = "https://www.google.com/s2/favicons?sz=64&domain=$domain", contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
fun AiAvatar(config: AiConfig?, size: Dp, modifier: Modifier = Modifier, performanceMode: Boolean = false) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = config?.let { AiDesign.providerColor(it.provider) } ?: MaterialTheme.colorScheme.primary,
        shadowElevation = if (performanceMode) 0.dp else 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = getIconForConfig(config?.iconRes ?: "BOT", config?.provider ?: "Gemini"),
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                tint = Color.White
            )
        }
    }
}

fun getIconForConfig(selected: String, provider: String): ImageVector = when (selected) {
    "GEMINI"   -> Icons.Rounded.AutoAwesome
    "CHATGPT"  -> Icons.Rounded.Chat
    "GROQ"     -> Icons.Rounded.Bolt
    "CLAUDE"   -> Icons.Rounded.HistoryEdu
    "DEEPSEEK" -> Icons.Rounded.Troubleshoot
    "OPENCODE_ZEN", "ZEN" -> Icons.Rounded.AllInclusive
    "OPENCODE_GO", "GO" -> Icons.Rounded.RocketLaunch
    "BOT"      -> Icons.Rounded.SmartToy
    "SPARKLE"  -> Icons.Rounded.AutoFixHigh
    else       -> when (provider) {
        "Gemini"   -> Icons.Rounded.AutoAwesome
        "Groq"     -> Icons.Rounded.Bolt
        "Claude"   -> Icons.Rounded.HistoryEdu
        "DeepSeek" -> Icons.Rounded.Troubleshoot
        "OpenRouter" -> Icons.Rounded.Hub
        "OpenCode Zen" -> Icons.Rounded.AllInclusive
        "OpenCode Go" -> Icons.Rounded.RocketLaunch
        else       -> Icons.Rounded.Chat
    }
}
