/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.frerox.toolz.ui.screens.clipboard

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.clipboard.ClipAiState
import com.frerox.toolz.data.clipboard.ClipboardAttachment
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardFilter
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.clipboard.ClipboardType
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveSearchField
import com.frerox.toolz.ui.components.ExpressiveStatePill
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.security.BiometricPromptUtils
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Toolz Clipboard — full Material 3 Expressive history, trash, detail sheet,
 * onboarding, capture diagnostics and settings.
 *
 * Integration callbacks are optional so the screen can be embedded anywhere.
 */
@Composable
fun ClipboardScreen(
    viewModel: ClipboardViewModel,
    onBack: () -> Unit,
    onOpenNotepad: (String) -> Unit = {},
    onCreateTask: (String) -> Unit = {},
    onShowQr: (String) -> Unit = {},
    onEncrypt: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = rememberToolzHapticFeedback()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Resolve localized labels through composition so they stay correct across
    // configuration/locale changes instead of reading resources from context.
    val undoLabel = stringResource(R.string.st_ClipboardScreen_action_undo)
    val shareLabel = stringResource(R.string.st_ClipboardScreen_action_share)
    val shareFailedLabel = stringResource(R.string.st_ClipboardScreen_message_share_failed)
    val biometricUnavailableLabel = stringResource(R.string.st_ClipboardScreen_biometric_unavailable)
    val attachmentFailedLabel = stringResource(R.string.st_ClipboardScreen_message_attachment_failed)
    val openFailedLabel = stringResource(R.string.st_ClipboardScreen_message_open_failed)

    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStatus by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showEmptyTrashDialog by rememberSaveable { mutableStateOf(false) }
    var showOverflow by rememberSaveable { mutableStateOf(false) }

    val detailEntry = remember(detailId, state.entries, state.trashed) {
        detailId?.let { id -> state.entries.firstOrNull { it.id == id } ?: state.trashed.firstOrNull { it.id == id } }
    }

    LaunchedEffect(state.settings.onboardingSeen) {
        if (!state.settings.onboardingSeen) showOnboarding = true
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ClipboardEvent.Message -> {
                    val text = messageText(context, event)
                    val undoable = event.type == ClipboardMessage.DELETED
                    val result = snackbarHostState.showSnackbar(
                        message = text,
                        actionLabel = if (undoable) {
                            undoLabel
                        } else {
                            null
                        },
                        withDismissAction = !undoable,
                        duration = if (undoable) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed && undoable) {
                        viewModel.undoDelete()
                    }
                }
                is ClipboardEvent.Share -> {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(event.intent, shareLabel),
                        )
                    }.onFailure {
                        snackbarHostState.showSnackbar(shareFailedLabel)
                    }
                }
            }
        }
    }

    fun runWithUnlock(entry: ClipboardEntry, action: () -> Unit) {
        if (!entry.isLocked) {
            action()
            return
        }
        if (!state.settings.biometricLock) {
            viewModel.markRevealed(entry.id)
            action()
            return
        }
        val activity = context.findFragmentActivity()
        if (activity == null || !BiometricPromptUtils.canAuthenticate(activity)) {
            scope.launch {
                snackbarHostState.showSnackbar(biometricUnavailableLabel)
            }
            return
        }
        BiometricPromptUtils.showBiometricPrompt(
            activity = activity,
            title = R.string.st_ClipboardScreen_unlock_title,
            subtitle = R.string.st_ClipboardScreen_unlock_subtitle,
            onSuccess = {
                haptic.success()
                viewModel.markRevealed(entry.id)
                action()
            },
            onError = { _, message ->
                scope.launch { snackbarHostState.showSnackbar(message.toString()) }
            },
        )
    }

    Scaffold(
        modifier = Modifier.toolzBackground(),
        containerColor = Color.Transparent,
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    Text(
                        text = if (state.showTrash) {
                            stringResource(R.string.st_ClipboardScreen_trash_title)
                        } else {
                            stringResource(R.string.st_ClipboardScreen_title)
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = if (state.isSelectionMode) {
                            pluralStringResource(
                                R.plurals.st_ClipboardScreen_selected_count,
                                state.selection.size,
                                state.selection.size,
                            )
                        } else {
                            stringResource(
                                R.string.st_ClipboardScreen_subtitle,
                                state.stats.activeCount,
                                state.stats.sensitiveCount,
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.click()
                            if (state.isSelectionMode) viewModel.clearSelection() else onBack()
                        },
                    ) {
                        Icon(
                            if (state.isSelectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(
                                if (state.isSelectionMode) R.string.st_ClipboardScreen_action_clear_selection
                                else R.string.st_ClipboardScreen_action_back,
                            ),
                        )
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(
                            onClick = {
                                haptic.click()
                                if (state.showTrash) viewModel.restoreSelected() else viewModel.copySelected()
                            },
                        ) {
                            Icon(
                                if (state.showTrash) Icons.Rounded.Restore else Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(
                                    if (state.showTrash) R.string.st_ClipboardScreen_action_restore
                                    else R.string.st_ClipboardScreen_action_copy,
                                ),
                            )
                        }
                        if (!state.showTrash && state.settings.aiEnabled) {
                            IconButton(
                                onClick = {
                                    haptic.click()
                                    viewModel.runAiSelected()
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = stringResource(R.string.st_ClipboardScreen_action_ai),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic.click()
                                if (state.showTrash) viewModel.deleteSelectedForever()
                                else viewModel.deleteSelected()
                            },
                        ) {
                            Icon(
                                if (state.showTrash) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
                                contentDescription = stringResource(
                                    if (state.showTrash) R.string.st_ClipboardScreen_action_delete_forever
                                    else R.string.st_ClipboardScreen_action_delete,
                                ),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                haptic.click()
                                viewModel.setShowTrash(!state.showTrash)
                            },
                        ) {
                            Icon(
                                if (state.showTrash) Icons.Rounded.ContentPaste else Icons.Rounded.Delete,
                                contentDescription = stringResource(
                                    if (state.showTrash) R.string.st_ClipboardScreen_action_back
                                    else R.string.st_ClipboardScreen_action_trash,
                                ),
                                tint = if (state.showTrash) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { haptic.click(); showStatus = true }) {
                            Icon(
                                Icons.Rounded.Security,
                                contentDescription = stringResource(R.string.st_ClipboardScreen_action_status),
                            )
                        }
                        Box {
                            IconButton(onClick = { haptic.click(); showOverflow = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.st_ClipboardScreen_more),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.st_ClipboardScreen_action_select_all)) },
                                    leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.selectAll()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.st_ClipboardScreen_action_settings)) },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                    onClick = {
                                        showOverflow = false
                                        showSettings = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.st_ClipboardScreen_action_refresh)) },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.pasteCurrent()
                                    },
                                )
                                HorizontalDivider()
                                if (state.showTrash) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.st_ClipboardScreen_action_empty_trash)) },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, null) },
                                        onClick = {
                                            showOverflow = false
                                            showEmptyTrashDialog = true
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.st_ClipboardScreen_action_clear_all)) },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                        onClick = {
                                            showOverflow = false
                                            showClearDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isSelectionMode && !state.showTrash) {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptic.click()
                        viewModel.pasteCurrent()
                    },
                    icon = {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.ContentPaste, null)
                        }
                    },
                    text = { Text(stringResource(R.string.st_ClipboardScreen_action_paste)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.sandboxActive) {
                InfoBanner(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.st_ClipboardScreen_sandbox_banner),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            } else if (state.showSetupBanner) {
                InfoBanner(
                    icon = Icons.Rounded.WarningAmber,
                    title = stringResource(R.string.st_ClipboardScreen_foreground_banner),
                    action = stringResource(R.string.st_ClipboardScreen_action_finish_setup),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { showStatus = true },
                )
            }

            if (state.settings.offline) {
                InfoBanner(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.st_ClipboardScreen_ai_offline),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            SearchAndFilters(
                query = state.query,
                filter = state.filter,
                onQueryChange = viewModel::onSearchQueryChanged,
                onFilterChange = viewModel::setFilter,
            )

            when {
                state.visibleEntries.isEmpty() && state.query.isNotBlank() -> {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.st_ClipboardScreen_no_results),
                        body = stringResource(R.string.st_ClipboardScreen_no_results_body, state.query),
                    )
                }
                state.visibleEntries.isEmpty() && state.showTrash -> {
                    EmptyState(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.st_ClipboardScreen_empty_trash_title),
                        body = stringResource(R.string.st_ClipboardScreen_empty_trash_body),
                    )
                }
                state.visibleEntries.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Rounded.ContentPaste,
                        title = stringResource(R.string.st_ClipboardScreen_empty_title),
                        body = stringResource(R.string.st_ClipboardScreen_empty_body),
                    )
                }
                else -> {
                    HistoryList(
                        state = state,
                        viewModel = viewModel,
                        onOpen = { entry ->
                            if (state.isSelectionMode) {
                                viewModel.toggleSelection(entry.id)
                            } else {
                                detailId = entry.id
                            }
                        },
                        onLongPress = { entry ->
                            haptic.longClick()
                            viewModel.toggleSelection(entry.id)
                        },
                        onCopy = { entry ->
                            runWithUnlock(entry) { viewModel.copyEntry(entry) }
                        },
                        onPin = viewModel::togglePin,
                        onDelete = viewModel::deleteEntry,
                        onRestore = viewModel::restoreEntry,
                        onDeleteForever = viewModel::deleteForever,
                    )
                }
            }
        }
    }

    if (showOnboarding) {
        OnboardingSheet(
            state = state,
            onDismiss = {
                showOnboarding = false
                viewModel.completeOnboarding()
            },
            onAuthorizeShizuku = viewModel::requestShizukuAuthorization,
            onGrantAppOp = viewModel::grantClipboardAppOp,
            onInstallShizuku = {
                runCatching { context.startActivity(viewModel.shizukuInstallIntent()) }
            },
            onOpenStatus = {
                showOnboarding = false
                viewModel.completeOnboarding()
                showStatus = true
            },
        )
    }

    if (showSettings) {
        SettingsSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showSettings = false },
            onShowStatus = {
                showSettings = false
                showStatus = true
            },
        )
    }

    if (showStatus) {
        StatusSheet(
            state = state,
            onDismiss = { showStatus = false },
            viewModel = viewModel,
            onOpenAppDetails = {
                runCatching { context.startActivity(viewModel.appDetailsIntent()) }
            },
        )
    }

    detailEntry?.let { entry ->
        val attachments = state.attachmentsByClip[entry.id].orEmpty()
        ClipDetailSheet(
            entry = entry,
            attachments = attachments,
            revealed = entry.id in state.revealedIds,
            state = state,
            viewModel = viewModel,
            onDismiss = { detailId = null },
            onUnlock = { runWithUnlock(entry) { viewModel.markRevealed(entry.id) } },
            onCopy = { runWithUnlock(entry) { viewModel.copyEntry(entry) } },
            onShare = { runWithUnlock(entry) { viewModel.shareEntry(entry) } },
            onOpenNotepad = { onOpenNotepad(entry.content) },
            onCreateTask = { onCreateTask(entry.content) },
            onShowQr = { onShowQr(entry.content) },
            onEncrypt = { onEncrypt(entry.content) },
            onOpenLink = { openLink(context, entry.content) },
            onToggleLock = {
                if (entry.isLocked) {
                    runWithUnlock(entry) { viewModel.setLocked(entry, false) }
                } else {
                    viewModel.setLocked(entry, true)
                }
            },
            onOpenAttachment = { attachment ->
                scope.launch {
                    val intent = viewModel.openAttachment(attachment)
                    if (intent == null) {
                        snackbarHostState.showSnackbar(attachmentFailedLabel)
                    } else {
                        runCatching { context.startActivity(intent) }.onFailure {
                            snackbarHostState.showSnackbar(openFailedLabel)
                        }
                    }
                }
            },
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = stringResource(R.string.st_ClipboardScreen_clear_title),
            body = stringResource(R.string.st_ClipboardScreen_clear_body),
            confirmLabel = stringResource(R.string.st_ClipboardScreen_clear_confirm),
            onConfirm = {
                showClearDialog = false
                viewModel.clearAll()
            },
            onDismiss = { showClearDialog = false },
        )
    }

    if (showEmptyTrashDialog) {
        ConfirmDialog(
            title = stringResource(R.string.st_ClipboardScreen_empty_trash_confirm_title),
            body = stringResource(R.string.st_ClipboardScreen_empty_trash_confirm_body),
            confirmLabel = stringResource(R.string.st_ClipboardScreen_action_empty_trash),
            onConfirm = {
                showEmptyTrashDialog = false
                viewModel.emptyTrash()
            },
            onDismiss = { showEmptyTrashDialog = false },
        )
    }
}

