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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
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
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Vote rows are tally data (rendered in poll cards by the social slice);
    // they carry no readable body, so they stay out of the line list.
    val visible = remember(state.messages) { state.messages.filter { it.vote == null } }

    // Auto-scroll only when the NEWEST line changes (paging prepends must not
    // yank the viewport to the bottom).
    LaunchedEffect(visible.lastOrNull()?.id) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.size - 1)
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
                                viewModel.send(draft)
                                draft = ""
                            }
                        }),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank()) {
                                viewModel.send(draft)
                                draft = ""
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
            } else {
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
                    var lastDay: String? = null
                    visible.forEach { msg ->
                        val day = groupDayKey(msg.createdAt)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "day_$day") {
                                DayDivider(label = groupDayLabel(day))
                            }
                        }
                        item(key = msg.id) {
                            GroupBubble(
                                msg = msg,
                                imageBytes = state.images[msg.id],
                                seenCount = state.receipts[msg.clientId]?.size ?: 0,
                                onRequestImage = { viewModel.loadImage(msg) },
                                onImageClick = { bytes -> viewerBytes = bytes },
                                onLongPress = { menuMsg = msg },
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
        val bitmap = remember(bytes) {
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
        Dialog(
            onDismissRequest = { viewerBytes = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable { viewerBytes = null },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                IconButton(
                    onClick = { viewerBytes = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
                }
            }
        }
    }
}

@Composable
private fun typingLine(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> stringResource(R.string.st_Whisper_Groups_TypingOne, names[0])
    2 -> stringResource(R.string.st_Whisper_Groups_TypingTwo, names[0], names[1])
    else -> stringResource(R.string.st_Whisper_Groups_TypingMany)
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
    onRequestImage: () -> Unit,
    onImageClick: (ByteArray) -> Unit,
    onLongPress: () -> Unit,
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
                    Text(
                        msg.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (msg.mine) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
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
