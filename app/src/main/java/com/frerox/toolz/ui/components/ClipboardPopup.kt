/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardType
import com.frerox.toolz.ui.screens.clipboard.ClipboardEvent
import com.frerox.toolz.ui.screens.clipboard.ClipboardMessage
import com.frerox.toolz.ui.screens.clipboard.ClipboardViewModel
import com.frerox.toolz.util.security.BiometricPromptUtils

/**
 * Floating quick switcher launched from the QS tile. Shows the most recent
 * clips, masks sensitive content, and supports image/file clips. Locked clips
 * require biometric confirmation before they can be copied.
 */
@Composable
fun ClipboardPopup(
    onDismiss: () -> Unit,
    onManageHistory: () -> Unit,
    viewModel: ClipboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recent = remember(state.entries) { state.entries.take(6) }
    val haptic = rememberToolzHapticFeedback()
    // Opening the popup captures the current clipboard too. Only dismiss on
    // SAVED/BUMPED/EMPTY when the user explicitly pressed "Paste current".
    var awaitingPaste by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshClipboard(notify = false)
    }

    // Safety net: if a manual capture finishes without emitting a result
    // (for example because a silent refresh won the race), never leave the
    // paste button stuck in a spinner state.
    LaunchedEffect(awaitingPaste, state.busy) {
        if (awaitingPaste && !state.busy) {
            kotlinx.coroutines.delay(8_000)
            if (!state.busy) awaitingPaste = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is ClipboardEvent.Message) {
                val dismiss = when (event.type) {
                    ClipboardMessage.COPIED,
                    ClipboardMessage.NOTHING_TO_COPY,
                    ClipboardMessage.SENSITIVE_SKIPPED,
                    ClipboardMessage.TOO_LARGE,
                    ClipboardMessage.QUOTA,
                    ClipboardMessage.FAILED,
                    -> true
                    ClipboardMessage.SAVED,
                    ClipboardMessage.BUMPED,
                    ClipboardMessage.EMPTY,
                    ClipboardMessage.SANDBOX,
                    -> awaitingPaste
                    else -> false
                }
                if (dismiss) onDismiss()
            }
        }
    }

    fun copy(entry: ClipboardEntry) {
        if (!entry.isLocked) {
            viewModel.copyEntry(entry)
            return
        }
        if (!state.settings.biometricLock) {
            viewModel.copyEntry(entry)
            return
        }
        val activity = context.findFragmentActivity()
        if (activity == null || !BiometricPromptUtils.canAuthenticate(activity)) {
            openHistory(context)
            onDismiss()
            return
        }
        BiometricPromptUtils.showBiometricPrompt(
            activity = activity,
            title = R.string.st_ClipboardPopup_unlock_title,
            subtitle = R.string.st_ClipboardPopup_unlock_subtitle,
            onSuccess = {
                haptic.success()
                viewModel.copyEntry(entry)
            },
            onError = { _, _ -> },
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        // Keep the card inside the safe area and let the clip list shrink on
        // small/landscape screens instead of pushing the action buttons off
        // the bottom of the display.
        val compact = maxHeight < 520.dp
        val listMaxHeight = (maxHeight - if (compact) 250.dp else 320.dp)
            .coerceIn(72.dp, 340.dp)
        ExpressiveCard(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.st_ClipboardPopup_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = when {
                                !state.loaded -> stringResource(R.string.st_ClipboardScreen_loading)
                                recent.isEmpty() ->
                                    stringResource(R.string.st_ClipboardPopup_empty_subtitle)
                                else -> pluralStringResource(
                                    R.plurals.st_ClipboardPopup_subtitle,
                                    state.entries.size,
                                    state.entries.size,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.click()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.st_ClipboardPopup_close),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                when {
                    !state.loaded -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    recent.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.st_ClipboardPopup_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = listMaxHeight),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(recent, key = { it.id }) { entry ->
                                PopupClipRow(
                                    entry = entry,
                                    revealed = entry.id in state.revealedIds,
                                    maskedPreview = viewModel.classifier.maskPreview(entry.content, 2),
                                    onCopy = {
                                        haptic.click()
                                        copy(entry)
                                    },
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        haptic.click()
                        awaitingPaste = true
                        viewModel.pasteCurrent()
                    },
                    enabled = state.loaded && !state.busy && !awaitingPaste,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy || awaitingPaste) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_ClipboardPopup_paste_current))
                }

                OutlinedButton(
                    onClick = {
                        haptic.click()
                        openHistory(context)
                        onManageHistory()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.st_ClipboardPopup_open_history),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupClipRow(
    entry: ClipboardEntry,
    revealed: Boolean,
    maskedPreview: String,
    onCopy: () -> Unit,
) {
    val masked = (entry.isSensitive || entry.isLocked) && !revealed
    val isImage = entry.type == ClipboardType.IMAGE
    val isFile = entry.type == ClipboardType.FILE
    Surface(
        onClick = onCopy,
        shape = RoundedCornerShape(18.dp),
        color = if (entry.isSensitive || entry.isLocked) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                when {
                    entry.isLocked -> Icons.Rounded.Lock
                    isImage -> Icons.Rounded.Image
                    isFile -> Icons.AutoMirrored.Rounded.InsertDriveFile
                    else -> Icons.Rounded.ContentCopy
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (entry.isSensitive || entry.isLocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = popupPreview(entry, masked, maskedPreview),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        when {
                            entry.isLocked -> R.string.st_ClipboardPopup_locked
                            isImage -> R.string.st_ClipboardScreen_type_image
                            isFile -> R.string.st_ClipboardScreen_type_file
                            entry.type == ClipboardType.MULTI ->
                                R.string.st_ClipboardScreen_type_multi
                            entry.type == ClipboardType.OTP ->
                                R.string.st_ClipboardScreen_type_otp
                            else -> R.string.st_ClipboardScreen_type_text
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.isPinned) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = stringResource(R.string.st_ClipboardScreen_badge_pinned),
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.st_ClipboardScreen_action_copy),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun popupPreview(entry: ClipboardEntry, masked: Boolean, maskedPreview: String): String {
    val content = entry.content
    val imageLabel = stringResource(R.string.st_ClipboardScreen_attachment_image)
    val fileLabel = stringResource(R.string.st_ClipboardScreen_attachment_file)
    val multiLabel = pluralStringResource(
        R.plurals.st_ClipboardScreen_multi_items,
        entry.attachmentCount.coerceAtLeast(1),
        entry.attachmentCount.coerceAtLeast(1),
    )
    return when {
        // Locked clips stay fully redacted until this popup reveals them.
        // Sensitive-only clips use the classifier's partial mask instead.
        masked && entry.isLocked -> "••••••••"
        masked -> maskedPreview
        entry.type == ClipboardType.IMAGE -> imageLabel
        entry.type == ClipboardType.FILE -> content.ifBlank { fileLabel }
        entry.type == ClipboardType.MULTI -> content.ifBlank { multiLabel }
        content.isBlank() -> entry.summary.orEmpty()
        else -> content
    }
}

private fun openHistory(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra("navigate_to", "clipboard")
    }
    runCatching { context.startActivity(intent) }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
