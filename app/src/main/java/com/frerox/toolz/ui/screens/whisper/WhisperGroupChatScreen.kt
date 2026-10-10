/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperGroupChatMessage
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.readBoundedGroupImageBytes
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * Phase-2 groups: one group conversation. Same expressive theme as 1:1
 * chats; every open re-verifies the event log, every send seals per member.
 * Parity slice: images, paging, date dividers, typing, long-press actions,
 * delete for me/everyone, seen-by info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperGroupChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInfo: (String) -> Unit,
    viewModel: WhisperGroupChatViewModel = hiltViewModel(),
) {
    if (!groupsEnabled()) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var draft by rememberSaveable(viewModel.groupId) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var showPollCreate by remember { mutableStateOf(false) }
    // Vote rows are tally data (rendered in poll cards below); they carry no
    // readable body, so they stay out of the line list.
    val visible = remember(state.messages) { state.messages.filter { it.vote == null } }

    // Full-history search results (decrypted page-by-page in the VM; the
    // timeline filter only ever saw the loaded page).
    var searchResults by remember { mutableStateOf<List<WhisperGroupChatMessage>?>(null) }
    var searchingNow by remember { mutableStateOf(false) }
    LaunchedEffect(searching, searchQuery) {
        if (!searching || searchQuery.trim().length < 2) {
            searchResults = null
            searchingNow = false
        } else {
            searchingNow = true
            kotlinx.coroutines.delay(400)
            // Query may have changed during debounce; re-check before running.
            val q = searchQuery
            if (searching && searchQuery == q) {
                searchResults = viewModel.searchAll(q)
                searchingNow = false
            }
        }
    }

    // Open at the first unread (WhatsApp behavior); live arrivals still pin
    // to the bottom. Paging prepends never yank the viewport.
    var scrolledInitial by remember(viewModel.groupId) { mutableStateOf(false) }
    LaunchedEffect(state.messages.size, state.systemEvents.size) {
        if (!scrolledInitial && (visible.isNotEmpty() || state.systemEvents.isNotEmpty())) {
            scrolledInitial = true
            val feed0 = buildFeed(visible, state.systemEvents, state.firstUnreadId, state.unreadCount)
            val anchor = feed0.indexOfFirst {
                it is FeedItem.UnreadMark
            }.takeIf { it >= 0 } ?: (feed0.size - 1)
            if (anchor >= 0) listState.scrollToItem(anchor)
        }
    }
    LaunchedEffect(visible.lastOrNull()?.id) {
        if (scrolledInitial && visible.isNotEmpty()) {
            val lastIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val total = listState.layoutInfo.totalItemsCount
            // Only auto-pin when already near the bottom (reading history
            // must not yank on every arrival).
            if (lastIdx == null || total == 0 || lastIdx >= total - 3) {
                listState.animateScrollToItem(visible.size - 1)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { readBoundedGroupImageBytes(it) }
            if (bytes != null) viewModel.sendImage(bytes, mime)
        }.onFailure { e ->
            // Bounded-read overflow surfaces here (no VM round-trip needed).
            android.widget.Toast.makeText(context, e.message ?: "", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var menuMsg by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var infoMsg by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var confirmWipe by remember { mutableStateOf<WhisperGroupChatMessage?>(null) }
    var viewerBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun sendWithContext() {
        val target = replyTarget
        val ref = target?.let {
            com.frerox.toolz.data.whisper.GroupReplyRef(
                id = it.clientId,
                sender = it.senderName,
                text = it.body.ifBlank { "Photo" }.take(140),
            )
        }
        viewModel.send(draft, reply = ref, mentionIds = resolveMentionIds(draft, state.memberNames))
        draft = ""
        replyTarget = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.membership?.name?.ifBlank { "Group" } ?: "Group",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            "${state.membership?.members?.size ?: 0} ${stringResource(R.string.st_Whisper_Groups_Members)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) searchQuery = "" }) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                    IconButton(onClick = { showPollCreate = true }) {
                        Icon(Icons.Rounded.BarChart, contentDescription = null)
                    }
                    IconButton(onClick = { viewModel.toggleMute() }) {
                        Icon(
                            if (state.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                    }
                    IconButton(onClick = { onNavigateToInfo(viewModel.groupId) }) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        },
        bottomBar = {
            Column {
                replyTarget?.let { target ->
                    ReplyPreviewBar(
                        sender = target.senderName,
                        text = target.body.ifBlank { stringResource(R.string.st_Whisper_Groups_Photo) },
                        onCancel = { replyTarget = null },
                    )
                }
                MentionSuggestions(
                    draft = draft,
                    memberNames = state.memberNames,
                    myId = viewModel.myUserId,
                    onPick = { name ->
                        val at = draft.lastIndexOf("@")
                        draft = if (at >= 0) draft.substring(0, at) + "@$name " else "$draft@$name "
                    },
                )
                if (state.typingNames.isNotEmpty()) {
                    Text(
                        typingLine(state.typingNames),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            if (it.isNotBlank()) viewModel.onTyping()
                        },
                        label = { Text(stringResource(R.string.st_Whisper_Groups_MessageHint)) },
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (draft.isNotBlank()) {
                                sendWithContext()
                            }
                        }),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank()) {
                                sendWithContext()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (searching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.st_Whisper_Groups_SearchMessagesHint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            state.error?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (state.degraded) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    GroupDegradedBanner()
                }
            }
            if (state.isUploadingImage) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            if (state.isLoading && visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (searching && searchQuery.trim().length >= 2) {
                SearchResultsList(
                    query = searchQuery,
                    loading = searchingNow,
                    results = searchResults,
                    onPick = { msg ->
                        searching = false
                        searchQuery = ""
                        searchResults = null
                        val idx = buildFeed(visible, state.systemEvents, state.firstUnreadId, state.unreadCount)
                            .indexOfFirst { it is FeedItem.Msg && it.msg.id == msg.id }
                        if (idx >= 0) scrollScope.launch { listState.animateScrollToItem(idx) }
                        else android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.st_Whisper_Groups_LoadOlder),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            } else {
                val feed = remember(visible, state.systemEvents, state.firstUnreadId, state.unreadCount) {
                    buildFeed(visible, state.systemEvents, state.firstUnreadId, state.unreadCount)
                }
                val atBottom = remember {
                    androidx.compose.runtime.derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index
                        last == null || info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
                    }
                }
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.hasMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text(stringResource(R.string.st_Whisper_Groups_LoadOlder))
                                    }
                                }
                            }
                        }
                    }
                    items(feed.size, key = { idx ->
                        when (val it = feed[idx]) {
                            is FeedItem.Day -> "day_${it.label}"
                            is FeedItem.Msg -> it.msg.id
                            is FeedItem.Sys -> "sys_${it.seq}"
                            is FeedItem.UnreadMark -> "unread_mark"
                        }
                    }) { idx ->
                        when (val item = feed[idx]) {
                            is FeedItem.Day -> DayDivider(label = groupDayLabel(item.label))
                            is FeedItem.Sys -> SysBubble(text = item.text)
                            is FeedItem.UnreadMark -> UnreadDivider(count = item.count)
                            is FeedItem.Msg -> {
                                val msg = item.msg
                                GroupBubble(
                                    msg = msg,
                                    imageBytes = state.images[msg.id],
                                    seenCount = state.receipts[msg.clientId]?.size ?: 0,
                                    pollTally = msg.poll?.let { state.pollTallies[it.id] },
                                    memberNames = state.memberNames,
                                    myId = viewModel.myUserId,
                                    onRequestImage = { viewModel.loadImage(msg) },
                                    onImageClick = { bytes -> viewerBytes = bytes },
                                    onLongPress = { menuMsg = msg },
                                    onReply = { replyTarget = msg },
                                    onQuoteClick = { qid ->
                                        val target = feed.indexOfFirst {
                                            it is FeedItem.Msg && it.msg.clientId == qid
                                        }
                                        if (target >= 0) scrollScope.launch { listState.animateScrollToItem(target) }
                                    },
                                    onVote = { opt ->
                                        msg.poll?.let { viewModel.vote(it.id, opt) }
                                    },
                                )
                            }
                        }
                    }
                    }
                    // Jump-to-latest: appears with pending unread once the user
                    // scrolls up (WhatsApp-style). Tapping pins to the bottom.
                    if (state.unreadCount > 0 && !atBottom.value) {
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            onClick = {
                                scrollScope.launch {
                                    listState.animateScrollToItem(feed.size - 1)
                                }
                            },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 12.dp),
                        ) {
                            Text(
                                stringResource(R.string.st_Whisper_Groups_NewMessages, state.unreadCount),
                            )
                        }
                    }
                }
            }
            if (state.isSending) {
                Text(
                    "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }

    menuMsg?.let { msg ->
        MessageActionDialog(
            msg = msg,
            onDismiss = { menuMsg = null },
            onReply = { replyTarget = msg; menuMsg = null },
            onCopy = {
                clipboard.setText(AnnotatedString(msg.body))
                android.widget.Toast.makeText(context, context.getString(R.string.st_Whisper_Groups_Copied), android.widget.Toast.LENGTH_SHORT).show()
                menuMsg = null
            },
            onDeleteForMe = { viewModel.deleteForMe(msg); menuMsg = null },
            onDeleteForEveryone = { confirmWipe = msg; menuMsg = null },
            onInfo = { infoMsg = msg; menuMsg = null },
        )
    }

    confirmWipe?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirmWipe = null },
            title = { Text(stringResource(R.string.st_Whisper_Groups_DeleteForAll), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.st_Whisper_Groups_DeleteForAllConfirm)) },
            confirmButton = {
                ToolzExpressiveButton(onClick = { viewModel.deleteForEveryone(msg); confirmWipe = null }) {
                    Text(stringResource(R.string.st_Whisper_Groups_DeleteForAll), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = null }) { Text(stringResource(R.string.st_Whisper_Cancel)) }
            },
        )
    }

    infoMsg?.let { msg ->
        val seenIds = state.receipts[msg.clientId].orEmpty().filter { it != msg.senderId }
        val names = state.memberNames
        MessageInfoDialog(
            msg = msg,
            seenNames = seenIds.map { names[it] ?: "Member" },
            onDismiss = { infoMsg = null },
        )
    }

    viewerBytes?.let { bytes ->
        GroupImageViewer(bytes = bytes, onDismiss = { viewerBytes = null })
    }

    if (showPollCreate) {
        CreatePollDialog(
            onDismiss = { showPollCreate = false },
            onSend = { q, opts -> showPollCreate = false; viewModel.sendPoll(q, opts) },
        )
    }

}

