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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val usernameAvailability by viewModel.usernameAvailability.collectAsStateWithLifecycle()
    val screenshotBypassEnabled by viewModel.screenshotBypassEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toastState = rememberWhisperToastState()
    val haptic = rememberToolzHapticFeedback()

    var showBypassDialog by remember { mutableStateOf(false) }

    // Recovery tokens and credentials are sensitive — never capture this screen.
    SecureWindow(bypassEnabled = screenshotBypassEnabled)

    if (showBypassDialog) {
        WhisperScreenshotBypassDialog(
            onDismiss = { showBypassDialog = false },
            onConfirm = { password ->
                if (isWhisperBypassPassword(password)) {
                    viewModel.setScreenshotBypass(true)
                    toastState.show("Successfully bypassed screenshot block", WhisperToastType.SUCCESS)
                } else {
                    toastState.show(context.getString(R.string.st_Whisper_Error_InvalidCredentials), WhisperToastType.ERROR)
                }
                showBypassDialog = false
            }
        )
    }

    LaunchedEffect(authState) {
        if (authState is WhisperAuthState.Authenticated) onAuthenticated()
    }

    LaunchedEffect(authState) {
        (authState as? WhisperAuthState.Error)?.let { err ->
            haptic.error()
            toastState.show(err.message.asString(context), WhisperToastType.ERROR)
            viewModel.clearError()
        }
    }

    LaunchedEffect(authState) {
        (authState as? WhisperAuthState.Notice)?.let { notice ->
            toastState.show(notice.message.asString(context), WhisperToastType.SUCCESS)
            viewModel.dismissNotice()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    modifier = Modifier.screenshotBypassGesture {
                        if (screenshotBypassEnabled) {
                            viewModel.setScreenshotBypass(false)
                            toastState.show("Successfully enabled screenshot block", WhisperToastType.SUCCESS)
                        } else {
                            showBypassDialog = true
                        }
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
                    usernameAvailability = usernameAvailability,
                    toastState = toastState,
                    onCheckUsername = viewModel::checkUsernameAvailable,
                    onLoginUsername = viewModel::loginWithUsername,
                    onRegisterUsername = viewModel::registerWithUsername,
                    onGenerateToken = viewModel::generateToken,
                    onRegisterToken = viewModel::registerWithGeneratedToken,
                    onLoginToken = viewModel::loginWithToken,
                    onNormalizeToken = viewModel::normalizeToken,
                    onCopyToken = { token, restoreTo ->
                        val systemClipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        viewModel.scheduleTokenClipboardExpiry(token, restoreTo, systemClipboard)
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }
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
    usernameAvailability: UsernameAvailability,
    toastState: WhisperToastState,
    onCheckUsername: (String) -> Unit,
    onLoginUsername: (String, String) -> Unit,
    onRegisterUsername: (String, String, String) -> Unit,
    onGenerateToken: () -> Unit,
    onRegisterToken: (displayName: String) -> Unit,
    onLoginToken: (String) -> Unit,
    onNormalizeToken: (String) -> String,
    onCopyToken: (token: String, restoreTo: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 = Username, 1 = Token
    var selectedMode by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .fadingEdges(top = 16.dp, bottom = 24.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
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
                    onLogin = onLoginUsername,
                    onRegister = onRegisterUsername,
                )
                1 -> TokenAuthSection(
                    isLoading = isLoading,
                    generatedToken = generatedToken,
                    toastState = toastState,
                    onGenerate = onGenerateToken,
                    onRegister = onRegisterToken,
                    onLogin = onLoginToken,
                    onNormalizeToken = onNormalizeToken,
                    onCopyToken = onCopyToken,
                )
            }
        }
        
        VaultAssuranceCard()

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
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    var isRegisterMode by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToolzConnectedButtonGroup(
            selectedIndex = if (isRegisterMode) 1 else 0,
            options = listOf(
                stringResource(R.string.st_Whisper_Auth_SignIn),
                stringResource(R.string.st_Whisper_Auth_CreateAccount),
            ),
            onOptionSelected = { isRegisterMode = it == 1 },
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
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    isRegister: Boolean = false,
    usernameAvailability: UsernameAvailability = UsernameAvailability.Idle,
    onCheckUsername: (String) -> Unit = {},
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    
    var showPassword by remember { mutableStateOf(false) }
    var touchedConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptic = rememberToolzHapticFeedback()

    val passwordsMatch = !isRegister || password == confirmPassword
    val canSubmit = if (isRegister) {
        username.isNotBlank() && password.length >= 10 && passwordsMatch && !isLoading &&
        usernameAvailability is UsernameAvailability.Available && displayName.isNotBlank()
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
            onValueChange = {
                val formatted = it.lowercase().filter { char -> char.isLetterOrDigit() || char == '_' }
                username = formatted
                if (isRegister) onCheckUsername(formatted)
            },
            label = { Text(if (isRegister) stringResource(R.string.st_Whisper_Auth_ChooseUsernameLabel) else stringResource(R.string.st_Whisper_Auth_UsernameLabel)) },
            leadingIcon = { Icon(Icons.Rounded.Person, null) },
            trailingIcon = {
                if (isRegister) {
                    when (usernameAvailability) {
                        is UsernameAvailability.Checking -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        is UsernameAvailability.Available -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        is UsernameAvailability.Taken -> Icon(Icons.Rounded.Cancel, null, tint = MaterialTheme.colorScheme.error)
                        is UsernameAvailability.Invalid -> Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error)
                        else -> {}
                    }
                }
            },
            isError = isRegister && (usernameAvailability is UsernameAvailability.Taken || usernameAvailability is UsernameAvailability.Invalid),
            supportingText = if (isRegister) {
                {
                    when (usernameAvailability) {
                        is UsernameAvailability.Taken -> Text(stringResource(R.string.st_Whisper_Auth_UsernameTaken))
                        is UsernameAvailability.Available -> Text(stringResource(R.string.st_Whisper_Auth_UsernameAvailable))
                        is UsernameAvailability.Checking -> Text(stringResource(R.string.st_Whisper_Auth_UsernameChecking))
                        is UsernameAvailability.Invalid -> Text(usernameAvailability.reason.asString())
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
            onValueChange = { password = it },
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
                onValueChange = { confirmPassword = it; touchedConfirm = true },
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
                onValueChange = { displayName = it },
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
    toastState: WhisperToastState,
    onGenerate: () -> Unit,
    onRegister: (displayName: String) -> Unit,
    onLogin: (String) -> Unit,
    onNormalizeToken: (String) -> String,
    onCopyToken: (token: String, restoreTo: String?) -> Unit,
) {
    var isLoginMode by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TokenExplainerCard()

        ToolzConnectedButtonGroup(
            selectedIndex = if (isLoginMode) 1 else 0,
            options = listOf(
                stringResource(R.string.st_Whisper_Auth_NewAccount),
                stringResource(R.string.st_Whisper_Auth_IHaveAToken),
            ),
            onOptionSelected = { isLoginMode = it == 1 },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedContent(
            targetState = isLoginMode,
            transitionSpec = { fadeIn(spring()).togetherWith(fadeOut(spring())) },
            label = "tokenMode",
        ) { loginMode ->
            if (loginMode) {
                TokenLoginForm(isLoading = isLoading, onLogin = onLogin, onNormalizeToken = onNormalizeToken)
            } else {
                TokenRegisterForm(
                    isLoading = isLoading,
                    generatedToken = generatedToken,
                    toastState = toastState,
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
    onLogin: (String) -> Unit,
    onNormalizeToken: (String) -> String,
) {
    var tokenInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val haptic = rememberToolzHapticFeedback()

    val cleanRaw = tokenInput.trim()
    val hasInvalidChars = cleanRaw.isNotEmpty() && cleanRaw.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }
    val normalized = onNormalizeToken(tokenInput)
    val isValidLength = normalized.length == 64

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { if (it.length <= 80) tokenInput = it },
            label = { Text(stringResource(R.string.st_Whisper_Auth_PasteToken)) },
            leadingIcon = { Icon(Icons.Rounded.Key, null) },
            trailingIcon = {
                ToolzExpressiveIconButton(onClick = {
                    haptic.click()
                    clipboard.getText()?.text?.let { tokenInput = it }
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
    toastState: WhisperToastState,
    onGenerate: () -> Unit,
    onRegister: (displayName: String) -> Unit,
    onCopyToken: (token: String, restoreTo: String?) -> Unit,
) {
    var isCopied by remember { mutableStateOf(false) }
    var hasSavedToken by remember { mutableStateOf(false) }
    var revealToken by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val haptic = rememberToolzHapticFeedback()
    val nameFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current

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
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
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
                    onValueChange = { displayName = it },
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
    if (pwd.length < 6) return 1
    var score = 1
    if (pwd.length >= 10) score++
    if (pwd.any { it.isDigit() } && pwd.any { it.isUpperCase() }) score++
    return score.coerceAtMost(3)
}

/** Hides a long hex credential, keeping only enough for the user to recognize it. */
private fun String.maskedHex(): String {
    if (length <= 12) return "•".repeat(length)
    return take(6) + "•".repeat(length - 10) + takeLast(4)
}
