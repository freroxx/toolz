/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hard stop mirrored from the preprocessor (12 MiB pipeline boundary). */
private const val DAILY_UP_SOURCE_MAX_BYTES = 12 * 1024 * 1024

/** Story aspect ratio: compose letterboxes exactly like the viewer. */
private const val DAILY_UP_PREVIEW_ASPECT = 9f / 16f

/** Bounded source the picker hands us; nothing allocates full-size beyond it. */
private sealed interface DailyUpSource {
    data class Ready(val bytes: ByteArray, val mime: String) : DailyUpSource
    data object TooBig : DailyUpSource
    data object Unreadable : DailyUpSource
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperDailyUpsComposeScreen(
    onNavigateBack: () -> Unit,
    /** Success lands on the hub with a confirmation, never a silent pop. */
    onPublished: () -> Unit = onNavigateBack,
    viewModel: WhisperDailyUpsComposeViewModel = hiltViewModel(),
) {
    if (!WhisperDailyUpsConfig.ENABLED) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }
    SecureWindow()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAudienceSheet by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Draft survives exactly one publish; success is reported to the route.
    LaunchedEffect(state.published) {
        if (state.published) onPublished()
    }

    val requestExit: () -> Unit = {
        if (state.hasDraft) showDiscardDialog = true else onNavigateBack()
    }

    // Back must never silently dump a live draft. Only a successful publish
    // clears the guard; an in-flight share asks before cancelling its upload.
    BackHandler(enabled = state.hasDraft) { requestExit() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            when (val source = readDailyUpSource(context, uri)) {
                is DailyUpSource.Ready -> viewModel.setPhoto(source.bytes, source.mime)
                DailyUpSource.TooBig ->
                    snackbar.showSnackbar(context.getString(R.string.st_Whisper_DailyUp_ErrTooBig))
                DailyUpSource.Unreadable ->
                    snackbar.showSnackbar(context.getString(R.string.st_Whisper_DailyUp_PickFailed))
            }
        }
    }

    val openPicker: () -> Unit = {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        snackbarHost = { GroupSnackbarHost(state = snackbar) },
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Whisper_DailyUp_ComposeTitle),
                navigationIcon = {
                    IconButton(onClick = requestExit) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = GroupCd.back())
                    }
                },
            )
        },
        bottomBar = {
            ComposeShareBar(state = state, onShare = viewModel::publish)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoSlot(photo = state.photo, onPick = openPicker, onClear = viewModel::clearPhoto)

            AudienceSummaryRow(
                state = state,
                onEdit = { showAudienceSheet = true },
                onRetryFriends = viewModel::retryFriends,
            )

            ReceiptsRow(on = state.receiptsOn, onChange = viewModel::setReceipts)
        }
    }

    if (showAudienceSheet) {
        AudienceSheet(
            state = state,
            onToggle = viewModel::toggleAudience,
            onSelectAll = viewModel::selectAllAudience,
            onRetryFriends = viewModel::retryFriends,
            onDismiss = { showAudienceSheet = false },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.st_Whisper_DailyUp_DiscardTitle)) },
            text = { Text(stringResource(R.string.st_Whisper_DailyUp_DiscardBody)) },
            confirmButton = {
                ToolzExpressiveTextButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    },
                ) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_DiscardConfirm))
                }
            },
            dismissButton = {
                ToolzExpressiveTextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_KeepEditing))
                }
            },
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Parts
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoSlot(
    photo: WhisperDailyUpsComposeViewModel.PhotoDraft?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val previewHint = stringResource(R.string.st_Whisper_DailyUp_ComposePreviewHint)

    if (photo == null) {
        Surface(
            onClick = onPick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = border,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DAILY_UP_PREVIEW_ASPECT)
                .semantics(mergeDescendants = true) {},
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_PickPhoto),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Column {
            // The same 9:16 Fit canvas the viewer uses: what is letterboxed
            // here is letterboxed there, instead of a crop that lies.
            Surface(
                onClick = onPick,
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = border,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(DAILY_UP_PREVIEW_ASPECT)
                    .semantics(mergeDescendants = true) {},
            ) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = photo.preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = previewHint,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_Discard))
                }
            }
        }
    }
}