@Composable
private fun typingLine(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> stringResource(R.string.st_Whisper_Groups_TypingOne, names[0])
    2 -> stringResource(R.string.st_Whisper_Groups_TypingTwo, names[0], names[1])
    else -> stringResource(R.string.st_Whisper_Groups_TypingMany)
}

/** Flat feed: exact indices (dividers included) so quote-tap can scroll precisely. */
private sealed interface FeedItem {
    data class Day(val label: String) : FeedItem
    data class Msg(val msg: WhisperGroupChatMessage) : FeedItem
    data class Sys(val seq: Long, val text: String) : FeedItem
    data class UnreadMark(val count: Int) : FeedItem
}

private fun msgTimeMs(iso: String): Long = runCatching {
    java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}.getOrDefault(Long.MIN_VALUE)

private fun dayOf(ms: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
}.getOrDefault("")

private fun buildFeed(
    visible: List<WhisperGroupChatMessage>,
    systems: List<WhisperGroupChatViewModel.SysLine> = emptyList(),
    firstUnreadId: String? = null,
    unreadCount: Int = 0,
): List<FeedItem> {
    data class Row(val ms: Long, val order: Int, val item: FeedItem)
    val rows = mutableListOf<Row>()
    visible.forEach { msg ->
        rows.add(Row(msgTimeMs(msg.createdAt), 1, FeedItem.Msg(msg)))
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
    return out
}

/** Day bucket key (yyyy-MM-dd) for dividers; unparseable rows share the "" bucket. */
private fun groupDayKey(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).toLocalDate().toString()
}.getOrDefault("")

