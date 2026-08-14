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

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Individual conversation screen with Material 3 Expressive UI,
 * swipe-to-reply, emoji reactions, in-chat search, live presence,
 * and 30s live countdown undo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WhisperChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    viewModel: WhisperChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val toastState = rememberWhisperToastState()
    val scope = rememberCoroutineScope()

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showClearChatSheet by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var selectedMessageForDelete by remember { mutableStateOf<WhisperMessage?>(null) }
    var quickReactionTargetMessage by remember { mutableStateOf<WhisperMessage?>(null) }

    // Auto-logout when session expires
    LaunchedEffect(Unit) {
        viewModel.sessionExpired.collect {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            toastState.show(it, WhisperToastType.ERROR)
            viewModel.clearError()
        }
    }

    val listState = rememberLazyListState()
    val messages = uiState.messages

    // Auto scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !uiState.isSearchActive) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Auto scroll to active search match
    val matchingIndices = remember(uiState.matchingMessageIds, messages) {
        messages.mapIndexedNotNull { index, msg ->
            if (uiState.matchingMessageIds.contains(msg.id)) index else null
        }
    }
    LaunchedEffect(uiState.activeSearchMatchIndex, matchingIndices) {
        if (uiState.isSearchActive && uiState.activeSearchMatchIndex in matchingIndices.indices) {
            val targetIdx = matchingIndices[uiState.activeSearchMatchIndex]
            listState.animateScrollToItem(targetIdx)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    title = {
                        if (uiState.isSearchActive) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Search messages…", style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, "Clear search", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = uiState.otherUser != null) {
                                        haptic.click()
                                        uiState.otherUser?.id?.let { onNavigateToProfile(it) }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            ) {
                                uiState.otherUser?.let { user ->
                                    Box {
                                        WhisperAvatar(user, 38.dp)
                                        if (uiState.isPartnerOnline) {
                                            Box(
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF4CAF50))
                                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            )
                                        }
                                    }
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(user.effectiveName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            if (uiState.isMuted) {
                                                Icon(
                                                    Icons.Rounded.NotificationsOff,
                                                    contentDescription = "Muted",
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        val subtitle = when {
                                            uiState.isPartnerTyping -> "typing…"
                                            uiState.isPartnerOnline -> "Online"
                                            else -> "@${user.effectiveUsername}"
                                        }
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (uiState.isPartnerTyping || uiState.isPartnerOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } ?: Text("Chat", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    navigationIcon = {
                        ToolzExpressiveIconButton(onClick = {
                            haptic.click()
                            if (uiState.isSearchActive) {
                                viewModel.toggleSearch(false)
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_Back))
                        }
                    },
                    actions = {
                        if (uiState.isSearchActive) {
                            if (uiState.matchingMessageIds.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        "${uiState.activeSearchMatchIndex + 1}/${uiState.matchingMessageIds.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                ToolzExpressiveIconButton(onClick = {
                                    haptic.click()
                                    viewModel.navigateSearchMatch(-1)
                                }) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, "Previous match")
                                }
                                ToolzExpressiveIconButton(onClick = {
                                    haptic.click()
                                    viewModel.navigateSearchMatch(1)
                                }) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, "Next match")
                                }
                            }
                        } else {
                            val status = uiState.friendStatus
                            if (status == FriendStatus.ACCEPTED) {
                                Icon(
                                    Icons.Rounded.VerifiedUser,
                                    contentDescription = stringResource(R.string.st_Whisper_Friends_Accept),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Search button
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                viewModel.toggleSearch(true)
                            }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search messages")
                            }

                            // 3-dot feature menu button (opens expressive bottom sheet)
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                showOptionsSheet = true
                            }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Chat options")
                            }
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.toolzBackground(),
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
            ) {
                // Friend gate banner: ONLY show when friend status is loaded and not accepted and neither is blocked
                AnimatedVisibility(
                    visible = uiState.isFriendStatusLoaded && uiState.friendStatus != FriendStatus.ACCEPTED && !uiState.isBlockedByMe && !uiState.isBlockedByOther,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    FriendGateBanner(
                        friendStatus = uiState.friendStatus,
                        iAmRequester = uiState.iAmRequester,
                        onSendRequest = { haptic.success(); viewModel.sendFriendRequest() },
                    )
                }

                // Blocked by other banner: The user needs to know they are blocked!
                if (uiState.isBlockedByOther) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                            Text(
                                "You have been blocked by this user. You cannot send messages.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else if (uiState.isBlockedByMe) {
                    // Blocked state banner (Visible to blocker)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(
                                "You have blocked this user. Unblock to resume conversation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            ToolzTonalExpressiveButton(onClick = { viewModel.toggleBlock() }) {
                                Text("Unblock")
                            }
                        }
                    }
                }

                // Messages list
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (uiState.isLoading && messages.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ToolzLoadingIndicator()
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdges(top = 16.dp, bottom = 32.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                                val isMine = message.isSentByMe(viewModel.myUserId)
                                val isPending = message.id.startsWith("pending_")

                                val showDateSeparator = index == 0 ||
                                    messages[index - 1].createdAt.extractDate() != message.createdAt.extractDate()

                                if (showDateSeparator) {
                                    StaggeredEntrance(
                                        index = index,
                                        enter = fadeIn(tween(320)) + scaleIn(
                                            initialScale = 0.92f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                        ),
                                        exit = fadeOut(tween(150)),
                                    ) {
                                        DateSeparator(message.createdAt.extractDate())
                                    }
                                }

                                val isHighlighted = uiState.matchingMessageIds.contains(message.id)

                                StaggeredEntrance(
                                    index = index,
                                    modifier = Modifier.animateItem(
                                        placementSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                                    ),
                                ) {
                                    MessageBubble(
                                        message = message,
                                        isMine = isMine,
                                        isPending = isPending,
                                        isHighlighted = isHighlighted,
                                        partnerName = uiState.otherUser?.effectiveName ?: "User",
                                        onReply = {
                                            haptic.click()
                                            viewModel.setReplyTarget(message)
                                        },
                                        onQuotedClick = { targetId ->
                                            val targetIndex = messages.indexOfFirst { it.id == targetId }
                                            if (targetIndex >= 0) {
                                                haptic.click()
                                                scope.launch {
                                                    listState.animateScrollToItem(targetIndex)
                                                }
                                            }
                                        },
                                        onDoubleTap = {
                                            haptic.click()
                                            quickReactionTargetMessage = message
                                        },
                                        onReactionClick = { emoji ->
                                            haptic.click()
                                            viewModel.toggleReaction(message, emoji)
                                        },
                                        onLongClick = {
                                            if (!message.isDeletedForEveryone && !isPending) {
                                                haptic.longClick()
                                                selectedMessageForDelete = message
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Scroll to bottom FAB: visible when last message is NOT visible
                    val isScrolledUp = remember {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val lastIndex = messages.lastIndex
                            if (lastIndex < 0) return@derivedStateOf false
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            lastVisibleIndex < lastIndex
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScrolledUp.value,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                haptic.click()
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.lastIndex)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                        }
                    }
                }

                // 30s Live Countdown Undo Banner
                AnimatedVisibility(
                    visible = uiState.clearedUndoMessagesCount > 0,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            Text(
                                "Cleared ${uiState.clearedUndoMessagesCount} messages",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            ToolzTonalExpressiveButton(
                                onClick = {
                                    haptic.success()
                                    viewModel.undoClearChat()
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Undo (${uiState.undoSecondsRemaining}s)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bouncing dots typing indicator banner
                AnimatedVisibility(
                    visible = uiState.isPartnerTyping,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        BouncingDotsIndicator()
                        Text(
                            "${uiState.otherUser?.effectiveName ?: "Partner"} is typing…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Reply preview bar
                uiState.replyingToMessage?.let { replyTarget ->
                    ReplyPreviewBar(
                        replyTarget = replyTarget,
                        partnerName = uiState.otherUser?.effectiveName ?: "User",
                        myUserId = viewModel.myUserId,
                        onDismiss = { viewModel.clearReplyTarget() }
                    )
                }

                // Message input bar
                val canSendMessage = uiState.friendStatus == FriendStatus.ACCEPTED && !uiState.isBlockedByMe && !uiState.isBlockedByOther
                val placeholderText = when {
                    uiState.isBlockedByOther -> "You have been blocked"
                    uiState.isBlockedByMe    -> "Unblock user to send messages"
                    uiState.friendStatus != FriendStatus.ACCEPTED -> stringResource(R.string.st_Whisper_Chat_InputPlaceholderDisabled)
                    else -> stringResource(R.string.st_Whisper_Chat_InputPlaceholder)
                }
                val draftText by viewModel.draftText.collectAsStateWithLifecycle()
                var sendPulse by remember { mutableIntStateOf(0) }
                LaunchedEffect(messages.size) {
                    messages.lastOrNull()?.let {
                        if (it.id.startsWith("pending_")) sendPulse++
                    }
                }
                MessageInputBar(
                    enabled = canSendMessage,
                    draftText = draftText,
                    placeholderText = placeholderText,
                    pulseTrigger = sendPulse,
                    onDraftChanged = { viewModel.updateDraft(it) },
                    onSend = { text ->
                        haptic.success()
                        viewModel.sendMessage(text)
                    },
                )
            }
        }

        // Expressive Toast Host
        WhisperToastHost(
            hostState = toastState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }

    // Quick Reaction Bar Pop-up on double tap
    quickReactionTargetMessage?.let { targetMsg ->
        QuickReactionDialog(
            onDismiss = { quickReactionTargetMessage = null },
            onEmojiSelected = { emoji ->
                quickReactionTargetMessage = null
                haptic.click()
                viewModel.toggleReaction(targetMsg, emoji)
            }
        )
    }

    // Material 3 Expressive Chat Options Bottom Sheet
    if (showOptionsSheet) {
        ChatOptionsSheet(
            isMuted = uiState.isMuted,
            isBlocked = uiState.isBlockedByMe,
            hasClearedUndo = uiState.clearedUndoMessagesCount > 0,
            clearedCount = uiState.clearedUndoMessagesCount,
            onDismiss = { showOptionsSheet = false },
            onSearch = {
                showOptionsSheet = false
                viewModel.toggleSearch(true)
            },
            onClearChat = {
                showOptionsSheet = false
                showClearChatSheet = true
            },
            onUndoClear = {
                showOptionsSheet = false
                haptic.success()
                viewModel.undoClearChat()
                toastState.show("Messages restored", WhisperToastType.SUCCESS)
            },
            onToggleMute = {
                showOptionsSheet = false
                if (uiState.isMuted) {
                    viewModel.toggleMute()
                    toastState.show("Notifications unmuted", WhisperToastType.INFO)
                } else {
                    showMuteDialog = true
                }
            },
            onToggleBlock = {
                showOptionsSheet = false
                if (uiState.isBlockedByMe) {
                    viewModel.toggleBlock()
                    toastState.show("User unblocked", WhisperToastType.INFO)
                } else {
                    showBlockConfirmDialog = true
                }
            },
            onViewProfile = {
                showOptionsSheet = false
                uiState.otherUser?.id?.let { onNavigateToProfile(it) }
            }
        )
    }

    // Material 3 Expressive Delete Message Bottom Sheet
    selectedMessageForDelete?.let { msg ->
        DeleteMessageSheet(
            message = msg,
            isMine = msg.isSentByMe(viewModel.myUserId),
            onDismiss = { selectedMessageForDelete = null },
            onReply = {
                selectedMessageForDelete = null
                viewModel.setReplyTarget(msg)
            },
            onReact = { emoji ->
                selectedMessageForDelete = null
                viewModel.toggleReaction(msg, emoji)
            },
            onDeleteForEveryone = {
                selectedMessageForDelete = null
                haptic.error()
                viewModel.deleteMessageForEveryone(msg)
                toastState.show("Message deleted for everyone", WhisperToastType.INFO)
            },
            onDeleteForMe = {
                selectedMessageForDelete = null
                haptic.click()
                viewModel.deleteMessageForMe(msg)
                toastState.show("Message deleted for you", WhisperToastType.INFO)
            }
        )
    }

    // Material 3 Expressive Clear Chat Bottom Sheet
    if (showClearChatSheet) {
        ClearChatSheet(
            onDismiss = { showClearChatSheet = false },
            onSelectRange = { range ->
                showClearChatSheet = false
                haptic.click()
                viewModel.clearChat(range)
                toastState.show("Chat cleared (30s to undo)", WhisperToastType.INFO)
            }
        )
    }

    // Material 3 Expressive Mute Dialog
    if (showMuteDialog) {
        MuteOptionsDialog(
            onDismiss = { showMuteDialog = false },
            onSelectDuration = { durationMs ->
                showMuteDialog = false
                haptic.click()
                viewModel.toggleMute(durationMs)
                toastState.show("Notifications muted", WhisperToastType.INFO)
            }
        )
    }

    // Material 3 Expressive Block Confirmation Dialog
    if (showBlockConfirmDialog) {
        BlockConfirmDialog(
            partnerName = uiState.otherUser?.effectiveName ?: "this user",
            onDismiss = { showBlockConfirmDialog = false },
            onConfirmBlock = {
                showBlockConfirmDialog = false
                haptic.error()
                viewModel.toggleBlock()
                toastState.show("User blocked", WhisperToastType.INFO)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// MATERIAL 3 BOUNCING DOTS TYPING INDICATOR
// ─────────────────────────────────────────────────────────────

@Composable
private fun BouncingDotsIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    val dotCount = 3
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -5f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0f at 0 using FastOutSlowInEasing
                        -5f at (150 + index * 120) using FastOutSlowInEasing
                        0f at (350 + index * 120)
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot_bounce_$index"
            )
            Box(
                Modifier
                    .size(6.dp)
                    .offset(y = offset.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// REPLY PREVIEW BAR
// ─────────────────────────────────────────────────────────────

@Composable
private fun ReplyPreviewBar(
    replyTarget: WhisperMessage,
    partnerName: String,
    myUserId: String,
    onDismiss: () -> Unit,
) {
    val senderTitle = if (replyTarget.isSentByMe(myUserId)) "You" else partnerName
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Replying to $senderTitle",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    replyTarget.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, "Cancel reply", modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// QUICK REACTION DIALOG / POPUP
// ─────────────────────────────────────────────────────────────

@Composable
private fun QuickReactionDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
) {
    val emojis = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text("React to message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                emojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .bouncyClick { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────
// CHAT OPTIONS BOTTOM SHEET (Replacing DropdownMenu)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatOptionsSheet(
    isMuted: Boolean,
    isBlocked: Boolean,
    hasClearedUndo: Boolean,
    clearedCount: Int,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onClearChat: () -> Unit,
    onUndoClear: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onViewProfile: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Conversation Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // Search
            ListItem(
                headlineContent = { Text("Search in chat", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSearch() }
            )

            // View Profile
            ListItem(
                headlineContent = { Text("View Profile", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onViewProfile() }
            )

            // Clear Chat
            ListItem(
                headlineContent = { Text("Clear chat history", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClearChat() }
            )

            if (hasClearedUndo) {
                ListItem(
                    headlineContent = { Text("Undo clear ($clearedCount msgs)", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onUndoClear() }
                )
            }

            // Mute / Unmute
            ListItem(
                headlineContent = { Text(if (isMuted) "Unmute notifications" else "Mute notifications", fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Icon(
                        if (isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        null,
                        tint = if (isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onToggleMute() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Block / Unblock
            ListItem(
                headlineContent = {
                    Text(
                        if (isBlocked) "Unblock user" else "Block user",
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(
                        if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                        null,
                        tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onToggleBlock() }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// MATERIAL 3 EXPRESSIVE DELETE MESSAGE SHEET
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteMessageSheet(
    message: WhisperMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDeleteForMe: () -> Unit,
) {
    val emojis = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick emojis row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                emojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bouncyClick { onReact(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Reply button
            ToolzTonalExpressiveButton(
                onClick = onReply,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reply to message", fontWeight = FontWeight.SemiBold)
            }

            // Message preview
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message.content.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }

            if (isMine) {
                ToolzExpressiveButton(
                    onClick = onDeleteForEveryone,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete for everyone", fontWeight = FontWeight.Bold)
                }
            }

            ToolzTonalExpressiveButton(
                onClick = onDeleteForMe,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete for me", fontWeight = FontWeight.SemiBold)
            }

            ToolzOutlinedExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// MATERIAL 3 EXPRESSIVE SHEETS & POPUPS
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearChatSheet(
    onDismiss: () -> Unit,
    onSelectRange: (ClearChatTimeRange) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text("Clear Chat History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Deletes your sent messages from all devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        "You'll have 30 seconds to undo this action if needed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val options = listOf(
                Pair("Past 24 hours", ClearChatTimeRange.PAST_24_HOURS),
                Pair("Past 7 days", ClearChatTimeRange.PAST_7_DAYS),
                Pair("Past 30 days", ClearChatTimeRange.PAST_30_DAYS),
                Pair("All time (Everything)", ClearChatTimeRange.ALL_TIME),
            )

            options.forEach { (label, range) ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .bouncyClick { onSelectRange(range) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MuteOptionsDialog(
    onDismiss: () -> Unit,
    onSelectDuration: (Long) -> Unit,
) {
    val durations = listOf(
        Pair("1 Hour", 3_600_000L),
        Pair("8 Hours", 8 * 3_600_000L),
        Pair("1 Week", 7 * 24 * 3_600_000L),
        Pair("Until I turn it back on", Long.MAX_VALUE),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
            }
        },
        title = {
            Text("Mute notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "You will not receive notification sounds or banners for new messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                durations.forEach { (label, durationMs) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .bouncyClick { onSelectDuration(durationMs) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            ToolzOutlinedExpressiveButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun BlockConfirmDialog(
    partnerName: String,
    onDismiss: () -> Unit,
    onConfirmBlock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
            }
        },
        title = {
            Text("Block $partnerName?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                "Blocked users will not be able to send you messages. You can unblock them at any time from this chat menu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = onConfirmBlock,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.Block, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Block User", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            ToolzOutlinedExpressiveButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun FriendGateBanner(
    friendStatus: FriendStatus,
    iAmRequester: Boolean,
    onSendRequest: () -> Unit,
) {
    val (icon, message, showButton) = when {
        friendStatus == FriendStatus.PENDING && iAmRequester ->
            Triple(Icons.Rounded.HourglassTop, stringResource(R.string.st_Whisper_Chat_FriendGateSent), false)
        friendStatus == FriendStatus.PENDING && !iAmRequester ->
            Triple(Icons.Rounded.PersonAdd, stringResource(R.string.st_Whisper_Chat_FriendGateReceived), false)
        friendStatus == FriendStatus.NONE ->
            Triple(Icons.Rounded.Lock, stringResource(R.string.st_Whisper_Chat_FriendGateNone), true)
        else ->
            Triple(Icons.Rounded.Block, stringResource(R.string.st_Whisper_Chat_FriendGateBlocked), false)
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (showButton) {
                ToolzTonalExpressiveButton(onClick = onSendRequest) {
                    Text(stringResource(R.string.st_Whisper_Chat_AddFriend), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PILL-SHAPED DATE SEPARATOR
// ─────────────────────────────────────────────────────────────

@Composable
private fun DateSeparator(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Text(
                date,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// MESSAGE BUBBLE WITH REPLY, REACTIONS & SWIPE GESTURE
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: WhisperMessage,
    isMine: Boolean,
    isPending: Boolean = false,
    isHighlighted: Boolean = false,
    partnerName: String = "User",
    onReply: () -> Unit = {},
    onQuotedClick: (String) -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onReactionClick: (String) -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val isDeleted = message.isDeletedForEveryone

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "swipeReplyOffset"
    )

    // Sending pulse: soft breathing alpha + scale while a message is pending delivery
    val statePulse = rememberInfiniteTransition(label = "msgStatePulse")
    val pulseAlpha by statePulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pendingAlpha"
    )
    val pulseScale by statePulse.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pendingScale"
    )
    val bubbleAlpha = if (isPending) pulseAlpha else 1f
    val bubbleScale = if (isPending) pulseScale else 1f

    val bubbleShape = when {
        isMine  -> RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp,  bottomStart = 20.dp, bottomEnd = 20.dp)
        else    -> RoundedCornerShape(topStart = 4.dp,  topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }
    val bubbleColor = when {
        isHighlighted -> MaterialTheme.colorScheme.primaryContainer
        isDeleted     -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        isMine        -> MaterialTheme.colorScheme.primaryContainer
        else          -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = when {
        isDeleted -> MaterialTheme.colorScheme.outline
        isMine    -> MaterialTheme.colorScheme.onPrimaryContainer
        else      -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Swipe icon indicator
            if (animatedOffsetX > 10f) {
                Icon(
                    Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = "Reply",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                    .widthIn(max = 290.dp)
                    .graphicsLayer { scaleX = bubbleScale; scaleY = bubbleScale }
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .then(
                        if (isHighlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, bubbleShape)
                        else Modifier
                    )
                    .alpha(bubbleAlpha)
                    .combinedClickable(
                        onClick = {},
                        onDoubleClick = onDoubleTap,
                        onLongClick = onLongClick
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount > 0) {
                                    offsetX = (offsetX + dragAmount).coerceIn(0f, 80f)
                                }
                            },
                            onDragEnd = {
                                if (offsetX > 45f) {
                                    onReply()
                                }
                                offsetX = 0f
                            },
                            onDragCancel = { offsetX = 0f }
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    // Quoted replied message preview
                    if (message.replyToContent != null && !isDeleted) {
                        Surface(
                            color = if (isMine) MaterialTheme.colorScheme.surface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .then(
                                    if (message.replyToId != null) {
                                        Modifier.clickable { onQuotedClick(message.replyToId) }
                                    } else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Column {
                                    Text(
                                        message.replyToSenderName ?: "User",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        message.replyToContent,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (isDeleted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = textColor, modifier = Modifier.size(15.dp))
                            Text(
                                text = if (isMine) "You deleted this message" else "$partnerName deleted this message",
                                color = textColor,
                                fontStyle = FontStyle.Italic,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Text(
                            message.content,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        if (isPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.5.dp,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        } else {
                            Text(
                                message.createdAt.formatTimestamp(),
                                color = textColor.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (isMine && !isDeleted) {
                                AnimatedContent(
                                    targetState = message.isRead,
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)))
                                            .togetherWith(fadeOut(tween(120)))
                                    },
                                    label = "readReceipt",
                                ) { read ->
                                    Icon(
                                        if (read) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                                        contentDescription = if (read) "Read" else "Sent",
                                        tint = if (read) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Emoji Reaction Chips
        AnimatedVisibility(
            visible = message.reactions.isNotEmpty(),
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            ),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120)),
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, start = if (isMine) 0.dp else 8.dp, end = if (isMine) 8.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.reactions.forEach { reaction ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (reaction.reactedByMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 1.dp,
                        modifier = Modifier.clickable { onReactionClick(reaction.emoji) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(reaction.emoji, fontSize = 12.sp)
                            if (reaction.count > 1) {
                                Text(
                                    reaction.count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (reaction.reactedByMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
private fun MessageInputBar(
    enabled: Boolean,
    draftText: String,
    placeholderText: String = stringResource(R.string.st_Whisper_Chat_InputPlaceholder),
    pulseTrigger: Int = 0,
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    val canSend = draftText.isNotBlank() && enabled

    // Send pop: a quick bounce whenever a new message is dispatched
    val sendPop = remember { Animatable(1f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger > 0) {
            sendPop.snapTo(1f)
            sendPop.animateTo(1.22f, spring(dampingRatio = 0.5f, stiffness = 900f))
            sendPop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 600f))
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draftText,
                onValueChange = onDraftChanged,
                placeholder = {
                    Text(
                        placeholderText,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
                enabled = enabled,
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = ExtraLargeExpressiveShape,
                modifier = Modifier.weight(1f),
            )

            val sendScale by animateFloatAsState(
                targetValue = if (canSend) 1f else 0.85f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "sendBtnScale"
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .animateContentSize()
                    .graphicsLayer { scaleX = sendScale * sendPop.value; scaleY = sendScale * sendPop.value }
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .bouncyClick(enabled = canSend, onClick = {
                        onSend(draftText.trim())
                    }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Extract date string (YYYY-MM-DD) from ISO timestamp */
fun String.extractDate(): String = if (length >= 10) take(10) else ""