// ── Search + filters ──────────────────────────────────────────────────────

@Composable
private fun SearchAndFilters(
    query: String,
    filter: ClipboardFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ClipboardFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressiveSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.st_ClipboardScreen_search_placeholder)) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_ClipboardScreen_action_clear_search))
                    }
                }
            },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(ClipboardFilter.entries) { item ->
                ExpressiveFilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = { Text(filterLabel(item)) },
                    leadingIcon = if (filter == item) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun filterLabel(filter: ClipboardFilter): String = stringResource(
    when (filter) {
        ClipboardFilter.ALL -> R.string.st_ClipboardScreen_filter_all
        ClipboardFilter.TEXT -> R.string.st_ClipboardScreen_filter_text
        ClipboardFilter.LINKS -> R.string.st_ClipboardScreen_filter_links
        ClipboardFilter.CODE -> R.string.st_ClipboardScreen_filter_code
        ClipboardFilter.OTP -> R.string.st_ClipboardScreen_filter_otp
        ClipboardFilter.IMAGES -> R.string.st_ClipboardScreen_filter_images
        ClipboardFilter.FILES -> R.string.st_ClipboardScreen_filter_files
        ClipboardFilter.PINNED -> R.string.st_ClipboardScreen_filter_pinned
        ClipboardFilter.SENSITIVE -> R.string.st_ClipboardScreen_filter_sensitive
    },
)

// ── History list ──────────────────────────────────────────────────────────

@Composable
private fun HistoryList(
    state: ClipboardUiState,
    viewModel: ClipboardViewModel,
    onOpen: (ClipboardEntry) -> Unit,
    onLongPress: (ClipboardEntry) -> Unit,
    onCopy: (ClipboardEntry) -> Unit,
    onPin: (ClipboardEntry) -> Unit,
    onDelete: (ClipboardEntry) -> Unit,
    onRestore: (ClipboardEntry) -> Unit,
    onDeleteForever: (ClipboardEntry) -> Unit,
) {
    val groups = remember(state.visibleEntries) { viewModel.groupedEntries(state.visibleEntries) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { group ->
            item(key = "header_${group.group.name}") {
                Text(
                    text = groupLabel(group.group),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                )
            }
            items(group.entries, key = { it.id }) { entry ->
                ClipboardCard(
                    entry = entry,
                    attachments = state.attachmentsByClip[entry.id].orEmpty(),
                    revealed = entry.id in state.revealedIds,
                    selected = entry.id in state.selection,
                    selectionMode = state.isSelectionMode,
                    showTrash = state.showTrash,
                    aiEnabled = state.settings.aiEnabled,
                    aiRunning = entry.id in state.aiRunningIds,
                    onOpen = { onOpen(entry) },
                    onLongPress = { onLongPress(entry) },
                    onCopy = { onCopy(entry) },
                    onPin = { onPin(entry) },
                    onDelete = { onDelete(entry) },
                    onRestore = { onRestore(entry) },
                    onDeleteForever = { onDeleteForever(entry) },
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun groupLabel(group: ClipboardDateGroup): String = stringResource(
    when (group) {
        ClipboardDateGroup.PINNED -> R.string.st_ClipboardScreen_section_pinned
        ClipboardDateGroup.TODAY -> R.string.st_ClipboardScreen_section_today
        ClipboardDateGroup.YESTERDAY -> R.string.st_ClipboardScreen_section_yesterday
        ClipboardDateGroup.OLDER -> R.string.st_ClipboardScreen_section_older
    },
)

@Composable
private fun ClipboardCard(
    entry: ClipboardEntry,
    attachments: List<ClipboardAttachment>,
    revealed: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    showTrash: Boolean,
    aiEnabled: Boolean,
    aiRunning: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    viewModel: ClipboardViewModel,
) {
    val haptic = rememberToolzHapticFeedback()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart },
        positionalThreshold = { it * 0.4f },
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            haptic.tick()
            onDelete()
        }
    }

    val masked = (entry.isSensitive || entry.isLocked) && !revealed
    val preview = when {
        // Never leak a file name or text preview while the clip is masked.
        masked -> when (entry.type) {
            ClipboardType.IMAGE -> stringResource(R.string.st_ClipboardScreen_attachment_image)
            ClipboardType.FILE -> stringResource(R.string.st_ClipboardScreen_attachment_file)
            ClipboardType.MULTI -> pluralStringResource(
                R.plurals.st_ClipboardScreen_multi_items,
                entry.attachmentCount.coerceAtLeast(attachments.size),
                entry.attachmentCount.coerceAtLeast(attachments.size),
            )
            else -> viewModel.classifier.maskPreview(entry.content, 3)
        }
        entry.type == ClipboardType.IMAGE -> stringResource(R.string.st_ClipboardScreen_attachment_image)
        entry.type == ClipboardType.FILE -> entry.content.ifBlank {
            attachments.firstOrNull()?.fileName ?: stringResource(R.string.st_ClipboardScreen_attachment_file)
        }
        entry.type == ClipboardType.MULTI -> pluralStringResource(
            R.plurals.st_ClipboardScreen_multi_items,
            entry.attachmentCount.coerceAtLeast(attachments.size),
            entry.attachmentCount.coerceAtLeast(attachments.size),
        )
        else -> entry.content
    }

    val containerColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            entry.isSensitive || entry.isLocked ->
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(180),
        label = "clipCardColor",
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode && !showTrash,
        backgroundContent = {
            val background by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                },
                animationSpec = tween(160),
                label = "clipSwipeBg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(background)
                    .padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ExpressiveCard(
            onClick = {
                haptic.click()
                onOpen()
            },
            onLongClick = {
                onLongPress()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            containerColor = containerColor,
            border = if (entry.isSensitive || entry.isLocked) {
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                )
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    TypeBadge(type = entry.type)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = preview.ifBlank { stringResource(R.string.st_ClipboardScreen_empty) },
                            style = if (entry.type == ClipboardType.CODE) {
                                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = if (masked) FontWeight.Medium else FontWeight.Normal,
                            maxLines = if (entry.type == ClipboardType.CODE) 5 else 4,
                            overflow = TextOverflow.Ellipsis,
                            color = if (masked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (aiEnabled && !masked && !entry.summary.isNullOrBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = entry.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (attachments.isNotEmpty() && !masked) {
                            AttachmentStrip(
                                attachments = attachments,
                                viewModel = viewModel,
                                onOpen = onOpen,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            MetaPill(typeLabel(entry.type))
                            MetaPill(relativeTime(entry.timestamp))
                            if (entry.isPinned) MetaPill(stringResource(R.string.st_ClipboardScreen_badge_pinned))
                            if (entry.isSensitive || entry.isLocked) {
                                MetaPill(
                                    text = if (entry.isLocked) {
                                        stringResource(R.string.st_ClipboardScreen_badge_locked)
                                    } else {
                                        stringResource(R.string.st_ClipboardScreen_badge_sensitive)
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (entry.passwordVaultMatch) {
                                MetaPill(
                                    text = stringResource(R.string.st_ClipboardScreen_badge_vault),
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            if (entry.isAiProcessed && !masked) {
                                MetaPill(
                                    text = stringResource(R.string.st_ClipboardScreen_badge_ai),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (entry.expiresAt != null) {
                                MetaPill(stringResource(R.string.st_ClipboardScreen_badge_expires))
                            }
                        }
                    }
                    if (selectionMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onOpen() },
                        )
                    } else if (showTrash) {
                        Column {
                            IconButton(onClick = { haptic.click(); onRestore() }) {
                                Icon(
                                    Icons.Rounded.Restore,
                                    contentDescription = stringResource(R.string.st_ClipboardScreen_action_restore),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { haptic.click(); onDeleteForever() }) {
                                Icon(
                                    Icons.Rounded.DeleteForever,
                                    contentDescription = stringResource(R.string.st_ClipboardScreen_action_delete_forever),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { haptic.click(); onPin() }) {
                                Icon(
                                    Icons.Rounded.PushPin,
                                    contentDescription = stringResource(
                                        if (entry.isPinned) R.string.st_ClipboardScreen_action_unpin
                                        else R.string.st_ClipboardScreen_action_pin,
                                    ),
                                    tint = if (entry.isPinned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    },
                                )
                            }
                            IconButton(onClick = { haptic.click(); onCopy() }) {
                                if (aiRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.st_ClipboardScreen_action_copy),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val icon = typeIcon(type)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .padding(10.dp)
                .size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MetaPill(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.1f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<ClipboardAttachment>,
    viewModel: ClipboardViewModel,
    onOpen: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        attachments.take(4).forEach { attachment ->
            val isImage = attachment.mimeType?.startsWith("image/") == true
            if (isImage) {
                AttachmentThumbnail(
                    attachment = attachment,
                    viewModel = viewModel,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpen),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.clickable(onClick = onOpen),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = attachment.fileName ?: stringResource(R.string.st_ClipboardScreen_attachment_file),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }
            }
        }
        if (attachments.size > 4) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = "+${attachments.size - 4}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    attachment: ClipboardAttachment,
    viewModel: ClipboardViewModel,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, attachment.blobId) {
        value = viewModel.loadAttachmentBitmap(attachment)?.asImageBitmap()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = attachment.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Detail sheet ──────────────────────────────────────────────────────────

@Composable
private fun ClipDetailSheet(
    entry: ClipboardEntry,
    attachments: List<ClipboardAttachment>,
    revealed: Boolean,
    state: ClipboardUiState,
    viewModel: ClipboardViewModel,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenNotepad: () -> Unit,
    onCreateTask: () -> Unit,
    onShowQr: () -> Unit,
    onEncrypt: () -> Unit,
    onOpenLink: () -> Unit,
    onOpenAttachment: (ClipboardAttachment) -> Unit,
    onToggleLock: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = rememberToolzHapticFeedback()
    val masked = (entry.isSensitive || entry.isLocked) && !revealed

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(entry.type)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = typeLabel(entry.type),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = relativeTime(entry.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (entry.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.st_ClipboardScreen_badge_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (entry.isSensitive || entry.isLocked) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (entry.isLocked) Icons.Rounded.Lock else Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = sensitivityLabel(entry),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (entry.passwordVaultMatch) {
                                    Text(
                                        text = stringResource(R.string.st_ClipboardScreen_badge_vault_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (masked) {
                                TextButton(onClick = onUnlock) {
                                    Text(stringResource(R.string.st_ClipboardScreen_action_reveal))
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        haptic.click()
                                        viewModel.hideRevealed(entry.id)
                                    },
                                ) {
                                    Text(stringResource(R.string.st_ClipboardScreen_action_hide))
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_content))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    SelectionContainer {
                        Text(
                            text = if (masked) viewModel.classifier.mask(entry.content) else entry.content,
                            style = if (entry.type == ClipboardType.CODE) {
                                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (attachments.isNotEmpty() && !masked) {
                item {
                    SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_attachments))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attachments.forEach { attachment ->
                            AttachmentRow(
                                attachment = attachment,
                                viewModel = viewModel,
                                onOpen = { onOpenAttachment(attachment) },
                            )
                        }
                    }
                }
            }

            if (state.settings.aiEnabled && !entry.isSensitive && !entry.isLocked) {
                item {
                    AiSection(
                        entry = entry,
                        running = entry.id in state.aiRunningIds,
                        configured = state.aiConfigured,
                        onRun = { viewModel.runAi(entry) },
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_meta))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetaRow(
                            stringResource(R.string.st_ClipboardScreen_meta_size),
                            Formatter.formatShortFileSize(LocalContext.current, entry.sizeBytes),
                        )
                        MetaRow(
                            stringResource(R.string.st_ClipboardScreen_meta_source),
                            sourceLabel(entry),
                        )
                        MetaRow(
                            stringResource(R.string.st_ClipboardScreen_meta_attachments),
                            attachments.size.toString(),
                        )
                        MetaRow(
                            stringResource(R.string.st_ClipboardScreen_meta_expiry),
                            entry.expiresAt?.let { timestamp ->
                                stringResource(
                                    R.string.st_ClipboardScreen_expires_on,
                                    android.text.format.DateFormat.getDateFormat(LocalContext.current)
                                        .format(Date(timestamp)),
                                )
                            } ?: stringResource(R.string.st_ClipboardScreen_no_expiry),
                        )
                    }
                }
            }

            item {
                SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_actions))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile(
                            icon = Icons.Rounded.ContentCopy,
                            label = stringResource(R.string.st_ClipboardScreen_action_copy),
                            modifier = Modifier.weight(1f),
                            onClick = onCopy,
                        )
                        ActionTile(
                            icon = Icons.Rounded.Share,
                            label = stringResource(R.string.st_ClipboardScreen_action_share),
                            modifier = Modifier.weight(1f),
                            onClick = onShare,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile(
                            icon = if (entry.isPinned) Icons.Rounded.PushPin else Icons.Rounded.PushPin,
                            label = stringResource(
                                if (entry.isPinned) R.string.st_ClipboardScreen_action_unpin
                                else R.string.st_ClipboardScreen_action_pin,
                            ),
                            modifier = Modifier.weight(1f),
                            selected = entry.isPinned,
                            onClick = { viewModel.togglePin(entry) },
                        )
                        ActionTile(
                            icon = if (entry.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            label = stringResource(
                                if (entry.isLocked) R.string.st_ClipboardScreen_action_unlock
                                else R.string.st_ClipboardScreen_action_lock,
                            ),
                            modifier = Modifier.weight(1f),
                            selected = entry.isLocked,
                            onClick = onToggleLock,
                        )
                    }
                    if (!entry.passwordVaultMatch) {
                        ActionTile(
                            icon = if (entry.isSensitive) Icons.Rounded.Shield else Icons.Rounded.Security,
                            label = stringResource(
                                if (entry.isSensitive) R.string.st_ClipboardScreen_action_unmark_sensitive
                                else R.string.st_ClipboardScreen_action_mark_sensitive,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            selected = entry.isSensitive,
                            onClick = { viewModel.setSensitive(entry, !entry.isSensitive) },
                        )
                    }
                    if (!masked &&
                        entry.type in setOf(ClipboardType.LINK, ClipboardType.URL, ClipboardType.SOCIAL)
                    ) {
                        ActionTile(
                            icon = Icons.Rounded.Link,
                            label = stringResource(R.string.st_ClipboardScreen_action_open_link),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenLink,
                        )
                    }
                    if (entry.content.isNotBlank() && !masked) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionTile(
                                icon = Icons.AutoMirrored.Rounded.Notes,
                                label = stringResource(R.string.st_ClipboardScreen_action_notepad),
                                modifier = Modifier.weight(1f),
                                onClick = onOpenNotepad,
                            )
                            ActionTile(
                                icon = Icons.Rounded.TaskAlt,
                                label = stringResource(R.string.st_ClipboardScreen_action_task),
                                modifier = Modifier.weight(1f),
                                onClick = onCreateTask,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionTile(
                                icon = Icons.Rounded.QrCode,
                                label = stringResource(R.string.st_ClipboardScreen_action_qr),
                                modifier = Modifier.weight(1f),
                                onClick = onShowQr,
                            )
                            ActionTile(
                                icon = Icons.Rounded.Lock,
                                label = stringResource(R.string.st_ClipboardScreen_action_encrypt),
                                modifier = Modifier.weight(1f),
                                onClick = onEncrypt,
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (entry.deletedAt != null) {
                        FilledTonalButton(
                            onClick = { viewModel.restoreEntry(entry); onDismiss() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.st_ClipboardScreen_action_restore))
                        }
                        Button(
                            onClick = { viewModel.deleteForever(entry); onDismiss() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.st_ClipboardScreen_action_delete_forever))
                        }
                    } else {
                        Button(
                            onClick = { viewModel.deleteEntry(entry); onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.st_ClipboardScreen_action_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSection(
    entry: ClipboardEntry,
    running: Boolean,
    configured: Boolean,
    onRun: () -> Unit,
) {
    SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_ai))
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = when {
                        entry.aiState == ClipAiState.FAILED -> stringResource(R.string.st_ClipboardScreen_ai_failed)
                        entry.aiState == ClipAiState.DISABLED -> stringResource(R.string.st_ClipboardScreen_ai_disabled_state)
                        entry.aiState == ClipAiState.RUNNING -> stringResource(R.string.st_ClipboardScreen_ai_running)
                        entry.isAiProcessed -> stringResource(R.string.st_ClipboardScreen_ai_done)
                        else -> stringResource(R.string.st_ClipboardScreen_ai_idle)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRun, enabled = configured) {
                        Text(stringResource(R.string.st_ClipboardScreen_action_ai))
                    }
                }
            }
            entry.summary?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            entry.aiError?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!configured) {
                Text(
                    text = stringResource(R.string.st_ClipboardScreen_ai_not_configured_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: ClipboardAttachment,
    viewModel: ClipboardViewModel,
    onOpen: () -> Unit,
) {
    val isImage = attachment.mimeType?.startsWith("image/") == true
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isImage) {
                AttachmentThumbnail(
                    attachment = attachment,
                    viewModel = viewModel,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName ?: stringResource(R.string.st_ClipboardScreen_attachment_file),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Formatter.formatShortFileSize(LocalContext.current, attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpen) {
                Icon(
                    Icons.Rounded.FileOpen,
                    contentDescription = stringResource(R.string.st_ClipboardScreen_action_view_attachment),
                )
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Onboarding ────────────────────────────────────────────────────────────

@Composable
private fun OnboardingSheet(
    state: ClipboardUiState,
    onDismiss: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    onGrantAppOp: () -> Unit,
    onInstallShizuku: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Rounded.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.st_ClipboardScreen_onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.st_ClipboardScreen_onboarding_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.stats.activeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = stringResource(R.string.st_ClipboardScreen_onboarding_existing),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            OnboardingStep(
                number = 1,
                title = stringResource(R.string.st_ClipboardScreen_onboarding_step1_title),
                body = stringResource(R.string.st_ClipboardScreen_onboarding_step1_body),
            )
            OnboardingStep(
                number = 2,
                title = stringResource(R.string.st_ClipboardScreen_onboarding_step2_title),
                body = stringResource(R.string.st_ClipboardScreen_onboarding_step2_body),
            )
            OnboardingStep(
                number = 3,
                title = stringResource(R.string.st_ClipboardScreen_onboarding_step3_title),
                body = stringResource(R.string.st_ClipboardScreen_onboarding_step3_body),
            )
            Text(
                text = stringResource(R.string.st_ClipboardScreen_onboarding_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.gate.capabilities.shizukuInstalled) {
                Button(onClick = onInstallShizuku, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_install_shizuku))
                }
            } else if (!state.gate.capabilities.shizukuAuthorized) {
                Button(onClick = onAuthorizeShizuku, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_authorize))
                }
            }
            if (state.gate.capabilities.shizukuAuthorized && !state.gate.capabilities.appOpAllowed) {
                OutlinedButton(onClick = onGrantAppOp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_grant_appop))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_ClipboardScreen_onboarding_skip))
                }
                Button(onClick = onOpenStatus, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_ClipboardScreen_onboarding_continue))
                }
            }
        }
    }
}

@Composable
private fun OnboardingStep(number: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsSheet(
    state: ClipboardUiState,
    viewModel: ClipboardViewModel,
    onDismiss: () -> Unit,
    onShowStatus: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.st_ClipboardScreen_settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_monitoring),
                    body = stringResource(R.string.st_ClipboardScreen_settings_monitoring_desc),
                    checked = state.settings.monitoring,
                    onCheckedChange = viewModel::setMonitoringEnabled,
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_ai),
                    body = stringResource(R.string.st_ClipboardScreen_settings_ai_desc),
                    checked = state.settings.aiEnabled,
                    onCheckedChange = viewModel::setClipboardAiEnabled,
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_auto_ai),
                    body = stringResource(R.string.st_ClipboardScreen_settings_auto_ai_desc),
                    checked = state.settings.autoAi,
                    enabled = state.settings.aiEnabled && !state.settings.offline,
                    onCheckedChange = viewModel::setAutoAiEnabled,
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_sensitive),
                    body = stringResource(R.string.st_ClipboardScreen_settings_sensitive_desc),
                    checked = state.settings.excludeSensitive,
                    onCheckedChange = viewModel::setExcludeSensitive,
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_biometric),
                    body = stringResource(R.string.st_ClipboardScreen_settings_biometric_desc),
                    checked = state.settings.biometricLock,
                    onCheckedChange = viewModel::setBiometricLock,
                )
            }
            item {
                StepperRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_retention),
                    value = if (state.settings.retentionDays == 0) {
                        stringResource(R.string.st_ClipboardScreen_retention_forever)
                    } else {
                        pluralStringResource(
                            R.plurals.st_ClipboardScreen_retention_days,
                            state.settings.retentionDays,
                            state.settings.retentionDays,
                        )
                    },
                    body = stringResource(R.string.st_ClipboardScreen_settings_retention_desc),
                    onDecrease = { viewModel.setRetentionDays((state.settings.retentionDays - 1).coerceAtLeast(0)) },
                    onIncrease = { viewModel.setRetentionDays((state.settings.retentionDays + 1).coerceAtMost(365)) },
                )
            }
            item {
                StepperRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_item_quota),
                    value = stringResource(R.string.st_ClipboardScreen_megabytes, state.settings.itemQuotaMb),
                    body = stringResource(R.string.st_ClipboardScreen_settings_item_quota_desc),
                    onDecrease = { viewModel.setItemQuotaMb((state.settings.itemQuotaMb - 5).coerceAtLeast(1)) },
                    onIncrease = { viewModel.setItemQuotaMb((state.settings.itemQuotaMb + 5).coerceAtMost(200)) },
                )
            }
            item {
                StepperRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_total_quota),
                    value = stringResource(R.string.st_ClipboardScreen_megabytes, state.settings.totalQuotaMb),
                    body = stringResource(R.string.st_ClipboardScreen_settings_total_quota_desc),
                    onDecrease = { viewModel.setTotalQuotaMb((state.settings.totalQuotaMb - 25).coerceAtLeast(25)) },
                    onIncrease = { viewModel.setTotalQuotaMb((state.settings.totalQuotaMb + 25).coerceAtMost(5_000)) },
                )
            }
            item {
                StepperRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_max_entries),
                    value = state.settings.maxEntries.toString(),
                    body = stringResource(R.string.st_ClipboardScreen_settings_max_entries_desc),
                    onDecrease = { viewModel.setMaxEntries((state.settings.maxEntries - 50).coerceAtLeast(50)) },
                    onIncrease = { viewModel.setMaxEntries((state.settings.maxEntries + 50).coerceAtMost(5_000)) },
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            text = stringResource(R.string.st_ClipboardScreen_settings_backup_warning),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowStatus, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.st_ClipboardScreen_action_status))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.resetOnboarding()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.st_ClipboardScreen_settings_reset_onboarding))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    body: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecrease, modifier = Modifier.weight(1f)) {
                    Text("−")
                }
                OutlinedButton(onClick = onIncrease, modifier = Modifier.weight(1f)) {
                    Text("+")
                }
            }
        }
    }
}

