/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.GroupReplyRef
import com.frerox.toolz.data.whisper.WhisperGroupCapability
import com.frerox.toolz.data.whisper.WhisperGroupChatMessage
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.readBoundedGroupImageBytes
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One group conversation. Newest-first reverse layout (the keyboard and
 * paging never fight the viewport), clustered bubbles with sender avatars,
 * swipe-to-reply, optimistic sends, reactions, polls, in-timeline search
 * with a flash on the target, and a composer that locks when only admins
 * may send. Every open re-verifies the event log; every send seals per member.
 */
@Composable
fun WhisperGroupChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInfo: (String) -> Unit,
    viewModel: WhisperGroupChatViewModel = hiltViewModel(),
) {
    if (!groupsEnabled()) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val myId = viewModel.myUserId
    var draft by rememberSaveable(viewModel.groupId) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberToolzHapticFeedback()
    val snackbar = remember { SnackbarHostState() }

    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var flashId by remember { mutableStateOf<String?>(null) }
    var menuMsg by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var infoMsg by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var confirmWipe by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var viewerBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var showPoll by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }

    val membership = state.membership
    // Authorization is the reconstructed POLICY state, not a role check: the
    // owner may delegate send (or poll rights) to members through a signed
    // POLICY event, and a member without send locks exactly as before.
    val canSend = membership?.allows(myId, WhisperGroupCapability.SEND_MESSAGES) == true
    val canCreatePoll = membership?.allows(myId, WhisperGroupCapability.CREATE_POLLS) == true
    val canClosePoll = membership?.allows(myId, WhisperGroupCapability.CLOSE_POLLS) == true
    val locked = membership != null && !canSend

    // Vote rows are tally data (rendered in poll cards); they carry no
    // readable body, so they stay out of the line list.
    val visible = remember(state.messages) { state.messages.filter { it.vote == null && it.pollClose == null } }
    val feedAsc = remember(visible, state.systemEvents, state.firstUnreadId, state.unreadCount, state.pending) {
        buildFeed(visible, state.systemEvents, state.firstUnreadId, state.unreadCount, state.pending)
    }
    // Index 0 = newest (reverseLayout draws it at the bottom).
    val feed = remember(feedAsc) { feedAsc.asReversed() }

    BackHandler(enabled = searching) {
        searching = false
        searchQuery = ""
    }

    // ── Full-history search (decrypted page-by-page in the VM, debounced) ──
    var searchResults by remember { mutableStateOf<List<WhisperGroupChatMessage>?>(null) }
    var searchingNow by remember { mutableStateOf(false) }
    LaunchedEffect(searching, searchQuery) {
        if (!searching || searchQuery.trim().length < 2) {
            searchResults = null
            searchingNow = false
        } else {
            searchingNow = true
            delay(400)
            val q = searchQuery
            if (searching && searchQuery == q) {
                searchResults = viewModel.searchAll(q)
                searchingNow = false
            }
        }
    }

    // ── Initial position: first unread (divider near the top), else bottom ──
    var positioned by remember(viewModel.groupId) { mutableStateOf(false) }
    LaunchedEffect(feed.size, state.isLoading) {
        if (!positioned && feed.isNotEmpty() && !state.isLoading) {
            positioned = true
            val mark = feed.indexOfFirst { it is FeedItem.UnreadMark }
            if (mark > 0) {
                listState.scrollToItem(mark)
                listState.scrollBy(-(listState.layoutInfo.viewportSize.height * 0.72f))
            }
        }
    }

    // ── Follow new arrivals only when already near the bottom (or mine) ──
    val newestKey = feed.firstOrNull()?.feedKey()
    LaunchedEffect(newestKey) {
        if (!positioned) return@LaunchedEffect
        val newest = feed.firstOrNull()
        val mineOrPending = newest is FeedItem.Pending || (newest is FeedItem.Msg && newest.msg.mine)
        if (mineOrPending || listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    // ── Auto-page older history as the top edge approaches ──
    LaunchedEffect(listState, feed.size, state.hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (state.hasMore && !state.loadingMore && last >= feed.size - 6) viewModel.loadMore()
            }
    }

    LaunchedEffect(flashId) {
        if (flashId != null) {
            delay(1_400)
            flashId = null
        }
    }

    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    val belowCount by remember(feed) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex.coerceAtMost(feed.size)
            (0 until first).count { i -> (feed[i] as? FeedItem.Msg)?.msg?.mine == false }
        }
    }
    val dayLabels = remember(feed) {
        val out = arrayOfNulls<String>(feed.size)
        var cur = ""
        for (i in feed.indices.reversed()) {
            val it = feed[i]
            if (it is FeedItem.Day) cur = it.label
            out[i] = cur
        }
        out
    }
    val topDay by remember(dayLabels) {
        derivedStateOf {
            val idx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            dayLabels.getOrNull(idx).orEmpty()
        }
    }
    val topDayText = groupDayLabel(topDay)

    fun jumpTo(match: (WhisperGroupChatMessage) -> Boolean) {
        val idx = feed.indexOfFirst { it is FeedItem.Msg && match(it.msg) }
        if (idx >= 0) {
            val id = (feed[idx] as FeedItem.Msg).msg.id
            scope.launch {
                listState.centerOn(idx)
                flashId = id
            }
        } else {
            scope.launch { snackbar.showGroupNotice(context.getString(R.string.st_Whisper_Groups_LoadOlder)) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { readBoundedGroupImageBytes(it) }
            if (bytes != null) pendingImage = bytes to mime
        }.onFailure { e ->
            scope.launch { snackbar.showGroupNotice(e.message.orEmpty()) }
        }
    }

    fun sendWithContext() {
        val target = replyTarget
        val ref = target?.let {
            GroupReplyRef(
                id = it.clientId,
                sender = it.senderName,
                text = (it.poll?.q ?: it.body.ifBlank { "Photo" }).take(140),
            )
        }
        viewModel.send(draft, reply = ref, mentionIds = resolveMentionIds(draft, state.memberNames))
        draft = ""
        replyTarget = null
    }

    val memberCount = membership?.members?.size
    val memberLabel = memberCount?.let {
        "$it " + stringResource(
            if (it == 1) R.string.st_Whisper_Groups_Member else R.string.st_Whisper_Groups_Members,
        )
    }
    val typing = if (state.typingNames.isNotEmpty()) typingLine(state.typingNames) else null

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        snackbarHost = { GroupSnackbarHost(state = snackbar) },
        topBar = {
            GroupChatTopBar(
                groupId = viewModel.groupId,
                title = membership?.name?.ifBlank { "Group" } ?: "Group",
                subtitle = memberLabel,
                typing = typing,
                searching = searching,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchToggle = {
                    searching = !searching
                    if (!searching) searchQuery = ""
                },
                muted = state.isMuted,
                onToggleMute = { viewModel.toggleMute() },
                onRefresh = { viewModel.load() },
                onBack = onNavigateBack,
                onOpenInfo = { onNavigateToInfo(viewModel.groupId) },
            )
        },
        bottomBar = {
            if (membership != null || state.isLoading) {
                GroupComposer(
                    draft = draft,
                    onDraftChange = {
                        draft = it
                        if (it.isNotBlank()) viewModel.onTyping()
                    },
                    onSend = { if (draft.isNotBlank()) sendWithContext() },
                    onAttach = { showAttach = true },
                    locked = locked,
                    replyTarget = replyTarget,
                    onCancelReply = { replyTarget = null },
                    typingNames = state.typingNames,
                    uploading = state.isUploadingImage,
                    memberNames = state.memberNames,
                    myId = myId,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GroupUiBanner(
                message = state.error.orEmpty(),
                visible = state.error != null,
                isError = true,
                actionLabel = stringResource(R.string.st_Whisper_Groups_Retry),
                onAction = { viewModel.load() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (state.degraded) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    GroupDegradedBanner()
                }
            }
            when {
                state.isLoading && feed.isEmpty() -> {
                    GroupUiCenterLoading(modifier = Modifier.fillMaxSize())
                }
                !state.isLoading && membership == null -> {
                    GroupUiDeadEnd(
                        message = state.error ?: stringResource(R.string.st_Whisper_Groups_ErrNoHistory),
                        onBack = onNavigateBack,
                        onRetry = { viewModel.load() },
                    )
                }
                searching && searchQuery.trim().length >= 2 -> {
                    GroupSearchResults(
                        query = searchQuery,
                        loading = searchingNow,
                        results = searchResults,
                        onPick = { msg ->
                            searching = false
                            searchQuery = ""
                            searchResults = null
                            jumpTo { it.id == msg.id }
                        },
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize().groupScrollFade(listState, reverse = true),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(feed.size, key = { idx -> feed[idx].feedKey() }) { idx ->
                                when (val item = feed[idx]) {
                                    is FeedItem.Day -> DayChip(label = groupDayLabel(item.label))
                                    is FeedItem.Sys -> SysChip(text = item.text)
                                    is FeedItem.UnreadMark -> UnreadDivider(count = item.count)
                                    is FeedItem.Pending -> PendingBubble(
                                        pending = item.p,
                                        onRetry = { viewModel.retryPending(item.p.localId) },
                                        onDiscard = { viewModel.discardPending(item.p.localId) },
                                    )
                                    is FeedItem.Msg -> {
                                        val msg = item.msg
                                        GroupMessageItem(
                                            item = item,
                                            imageBytes = state.images[msg.id],
                                            imageExpired = msg.id in state.expiredImages,
                                            seenCount = state.receipts[msg.clientId]?.count { it != msg.senderId } ?: 0,
                                            pollTally = msg.poll?.let { state.pollTallies[it.id] },
                                            pollClosed = msg.poll?.let { it.id in state.closedPolls } == true,
                                            pollCanClose = canClosePoll &&
                                                msg.poll?.let { it.id !in state.closedPolls } == true,
                                            reactions = state.reactions[msg.clientId].orEmpty(),
                                            myId = myId,
                                            memberNames = state.memberNames,
                                            highlighted = flashId == msg.id,
                                            onRequestImage = { viewModel.loadImage(msg) },
                                            onImageClick = { bytes -> viewerBytes = bytes },
                                            onLongPress = { haptic.longClick(); menuMsg = msg },
                                            onReply = { replyTarget = msg },
                                            onQuoteClick = { qid -> jumpTo { it.clientId == qid } },
                                            onVote = { opt -> msg.poll?.let { viewModel.vote(it.id, opt) } },
                                            onClosePoll = { msg.poll?.let { viewModel.closePoll(it.id) } },
                                            onReact = { emoji -> viewModel.toggleReaction(msg, emoji) },
                                        )
                                    }
                                }
                            }
                            // Visually the top of the list: older history.
                            if (state.hasMore) {
                                item(key = "older") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (state.loadingMore) {
                                            GroupUiInlineLoading()
                                        } else {
                                            TextButton(onClick = { viewModel.loadMore() }) {
                                                Text(stringResource(R.string.st_Whisper_Groups_LoadOlder))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        StickyDayPill(
                            visible = listState.isScrollInProgress,
                            label = topDayText,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        )
                        JumpToLatest(
                            visible = !atBottom,
                            newCount = belowCount,
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        }
    }

    // ── Sheets & dialogs ─────────────────────────────────────────────────────
    menuMsg?.let { msg ->
        MessageActionsSheet(
            msg = msg,
            reactions = state.reactions[msg.clientId].orEmpty(),
            myId = myId,
            onDismiss = { menuMsg = null },
            onReact = { emoji -> viewModel.toggleReaction(msg, emoji) },
            onReply = { replyTarget = msg },
            onCopy = {
                clipboard.setText(AnnotatedString(msg.body))
                scope.launch { snackbar.showGroupNotice(context.getString(R.string.st_Whisper_Groups_Copied)) }
            },
            onInfo = { infoMsg = msg },
            onDeleteForMe = { viewModel.deleteForMe(msg) },
            onDeleteForEveryone = { confirmWipe = msg },
        )
    }

    confirmWipe?.let { msg ->
        GroupConfirmDialog(
            title = stringResource(R.string.st_Whisper_Groups_DeleteForAll),
            text = stringResource(R.string.st_Whisper_Groups_DeleteForAllConfirm),
            confirmLabel = stringResource(R.string.st_Whisper_Groups_DeleteForAll),
            destructive = true,
            onConfirm = { viewModel.deleteForEveryone(msg); confirmWipe = null },
            onDismiss = { confirmWipe = null },
        )
    }

    infoMsg?.let { msg ->
        val names = state.memberNames
        MessageInfoSheet(
            seen = state.receipts[msg.clientId].orEmpty()
                .filter { it != msg.senderId }
                .map { id -> id to (names[id] ?: "Member") },
            onDismiss = { infoMsg = null },
        )
    }

    viewerBytes?.let { bytes ->
        GroupImageViewer(
            bytes = bytes,
            onDismiss = { viewerBytes = null },
            onMessage = { msg -> scope.launch { snackbar.showGroupNotice(msg) } },
        )
    }

    if (showAttach) {
        AttachSheet(
            showPhoto = canSend,
            showPoll = canCreatePoll,
            onPhoto = { picker.launch("image/*") },
            onPoll = { showPoll = true },
            onDismiss = { showAttach = false },
        )
    }

    if (showPoll) {
        CreatePollSheet(
            onDismiss = { showPoll = false },
            onSend = { q, opts -> viewModel.sendPoll(q, opts) },
        )
    }

    pendingImage?.let { (bytes, mime) ->
        ImageSendSheet(
            bytes = bytes,
            onDismiss = { pendingImage = null },
            onSend = { expiry -> viewModel.sendImage(bytes, mime, expiry) },
        )
    }
}

/** Centers a message in the viewport (reverse layout: the item lands at the bottom edge first). */
private suspend fun LazyListState.centerOn(index: Int) {
    animateScrollToItem(index)
    animateScrollBy(-(layoutInfo.viewportSize.height * 0.4f))
}

// ─────────────────────────────────────────────────────────────────────────────
// Feed construction
// ─────────────────────────────────────────────────────────────────────────────

private fun msgTimeMs(iso: String): Long = runCatching {
    java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}.getOrDefault(Long.MIN_VALUE)

/** Local-date key (yyyy-MM-dd) for day dividers. */
private fun dayOf(ms: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
}.getOrDefault("")

/**
 * Oldest-first feed: day dividers, verified activity lines, the unread
 * marker, bubbles with cluster flags (first/last of a same-sender run), and
 * optimistic rows last. The screen reverses it for display.
 */
internal fun buildFeed(
    visible: List<WhisperGroupChatMessage>,
    systems: List<WhisperGroupChatViewModel.SysLine> = emptyList(),
    firstUnreadId: String? = null,
    unreadCount: Int = 0,
    pending: List<WhisperGroupChatViewModel.PendingMessage> = emptyList(),
): List<FeedItem> {
    data class Row(val ms: Long, val order: Int, val item: FeedItem)
    val rows = mutableListOf<Row>()
    visible.forEach { msg ->
        rows.add(Row(msgTimeMs(msg.createdAt), 1, FeedItem.Msg(msg, first = true, last = true)))
    }
    systems.forEach { sys ->
        // System lines without a timestamp (0) sort before everything.
        rows.add(Row(if (sys.atMs > 0) sys.atMs else Long.MIN_VALUE, 0, FeedItem.Sys(sys.seq, sys.text)))
    }
    rows.sortWith(compareBy<Row> { it.ms }.thenBy { it.order })

    val out = mutableListOf<FeedItem>()
    var lastDay: String? = null
    var unreadInserted = firstUnreadId == null
    rows.forEach { row ->
        val day = dayOf(row.ms)
        if (day != lastDay && day.isNotBlank()) {
            lastDay = day
            out.add(FeedItem.Day(day))
        }
        val item = row.item
        if (!unreadInserted && item is FeedItem.Msg && item.msg.id == firstUnreadId) {
            out.add(FeedItem.UnreadMark(unreadCount))
            unreadInserted = true
        }
        out.add(item)
    }

    // Cluster flags: a run is same-sender bubbles with nothing in between.
    for (i in out.indices) {
        val cur = out[i] as? FeedItem.Msg ?: continue
        val prev = out.getOrNull(i - 1) as? FeedItem.Msg
        val next = out.getOrNull(i + 1) as? FeedItem.Msg
        val first = prev == null || prev.msg.senderId != cur.msg.senderId
        val last = (next == null && !(cur.msg.mine && pending.isNotEmpty())) ||
            (next != null && next.msg.senderId != cur.msg.senderId)
        out[i] = cur.copy(first = first, last = last)
    }
    pending.forEach { out.add(FeedItem.Pending(it)) }
    return out
}
