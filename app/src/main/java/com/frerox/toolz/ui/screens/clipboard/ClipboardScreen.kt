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
    ExperimentalLayoutApi::class,
)

package com.frerox.toolz.ui.screens.clipboard

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FilterList
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.clipboard.ClipAiState
import com.frerox.toolz.data.clipboard.ClipboardAttachment
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardFilter
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.clipboard.ClipboardSource
import com.frerox.toolz.data.clipboard.ClipboardType
import com.frerox.toolz.service.PurgeShotDetector
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveLoadingWheel
import com.frerox.toolz.ui.components.ExpressivePulseIndicator
import com.frerox.toolz.ui.components.ExpressiveRefreshIndicator
import com.frerox.toolz.ui.components.ExpressiveSearchField
import com.frerox.toolz.ui.components.ExpressiveStatePill
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.security.BiometricPromptUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

// ── Design tokens ─────────────────────────────────────────────────────────
//
// Shape: grouped content is "segmented". Items in a group sit 2dp apart; the
// first and last carry large outer corners, the middle ones small corners.
// A selected item morphs to a large rounded shape on all four corners.
//
// Motion: M3 Expressive uses two spring families. Spatial springs (position,
// size, shape) have a little bounce; effects springs (colour, alpha) never
// overshoot.

private val ScreenGutter = 16.dp
private val OuterRadius = 20.dp
private val InnerRadius = 6.dp
private val SelectedRadius = 28.dp
private val SegmentGap = 2.dp
private val ControlHeight = 40.dp
private val CardPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)

private fun <T> spatialSpring(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 380f)
private fun <T> effectsSpring(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
private fun <T> playfulSpring(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 520f)

private fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) OuterRadius else InnerRadius
    val bottom = if (index == count - 1) OuterRadius else InnerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val LinkTypes = setOf(ClipboardType.LINK, ClipboardType.URL, ClipboardType.SOCIAL)

/** A snackbar request. Ordinary messages replace each other; undoable ones are never stolen. */
private data class TransientMessage(
    val text: String,
    val undoable: Boolean = false,
)

/** Retention steps, ascending. 0 means "keep forever" and sorts last. */
private val RetentionPresets = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365, 0)

private fun stepRetention(current: Int, up: Boolean): Int {
    fun rank(days: Int) = if (days == 0) Int.MAX_VALUE else days
    val sorted = RetentionPresets.sortedBy(::rank)
    return if (up) {
        sorted.firstOrNull { rank(it) > rank(current) } ?: current
    } else {
        sorted.lastOrNull { rank(it) < rank(current) } ?: current
    }
}