@Composable
private fun groupDayLabel(day: String): String {
    val today = remember { java.time.LocalDate.now().toString() }
    val yesterday = remember { java.time.LocalDate.now().minusDays(1).toString() }
    return when (day) {
        "" -> ""
        today -> stringResource(R.string.st_Whisper_Groups_Today)
        yesterday -> stringResource(R.string.st_Whisper_Groups_Yesterday)
        else -> runCatching {
            val d = java.time.LocalDate.parse(day)
            "${d.dayOfMonth} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${d.year}"
        }.getOrDefault("")
    }
}

@Composable
private fun SysBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun UnreadDivider(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                stringResource(R.string.st_Whisper_Groups_NewMessagesDivider, count),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SearchResultsList(
    query: String,
    loading: Boolean,
    results: List<WhisperGroupChatMessage>?,
    onPick: (WhisperGroupChatMessage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (loading && results == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        val list = results.orEmpty()
        if (!loading && results != null) {
            Text(
                if (list.isEmpty()) "" else "${list.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                textAlign = TextAlign.End,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(list, key = { it.id }) { msg ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(msg) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        msg.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        msg.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatGroupTime(msg.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDivider(label: String) {
    if (label.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun GroupBubble(
    msg: WhisperGroupChatMessage,
    imageBytes: ByteArray?,
    seenCount: Int,
    pollTally: WhisperGroupChatViewModel.PollTally?,
    memberNames: Map<String, String>,
    myId: String,
    onRequestImage: () -> Unit,
    onImageClick: (ByteArray) -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onVote: (Int) -> Unit,
) {
    val bitmap = remember(imageBytes) {
        imageBytes?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    if (msg.image != null) {
        LaunchedEffect(msg.id) { onRequestImage() }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
    ) {
        if (!msg.mine) {
            Text(
                msg.senderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
        Box(
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress).clip(
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (msg.mine) 18.dp else 4.dp,
                    bottomEnd = if (msg.mine) 4.dp else 18.dp,
                ),
            ).background(
                if (msg.mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.reply != null) {
                    QuoteBlock(quote = msg.reply, onClick = { onQuoteClick(msg.reply.id) })
                }
                if (msg.poll != null) {
                    PollCard(poll = msg.poll, tally = pollTally, enabled = true, onVote = onVote)
                }
                if (msg.image != null) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { imageBytes?.let(onImageClick) },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier.heightIn(min = 120.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
                if (msg.body.isNotBlank()) {
                    val mentionedMe = remember(msg.mentions, myId) { myId.isNotBlank() && myId in msg.mentions }
                    if (mentionedMe) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        ) {
                            MentionText(
                                body = msg.body,
                                memberNames = memberNames,
                                mine = msg.mine,
                                baseColor = if (msg.mine) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        MentionText(
                            body = msg.body,
                            memberNames = memberNames,
                            mine = msg.mine,
                            baseColor = if (msg.mine) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (msg.mine && seenCount > 0) {
                        Text(
                            "✓✓ $seenCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        formatGroupTime(msg.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (msg.mine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionDialog(
    msg: WhisperGroupChatMessage,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onInfo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                msg.body.ifBlank { stringResource(R.string.st_Whisper_Groups_Photo) }.take(80),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReply, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_Whisper_Groups_Reply), modifier = Modifier.fillMaxWidth())
                }
                if (msg.body.isNotBlank()) {
                    TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.st_Whisper_Groups_Copy), modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = onDeleteForMe, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_Whisper_Groups_DeleteForMe), modifier = Modifier.fillMaxWidth())
                }
                if (msg.mine) {
                    TextButton(onClick = onDeleteForEveryone, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.st_Whisper_Groups_DeleteForAll),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onInfo, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.st_Whisper_Groups_MessageInfo), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}

@Composable
private fun MessageInfoDialog(
    msg: WhisperGroupChatMessage,
    seenNames: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_Whisper_Groups_MessageInfo), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (seenNames.isEmpty()) stringResource(R.string.st_Whisper_Groups_SeenByNone)
                    else stringResource(R.string.st_Whisper_Groups_SeenBy, seenNames.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                seenNames.take(12).forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}

private fun formatGroupTime(iso: String): String = runCatching {
    val instant = java.time.OffsetDateTime.parse(iso)
    "%02d:%02d".format(instant.hour, instant.minute)
}.getOrDefault("")

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
private fun MentionSuggestions(
    draft: String,
    memberNames: Map<String, String>,
    myId: String,
    onPick: (String) -> Unit,
) {
    val q = remember(draft) { mentionQuery(draft) } ?: return
    val hits = remember(memberNames, q) {
        memberNames.filter { (id, name) ->
            id != myId && name.isNotBlank() && (q.isBlank() || name.lowercase().startsWith(q.lowercase()))
        }.values.take(4)
    }
    if (hits.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        hits.forEach { name ->
            Text(
                "@$name",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .clickable { onPick(name) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReplyPreviewBar(sender: String, text: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(3.dp).heightIn(min = 28.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.st_Whisper_Groups_Reply) + " · $sender",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QuoteBlock(quote: com.frerox.toolz.data.whisper.GroupReplyRef, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(3.dp).heightIn(min = 28.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                quote.sender,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                quote.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Body text with @Name tokens tinted (only tokens matching members). */
@Composable
private fun MentionText(
    body: String,
    memberNames: Map<String, String>,
    mine: Boolean,
    baseColor: androidx.compose.ui.graphics.Color,
) {
    val annotated = remember(body, memberNames) {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(body)
        if ("@" in body) {
            memberNames.values.filter { it.isNotBlank() }
                .sortedByDescending { it.length }
                .forEach { name ->
                    var from = 0
                    while (true) {
                        val idx = body.indexOf("@$name", from)
                        if (idx < 0) break
                        builder.addStyle(
                            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold),
                            idx, idx + name.length + 1,
                        )
                        from = idx + name.length + 1
                    }
                }
        }
        builder.toAnnotatedString()
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = baseColor,
    )
}

@Composable
private fun PollCard(
    poll: com.frerox.toolz.data.whisper.GroupPoll,
    tally: WhisperGroupChatViewModel.PollTally?,
    enabled: Boolean,
    onVote: (Int) -> Unit,
) {
    val total = tally?.counts?.values?.sum() ?: 0
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            poll.q,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        poll.opts.forEachIndexed { idx, opt ->
            val votes = tally?.counts?.get(idx) ?: 0
            val frac = if (total == 0) 0f else votes.toFloat() / total
            val selected = tally?.myVote == idx
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    )
                    .clickable(enabled = enabled) { onVote(idx) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier.matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f * frac + 0.04f)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        opt,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (total == 0) "" else "$votes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (total > 0) {
            Text(
                "$total vote${if (total == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatePollDialog(onDismiss: () -> Unit, onSend: (String, List<String>) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { androidx.compose.runtime.mutableStateListOf("", "") }
    val canSend = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_Whisper_Groups_CreatePoll), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { if (it.length <= 140) question = it },
                    label = { Text(stringResource(R.string.st_Whisper_Groups_PollQuestionHint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { idx, opt ->
                    OutlinedTextField(
                        value = opt,
                        onValueChange = { if (it.length <= 60) options[idx] = it },
                        label = { Text(stringResource(R.string.st_Whisper_Groups_PollOptionHint, idx + 1)) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (options.size < 6) {
                    TextButton(onClick = { options.add("") }) {
                        Text(stringResource(R.string.st_Whisper_Groups_PollAddOption))
                    }
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = { onSend(question.trim(), options.map { it.trim() }.filter { it.isNotBlank() }) },
                enabled = canSend,
            ) {
                Text(stringResource(R.string.st_Whisper_Groups_PollSend), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}