// ── Status / diagnostics ──────────────────────────────────────────────────

@Composable
private fun StatusSheet(
    state: ClipboardUiState,
    onDismiss: () -> Unit,
    viewModel: ClipboardViewModel,
    onOpenAppDetails: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val status = state.gate.status
    val caps = state.gate.capabilities
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.st_ClipboardScreen_status_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            StatusBanner(status)
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_shizuku_installed),
                caps.shizukuInstalled,
            )
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_shizuku_running),
                caps.shizukuRunning,
            )
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_shizuku_authorized),
                caps.shizukuAuthorized,
            )
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_appop),
                caps.appOpAllowed,
            )
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_monitoring),
                state.settings.monitoring,
            )
            HorizontalDivider()
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_active_clips),
                state.stats.activeCount.toString(),
            )
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_trash),
                state.stats.trashedCount.toString(),
            )
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_sensitive),
                state.stats.sensitiveCount.toString(),
            )
            StatusRow(
                stringResource(R.string.st_ClipboardScreen_status_storage),
                stringResource(
                    R.string.st_ClipboardScreen_status_quota_used,
                    Formatter.formatShortFileSize(LocalContext.current, state.stats.blobBytes),
                    Formatter.formatShortFileSize(
                        LocalContext.current,
                        state.stats.totalQuotaMb.toLong() * 1024L * 1024L,
                    ),
                ),
            )
            if (state.stats.totalQuotaMb > 0) {
                LinearProgressIndicator(
                    progress = {
                        (state.stats.blobBytes.toFloat() /
                            (state.stats.totalQuotaMb.toLong() * 1024f * 1024f)).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!caps.shizukuInstalled) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(viewModel.shizukuInstallIntent())
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_install_shizuku))
                }
            } else if (!caps.shizukuAuthorized) {
                Button(
                    onClick = viewModel::requestShizukuAuthorization,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_authorize))
                }
            } else {
                if (!caps.appOpAllowed) {
                    OutlinedButton(
                        onClick = viewModel::grantClipboardAppOp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.st_ClipboardScreen_action_grant_appop))
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::revokeClipboardAppOp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.st_ClipboardScreen_action_revoke_appop))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.refreshGate(); viewModel.refreshStats() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.st_ClipboardScreen_action_refresh))
                }
                OutlinedButton(onClick = onOpenAppDetails, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_ClipboardScreen_status_app_details))
                }
            }
            Text(
                text = stringResource(R.string.st_ClipboardScreen_status_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBanner(status: ClipboardGate.Status) {
    val (label, icon, color) = when (status) {
        ClipboardGate.Status.ACTIVE_SHIZUKU -> Triple(
            stringResource(R.string.st_ClipboardScreen_status_active_shizuku),
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.primary,
        )
        ClipboardGate.Status.ACTIVE_APP_OP -> Triple(
            stringResource(R.string.st_ClipboardScreen_status_active_appop),
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.primary,
        )
        ClipboardGate.Status.FOREGROUND_ONLY -> Triple(
            stringResource(R.string.st_ClipboardScreen_status_foreground),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.tertiary,
        )
        ClipboardGate.Status.PAUSED -> Triple(
            stringResource(R.string.st_ClipboardScreen_status_paused),
            Icons.Rounded.Info,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClipboardGate.Status.SETUP_REQUIRED -> Triple(
            stringResource(R.string.st_ClipboardScreen_status_setup),
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.error,
        )
    }
    ExpressiveStatePill(text = label, icon = icon, color = color, isFilled = true)
}

@Composable
private fun StatusRow(label: String, value: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (value) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ── Shared small pieces ───────────────────────────────────────────────────

@Composable
private fun InfoBanner(
    icon: ImageVector,
    title: String,
    color: Color,
    action: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(20.dp)
                    .size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_ClipboardScreen_action_cancel))
            }
        },
    )
}