/**
 * Toolz Clipboard — history, trash, detail sheet, onboarding, capture
 * diagnostics and settings.
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Relative timestamps refresh every 30 s so an open screen never shows
    // "just now" for a clip captured hours ago.
    val now = rememberNow()
    var refreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    // The FAB collapses to an icon once the list scrolls away from the top.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // Screenshot ingestion reads MediaStore. Without this runtime permission
    // the observer and scan silently find nothing, so surface and request it.
    var mediaPermissionGranted by remember {
        mutableStateOf(PurgeShotDetector.hasMediaPermission(context))
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Re-read the real grant state: on Android 14 a partial photo grant can
        // report `granted` while MediaStore still cannot read every image.
        mediaPermissionGranted = PurgeShotDetector.hasMediaPermission(context)
        if (granted || mediaPermissionGranted) viewModel.onMediaPermissionGranted()
    }
    fun requestMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        mediaPermissionLauncher.launch(permission)
    }

    // Refresh status, stats, clipboard and permission state whenever the screen
    // returns to the foreground (Shizuku settings, another app, share sheet).
    DisposableEffect(lifecycleOwner, viewModel, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
                mediaPermissionGranted = PurgeShotDetector.hasMediaPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // Modal bottom sheets live in their own window above the Scaffold, so the
    // regular snackbar host is hidden behind the scrim. Events raised while a
    // sheet is open are shown by a second host rendered inside that sheet.
    val sheetSnackbarHostState = remember { SnackbarHostState() }
    // Unbounded queue with a single consumer below. It must not be conflated:
    // an undoable delete prompt could be overwritten by an ordinary "copied"
    // message and silently lose the only way to undo.
    val snackbarQueue = remember { Channel<TransientMessage>(Channel.UNLIMITED) }
    val haptic = rememberToolzHapticFeedback()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
    var showDeleteForeverDialog by rememberSaveable { mutableStateOf(false) }
    // A single-row "delete forever" is irreversible too, so it goes through
    // the same confirmation step as the bulk action.
    var pendingDeleteForeverId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showOverflow by rememberSaveable { mutableStateOf(false) }
    // Confirmed copy flash, advanced only by the ViewModel's COPIED event so a
    // cancelled biometric prompt or failed write never shows a false check.
    var copyFlashEntryId by remember { mutableStateOf<Int?>(null) }
    var copyFlashToken by remember { mutableStateOf(0) }
    LaunchedEffect(copyFlashEntryId, copyFlashToken) {
        if (copyFlashEntryId != null) {
            delay(1_400)
            copyFlashEntryId = null
        }
    }

    val detailEntry = remember(detailId, state.entries, state.trashed) {
        detailId?.let { id ->
            state.entries.firstOrNull { it.id == id } ?: state.trashed.firstOrNull { it.id == id }
        }
    }

    val anySheetOpen = showOnboarding || showSettings || showStatus || detailEntry != null
    val anySheetOpenState by rememberUpdatedState(anySheetOpen)
    val undoLabelState by rememberUpdatedState(undoLabel)
    val shareLabelState by rememberUpdatedState(shareLabel)
    val shareFailedLabelState by rememberUpdatedState(shareFailedLabel)

    fun showTransient(message: String) {
        snackbarQueue.trySend(TransientMessage(message))
    }

    LaunchedEffect(state.loaded, state.settings.onboardingSeen) {
        if (state.loaded && !state.settings.onboardingSeen) {
            // Onboarding is the only sheet that may appear on its own. Close
            // anything else first so two bottom sheets can never stack.
            showSettings = false
            showStatus = false
            detailId = null
            showOnboarding = true
        }
    }

    LaunchedEffect(anySheetOpen) {
        // A snackbar that outlived its sheet must not reappear in the next one.
        if (!anySheetOpen) sheetSnackbarHostState.currentSnackbarData?.dismiss()
    }

    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        // Re-read the system clipboard, capture gate and storage stats together
        // so every counter agrees afterwards.
        viewModel.refreshClipboard(notify = false)
        viewModel.refreshGate()
        viewModel.refreshStats()
        delay(450)
        refreshing = false
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ClipboardEvent.Message -> {
                    if (event.type == ClipboardMessage.COPIED && event.entryId != null) {
                        copyFlashEntryId = event.entryId
                        copyFlashToken += 1
                    }
                    snackbarQueue.trySend(
                        TransientMessage(
                            text = messageText(context, event),
                            undoable = event.type == ClipboardMessage.DELETED,
                        ),
                    )
                }
                is ClipboardEvent.Share -> {
                    runCatching {
                        context.startActivity(Intent.createChooser(event.intent, shareLabelState))
                    }.onFailure {
                        snackbarQueue.trySend(TransientMessage(shareFailedLabelState))
                    }
                }
            }
        }
    }

    // Single consumer for feedback. Showing a snackbar suspends for its whole
    // timeout, so each message runs in its own child job: a newer message
    // cancels the previous one instead of waiting behind it. A visible
    // delete/undo prompt is never stolen by ordinary feedback.
    LaunchedEffect(Unit) {
        var active: Job? = null
        var activeUndoable = false
        for (message in snackbarQueue) {
            if (activeUndoable && !message.undoable) continue
            snackbarHostState.currentSnackbarData?.dismiss()
            sheetSnackbarHostState.currentSnackbarData?.dismiss()
            active?.cancel()
            activeUndoable = message.undoable
            val host = if (anySheetOpenState) sheetSnackbarHostState else snackbarHostState
            active = launch {
                val result = host.showSnackbar(
                    message = message.text,
                    actionLabel = if (message.undoable) undoLabelState else null,
                    withDismissAction = !message.undoable,
                    duration = if (message.undoable) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed && message.undoable) {
                    viewModel.undoDelete()
                }
                if (message.undoable) activeUndoable = false
            }
        }
    }

    fun runWithUnlock(entry: ClipboardEntry, action: () -> Unit) {
        if (!entry.isLocked) {
            action()
            return
        }
        // Already unlocked in this session: re-prompting for every follow-up
        // action would make a revealed clip feel broken.
        if (entry.id in state.revealedIds) {
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
            showTransient(biometricUnavailableLabel)
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
            onError = { _, message -> showTransient(message.toString()) },
        )
    }

    // Sheets handle back in their own window. This handler only runs for the
    // history screen, where back undoes one layer of navigation.
    BackHandler {
        when {
            state.isSelectionMode -> viewModel.clearSelection()
            state.showTrash -> viewModel.setShowTrash(false)
            else -> onBack()
        }
    }

    Scaffold(
        modifier = Modifier
            .toolzBackground()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = when {
                            state.isSelectionMode -> pluralStringResource(
                                R.plurals.st_ClipboardScreen_selected_count,
                                state.selection.size,
                                state.selection.size,
                            )
                            state.showTrash -> stringResource(
                                R.string.st_ClipboardScreen_trash_subtitle,
                                state.stats.trashedCount,
                            )
                            else -> stringResource(
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
                            when {
                                state.isSelectionMode -> viewModel.clearSelection()
                                state.showTrash -> viewModel.setShowTrash(false)
                                else -> onBack()
                            }
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
                        val allSelected = state.visibleEntries.isNotEmpty() &&
                                state.selection.size == state.visibleEntries.size
                        IconButton(
                            onClick = {
                                haptic.click()
                                if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                            },
                        ) {
                            Icon(
                                if (allSelected) Icons.Rounded.Close else Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(
                                    if (allSelected) R.string.st_ClipboardScreen_action_clear_selection
                                    else R.string.st_ClipboardScreen_action_select_all,
                                ),
                            )
                        }
                    } else {
                        if (!state.showTrash) {
                            BadgedBox(
                                badge = {
                                    if (state.stats.trashedCount > 0) {
                                        Badge {
                                            Text(
                                                if (state.stats.trashedCount > 99) {
                                                    "99+"
                                                } else {
                                                    state.stats.trashedCount.toString()
                                                },
                                            )
                                        }
                                    }
                                },
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.click()
                                        viewModel.setShowTrash(true)
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(
                                            R.string.st_ClipboardScreen_action_open_trash,
                                        ),
                                    )
                                }
                            }
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
                                    text = { Text(stringResource(R.string.st_ClipboardScreen_action_status)) },
                                    leadingIcon = { Icon(Icons.Rounded.Security, null) },
                                    onClick = {
                                        showOverflow = false
                                        showStatus = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (state.settings.monitoring) {
                                                    R.string.st_ClipboardScreen_action_pause_capture
                                                } else {
                                                    R.string.st_ClipboardScreen_action_resume_capture
                                                },
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (state.settings.monitoring) Icons.Rounded.Pause
                                            else Icons.Rounded.PlayArrow,
                                            null,
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.setMonitoringEnabled(!state.settings.monitoring)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.st_ClipboardScreen_action_capture_clipboard)) },
                                    leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
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
        bottomBar = {
            AnimatedVisibility(
                visible = state.isSelectionMode,
                enter = slideInVertically(animationSpec = spatialSpring()) { it } +
                        fadeIn(animationSpec = effectsSpring()),
                exit = slideOutVertically(animationSpec = spatialSpring()) { it } +
                        fadeOut(animationSpec = effectsSpring()),
            ) {
                SelectionActionBar(
                    state = state,
                    viewModel = viewModel,
                    onDeleteForever = { showDeleteForeverDialog = true },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !state.isSelectionMode && !state.showTrash,
                enter = fadeIn(effectsSpring()) + scaleIn(playfulSpring(), initialScale = 0.6f),
                exit = fadeOut(effectsSpring()) + scaleOut(effectsSpring(), targetScale = 0.6f),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!state.busy) {
                            haptic.click()
                            viewModel.pasteCurrent()
                        }
                    },
                    expanded = fabExpanded,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = {
                        if (state.busy) {
                            ExpressivePulseIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Rounded.ContentPaste, null)
                        }
                    },
                    text = { Text(stringResource(R.string.st_ClipboardScreen_action_capture)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NoticeHost(
                state = state,
                mediaPermissionGranted = mediaPermissionGranted,
                onRequestMediaPermission = ::requestMediaPermission,
                onResumeCapture = { viewModel.setMonitoringEnabled(true) },
                onOpenStatus = { showStatus = true },
            )

            SearchAndFilters(
                query = state.query,
                filter = state.filter,
                counts = state.filterCounts,
                onQueryChange = viewModel::onSearchQueryChanged,
                onFilterChange = viewModel::setFilter,
            )

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    haptic.tick()
                    refreshing = true
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = pullState,
                indicator = {
                    ExpressiveRefreshIndicator(
                        state = pullState,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                when {
                    !state.loaded -> LoadingState()
                    state.visibleEntries.isEmpty() && state.query.isNotBlank() -> {
                        EmptyState(
                            icon = Icons.Rounded.Search,
                            title = stringResource(R.string.st_ClipboardScreen_no_results),
                            body = stringResource(R.string.st_ClipboardScreen_no_results_body, state.query),
                            actionLabel = stringResource(R.string.st_ClipboardScreen_action_clear_search),
                            onAction = { viewModel.onSearchQueryChanged("") },
                        )
                    }
                    state.visibleEntries.isEmpty() && state.showTrash && state.trashed.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Rounded.Delete,
                            title = stringResource(R.string.st_ClipboardScreen_empty_trash_title),
                            body = stringResource(R.string.st_ClipboardScreen_empty_trash_body),
                            actionLabel = stringResource(R.string.st_ClipboardScreen_action_back_to_history),
                            onAction = { viewModel.setShowTrash(false) },
                        )
                    }
                    state.visibleEntries.isEmpty() -> {
                        if (state.filter != ClipboardFilter.ALL) {
                            val label = filterLabel(state.filter)
                            EmptyState(
                                icon = Icons.Rounded.FilterList,
                                title = stringResource(R.string.st_ClipboardScreen_empty_filter_title, label),
                                body = stringResource(R.string.st_ClipboardScreen_empty_filter_body, label),
                                actionLabel = stringResource(R.string.st_ClipboardScreen_action_show_all),
                                onAction = { viewModel.setFilter(ClipboardFilter.ALL) },
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Rounded.ContentPaste,
                                title = stringResource(R.string.st_ClipboardScreen_empty_title),
                                body = stringResource(R.string.st_ClipboardScreen_empty_body),
                                actionLabel = stringResource(R.string.st_ClipboardScreen_action_capture_clipboard),
                                onAction = { viewModel.pasteCurrent() },
                                secondaryLabel = stringResource(R.string.st_ClipboardScreen_action_settings),
                                onSecondaryAction = { showSettings = true },
                            )
                        }
                    }
                    else -> {
                        HistoryList(
                            state = state,
                            viewModel = viewModel,
                            listState = listState,
                            now = now,
                            copyFlashEntryId = copyFlashEntryId,
                            copyFlashToken = copyFlashToken,
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
                            onCopy = { entry -> runWithUnlock(entry) { viewModel.copyEntry(entry) } },
                            onPin = viewModel::togglePin,
                            onDelete = viewModel::deleteEntry,
                            onRestore = viewModel::restoreEntry,
                            onDeleteForever = { entry -> pendingDeleteForeverId = entry.id },
                        )
                    }
                }
            }
        }
    }

    if (showOnboarding) {
        OnboardingSheet(
            state = state,
            feedbackHostState = sheetSnackbarHostState,
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
            feedbackHostState = sheetSnackbarHostState,
            mediaPermissionGranted = mediaPermissionGranted,
            onRequestMediaPermission = ::requestMediaPermission,
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
            feedbackHostState = sheetSnackbarHostState,
            mediaPermissionGranted = mediaPermissionGranted,
            onRequestMediaPermission = ::requestMediaPermission,
            onOpenAppDetails = {
                runCatching { context.startActivity(viewModel.appDetailsIntent()) }
            },
        )
    }

    detailEntry?.let { entry ->
        val attachments = state.attachmentsByClip[entry.id].orEmpty()
        // Tool actions must never receive a `[image]`/`[file]` placeholder or
        // a bare attachment name as if it were real text.
        val toolText = viewModel.textForEntry(entry)
        ClipDetailSheet(
            entry = entry,
            attachments = attachments,
            revealed = entry.id in state.revealedIds,
            toolText = toolText,
            now = now,
            state = state,
            viewModel = viewModel,
            feedbackHostState = sheetSnackbarHostState,
            onDismiss = { detailId = null },
            onUnlock = { runWithUnlock(entry) { viewModel.markRevealed(entry.id) } },
            onCopy = { runWithUnlock(entry) { viewModel.copyEntry(entry) } },
            onShare = { runWithUnlock(entry) { viewModel.shareEntry(entry) } },
            onOpenNotepad = { toolText?.let { text -> runWithUnlock(entry) { onOpenNotepad(text) } } },
            onCreateTask = { toolText?.let { text -> runWithUnlock(entry) { onCreateTask(text) } } },
            onShowQr = { toolText?.let { text -> runWithUnlock(entry) { onShowQr(text) } } },
            onEncrypt = { toolText?.let { text -> runWithUnlock(entry) { onEncrypt(text) } } },
            onOpenLink = { toolText?.let { text -> runWithUnlock(entry) { openLink(context, text) } } },
            onToggleLock = {
                if (entry.isLocked) {
                    runWithUnlock(entry) { viewModel.setLocked(entry, false) }
                } else {
                    viewModel.setLocked(entry, true)
                }
            },
            onDeleteForever = {
                pendingDeleteForeverId = entry.id
                detailId = null
            },
            onOpenAttachment = { attachment ->
                runWithUnlock(entry) {
                    scope.launch {
                        val intent = viewModel.openAttachment(attachment)
                        if (intent == null) {
                            showTransient(attachmentFailedLabel)
                        } else {
                            runCatching { context.startActivity(intent) }.onFailure {
                                showTransient(openFailedLabel)
                            }
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

    if (showDeleteForeverDialog) {
        ConfirmDialog(
            title = stringResource(R.string.st_ClipboardScreen_delete_forever_confirm_title),
            body = stringResource(R.string.st_ClipboardScreen_delete_forever_confirm_body),
            confirmLabel = stringResource(R.string.st_ClipboardScreen_action_delete_forever),
            onConfirm = {
                showDeleteForeverDialog = false
                viewModel.deleteSelectedForever()
            },
            onDismiss = { showDeleteForeverDialog = false },
        )
    }

    pendingDeleteForeverId?.let { pendingId ->
        val pendingEntry = state.trashed.firstOrNull { it.id == pendingId }
            ?: state.entries.firstOrNull { it.id == pendingId }
        if (pendingEntry == null) {
            // The clip disappeared (e.g. retention ran) while the prompt was
            // open, so there is nothing left to confirm.
            pendingDeleteForeverId = null
        } else {
            ConfirmDialog(
                title = stringResource(R.string.st_ClipboardScreen_delete_forever_single_title),
                body = stringResource(R.string.st_ClipboardScreen_delete_forever_single_body),
                confirmLabel = stringResource(R.string.st_ClipboardScreen_action_delete_forever),
                onConfirm = {
                    pendingDeleteForeverId = null
                    viewModel.deleteForever(pendingEntry)
                },
                onDismiss = { pendingDeleteForeverId = null },
            )
        }
    }
}

// ── Notices, search and filters ───────────────────────────────────────────

/**
 * At most one notice is ever shown, in order of urgency: sandbox, setup,
 * capture needs attention, AI offline. Healthy capture takes no space at all;
 * the status sheet stays reachable from the overflow menu.
 */
