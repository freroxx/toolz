/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
/**
 * P4c: Profile tab extracted verbatim from WhisperMainScreen.kt (the "Next PR"
 * promised in WhisperMainScreenComponents.kt). Physical move only — no logic
 * edits; visibility widened private → internal so the pager can keep calling it.
 */

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTab(
    uiState: WhisperUiState,
    viewModel: WhisperViewModel,
    toastState: WhisperToastState,
    saveTrigger: Int,
    discardTrigger: Int,
    onUnsavedChangesChanged: (Boolean) -> Unit,
    onProfileSaved: () -> Unit,
    onLoggedOut: () -> Unit,
    onShowAvatarOptions: () -> Unit,
    onViewAvatarFull: (WhisperProfile) -> Unit,
) {
    val profile = uiState.currentProfile ?: return
    val haptic = rememberToolzHapticFeedback()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val initialDisplayName = profile.displayName ?: ""
    val initialBio = profile.bio ?: ""
    val initialIsPrivate = profile.isPrivate
    val initialIsHidden = profile.isHiddenFromDiscover

    // Reset the form only when a different account loads; refreshed profile data after a
    // save should not wipe edits the user is still making. rememberSaveable keeps the
    // in-progress edits across rotation/process recreation too.
    var displayName by rememberSaveable(profile.id) { mutableStateOf(initialDisplayName) }
    var bio by rememberSaveable(profile.id) { mutableStateOf(initialBio) }
    var isPrivate by rememberSaveable(profile.id) { mutableStateOf(initialIsPrivate) }
    var isHidden by rememberSaveable(profile.id) { mutableStateOf(initialIsHidden) }
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDiscoveryWarningDialog by remember { mutableStateOf(false) }
    var showAubupInfoDialog by remember { mutableStateOf(false) }
    var showCreateAccessFileDialog by remember { mutableStateOf(false) }
    var showRotateKeyDialog by remember { mutableStateOf(false) }
    // V3-FIX: QR verification entry point — show my own verification QR for in-person scan.
    var showVerifyQrDialog by remember { mutableStateOf(false) }
    // PHASE 1 (roadmap §1.3): debug-only protocol diagnostics viewer.
    var showProtocolDiagnostics by remember { mutableStateOf(false) }
    var isRotatingKey by remember { mutableStateOf(false) }

    // Resolved in composition scope: stringResource is composable-only and cannot be
    // called from the non-composable doSave() callback.
    val nameUpdatedMsg = stringResource(R.string.st_Whisper_DisplayNameUpdated)
    val bioUpdatedMsg = stringResource(R.string.st_Whisper_BioUpdated)
    val setPrivateMsg = stringResource(R.string.st_Whisper_ProfileSetPrivate)
    val setPublicMsg = stringResource(R.string.st_Whisper_ProfileSetPublic)
    val hiddenMsg = stringResource(R.string.st_Whisper_HiddenFromDiscover)
    val visibleMsg = stringResource(R.string.st_Whisper_VisibleInDiscover)
    val profileSavedMsg = stringResource(R.string.st_Whisper_ProfileSaved)
    // M-M8 copy feedback (existing shared "credential copied" key).
    val credentialCopiedMsg = stringResource(R.string.st_Whisper_CredentialCopied)

    // Track unsaved changes and notify parent
    val hasUnsaved = displayName != initialDisplayName || bio != initialBio || 
                    isPrivate != initialIsPrivate || isHidden != initialIsHidden
    
    LaunchedEffect(hasUnsaved) {
        onUnsavedChangesChanged(hasUnsaved)
    }

    // V2-FIX L15: doSave is declared before its callers so the dialog save trigger routes
    // through the SAME logic as the Save button — identical diff-toasts and one success
    // path that clears the parent flag / pending tab switch (failure keeps them pending).
    fun doSave() {
        val prevName = initialDisplayName
        val prevBio = initialBio
        val prevPrivate = initialIsPrivate
        val prevHidden = initialIsHidden

        viewModel.updateProfile(displayName, bio, isPrivate, isHidden) {
            val toasts = mutableListOf<String>()
            if (displayName != prevName) toasts.add(nameUpdatedMsg)
            if (bio != prevBio) toasts.add(bioUpdatedMsg)
            if (isPrivate != prevPrivate) toasts.add(if (isPrivate) setPrivateMsg else setPublicMsg)
            if (isHidden != prevHidden) toasts.add(if (isHidden) hiddenMsg else visibleMsg)
            if (toasts.isEmpty()) toasts.add(profileSavedMsg)
            toastState.show(toasts.joinToString(" · "), WhisperToastType.SUCCESS)
            // Unified completion; never runs on failure, so a pending tab switch survives.
            onProfileSaved()
        }
    }

    // Save trigger from UnsavedChangesDialog — routed through doSave()
    // V6-R2 (review): HorizontalPager re-runs these effects with stale non-zero
    // counters on every tab re-entry, silently re-saving (network write + toasts).
    // Same one-shot guard as pickPhotoTrigger below: rememberSaveable watermark so
    // neither recomposition nor process recreation replays an already-handled value.
    var lastHandledSaveTrigger by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(saveTrigger) {
        if (saveTrigger > lastHandledSaveTrigger) {
            lastHandledSaveTrigger = saveTrigger
            doSave()
        }
    }

    // Discard trigger from UnsavedChangesDialog
    var lastHandledDiscardTrigger by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(discardTrigger) {
        if (discardTrigger > lastHandledDiscardTrigger) {
            lastHandledDiscardTrigger = discardTrigger
            displayName = initialDisplayName
            bio = initialBio
            isPrivate = initialIsPrivate
            isHidden = initialIsHidden
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Reads and bounded-size checks never run on the main thread; any failure
            // (including readBounded's require for oversized files) is caught here and
            // surfaced as a toast instead of crashing the picker.
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("Could not open picked image")
                    stream.use { s ->
                        // Bound the read so a huge picker image can never exhaust memory.
                        val bytes = s.readBounded(MAX_AVATAR_READ_BYTES)
                        // V2-FIX L17 (comment-only): GetContent cannot guarantee a MIME type —
                        // sniffing magic bytes is overkill here and PickVisualMedia is NOT a
                        // drop-in replacement for every vendor picker, so keep the
                        // conservative image/jpeg fallback and let the server validate.
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        bytes to mimeType
                    }
                }
                result
                    .onSuccess { (bytes, mimeType) -> viewModel.uploadAvatar(bytes, mimeType) }
                    .onFailure { err ->
                        // V2-FIX L18: runCatching swallows CancellationException — rethrow so
                        // leaving the screen mid-read actually cancels instead of surfacing
                        // a bogus "could not read image" toast.
                        if (err is kotlinx.coroutines.CancellationException) throw err
                        // V6-R2 (review): oversized avatars hit readBounded's require("Avatar
                        // file is too large.") and previously showed the generic read-error.
                        val tooLarge = err.message?.contains("too large", ignoreCase = true) == true
                        toastState.show(
                            context.getString(
                                if (tooLarge) R.string.st_Whisper_Error_ImageTooLarge
                                else R.string.st_Whisper_Error_ReadImage,
                            ),
                            WhisperToastType.ERROR,
                        )
                    }
            }
        }
    }

    val pickTrigger by viewModel.pickPhotoTrigger.collectAsStateWithLifecycle()
    // V2-FIX M-H1: pickPhotoTrigger is a hot StateFlow — its last value used to re-fire on
    // every collection restart (tab re-entry, process recreation), silently relaunching
    // the picker. Consume one-shot via the VM AND keep the last-consumed counter in
    // rememberSaveable so recreation can't replay an already-handled trigger.
    var lastConsumedPickTrigger by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(pickTrigger) {
        if (pickTrigger > lastConsumedPickTrigger) {
            lastConsumedPickTrigger = pickTrigger
            viewModel.consumePickPhotoTrigger()
            imagePickerLauncher.launch("image/*")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .fadingEdges(top = 20.dp, bottom = 32.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Hero Avatar Section
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(contentAlignment = Alignment.Center) {
                WhisperAvatar(
                    profile = profile,
                    size = 104.dp,
                    onClick = onShowAvatarOptions,
                    onLongClick = { onViewAvatarFull(profile) },
                    bustCache = true, // L-16: bust right after a self-upload/edit
                )
                if (uiState.isUploadingAvatar) {
                    // M3 Expressive contained loading overlays the avatar circle
                    // while the encrypted upload (downscale → seal → host wrap)
                    // is in flight.
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.32f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveContainedLoadingIndicator()
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClick(onClick = onShowAvatarOptions)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        stringResource(R.string.cd_ChangePhoto),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Header Title & Username
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                profile.effectiveName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clickable {
                    haptic.click()
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("@${profile.effectiveUsername}"))
                    // V2-FIX M-M8: copy now confirms with haptic (click above) + toast.
                    toastState.show(credentialCopiedMsg, WhisperToastType.SUCCESS)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "@${profile.effectiveUsername}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.cd_Whisper_CopyUsername), modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Status Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isPrivate) stringResource(R.string.st_Whisper_Private) else stringResource(R.string.st_Whisper_Public),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (profile.publicKey != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (profile.publicKey != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        // V6-R2 (review): was a hardcoded English "E2EE (P-256)" literal.
                        // Now localized and architecture-aware: forward secrecy is the
                        // headline guarantee since the Double Ratchet went live.
                        when {
                            profile.publicKey != null && com.frerox.toolz.data.whisper.WhisperProtocolConfig.ratchetEnabled ->
                                stringResource(R.string.st_Whisper_Badge_Fs)
                            profile.publicKey != null -> stringResource(R.string.st_Whisper_Profile_E2EEBadge)
                            else -> stringResource(R.string.st_Whisper_Profile_StandardAuth)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.publicKey != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── V6-R3 REDESIGN: clear top-to-bottom hierarchy ──
        // 1 Account (fields + save together) → 2 Privacy & discovery →
        // 3 Security → 4 Backup → 5 Danger zone. Same state and handlers as
        // before; only grouping/order/containers changed.

        // ═══ 1. ACCOUNT — display name, bio, save action in ONE card ═══
        SectionHeader(stringResource(R.string.st_Whisper_Section_Account))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.st_Whisper_Profile_DisplayName)) },
                    leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                    singleLine = true,
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 160) bio = it },
                    label = { Text(stringResource(R.string.st_Whisper_Profile_Bio)) },
                    leadingIcon = { Icon(Icons.Rounded.Info, null) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MediumExpressiveShape,
                    supportingText = { Text("${bio.length}/160") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Save lives with the fields it saves (was stranded at page bottom).
                val saveButtonAnim by animateFloatAsState(
                    targetValue = if (hasUnsaved) 1f else 0.7f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
                ToolzExpressiveButton(
                    onClick = {
                        haptic.success()
                        doSave()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp).graphicsLayer { alpha = saveButtonAnim },
                    enabled = hasUnsaved,
                ) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (hasUnsaved) stringResource(R.string.st_Whisper_Profile_SaveChanges) else stringResource(R.string.st_Whisper_Profile_NoChanges),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ═══ 2. PRIVACY & DISCOVERY ═══
        SectionHeader(stringResource(R.string.st_Whisper_Section_PrivacyDiscovery))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Profile Visibility: Public vs Private
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.st_Whisper_ProfileVisibility), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    ToolzConnectedButtonGroup(
                        selectedIndex = if (isPrivate) 1 else 0,
                        options = listOf(stringResource(R.string.st_Whisper_Public), stringResource(R.string.st_Whisper_Private)),
                        onOptionSelected = { isPrivate = it == 1 },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (isPrivate) stringResource(R.string.st_Whisper_Profile_PrivateDesc) else stringResource(R.string.st_Whisper_Profile_PublicDesc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Hide from Discover Toggle
                ExpressiveCard(
                    onClick = {
                        if (!isHidden) {
                            showDiscoveryWarningDialog = true
                        } else {
                            isHidden = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.PersonSearch,
                            null,
                            tint = if (isHidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.st_Whisper_HideFromDiscover), fontWeight = FontWeight.Bold)
                            // V2-FIX M-H2: reuse the identical existing copy from the
                            // hide-from-discover confirm dialog instead of a hardcoded literal.
                            Text(
                                stringResource(R.string.st_Whisper_HideDiscoverConfirmDesc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ExpressiveSwitch(
                            checked = isHidden,
                            onCheckedChange = {
                                if (it) showDiscoveryWarningDialog = true
                                else isHidden = false
                            },
                        )
                    }
                }
            }
        }

        // ═══ 3. SECURITY ═══
        SectionHeader(stringResource(R.string.st_Whisper_Section_Security))
        val myFingerprint = viewModel.myFingerprint
        if (myFingerprint != null) {
            // V2-FIX L14: the inert ExpressiveCard(onClick = {}) wrapper is gone — a plain
            // Surface keeps the identical look without dead press feedback; the real
            // actions stay on the copy button and the rotate button below.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.st_Whisper_Fingerprint_Title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        stringResource(R.string.st_Whisper_Fingerprint_Desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                myFingerprint,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(myFingerprint))
                                // V2-FIX M-M8: copy now confirms with haptic (click above) + toast.
                                toastState.show(credentialCopiedMsg, WhisperToastType.SUCCESS)
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.cd_Whisper_CopyFingerprint), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    // V6-R2 (review): QR path replaces eyeballing the 8 hex groups — partner scans this.
                    ToolzTonalExpressiveButton(
                        onClick = { haptic.click(); showVerifyQrDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.st_Whisper_QrShowButton), fontWeight = FontWeight.SemiBold)
                    }
                    // V6-R2: non-debug encryption status — states the live guarantee
                    // (per-message forward secrecy) instead of leaving users to infer it
                    // from a protocol badge. Static copy; no session plumbing needed.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                stringResource(R.string.st_Whisper_Fs_Title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.st_Whisper_Fs_Desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // PHASE 1 (roadmap §1.3): debug builds expose the protocol event
                    // ring buffer for field diagnosis — never compiled into release UX.
                    if (BuildConfig.DEBUG) {
                        WhisperOptionsListItem(
                            leadingIcon = Icons.Rounded.BugReport,
                            label = stringResource(R.string.st_Whisper_Diag_Title),
                            onClick = { showProtocolDiagnostics = true },
                        )
                        if (showProtocolDiagnostics) {
                            WhisperDiagnosticsDialog(
                                lines = com.frerox.toolz.data.whisper.ProtocolDiagnostics.snapshot(),
                                counters = com.frerox.toolz.data.whisper.ProtocolDiagnostics.counters.toMap(),
                                onDismiss = { showProtocolDiagnostics = false },
                                onCopy = {
                                    haptic.click()
                                    clipboardManager.setText(
                                        androidx.compose.ui.text.AnnotatedString(
                                            buildString {
                                                com.frerox.toolz.data.whisper.ProtocolDiagnostics.counters.forEach { (k, v) -> appendLine("$k: $v") }
                                                appendLine()
                                                com.frerox.toolz.data.whisper.ProtocolDiagnostics.snapshot().forEach { appendLine(it) }
                                            },
                                        ),
                                    )
                                    toastState.show(context.getString(R.string.st_Whisper_Diag_Copied), WhisperToastType.SUCCESS)
                                },
                            )
                        }
                    }
                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            haptic.click()
                            showRotateKeyDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.SyncLock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.st_Whisper_Fingerprint_RotateButton), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ═══ 4. BACKUP (AUBUP) ═══
        SectionHeader(stringResource(R.string.st_Whisper_Section_Backup))
        // AUBUP: Auth User Backup Program Card
        ExpressiveCard(
            onClick = { showCreateAccessFileDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.EnhancedEncryption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.st_Whisper_Aubup_SaveAccessFile),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    ToolzExpressiveIconButton(onClick = {
                        haptic.click()
                        showAubupInfoDialog = true
                    }) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.st_Whisper_AccessFileInfo),
                            modifier = Modifier.size(18.dp),
                            // V6-R6 FIX: was tinted primary ON a primary-filled button —
                            // invisible icon inside a plain colored circle.
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Text(
                    stringResource(R.string.st_Whisper_Aubup_SaveAccessFileDesc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolzOutlinedExpressiveButton(
                    onClick = {
                        haptic.click()
                        showCreateAccessFileDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_Whisper_Aubup_CreateBackup), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ═══ 5. DANGER ZONE — logout (common) first, deletion last ═══
        SectionHeader(stringResource(R.string.st_Whisper_Section_DangerZone))

        // Logout — plain tap opens the confirmation dialog; the old hidden "hold 3s"
        // gesture is gone because it silently reset onboarding without signing out.
        ToolzOutlinedExpressiveButton(
            onClick = { haptic.click(); showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_Whisper_Profile_LogOut), fontWeight = FontWeight.Bold)
        }

        // Delete Account
        ToolzOutlinedExpressiveButton(
            onClick = { haptic.click(); showDeleteAccountDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_Whisper_DeleteAccount), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
    }

    // AUBUP Info Dialog
    if (showAubupInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAubupInfoDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    Icons.Rounded.EnhancedEncryption,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.st_Whisper_Aubup_InfoTitle),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    stringResource(R.string.st_Whisper_Aubup_InfoDesc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(onClick = { showAubupInfoDialog = false }) {
                    Text(stringResource(R.string.st_Whisper_Aubup_GotIt), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // AUBUP Create Access File Dialog
    if (showCreateAccessFileDialog) {
        var whisperCode by remember { mutableStateOf("") }
        var confirmWhisperCode by remember { mutableStateOf("") }
        var codeError by remember { mutableStateOf<String?>(null) }
        var isCreating by remember { mutableStateOf(false) }

        val codeMismatchMsg = stringResource(R.string.st_Whisper_Aubup_CodeMismatch)
        val codeLengthMsg = stringResource(R.string.st_Whisper_Aubup_CodeLengthError)

        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateAccessFileDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.st_Whisper_Aubup_SaveAccessFile),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.st_Whisper_Aubup_EnterWhisperCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = whisperCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                whisperCode = it
                                codeError = null
                            }
                        },
                        label = { Text(stringResource(R.string.st_Whisper_Aubup_WhisperCodeLabel)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = MediumExpressiveShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmWhisperCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                confirmWhisperCode = it
                                codeError = null
                            }
                        },
                        label = { Text(stringResource(R.string.st_Whisper_Aubup_ConfirmCodeLabel)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = MediumExpressiveShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (codeError != null) {
                        Text(
                            text = codeError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        if (whisperCode.length != 6) {
                            codeError = codeLengthMsg
                            return@ToolzExpressiveButton
                        }
                        if (whisperCode != confirmWhisperCode) {
                            codeError = codeMismatchMsg
                            return@ToolzExpressiveButton
                        }
                        isCreating = true
                        viewModel.createAccessFile(
                            whisperCode = whisperCode,
                            onSuccess = { file ->
                                isCreating = false
                                showCreateAccessFileDialog = false
                                haptic.success()
                                // V2-FIX B3: pre-Q devices write to the world-readable
                                // legacy Downloads dir — warn loudly on the success toast.
                                var createdMsg = context.getString(R.string.st_Whisper_Aubup_FileCreatedSuccess, profile.effectiveUsername)
                                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                                    // Localized here (UI layer); the English-only constant in
                                    // WhisperAubupManager.WARNING_LEGACY_STORAGE is no longer used.
                                    createdMsg += "\n" + context.getString(R.string.st_Whisper_Aubup_LegacyStorageWarning)
                                }
                                toastState.show(createdMsg, WhisperToastType.SUCCESS)
                            },
                            onError = { err ->
                                isCreating = false
                                codeError = err
                                toastState.show(err, WhisperToastType.ERROR)
                            }
                        )
                    },
                    enabled = whisperCode.length == 6 && confirmWhisperCode.length == 6 && !isCreating
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.st_Whisper_Aubup_CreateBackup), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showCreateAccessFileDialog = false },
                    enabled = !isCreating
                ) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // V3-FIX: verification QR dialog — encodes whisper-verify:<username>:<fingerprint>.
    // viewModel.myFingerprint is the same cached value shown in the fingerprint card above.
    val myQrFingerprint = viewModel.myFingerprint
    if (showVerifyQrDialog && myQrFingerprint != null) {
        WhisperVerifyQrDialog(
            username = profile.effectiveUsername,
            fingerprint = myQrFingerprint,
            onDismiss = { showVerifyQrDialog = false },
        )
    }

    // Rotate Key Dialog
    if (showRotateKeyDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRotatingKey) showRotateKeyDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            },
            title = { Text(stringResource(R.string.st_Whisper_RotateKey_Title), fontWeight = FontWeight.Bold) },
            text = {
                // V2-FIX M-H2: the exact copy already exists as a resource — reuse it.
                Text(
                    stringResource(R.string.st_Whisper_RotateKey_Desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        isRotatingKey = true
                        viewModel.rotateEncryptionKey { success ->
                            isRotatingKey = false
                            showRotateKeyDialog = false
                            if (success) {
                                haptic.success()
                                toastState.show(context.getString(R.string.st_Whisper_RotateKey_Success), WhisperToastType.SUCCESS)
                            } else {
                                toastState.show(context.getString(R.string.st_Whisper_RotateKey_Failed), WhisperToastType.ERROR)
                            }
                        }
                    },
                    enabled = !isRotatingKey
                ) {
                    if (isRotatingKey) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.st_Whisper_RotateKey_Action), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showRotateKeyDialog = false },
                    enabled = !isRotatingKey
                ) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Material 3 Expressive Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.st_Whisper_Profile_LogOutTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.st_Whisper_Profile_LogOutDesc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut {
                            onLoggedOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.st_Whisper_Profile_LogOut), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showLogoutDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // M3 Expressive Account Deletion Dialog
    if (showDeleteAccountDialog) {
        val isTokenUser = viewModel.isAnonymousTokenUser
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        // V2-FIX M-M5: mirror the rotate-key busy pattern — the VM has no deleting flag,
        // so track it locally; buttons lock and a spinner runs until the deletion verdict
        // arrives (a surfaced error releases the hold since deleteAccount reports
        // failures only through handleError).
        var isDeletingAccount by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.error) {
            if (uiState.error != null) isDeletingAccount = false
        }
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(
                    stringResource(R.string.st_Whisper_DeleteAccount_Title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isTokenUser) stringResource(R.string.st_Whisper_DeleteAccount_Desc_Token)
                        else stringResource(R.string.st_Whisper_DeleteAccount_Desc_Password),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isTokenUser) {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text(stringResource(R.string.st_Whisper_Bypass_PasswordLabel)) },
                            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    // V2-FIX M-H2: existing localized content descriptions.
                                    Icon(
                                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (passwordVisible) stringResource(R.string.cd_Whisper_HidePassword)
                                        else stringResource(R.string.cd_Whisper_ShowPassword)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        isDeletingAccount = true
                        viewModel.deleteAccount(
                            password = if (isTokenUser) null else passwordInput.ifBlank { null }
                        ) {
                            isDeletingAccount = false
                            showDeleteAccountDialog = false
                            onLoggedOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = (isTokenUser || passwordInput.isNotBlank()) && !isDeletingAccount,
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.st_Whisper_DeleteAccount), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showDeleteAccountDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDeletingAccount,
                ) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Whisper Discovery Warning Dialog
    if (showDiscoveryWarningDialog) {
        AlertDialog(
            onDismissRequest = { showDiscoveryWarningDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(stringResource(R.string.st_Whisper_HideDiscoverConfirmTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    stringResource(R.string.st_Whisper_HideDiscoverWarning_Body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        haptic.success()
                        isHidden = true
                        showDiscoveryWarningDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.st_Whisper_HideMe), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showDiscoveryWarningDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.st_Whisper_KeepVisible))
                }
            }
        )
    }
}