@Composable
private fun ReceiptsRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_ReceiptsLabel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_ReceiptsDesc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun AudienceSummaryRow(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onEdit: () -> Unit,
    onRetryFriends: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.st_Whisper_DailyUp_AudienceSummary,
                    state.audienceSize,
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            when {
                state.friendsLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                state.friends.isNotEmpty() -> TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_AudienceEdit))
                }
            }
        }
        Text(
            text = stringResource(R.string.st_Whisper_DailyUp_AudienceHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            // Loading wins over a stale failure while a retry is in flight.
            state.friendsLoading -> Unit
            state.friendsFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_FriendsFailed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetryFriends) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_Retry))
                }
            }
            state.friends.isEmpty() -> Text(
                text = stringResource(R.string.st_Whisper_DailyUp_NoFriends),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudienceSheet(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onRetryFriends: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(state.friends, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            state.friends
        } else {
            state.friends.filter { it.effectiveName.contains(needle, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.st_Whisper_DailyUp_AudienceSheetTitle),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            R.string.st_Whisper_DailyUp_AudienceSelected,
                            state.audienceSize,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_AudienceDone))
                }
            }

            when {
                state.friendsLoading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                state.friendsFailed && state.friends.isEmpty() -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.st_Whisper_DailyUp_FriendsFailed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetryFriends) {
                        Text(stringResource(R.string.st_Whisper_DailyUp_Retry))
                    }
                }

                state.friends.isEmpty() -> Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_NoFriends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                else -> {
                    GroupSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.st_Whisper_DailyUp_AudienceSearchHint),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onSelectAll,
                            enabled = state.excluded.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.st_Whisper_DailyUp_AudienceSelectAll))
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp),
                    ) {
                        items(filtered, key = { it.id }) { friend ->
                            val included = friend.id !in state.excluded
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .toggleable(
                                        value = included,
                                        role = Role.Checkbox,
                                        onValueChange = { onToggle(friend.id) },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GroupAvatar(
                                    name = friend.effectiveName,
                                    groupId = friend.id,
                                    size = 40.dp,
                                )
                                Text(
                                    text = friend.effectiveName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Checkbox(checked = included, onCheckedChange = null)
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.st_Whisper_Groups_NoFriendsFound),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeShareBar(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onShare: () -> Unit,
) {
    val block = state.shareBlock
    val reason: String? = when (block) {
        ShareBlock.NoPhoto -> stringResource(R.string.st_Whisper_DailyUp_NeedPhoto)
        ShareBlock.NoQuota -> stringResource(R.string.st_Whisper_DailyUp_NoUpsLeft)
        ShareBlock.NoAudience -> if (
            !state.friendsLoading &&
            !state.friendsFailed &&
            state.friends.isNotEmpty()
        ) {
            stringResource(R.string.st_Whisper_DailyUp_AudienceWarning)
        } else {
            null
        }
        else -> null
    }
    val enabled = state.photo != null &&
        state.remaining > 0 &&
        state.audienceSize > 0 &&
        !state.publishing &&
        !state.published

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (reason != null) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (block == ShareBlock.NoAudience) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.st_Whisper_DailyUp_QuotaLeft,
                            state.remaining,
                            WhisperDailyUpsConfig.DAILY_LIMIT,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    ToolzExpressiveButton(
                        onClick = onShare,
                        enabled = enabled,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .widthIn(min = 132.dp),
                    ) {
                        if (state.publishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(R.string.st_Whisper_DailyUp_Posting))
                        } else {
                            Text(
                                text = stringResource(R.string.st_Whisper_DailyUp_PostLabel),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Picker plumbing (bounded read — the pipeline re-encodes everything anyway)
// ────────────────────────────────────────────────────────────────────────────

private val PICK_CHUNK = 64 * 1024

private suspend fun readDailyUpSource(context: Context, uri: Uri): DailyUpSource =
    withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@runCatching DailyUpSource.Unreadable
            stream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(PICK_CHUNK)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n <= 0) break
                    total += n
                    if (total > DAILY_UP_SOURCE_MAX_BYTES) {
                        return@runCatching DailyUpSource.TooBig
                    }
                    buffer.write(chunk, 0, n)
                }
                val bytes = buffer.toByteArray()
                if (bytes.isEmpty()) {
                    return@runCatching DailyUpSource.Unreadable
                }
                DailyUpSource.Ready(
                    bytes = bytes,
                    mime = context.contentResolver.getType(uri) ?: "image/jpeg",
                )
            }
        }.getOrDefault(DailyUpSource.Unreadable)
    }