// ── Mapping / formatting helpers ──────────────────────────────────────────

private fun typeIcon(type: String): ImageVector = when (type.uppercase()) {
    ClipboardType.LINK, ClipboardType.URL -> Icons.Rounded.Link
    ClipboardType.SOCIAL -> Icons.Rounded.Public
    ClipboardType.EMAIL -> Icons.Rounded.Email
    ClipboardType.PHONE -> Icons.Rounded.Phone
    ClipboardType.COLOR -> Icons.Rounded.Palette
    ClipboardType.CODE -> Icons.Rounded.Code
    ClipboardType.OTP -> Icons.Rounded.Password
    ClipboardType.SECRET -> Icons.Rounded.Key
    ClipboardType.CARD -> Icons.Rounded.CreditCard
    ClipboardType.CRYPTO -> Icons.Rounded.CurrencyBitcoin
    ClipboardType.ADDRESS -> Icons.Rounded.LocationOn
    ClipboardType.MATH, ClipboardType.MATHS -> Icons.Rounded.Calculate
    ClipboardType.PERSONAL -> Icons.Rounded.Person
    ClipboardType.IMAGE -> Icons.Rounded.Image
    ClipboardType.FILE -> Icons.AutoMirrored.Rounded.InsertDriveFile
    ClipboardType.MULTI -> Icons.Rounded.Collections
    else -> Icons.AutoMirrored.Rounded.Notes
}