@Composable
private fun NoticeHost(
    state: ClipboardUiState,
    mediaPermissionGranted: Boolean,
    onRequestMediaPermission: () -> Unit,
    onResumeCapture: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val status = state.gate.status
    val monitoringOff = !state.settings.monitoring
    val paused = monitoringOff || status == ClipboardGate.Status.PAUSED
    val foregroundOnly = status == ClipboardGate.Status.FOREGROUND_ONLY
    val missingMedia = state.settings.captureScreenshots && !mediaPermissionGranted
    val attentionVisible = state.loaded && !state.showTrash && !state.isSelectionMode &&
            (paused || foregroundOnly || missingMedia)
    val noticeModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = ScreenGutter, vertical = 4.dp)

    Box(Modifier.fillMaxWidth().animateContentSize(spatialSpring())) {
        when {
            state.sandboxActive -> Notice(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.st_ClipboardScreen_sandbox_banner),
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
                modifier = noticeModifier,
            )
            state.showSetupBanner -> Notice(
                icon = Icons.Rounded.WarningAmber,
                title = stringResource(R.string.st_ClipboardScreen_foreground_banner),
                action = stringResource(R.string.st_ClipboardScreen_action_finish_setup),
                container = scheme.errorContainer,
                content = scheme.onErrorContainer,
                onClick = onOpenStatus,
                modifier = noticeModifier,
            )
            attentionVisible -> {
                val calmPause = paused && !missingMedia
                Notice(
                    icon = if (calmPause) Icons.Rounded.Pause else Icons.Rounded.WarningAmber,
                    title = when {
                        paused -> stringResource(R.string.st_ClipboardScreen_capture_paused)
                        missingMedia -> stringResource(R.string.st_ClipboardScreen_capture_issue_title)
                        else -> stringResource(R.string.st_ClipboardScreen_capture_foreground_title)
                    },
                    subtitle = when {
                        missingMedia -> stringResource(R.string.st_ClipboardScreen_capture_missing_permission)
                        paused -> stringResource(
                            R.string.st_ClipboardScreen_overview_counts,
                            state.stats.activeCount,
                            state.stats.trashedCount,
                        )
                        else -> stringResource(R.string.st_ClipboardScreen_capture_foreground_subtitle)
                    },
                    action = stringResource(
                        when {
                            monitoringOff -> R.string.st_ClipboardScreen_action_resume_capture
                            missingMedia -> R.string.st_ClipboardScreen_action_grant_media
                            else -> R.string.st_ClipboardScreen_action_status
                        },
                    ),
                    container = if (calmPause) scheme.tertiaryContainer else scheme.errorContainer,
                    content = if (calmPause) scheme.onTertiaryContainer else scheme.onErrorContainer,
                    onClick = {
                        when {
                            monitoringOff -> onResumeCapture()
                            missingMedia -> onRequestMediaPermission()
                            else -> onOpenStatus()
                        }
                    },
                    modifier = noticeModifier,
                )
            }
            state.settings.offline && state.settings.aiEnabled -> Notice(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.st_ClipboardScreen_ai_offline),
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                modifier = noticeModifier,
            )
        }
    }
}

/** One solid tonal row: icon, message, optional trailing action label. */
@Composable
private fun Notice(
    icon: ImageVector,
    title: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = if (subtitle != null) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (action != null) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = container,
            contentColor = content,
        ) { body() }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = container,
            contentColor = content,
        ) { body() }
    }
}

