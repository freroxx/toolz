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

import android.content.ContentValues
import android.content.Context

import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.data.whisper.asString
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds


/**
 * Individual conversation screen with Material 3 Expressive UI.
 */
/** V6-R3 UX: bubbles younger than this pop in on first composition (see itemsIndexed). */
private const val NEW_BUBBLE_WINDOW_MS = 15_000L

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WhisperChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    viewModel: WhisperChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val undoState by viewModel.undoState.collectAsStateWithLifecycle()
    val screenshotBypassEnabled by viewModel.screenshotBypassEnabled.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val toastState = rememberWhisperToastState()
    val scope = rememberCoroutineScope()

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showClearChatSheet by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var selectedMessageForDelete by remember { mutableStateOf<WhisperMessage?>(null) }
    var pendingDeleteForEveryone by remember { mutableStateOf<WhisperMessage?>(null) }
    var quickReactionTargetMessage by remember { mutableStateOf<WhisperMessage?>(null) }
    var showKeyVerifyDialog by remember { mutableStateOf(false) }
    // V3-FIX: in-chat QR verification scanner entry (launched from KeyVerifyDialog).
    var showQrScanDialog by remember { mutableStateOf(false) }
    var showImageOptions by remember { mutableStateOf(false) }
    var showBypassDialog by remember { mutableStateOf(false) }
    // One-shot expiry: consumed by the next picker result, then cleared so a stale
    // choice never silently applies to a later image.
    var pendingImageExpiry by remember { mutableStateOf<Long?>(null) }
    var fullScreenImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fullScreenImageMimeType by remember { mutableStateOf("image/jpeg") }
    // V6-R6 (#disappearing): expiry rides with the opened image for the countdown pill.
    var fullScreenImageExpiresAt by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    // E2EE content never appears in screenshots or recent-app previews.
    SecureWindow(bypassEnabled = screenshotBypassEnabled)

    // V2-FIX (reviewwhisper.md) V-1: read receipts only fire while the chat is actually
    // visible — the VM gates every mark-read path behind this ON_START/ON_STOP signal.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onChatVisibilityChanged(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onChatVisibilityChanged(false) }

    // S-6 FIX: busy flag keeps the bypass dialog open (and re-entry blocked) until the
    // verdict arrives, so it can no longer close before success/failure is known.
    var verifyingBypass by remember { mutableStateOf(false) }

    if (showBypassDialog) {
        // M-17 FIX (reviewwhisper.md): password required to enable AND disable; unified copy.
        WhisperScreenshotBypassDialog(
            onDismiss = { if (!verifyingBypass) showBypassDialog = false },
            onConfirm = { password ->
                if (!verifyingBypass) {
                    verifyingBypass = true
                    scope.launch {
                        // FIX: surface WHY verification failed instead of blaming the password
                        // for lockouts/service errors.
                        when (verifyWhisperBypass(password)) {
                            WhisperBypassVerdict.Granted -> {
                                val enabling = !screenshotBypassEnabled
                                viewModel.setScreenshotBypass(enabling)
                                toastState.show(
                                    context.getString(
                                        // S-6 FIX: these two branches were swapped — enabling
                                        // announced "disabled" and vice versa.
                                        if (enabling) R.string.st_Whisper_Bypass_ProtectionOn
                                        else R.string.st_Whisper_Bypass_ProtectionOff
                                    ),
                                    WhisperToastType.SUCCESS
                                )
                            }
                            WhisperBypassVerdict.Denied ->
                                toastState.show(context.getString(R.string.st_Whisper_Error_InvalidCredentials), WhisperToastType.ERROR)
                            WhisperBypassVerdict.RateLimited ->
                                toastState.show(context.getString(R.string.st_Whisper_Bypass_RateLimited), WhisperToastType.ERROR)
                            WhisperBypassVerdict.Unavailable ->
                                toastState.show(context.getString(R.string.st_Whisper_Bypass_Unavailable), WhisperToastType.ERROR)
                        }
                        // S-6 FIX: close only AFTER the verdict was handled.
                        verifyingBypass = false
                        showBypassDialog = false
                    }
                }
            }
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // One-shot expiry: consumed by this picker result, then cleared so a stale
        // choice never silently applies to a later image.
        val expiry = pendingImageExpiry
        pendingImageExpiry = null
        // Read + compress + send all run inside the ViewModel scope, so leaving the
        // screen mid-upload no longer silently cancels the send.
        viewModel.sendImageFromUri(context.applicationContext, uri, expiry)
    }

    val listState = rememberLazyListState()
    val messages = uiState.messages

    // ── REVERSE LAYOUT (The standard chat pattern) ──
    // Index 0 is now the BOTTOM (newest message).
    val reversedMessages = remember(messages) { messages.asReversed() }

    // V2-FIX (reviewwhisper.md) M-5/V-8: quoted-sender names are resolved from ids at
    // render time; legacy persisted names are only a fallback (handled in MessageBubble).
    val messagesById = remember(messages) { messages.associateBy { it.id } }
    val youLabel = stringResource(R.string.st_Whisper_You)
    val partnerLabel = uiState.otherUser?.effectiveName ?: stringResource(R.string.st_Whisper_UserDefault)

    val density = LocalDensity.current
    val isAtBottom by remember(density) {
        derivedStateOf {
            // In reverseLayout, firstVisibleItemIndex is the index from the BOTTOM.
            // Index 0 is the newest message. The scroll offset must also be near zero:
            // a partially scrolled-away bottom row shouldn't count as "at bottom".
            listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset < with(density) { 8.dp.toPx() }
        }
    }

    // V2-FIX (reviewwhisper.md) V-3: load one older history page when the user scrolls
    // near the FAR end of the reverse-layout list (high indices = oldest messages).
    // Re-entry is guarded inside loadOlderMessages().
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsStateWithLifecycle()
    LaunchedEffect(isLoadingOlder) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisible ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && firstVisible >= total - 8 && !isLoadingOlder) {
                    viewModel.loadOlderMessages()
                }
            }
    }

    // Toast host is anchored to the INPUT BAR height instead of a fixed 80dp guess.
    var inputBarHeightPx by remember { mutableIntStateOf(0) }

    // Auto-scroll when new messages arrive
    val newestMessageId = remember(messages) { messages.lastOrNull()?.id }
    LaunchedEffect(newestMessageId) {
        if (newestMessageId == null || uiState.isSearchActive) return@LaunchedEffect
        
        val lastMsg = messages.lastOrNull() ?: return@LaunchedEffect
        val isMine = lastMsg.senderId == viewModel.myUserId
        
        // In reverse layout, item 0 is the newest (bottom).
        // We scroll to 0 if we sent the message or were already at the bottom.
        // Re-check after delay so a user scroll-up during that window isn't stolen.
        if (isMine || isAtBottom) {
            delay(100.milliseconds)
            if (isMine || isAtBottom) {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Auto-logout when session expires
    val sessionExpiredMsg = stringResource(R.string.st_Whisper_Error_SessionExpired)
    LaunchedEffect(Unit) {
        viewModel.sessionExpired.collect {
            // Surface WHY the chat bounced before navigating away.
            toastState.show(sessionExpiredMsg, WhisperToastType.ERROR)
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            toastState.show(it.asString(context), WhisperToastType.ERROR)
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
                    modifier = Modifier.screenshotBypassGesture {
                        // M-17: verification happens inside the dialog for both directions.
                        showBypassDialog = true
                    },
                    title = {
                        if (uiState.isSearchActive) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { 
                                    Text(
                                        stringResource(R.string.st_Whisper_Chat_SearchPlaceholder), 
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
                                            Icon(Icons.Rounded.Close, stringResource(R.string.cd_Whisper_ClearSearch), modifier = Modifier.size(20.dp))
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
                                    // V3-FIX (item 8a): typed presence replaces the
                                    // `== "Online"` literal compare.
                                    val presence = user.presence
                                    val isUserOnline = uiState.isPartnerOnline || presence == WhisperPresence.ONLINE
                                    Box {
                                        WhisperAvatar(user, 38.dp)
                                        if (isUserOnline) {
                                            Box(
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .clip(CircleShape)
                                                    .background(WhisperOnlineGreen)
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
                                                    contentDescription = stringResource(R.string.cd_Whisper_Muted),
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        val subtitle = when {
                                            uiState.isPartnerTyping -> stringResource(R.string.st_Whisper_Typing)
                                            // V3-FIX (item 8a): localized presence label instead
                                            // of the raw English model string.
                                            else -> whisperPresenceLabel(presence)
                                        }
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (uiState.isPartnerTyping || isUserOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } ?: Text(stringResource(R.string.st_Whisper_Title), fontWeight = FontWeight.Bold)
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
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_Whisper_ExitSearch))
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
                                        // Clamp so the counter can never over-report the match count.
                                        "${(uiState.activeSearchMatchIndex + 1).coerceIn(1, uiState.matchingMessageIds.size)}/${uiState.matchingMessageIds.size}",
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
                                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.cd_Whisper_SearchPrev), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    haptic.click()
                                    viewModel.navigateSearchMatch(1)
                                }) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.cd_Whisper_SearchNext), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                viewModel.toggleSearch(true)
                            }) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.cd_Whisper_Search))
                            }
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                showOptionsSheet = true
                            }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_Whisper_Options))
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
                        onAcceptRequest = { haptic.success(); viewModel.acceptFriendRequest() },
                    )
                }

                // Blocked banner
                if (uiState.isBlockedByOther) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(stringResource(R.string.st_Whisper_Chat_BlockedByOther), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                } else if (uiState.isBlockedByMe) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.st_Whisper_Chat_BlockedByMe), modifier = Modifier.weight(1f))
                            ToolzTonalExpressiveButton(onClick = { viewModel.toggleBlock() }) { Text(stringResource(R.string.st_Whisper_Unblock)) }
                        }
                    }
                }

                // Key trust banners — V6-R2 (review): the unreachable ROTATED_MANUAL arm
                // is gone; classifyKeyChange can only return MATCH / ROTATED_AUTO / CHANGED.
                when (uiState.keyTrust?.status) {
                    KeyTrustStatus.CHANGED -> {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text(
                                    stringResource(R.string.st_Whisper_Chat_KeyChanged, uiState.otherUser?.effectiveName ?: "Partner"),
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                ToolzTonalExpressiveButton(onClick = { haptic.click(); showKeyVerifyDialog = true }) {
                                    Text(stringResource(R.string.st_Whisper_Review), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    KeyTrustStatus.ROTATED_AUTO -> {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Autorenew, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(
                                    // P0-1 FIX: rotateMessage is now a localized UiText aligned
                                    // with the real 30-day interval ("every week" is gone).
                                    uiState.keyTrust?.rotateMessage?.asString(context)
                                        ?: stringResource(R.string.st_Whisper_KeyRotate_AutoMonthly, 30),
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                ToolzTonalExpressiveButton(onClick = { haptic.click(); showKeyVerifyDialog = true }) {
                                    Text(stringResource(R.string.st_Whisper_Review), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    else -> {}
                }

                // Realtime Disconnected banner (M-6)
                if (uiState.isRealtimeDisconnected) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.st_Whisper_ConnectionLost),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            ToolzTonalExpressiveButton(onClick = { haptic.click(); viewModel.reconnectRealtime() }) {
                                Text(stringResource(R.string.st_Whisper_Reconnect), fontWeight = FontWeight.Bold)
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
                                val isPending = message.isPending || message.id.startsWith("pending_")

                                val showDateSeparator = index == reversedMessages.lastIndex ||
                                    reversedMessages[index + 1].createdAt.extractDate() != message.createdAt.extractDate()

                                // V6-R6 (#animations): fresh bubbles RISE into place
                                // (fade + upward slide) like mainstream messengers, while
                                // Modifier.animateItem's placement spring smoothly pushes
                                // every previous bubble upwards. History renders statically.
                                val isNewBubble = remember(message.id) {
                                    runCatching {
                                        java.time.OffsetDateTime.parse(message.createdAt).toInstant().toEpochMilli()
                                    }.getOrDefault(0L) > System.currentTimeMillis() - NEW_BUBBLE_WINDOW_MS
                                }
                                val density = LocalDensity.current
                                val appearOffsetY = remember(message.id) {
                                    androidx.compose.animation.core.Animatable(
                                        if (isNewBubble) with(density) { 16.dp.toPx() } else 0f,
                                    )
                                }
                                val appearAlpha = remember(message.id) {
                                    androidx.compose.animation.core.Animatable(if (isNewBubble) 0f else 1f)
                                }
                                LaunchedEffect(message.id, isNewBubble) {
                                    if (!isNewBubble) return@LaunchedEffect
                                    launch { appearAlpha.animateTo(1f, tween(durationMillis = 160)) }
                                    appearOffsetY.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .animateItem(
                                            fadeInSpec = tween(durationMillis = 120),
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                            fadeOutSpec = tween(durationMillis = 120),
                                        )
                                        .graphicsLayer {
                                            alpha = appearAlpha.value
                                            translationY = appearOffsetY.value
                                        }
                                ) {
                                    MessageBubble(
                                        message = message,
                                        isMine = isMine,
                                        isPending = isPending,
                                        isHighlighted = uiState.matchingMessageIds.contains(message.id),
                                        partnerName = partnerLabel,
                                        // V2-FIX (reviewwhisper.md) V-8: id-based resolution of the
                                        // quoted sender; null when the target row is unavailable so
                                        // legacy persisted names still render as fallback.
                                        resolveReplySenderName = { targetId ->
                                            messagesById[targetId]?.let { target ->
                                                if (target.senderId == viewModel.myUserId) youLabel else partnerLabel
                                            }
                                        },
                                        decryptedImageBytes = uiState.decryptedImageBytes[message.id],
                                        onLoadImage = { viewModel.loadEncryptedImage(message) },
                                        onImageClick = { bytes, mime, expiresAt ->
                                            haptic.click()
                                            fullScreenImageBytes = bytes
                                            fullScreenImageMimeType = mime
                                            fullScreenImageExpiresAt = expiresAt
                                        },
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
                                    Box(modifier = Modifier.animateItem()) {
                                        DateSeparator(message.createdAt)
                                    }
                                }
                            }

                            // V2-FIX (reviewwhisper.md) V-3: spinner at the far (oldest) end
                            // while the previous page loads.
                            if (isLoadingOlder) {
                                item(key = "whisper_loading_older") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
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
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_Whisper_ScrollBottom), modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // 30s Undo Banner (undo countdown state is separate from chat state so the
                // 1 Hz tick doesn't recompose the message list; UndoBanner reads
                // secondsRemaining itself so only the banner subtree recomposes)
                UndoBanner(undoState = undoState, onUndo = { viewModel.undoClearChat() })

                // Typing indicator
                androidx.compose.animation.AnimatedVisibility(visible = uiState.isPartnerTyping) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        BouncingDotsIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.st_Whisper_PartnerTyping, uiState.otherUser?.effectiveName ?: stringResource(R.string.st_Whisper_UserDefault)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Reply bar
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.replyingToMessage != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    uiState.replyingToMessage?.let { replyTarget ->
                        ReplyPreviewBar(
                            replyTarget = replyTarget,
                            partnerName = uiState.otherUser?.effectiveName ?: stringResource(R.string.st_Whisper_UserDefault),
                            myUserId = viewModel.myUserId,
                            decryptedImageBytes = uiState.decryptedImageBytes[replyTarget.id],
                            onDismiss = { viewModel.clearReplyTarget() }
                        )
                    }
                }

                // Input bar
                // S-1 FIX (reviewwhisper.md): the draft StateFlow is collected INSIDE the
                // wrapper composable, so a keystroke recomposes only this subtree instead
                // of the whole Scaffold/column (the old inline collect invalidated every
                // banner, list and sheet read on each character).
                var sendPulse by remember { mutableIntStateOf(0) }
                LaunchedEffect(messages.size) { if (messages.lastOrNull()?.isPending == true) sendPulse++ }
                // Low FIX: remember the (previously unstable) lambdas handed to the bar.
                val onDraftChanged = remember { { text: String -> viewModel.updateDraft(text) } }
                val onSend = remember { { text: String -> viewModel.sendMessage(text) } }
                val onImageClick = remember { { showImageOptions = true } }

                WhisperDraftBoundInputBar(
                    draftFlow = viewModel.draftText,
                    enabled = uiState.friendStatus == FriendStatus.ACCEPTED && !uiState.isBlockedByMe && !uiState.isBlockedByOther,
                    placeholderText = when {
                        uiState.isBlockedByOther -> stringResource(R.string.st_Whisper_Chat_InputBlocked)
                        uiState.isBlockedByMe -> stringResource(R.string.st_Whisper_Chat_InputUnblock)
                        uiState.friendStatus != FriendStatus.ACCEPTED -> stringResource(R.string.st_Whisper_Chat_InputAcceptRequest)
                        else -> stringResource(R.string.st_Whisper_Chat_InputPlaceholder)
                    },
                    pulseTrigger = sendPulse,
                    isUploadingImage = uiState.isUploadingAttachment,
                    modifier = Modifier.onSizeChanged { inputBarHeightPx = it.height },
                    onDraftChanged = onDraftChanged,
                    onSend = onSend,
                    onImage = onImageClick,
                )
            }
        }
    }

        WhisperToastHost(
            hostState = toastState,
            // Low FIX: pad relative to the real input-bar height, not a fixed 80dp.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = with(density) { inputBarHeightPx.toDp() } + 12.dp)
        )
    }

    // Sheets & Dialogs
    if (showOptionsSheet) {
        ConversationOptionsSheet(
            isMuted = uiState.isMuted,
            isBlocked = uiState.isBlockedByMe,
            hasClearedUndo = undoState.clearedCount > 0,
            clearedCount = undoState.clearedCount,
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
            // V6-R6 (#receipts): long-press now shows exactly where the message is —
            // pending / delivered / read.
            statusLine = if (msg.isSentByMe(viewModel.myUserId)) {
                when {
                    msg.isPending || msg.id.startsWith("pending_") ->
                        MessageStatusLine(Icons.Rounded.Schedule, stringResource(R.string.st_Whisper_MsgStatus_Pending))
                    msg.isRead -> MessageStatusLine(Icons.Rounded.DoneAll, stringResource(R.string.st_Whisper_MsgStatus_Read))
                    else -> MessageStatusLine(Icons.Rounded.Done, stringResource(R.string.st_Whisper_MsgStatus_Sent))
                }
            } else null,
            onDismiss = { selectedMessageForDelete = null },
            onReply = { selectedMessageForDelete = null; viewModel.setReplyTarget(msg) },
            onReact = { emoji -> selectedMessageForDelete = null; viewModel.toggleReaction(msg, emoji) },
            onDeleteForEveryone = { selectedMessageForDelete = null; pendingDeleteForEveryone = msg },
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
        BlockConfirmDialog(partnerName = uiState.otherUser?.effectiveName ?: stringResource(R.string.st_Whisper_UserDefault), onDismiss = { showBlockConfirmDialog = false }, onConfirmBlock = { viewModel.toggleBlock(); showBlockConfirmDialog = false })
    }

    // Delete-for-everyone is irreversible and affects the partner too — confirm first.
    pendingDeleteForEveryone?.let { msg ->
        AlertDialog(
            onDismissRequest = { pendingDeleteForEveryone = null },
            title = { Text(stringResource(R.string.st_Whisper_DeleteForEveryoneTitle)) },
            text = { Text(stringResource(R.string.st_Whisper_DeleteForEveryoneDesc)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteForEveryone = null
                    viewModel.deleteMessageForEveryone(msg)
                }) {
                    Text(stringResource(R.string.st_Whisper_Delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteForEveryone = null }) { Text(stringResource(R.string.st_Whisper_Cancel)) } }
        )
    }

    if (showKeyVerifyDialog) {
        KeyVerifyDialog(
            partnerName = uiState.otherUser?.effectiveName ?: stringResource(R.string.st_Whisper_UserDefault),
            partnerFingerprint = uiState.keyTrust?.partnerFingerprint,
            myFingerprint = uiState.keyTrust?.myFingerprint,
            onVerify = { showKeyVerifyDialog = false; viewModel.verifyKey() },
            onAccept = { showKeyVerifyDialog = false; viewModel.acceptNewKey() },
            onDismiss = { showKeyVerifyDialog = false },
            // V3-FIX: offer the QR path instead of eyeballing fingerprints.
            onScanQr = { showKeyVerifyDialog = false; showQrScanDialog = true },
        )
    }

    // V3-FIX: QR verification scanner — compares the scanned fingerprint against the one
    // computed LOCALLY from the stored partner public key (never against server data).
    val qrExpectedFingerprint = remember(uiState.otherUser?.publicKey) {
        uiState.otherUser?.publicKey?.let { WhisperCrypto.computeFingerprint(it) }
    }
    if (showQrScanDialog) {
        WhisperQrScanDialog(
            partnerName = uiState.otherUser?.effectiveName ?: stringResource(R.string.st_Whisper_UserDefault),
            expectedPartnerFingerprint = qrExpectedFingerprint,
            toastState = toastState,
            haptic = haptic,
            onVerified = {
                showQrScanDialog = false
                viewModel.verifyKey()
            },
            onDismiss = { showQrScanDialog = false },
        )
    }

    if (showImageOptions) {
        ImageExpirySheet(
            onDismiss = { showImageOptions = false },
            onSelect = { expiry ->
                pendingImageExpiry = expiry
                showImageOptions = false
                imagePicker.launch("image/*")
            },
        )
    }
    
    quickReactionTargetMessage?.let { targetMsg ->
        QuickReactionDialog(onDismiss = { quickReactionTargetMessage = null }, onEmojiSelected = { quickReactionTargetMessage = null; viewModel.toggleReaction(targetMsg, it) })
    }

    fullScreenImageBytes?.let { bytes ->
        WhisperFullScreenImageViewer(
            expiresAtEpochSeconds = fullScreenImageExpiresAt,
            imageBytes = bytes,
            mimeType = fullScreenImageMimeType,
            onDismiss = { fullScreenImageBytes = null },
            onShowToast = { text, type -> toastState.show(text, type) }
        )
    }
}


@Composable
private fun UndoBanner(undoState: WhisperUndoUiState, onUndo: () -> Unit) {
    // Reads secondsRemaining itself so the 1 Hz countdown only recomposes this subtree,
    // never the whole message list column. The message list is deliberately NOT a param.
    androidx.compose.animation.AnimatedVisibility(visible = undoState.clearedCount > 0) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_Whisper_Chat_ClearedMessages, undoState.clearedCount), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
                ToolzTonalExpressiveButton(onClick = onUndo) {
                    Text(stringResource(R.string.st_Whisper_Undo, undoState.secondsRemaining), fontWeight = FontWeight.Bold)
                }
            }
        }
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
private fun ReplyPreviewBar(
    replyTarget: WhisperMessage,
    partnerName: String,
    myUserId: String,
    decryptedImageBytes: ByteArray? = null,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            
            // Thumbnail for image replies — decode off Main to avoid jank when replying to image
            val attachment = WhisperImageAttachment.fromMessageContent(replyTarget.content)
            if (attachment != null) {
                var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(decryptedImageBytes) {
                    val newBitmap = withContext(Dispatchers.Default) {
                        decryptedImageBytes?.let { decodeBoundedBitmap(it, 34, 34) }
                    }
                    val old = bitmap
                    bitmap = newBitmap
                    if (old != null && old != newBitmap) old.recycle()
                }
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Image, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        R.string.st_Whisper_ReplyingTo,
                        if (replyTarget.isSentByMe(myUserId)) stringResource(R.string.st_Whisper_You) else partnerName
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (attachment != null) stringResource(R.string.st_Whisper_Image)
                    else if (replyTarget.isDeletedForEveryone) stringResource(R.string.st_Whisper_MessageDeleted)
                    else replyTarget.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}


@Composable
private fun QuickReactionDialog(onDismiss: () -> Unit, onEmojiSelected: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), title = { Text(stringResource(R.string.st_Whisper_React)) },
        text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WHISPER_QUICK_REACTIONS.forEach { Text(it, fontSize = 24.sp, modifier = Modifier.clickable { onEmojiSelected(it) }) }
        }}, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationOptionsSheet(
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
    onViewProfile: () -> Unit
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(stringResource(R.string.st_Whisper_Chat_Options), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.st_Whisper_ManageConversation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // M-16 FIX: shared rows — identical behavior/labels to the hub screen's sheet.
            WhisperOptionsListItem(
                leadingIcon = Icons.Rounded.Search,
                label = stringResource(R.string.st_Whisper_SearchLabel),
                onClick = onSearch,
            )
            WhisperOptionsListItem(
                leadingIcon = Icons.Rounded.Person,
                label = stringResource(R.string.st_Whisper_ViewProfile),
                onClick = onViewProfile,
            )
            WhisperOptionsListItem(
                leadingIcon = Icons.Rounded.CleaningServices,
                label = stringResource(R.string.st_Whisper_ClearHistory),
                onClick = onClearChat,
            )
            if (hasClearedUndo) {
                WhisperOptionsListItem(
                    leadingIcon = Icons.AutoMirrored.Rounded.Undo,
                    label = stringResource(R.string.st_Whisper_UndoClearCount, clearedCount),
                    onClick = onUndoClear,
                )
            }
            WhisperOptionsListItem(
                leadingIcon = if (isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                iconTint = if (isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = if (isMuted) stringResource(R.string.st_Whisper_UnmuteNotifications)
                else stringResource(R.string.st_Whisper_MuteNotifications),
                onClick = onToggleMute,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            WhisperOptionsListItem(
                leadingIcon = if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                iconTint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                label = if (isBlocked) stringResource(R.string.st_Whisper_UnblockUser) else stringResource(R.string.st_Whisper_BlockUser),
                labelColor = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                onClick = onToggleBlock,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** V6-R6 (#receipts): long-press status payload for own messages. */
private data class MessageStatusLine(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteMessageSheet(
    message: WhisperMessage,
    isMine: Boolean,
    statusLine: MessageStatusLine?,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDeleteForMe: () -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // V6-R6 (#receipts): delivery status header for own messages.
            if (isMine && statusLine != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            statusLine.icon,
                            contentDescription = null,
                            tint = if (message.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                stringResource(R.string.st_Whisper_MsgStatus_Title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                statusLine.label + " · " + message.createdAt.formatWhisperTime(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            // Quick Emoji Reaction Bar (Expressive circular pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WHISPER_QUICK_REACTIONS.forEach { emoji ->
                    Surface(
                        onClick = { onReact(emoji) },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            // Message Preview (Subtle tinted bubble).
            // Ciphertext envelopes are never shown raw: images preview as a label.
            // V6-R2 (review): lock sentinels preview as the localized placeholder too.
            val previewText = when {
                message.isDeletedForEveryone -> stringResource(R.string.st_Whisper_MessageDeleted)
                WhisperImageAttachment.fromMessageContent(message.content) != null ->
                    "📷 " + stringResource(R.string.st_Whisper_Image)
                com.frerox.toolz.data.whisper.WhisperTombstone.isLockedMarker(message.content) ->
                    stringResource(R.string.st_Whisper_Locked_Short)
                else -> message.content.take(100)
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onReply() }
            ) {
                Text(stringResource(R.string.st_Whisper_Reply), fontWeight = FontWeight.Medium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            if (isMine) {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onDeleteForEveryone() }
                ) {
                    Text(stringResource(R.string.st_Whisper_DeleteForEveryone), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            }
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Delete, null, tint = if (isMine) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onDeleteForMe() }
            ) {
                Text(stringResource(R.string.st_Whisper_DeleteForMe), fontWeight = FontWeight.Medium, color = if (isMine) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearChatSheet(onDismiss: () -> Unit, onSelectRange: (ClearChatTimeRange) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(stringResource(R.string.st_Whisper_ClearHistory), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.st_Whisper_SelectClearRange), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            listOf(
                Pair(stringResource(R.string.st_Whisper_Clear_Past24h), ClearChatTimeRange.PAST_24_HOURS),
                Pair(stringResource(R.string.st_Whisper_Clear_Past7d), ClearChatTimeRange.PAST_7_DAYS),
                Pair(stringResource(R.string.st_Whisper_Clear_Past30d), ClearChatTimeRange.PAST_30_DAYS),
                Pair(stringResource(R.string.st_Whisper_Clear_AllTime), ClearChatTimeRange.ALL_TIME)
            ).forEach { (label, range) ->
                ListItem(
                    leadingContent = {
                        Icon(
                            if (range == ClearChatTimeRange.ALL_TIME) Icons.Rounded.DeleteForever else Icons.Rounded.Schedule,
                            null,
                            tint = if (range == ClearChatTimeRange.ALL_TIME) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelectRange(range) }
                ) {
                    Text(
                        label,
                        fontWeight = if (range == ClearChatTimeRange.ALL_TIME) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (range == ClearChatTimeRange.ALL_TIME) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageExpirySheet(onDismiss: () -> Unit, onSelect: (Long?) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
                 Column {
                     // Low FIX: a content-description resource was reused as this sheet's
                     // TITLE; the dedicated st_Whisper_ImageExpiry_Title now covers it.
                     Text(stringResource(R.string.st_Whisper_ImageExpiry_Title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                     Text(stringResource(R.string.st_Whisper_ImageExpiryDesc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                 }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            listOf(
                Triple(stringResource(R.string.st_Whisper_Expiry_Never), null, Icons.Rounded.AllInclusive),
                Triple(stringResource(R.string.st_Whisper_Expiry_1m), 60L, Icons.Rounded.Timelapse),
                Triple(stringResource(R.string.st_Whisper_Expiry_1h), 3_600L, Icons.Rounded.HourglassTop),
                Triple(stringResource(R.string.st_Whisper_Expiry_1d), 86_400L, Icons.Rounded.Today),
            ).forEach { (label, expiry, icon) ->
                ListItem(
                    leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelect(expiry) }
                ) {
                    Text(label, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


@Composable
private fun MuteOptionsDialog(onDismiss: () -> Unit, onSelectDuration: (Long) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.st_Whisper_Mute)) }, text = { Column {
        listOf(Pair(stringResource(R.string.st_Whisper_Mute_1h), 3600000L), Pair(stringResource(R.string.st_Whisper_Mute_8h), 28800000L), Pair(stringResource(R.string.st_Whisper_Mute_1w), 604800000L), Pair(stringResource(R.string.st_Whisper_Mute_Forever), Long.MAX_VALUE)).forEach { (l, d) ->
            ListItem(modifier = Modifier.clickable { onSelectDuration(d) }) {
                Text(l)
            }
        }
    }}, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) } })
}

@Composable
private fun BlockConfirmDialog(partnerName: String, onDismiss: () -> Unit, onConfirmBlock: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.st_Whisper_BlockConfirmTitle, partnerName)) }, text = { Text(stringResource(R.string.st_Whisper_BlockConfirmDesc)) },
        confirmButton = { TextButton(onClick = onConfirmBlock) { Text(stringResource(R.string.st_Whisper_Block), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) } })
}

@Composable
private fun KeyVerifyDialog(
    partnerName: String,
    partnerFingerprint: String?,
    myFingerprint: String?,
    onVerify: () -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    // V3-FIX: when provided, a "Scan verification QR" shortcut is offered next to the
    // manual compare path.
    onScanQr: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.st_Whisper_KeyChanged_Title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.st_Whisper_KeyChanged_Desc, partnerName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (partnerFingerprint != null) {
                    val formattedPartner = partnerFingerprint.split("-").let { groups ->
                        if (groups.size == 8) "${groups.take(4).joinToString("-")}\n${groups.drop(4).joinToString("-")}"
                        else partnerFingerprint
                    }
                    Text(
                        stringResource(R.string.st_Whisper_PartnerFingerprint, partnerName),
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
                            formattedPartner,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (myFingerprint != null) {
                    val formattedMine = myFingerprint.split("-").let { groups ->
                        if (groups.size == 8) "${groups.take(4).joinToString("-")}\n${groups.drop(4).joinToString("-")}"
                        else myFingerprint
                    }
                    Text(
                        stringResource(R.string.st_Whisper_YourFingerprint),
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
                            formattedMine,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Text(
                    stringResource(R.string.st_Whisper_KeyChanged_Warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                // V6-R2 (review): state the forward-secrecy reassurance explicitly — a key
                // change never endangers already-delivered mail because those message keys
                // were destroyed after use.
                Text(
                    stringResource(R.string.st_Whisper_KeyChanged_FsNote),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // V3-FIX: QR scan shortcut — the whole point is not comparing hex by eye.
                if (onScanQr != null) {
                    TextButton(onClick = onScanQr) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.st_Whisper_QrScanButton), fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onVerify) { Text(stringResource(R.string.st_Whisper_Verify), fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAccept) { Text(stringResource(R.string.st_Whisper_AcceptAnyway)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
            }
        }
    )
}

@Composable
private fun FriendGateBanner(friendStatus: FriendStatus, iAmRequester: Boolean, onSendRequest: () -> Unit, onAcceptRequest: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            val msg = when {
                friendStatus == FriendStatus.PENDING && iAmRequester -> stringResource(R.string.st_Whisper_FriendRequestSent)
                friendStatus == FriendStatus.PENDING && !iAmRequester -> stringResource(R.string.st_Whisper_FriendRequestReceived)
                else -> stringResource(R.string.st_Whisper_AddFriendToChat)
            }
            Text(msg, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
            when {
                friendStatus == FriendStatus.NONE -> ToolzTonalExpressiveButton(onClick = onSendRequest) { Text(stringResource(R.string.st_Whisper_Chat_AddFriend)) }
                friendStatus == FriendStatus.PENDING && !iAmRequester -> ToolzTonalExpressiveButton(onClick = onAcceptRequest) { Text(stringResource(R.string.st_Whisper_Accept)) }
            }
        }
    }
}

@Composable
private fun DateSeparator(isoTimestamp: String) {
    // S-5 FIX (reviewwhisper.md): localized SHORT date instead of the raw ISO
    // "YYYY-MM-DD" slice; formatter remembered per spec (immutable, cheap to share).
    val dateLabel = remember(isoTimestamp) { isoTimestamp.formatWhisperDate() }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(dateLabel, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
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
    resolveReplySenderName: (String) -> String?,
    decryptedImageBytes: ByteArray?,
    onLoadImage: () -> Unit,
    onImageClick: (ByteArray, String, Long?) -> Unit,
    onReply: () -> Unit,
    onQuotedClick: (String) -> Unit,
    onDoubleTap: () -> Unit,
    onReactionClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val bubbleShape = if (isMine) RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp) else RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    val performanceMode = LocalPerformanceMode.current
    // Gesture hints for accessibility (resolved once; the semantics block isn't composable).
    val messageSemanticsCd = stringResource(R.string.st_Whisper_MessageSemantics)

    // V2-FIX (reviewwhisper.md) M-5/V-8: prefer id-based resolution of the quoted sender;
    // fall back to the legacy persisted name for old rows that carry one.
    val quotedSenderLabel = message.replyToId?.let(resolveReplySenderName)
        ?: message.replyToSenderName
        ?: stringResource(R.string.st_Whisper_UserDefault)

    // S-2 FIX (reviewwhisper.md): search-highlighted bubbles flip to primary/onPrimary so
    // the highlight is high-contrast; every nested content color derives from this pair.
    val bubbleContainer = if (isHighlighted) MaterialTheme.colorScheme.primary
        else if (isMine) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val bubbleContentColor = if (isHighlighted) MaterialTheme.colorScheme.onPrimary
        else if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    // L-7 FIX (reviewwhisper.md): the swipe accumulated only positive drag deltas, so in
    // RTL locales the gesture was effectively dead. The delta is now mirrored for RTL.
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current

    // S-4 FIX (reviewwhisper.md): the reply-swipe thresholds were raw pixel values
    // (45px/80px ≈ 15dp/27dp on a 3x display), so the gesture needed a different finger
    // travel on every density. They are density-stable dp now.
    val swipeMaxPx = with(LocalDensity.current) { SWIPE_REPLY_MAX.toPx() }
    val swipeTriggerPx = with(LocalDensity.current) { SWIPE_REPLY_TRIGGER.toPx() }

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
                    contentDescription = stringResource(R.string.cd_Whisper_Reply),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                    // Low FIX: bubble width is fraction-based (85% of the row) with a hard
                    // 320dp ceiling — the old fixed 280.dp max ignored both screen size and
                    // the swipe affordance. weight(fill = false) hugs short content.
                    .weight(0.85f, fill = false)
                    .widthIn(max = 320.dp)
                    .clip(bubbleShape)
                    .background(bubbleContainer)
                    .alpha(bubbleAlpha)
                    .combinedClickable(
                        onClick = { }, // Click no longer replies to allow for slide
                        onDoubleClick = onDoubleTap,
                        onLongClick = onLongClick
                    )
                    // Surface the swipe/double-tap gestures to screen readers.
                    .semantics { contentDescription = messageSemanticsCd }
                    .pointerInput(layoutDirection, swipeMaxPx, swipeTriggerPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                // L-7: mirror the delta under RTL so the swipe always runs
                                // toward the reply affordance side.
                                val effective = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) -dragAmount else dragAmount
                                if (effective > 0) {
                                    offsetX = (offsetX + effective).coerceIn(0f, swipeMaxPx)
                                }
                            },
                            onDragEnd = {
                                if (offsetX >= swipeTriggerPx) {
                                    onReply()
                                }
                                offsetX = 0f
                            },
                            onDragCancel = { offsetX = 0f }
                        )
                    }
                    .padding(12.dp)
            ) {
                // S-2 FIX: nested content inherits onPrimary while highlighted (quote texts,
                // timestamp, image-state rows) instead of scheme defaults tuned for surfaces.
                CompositionLocalProvider(LocalContentColor provides bubbleContentColor) {
                Column {
                    if (message.replyToContent != null) {
                        // S-2 FIX: on a primary-highlighted bubble the old black@5% quote
                        // tint is invisible — switch to an onPrimary-based tint + accent.
                        Surface(
                            color = if (isHighlighted) bubbleContentColor.copy(alpha = 0.18f) else Color.Black.copy(0.05f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp).clickable { message.replyToId?.let { onQuotedClick(it) } }
                        ) {
                            Row(Modifier.padding(8.dp)) {
                                Box(Modifier.width(2.dp).fillMaxHeight().background(if (isHighlighted) bubbleContentColor else MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(quotedSenderLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    // Image replies are flagged with the attachment prefix by the ViewModel
                                    // (locale-independent, model-based) instead of a display-string sentinel.
                                    val isImage = message.replyToContent?.startsWith(WhisperImageAttachment.MESSAGE_PREFIX) == true
                                    val isDeletedQuote = message.replyToContent?.startsWith("[deleted_by_sender") == true || message.replyToContent == stringResource(R.string.st_Whisper_MessageDeleted)
                                    // V6-R2 (review): lock sentinels quoted as replies showed raw English.
                                    val isLockedQuote = message.replyToContent?.let {
                                        com.frerox.toolz.data.whisper.WhisperTombstone.isLockedMarker(it)
                                    } == true
                                    Text(
                                        when {
                                            isImage -> "📷 " + stringResource(R.string.st_Whisper_Image)
                                            isDeletedQuote -> stringResource(R.string.st_Whisper_MessageDeleted)
                                            isLockedQuote -> stringResource(R.string.st_Whisper_Locked_Short)
                                            else -> message.replyToContent ?: ""
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    
                    if (message.isDeletedForEveryone) {
                        Text(
                            if (isMine) stringResource(R.string.st_Whisper_YouDeleted) else stringResource(R.string.st_Whisper_PartnerDeleted, partnerName),
                            fontStyle = FontStyle.Italic,
                            // S-2 FIX: derive from the bubble's content color (onPrimary when highlighted).
                            color = bubbleContentColor.copy(alpha = 0.6f)
                        )
                    } else {
                        val attachment = WhisperImageAttachment.fromMessageContent(message.content)
                        if (attachment != null) {
                            // A failed decrypt used to leave "Loading image" up forever. Track a
                            // per-message retry key + a bounded failure window so the user can
                            // re-trigger the load when decryption doesn't resolve.
                            var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                            // V6-R6 (#disappearing): live expiry gate — a 1s ticker
                            // recomposes this bubble so the image (and its badge) flips
                            // to the honest expired state EXACTLY when the countdown
                            // ends, no chat re-entry required.
                            var expiredNow by remember(message.id) { mutableStateOf(false) }
                            val isExpiredImage = attachment.expiresAtEpochSeconds?.let {
                                Instant.now().epochSecond >= it
                            } == true || expiredNow
                            LaunchedEffect(attachment.expiresAtEpochSeconds) {
                                if (attachment.expiresAtEpochSeconds == null) return@LaunchedEffect
                                while (isActive) {
                                    delay(1_000)
                                    if (Instant.now().epochSecond >= attachment.expiresAtEpochSeconds) {
                                        expiredNow = true
                                        bitmap?.recycle()
                                        bitmap = null
                                        break
                                    }
                                }
                            }
                            var imageRetryKey by remember { mutableStateOf(0) }
                            var imageLoadFailed by remember { mutableStateOf(false) }
                            // Reload on first appearance and on every explicit retry.
                            // V6-R6: never fetch an already-expired image.
                            LaunchedEffect(message.id, imageRetryKey, isExpiredImage) {
                                if (!isExpiredImage) onLoadImage()
                            }
                            // Give the decrypt a bounded window; if nothing arrives, surface a
                            // retry affordance instead of an endless spinner.
                            LaunchedEffect(message.id, imageRetryKey, decryptedImageBytes, message.isPending) {
                                if (decryptedImageBytes == null && !message.isPending) {
                                    imageLoadFailed = false
                                    delay(12_000)
                                    imageLoadFailed = true
                                } else {
                                    imageLoadFailed = false
                                }
                            }
                            LaunchedEffect(decryptedImageBytes) {
                                if (expiredNow || isExpiredImage) return@LaunchedEffect
                                val newBitmap = withContext(Dispatchers.Default) {
                                    decryptedImageBytes?.let { decodeBoundedBitmap(it, 256, 320) }
                                }
                                val old = bitmap
                                bitmap = newBitmap
                                if (old != null && old != newBitmap) old.recycle()
                            }
                            val bmp = bitmap
                            if (bmp != null && !isExpiredImage) {
                                Box {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = stringResource(R.string.cd_Whisper_EncryptedImage),
                                        modifier = Modifier
                                            .widthIn(max = 256.dp)
                                            .heightIn(max = 320.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                decryptedImageBytes?.let {
                                                    if (!isExpiredImage) {
                                                        onImageClick(it, attachment.mimeType, attachment.expiresAtEpochSeconds)
                                                    }
                                                }
                                            },
                                    )
                                    // V6-R6 (#disappearing): visible cue that this image
                                    // will self-destruct — timer badge on the thumbnail.
                                    if (attachment.expiresAtEpochSeconds != null && !isExpiredImage) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color.Black.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Timer,
                                                    contentDescription = stringResource(R.string.cd_Whisper_DisappearingImage),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                when {
                                    attachment.expiresAtEpochSeconds?.let { Instant.now().epochSecond >= it } == true -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Rounded.Lock, contentDescription = null)
                                            Text(stringResource(R.string.st_Whisper_ImageExpired))
                                        }
                                    }
                                    imageLoadFailed -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Rounded.Lock, contentDescription = null)
                                                Text(stringResource(R.string.st_Whisper_ImageLoadFailed))
                                            }
                                            TextButton(
                                                onClick = { imageRetryKey++ },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text(stringResource(R.string.st_Whisper_Retry), style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                    else -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Rounded.Lock, contentDescription = null)
                                            Text(stringResource(R.string.st_Whisper_LoadingImage))
                                        }
                                    }
                                }
                            }
                                        } else if (com.frerox.toolz.data.whisper.WhisperTombstone.isLockedMarker(message.content)) {
                                            // V6-R2 (review): lock sentinels used to fall through to
                                            // the markdown body and render as ordinary quoted/searchable
                                            // text. Render them as the honest localized placeholder.
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Lock,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = bubbleContentColor.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    stringResource(R.string.st_Whisper_Locked_Message),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontStyle = FontStyle.Italic,
                                                    color = bubbleContentColor.copy(alpha = 0.7f)
                                                )
                                            }
                                        } else {
                        val parsedContent = remember(message.content) { message.content.parseMarkdown() }
                        Text(
                            text = parsedContent,
                            // S-2 FIX: onPrimary while search-highlighted (AA contrast).
                            color = bubbleContentColor
                        )
                        }
                    }

                    
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Text(message.createdAt.formatWhisperTime(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.6f))
                        if (isMine && !message.isDeletedForEveryone) {
                            Spacer(Modifier.width(4.dp))
                            MessageStatusTick(
                                isPending = isPending,
                                isRead = message.isRead,
                                isHighlighted = isHighlighted,
                                bubbleContentColor = bubbleContentColor,
                            )
                        }
                    }
                }
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                message.reactions.forEach { r ->
                    // Low FIX: the AnimatedVisibility here was a no-op (visible = true
                    // constant) that still allocated animation state per pill.
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.clickable { onReactionClick(r.emoji) }) {
                        Text("${r.emoji} ${if (r.count > 1) r.count else ""}", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/**
 * V6-R6 (#receipts): animated delivery tick. Pending shows a clock, sent a single
 * check, read a double-check with a one-shot spring pulse + color tween — the
 * "read" moment is the payoff and should feel instant and satisfying.
 */
@Composable
private fun MessageStatusTick(
    isPending: Boolean,
    isRead: Boolean,
    isHighlighted: Boolean,
    bubbleContentColor: Color,
) {
    val readPulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isRead) {
        if (isRead) {
            readPulse.snapTo(0.55f)
            readPulse.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isHighlighted -> bubbleContentColor.copy(alpha = 0.8f)
            isRead -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(durationMillis = 200),
        label = "statusTickColor",
    )
    Icon(
        imageVector = when {
            isPending -> Icons.Rounded.Schedule
            isRead -> Icons.Rounded.DoneAll
            else -> Icons.Rounded.Done
        },
        contentDescription = null,
        modifier = Modifier
            .size(14.dp)
            .graphicsLayer {
                scaleX = readPulse.value
                scaleY = readPulse.value
            },
        tint = tint,
    )
}

/**
 * S-1 FIX (reviewwhisper.md): isolates the draft StateFlow collection. Only THIS subtree
 * recomposes per keystroke; the parent Scaffold/column never sees draft emissions.
 */
@Composable
private fun WhisperDraftBoundInputBar(
    draftFlow: StateFlow<String>,
    enabled: Boolean,
    placeholderText: String,
    pulseTrigger: Int,
    isUploadingImage: Boolean,
    modifier: Modifier = Modifier,
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    onImage: () -> Unit,
) {
    val draftText by draftFlow.collectAsStateWithLifecycle()
    MessageInputBar(
        modifier = modifier,
        enabled = enabled,
        draftText = draftText,
        placeholderText = placeholderText,
        pulseTrigger = pulseTrigger,
        onDraftChanged = onDraftChanged,
        onSend = onSend,
        onImage = onImage,
        isUploadingImage = isUploadingImage,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageInputBar(
    enabled: Boolean,
    draftText: String,
    placeholderText: String,
    pulseTrigger: Int,
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    onImage: () -> Unit,
    isUploadingImage: Boolean,
    modifier: Modifier = Modifier,
) {
    val performanceMode = LocalPerformanceMode.current

    // Send-pop bounce animation
    val sendPop = remember { Animatable(1f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger > 0 && !performanceMode) {
            sendPop.snapTo(1.25f)
            sendPop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
        } else {
            sendPop.snapTo(1f)
        }
    }

    val hasText = draftText.isNotBlank()
    val sendButtonScale by animateFloatAsState(
        targetValue = if (hasText) 1f else 0.88f,
        animationSpec = if (performanceMode) snap() else spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "sendScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp),
        // NOTE: no navigationBarsPadding here — the parent Column already applies
        // imePadding, so adding a second inset would double the gap above the navbar.
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Main Pill Container (Attachment + Text input)
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment / photo picker button
                if (isUploadingImage) {
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(
                        onClick = onImage,
                        enabled = enabled,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.cd_Whisper_SendImage),
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Text field
                BasicTextField(
                    value = draftText,
                    onValueChange = onDraftChanged,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    // Low FIX: hardware/IME send action — the keyboard's Send key submits.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (hasText) onSend(draftText) }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draftText.isEmpty()) {
                                Text(
                                    placeholderText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        // 2. Floating Circular Action Button (Send / Mic)
        val sendMessageCd = stringResource(R.string.cd_Whisper_SendMessage)
        Surface(
            onClick = { if (hasText) onSend(draftText) },
            enabled = enabled && hasText,
            shape = CircleShape,
            color = if (hasText && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (hasText && enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            shadowElevation = if (hasText) 3.dp else 0.dp,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = sendPop.value * sendButtonScale
                    scaleY = sendPop.value * sendButtonScale
                }
                // The idle mic-less icon has no CD of its own — expose the action here.
                .semantics { if (!hasText) contentDescription = sendMessageCd }
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = hasText,
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    },
                    label = "sendIconAnim"
                ) { targetHasText ->
                    if (targetHasText) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_Whisper_SendMessage),
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        // No voice feature exists; a mic would be a dead affordance.
                        Icon(
                            Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}



/** Extract date string (YYYY-MM-DD) from ISO timestamp */
fun String.extractDate(): String = if (length >= 10) take(10) else ""

// Low FIX: one shared quick-reaction palette for QuickReactionDialog AND DeleteMessageSheet
// (the lists used to be duplicated literals that could drift apart).
private val WHISPER_QUICK_REACTIONS = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")

// S-4 FIX (reviewwhisper.md): density-stable reply-swipe thresholds. The old raw pixel
// values (45px/80px) equalled ~15dp/27dp on a 3x device and were far too twitchy/lax
// elsewhere; dp keeps the gesture identical across densities.
private val SWIPE_REPLY_TRIGGER = 16.dp
private val SWIPE_REPLY_MAX = 28.dp

/**
 * S-5 FIX (reviewwhisper.md): locale-aware, zone-aware formatters. DateTimeFormatter is
 * immutable and thread-safe, so ONE shared instance per style is used instead of the old
 * per-call ofPattern("HH:mm") allocation; ofLocalizedTime(SHORT) also respects 12h/24h.
 */
private val whisperTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val whisperDateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

fun String.formatWhisperTime(): String {
    return try {
        val instant = Instant.parse(this)
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        dateTime.format(whisperTimeFormatter)
    } catch (e: Exception) {
        // L-10 FIX (reviewwhisper.md): show a raw fallback instead of a blank cell when
        // parsing fails (e.g. legacy rows with non-ISO timestamps).
        if (length >= 16) take(16).replace('T', ' ') else this
    }
}

/** S-5 FIX: localized SHORT date (system zone via the parsed calendar date). */
private fun String.formatWhisperDate(): String {
    return try {
        LocalDate.parse(extractDate()).format(whisperDateFormatter)
    } catch (e: Exception) {
        extractDate() // legacy fallback: raw YYYY-MM-DD slice
    }
}

/**
 * Basic Markdown parser for Bold (**text**) and Italic (*text*).
 *
 * L-8 (documented limitations): intentionally minimal for a chat bubble —
 * no links/underscores/code spans/escapes, and `***bold italic***` closes at
 * the NEAREST terminator rather than balancing across paragraphs. Content is
 * E2EE plaintext authored by a human peer, so under-parsing is safe (it can
 * only render literal asterisks), never inject markup.
 */
private fun String.parseMarkdown(): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < this.length) {
        when {
            // Bold italic: ***text***
            startsWith("***", i) -> {
                val end = indexOf("***", i + 3)
                if (end != -1 && end > i + 3) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    builder.append(substring(i + 3, end))
                    builder.pop()
                    i = end + 3
                } else {
                    builder.append("***")
                    i += 3
                }
            }
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

// decodeBoundedBitmap now lives in WhisperMainScreenComponents.kt (single shared copy).

@Composable
private fun WhisperFullScreenImageViewer(
    imageBytes: ByteArray,
    mimeType: String,
    expiresAtEpochSeconds: Long? = null,
    onDismiss: () -> Unit,
    onShowToast: (String, WhisperToastType) -> Unit
) {
    // V6-R6 (#disappearing): live countdown + AUTO-EXIT. A 1s ticker recomputes the
    // remaining time; when it hits zero the viewer dismisses itself — the image is
    // gone, and lingering on a black shell would be dishonest UI.
    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(expiresAtEpochSeconds) {
        if (expiresAtEpochSeconds == null) return@LaunchedEffect
        while (isActive) {
            delay(1_000)
            nowSeconds = System.currentTimeMillis() / 1000
            if (nowSeconds >= expiresAtEpochSeconds) { onDismiss(); break }
        }
    }
    val remainingSeconds = expiresAtEpochSeconds?.let { (it - nowSeconds).coerceAtLeast(0) }
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    val scope = rememberCoroutineScope()
    val screen = LocalConfiguration.current
    val density = LocalDensity.current

    // S-3 FIX (reviewwhisper.md): saving an E2EE image exports it OUTSIDE the encryption
    // boundary (system gallery/MediaStore). The user must opt into that explicitly — the
    // download button now opens a warning dialog instead of saving silently.
    var showSaveToGalleryConfirm by remember { mutableStateOf(false) }
    // Decode off the main thread — a full-screen decode can take tens of ms and must
    // never run during composition. Configuration bounds are dp; convert to px first
    // because decodeBoundedBitmap treats its bounds as pixels.
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageBytes) {
        val maxWidthPx = with(density) { (screen.screenWidthDp * 2).dp.toPx() }.toInt()
        val maxHeightPx = with(density) { (screen.screenHeightDp * 2).dp.toPx() }.toInt()
        bitmap = withContext(Dispatchers.Default) {
            runCatching { decodeBoundedBitmap(imageBytes, maxWidthPx, maxHeightPx) }.getOrNull()
        }
    }
    // Release the decoded bitmap when the viewer closes or it is replaced.
    // Capture the current bitmap value at composition time — the live `bitmap` var
    // would be read as the *new* bitmap when the old key disposes, recycling the
    // wrong (new) bitmap and leaking the old one.
    val bitmapToRecycle = bitmap
    DisposableEffect(bitmapToRecycle) {
        onDispose { bitmapToRecycle?.recycle() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            // V6-R6 (#disappearing): top-center countdown pill for self-destructing images.
            if (expiresAtEpochSeconds != null && remainingSeconds != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            val bmp = bitmap
            if (bmp != null) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_Whisper_EncryptedImage),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bmp) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                // L-9 FIX (reviewwhisper.md): clamp pan so the image can no
                                // longer be dragged entirely off-screen. translation is
                                // center-origin, so max |offset| is (scale-1)*dimension/2.
                                val maxX = (scale - 1f) * size.width / 2f
                                val maxY = (scale - 1f) * size.height / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.st_Whisper_Error_ReadImage), color = Color.White)
                }
            }

            // Controls overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolzExpressiveIconButton(
                    onClick = { haptic.click(); onDismiss() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_Back))
                }

                ToolzExpressiveIconButton(
                    onClick = {
                        haptic.click()
                        // S-3 FIX: confirm first — the copy lands outside the E2EE boundary.
                        showSaveToGalleryConfirm = true
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.st_Whisper_Save))
                }
            }
        }
    }

    // S-3 FIX: save-to-gallery warning — the image leaves the E2EE boundary, so the
    // user must confirm explicitly.
    if (showSaveToGalleryConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveToGalleryConfirm = false },
            title = { Text(stringResource(R.string.st_Whisper_SaveToGallery_Title)) },
            text = {
                Text(stringResource(R.string.st_Whisper_SaveToGallery_Desc))
            },
            confirmButton = {
                TextButton(onClick = {
                    showSaveToGalleryConfirm = false
                    scope.launch {
                        val saved = saveImageToGallery(context, imageBytes, mimeType)
                        if (saved) {
                            onShowToast(context.getString(R.string.st_Whisper_ImageSaved), WhisperToastType.SUCCESS)
                        } else {
                            onShowToast(context.getString(R.string.st_Whisper_Error_Generic), WhisperToastType.ERROR)
                        }
                    }
                }) {
                    Text(stringResource(R.string.st_Whisper_Save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveToGalleryConfirm = false }) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
            }
        )
    }
}

private suspend fun saveImageToGallery(context: Context, bytes: ByteArray, mimeType: String): Boolean =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val filename = "Whisper_${System.currentTimeMillis()}.${if (mimeType == "image/png") "png" else "jpg"}"
            // RELATIVE_PATH / IS_PENDING only exist on API 29+; older devices get a plain
            // insert (MediaStore auto-assigns the Pictures location) without the pending dance.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Whisper")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext false

                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext false
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                } catch (e: Exception) {
                    // A failed write must not leave a phantom pending media item behind.
                    runCatching { resolver.delete(uri, null, null) }
                    return@withContext false
                }
            } else {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext false
                val wrote = resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                if (!wrote) {
                    runCatching { resolver.delete(uri, null, null) }
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