@Composable
private fun typeLabel(type: String): String = stringResource(
    when (type.uppercase()) {
        ClipboardType.LINK, ClipboardType.URL -> R.string.st_ClipboardScreen_type_link
        ClipboardType.SOCIAL -> R.string.st_ClipboardScreen_type_social
        ClipboardType.EMAIL -> R.string.st_ClipboardScreen_type_email
        ClipboardType.PHONE -> R.string.st_ClipboardScreen_type_phone
        ClipboardType.COLOR -> R.string.st_ClipboardScreen_type_color
        ClipboardType.CODE -> R.string.st_ClipboardScreen_type_code
        ClipboardType.OTP -> R.string.st_ClipboardScreen_type_otp
        ClipboardType.SECRET -> R.string.st_ClipboardScreen_type_secret
        ClipboardType.CARD -> R.string.st_ClipboardScreen_type_card
        ClipboardType.CRYPTO -> R.string.st_ClipboardScreen_type_crypto
        ClipboardType.ADDRESS -> R.string.st_ClipboardScreen_type_address
        ClipboardType.MATH, ClipboardType.MATHS -> R.string.st_ClipboardScreen_type_math
        ClipboardType.PERSONAL -> R.string.st_ClipboardScreen_type_personal
        ClipboardType.IMAGE -> R.string.st_ClipboardScreen_type_image
        ClipboardType.FILE -> R.string.st_ClipboardScreen_type_file
        ClipboardType.MULTI -> R.string.st_ClipboardScreen_type_multi
        else -> R.string.st_ClipboardScreen_type_text
    },
)