@Composable
private fun SearchAndFilters(
    query: String,
    filter: ClipboardFilter,
    counts: Map<ClipboardFilter, Int>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ClipboardFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExpressiveSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenGutter),
            placeholder = { Text(stringResource(R.string.st_ClipboardScreen_search_placeholder)) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(effectsSpring()) + scaleIn(playfulSpring(), initialScale = 0.6f),
                    exit = fadeOut(effectsSpring()) + scaleOut(effectsSpring(), targetScale = 0.6f),
                ) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.st_ClipboardScreen_action_clear_search),
                        )
                    }
                }
            },
        )
        // Chips scroll edge to edge; the gutter lives in contentPadding so the
        // first chip aligns with the search field and the last never clips.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = ScreenGutter),
        ) {
            items(ClipboardFilter.entries, key = { it.name }) { item ->
                val count = counts[item] ?: 0
                val label = filterLabel(item)
                ExpressiveFilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = {
                        Text(
                            if (count > 0) {
                                stringResource(R.string.st_ClipboardScreen_filter_count, label, count)
                            } else {
                                label
                            },
                        )
                    },
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

// ── Selection bar ─────────────────────────────────────────────────────────

/**
 * A floating tonal bar rather than a full-width slab: it only takes the room
 * its actions need and every action keeps a visible label.
 */
@Composable
private fun SelectionActionBar(
    state: ClipboardUiState,
    viewModel: ClipboardViewModel,
    onDeleteForever: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val scheme = MaterialTheme.colorScheme
    val aiEligible = !state.showTrash &&
            state.settings.aiEnabled &&
            state.aiConfigured &&
            !state.settings.offline &&
            state.visibleEntries.any { candidate ->
                candidate.id in state.selection &&
                        candidate.isTextLike &&
                        !candidate.isSensitive &&
                        !candidate.isLocked
            }
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = ScreenGutter, vertical = 8.dp),
        color = scheme.surfaceContainerHighest,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .animateContentSize(spatialSpring()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showTrash) {
                SelectionAction(
                    icon = Icons.Rounded.Restore,
                    label = stringResource(R.string.st_ClipboardScreen_action_restore),
                    container = scheme.secondaryContainer,
                    content = scheme.onSecondaryContainer,
                    onClick = {
                        haptic.click()
                        viewModel.restoreSelected()
                    },
                )
                SelectionAction(
                    icon = Icons.Rounded.DeleteForever,
                    label = stringResource(R.string.st_ClipboardScreen_action_delete_forever),
                    container = scheme.errorContainer,
                    content = scheme.onErrorContainer,
                    onClick = {
                        haptic.click()
                        onDeleteForever()
                    },
                )
            } else {
                SelectionAction(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.st_ClipboardScreen_action_copy),
                    container = scheme.primaryContainer,
                    content = scheme.onPrimaryContainer,
                    onClick = {
                        haptic.click()
                        viewModel.copySelected()
                    },
                )
                if (aiEligible) {
                    SelectionAction(
                        icon = Icons.Rounded.AutoAwesome,
                        label = stringResource(R.string.st_ClipboardScreen_action_ai),
                        container = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer,
                        onClick = {
                            haptic.click()
                            viewModel.runAiSelected()
                        },
                    )
                }
                SelectionAction(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.st_ClipboardScreen_action_delete),
                    container = scheme.errorContainer,
                    content = scheme.onErrorContainer,
                    onClick = {
                        haptic.click()
                        viewModel.deleteSelected()
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.SelectionAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = ControlHeight)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── History list ──────────────────────────────────────────────────────────

@Composable
private fun HistoryList(
    state: ClipboardUiState,
    viewModel: ClipboardViewModel,
    listState: LazyListState,
    now: Long,
    copyFlashEntryId: Int?,
    copyFlashToken: Int,
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
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        item(key = "history_summary") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .animateItem(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.st_ClipboardScreen_results_count,
                        state.visibleEntries.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.isSelectionMode) {
                    Text(
                        text = stringResource(R.string.st_ClipboardScreen_selection_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        groups.forEach { group ->
            item(key = "header_${group.group.name}") {
                Text(
                    text = stringResource(
                        R.string.st_ClipboardScreen_filter_count,
                        groupLabel(group.group),
                        group.entries.size,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
                        .animateItem(),
                )
            }
            itemsIndexed(group.entries, key = { _, entry -> entry.id }) { index, entry ->
                ClipboardCard(
                    entry = entry,
                    index = index,
                    count = group.entries.size,
                    attachments = state.attachmentsByClip[entry.id].orEmpty(),
                    now = now,
                    selected = entry.id in state.selection,
                    selectionMode = state.isSelectionMode,
                    showTrash = state.showTrash,
                    aiEnabled = state.settings.aiEnabled,
                    aiRunning = entry.id in state.aiRunningIds,
                    copiedFlashToken = if (entry.id == copyFlashEntryId) copyFlashToken else 0,
                    onOpen = { onOpen(entry) },
                    onLongPress = { onLongPress(entry) },
                    onCopy = { onCopy(entry) },
                    onPin = { onPin(entry) },
                    onDelete = { onDelete(entry) },
                    onRestore = { onRestore(entry) },
                    onDeleteForever = { onDeleteForever(entry) },
                    viewModel = viewModel,
                    modifier = Modifier.animateItem(),
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

/**
 * One history row, part of a segmented group. Swipe right pins, swipe left
 * deletes (undoable). Selecting a row morphs its shape and turns the leading
 * badge into a check, so no separate checkbox is needed.
 */
@Composable
private fun ClipboardCard(
    entry: ClipboardEntry,
    index: Int,
    count: Int,
    attachments: List<ClipboardAttachment>,
    now: Long,
    selected: Boolean,
    selectionMode: Boolean,
    showTrash: Boolean,
    aiEnabled: Boolean,
    aiRunning: Boolean,
    copiedFlashToken: Int,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    viewModel: ClipboardViewModel,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberToolzHapticFeedback()
    val scheme = MaterialTheme.colorScheme
    val canSwipe = !selectionMode && !showTrash
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it != SwipeToDismissBoxValue.Settled },
        positionalThreshold = { it * 0.4f },
    )
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                haptic.tick()
                onDelete()
                // If the row survives (delete failed or the list is slow to
                // update), snap it back so it never stays stuck off-screen.
                dismissState.reset()
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                haptic.tick()
                onPin()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    // Shape morph: outer corners by position, all-round when selected.
    val topRadius by animateDpAsState(
        targetValue = when {
            selected -> SelectedRadius
            index == 0 -> OuterRadius
            else -> InnerRadius
        },
        animationSpec = spatialSpring(),
        label = "clipTopRadius",
    )
    val bottomRadius by animateDpAsState(
        targetValue = when {
            selected -> SelectedRadius
            index == count - 1 -> OuterRadius
            else -> InnerRadius
        },
        animationSpec = spatialSpring(),
        label = "clipBottomRadius",
    )
    val top = topRadius.coerceAtLeast(0.dp)
    val bottom = bottomRadius.coerceAtLeast(0.dp)
    val cardShape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)

    val copiedFlash = copiedFlashToken > 0
    val heroImage = attachments.firstOrNull { it.mimeType?.startsWith("image/") == true }

    // Sensitive/locked clips never reveal their content in the list, even if
    // already unlocked in the detail sheet: the list is visible to anyone
    // glancing at the screen, the sheet is the deliberate reveal.
    val masked = entry.isSensitive || entry.isLocked
    val sourceText = sourceLabel(entry)
    val itemCount = entry.attachmentCount.coerceAtLeast(attachments.size)
    val preview = when {
        masked -> when (entry.type) {
            ClipboardType.IMAGE -> stringResource(R.string.st_ClipboardScreen_attachment_image)
            ClipboardType.FILE -> stringResource(R.string.st_ClipboardScreen_attachment_file)
            ClipboardType.MULTI -> pluralStringResource(
                R.plurals.st_ClipboardScreen_multi_items,
                itemCount,
                itemCount,
            )
            // Even the first/last letters of a password or OTP are sensitive.
            else -> stringResource(R.string.st_ClipboardScreen_masked_title)
        }
        entry.type == ClipboardType.IMAGE -> stringResource(R.string.st_ClipboardScreen_attachment_image)
        entry.type == ClipboardType.FILE -> attachments.firstOrNull()?.fileName
            ?: entry.content.takeIf { it.isNotBlank() && it != "[file]" && it != "[image]" }
            ?: stringResource(R.string.st_ClipboardScreen_attachment_file)
        entry.type == ClipboardType.MULTI -> pluralStringResource(
            R.plurals.st_ClipboardScreen_multi_items,
            itemCount,
            itemCount,
        )
        else -> entry.content
    }

    val containerColor by animateColorAsState(
        when {
            copiedFlash -> scheme.primaryContainer
            selected -> scheme.secondaryContainer
            else -> scheme.surfaceContainerHigh
        },
        animationSpec = effectsSpring(),
        label = "clipCardColor",
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = canSwipe,
        enableDismissFromEndToStart = canSwipe,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val pinning = direction == SwipeToDismissBoxValue.StartToEnd
            val active = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val background by animateColorAsState(
                when {
                    direction == SwipeToDismissBoxValue.Settled -> Color.Transparent
                    !active -> scheme.surfaceContainerHighest
                    pinning -> scheme.primaryContainer
                    else -> scheme.errorContainer
                },
                animationSpec = effectsSpring(),
                label = "clipSwipeBg",
            )
            // The icon pops once the swipe passes the commit threshold.
            val iconScale by animateFloatAsState(
                targetValue = if (active) 1.2f else 0.9f,
                animationSpec = playfulSpring(),
                label = "clipSwipeIcon",
            )
            val contentColor = if (pinning) scheme.onPrimaryContainer else scheme.onErrorContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(background)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (pinning) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (pinning) Icons.Rounded.PushPin else Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                        )
                        Text(
                            text = stringResource(
                                if (pinning) R.string.st_ClipboardScreen_action_pin
                                else R.string.st_ClipboardScreen_action_delete,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                        )
                    }
                }
            }
        },
    ) {
        ExpressiveCard(
            onClick = {
                haptic.click()
                onOpen()
            },
            onLongClick = onLongPress,
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            containerColor = containerColor,
            border = null,
        ) {
            Column(
                modifier = Modifier
                    .padding(CardPadding)
                    .animateContentSize(spatialSpring()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LeadingBadge(type = entry.type, selected = selected, masked = masked)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = preview.ifBlank { stringResource(R.string.st_ClipboardScreen_empty) },
                            style = if (entry.type == ClipboardType.CODE) {
                                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            maxLines = if (entry.type == ClipboardType.CODE) 4 else 3,
                            overflow = TextOverflow.Ellipsis,
                            color = if (masked) scheme.onSurfaceVariant else scheme.onSurface,
                        )
                        if (aiRunning && !masked) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExpressivePulseIndicator(modifier = Modifier.size(12.dp))
                                Text(
                                    text = stringResource(R.string.st_ClipboardScreen_ai_running),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        val summary = entry.summary?.takeIf { it.isNotBlank() }
                        if (aiEnabled && !masked && summary != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(14.dp),
                                    tint = scheme.tertiary,
                                )
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (attachments.isNotEmpty() && !masked) {
                            // The hero preview already shows the first image;
                            // the strip shows the remaining attachments only.
                            val stripAttachments = if (heroImage != null) {
                                attachments.filter { it.id != heroImage.id }
                            } else {
                                attachments
                            }
                            if (stripAttachments.isNotEmpty()) {
                                AttachmentStrip(
                                    attachments = stripAttachments,
                                    viewModel = viewModel,
                                    onOpen = onOpen,
                                )
                            }
                        }
                        ClipMetaLine(
                            entry = entry,
                            sourceText = sourceText,
                            masked = masked,
                            now = now,
                        )
                    }
                    if (!selectionMode && !showTrash) {
                        CompactClipAction(
                            icon = if (copiedFlash) Icons.Rounded.CheckCircle else Icons.Rounded.ContentCopy,
                            copied = copiedFlash,
                            contentDescription = stringResource(
                                if (copiedFlash) R.string.st_ClipboardScreen_copied
                                else R.string.st_ClipboardScreen_action_copy,
                            ),
                            tint = if (copiedFlash) scheme.primary else scheme.onSurfaceVariant,
                            onClick = {
                                haptic.click()
                                onCopy()
                            },
                        )
                    }
                }
                if (heroImage != null && !masked) {
                    ClipHeroPreview(
                        attachment = heroImage,
                        viewModel = viewModel,
                        onOpen = onOpen,
                    )
                }
                if (showTrash && !selectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        ActionPill(
                            icon = Icons.Rounded.Restore,
                            label = stringResource(R.string.st_ClipboardScreen_action_restore),
                            container = scheme.secondaryContainer,
                            content = scheme.onSecondaryContainer,
                            onClick = { haptic.click(); onRestore() },
                        )
                        ActionPill(
                            icon = Icons.Rounded.DeleteForever,
                            label = stringResource(R.string.st_ClipboardScreen_action_delete_forever),
                            container = scheme.errorContainer,
                            content = scheme.onErrorContainer,
                            onClick = { haptic.click(); onDeleteForever() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Leading badge. Its corner radius morphs from a rounded square to a circle
 * and the glyph crossfades to a check when the row is selected.
 */
@Composable
private fun LeadingBadge(
    type: String,
    selected: Boolean,
    masked: Boolean,
    size: Dp = 40.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val radius by animateDpAsState(
        targetValue = if (selected) size / 2 else size * 0.3f,
        animationSpec = spatialSpring(),
        label = "badgeRadius",
    )
    val container by animateColorAsState(
        when {
            selected -> scheme.primary
            masked -> scheme.errorContainer
            else -> scheme.secondaryContainer
        },
        animationSpec = effectsSpring(),
        label = "badgeContainer",
    )
    val content by animateColorAsState(
        when {
            selected -> scheme.onPrimary
            masked -> scheme.onErrorContainer
            else -> scheme.onSecondaryContainer
        },
        animationSpec = effectsSpring(),
        label = "badgeContent",
    )
    val typeDescription = stringResource(R.string.st_ClipboardScreen_badge_type_desc, typeLabel(type))
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius.coerceAtLeast(0.dp)))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = selected, animationSpec = tween(150), label = "badgeGlyph") { isSelected ->
            Icon(
                if (isSelected) Icons.Rounded.Check else typeIcon(type),
                contentDescription = if (isSelected) null else typeDescription,
                modifier = Modifier.size(size * 0.5f),
                tint = content,
            )
        }
    }
}

/** Small tonal pill (36dp) for secondary row actions. */
@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClipHeroPreview(
    attachment: ClipboardAttachment,
    viewModel: ClipboardViewModel,
    onOpen: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = attachment.blobId,
    ) {
        value = viewModel.loadAttachmentBitmap(attachment)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "heroPreview") { loaded ->
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = stringResource(R.string.st_ClipboardScreen_attachment_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/**
 * One line of secondary context. Type is already conveyed by the leading
 * badge, so the line carries only what changes per clip: age, source and the
 * few states worth surfacing.
 */
@Composable
private fun ClipMetaLine(
    entry: ClipboardEntry,
    sourceText: String,
    masked: Boolean,
    now: Long,
) {
    val parts = buildList {
        add(relativeTime(entry.timestamp, now))
        if (sourceText.isNotBlank()) add(sourceText)
        if (entry.passwordVaultMatch) add(stringResource(R.string.st_ClipboardScreen_badge_vault))
        if (entry.isAiProcessed && !masked) add(stringResource(R.string.st_ClipboardScreen_badge_ai))
        entry.expiresAt?.let { expiresAt ->
            add(stringResource(R.string.st_ClipboardScreen_expires_on, relativeTime(expiresAt, now)))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (entry.isPinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = stringResource(R.string.st_ClipboardScreen_badge_pinned),
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (masked) {
            Icon(
                if (entry.isLocked) Icons.Rounded.Lock else Icons.Rounded.Shield,
                contentDescription = if (entry.isLocked) {
                    stringResource(R.string.st_ClipboardScreen_badge_locked)
                } else {
                    stringResource(R.string.st_ClipboardScreen_badge_sensitive)
                },
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = if (masked) {
                stringResource(R.string.st_ClipboardScreen_reveal_hint) + " · " + parts.first()
            } else {
                parts.joinToString(" · ")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * 48dp touch target so the row action stays comfortably tappable. The icon
 * crossfades with a small bounce when a copy is confirmed.
 */
@Composable
private fun CompactClipAction(
    icon: ImageVector,
    copied: Boolean,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (copied) 1.15f else 1f,
        animationSpec = playfulSpring(),
        label = "copyIconScale",
    )
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Crossfade(targetState = icon, animationSpec = tween(160), label = "copyIcon") { current ->
            Icon(
                current,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
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
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpen),
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.clickable(onClick = onOpen),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                            style = MaterialTheme.typography.labelMedium,
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
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpen),
            ) {
                Text(
                    text = "+${attachments.size - 4}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
    val bitmap by produceState<ImageBitmap?>(null, attachment.blobId) {
        value = viewModel.loadAttachmentBitmap(attachment)?.asImageBitmap()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "thumb") { loaded ->
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = attachment.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Sheet scaffold ────────────────────────────────────────────────────────

/** Shared modal sheet: one container colour, one snackbar overlay. */
@Composable
private fun ToolzSheet(
    onDismiss: () -> Unit,
    feedbackHostState: SnackbarHostState,
    content: @Composable (SheetState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxWidth()) {
            content(sheetState)
            SheetSnackbarHost(feedbackHostState)
        }
    }
}

/** Bottom-anchored snackbar host used inside every modal sheet. */
@Composable
private fun BoxScope.SheetSnackbarHost(
    hostState: SnackbarHostState,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp),
    )
}

// ── Detail sheet ──────────────────────────────────────────────────────────

/**
 * Order is by how often each block is used: content, then actions, then the
 * AI summary, then metadata, with the destructive action last.
 */
@Composable
private fun ClipDetailSheet(
    entry: ClipboardEntry,
    attachments: List<ClipboardAttachment>,
    revealed: Boolean,
    toolText: String?,
    now: Long,
    state: ClipboardUiState,
    viewModel: ClipboardViewModel,
    feedbackHostState: SnackbarHostState,
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
    onDeleteForever: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = rememberToolzHapticFeedback()
    val scheme = MaterialTheme.colorScheme
    val masked = (entry.isSensitive || entry.isLocked) && !revealed
    val imageAttachments = remember(attachments) {
        attachments.filter { it.mimeType?.startsWith("image/") == true }
    }
    val showTextContent = entry.content.isNotBlank() && (entry.isTextLike || toolText != null)
    val isTrashed = entry.deletedAt != null

    ToolzSheet(onDismiss = onDismiss, feedbackHostState = feedbackHostState) { sheetState ->
        // Actions that leave the sheet slide it away first instead of cutting it.
        fun closeAnimated(after: () -> Unit) {
            after()
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) onDismiss()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeadingBadge(
                        type = entry.type,
                        selected = false,
                        masked = entry.isSensitive || entry.isLocked,
                        size = 48.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = typeLabel(entry.type),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = relativeTime(entry.timestamp, now),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (entry.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.st_ClipboardScreen_badge_pinned),
                            tint = scheme.primary,
                        )
                    }
                }
            }

            if (entry.isSensitive || entry.isLocked) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(spatialSpring()),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (entry.isLocked) Icons.Rounded.Lock else Icons.Rounded.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = sensitivityLabel(entry),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (entry.passwordVaultMatch) {
                                    Text(
                                        text = stringResource(R.string.st_ClipboardScreen_badge_vault_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else if (masked) {
                                    Text(
                                        text = stringResource(R.string.st_ClipboardScreen_reveal_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (masked) {
                                ToolzExpressiveTextButton(
                                    onClick = onUnlock,
                                    shape = SmallExpressiveShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(stringResource(R.string.st_ClipboardScreen_action_reveal))
                                }
                            } else {
                                ToolzExpressiveTextButton(
                                    onClick = {
                                        haptic.click()
                                        viewModel.hideRevealed(entry.id)
                                    },
                                    shape = SmallExpressiveShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(stringResource(R.string.st_ClipboardScreen_action_hide))
                                }
                            }
                        }
                    }
                }
            }

            if (imageAttachments.isNotEmpty() && !masked) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_images))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(imageAttachments, key = { it.id }) { image ->
                                AttachmentThumbnail(
                                    attachment = image,
                                    viewModel = viewModel,
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(156.dp)
                                        .clip(RoundedCornerShape(OuterRadius))
                                        .clickable { onOpenAttachment(image) },
                                )
                            }
                        }
                    }
                }
            }

            if (showTextContent && !masked) {
                item {
                    Column(
                        modifier = Modifier.animateContentSize(spatialSpring()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_content))
                        Surface(
                            shape = RoundedCornerShape(OuterRadius),
                            color = scheme.surfaceContainerHigh,
                        ) {
                            // A clip can hold up to 100k characters. Cap the
                            // visible area and let the content scroll instead
                            // of forcing one enormous item into the list.
                            Box(
                                modifier = Modifier
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = entry.content,
                                        style = if (entry.type == ClipboardType.CODE) {
                                            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                        } else {
                                            MaterialTheme.typography.bodyLarge
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (attachments.isNotEmpty() && !masked) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_attachments))
                        Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
                            attachments.forEachIndexed { index, attachment ->
                                AttachmentRow(
                                    attachment = attachment,
                                    viewModel = viewModel,
                                    shape = segmentShape(index, attachments.size),
                                    onOpen = { onOpenAttachment(attachment) },
                                )
                            }
                        }
                    }
                }
            }

            // Trashed clips cannot be copied, shared, pinned or reclassified
            // until they are restored; hide those actions instead of offering
            // buttons that would either fail or mutate a deleted row.
            if (!isTrashed) {
                item {
                    val showLink = !masked && toolText != null && entry.type in LinkTypes
                    val showTools = toolText != null && !masked
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_actions))
                        // Primary pair: the two things people actually do.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolzExpressiveButton(
                                onClick = onCopy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = ControlHeight),
                                shape = SmallExpressiveShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.st_ClipboardScreen_action_copy),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            ToolzTonalExpressiveButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = ControlHeight),
                                shape = SmallExpressiveShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.st_ClipboardScreen_action_share),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // State toggles are chips: selected means "on".
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ExpressiveFilterChip(
                                selected = entry.isPinned,
                                onClick = { haptic.tick(); viewModel.togglePin(entry) },
                                label = {
                                    Text(
                                        stringResource(
                                            if (entry.isPinned) R.string.st_ClipboardScreen_action_unpin
                                            else R.string.st_ClipboardScreen_action_pin,
                                        ),
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, null, Modifier.size(16.dp)) },
                            )
                            ExpressiveFilterChip(
                                selected = entry.isLocked,
                                onClick = { haptic.tick(); onToggleLock() },
                                label = {
                                    Text(
                                        stringResource(
                                            if (entry.isLocked) R.string.st_ClipboardScreen_action_unlock
                                            else R.string.st_ClipboardScreen_action_lock,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (entry.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                        null,
                                        Modifier.size(16.dp),
                                    )
                                },
                            )
                            if (!entry.passwordVaultMatch && !entry.isLocked) {
                                ExpressiveFilterChip(
                                    selected = entry.isSensitive,
                                    onClick = { haptic.tick(); viewModel.setSensitive(entry, !entry.isSensitive) },
                                    label = {
                                        Text(
                                            stringResource(
                                                if (entry.isSensitive) R.string.st_ClipboardScreen_action_unmark_sensitive
                                                else R.string.st_ClipboardScreen_action_mark_sensitive,
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (entry.isSensitive) Icons.Rounded.Shield else Icons.Rounded.Security,
                                            null,
                                            Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                        // Hand-offs to other tools are one-shot chips.
                        if (showLink || showTools) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (showLink) {
                                    ToolChip(
                                        Icons.Rounded.Link,
                                        stringResource(R.string.st_ClipboardScreen_action_open_link),
                                        onOpenLink,
                                    )
                                }
                                if (showTools) {
                                    ToolChip(
                                        Icons.AutoMirrored.Rounded.Notes,
                                        stringResource(R.string.st_ClipboardScreen_action_notepad),
                                        onOpenNotepad,
                                    )
                                    ToolChip(
                                        Icons.Rounded.TaskAlt,
                                        stringResource(R.string.st_ClipboardScreen_action_task),
                                        onCreateTask,
                                    )
                                    ToolChip(
                                        Icons.Rounded.QrCode,
                                        stringResource(R.string.st_ClipboardScreen_action_qr),
                                        onShowQr,
                                    )
                                    ToolChip(
                                        Icons.Rounded.Lock,
                                        stringResource(R.string.st_ClipboardScreen_action_encrypt),
                                        onEncrypt,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.settings.aiEnabled && entry.isTextLike &&
                !entry.isSensitive && !entry.isLocked
            ) {
                item {
                    AiSection(
                        entry = entry,
                        running = entry.id in state.aiRunningIds,
                        configured = state.aiConfigured,
                        offline = state.settings.offline,
                        onRun = { viewModel.runAi(entry) },
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_meta))
                    Surface(
                        shape = RoundedCornerShape(OuterRadius),
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                        R.string.st_ClipboardScreen_expires_relative_on,
                                        relativeTime(timestamp, now),
                                        android.text.format.DateFormat.getDateFormat(LocalContext.current)
                                            .format(Date(timestamp)),
                                    )
                                } ?: stringResource(R.string.st_ClipboardScreen_no_expiry),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isTrashed) {
                        ToolzTonalExpressiveButton(
                            onClick = { closeAnimated { viewModel.restoreEntry(entry) } },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = ControlHeight),
                            shape = SmallExpressiveShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.st_ClipboardScreen_action_restore),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        ToolzExpressiveButton(
                            onClick = onDeleteForever,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = ControlHeight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.errorContainer,
                                contentColor = scheme.onErrorContainer,
                            ),
                            shape = SmallExpressiveShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.st_ClipboardScreen_action_delete_forever),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        ToolzExpressiveButton(
                            onClick = { closeAnimated { viewModel.deleteEntry(entry) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ControlHeight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.errorContainer,
                                contentColor = scheme.onErrorContainer,
                            ),
                            shape = SmallExpressiveShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.st_ClipboardScreen_action_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
    )
}

@Composable
private fun AiSection(
    entry: ClipboardEntry,
    running: Boolean,
    configured: Boolean,
    offline: Boolean,
    onRun: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.st_ClipboardScreen_detail_ai))
        Surface(
            shape = RoundedCornerShape(OuterRadius),
            color = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .animateContentSize(spatialSpring()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = when {
                            entry.aiState == ClipAiState.FAILED -> stringResource(R.string.st_ClipboardScreen_ai_failed)
                            entry.aiState == ClipAiState.DISABLED -> stringResource(R.string.st_ClipboardScreen_ai_disabled_state)
                            entry.aiState == ClipAiState.RUNNING -> stringResource(R.string.st_ClipboardScreen_ai_running)
                            entry.isAiProcessed -> stringResource(R.string.st_ClipboardScreen_ai_done)
                            else -> stringResource(R.string.st_ClipboardScreen_ai_idle)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (running) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ToolzTonalExpressiveButton(
                            onClick = onRun,
                            enabled = configured && !offline,
                            shape = SmallExpressiveShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Text(stringResource(R.string.st_ClipboardScreen_action_ai))
                        }
                    }
                }
                entry.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                if (offline) {
                    Text(
                        text = stringResource(R.string.st_ClipboardScreen_ai_offline),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (!configured) {
                    Text(
                        text = stringResource(R.string.st_ClipboardScreen_ai_not_configured_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: ClipboardAttachment,
    viewModel: ClipboardViewModel,
    shape: Shape,
    onOpen: () -> Unit,
) {
    val isImage = attachment.mimeType?.startsWith("image/") == true
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isImage) {
                AttachmentThumbnail(
                    attachment = attachment,
                    viewModel = viewModel,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName ?: stringResource(R.string.st_ClipboardScreen_attachment_file),
                    style = MaterialTheme.typography.bodyMedium,
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ── Onboarding ────────────────────────────────────────────────────────────

@Composable
private fun OnboardingSheet(
    state: ClipboardUiState,
    feedbackHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    onGrantAppOp: () -> Unit,
    onInstallShizuku: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ToolzSheet(onDismiss = onDismiss, feedbackHostState = feedbackHostState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = scheme.onPrimaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.st_ClipboardScreen_onboarding_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.st_ClipboardScreen_onboarding_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (state.stats.activeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.st_ClipboardScreen_onboarding_existing),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
                OnboardingStep(
                    number = 1,
                    title = stringResource(R.string.st_ClipboardScreen_onboarding_step1_title),
                    body = stringResource(R.string.st_ClipboardScreen_onboarding_step1_body),
                    shape = segmentShape(0, 3),
                )
                OnboardingStep(
                    number = 2,
                    title = stringResource(R.string.st_ClipboardScreen_onboarding_step2_title),
                    body = stringResource(R.string.st_ClipboardScreen_onboarding_step2_body),
                    shape = segmentShape(1, 3),
                )
                OnboardingStep(
                    number = 3,
                    title = stringResource(R.string.st_ClipboardScreen_onboarding_step3_title),
                    body = stringResource(R.string.st_ClipboardScreen_onboarding_step3_body),
                    shape = segmentShape(2, 3),
                )
            }
            Text(
                text = stringResource(R.string.st_ClipboardScreen_onboarding_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (!state.gate.capabilities.shizukuInstalled) {
                ToolzTonalExpressiveButton(
                    onClick = onInstallShizuku,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_install_shizuku))
                }
            } else if (!state.gate.capabilities.shizukuAuthorized) {
                ToolzTonalExpressiveButton(
                    onClick = onAuthorizeShizuku,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_authorize))
                }
            }
            if (state.gate.capabilities.shizukuAuthorized && !state.gate.capabilities.appOpAllowed) {
                ToolzTonalExpressiveButton(
                    onClick = onGrantAppOp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_grant_appop))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToolzExpressiveTextButton(
                    onClick = onDismiss,
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_onboarding_skip))
                }
                ToolzExpressiveButton(
                    onClick = onOpenStatus,
                    modifier = Modifier.heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_onboarding_continue))
                }
            }
        }
    }
}

@Composable
private fun OnboardingStep(number: Int, title: String, body: String, shape: Shape) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsSheet(
    state: ClipboardUiState,
    viewModel: ClipboardViewModel,
    feedbackHostState: SnackbarHostState,
    mediaPermissionGranted: Boolean,
    onRequestMediaPermission: () -> Unit,
    onDismiss: () -> Unit,
    onShowStatus: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val needsMediaRow = state.settings.captureScreenshots && !mediaPermissionGranted
    ToolzSheet(onDismiss = onDismiss, feedbackHostState = feedbackHostState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            item {
                Text(
                    text = stringResource(R.string.st_ClipboardScreen_settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            // ── Capture
            item { SettingsSectionTitle(stringResource(R.string.st_ClipboardScreen_settings_section_capture)) }
            item {
                val count = if (needsMediaRow) 3 else 2
                Column(
                    modifier = Modifier.animateContentSize(spatialSpring()),
                    verticalArrangement = Arrangement.spacedBy(SegmentGap),
                ) {
                    SettingSwitchRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_monitoring),
                        body = stringResource(R.string.st_ClipboardScreen_settings_monitoring_desc),
                        checked = state.settings.monitoring,
                        shape = segmentShape(0, count),
                        onCheckedChange = viewModel::setMonitoringEnabled,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_screenshots),
                        body = stringResource(R.string.st_ClipboardScreen_settings_screenshots_desc),
                        checked = state.settings.captureScreenshots,
                        enabled = state.settings.monitoring,
                        shape = segmentShape(1, count),
                        onCheckedChange = viewModel::setCaptureScreenshots,
                    )
                    if (needsMediaRow) {
                        PermissionRow(
                            title = stringResource(R.string.st_ClipboardScreen_settings_screenshots_permission),
                            body = stringResource(R.string.st_ClipboardScreen_settings_screenshots_permission_desc),
                            action = stringResource(R.string.st_ClipboardScreen_action_grant_media),
                            shape = segmentShape(2, count),
                            onAction = onRequestMediaPermission,
                        )
                    }
                }
            }

            // ── AI
            item { SettingsSectionTitle(stringResource(R.string.st_ClipboardScreen_settings_section_ai)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_ai),
                        body = stringResource(R.string.st_ClipboardScreen_settings_ai_desc),
                        checked = state.settings.aiEnabled,
                        shape = segmentShape(0, 2),
                        onCheckedChange = viewModel::setClipboardAiEnabled,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_auto_ai),
                        body = stringResource(R.string.st_ClipboardScreen_settings_auto_ai_desc),
                        checked = state.settings.autoAi,
                        enabled = state.settings.aiEnabled && !state.settings.offline,
                        shape = segmentShape(1, 2),
                        onCheckedChange = viewModel::setAutoAiEnabled,
                    )
                }
            }

            // ── Privacy
            item { SettingsSectionTitle(stringResource(R.string.st_ClipboardScreen_settings_section_privacy)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_sensitive),
                        body = stringResource(R.string.st_ClipboardScreen_settings_sensitive_desc),
                        checked = state.settings.excludeSensitive,
                        shape = segmentShape(0, 2),
                        onCheckedChange = viewModel::setExcludeSensitive,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_biometric),
                        body = stringResource(R.string.st_ClipboardScreen_settings_biometric_desc),
                        checked = state.settings.biometricLock,
                        shape = segmentShape(1, 2),
                        onCheckedChange = viewModel::setBiometricLock,
                    )
                }
            }

            // ── Storage
            item { SettingsSectionTitle(stringResource(R.string.st_ClipboardScreen_settings_section_storage)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
                    val retention = state.settings.retentionDays
                    StepperRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_retention),
                        value = if (retention == 0) {
                            stringResource(R.string.st_ClipboardScreen_retention_forever)
                        } else {
                            pluralStringResource(
                                R.plurals.st_ClipboardScreen_retention_days,
                                retention,
                                retention,
                            )
                        },
                        body = stringResource(R.string.st_ClipboardScreen_settings_retention_desc),
                        shape = segmentShape(0, 4),
                        canDecrease = stepRetention(retention, up = false) != retention,
                        canIncrease = stepRetention(retention, up = true) != retention,
                        onDecrease = { viewModel.setRetentionDays(stepRetention(retention, up = false)) },
                        onIncrease = { viewModel.setRetentionDays(stepRetention(retention, up = true)) },
                    )
                    val itemQuota = state.settings.itemQuotaMb
                    StepperRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_item_quota),
                        value = stringResource(R.string.st_ClipboardScreen_megabytes, itemQuota),
                        body = stringResource(R.string.st_ClipboardScreen_settings_item_quota_desc),
                        shape = segmentShape(1, 4),
                        canDecrease = itemQuota > 1,
                        canIncrease = itemQuota < 200,
                        onDecrease = { viewModel.setItemQuotaMb((itemQuota - 5).coerceAtLeast(1)) },
                        onIncrease = { viewModel.setItemQuotaMb((itemQuota + 5).coerceAtMost(200)) },
                    )
                    val totalQuota = state.settings.totalQuotaMb
                    StepperRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_total_quota),
                        value = stringResource(R.string.st_ClipboardScreen_megabytes, totalQuota),
                        body = stringResource(R.string.st_ClipboardScreen_settings_total_quota_desc),
                        shape = segmentShape(2, 4),
                        canDecrease = totalQuota > 25,
                        canIncrease = totalQuota < 5_000,
                        onDecrease = { viewModel.setTotalQuotaMb((totalQuota - 25).coerceAtLeast(25)) },
                        onIncrease = { viewModel.setTotalQuotaMb((totalQuota + 25).coerceAtMost(5_000)) },
                    )
                    val entries = state.settings.maxEntries
                    StepperRow(
                        title = stringResource(R.string.st_ClipboardScreen_settings_max_entries),
                        value = entries.toString(),
                        body = stringResource(R.string.st_ClipboardScreen_settings_max_entries_desc),
                        shape = segmentShape(3, 4),
                        canDecrease = entries > 50,
                        canIncrease = entries < 5_000,
                        onDecrease = { viewModel.setMaxEntries((entries - 50).coerceAtLeast(50)) },
                        onIncrease = { viewModel.setMaxEntries((entries + 50).coerceAtMost(5_000)) },
                    )
                }
            }

            item {
                Notice(
                    icon = Icons.Rounded.WarningAmber,
                    title = stringResource(R.string.st_ClipboardScreen_settings_backup_warning),
                    container = scheme.tertiaryContainer,
                    content = scheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    ToolzOutlinedExpressiveButton(
                        onClick = onShowStatus,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ControlHeight),
                        shape = SmallExpressiveShape,
                    ) {
                        Text(stringResource(R.string.st_ClipboardScreen_action_status))
                    }
                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            viewModel.resetOnboarding()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ControlHeight),
                        shape = SmallExpressiveShape,
                    ) {
                        Text(
                            text = stringResource(R.string.st_ClipboardScreen_settings_reset_onboarding),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Section label with extra room above so groups breathe. */
@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    shape: Shape,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f),
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
private fun PermissionRow(
    title: String,
    body: String,
    action: String,
    shape: Shape,
    onAction: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(text = body, style = MaterialTheme.typography.bodySmall)
                }
            }
            ToolzTonalExpressiveButton(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ControlHeight),
                shape = SmallExpressiveShape,
            ) {
                Text(action)
            }
        }
    }
}

