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

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Individual conversation screen with Material 3 Expressive UI.
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
    var showKeyVerifyDialog by remember { mutableStateOf(false) }
    var showImageOptions by remember { mutableStateOf(false) }
    var selectedImageExpiry by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use(::readBoundedImageBytes) }.getOrNull()
        if (bytes == null) {
            toastState.show("Couldn’t read that image.", WhisperToastType.ERROR)
        } else {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.sendImage(bytes, mimeType, selectedImageExpiry)
        }
    }

    val listState = rememberLazyListState()
    val messages = uiState.messages

    // ── REVERSE LAYOUT (The standard chat pattern) ──
    // Index 0 is now the BOTTOM (newest message).
    val reversedMessages = remember(messages) { messages.asReversed() }

    val isAtBottom by remember {
        derivedStateOf {
            // In reverseLayout, firstVisibleItemIndex is the index from the BOTTOM.
            // Index 0 is the newest message.
            listState.firstVisibleItemIndex <= 1
        }
    }

    // Auto-scroll when new messages arrive
    val newestMessageId = remember(messages) { messages.lastOrNull()?.id }
    LaunchedEffect(newestMessageId) {
        if (newestMessageId == null || uiState.isSearchActive) return@LaunchedEffect
        
        val lastMsg = messages.last()
        val isMine = lastMsg.senderId == viewModel.myUserId
        
        // In reverse layout, item 0 is the newest (bottom).
        // We scroll to 0 if we sent the message or were already at the bottom.
        if (isMine || isAtBottom) {
            delay(100.milliseconds)
            listState.animateScrollToItem(0)
        }
    }

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

    // Auto scroll to active search match
    val matchingIndices = remember(uiState.matchingMessageIds, messages) {
        messages.mapIndexedNotNull { index, msg ->
            if (uiState.matchingMessageIds.contains(msg.id)) index else null
        }
    }
    LaunchedEffect(uiState.activeSearchMatchIndex, matchingIndices) {
        if (uiState.isSearchActive && uiState.activeSearchMatchIndex in matchingIndices.indices) {
            val targetIdx = matchingIndices[uiState.activeSearchMatchIndex]
            // In reverse layout, we need to map the index
            val reverseIdx = messages.size - 1 - targetIdx
            listState.animateScrollToItem(reverseIdx)
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
                                placeholder = { 
                                    Text(
                                        "Search messages…", 
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ) 
                                },
                                singleLine = true,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                ),
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, "Clear search", modifier = Modifier.size(20.dp))
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
                                    val status = user.onlineStatus
                                    val isUserOnline = uiState.isPartnerOnline || status == "Online"
                                    Box {
                                        WhisperAvatar(user, 38.dp)
                                        if (isUserOnline) {
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
                                            else -> status
                                        }
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (uiState.isPartnerTyping || isUserOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } ?: Text("Chat", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    navigationIcon = {
                        if (uiState.isSearchActive) {
                            FilledIconButton(
                                onClick = {
                                    haptic.click()
                                    viewModel.toggleSearch(false)
                                },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = CircleShape
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Exit search")
                            }
                        } else {
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                onNavigateBack()
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_Back))
                            }
                        }
                    },
                    actions = {
                        if (uiState.isSearchActive) {
                            if (uiState.matchingMessageIds.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        "${uiState.activeSearchMatchIndex + 1}/${uiState.matchingMessageIds.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    haptic.click()
                                    viewModel.navigateSearchMatch(-1)
                                }) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, "Previous match", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    haptic.click()
                                    viewModel.navigateSearchMatch(1)
                                }) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, "Next match", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                viewModel.toggleSearch(true)
                            }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search messages")
                            }
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
            CompositionLocalProvider(LocalPerformanceMode provides (uiState.isSearchActive || LocalPerformanceMode.current)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .imePadding(),
                ) {
                // Friend gate banner
                androidx.compose.animation.AnimatedVisibility(
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

                // Blocked banner
                if (uiState.isBlockedByOther) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text("You have been blocked by this user.", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                } else if (uiState.isBlockedByMe) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error)
                            Text("You have blocked this user.", modifier = Modifier.weight(1f))
                            ToolzTonalExpressiveButton(onClick = { viewModel.toggleBlock() }) { Text("Unblock") }
                        }
                    }
                }

                // Key changed banner
                if (uiState.keyTrust?.status == KeyTrustStatus.CHANGED) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "Encryption key changed — verify before trusting this conversation.",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            ToolzTonalExpressiveButton(onClick = { haptic.click(); showKeyVerifyDialog = true }) {
                                Text("Review", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Messages list
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (uiState.isLoading && messages.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ToolzLoadingIndicator() }
                    } else {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize().fadingEdges(top = 16.dp, bottom = 32.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                        ) {
                            itemsIndexed(reversedMessages, key = { _, m -> m.id }) { index, message ->
                                val isMine = message.isSentByMe(viewModel.myUserId)
                                val isPending = message.id.startsWith("pending_")
                                
                                val showDateSeparator = index == reversedMessages.lastIndex ||
                                    reversedMessages[index + 1].createdAt.extractDate() != message.createdAt.extractDate()

                                StaggeredEntrance(index = index) {
                                    MessageBubble(
                                        message = message,
                                        isMine = isMine,
                                        isPending = isPending,
                                        isHighlighted = uiState.matchingMessageIds.contains(message.id),
                                        partnerName = uiState.otherUser?.effectiveName ?: "User",
                                        decryptedImageBytes = uiState.decryptedImageBytes[message.id],
                                        onLoadImage = { viewModel.loadEncryptedImage(message) },
                                        onReply = { haptic.click(); viewModel.setReplyTarget(message) },
                                        onQuotedClick = { targetId ->
                                            val targetIndex = reversedMessages.indexOfFirst { it.id == targetId }
                                            if (targetIndex >= 0) {
                                                haptic.click()
                                                scope.launch { listState.animateScrollToItem(targetIndex) }
                                            }
                                        },
                                        onDoubleTap = { haptic.click(); quickReactionTargetMessage = message },
                                        onReactionClick = { emoji -> haptic.click(); viewModel.toggleReaction(message, emoji) },
                                        onLongClick = { if (!message.isDeletedForEveryone && !isPending) { haptic.longClick(); selectedMessageForDelete = message } }
                                    )
                                }

                                if (showDateSeparator) {
                                    StaggeredEntrance(index = index + 100) {
                                        DateSeparator(message.createdAt.extractDate())
                                    }
                                }
                            }
                        }
                    }

                    // Scroll to bottom FAB: Visible ONLY when NOT at bottom
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAtBottom && messages.isNotEmpty(),
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                haptic.click()
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Scroll to bottom", modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // 30s Undo Banner
                androidx.compose.animation.AnimatedVisibility(visible = uiState.clearedUndoMessagesCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cleared ${uiState.clearedUndoMessagesCount} messages", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
                            ToolzTonalExpressiveButton(onClick = { viewModel.undoClearChat() }) {
                                Text("Undo (${uiState.undoSecondsRemaining}s)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Typing indicator
                androidx.compose.animation.AnimatedVisibility(visible = uiState.isPartnerTyping) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        BouncingDotsIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text("${uiState.otherUser?.effectiveName ?: "Partner"} is typing…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Reply bar
                uiState.replyingToMessage?.let { replyTarget ->
                    ReplyPreviewBar(replyTarget = replyTarget, partnerName = uiState.otherUser?.effectiveName ?: "User", myUserId = viewModel.myUserId, onDismiss = { viewModel.clearReplyTarget() })
                }

                // Input bar
                val draftText by viewModel.draftText.collectAsStateWithLifecycle()
                var sendPulse by remember { mutableIntStateOf(0) }
                LaunchedEffect(messages.size) { if (messages.lastOrNull()?.id?.startsWith("pending_") == true) sendPulse++ }
                
                MessageInputBar(
                    enabled = uiState.friendStatus == FriendStatus.ACCEPTED && !uiState.isBlockedByMe && !uiState.isBlockedByOther,
                    draftText = draftText,
                    placeholderText = when {
                        uiState.isBlockedByOther -> "You have been blocked"
                        uiState.isBlockedByMe -> "Unblock user to send"
                        uiState.friendStatus != FriendStatus.ACCEPTED -> "Accept request to message"
                        else -> "Whisper something..."
                    },
                    pulseTrigger = sendPulse,
                    onDraftChanged = { viewModel.updateDraft(it) },
                    onSend = { viewModel.sendMessage(it) },
                    onImage = { showImageOptions = true },
                    isUploadingImage = uiState.isUploadingAttachment,
                )
            }
        }
    }

        WhisperToastHost(hostState = toastState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }

    // Sheets & Dialogs
    if (showOptionsSheet) {
        ConversationOptionsSheet(
            isMuted = uiState.isMuted,
            isBlocked = uiState.isBlockedByMe,
            hasClearedUndo = uiState.clearedUndoMessagesCount > 0,
            clearedCount = uiState.clearedUndoMessagesCount,
            onDismiss = { showOptionsSheet = false },
            onSearch = { showOptionsSheet = false; viewModel.toggleSearch(true) },
            onClearChat = { showOptionsSheet = false; showClearChatSheet = true },
            onUndoClear = { showOptionsSheet = false; viewModel.undoClearChat() },
            onToggleMute = { showOptionsSheet = false; if (uiState.isMuted) viewModel.toggleMute() else showMuteDialog = true },
            onToggleBlock = { showOptionsSheet = false; if (uiState.isBlockedByMe) viewModel.toggleBlock() else showBlockConfirmDialog = true },
            onViewProfile = { showOptionsSheet = false; uiState.otherUser?.id?.let { onNavigateToProfile(it) } }
        )
    }

    selectedMessageForDelete?.let { msg ->
        DeleteMessageSheet(
            message = msg,
            isMine = msg.isSentByMe(viewModel.myUserId),
            onDismiss = { selectedMessageForDelete = null },
            onReply = { selectedMessageForDelete = null; viewModel.setReplyTarget(msg) },
            onReact = { emoji -> selectedMessageForDelete = null; viewModel.toggleReaction(msg, emoji) },
            onDeleteForEveryone = { selectedMessageForDelete = null; viewModel.deleteMessageForEveryone(msg) },
            onDeleteForMe = { selectedMessageForDelete = null; viewModel.deleteMessageForMe(msg) }
        )
    }

    if (showClearChatSheet) {
        ClearChatSheet(onDismiss = { showClearChatSheet = false }, onSelectRange = { viewModel.clearChat(it); showClearChatSheet = false })
    }

    if (showMuteDialog) {
        MuteOptionsDialog(onDismiss = { showMuteDialog = false }, onSelectDuration = { viewModel.toggleMute(it); showMuteDialog = false })
    }

    if (showBlockConfirmDialog) {
        BlockConfirmDialog(partnerName = uiState.otherUser?.effectiveName ?: "User", onDismiss = { showBlockConfirmDialog = false }, onConfirmBlock = { viewModel.toggleBlock(); showBlockConfirmDialog = false })
    }

    if (showKeyVerifyDialog) {
        KeyVerifyDialog(
            partnerName = uiState.otherUser?.effectiveName ?: "This user",
            partnerFingerprint = uiState.keyTrust?.partnerFingerprint,
            myFingerprint = uiState.keyTrust?.myFingerprint,
            onVerify = { showKeyVerifyDialog = false; viewModel.verifyKey() },
            onAccept = { showKeyVerifyDialog = false; viewModel.acceptNewKey() },
            onDismiss = { showKeyVerifyDialog = false },
        )
    }

    if (showImageOptions) {
        ImageExpiryDialog(
            onDismiss = { showImageOptions = false },
            onSelect = { expiry ->
                selectedImageExpiry = expiry
                showImageOptions = false
                imagePicker.launch("image/*")
            },
        )
    }
    
    quickReactionTargetMessage?.let { targetMsg ->
        QuickReactionDialog(onDismiss = { quickReactionTargetMessage = null }, onEmojiSelected = { quickReactionTargetMessage = null; viewModel.toggleReaction(targetMsg, it) })
    }
}

@Composable
private fun BouncingDotsIndicator(modifier: Modifier = Modifier) {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
        }
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = -5f,
                animationSpec = infiniteRepeatable(animation = keyframes { durationMillis = 900; 0f at 0; -5f at (150 + index * 120); 0f at (350 + index * 120) }, repeatMode = RepeatMode.Restart),
                label = ""
            )
            Box(Modifier.size(6.dp).offset { IntOffset(0, offset.dp.roundToPx()) }.background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun ReplyPreviewBar(replyTarget: WhisperMessage, partnerName: String, myUserId: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(4.dp).height(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            Column(Modifier.weight(1f)) {
                Text("Replying to ${if (replyTarget.isSentByMe(myUserId)) "You" else partnerName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(replyTarget.content, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun QuickReactionDialog(onDismiss: () -> Unit, onEmojiSelected: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), title = { Text("React") },
        text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("❤️", "😂", "👍", "😮", "😢", "🔥").forEach { Text(it, fontSize = 24.sp, modifier = Modifier.clickable { onEmojiSelected(it) }) }
        }}, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationOptionsSheet(isMuted: Boolean, isBlocked: Boolean, hasClearedUndo: Boolean, clearedCount: Int, onDismiss: () -> Unit, onSearch: () -> Unit, onClearChat: () -> Unit, onUndoClear: () -> Unit, onToggleMute: () -> Unit, onToggleBlock: () -> Unit, onViewProfile: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListItem(headlineContent = { Text("Search") }, leadingContent = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.clickable { onSearch() })
            ListItem(headlineContent = { Text("View Profile") }, leadingContent = { Icon(Icons.Rounded.Person, null) }, modifier = Modifier.clickable { onViewProfile() })
            ListItem(headlineContent = { Text("Clear History") }, leadingContent = { Icon(Icons.Rounded.CleaningServices, null) }, modifier = Modifier.clickable { onClearChat() })
            if (hasClearedUndo) ListItem(headlineContent = { Text("Undo Clear ($clearedCount)") }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.Undo, null) }, modifier = Modifier.clickable { onUndoClear() })
            ListItem(headlineContent = { Text(if (isMuted) "Unmute" else "Mute") }, leadingContent = { Icon(if (isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff, null) }, modifier = Modifier.clickable { onToggleMute() })
            ListItem(headlineContent = { Text(if (isBlocked) "Unblock" else "Block", color = Color.Red) }, leadingContent = { Icon(Icons.Rounded.Block, null, tint = Color.Red) }, modifier = Modifier.clickable { onToggleBlock() })
            Spacer(Modifier.height(16.dp))
        }
    }
}

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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("❤️", "😂", "👍", "😮", "😢", "🔥").forEach { Text(it, fontSize = 22.sp, modifier = Modifier.clickable { onReact(it) }) }
            }
            HorizontalDivider()
            ListItem(headlineContent = { Text("Reply") }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.Reply, null) }, modifier = Modifier.clickable { onReply() })
            
            // Message Preview
            Surface(color = Color.Black.copy(0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(message.content.take(100), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            if (isMine) ListItem(headlineContent = { Text("Delete for everyone", color = Color.Red) }, leadingContent = { Icon(Icons.Rounded.DeleteForever, null, tint = Color.Red) }, modifier = Modifier.clickable { onDeleteForEveryone() })
            ListItem(headlineContent = { Text("Delete for me") }, leadingContent = { Icon(Icons.Rounded.Delete, null) }, modifier = Modifier.clickable { onDeleteForMe() })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearChatSheet(onDismiss: () -> Unit, onSelectRange: (ClearChatTimeRange) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clear History", fontWeight = FontWeight.Bold)
            listOf(Pair("Past 24h", ClearChatTimeRange.PAST_24_HOURS), Pair("Past 7d", ClearChatTimeRange.PAST_7_DAYS), Pair("Past 30d", ClearChatTimeRange.PAST_30_DAYS), Pair("All time", ClearChatTimeRange.ALL_TIME)).forEach { (label, range) ->
                ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { onSelectRange(range) })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MuteOptionsDialog(onDismiss: () -> Unit, onSelectDuration: (Long) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mute") }, text = { Column {
        listOf(Pair("1 Hour", 3600000L), Pair("8 Hours", 28800000L), Pair("1 Week", 604800000L), Pair("Forever", Long.MAX_VALUE)).forEach { (l, d) ->
            ListItem(headlineContent = { Text(l) }, modifier = Modifier.clickable { onSelectDuration(d) })
        }
    }}, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun BlockConfirmDialog(partnerName: String, onDismiss: () -> Unit, onConfirmBlock: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Block $partnerName?") }, text = { Text("They won't be able to message you.") },
        confirmButton = { TextButton(onClick = onConfirmBlock) { Text("Block", color = Color.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun KeyVerifyDialog(
    partnerName: String,
    partnerFingerprint: String?,
    myFingerprint: String?,
    onVerify: () -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Encryption key changed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "$partnerName's encryption key is different from the one you last used together. Compare the fingerprint below with the one shown on their device before continuing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (partnerFingerprint != null) {
                    Text(
                        "$partnerName's fingerprint",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            partnerFingerprint,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (myFingerprint != null) {
                    Text(
                        "Your fingerprint",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            myFingerprint,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Text(
                    "Only continue if the fingerprint matches the one on their device. If it doesn't, the person may be impersonated.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onVerify) { Text("Verify", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAccept) { Text("Accept anyway") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun FriendGateBanner(friendStatus: FriendStatus, iAmRequester: Boolean, onSendRequest: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            val msg = when {
                friendStatus == FriendStatus.PENDING && iAmRequester -> "Friend request sent"
                friendStatus == FriendStatus.PENDING && !iAmRequester -> "Friend request received"
                else -> "Add them to start chatting"
            }
            Text(msg, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (friendStatus == FriendStatus.NONE) ToolzTonalExpressiveButton(onClick = onSendRequest) { Text("Add Friend") }
        }
    }
}

@Composable
private fun DateSeparator(date: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(date, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: WhisperMessage,
    isMine: Boolean,
    isPending: Boolean,
    isHighlighted: Boolean,
    partnerName: String,
    decryptedImageBytes: ByteArray?,
    onLoadImage: () -> Unit,
    onReply: () -> Unit,
    onQuotedClick: (String) -> Unit,
    onDoubleTap: () -> Unit,
    onReactionClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val bubbleShape = if (isMine) RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp) else RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    val performanceMode = LocalPerformanceMode.current
    
    // ── SLIDE TO REPLY STATE ──
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (performanceMode) snap() else spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "swipeReplyOffset"
    )

    // Sending pulse
    val statePulse = rememberInfiniteTransition(label = "msgPulse")
    val pulseAlpha by statePulse.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = ""
    )
    val bubbleAlpha = if (isPending && !performanceMode) pulseAlpha else 1f

    Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
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
                    .widthIn(max = 280.dp)
                    .clip(bubbleShape)
                    .background(
                        if (isHighlighted) MaterialTheme.colorScheme.primary 
                        else if (isMine) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .alpha(bubbleAlpha)
                    .combinedClickable(
                        onClick = { }, // Click no longer replies to allow for slide
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
                    .padding(12.dp)
            ) {
                Column {
                    if (message.replyToContent != null) {
                        Surface(color = Color.Black.copy(0.05f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(bottom = 4.dp).clickable { message.replyToId?.let { onQuotedClick(it) } }) {
                            Row(Modifier.padding(8.dp)) {
                                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(message.replyToSenderName ?: "User", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(message.replyToContent ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    
                    if (message.isDeletedForEveryone) {
                        Text(
                            if (isMine) "You deleted this message" else "$partnerName deleted this message",
                            fontStyle = FontStyle.Italic,
                            color = (if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.6f)
                        )
                    } else {
                        val attachment = WhisperImageAttachment.fromMessageContent(message.content)
                        if (attachment != null) {
                            LaunchedEffect(message.id) { onLoadImage() }
                            val bitmap = decryptedImageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Encrypted image",
                                    modifier = Modifier.widthIn(max = 256.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null)
                                    Text(if (attachment.expiresAtEpochSeconds?.let { Instant.now().epochSecond >= it } == true) "Image expired" else "Loading encrypted image…")
                                }
                            }
                        } else {
                        Text(
                            text = message.content.parseMarkdown(),
                            color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        }
                    }
                    
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Text(message.createdAt.formatWhisperTime(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.6f))
                        if (isMine && !message.isDeletedForEveryone) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (message.isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                                null,
                                Modifier.size(14.dp),
                                tint = if (message.isRead) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                message.reactions.forEach { r -> 
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.clickable { onReactionClick(r.emoji) }) {
                        Text("${r.emoji} ${if (r.count > 1) r.count else ""}", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageInputBar(enabled: Boolean, draftText: String, placeholderText: String, pulseTrigger: Int, onDraftChanged: (String) -> Unit, onSend: (String) -> Unit, onImage: () -> Unit, isUploadingImage: Boolean) {
    val performanceMode = LocalPerformanceMode.current
    
    // Send pop animation
    val sendPop = remember { Animatable(1f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger > 0 && !performanceMode) {
            sendPop.snapTo(1f)
            sendPop.animateTo(1.2f, spring(dampingRatio = 0.4f, stiffness = 800f))
            sendPop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f))
        } else if (performanceMode) {
            sendPop.snapTo(1f)
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = onImage, enabled = enabled && !isUploadingImage) {
                if (isUploadingImage) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Image, contentDescription = "Send encrypted image")
            }
            OutlinedTextField(
                value = draftText,
                onValueChange = onDraftChanged,
                placeholder = { Text(placeholderText) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.graphicsLayer { scaleX = sendPop.value; scaleY = sendPop.value }) {
                IconButton(
                    onClick = { onSend(draftText) },
                    enabled = enabled && draftText.isNotBlank(),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, disabledContainerColor = Color.Transparent)
                ) { Icon(Icons.AutoMirrored.Rounded.Send, null) }
            }
        }
    }
}

@Composable
private fun ImageExpiryDialog(onDismiss: () -> Unit, onSelect: (Long?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send encrypted image") },
        text = { Column {
            Text("Images are encrypted before upload. Choose when this image should disappear.")
            listOf(
                "Keep in chat" to null,
                "Disappear in 1 minute" to 60L,
                "Disappear in 1 hour" to 3_600L,
                "Disappear in 1 day" to 86_400L,
            ).forEach { (label, expiry) ->
                ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { onSelect(expiry) })
            }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Extract date string (YYYY-MM-DD) from ISO timestamp */
fun String.extractDate(): String = if (length >= 10) take(10) else ""

fun String.formatWhisperTime(): String {
    return try {
        val instant = Instant.parse(this)
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}

/**
 * Basic Markdown parser for Bold (**text**) and Italic (*text*).
 */
private fun String.parseMarkdown(): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < this.length) {
        when {
            // Bold: **text**
            startsWith("**", i) -> {
                val end = indexOf("**", i + 2)
                if (end != -1 && end > i + 2) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(substring(i + 2, end))
                    builder.pop()
                    i = end + 2
                } else {
                    builder.append("**")
                    i += 2
                }
            }
            // Italic: *text* (must not be followed by another * to avoid bold collision)
            startsWith("*", i) -> {
                val end = indexOf("*", i + 1)
                if (end != -1 && end > i + 1) {
                    // Check if it's actually bold starting (handled above, but for safety)
                    if (this.getOrNull(i + 1) == '*') {
                        builder.append("*")
                        i += 1
                    } else {
                        builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        builder.append(substring(i + 1, end))
                        builder.pop()
                        i = end + 1
                    }
                } else {
                    builder.append("*")
                    i += 1
                }
            }
            else -> {
                builder.append(this[i])
                i++
            }
        }
    }
    return builder.toAnnotatedString()
}

private fun readBoundedImageBytes(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= MAX_LOCAL_IMAGE_BYTES) { "Choose an image smaller than 22 MB." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private const val MAX_LOCAL_IMAGE_BYTES = 22 * 1024 * 1024