@Composable
private fun sensitivityLabel(entry: ClipboardEntry): String = stringResource(
    when {
        entry.sensitivityReason == "PASSWORD_VAULT" -> R.string.st_ClipboardScreen_sensitive_vault
        entry.sensitivityReason == "OTP" -> R.string.st_ClipboardScreen_sensitive_otp
        entry.sensitivityReason == "CARD" -> R.string.st_ClipboardScreen_sensitive_card
        entry.sensitivityReason == "LABEL" -> R.string.st_ClipboardScreen_sensitive_password
        entry.sensitivityReason == "EXTRA" -> R.string.st_ClipboardScreen_sensitive_extra
        entry.sensitivityReason == null && entry.isLocked -> R.string.st_ClipboardScreen_badge_locked
        else -> R.string.st_ClipboardScreen_sensitive_secret
    },
)

@Composable
private fun relativeTime(timestamp: Long): String = DateUtils.getRelativeTimeSpanString(
    timestamp,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE,
).toString()

private fun sourceLabel(entry: ClipboardEntry): String {
    val source = entry.sourceApp?.takeIf { it.isNotBlank() }
        ?: entry.source?.takeIf { it.isNotBlank() }
        ?: return ""
    return if (source.contains('.')) source.substringAfterLast('.') else source
}

