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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperAuthState
import com.frerox.toolz.data.whisper.asString
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * Whisper authentication screen — Material 3 Expressive.
 *
 * Two paths:
 *  - Username + password, for people who want standard credentials.
 *  - A locally generated token, for people who want to remain anonymous.
 *
 * Credentials are automatically saved to the built-in Toolz Vault.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperAuthScreen(
    onAuthenticated: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: WhisperAuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()
    val generatedToken by viewModel.generatedToken.collectAsStateWithLifecycle()
    // V2-FIX A-M4: bumps on every (re)generation so the UI can reset stale copied/saved flags.
    val generatedTokenVersion by viewModel.generatedTokenVersion.collectAsStateWithLifecycle()
    val usernameAvailability by viewModel.usernameAvailability.collectAsStateWithLifecycle()
    val screenshotBypassEnabled by viewModel.screenshotBypassEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val toastState = rememberWhisperToastState()
    val haptic = rememberToolzHapticFeedback()
    val scope = rememberCoroutineScope()

    var showBypassDialog by remember { mutableStateOf(false) }

    // Recovery tokens and credentials are sensitive — never capture this screen.
    SecureWindow(bypassEnabled = screenshotBypassEnabled)

    if (showBypassDialog) {
        // M-17 FIX (reviewwhisper.md): password required to enable AND disable; unified copy.
        WhisperScreenshotBypassDialog(
            onDismiss = { showBypassDialog = false },
            onConfirm = { password ->
                scope.launch {
                    // FIX: surface WHY verification failed instead of blaming the password
                    // for lockouts/service errors.
                    when (verifyWhisperBypass(password)) {
                        WhisperBypassVerdict.Granted -> {
                            val enabling = !screenshotBypassEnabled
                            viewModel.setScreenshotBypass(enabling)
                            toastState.show(
                                context.getString(
                                    if (enabling) R.string.st_Whisper_Bypass_ProtectionOff
                                    else R.string.st_Whisper_Bypass_ProtectionOn
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
                }
                showBypassDialog = false
            }
        )
    }

    val aubupState by viewModel.aubupState.collectAsStateWithLifecycle()
    var showAubupRecoverySheet by remember { mutableStateOf(false) }
    var restoredCredentials by remember { mutableStateOf<AubupRecoveryState.Restored?>(null) }

    // ── V6-R7 (#auto-detect): file-access permission state + auto-found banner ──
    val hasFileAccess = remember {
        mutableStateOf(com.frerox.toolz.data.whisper.WhisperAubupManager.hasFileAccessForScan(context))
    }
    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasFileAccess.value = com.frerox.toolz.data.whisper.WhisperAubupManager.hasFileAccessForScan(context)
        if (hasFileAccess.value) viewModel.beginAubupFlow() // rescan now that storage is readable
    }
    // Re-evaluate on return from the Settings toggle (API ≥30 grant path).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasFileAccess.value = com.frerox.toolz.data.whisper.WhisperAubupManager.hasFileAccessForScan(context)
                if (hasFileAccess.value) viewModel.beginAubupFlow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // BUGFIX (P0-RestorePopup #37): The "Account restored successfully" dialog was
    // flashing for ~1 frame and disappearing. Root cause: restore paths set
    // _authState=Authenticated AND _aubupState=Restored in the ViewModel. The old
    // LaunchedEffect(authState) navigated away immediately (popUpTo WhisperAuth),
    // unmounting this screen before the aubupState Restored could promote to
    // restoredCredentials and render WhisperCredentialRevealedDialog.
    // Fix: gate navigation on the restore state. When aubupState is Restored (or a
    // pending restoredCredentials exists) we suppress onAuthenticated() and let the
    // dialog drive navigation onDismiss. Also consolidate the three authState effects
    // into one to avoid stale closures.
    LaunchedEffect(authState, aubupState, restoredCredentials) {
        // If a restore just happened, don't auto-navigate — the credential dialog must be acknowledged.
        if (authState is WhisperAuthState.Authenticated && aubupState is AubupRecoveryState.Restored) return@LaunchedEffect
        if (authState is WhisperAuthState.Authenticated && restoredCredentials != null) return@LaunchedEffect

        when (val s = authState) {
            is WhisperAuthState.Authenticated -> onAuthenticated()
            is WhisperAuthState.Error -> {
                haptic.error()
                toastState.show(s.message.asString(context), WhisperToastType.ERROR)
                viewModel.clearError()
            }
            is WhisperAuthState.Notice -> {
                toastState.show(s.message.asString(context), WhisperToastType.SUCCESS)
                viewModel.dismissNotice()
            }
            else -> {}
        }
    }

    LaunchedEffect(aubupState) {
        when (val s = aubupState) {
            is AubupRecoveryState.Restored -> {
                // Persist for dialog even if sheet closes; suppress automatic navigation
                restoredCredentials = s
                showAubupRecoverySheet = false
            }
            is AubupRecoveryState.Error -> {
                // V2-FIX A-M3: Error now carries UiText.
                toastState.show(s.message.asString(context), WhisperToastType.ERROR)
            }
            else -> {}
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.st_Whisper_Title), fontWeight = FontWeight.Bold)
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.st_Whisper_Beta_Badge),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        ToolzExpressiveIconButton(onClick = { haptic.click(); onNavigateBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_Back))
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.toolzBackground(),
        ) { paddingValues ->
            // Full-screen loading only while the session is being resolved at startup.
            // During a submit the form stays mounted so user input is never lost.
            val initLoading = authState is WhisperAuthState.Loading && !submitting
            if (initLoading) {
                Box(Modifier.fillMaxSize().toolzBackground(), contentAlignment = Alignment.Center) {
                    ToolzLoadingIndicator()
                }
            } else {
                WhisperAuthContent(
                    authState = authState,
                    isLoading = submitting || authState is WhisperAuthState.Loading,
                    generatedToken = generatedToken?.token,
                    generatedTokenVersion = generatedTokenVersion,
                    usernameAvailability = usernameAvailability,
                    toastState = toastState,
                    onCheckUsername = viewModel::checkUsernameAvailable,
                    onLoginUsername = viewModel::loginWithUsername,
                    onRegisterUsername = viewModel::registerWithUsername,
                    onGenerateToken = viewModel::generateToken,
                    onRegisterToken = viewModel::registerWithGeneratedToken,
                    onLoginToken = viewModel::loginWithToken,
                    onNormalizeToken = viewModel::normalizeToken,
                    onLostAccountClick = {
                        showAubupRecoverySheet = true
                        // V3-FIX (multi-account): reuses a cached ScanResult when present
                        // so switching accounts doesn't force a rescan.
                        viewModel.beginAubupFlow()
                    },
                    onCopyToken = { token, restoreTo ->
                        val systemClipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        viewModel.scheduleTokenClipboardExpiry(token, restoreTo, systemClipboard)
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }

        }

        if (showAubupRecoverySheet) {
            WhisperAubupRecoveryModalSheet(
                hasFileAccess = hasFileAccess.value,
                onRequestFileAccess = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        runCatching {
                            context.startActivity(
                                com.frerox.toolz.data.whisper.WhisperAubupManager.allFilesAccessIntent(context)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    } else {
                        storagePermissionLauncher.launch(arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ))
                    }
                },
                aubupState = aubupState,
                toastState = toastState,
                onDismiss = {
                    showAubupRecoverySheet = false
                    viewModel.resetAubupState()
                },
                onRestoreVault = viewModel::restoreFromVault,
                onRestoreFile = viewModel::restoreFromAccessFile,
                onRestoreBytes = viewModel::restoreFromAccessBytes,
                // V2-FIX AU-M5: file reads moved off main into the ViewModel (1 MB cap).
                onImportAccessBytes = viewModel::importAccessBytes,
                onRescan = viewModel::startAubupScan,
            )
        }

        if (restoredCredentials != null) {
            // V2-FIX AU-H1: credential copy now routes through the same clipboard-expiry
            // pipeline as token copy — the previous clip is captured and scheduled for
            // restore via WorkManager, and confirmation goes through the in-app toast
            // host instead of a system Toast that lingers over this SecureWindow screen.
            WhisperCredentialRevealedDialog(
                restored = restoredCredentials!!,
                onCopyCredential = { credential ->
                    val previousClip = clipboard.getText()?.text
                    clipboard.setText(AnnotatedString(credential))
                    val systemClipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    viewModel.scheduleTokenClipboardExpiry(credential, previousClip, systemClipboard)
                    toastState.show(context.getString(R.string.st_Whisper_CredentialCopied), WhisperToastType.INFO)
                },
                onDismiss = {
                    restoredCredentials = null
                    // V3-FIX (multi-account): return the recovery state to the cached
                    // ScanResult (consumed entry pruned) instead of leaving it at Restored,
                    // so reopening the sheet still lists the remaining entries and users
                    // can switch accounts without rescanning.
                    viewModel.resumeAubupAfterRestore()
                    onAuthenticated()
                }
            )
        }

        WhisperToastHost(
            hostState = toastState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhisperAuthContent(
    authState: WhisperAuthState,
    isLoading: Boolean,
    generatedToken: String?,
    generatedTokenVersion: Int,
    usernameAvailability: UsernameAvailability,
    toastState: WhisperToastState,
    onCheckUsername: (String) -> Unit,
    onLoginUsername: (String, String) -> Unit,
    onRegisterUsername: (String, String, String) -> Unit,
    onGenerateToken: () -> Unit,
    onRegisterToken: (displayName: String) -> Unit,
    onLoginToken: (String) -> Unit,
    onNormalizeToken: (String) -> String,
    onLostAccountClick: () -> Unit,
    onCopyToken: (token: String, restoreTo: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 = Username, 1 = Token
    var selectedMode by remember { mutableIntStateOf(0) }

    // V2-FIX AU-M2 + AU-M3: every auth form field is remembered with rememberSaveable and
    // hoisted ABOVE the AnimatedContent containers. AnimatedContent disposes the outgoing
    // content's state, so fields previously lived (and died) inside the section
    // composables — switching Username<->Token or SignIn<->Register wiped whatever the
    // user had typed. Hoisted here, the entered text survives both tab switches and
    // configuration changes/process death.
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(false) }   // SignIn <-> Register
    var tokenInput by rememberSaveable { mutableStateOf("") }
    var tokenDisplayName by rememberSaveable { mutableStateOf("") }
    var tokenLoginMode by rememberSaveable { mutableStateOf(false) } // New account <-> I have a token

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .fadingEdges(top = 16.dp, bottom = 24.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        WhisperAuthHeader()

        // Mode Segmented Control — Username vs Token, equal weight
        ToolzConnectedButtonGroup(
            selectedIndex = selectedMode,
            options = listOf(
                stringResource(R.string.st_Whisper_Auth_Mode_Username),
                stringResource(R.string.st_Whisper_Auth_Mode_Token),
            ),
            unCheckedIcons = listOf(Icons.Rounded.Person, Icons.Rounded.Key),
            checkedIcons = listOf(Icons.Rounded.Person, Icons.Rounded.Key),
            onOptionSelected = { selectedMode = it },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                (fadeIn(spring()) + slideInHorizontally(spring()) { if (targetState > initialState) 40 else -40 })
                    .togetherWith(fadeOut(spring()) + slideOutHorizontally(spring()) { if (targetState > initialState) -40 else 40 })
            },
            label = "authModeContent",
        ) { mode ->
            when (mode) {
                0 -> UsernameAuthSection(
                    isLoading = isLoading,
                    usernameAvailability = usernameAvailability,
                    onCheckUsername = onCheckUsername,
                    username = username,
                    onUsernameChange = { raw ->
                        val formatted = raw.lowercase().filter { char -> char.isLetterOrDigit() || char == '_' }
                        username = formatted
                        // Availability pre-check only matters while registering.
                        if (registerMode) onCheckUsername(formatted)
                    },
                    password = password,
                    onPasswordChange = { password = it },
                    confirmPassword = confirmPassword,
                    onConfirmPasswordChange = { confirmPassword = it },
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it },
                    isRegisterMode = registerMode,
                    onRegisterModeChange = { registerMode = it },
                    onLogin = onLoginUsername,
                    onRegister = onRegisterUsername,
                )
                1 -> TokenAuthSection(
                    isLoading = isLoading,
                    generatedToken = generatedToken,
                    generatedTokenVersion = generatedTokenVersion,
                    toastState = toastState,
                    tokenInput = tokenInput,
                    onTokenInputChange = { tokenInput = it },
                    tokenDisplayName = tokenDisplayName,
                    onTokenDisplayNameChange = { tokenDisplayName = it },
                    isLoginMode = tokenLoginMode,
                    onLoginModeChange = { tokenLoginMode = it },
                    onGenerate = onGenerateToken,
                    onRegister = onRegisterToken,
                    onLogin = onLoginToken,
                    onNormalizeToken = onNormalizeToken,
                    onCopyToken = onCopyToken,
                )
            }
        }
        
        VaultAssuranceCard()

        // AUBUP: I lost my account recovery entrypoint
        ToolzOutlinedExpressiveButton(
            onClick = onLostAccountClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MediumExpressiveShape,
        ) {
            Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.st_Whisper_Aubup_LostAccount),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun VaultAssuranceCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    stringResource(R.string.st_Whisper_Vault_SecuredTitle),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.st_Whisper_Vault_SecuredDesc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Plain, static header.
 */
@Composable
private fun WhisperAuthHeader() {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(MediumExpressiveShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Chat,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.st_Whisper_Auth_Headline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.st_Whisper_Auth_ZeroKnowledgeHeadline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UsernameAuthSection(
    isLoading: Boolean,
    usernameAvailability: UsernameAvailability,
    onCheckUsername: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    isRegisterMode: Boolean,
    onRegisterModeChange: (Boolean) -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    // V2-FIX AU-M3: field values and the mode toggle are hoisted to WhisperAuthContent —
    // AnimatedContent here only animates between the two form layouts.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToolzConnectedButtonGroup(
            selectedIndex = if (isRegisterMode) 1 else 0,
            options = listOf(
                stringResource(R.string.st_Whisper_Auth_SignIn),
                stringResource(R.string.st_Whisper_Auth_CreateAccount),
            ),
            onOptionSelected = { onRegisterModeChange(it == 1) },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedContent(
            targetState = isRegisterMode,
            transitionSpec = { fadeIn(spring()).togetherWith(fadeOut(spring())) },
            label = "usernameMode",
        ) { registerMode ->
            UsernameAuthForm(
                isLoading = isLoading,
                ctaLabel = if (registerMode) {
                    stringResource(R.string.st_Whisper_Auth_CreateAccount)
                } else {
                    stringResource(R.string.st_Whisper_Auth_SignIn)
                },
                isRegister = registerMode,
                usernameAvailability = usernameAvailability,
                username = username,
                onUsernameChange = onUsernameChange,
                password = password,
                onPasswordChange = onPasswordChange,
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = onConfirmPasswordChange,
                displayName = displayName,
                onDisplayNameChange = onDisplayNameChange,
                onCheckUsername = onCheckUsername,
                onLogin = onLogin,
                onRegister = onRegister,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UsernameAuthForm(
    isLoading: Boolean,
    ctaLabel: String,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    isRegister: Boolean = false,
    usernameAvailability: UsernameAvailability = UsernameAvailability.Idle,
    // V2-FIX A-H2: used by the Unavailable "tap to retry" affordance.
    onCheckUsername: (String) -> Unit = {},
) {
    var showPassword by remember { mutableStateOf(false) }
    var touchedConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptic = rememberToolzHapticFeedback()

    val passwordsMatch = !isRegister || password == confirmPassword

    // V2-FIX A-H2: Unavailable (offline / server error) no longer dead-ends registration.
    // Format-valid usernames may still submit — the server final-validates availability;
    // the UI shows a hint that verification failed instead of silently blocking.
    val availabilityOk = usernameAvailability is UsernameAvailability.Available ||
        usernameAvailability is UsernameAvailability.Unavailable

    val canSubmit = if (isRegister) {
        username.isNotBlank() && password.length >= 10 && passwordsMatch && !isLoading &&
        availabilityOk && displayName.isNotBlank()
    } else {
        username.isNotBlank() && password.length >= 10 && !isLoading
    }

    fun submit() {
        if (canSubmit) {
            haptic.success()
            focusManager.clearFocus()
            if (isRegister) {
                onRegister(username.trim().lowercase(), password, displayName.trim())
            } else {
                onLogin(username.trim().lowercase(), password)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { onUsernameChange(it) },
            label = { Text(if (isRegister) stringResource(R.string.st_Whisper_Auth_ChooseUsernameLabel) else stringResource(R.string.st_Whisper_Auth_UsernameLabel)) },
            leadingIcon = { Icon(Icons.Rounded.Person, null) },
            trailingIcon = {
                if (isRegister) {
                    when (usernameAvailability) {
                        is UsernameAvailability.Checking -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        is UsernameAvailability.Available -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        is UsernameAvailability.Taken -> Icon(Icons.Rounded.Cancel, null, tint = MaterialTheme.colorScheme.error)
                        is UsernameAvailability.Invalid -> Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error)
                        // V2-FIX A-H2: verification failed (offline etc.) — warn, don't hide.
                        is UsernameAvailability.Unavailable -> Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.tertiary)
                        else -> {}
                    }
                }
            },
            isError = isRegister && (
                usernameAvailability is UsernameAvailability.Taken ||
                    usernameAvailability is UsernameAvailability.Invalid ||
                    usernameAvailability is UsernameAvailability.Unavailable
                ),
            supportingText = if (isRegister) {
                {
                    when (usernameAvailability) {
                        is UsernameAvailability.Taken -> Text(stringResource(R.string.st_Whisper_Auth_UsernameTaken))
                        is UsernameAvailability.Available -> Text(stringResource(R.string.st_Whisper_Auth_UsernameAvailable))
                        is UsernameAvailability.Checking -> Text(stringResource(R.string.st_Whisper_Auth_UsernameChecking))
                        is UsernameAvailability.Invalid -> Text(usernameAvailability.reason.asString())
                        // V2-FIX A-H2: retry affordance — tapping re-runs the availability
                        // check. Registration stays possible; the server final-validates.
                        is UsernameAvailability.Unavailable -> Text(
                            stringResource(R.string.st_Whisper_Auth_UsernameUnavailable),
                            modifier = Modifier.clickable { onCheckUsername(username) },
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        else -> {}
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
            shape = MediumExpressiveShape,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { onPasswordChange(it) },
            label = { Text(stringResource(R.string.st_Whisper_Auth_Password)) },
            leadingIcon = { Icon(Icons.Rounded.Password, null) },
            trailingIcon = {
                ToolzExpressiveIconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) stringResource(R.string.cd_Whisper_HidePassword) else stringResource(R.string.cd_Whisper_ShowPassword),
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isRegister) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { if (!isRegister) submit() },
            ),
            singleLine = true,
            shape = MediumExpressiveShape,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isRegister && password.isNotEmpty()) {
            PasswordStrengthMeter(password)
        }

        if (isRegister) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { onConfirmPasswordChange(it); touchedConfirm = true },
                label = { Text(stringResource(R.string.st_Whisper_Auth_ConfirmPassword)) },
                leadingIcon = { Icon(Icons.Rounded.Password, null) },
                visualTransformation = PasswordVisualTransformation(),
                isError = touchedConfirm && confirmPassword.isNotEmpty() && !passwordsMatch,
                supportingText = if (touchedConfirm && confirmPassword.isNotEmpty() && !passwordsMatch) {
                    { Text(stringResource(R.string.st_Whisper_Auth_PasswordsDoNotMatch)) }
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true,
                shape = MediumExpressiveShape,
                modifier = Modifier.fillMaxWidth(),
            )
            
            OutlinedTextField(
                value = displayName,
                onValueChange = { onDisplayNameChange(it) },
                label = { Text(stringResource(R.string.st_Whisper_Auth_DisplayName)) },
                leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                singleLine = true,
                shape = MediumExpressiveShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ToolzExpressiveButton(
            onClick = { submit() },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            AnimatedContent(
                targetState = isLoading,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "btnLoading",
            ) { loading ->
                if (loading) {
                    ToolzLoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(ctaLabel, fontWeight = FontWeight.Bold)
                }
            }
        }

        // V2-FIX AU-M4: a silently disabled CTA gives no feedback — list exactly which
        // requirements are still unmet. Reuses existing error strings where possible.
        if (!canSubmit && !isLoading) {
            val unmetRequirements = buildList {
                if (isRegister && username.isNotBlank() && (username.length < 3 || username.length > 20)) {
                    add(stringResource(R.string.st_Whisper_Error_UsernameLength))
                }
                if (password.length < 10) add(stringResource(R.string.st_Whisper_Auth_PasswordMinLength))
                if (isRegister && password.isNotEmpty() && confirmPassword.isNotEmpty() && !passwordsMatch) {
                    add(stringResource(R.string.st_Whisper_Auth_PasswordsDoNotMatch))
                }
                if (isRegister && displayName.isBlank()) {
                    add(stringResource(R.string.st_Whisper_Auth_ChooseDisplayName))
                }
                if (isRegister && usernameAvailability is UsernameAvailability.Checking) {
                    add(stringResource(R.string.st_Whisper_Auth_UsernameChecking))
                }
            }
            if (unmetRequirements.isNotEmpty()) {
                Text(
                    text = unmetRequirements.joinToString(separator = "  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PasswordStrengthMeter(password: String) {
    val score = calculatePasswordScore(password)
    val barColor = when (score) {
        1 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val label = when (score) {
        1 -> stringResource(R.string.st_Whisper_Auth_PasswordWeak)
        2 -> stringResource(R.string.st_Whisper_Auth_PasswordMedium)
        else -> stringResource(R.string.st_Whisper_Auth_PasswordStrong)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { score / 3f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = barColor)
    }
}

/**
 * Token auth.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenAuthSection(
    isLoading: Boolean,
    generatedToken: String?,
    generatedTokenVersion: Int,
    toastState: WhisperToastState,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    tokenDisplayName: String,
    onTokenDisplayNameChange: (String) -> Unit,
    isLoginMode: Boolean,
    onLoginModeChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onRegister: (displayName: String) -> Unit,
    onLogin: (String) -> Unit,
    onNormalizeToken: (String) -> String,
    onCopyToken: (token: String, restoreTo: String?) -> Unit,
) {
    // V2-FIX AU-M3: mode toggle and field values are hoisted — AnimatedContent only
    // animates between layouts and no longer owns any text state.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TokenExplainerCard()

        ToolzConnectedButtonGroup(
            selectedIndex = if (isLoginMode) 1 else 0,
            options = listOf(
                stringResource(R.string.st_Whisper_Auth_NewAccount),
                stringResource(R.string.st_Whisper_Auth_IHaveAToken),
            ),
            onOptionSelected = { onLoginModeChange(it == 1) },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedContent(
            targetState = isLoginMode,
            transitionSpec = { fadeIn(spring()).togetherWith(fadeOut(spring())) },
            label = "tokenMode",
        ) { loginMode ->
            if (loginMode) {
                TokenLoginForm(
                    isLoading = isLoading,
                    tokenInput = tokenInput,
                    onTokenInputChange = onTokenInputChange,
                    onLogin = onLogin,
                    onNormalizeToken = onNormalizeToken,
                )
            } else {
                TokenRegisterForm(
                    isLoading = isLoading,
                    generatedToken = generatedToken,
                    generatedTokenVersion = generatedTokenVersion,
                    toastState = toastState,
                    displayName = tokenDisplayName,
                    onDisplayNameChange = onTokenDisplayNameChange,
                    onGenerate = onGenerate,
                    onRegister = onRegister,
                    onCopyToken = onCopyToken,
                )
            }
        }
    }
}

@Composable
private fun TokenExplainerCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MediumExpressiveShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.st_Whisper_Auth_ZeroKnowledgeTitle),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.st_Whisper_Auth_ZeroKnowledgeDesc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenLoginForm(
    isLoading: Boolean,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    onLogin: (String) -> Unit,
    onNormalizeToken: (String) -> String,
) {
    // V2-FIX AU-M2/AU-M3: tokenInput is hoisted (rememberSaveable in WhisperAuthContent).
    val clipboard = LocalClipboardManager.current
    val haptic = rememberToolzHapticFeedback()

    val cleanRaw = tokenInput.trim()
    val hasInvalidChars = cleanRaw.isNotEmpty() && cleanRaw.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }
    val normalized = onNormalizeToken(tokenInput)
    val isValidLength = normalized.length == 64

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { if (it.length <= 80) onTokenInputChange(it) },
            label = { Text(stringResource(R.string.st_Whisper_Auth_PasteToken)) },
            leadingIcon = { Icon(Icons.Rounded.Key, null) },
            trailingIcon = {
                ToolzExpressiveIconButton(onClick = {
                    haptic.click()
                    clipboard.getText()?.text?.let { if (it.length <= 80) onTokenInputChange(it) }
                }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.cd_Paste)) }
            },
            isError = hasInvalidChars,
            singleLine = true,
            shape = MediumExpressiveShape,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            supportingText = {
                Text(
                    if (hasInvalidChars) {
                        stringResource(R.string.st_Whisper_Auth_TokenInvalidChars)
                    } else {
                        stringResource(R.string.st_Whisper_Auth_TokenLength, normalized.length)
                    }
                )
            },
        )
        ToolzExpressiveButton(
            onClick = { haptic.success(); onLogin(normalized) },
            enabled = isValidLength && !hasInvalidChars && !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            AnimatedContent(isLoading, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "loginBtn") { l ->
                if (l) ToolzLoadingIndicator(Modifier.size(24.dp))
                else Text(stringResource(R.string.st_Whisper_Auth_SignIn), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenRegisterForm(
    isLoading: Boolean,
    generatedToken: String?,
    generatedTokenVersion: Int,
    toastState: WhisperToastState,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onRegister: (displayName: String) -> Unit,
    onCopyToken: (token: String, restoreTo: String?) -> Unit,
) {
    var isCopied by remember { mutableStateOf(false) }
    var hasSavedToken by remember { mutableStateOf(false) }
    var revealToken by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val haptic = rememberToolzHapticFeedback()
    val nameFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // V2-FIX A-M4: a regenerated token burns the old credential — reset the copied/saved
    // affordances so the UI never claims the OLD token is still copied, and re-prompt the
    // user to save the NEW one before continuing.
    LaunchedEffect(generatedTokenVersion) {
        if (generatedTokenVersion > 0) {
            isCopied = false
            hasSavedToken = false
            revealToken = false
        }
    }

    LaunchedEffect(isCopied) {
        if (isCopied) hasSavedToken = true
    }

    // The token only ever sits on the clipboard for 60 seconds: it is masked on screen
    // and replaced afterwards, so a leaked screenshot or a forgotten clipboard never
    // hands over a working credential. The expiry timer lives on the ViewModel so it
    // survives rotation and navigation (the old rememberCoroutineScope died with the
    // composable and could leak a live token).
    fun copyTokenWithExpiry(token: String) {
        val previous = clipboard.getText()?.text
        clipboard.setText(AnnotatedString(token))
        isCopied = true
        hasSavedToken = true
        toastState.show(
            context.getString(R.string.st_Whisper_Auth_CopyExpiry),
            WhisperToastType.INFO
        )
        onCopyToken(token, previous)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (generatedToken == null) {
            ToolzExpressiveButton(
                onClick = { haptic.success(); onGenerate() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.Key, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_Whisper_Auth_GenerateMyToken), fontWeight = FontWeight.Bold)
            }
        } else {
            // V6-R2 (review): inert ExpressiveCard(onClick = {}) — the exact dead
            // press-feedback anti-pattern already removed twice elsewhere (V2-FIX L14).
            // A plain Surface keeps the look without pretending to be clickable.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.st_Whisper_Auth_YourToken),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        ToolzExpressiveIconButton(onClick = {
                            haptic.success()
                            copyTokenWithExpiry(generatedToken)
                        }) {
                            Icon(
                                if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                stringResource(R.string.cd_CopyToken),
                                tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        ToolzExpressiveIconButton(onClick = {
                            haptic.click()
                            revealToken = !revealToken
                        }) {
                            Icon(
                                if (revealToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                stringResource(
                                    if (revealToken) R.string.cd_Whisper_HideToken else R.string.cd_Whisper_ShowToken
                                ),
                            )
                        }
                    }
                    Text(
                        if (revealToken) generatedToken else generatedToken.maskedHex(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.st_Whisper_Auth_SaveTokenWarning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (!hasSavedToken) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SmallExpressiveShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.st_Whisper_Auth_CopyToContinue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { onDisplayNameChange(it) },
                    label = { Text(stringResource(R.string.st_Whisper_Auth_ChooseDisplayName)) },
                    leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                    singleLine = true,
                    shape = MediumExpressiveShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                )

                LaunchedEffect(hasSavedToken) {
                    if (hasSavedToken) nameFocusRequester.requestFocus()
                }

                ToolzExpressiveButton(
                    onClick = { haptic.success(); onRegister(displayName.trim()) },
                    enabled = !isLoading && displayName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    AnimatedContent(isLoading, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "regBtn") { l ->
                        if (l) ToolzLoadingIndicator(Modifier.size(24.dp))
                        else Text(stringResource(R.string.st_Whisper_Auth_CreateAccount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun calculatePasswordScore(pwd: String): Int {
    // L-11 FIX (reviewwhisper.md): reward long passphrases and symbols, not just digits+caps.
    if (pwd.length < 6) return 1
    var score = 1
    if (pwd.length >= 10) score++
    if (pwd.any { it.isDigit() } && pwd.any { it.isUpperCase() }) score++
    if (pwd.length >= 16 || pwd.any { !it.isLetterOrDigit() }) score++
    return score.coerceAtMost(3)
}

/** Hides a long hex credential, keeping only enough for the user to recognize it. */
private fun String.maskedHex(): String {
    if (length <= 12) return "•".repeat(length)
    return take(6) + "•".repeat(length - 10) + takeLast(4)
}

// ═══════════════════════════════════════════════════════════════════════════════
// AUBUP RECOVERY COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperAubupRecoveryModalSheet(
    hasFileAccess: Boolean,
    onRequestFileAccess: () -> Unit,
    aubupState: AubupRecoveryState,
    toastState: WhisperToastState,
    onDismiss: () -> Unit,
    onRestoreVault: (com.frerox.toolz.data.password.PasswordEntity) -> Unit,
    onRestoreFile: (java.io.File, String) -> Unit,
    onRestoreBytes: (ByteArray, String) -> Unit,
    onImportAccessBytes: (android.net.Uri, (Result<ByteArray>) -> Unit) -> Unit,
    onRescan: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val context = LocalContext.current
    var selectedFileForCode by remember { mutableStateOf<java.io.File?>(null) }
    var uploadedBytesForCode by remember { mutableStateOf<ByteArray?>(null) }
    var whisperCodeInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }

    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        // V2-FIX AU-M5: bytes are read on the ViewModel's IO dispatcher with a hard 1 MB
        // cap (the old callback did a blocking readBytes() on main). Read failures and
        // oversize files surface through the in-app toast host.
        uri?.let {
            onImportAccessBytes(it) { result ->
                result.onSuccess { bytes ->
                    uploadedBytesForCode = bytes
                    whisperCodeInput = ""
                    codeError = null
                }.onFailure {
                    toastState.show(context.getString(R.string.st_Whisper_Error_AccessFileUnreadable), WhisperToastType.ERROR)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // V6-R7 (#auto-detect): without file access the scan cannot see Downloads —
            // show an explicit permission state with a one-tap grant instead of a
            // silently-empty list.
            if (!hasFileAccess) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            stringResource(R.string.st_Whisper_Aubup_PermissionNeeded_Title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            stringResource(R.string.st_Whisper_Aubup_PermissionNeeded_Desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        ToolzExpressiveButton(
                            onClick = onRequestFileAccess,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.st_Whisper_Aubup_PermissionNeeded_Button), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.LockReset,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.st_Whisper_Aubup_Title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.st_Whisper_Aubup_Subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToolzExpressiveIconButton(onClick = { haptic.click(); onRescan() }) {
                    // V2-FIX AU-M6: content description was hardcoded English.
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cd_Whisper_Aubup_Rescan), modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider()

            when (aubupState) {
                // V2-FIX AU-M1: Idle previously fell through to a blank sheet. It is the
                // transient state right after opening (startAubupScan flips to Scanning),
                // so render it exactly like Scanning instead of showing nothing.
                is AubupRecoveryState.Idle,
                is AubupRecoveryState.Scanning -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.st_Whisper_Aubup_Scanning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // V2-FIX AU-M1: recovery errors were only toasted (and invisible once the
                // toast dismissed) — now rendered inline with a retry affordance.
                is AubupRecoveryState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = aubupState.message.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        ToolzExpressiveButton(
                            onClick = {
                                haptic.click()
                                onRescan()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            // Reuses the existing localized "Retry" string.
                            Text(stringResource(R.string.st_Whisper_Retry), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // V2-FIX AU-M1: Restored promotes to the credential dialog almost
                // immediately; show brief graceful feedback instead of a blank sheet.
                is AubupRecoveryState.Restored -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            stringResource(R.string.st_Whisper_Aubup_CredentialRevealedTitle),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                is AubupRecoveryState.ScanResult -> {
                    val hasVault = aubupState.vaultAccounts.isNotEmpty()
                    val hasFiles = aubupState.accessFiles.isNotEmpty()

                    if (selectedFileForCode != null || uploadedBytesForCode != null) {
                        // Whisper Code Prompt Subview
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.st_Whisper_Aubup_EnterWhisperCode),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            OutlinedTextField(
                                value = whisperCodeInput,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        whisperCodeInput = it
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

                            if (codeError != null) {
                                Text(
                                    text = codeError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ToolzOutlinedExpressiveButton(
                                    onClick = {
                                        selectedFileForCode = null
                                        uploadedBytesForCode = null
                                        whisperCodeInput = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.st_Whisper_Friends_Cancel))
                                }

                                ToolzExpressiveButton(
                                    onClick = {
                                        if (whisperCodeInput.length != 6) {
                                            codeError = context.getString(R.string.st_Whisper_Aubup_CodeLengthError)
                                            return@ToolzExpressiveButton
                                        }
                                        haptic.success()
                                        if (selectedFileForCode != null) {
                                            onRestoreFile(selectedFileForCode!!, whisperCodeInput)
                                        } else if (uploadedBytesForCode != null) {
                                            onRestoreBytes(uploadedBytesForCode!!, whisperCodeInput)
                                        }
                                    },
                                    enabled = whisperCodeInput.length == 6,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.st_Whisper_Aubup_DecryptAndLogin), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (!hasVault && !hasFiles) {
                        // Empty State: Prompt Upload
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                stringResource(R.string.st_Whisper_Aubup_NoBackupsFound),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.st_Whisper_Aubup_UploadDesc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            ToolzExpressiveButton(
                                onClick = { filePicker.launch("application/octet-stream") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.st_Whisper_Aubup_ManualUpload), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Tier 1: Vault Accounts
                        if (hasVault) {
                            // V3-FIX (multi-account): section headers carry counts.
                            Text(
                                stringResource(R.string.st_Whisper_Aubup_VaultFoundCount, aubupState.vaultAccounts.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            aubupState.vaultAccounts.forEach { account ->
                                ExpressiveCard(
                                    onClick = { onRestoreVault(account) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Shield,
                                            contentDescription = null,
                                            tint = Color(0xFF8E24AA),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        // V3-FIX (multi-account): display name prominent,
                                        // username secondary, TOKEN/PASSWORD badge resolved
                                        // from the persisted isToken flag (name-sniffing only
                                        // for legacy rows), plus a short relative date.
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    vaultAccountDisplayName(account),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (account.isToken ?: account.name.contains("Anon", ignoreCase = true)) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    },
                                                ) {
                                                    Text(
                                                        // V3-FIX: prefer the persisted isToken flag;
                                                        // name-sniffing only for legacy rows (flag null).
                                                        if (account.isToken ?: account.name.contains("Anon", ignoreCase = true)) stringResource(R.string.st_Whisper_Aubup_TokenAccount) else stringResource(R.string.st_Whisper_Aubup_PasswordAccount),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                            Text(
                                                text = buildString {
                                                    append("@").append(account.username)
                                                    append(" · ")
                                                    // V3-FIX (multi-account): short relative date
                                                    // (Today / dd MMM) from the vault row's date.
                                                    append(vaultAccountRelativeDate(account.lastUsedAt))
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        ToolzExpressiveButton(
                                            onClick = { onRestoreVault(account) },
                                        ) {
                                            Text(stringResource(R.string.st_Whisper_Aubup_RestoreAndLogin), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Tier 2: Access Files (.enc)
                        if (hasFiles) {
                            Text(
                                stringResource(R.string.st_Whisper_Aubup_FilesFoundCount, aubupState.accessFiles.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            aubupState.accessFiles.forEach { file ->
                                ExpressiveCard(
                                    onClick = {
                                        selectedFileForCode = file
                                        whisperCodeInput = ""
                                        codeError = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.EnhancedEncryption,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    file.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                )
                                                // V3-FIX (multi-account): short relative date so the
                                                // most-recent-first ordering is visible to the user.
                                                Text(
                                                    vaultFileRelativeDate(file),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                // V2-FIX AU-M6: hardcoded caption.
                                                text = stringResource(R.string.st_Whisper_Aubup_AccessFileLocation),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        ToolzOutlinedExpressiveButton(
                                            onClick = {
                                                selectedFileForCode = file
                                                whisperCodeInput = ""
                                                codeError = null
                                            }
                                        ) {
                                            Text(stringResource(R.string.st_Whisper_Aubup_DecryptAndLogin), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        // Manual File Upload Button at bottom
                        ToolzOutlinedExpressiveButton(
                            onClick = { filePicker.launch("application/octet-stream") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.st_Whisper_Aubup_ManualUpload), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun WhisperCredentialRevealedDialog(
    restored: AubupRecoveryState.Restored,
    onCopyCredential: (credential: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                stringResource(R.string.st_Whisper_Aubup_CredentialRevealedTitle),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Security Warning Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            stringResource(R.string.st_Whisper_Aubup_CredentialWarning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                // Credential Surface
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            // L-6 FIX: localized (was hardcoded English).
                            stringResource(R.string.st_Whisper_Aubup_AccountLabel) + " @${restored.username}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                // V6-R2 (review): raw "TOKEN"/"PASSWORD" enum words leaked
                                // untranslated — render localized auth-type labels instead.
                                text = stringResource(
                                    if (restored.authType.equals("TOKEN", ignoreCase = true)) {
                                        R.string.st_Whisper_AuthType_Token
                                    } else {
                                        R.string.st_Whisper_AuthType_Password
                                    },
                                ) + ": ${restored.credential.take(16)}...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                // V2-FIX AU-H1: copying used to bypass the clipboard-expiry
                                // pipeline and confirm via a system Toast. The caller now
                                // captures the previous clip, schedules the 60 s expiry via
                                // WorkManager and confirms through the app toast host.
                                onCopyCredential(restored.credential)
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.cd_Whisper_CopyCredential), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.st_Whisper_Aubup_Continue), fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// V3-FIX (multi-account): recovery-row helpers
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * V3-FIX (multi-account): vault rows store "Whisper: <handle>" / "Whisper Anon:
 * <handle>" — surface the human-readable part prominently; anything that doesn't
 * follow the convention falls back to the raw username.
 */
private fun vaultAccountDisplayName(account: com.frerox.toolz.data.password.PasswordEntity): String {
    val name = account.name.trim()
    if (!name.startsWith("Whisper", ignoreCase = true)) return account.username
    return name.substringAfter(':', "").trim().ifEmpty { account.username }
}

/**
 * V3-FIX (multi-account): short relative date for recovery rows — localized "Today"
 * string for the current calendar day, otherwise "dd MMM" via a locale-aware
 * DateTimeFormatter. Year omitted on purpose: access files are short-lived artifacts.
 */
@Composable
private fun vaultAccountRelativeDate(epochMillis: Long): String {
    val context = LocalContext.current
    return remember(epochMillis) {
        if (epochMillis <= 0L) return@remember ""
        val zone = java.time.ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        if (date == java.time.LocalDate.now(zone)) {
            context.getString(R.string.st_Whisper_Aubup_Today)
        } else {
            val locale = context.resources.configuration.locales[0]
            java.time.format.DateTimeFormatter.ofPattern("dd MMM", locale).format(date)
        }
    }
}

/** V3-FIX (multi-account): same relative-date treatment for .enc file rows. */
@Composable
private fun vaultFileRelativeDate(file: java.io.File): String {
    val modified = file.lastModified()
    if (modified <= 0L) return ""
    return vaultAccountRelativeDate(modified)
}
