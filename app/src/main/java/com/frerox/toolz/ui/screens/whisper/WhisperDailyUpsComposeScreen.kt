/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hard stop mirrored from the preprocessor (12 MiB pipeline boundary). */
private const val DAILY_UP_SOURCE_MAX_BYTES = 12 * 1024 * 1024

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
    viewModel: WhisperDailyUpsComposeViewModel = hiltViewModel(),
) {
    if (!WhisperDailyUpsConfig.ENABLED) return
    SecureWindow()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Draft survives exactly one publish: back to the hub after success.
    LaunchedEffect(state.published) {
        if (state.published) onNavigateBack()
    }

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

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        snackbarHost = { GroupSnackbarHost(state = snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.st_Whisper_DailyUp_ComposeTitle),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = GroupCd.back())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoSlot(
                state = state,
                onPick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClear = viewModel::clearPhoto,
            )

            ReceiptsRow(on = state.receiptsOn, onChange = viewModel::setReceipts)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AudienceSection(state = state, onToggle = viewModel::toggleExcluded)

            Spacer(Modifier.height(8.dp))
            ShareButton(state = state, onClick = viewModel::publish)
            // Disabled buttons that don't explain themselves read like a bug —
            // this only appears in the one case no other copy covers.
            if (state.photo != null && !state.friendsLoading && state.friends.isNotEmpty() && state.audienceSize <= 0) {
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_AudienceWarning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Parts
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoSlot(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val photo = state.photo
    if (photo == null) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .clickable(onClick = onPick),
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.9f),
            ) {
                Image(
                    bitmap = photo.preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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
private fun AudienceSection(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (state.excluded.isEmpty()) {
                stringResource(R.string.st_Whisper_DailyUp_AudienceAll)
            } else {
                stringResource(
                    R.string.st_Whisper_DailyUp_AudienceSummary,
                    state.audienceSize,
                )
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.st_Whisper_DailyUp_AudienceHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.friendsLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            state.friends.isEmpty() -> {
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_NoFriends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                state.friends.forEach { friend ->
                    val included = friend.id !in state.excluded
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(friend.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = friend.effectiveName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            text = friend.effectiveName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            maxLines = 1,
                        )
                        Checkbox(checked = included, onCheckedChange = { onToggle(friend.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareButton(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onClick: () -> Unit,
) {
    val enabled = state.photo != null &&
        !state.publishing &&
        !state.published &&
        state.remaining > 0 &&
        state.audienceSize > 0
    ToolzExpressiveButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (state.publishing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.st_Whisper_DailyUp_Posting))
        } else {
            // Disabled buttons explain themselves: no photo → what to do next,
            // exhausted quota → the limit. Same action, honest feedback.
            Text(
                text = when {
                    state.remaining <= 0 -> stringResource(R.string.st_Whisper_DailyUp_ErrQuota)
                    // Requires no new string: the photo slot's own invite copy.
                    state.photo == null -> stringResource(R.string.st_Whisper_DailyUp_PickPhoto)
                    else -> stringResource(R.string.st_Whisper_DailyUp_PostLabel)
                },
                fontWeight = FontWeight.SemiBold,
            )
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