private fun openLink(context: Context, content: String) {
    val uri = runCatching { Uri.parse(content.trim()) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, R.string.st_ClipboardScreen_message_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun messageText(context: Context, event: ClipboardEvent.Message): String {
    val count = event.count
    return when (event.type) {
        ClipboardMessage.SAVED -> context.getString(R.string.st_ClipboardScreen_message_saved)
        ClipboardMessage.BUMPED -> context.getString(R.string.st_ClipboardScreen_message_bumped)
        ClipboardMessage.EMPTY -> context.getString(R.string.st_ClipboardScreen_message_empty)
        ClipboardMessage.SENSITIVE_SKIPPED -> context.getString(R.string.st_ClipboardScreen_message_sensitive_skipped)
        ClipboardMessage.TOO_LARGE -> context.getString(R.string.st_ClipboardScreen_message_too_large)
        ClipboardMessage.QUOTA -> context.getString(R.string.st_ClipboardScreen_message_quota)
        ClipboardMessage.FAILED -> context.getString(R.string.st_ClipboardScreen_message_failed)
        ClipboardMessage.COPIED -> context.getString(R.string.st_ClipboardScreen_message_copied)
        ClipboardMessage.SELECTION_COPIED -> context.resources.getQuantityString(
            R.plurals.st_ClipboardScreen_message_selection_copied,
            count,
            count,
        )
        ClipboardMessage.SHARED -> context.getString(R.string.st_ClipboardScreen_message_shared)
        ClipboardMessage.PINNED -> context.getString(R.string.st_ClipboardScreen_message_pinned)
        ClipboardMessage.UNPINNED -> context.getString(R.string.st_ClipboardScreen_message_unpinned)
        ClipboardMessage.LOCKED -> context.getString(R.string.st_ClipboardScreen_message_locked)
        ClipboardMessage.UNLOCKED -> context.getString(R.string.st_ClipboardScreen_message_unlocked)
        ClipboardMessage.MARKED_SENSITIVE -> context.getString(R.string.st_ClipboardScreen_message_marked_sensitive)
        ClipboardMessage.MARKED_NOT_SENSITIVE -> context.getString(R.string.st_ClipboardScreen_message_marked_not_sensitive)
        ClipboardMessage.DELETED -> if (count > 1) {
            context.getString(R.string.st_ClipboardScreen_message_deleted_many, count)
        } else {
            context.getString(R.string.st_ClipboardScreen_message_deleted)
        }
        ClipboardMessage.DELETED_FOREVER -> context.getString(R.string.st_ClipboardScreen_message_deleted_forever)
        ClipboardMessage.RESTORED -> context.getString(R.string.st_ClipboardScreen_message_restored)
        ClipboardMessage.TRASH_EMPTY -> context.getString(R.string.st_ClipboardScreen_message_trash_empty)
        ClipboardMessage.CLEARED -> context.getString(R.string.st_ClipboardScreen_message_cleared)
        ClipboardMessage.AI_DONE -> context.getString(R.string.st_ClipboardScreen_message_ai_done)
        ClipboardMessage.AI_SENSITIVE -> context.getString(R.string.st_ClipboardScreen_message_ai_sensitive)
        ClipboardMessage.AI_DISABLED -> context.getString(R.string.st_ClipboardScreen_message_ai_disabled)
        ClipboardMessage.AI_NOT_CONFIGURED -> context.getString(R.string.st_ClipboardScreen_message_ai_not_configured)
        ClipboardMessage.AI_FAILED -> context.getString(R.string.st_ClipboardScreen_message_ai_failed)
        ClipboardMessage.ONBOARDING_DONE -> context.getString(R.string.st_ClipboardScreen_message_onboarding_done)
        ClipboardMessage.APP_OP_GRANTED -> context.getString(R.string.st_ClipboardScreen_message_appop_granted)
        ClipboardMessage.APP_OP_FAILED -> context.getString(R.string.st_ClipboardScreen_message_appop_failed)
        ClipboardMessage.APP_OP_REVOKED -> context.getString(R.string.st_ClipboardScreen_message_appop_revoked)
        ClipboardMessage.SHIZUKU_GRANTED -> context.getString(R.string.st_ClipboardScreen_message_shizuku_granted)
        ClipboardMessage.SHIZUKU_DENIED -> context.getString(R.string.st_ClipboardScreen_message_shizuku_denied)
        ClipboardMessage.SHIZUKU_NOT_RUNNING -> context.getString(R.string.st_ClipboardScreen_message_shizuku_not_running)
        ClipboardMessage.ALREADY_AUTHORIZED -> context.getString(R.string.st_ClipboardScreen_message_already_authorized)
        ClipboardMessage.SANDBOX -> context.getString(R.string.st_ClipboardScreen_message_sandbox)
        ClipboardMessage.ATTACHMENT_FAILED -> context.getString(R.string.st_ClipboardScreen_message_attachment_failed)
        ClipboardMessage.NOTHING_TO_COPY -> context.getString(R.string.st_ClipboardScreen_message_nothing_to_copy)
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