/** Title and description on the left, value with compact −/+ buttons on the right. */
@Composable
private fun StepperRow(
    title: String,
    value: String,
    body: String,
    shape: Shape,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    val haptic = rememberToolzHapticFeedback()
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { haptic.tick(); onDecrease() },
                enabled = canDecrease,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.st_ClipboardScreen_action_decrease),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 56.dp),
            )
            FilledTonalIconButton(
                onClick = { haptic.tick(); onIncrease() },
                enabled = canIncrease,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.st_ClipboardScreen_action_increase),
                    modifier = Modifier.size(18.dp),
                )
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
    feedbackHostState: SnackbarHostState,
    mediaPermissionGranted: Boolean,
    onRequestMediaPermission: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    val context = LocalContext.current
    val status = state.gate.status
    val caps = state.gate.capabilities
    val needsMediaRow = state.settings.captureScreenshots && !mediaPermissionGranted
    ToolzSheet(onDismiss = onDismiss, feedbackHostState = feedbackHostState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.st_ClipboardScreen_status_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
            StatusBanner(status)

            SectionTitle(stringResource(R.string.st_ClipboardScreen_status_section_access))
            Surface(
                shape = RoundedCornerShape(OuterRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .animateContentSize(spatialSpring()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusRow(stringResource(R.string.st_ClipboardScreen_status_shizuku_installed), caps.shizukuInstalled)
                    StatusRow(stringResource(R.string.st_ClipboardScreen_status_shizuku_running), caps.shizukuRunning)
                    StatusRow(stringResource(R.string.st_ClipboardScreen_status_shizuku_authorized), caps.shizukuAuthorized)
                    StatusRow(stringResource(R.string.st_ClipboardScreen_status_appop), caps.appOpAllowed)
                    StatusRow(stringResource(R.string.st_ClipboardScreen_status_monitoring), state.settings.monitoring)
                    StatusRow(stringResource(R.string.st_ClipboardScreen_status_screenshots), state.settings.captureScreenshots)
                    if (state.settings.captureScreenshots) {
                        StatusRow(
                            stringResource(R.string.st_ClipboardScreen_status_media_permission),
                            mediaPermissionGranted,
                        )
                    }
                }
            }
            if (needsMediaRow) {
                PermissionRow(
                    title = stringResource(R.string.st_ClipboardScreen_settings_screenshots_permission),
                    body = stringResource(R.string.st_ClipboardScreen_settings_screenshots_permission_desc),
                    action = stringResource(R.string.st_ClipboardScreen_action_grant_media),
                    shape = RoundedCornerShape(OuterRadius),
                    onAction = onRequestMediaPermission,
                )
            }

            SectionTitle(stringResource(R.string.st_ClipboardScreen_status_section_storage))
            Surface(
                shape = RoundedCornerShape(OuterRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                    val quotaBytes = state.stats.totalQuotaMb.toLong() * 1024L * 1024L
                    StatusRow(
                        stringResource(R.string.st_ClipboardScreen_status_storage),
                        stringResource(
                            R.string.st_ClipboardScreen_status_quota_used,
                            Formatter.formatShortFileSize(context, state.stats.blobBytes),
                            Formatter.formatShortFileSize(context, quotaBytes),
                        ),
                    )
                    if (quotaBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (state.stats.blobBytes.toFloat() / quotaBytes.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (!caps.shizukuInstalled) {
                ToolzExpressiveButton(
                    onClick = {
                        runCatching {
                            context.startActivity(viewModel.shizukuInstallIntent())
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_install_shizuku))
                }
            } else if (!caps.shizukuAuthorized) {
                ToolzExpressiveButton(
                    onClick = viewModel::requestShizukuAuthorization,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_authorize))
                }
            } else if (!caps.appOpAllowed) {
                ToolzTonalExpressiveButton(
                    onClick = viewModel::grantClipboardAppOp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_grant_appop))
                }
            } else {
                ToolzOutlinedExpressiveButton(
                    onClick = viewModel::revokeClipboardAppOp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(stringResource(R.string.st_ClipboardScreen_action_revoke_appop))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolzOutlinedExpressiveButton(
                    onClick = { viewModel.refreshGate(); viewModel.refreshStats() },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.st_ClipboardScreen_action_refresh))
                }
                ToolzOutlinedExpressiveButton(
                    onClick = onOpenAppDetails,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ControlHeight),
                    shape = SmallExpressiveShape,
                ) {
                    Text(
                        text = stringResource(R.string.st_ClipboardScreen_status_app_details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = stringResource(R.string.st_ClipboardScreen_status_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
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
            contentDescription = stringResource(
                if (value) R.string.st_ClipboardScreen_status_enabled
                else R.string.st_ClipboardScreen_status_disabled,
            ),
            tint = if (value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Empty / loading / dialogs ─────────────────────────────────────────────

@Composable
private fun FullHeightState(content: @Composable () -> Unit) {
    // Pull-to-refresh only receives gestures from a scrollable child. A single
    // full-height lazy item keeps the state centred while still giving the
    // refresh gesture something to consume.
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize()) {
                content()
            }
        }
    }
}

@Composable
private fun LoadingState() {
    FullHeightState {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ExpressiveLoadingWheel(
                contentDesc = stringResource(R.string.st_ClipboardScreen_loading),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.st_ClipboardScreen_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    // Fades and settles in so switching filters / trash does not flash a blank page.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    FullHeightState {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(effectsSpring()) + slideInVertically(spatialSpring()) { it / 12 } +
                    scaleIn(spatialSpring(), initialScale = 0.92f),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    ToolzExpressiveButton(
                        onClick = onAction,
                        modifier = Modifier.heightIn(min = ControlHeight),
                        shape = SmallExpressiveShape,
                    ) {
                        Text(actionLabel)
                    }
                }
                if (secondaryLabel != null && onSecondaryAction != null) {
                    Spacer(Modifier.height(4.dp))
                    ToolzExpressiveTextButton(
                        onClick = onSecondaryAction,
                        shape = SmallExpressiveShape,
                    ) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
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
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = onConfirm,
                shape = SmallExpressiveShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            ToolzExpressiveTextButton(
                onClick = onDismiss,
                shape = SmallExpressiveShape,
            ) {
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

/**
 * Wall clock that advances every 30 seconds. Relative timestamps derive from
 * this value so an open screen never keeps showing "just now" for a clip
 * captured hours ago, without waking the device often enough to matter.
 */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

@Composable
private fun sourceLabel(entry: ClipboardEntry): String {
    val context = LocalContext.current
    val sourceApp = entry.sourceApp?.takeIf { it.isNotBlank() }
    // Real captures currently store only a source code, while legacy rows and
    // backups may store a package name (or a human-readable app name).
    val packageLabel = remember(sourceApp) {
        sourceApp?.takeIf { it.contains('.') }?.let { packageName ->
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0L),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getApplicationInfo(packageName, 0)
                }
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrNull()
        }
    }
    if (!packageLabel.isNullOrBlank()) return packageLabel
    if (!sourceApp.isNullOrBlank() && !sourceApp.contains('.')) return sourceApp

    val rawSource = entry.source?.takeIf { it.isNotBlank() }
        ?: sourceApp
        ?: return stringResource(R.string.st_ClipboardScreen_source_unknown)
    return when (rawSource.uppercase()) {
        ClipboardSource.MANUAL -> stringResource(R.string.st_ClipboardScreen_source_manual)
        ClipboardSource.LISTENER -> stringResource(R.string.st_ClipboardScreen_source_listener)
        ClipboardSource.POLL -> stringResource(R.string.st_ClipboardScreen_source_poll)
        ClipboardSource.TILE -> stringResource(R.string.st_ClipboardScreen_source_tile)
        ClipboardSource.EXTERNAL -> stringResource(R.string.st_ClipboardScreen_source_external)
        ClipboardSource.SCREENSHOT -> stringResource(R.string.st_ClipboardScreen_source_screenshot)
        ClipboardSource.SHARE -> stringResource(R.string.st_ClipboardScreen_source_share)
        ClipboardSource.PROCESS_TEXT -> stringResource(R.string.st_ClipboardScreen_source_process_text)
        else -> if (rawSource.contains('.')) rawSource.substringAfterLast('.') else rawSource
    }
}

private val LINK_IN_TEXT_REGEX = Regex(
    "(https?://|www\\.)[^\\s<>\"']+",
    RegexOption.IGNORE_CASE,
)

private fun openLink(context: Context, content: String) {
    // Only ever launch http/https. A clip can contain arbitrary text or a
    // malicious scheme (intent:, file:, javascript:), so normalize bare
    // www. links and extract the first real URL from mixed text.
    val candidate = LINK_IN_TEXT_REGEX.find(content.trim())?.value?.trimEnd('.', ',', ')', ']', '}')
        ?: return
    val normalized = if (candidate.startsWith("www.", ignoreCase = true)) {
        "https://$candidate"
    } else {
        candidate
    }
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
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
        ClipboardMessage.NOT_FOREGROUND -> context.getString(R.string.st_ClipboardScreen_message_not_foreground)
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
        ClipboardMessage.AI_TEXT_ONLY -> context.getString(R.string.st_ClipboardScreen_message_ai_text_only)
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