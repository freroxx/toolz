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

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperAuthState
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * Whisper authentication screen — dual mode:
 * 1. Email (Login / Register)
 * 2. Anonymous Token (zero-knowledge auth)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperAuthScreen(
    onAuthenticated: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: WhisperAuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val generatedToken by viewModel.generatedToken.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = rememberToolzHapticFeedback()

    // Navigate on success
    LaunchedEffect(authState) {
        if (authState is WhisperAuthState.Authenticated) onAuthenticated()
    }

    // Show errors in snackbar
    LaunchedEffect(authState) {
        (authState as? WhisperAuthState.Error)?.let { err ->
            snackbarHostState.showSnackbar(err.message)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(stringResource(R.string.st_Whisper_Title), fontWeight = FontWeight.Black)
                    }
                },
                navigationIcon = {
                    ToolzExpressiveIconButton(onClick = { haptic.click(); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.toolzBackground(),
    ) { paddingValues ->
        WhisperAuthContent(
            authState = authState,
            generatedToken = generatedToken?.token,
            onLoginEmail = viewModel::loginWithEmail,
            onRegisterEmail = viewModel::registerWithEmail,
            onGenerateToken = viewModel::generateToken,
            onRegisterToken = viewModel::registerWithGeneratedToken,
            onLoginToken = viewModel::loginWithToken,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhisperAuthContent(
    authState: WhisperAuthState,
    generatedToken: String?,
    onLoginEmail: (String, String) -> Unit,
    onRegisterEmail: (String, String) -> Unit,
    onGenerateToken: () -> Unit,
    onRegisterToken: () -> Unit,
    onLoginToken: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 = Login, 1 = Register, 2 = Token
    var selectedMode by remember { mutableIntStateOf(0) }
    val isLoading = authState is WhisperAuthState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Hero section
        StaggeredEntrance(index = 0) {
            WhisperAuthHero()
        }

        // Mode selector
        StaggeredEntrance(index = 1) {
            ToolzConnectedButtonGroup(
                selectedIndex = selectedMode,
                options = listOf(
                    stringResource(R.string.st_Whisper_Auth_Mode_Login),
                    stringResource(R.string.st_Whisper_Auth_Mode_Register),
                    stringResource(R.string.st_Whisper_Auth_Mode_Token),
                ),
                unCheckedIcons = listOf(
                    Icons.Rounded.Login,
                    Icons.Rounded.PersonAdd,
                    Icons.Rounded.Key,
                ),
                checkedIcons = listOf(
                    Icons.Rounded.Login,
                    Icons.Rounded.PersonAdd,
                    Icons.Rounded.Key,
                ),
                onOptionSelected = { selectedMode = it },
            )
        }

        // Mode content
        StaggeredEntrance(index = 2) {
            AnimatedContent(
                targetState = selectedMode,
                transitionSpec = {
                    (fadeIn(spring()) + slideInHorizontally(spring()) { if (targetState > initialState) 80 else -80 })
                        .togetherWith(fadeOut(spring()) + slideOutHorizontally(spring()) { if (targetState > initialState) -80 else 80 })
                },
                label = "authModeContent"
            ) { mode ->
                when (mode) {
                    0 -> EmailAuthForm(
                        isLoading = isLoading,
                        ctaLabel = stringResource(R.string.st_Whisper_Auth_SignIn),
                        onSubmit = onLoginEmail,
                    )
                    1 -> EmailAuthForm(
                        isLoading = isLoading,
                        ctaLabel = stringResource(R.string.st_Whisper_Auth_CreateAccount),
                        onSubmit = onRegisterEmail,
                        isRegister = true,
                    )
                    2 -> TokenAuthForm(
                        isLoading = isLoading,
                        generatedToken = generatedToken,
                        onGenerate = onGenerateToken,
                        onRegister = onRegisterToken,
                        onLogin = onLoginToken,
                    )
                }
            }
        }

        // Privacy note
        StaggeredEntrance(index = 3) {
            WhisperPrivacyNote()
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun WhisperAuthHero() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        // Icon with gradient background
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.st_Whisper_Title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            stringResource(R.string.st_Whisper_Auth_Subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmailAuthForm(
    isLoading: Boolean,
    ctaLabel: String,
    onSubmit: (String, String) -> Unit,
    isRegister: Boolean = false,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptic = rememberToolzHapticFeedback()

    val passwordsMatch = !isRegister || password == confirmPassword
    val canSubmit = email.isNotBlank() && password.length >= 6 && passwordsMatch && !isLoading

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.st_Whisper_Auth_Email)) },
            leadingIcon = { Icon(Icons.Rounded.Email, null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
            shape = MediumExpressiveShape,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.st_Whisper_Auth_Password)) },
            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
            trailingIcon = {
                ToolzExpressiveIconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
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
                onDone = { if (canSubmit) { haptic.success(); onSubmit(email, password) } },
            ),
            singleLine = true,
            shape = MediumExpressiveShape,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isRegister) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.st_Whisper_Auth_ConfirmPassword)) },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                supportingText = if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                    { Text(stringResource(R.string.st_Whisper_Auth_PasswordsDoNotMatch)) }
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canSubmit) { haptic.success(); onSubmit(email, password) } },
                ),
                singleLine = true,
                shape = MediumExpressiveShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ToolzExpressiveButton(
            onClick = { haptic.success(); onSubmit(email, password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            AnimatedContent(
                targetState = isLoading,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "btnLoading"
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenAuthForm(
    isLoading: Boolean,
    generatedToken: String?,
    onGenerate: () -> Unit,
    onRegister: () -> Unit,
    onLogin: (String) -> Unit,
) {
    var tokenInput by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val haptic = rememberToolzHapticFeedback()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Explanation card
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.st_Whisper_Auth_ZeroKnowledgeTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.st_Whisper_Auth_ZeroKnowledgeDesc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            ToolzConnectedButtonGroup(
                selectedIndex = if (isLoginMode) 1 else 0,
                options = listOf(
                    stringResource(R.string.st_Whisper_Auth_NewAccount),
                    stringResource(R.string.st_Whisper_Auth_IHaveAToken),
                ),
                onOptionSelected = { isLoginMode = it == 1 },
            )
        }

        AnimatedContent(
            targetState = isLoginMode,
            transitionSpec = { fadeIn(spring()).togetherWith(fadeOut(spring())) },
            label = "tokenMode"
        ) { loginMode ->
            if (loginMode) {
                // Login with existing token
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { if (it.length <= 64) tokenInput = it },
                        label = { Text(stringResource(R.string.st_Whisper_Auth_PasteToken)) },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        trailingIcon = {
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                clipboard.getText()?.text?.let { tokenInput = it.take(64) }
                            }) { Icon(Icons.Rounded.ContentPaste, "Paste") }
                        },
                        singleLine = true,
                        shape = MediumExpressiveShape,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        supportingText = { Text("${tokenInput.length}/64") },
                    )
                    ToolzExpressiveButton(
                        onClick = { haptic.success(); onLogin(tokenInput) },
                        enabled = tokenInput.length == 64 && !isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        AnimatedContent(isLoading, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "loginBtn") { l ->
                            if (l) ToolzLoadingIndicator(Modifier.size(24.dp)) else Text(stringResource(R.string.st_Whisper_Auth_SignIn), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Generate new token
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
                        // Token display
                        ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.st_Whisper_Auth_YourToken), fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    ToolzExpressiveIconButton(onClick = {
                                        haptic.success()
                                        clipboard.setText(AnnotatedString(generatedToken))
                                    }) {
                                        Icon(Icons.Rounded.ContentCopy, "Copy")
                                    }
                                }
                                Text(
                                    generatedToken,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(SmallExpressiveShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .padding(10.dp),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Warning,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.st_Whisper_Auth_SaveTokenWarning),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        ToolzExpressiveButton(
                            onClick = { haptic.success(); onRegister() },
                            enabled = !isLoading,
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
    }
}

@Composable
private fun WhisperPrivacyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MediumExpressiveShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(R.string.st_Whisper_Auth_Phase2Note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
