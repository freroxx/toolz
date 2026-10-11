/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.GROUP_REACTION_EMOJI
import com.frerox.toolz.data.whisper.GroupPoll
import com.frerox.toolz.data.whisper.GroupReplyRef
import com.frerox.toolz.data.whisper.WhisperGroupChatMessage
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveTypingDots
import com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconToggleButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback

// ─────────────────────────────────────────────────────────────────────────────
// Feed model
// ─────────────────────────────────────────────────────────────────────────────

/** Flat feed rows (dividers included) so jumps scroll to exact indices. */
internal sealed interface FeedItem {
    data class Day(val label: String) : FeedItem
    data class Msg(val msg: WhisperGroupChatMessage, val first: Boolean, val last: Boolean) : FeedItem
    data class Sys(val seq: Long, val text: String) : FeedItem
    data class UnreadMark(val count: Int) : FeedItem
    data class Pending(val p: WhisperGroupChatViewModel.PendingMessage) : FeedItem
}

internal fun FeedItem.feedKey(): String = when (this) {
    is FeedItem.Day -> "day_$label"
    is FeedItem.Msg -> msg.id
    is FeedItem.Sys -> "sys_$seq"
    is FeedItem.UnreadMark -> "unread_mark"
    is FeedItem.Pending -> "pending_${p.localId}"
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GroupChatTopBar(
    groupId: String,
    title: String,
    subtitle: String?,
    typing: String?,
    searching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surfaceContainerLow)
    AnimatedContent(
        targetState = searching,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "chatTopBar",
    ) { isSearch ->
        if (isSearch) {
            TopAppBar(
                title = {
                    GroupSearchField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = stringResource(R.string.st_Whisper_Groups_SearchMessagesHint),
                        autoFocus = true,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSearchToggle) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = GroupCd.back())
                    }
                },
                colors = colors,
            )
        } else {
            var menu by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = GroupCd.info(), onClick = onOpenInfo)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupAvatar(name = title, groupId = groupId, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AnimatedContent(
                                targetState = typing ?: subtitle.orEmpty(),
                                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                                label = "chatSubtitle",
                            ) { text ->
                                if (text.isNotBlank()) {
                                    Text(
                                        text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (typing != null) scheme.primary else scheme.onSurfaceVariant,
                                        fontWeight = if (typing != null) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = GroupCd.back())
                    }
                },
                actions = {
                    IconButton(onClick = onSearchToggle) {
                        Icon(Icons.Rounded.Search, contentDescription = GroupCd.search())
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = GroupCd.more())
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (muted) R.string.st_Whisper_Groups_Unmute
                                            else R.string.st_Whisper_Groups_Mute,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (muted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                        contentDescription = null,
                                    )
                                },
                                onClick = { menu = false; onToggleMute() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.st_Whisper_Groups_Info)) },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                onClick = { menu = false; onOpenInfo() },
                            )
                            DropdownMenuItem(
                                text = { Text(GroupCd.refresh()) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menu = false; onRefresh() },
                            )
                        }
                    }
                },
                colors = colors,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Timeline chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DayChip(label: String, modifier: Modifier = Modifier) {
    if (label.isBlank()) return
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
internal fun SysChip(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun UnreadDivider(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = scheme.primary.copy(alpha = 0.5f))
        Surface(shape = CircleShape, color = scheme.primaryContainer) {
            Text(
                stringResource(R.string.st_Whisper_Groups_NewMessagesDivider, count),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = scheme.primary.copy(alpha = 0.5f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Message row (swipe-to-reply → avatar → bubble → reactions)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GroupMessageItem(
    item: FeedItem.Msg,
    imageBytes: ByteArray?,
    imageExpired: Boolean,
    seenCount: Int,
    pollTally: WhisperGroupChatViewModel.PollTally?,
    pollClosed: Boolean,
    pollCanClose: Boolean,
    reactions: Map<String, List<String>>,
    myId: String,
    memberNames: Map<String, String>,
    highlighted: Boolean,
    onRequestImage: () -> Unit,
    onImageClick: (ByteArray) -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onVote: (Int) -> Unit,
    onClosePoll: () -> Unit,
    onReact: (String) -> Unit,
) {
    val msg = item.msg
    if (msg.image != null) {
        LaunchedEffect(msg.id) { onRequestImage() }
    }
    SwipeToReply(onReply = onReply) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (item.first) 6.dp else 0.dp),
            horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!msg.mine) {
                Box(modifier = Modifier.width(34.dp)) {
                    if (item.last) {
                        GroupAvatar(name = msg.senderName, groupId = msg.senderId, size = 30.dp)
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Column(
                modifier = Modifier.widthIn(max = 300.dp),
                horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                MessageBubble(
                    msg = msg,
                    first = item.first,
                    last = item.last,
                    imageBytes = imageBytes,
                    imageExpired = imageExpired,
                    seenCount = seenCount,
                    pollTally = pollTally,
                    pollClosed = pollClosed,
                    pollCanClose = pollCanClose,
                    myId = myId,
                    memberNames = memberNames,
                    highlighted = highlighted,
                    onImageClick = onImageClick,
                    onLongPress = onLongPress,
                    onQuoteClick = onQuoteClick,
                    onVote = onVote,
                    onClosePoll = onClosePoll,
                )
                if (reactions.values.any { it.isNotEmpty() }) {
                    ReactionRow(reactions = reactions, myId = myId, onReact = onReact)
                }
            }
        }
    }
}

/** Swipe right on any message to reply; the row always springs back. */
@Composable
private fun SwipeToReply(onReply: () -> Unit, content: @Composable () -> Unit) {
    val haptic = rememberToolzHapticFeedback()
    val current by rememberUpdatedState(onReply)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.StartToEnd) {
                haptic.tick()
                current()
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.22f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        enableDismissFromEndToStart = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(start = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Reply,
                            contentDescription = stringResource(R.string.st_Whisper_Groups_Reply),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        content = { content() },
    )
}

@Composable
private fun MessageBubble(
    msg: WhisperGroupChatMessage,
    first: Boolean,
    last: Boolean,
    imageBytes: ByteArray?,
    imageExpired: Boolean,
    seenCount: Int,
    pollTally: WhisperGroupChatViewModel.PollTally?,
    pollClosed: Boolean,
    pollCanClose: Boolean,
    myId: String,
    memberNames: Map<String, String>,
    highlighted: Boolean,
    onImageClick: (ByteArray) -> Unit,
    onLongPress: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onVote: (Int) -> Unit,
    onClosePoll: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val mine = msg.mine
    val mentionedMe = !mine && myId.isNotBlank() && myId in msg.mentions
    val bg by animateColorAsState(
        when {
            highlighted -> scheme.secondaryContainer
            mentionedMe -> scheme.tertiaryContainer
            mine -> scheme.primaryContainer
            else -> scheme.surfaceContainerHigh
        },
        tween(280), label = "bubbleBg",
    )
    val fg by animateColorAsState(
        when {
            highlighted -> scheme.onSecondaryContainer
            mentionedMe -> scheme.onTertiaryContainer
            mine -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        },
        tween(280), label = "bubbleFg",
    )
    val big = 20.dp
    val small = 6.dp
    val tail = 4.dp
    val shape = if (mine) {
        RoundedCornerShape(
            topStart = big, topEnd = if (first) big else small,
            bottomEnd = if (last) tail else small, bottomStart = big,
        )
    } else {
        RoundedCornerShape(
            topStart = if (first) big else small, topEnd = big,
            bottomEnd = big, bottomStart = if (last) tail else small,
        )
    }
    val hasImage = msg.image != null
    val poll = msg.poll
    val hasPoll = poll != null
    val wide = hasImage || hasPoll || msg.reply != null
    val inset = if (hasImage) Modifier.padding(horizontal = 8.dp, vertical = 2.dp) else Modifier
    val timeText = remember(msg.createdAt) { formatGroupClock(msg.createdAt) }
    val showName = !mine && first

    Column(
        modifier = (if (wide) Modifier.width(IntrinsicSize.Max) else Modifier)
            .clip(shape)
            .background(bg)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(if (hasImage) PaddingValues(4.dp) else PaddingValues(horizontal = 12.dp, vertical = 8.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showName) {
            Text(
                msg.senderName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = senderTint(msg.senderId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = inset,
            )
        }
        if (msg.reply != null) {
            QuoteBlock(quote = msg.reply, fg = fg, onClick = { onQuoteClick(msg.reply.id) })
        }
        if (poll != null) {
            PollCard(
                poll = poll,
                tally = pollTally,
                closed = pollClosed,
                canClose = pollCanClose,
                fg = fg,
                onVote = onVote,
                onClose = onClosePoll,
            )
            GroupMeta(
                time = timeText, mine = mine, seenCount = seenCount, color = fg,
                tick = scheme.primary, modifier = Modifier.align(Alignment.End),
            )
        } else {
            if (hasImage) {
                BubbleImage(
                    bytes = imageBytes,
                    expired = imageExpired,
                    onClick = onImageClick,
                    overlay = if (msg.body.isBlank() && !imageExpired) {
                        {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                            ) {
                                GroupMeta(
                                    time = timeText, mine = mine, seenCount = seenCount,
                                    color = Color.White, tick = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    } else null,
                )
            }
            if (msg.body.isNotBlank()) {
                BodyWithMeta(
                    body = msg.body,
                    memberNames = memberNames,
                    fg = fg,
                    time = timeText,
                    mine = mine,
                    seenCount = seenCount,
                    fillWidth = wide,
                    modifier = inset,
                )
            } else if (!hasImage || imageExpired) {
                GroupMeta(
                    time = timeText, mine = mine, seenCount = seenCount, color = fg,
                    tick = scheme.primary, modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/** Time + (for my messages) seen ticks. */
@Composable
private fun GroupMeta(
    time: String,
    mine: Boolean,
    seenCount: Int,
    color: Color,
    tick: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (mine && seenCount > 0) {
            Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = tick, modifier = Modifier.size(14.dp))
            if (seenCount > 1) {
                Text("$seenCount", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
            }
        }
        Text(time, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}

/**
 * Body text whose last line leaves room for the timestamp: an inline
 * placeholder reserves the meta width, the meta overlays it bottom-end.
 */
@Composable
private fun BodyWithMeta(
    body: String,
    memberNames: Map<String, String>,
    fg: Color,
    time: String,
    mine: Boolean,
    seenCount: Int,
    fillWidth: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val metaStyle = MaterialTheme.typography.labelSmall
    val reserve = remember(time, mine, seenCount, metaStyle, density) {
        val timePx = measurer.measure(time, metaStyle).size.width
        val extraDp = if (mine && seenCount > 0) 17 + (if (seenCount > 1) 10 else 0) else 0
        with(density) { (timePx + extraDp.dp.toPx() + 12.dp.toPx()).toSp() }
    }
    val annotated = remember(body, memberNames, reserve, scheme.primary) {
        buildAnnotatedString {
            append(mentionAnnotated(body, memberNames, scheme.primary))
            appendInlineContent("meta", " ")
        }
    }
    val inline = remember(reserve) {
        mapOf(
            "meta" to InlineTextContent(
                Placeholder(width = reserve, height = 1.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom),
            ) { },
        )
    }
    Box(modifier = if (fillWidth) modifier.fillMaxWidth() else modifier) {
        Text(
            text = annotated,
            inlineContent = inline,
            style = MaterialTheme.typography.bodyLarge,
            color = fg,
        )
        GroupMeta(
            time = time, mine = mine, seenCount = seenCount, color = fg,
            tick = scheme.primary, modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Body with @Name tokens bolded + tinted (only tokens matching members). */
private fun mentionAnnotated(body: String, memberNames: Map<String, String>, accent: Color): AnnotatedString {
    val builder = AnnotatedString.Builder(body)
    if ("@" in body) {
        val taken = mutableListOf<IntRange>()
        memberNames.values.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { name ->
            var from = 0
            while (true) {
                val idx = body.indexOf("@$name", from)
                if (idx < 0) break
                val range = idx until idx + name.length + 1
                from = range.last + 1
                if (taken.any { it.first <= range.last && range.first <= it.last }) continue
                taken.add(range)
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent), range.first, range.last + 1)
            }
        }
    }
    return builder.toAnnotatedString()
}

@Composable
private fun BubbleImage(
    bytes: ByteArray?,
    expired: Boolean,
    onClick: (ByteArray) -> Unit,
    overlay: (@Composable BoxScope.() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    if (expired) {
        Row(
            modifier = Modifier
                .width(248.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.HourglassEmpty, contentDescription = null,
                tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.st_Whisper_ImageExpired),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        return
    }
    val bitmap = rememberGroupBitmap(bytes, 1280)
    val ratio = bitmap?.let { it.width.toFloat() / it.height.toFloat() }?.coerceIn(0.7f, 1.5f) ?: 1.25f
    Box(
        modifier = Modifier
            .width(248.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHighest)
            .clickable(enabled = bytes != null) { bytes?.let(onClick) },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.Crossfade(targetState = bitmap, label = "bubbleImage") { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = stringResource(R.string.st_Whisper_Groups_Photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GroupUiInlineLoading() }
            }
        }
        overlay?.invoke(this)
    }
}

@Composable
private fun QuoteBlock(quote: GroupReplyRef, fg: Color, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(fg.copy(alpha = 0.09f))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                quote.sender,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                quote.text,
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReactionRow(
    reactions: Map<String, List<String>>,
    myId: String,
    onReact: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = rememberToolzHapticFeedback()
    Row(
        modifier = Modifier.animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.entries.filter { it.value.isNotEmpty() }.sortedBy { it.key }.take(6).forEach { (emoji, users) ->
            val mine = myId in users
            Surface(
                onClick = { haptic.tick(); onReact(emoji) },
                shape = RoundedCornerShape(12.dp),
                color = if (mine) scheme.primaryContainer else scheme.surfaceContainerHighest,
                border = if (mine) BorderStroke(1.dp, scheme.primary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(emoji, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${users.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium,
                        color = if (mine) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Poll card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PollCard(
    poll: GroupPoll,
    tally: WhisperGroupChatViewModel.PollTally?,
    closed: Boolean,
    canClose: Boolean,
    fg: Color,
    onVote: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = rememberToolzHapticFeedback()
    val total = tally?.counts?.values?.sum() ?: 0
    Column(
        modifier = Modifier.widthIn(min = 236.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Rounded.BarChart, contentDescription = null,
                tint = scheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
            Text(
                poll.q,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = fg,
                modifier = Modifier.weight(1f),
            )
            if (closed) {
                Surface(shape = CircleShape, color = fg.copy(alpha = 0.12f)) {
                    Text(
                        stringResource(R.string.st_Whisper_Groups_PollClosed),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        poll.opts.forEachIndexed { idx, opt ->
            val votes = tally?.counts?.get(idx) ?: 0
            val selected = tally?.myVote == idx
            val frac by animateFloatAsState(
                targetValue = if (total == 0) 0f else votes.toFloat() / total,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "pollFrac",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(fg.copy(alpha = 0.08f))
                    .clickable(enabled = !closed, role = Role.RadioButton) {
                        haptic.tick()
                        onVote(idx)
                    },
            ) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(scheme.primary.copy(alpha = if (selected) 0.32f else 0.18f)),
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) scheme.primary else fg.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        opt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fg,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (total > 0) {
                        Text(
                            "$votes",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (total > 0) {
                Icon(
                    Icons.Rounded.HowToVote, contentDescription = null,
                    tint = fg.copy(alpha = 0.6f), modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("$total", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))
            if (canClose && !closed) {
                ToolzExpressiveTextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.st_Whisper_Groups_ClosePoll), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Optimistic outgoing row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PendingBubble(
    pending: WhisperGroupChatViewModel.PendingMessage,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .graphicsLayer { alpha = if (pending.failed) 1f else 0.72f }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp))
                .background(if (pending.failed) scheme.errorContainer else scheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                pending.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (pending.failed) scheme.onErrorContainer else scheme.onPrimaryContainer,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                if (pending.failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Schedule,
                contentDescription = null,
                tint = if (pending.failed) scheme.error else scheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 2.dp).size(14.dp),
            )
        }
        AnimatedVisibility(visible = pending.failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolzExpressiveTextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
                ToolzExpressiveTextButton(onClick = onRetry) {
                    Text(stringResource(R.string.st_Whisper_Groups_Retry), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search results
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GroupSearchResults(
    query: String,
    loading: Boolean,
    results: List<WhisperGroupChatMessage>?,
    onPick: (WhisperGroupChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = results.orEmpty()
    val listState = rememberLazyListState()
    Column(modifier = modifier.fillMaxSize()) {
        if (loading && results == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                GroupUiInlineLoading()
            }
        } else if (!loading && results != null && list.isEmpty()) {
            GroupEmptyPanel(icon = Icons.Rounded.Search, message = null)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().groupScrollFade(listState),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(list, key = { it.id }) { msg ->
                Surface(
                    onClick = { onPick(msg) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupAvatar(name = msg.senderName, groupId = msg.senderId, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    msg.senderName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = senderTint(msg.senderId),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatGroupListTime(msg.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HighlightedSnippet(body = msg.body, query = query)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedSnippet(body: String, query: String) {
    val q = query.trim()
    val accent = MaterialTheme.colorScheme.primary
    val annotated = remember(body, q, accent) {
        val builder = AnnotatedString.Builder(body)
        if (q.length >= 2) {
            val lowerBody = body.lowercase()
            val lowerQ = q.lowercase()
            var from = 0
            while (true) {
                val idx = lowerBody.indexOf(lowerQ, from)
                if (idx < 0) break
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent), idx, idx + q.length)
                from = idx + q.length
            }
        }
        builder.toAnnotatedString()
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Composer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resolves @Name tokens in a draft to member ids (longest-name-first, with
 * span consumption: "@Ann Lee" must not also match "@Ann").
 */
internal fun resolveMentionIds(draft: String, memberNames: Map<String, String>): List<String> {
    if ("@" !in draft) return emptyList()
    val consumed = mutableListOf<IntRange>()
    return memberNames.entries
        .sortedByDescending { it.value.length }
        .filter { (_, name) ->
            if (name.isBlank()) return@filter false
            val idx = draft.indexOf("@$name")
            if (idx < 0) return@filter false
            val range = idx..<idx + name.length + 1
            if (consumed.any { it.first <= range.last && range.first <= it.last }) return@filter false
            consumed.add(range)
            true
        }
        .map { it.key }
        .distinct()
}

/** Trailing @word being typed (null when the caret isn't in a mention). */
private fun mentionQuery(draft: String): String? {
    val tail = draft.substringAfterLast("\n")
    val at = tail.lastIndexOf("@")
    if (at < 0) return null
    val word = tail.substring(at + 1)
    if (word.any { it.isWhitespace() } || word.length > 32) return null
    return word
}

@Composable
internal fun typingLine(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> stringResource(R.string.st_Whisper_Groups_TypingOne, names[0])
    2 -> stringResource(R.string.st_Whisper_Groups_TypingTwo, names[0], names[1])
    else -> stringResource(R.string.st_Whisper_Groups_TypingMany)
}

@Composable
internal fun GroupComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    locked: Boolean,
    replyTarget: WhisperGroupChatMessage?,
    onCancelReply: () -> Unit,
    typingNames: List<String>,
    uploading: Boolean,
    memberNames: Map<String, String>,
    myId: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val maxChars = WhisperRepository.MAX_MESSAGE_CHARS
    val overLimit = draft.length > maxChars
    val canSend = draft.isNotBlank() && !overLimit && !locked
    val shownTyping = rememberLastNonEmpty(typingNames)
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "sendScale",
    )

    Surface(color = scheme.surfaceContainerLow, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            GroupAnimatedReveal(visible = uploading) {
                ExpressiveLinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            GroupAnimatedReveal(visible = typingNames.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpressiveTypingDots(color = scheme.primary)
                    Text(
                        typingLine(shownTyping),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            GroupAnimatedReveal(visible = replyTarget != null && !locked) {
                if (replyTarget != null) {
                    ReplyPreviewBar(
                        sender = replyTarget.senderName,
                        text = replyTarget.body.ifBlank { stringResource(R.string.st_Whisper_Groups_Photo) },
                        onCancel = onCancelReply,
                    )
                }
            }
            MentionSuggestions(
                draft = draft,
                memberNames = memberNames,
                myId = myId,
                onPick = { name ->
                    val at = draft.lastIndexOf("@")
                    onDraftChange(if (at >= 0) draft.substring(0, at) + "@$name " else "$draft@$name ")
                },
            )
            if (locked) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.Lock, contentDescription = null,
                        tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.st_Whisper_Groups_AdminOnlySend),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToolzTonalExpressiveIconButton(onClick = onAttach, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = GroupCd.attach())
                    }
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            BasicTextField(
                                value = draft,
                                onValueChange = onDraftChange,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box(
                                        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (draft.isEmpty()) {
                                            Text(
                                                stringResource(R.string.st_Whisper_Groups_MessageHint),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            if (draft.length > maxChars - 200) {
                                Text(
                                    "${maxChars - draft.length}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (overLimit) scheme.error else scheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }
                    ToolzExpressiveIconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier.size(48.dp).graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = GroupCd.send())
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionSuggestions(
    draft: String,
    memberNames: Map<String, String>,
    myId: String,
    onPick: (String) -> Unit,
) {
    val q = remember(draft) { mentionQuery(draft) }
    val hits = remember(memberNames, q) {
        if (q == null) emptyList()
        else memberNames.entries.filter { (id, name) ->
            id != myId && name.isNotBlank() && (q.isBlank() || name.lowercase().startsWith(q.lowercase()))
        }.take(4).map { it.key to it.value }
    }
    GroupAnimatedReveal(visible = hits.isNotEmpty()) {
        val shown = rememberLastNonEmpty(hits)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    shown.forEach { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(name) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GroupAvatar(name = name, groupId = id, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "@$name",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyPreviewBar(sender: String, text: String, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(scheme.primary))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.st_Whisper_Groups_Reply) + " · $sender",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = GroupCd.clear(), modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating chrome (jump-to-latest, sticky day pill)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun JumpToLatest(
    visible: Boolean,
    newCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier,
    ) {
        androidx.compose.material3.BadgedBox(
            badge = {
                if (newCount > 0) {
                    Badge { Text(if (newCount > 99) "99+" else "$newCount") }
                }
            },
        ) {
            ToolzExpressiveIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = GroupCd.latest())
            }
        }
    }
}

@Composable
internal fun StickyDayPill(visible: Boolean, label: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible && label.isNotBlank(),
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheets
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MessageActionsSheet(
    msg: WhisperGroupChatMessage,
    reactions: Map<String, List<String>>,
    myId: String,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
) {
    GroupSheet(onDismiss = onDismiss) { close ->
        val preview = msg.poll?.q ?: msg.body.ifBlank { stringResource(R.string.st_Whisper_Groups_Photo) }
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GROUP_REACTION_EMOJI.forEach { emoji ->
                val mine = reactions[emoji]?.contains(myId) == true
                ToolzExpressiveIconToggleButton(
                    checked = mine,
                    onCheckedChange = { onReact(emoji); close() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        GroupActionRow(
            icon = Icons.AutoMirrored.Rounded.Reply,
            label = stringResource(R.string.st_Whisper_Groups_Reply),
            onClick = { onReply(); close() },
        )
        if (msg.body.isNotBlank()) {
            GroupActionRow(
                icon = Icons.Rounded.ContentCopy,
                label = stringResource(R.string.st_Whisper_Groups_Copy),
                onClick = { onCopy(); close() },
            )
        }
        if (msg.mine) {
            GroupActionRow(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.st_Whisper_Groups_MessageInfo),
                onClick = { onInfo(); close() },
            )
        }
        GroupActionRow(
            icon = Icons.Rounded.Delete,
            label = stringResource(R.string.st_Whisper_Groups_DeleteForMe),
            onClick = { onDeleteForMe(); close() },
        )
        if (msg.mine) {
            GroupActionRow(
                icon = Icons.Rounded.DeleteForever,
                label = stringResource(R.string.st_Whisper_Groups_DeleteForAll),
                destructive = true,
                onClick = { onDeleteForEveryone(); close() },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun MessageInfoSheet(seen: List<Pair<String, String>>, onDismiss: () -> Unit) {
    GroupSheet(onDismiss = onDismiss) { _ ->
        GroupSheetTitle(
            text = stringResource(R.string.st_Whisper_Groups_MessageInfo),
            subtitle = if (seen.isEmpty()) stringResource(R.string.st_Whisper_Groups_SeenByNone)
            else stringResource(R.string.st_Whisper_Groups_SeenBy, seen.size),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(seen, key = { it.first }) { (id, name) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupAvatar(name = name, groupId = id, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun AttachSheet(onPhoto: () -> Unit, onPoll: () -> Unit, onDismiss: () -> Unit) {
    GroupSheet(onDismiss = onDismiss) { close ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AttachTile(
                icon = Icons.Rounded.AddPhotoAlternate,
                label = stringResource(R.string.st_Whisper_Groups_Photo),
                modifier = Modifier.weight(1f),
                onClick = { close(); onPhoto() },
            )
            AttachTile(
                icon = Icons.Rounded.BarChart,
                label = stringResource(R.string.st_Whisper_Groups_CreatePoll),
                modifier = Modifier.weight(1f),
                onClick = { close(); onPoll() },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AttachTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun CreatePollSheet(onDismiss: () -> Unit, onSend: (String, List<String>) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    val canSend = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
    GroupSheet(onDismiss = onDismiss) { close ->
        GroupSheetTitle(stringResource(R.string.st_Whisper_Groups_CreatePoll))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = question,
                onValueChange = { if (it.length <= 140) question = it },
                label = { Text(stringResource(R.string.st_Whisper_Groups_PollQuestionHint)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            options.forEachIndexed { idx, opt ->
                OutlinedTextField(
                    value = opt,
                    onValueChange = { if (it.length <= 60) options[idx] = it },
                    label = { Text(stringResource(R.string.st_Whisper_Groups_PollOptionHint, idx + 1)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    trailingIcon = if (options.size > 2) {
                        {
                            IconButton(onClick = { options.removeAt(idx) }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_Whisper_Groups_Remove))
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (options.size < 6) {
                ToolzExpressiveTextButton(onClick = { options.add("") }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.st_Whisper_Groups_PollAddOption))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolzExpressiveTextButton(onClick = close) { Text(stringResource(R.string.st_Whisper_Cancel)) }
                ToolzExpressiveButton(
                    onClick = {
                        onSend(question.trim(), options.map { it.trim() }.filter { it.isNotBlank() })
                        close()
                    },
                    enabled = canSend,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.st_Whisper_Groups_PollSend), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Preview + disappearing-photo choice before a group photo is sent. */
@Composable
internal fun ImageSendSheet(bytes: ByteArray, onDismiss: () -> Unit, onSend: (Long?) -> Unit) {
    val bitmap = rememberGroupBitmap(bytes, 900)
    var expiry by remember { mutableStateOf<Long?>(null) }
    val options = listOf(
        null to stringResource(R.string.st_Whisper_Expiry_Never),
        60L to stringResource(R.string.st_Whisper_Expiry_1m),
        3600L to stringResource(R.string.st_Whisper_Expiry_1h),
        86400L to stringResource(R.string.st_Whisper_Expiry_1d),
    )
    GroupSheet(onDismiss = onDismiss) { close ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.st_Whisper_Groups_Photo),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(Modifier.height(120.dp), contentAlignment = Alignment.Center) { GroupUiInlineLoading() }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.st_Whisper_ImageExpiry_Title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.st_Whisper_ImageExpiryDesc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = expiry == value, role = Role.RadioButton, onClick = { expiry = value })
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = expiry == value, onClick = null)
                Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolzExpressiveTextButton(onClick = close) { Text(stringResource(R.string.st_Whisper_Cancel)) }
            ToolzExpressiveButton(
                onClick = { onSend(expiry); close() },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(GroupCd.send(), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
