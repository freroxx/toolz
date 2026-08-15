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

package com.frerox.toolz.ui.screens.utils

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.crypto.CryptoHistoryEntry
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.util.CryptoManager.CryptoAlgorithm
import com.frerox.toolz.util.CryptoManager.CryptoFormat
import com.frerox.toolz.util.CryptoManager.CryptoOperation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

/**
 * SmartEncrypterScreen — Material 3 Expressive redesign done YAYAYYAYAYYAYA!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartEncrypterScreen(
    onBack: () -> Unit,
    initialUri: String? = null,
    mode: String? = null,
    viewModel: SmartEncrypterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(initialUri, mode) {
        if (!initialUri.isNullOrEmpty()) {
            try {
                if (!uiState.isFileMode) {
                    viewModel.toggleFileMode()
                }

                val operationIntent = when (mode) {
                    "encrypt" -> CryptoOperation.ENCRYPT
                    "decrypt" -> CryptoOperation.DECRYPT
                    else -> null
                }
                viewModel.setFileOperationIntent(operationIntent)
                viewModel.onFileSelected(context, android.net.Uri.parse(initialUri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val vibrationManager = LocalVibrationManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showHistory by remember { mutableStateOf(false) }
    var showFullQr by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }
    var showEncPicker by remember { mutableStateOf(false) }

    // Handle External Intent (Open With...)
    val activity = context as? androidx.appcompat.app.AppCompatActivity
    LaunchedEffect(Unit) {
        activity?.intent?.let { intent ->
            if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                val uri = intent.data!!
                viewModel.handleExternalUri(context, uri)
                // Clear intent so it doesn't trigger again on rotation
                intent.action = null
                intent.data = null
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(context, it) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(context, it) }
    }

    // Specialized picker for .enc files (decrypt mode)
    val encFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(context, it) }
    }

    // Permission check
    fun hasAllFilesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Legacy permissions handled by system launcher if needed
        }
    }

    LaunchedEffect(Unit) {
        viewModel.updateFilePermissionStatus(hasAllFilesPermission())
    }

    // Re-check permission when returning to app
    val view = LocalView.current
    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                viewModel.updateFilePermissionStatus(hasAllFilesPermission())
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.st_SmartEncrypterScreen_a1b2), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                        AnimatedVisibility(visible = uiState.isLiveEnabled) {
                            LiveIndicator()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_SmartEncrypterScreen_e5f6))
                    }
                },
                actions = {
                    AnimatedVisibility(visible = !uiState.isFileMode) {
                        Row {
                            IconButton(onClick = viewModel::toggleLiveMode) {
                                Icon(
                                    if (uiState.isLiveEnabled) Icons.Rounded.Bolt else Icons.Rounded.FlashOff,
                                    contentDescription = stringResource(R.string.st_SmartEncrypterScreen_g7h8),
                                    tint = if (uiState.isLiveEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = viewModel::toggleSecureMode) {
                                Icon(
                                    if (uiState.isSecureMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(R.string.st_SmartEncrypterScreen_i9j0),
                                    tint = if (uiState.isSecureMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.st_SmartEncrypterScreen_k1l2))
                    }
                    if (!uiState.isProcessingFile) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.st_SmartEncrypterScreen_m3n4))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                ToolzConnectedButtonGroup(
                    selectedIndex = if (uiState.isFileMode) 1 else 0,
                    options = listOf(stringResource(R.string.st_SmartEncrypterScreen_o5p6), stringResource(R.string.st_SmartEncrypterScreen_q7r8)),
                    unCheckedIcons = listOf(Icons.AutoMirrored.Rounded.TextSnippet, Icons.Rounded.Description),
                    checkedIcons = listOf(Icons.AutoMirrored.Rounded.TextSnippet, Icons.Rounded.Description),
                    onOptionSelected = { viewModel.toggleFileMode() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            item {
                AnimatedContent(
                    targetState = uiState.isFileMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400)))
                            .togetherWith(fadeOut(animationSpec = tween(400)) + slideOutHorizontally(animationSpec = tween(400)))
                    },
                    label = "mode_switch"
                ) { isFileMode ->
                    if (isFileMode) {
                        FileModePanel(
                            uiState = uiState,
                            viewModel = viewModel,
                            onGrantClick = { showPermissionSheet = true },
                            onPickPhotos = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                            onPickFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
                            onPickEncFiles = {
                                vibrationManager?.vibrateClick()
                                viewModel.scanForEncFiles()
                                showEncPicker = true
                            },
                            onChangeOperation = { viewModel.setFileOperationIntent(null) },
                            onToggleRenamer = viewModel::toggleRenamer,
                            onCustomNameChange = viewModel::onCustomFileNameChanged,
                            onContinue = { viewModel.processFile(context) },
                            onClear = viewModel::clearFileSelection
                        )
                    } else {
                        CryptoPanel(
                            title = stringResource(R.string.st_SmartEncrypterScreen_s9t0),
                            icon = Icons.AutoMirrored.Rounded.TextSnippet
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                OutlinedTextField(
                                    value = uiState.inputText,
                                    onValueChange = viewModel::onInputChanged,
                                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                                    placeholder = { Text(stringResource(R.string.st_SmartEncrypterScreen_u1v2)) },
                                    visualTransformation = if (uiState.isSecureMode) PasswordVisualTransformation() else VisualTransformation.None,
                                    keyboardOptions = KeyboardOptions(keyboardType = if (uiState.isSecureMode) KeyboardType.Password else KeyboardType.Text),
                                    shape = RoundedCornerShape(24.dp),
                                    minLines = 3,
                                    maxLines = 8,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val text = clipboardManager.getText()?.text ?: ""
                                                viewModel.onInputChanged(text)
                                            },
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                                .size(36.dp)
                                        ) {
                                            Icon(Icons.Rounded.ContentPaste, stringResource(R.string.st_SmartEncrypterScreen_w3x4), tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnimatedVisibility(
                                        visible = uiState.inputText.isNotBlank(),
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        FormatBadge(format = uiState.detectedFormat)
                                    }

                                    Spacer(Modifier.weight(1f))

                                    if (uiState.isLiveEnabled) {
                                        SmartAutoToggle(
                                            isEnabled = uiState.isSmartAutoEnabled,
                                            onToggle = viewModel::toggleSmartAuto
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                CryptoPanel(
                    title = stringResource(R.string.st_SmartEncrypterScreen_c9d0),
                    icon = Icons.Rounded.Tune
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.toggleAlgorithmSection() }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                uiState.selectedAlgorithm.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                if (uiState.isAlgorithmSectionExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = uiState.isAlgorithmSectionExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            AlgorithmSelector(
                                selected = uiState.selectedAlgorithm,
                                onSelected = viewModel::onAlgorithmSelected,
                                isFileMode = uiState.isFileMode
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20).contains(uiState.selectedAlgorithm),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    CryptoPanel(
                        title = stringResource(R.string.st_SmartEncrypterScreen_e1f2),
                        icon = Icons.Rounded.Security
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            var showPassword by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = uiState.password,
                                onValueChange = viewModel::onPasswordChanged,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.st_SmartEncrypterScreen_g3h4)) },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(20.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (showPassword) stringResource(R.string.st_SmartEncrypterScreen_i5j6) else stringResource(R.string.st_SmartEncrypterScreen_k7l8),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )

                            PasswordStrengthIndicator(strength = uiState.passwordStrength)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { viewModel.toggleAutoClear() }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                              ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.st_SmartEncrypterScreen_u7v8), style = MaterialTheme.typography.bodyMedium)
                                }
                                Switch(
                                    checked = uiState.isAutoClearEnabled,
                                    onCheckedChange = { viewModel.toggleAutoClear() },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = !uiState.isFileMode && (!uiState.isLiveEnabled || !uiState.isSmartAutoEnabled || uiState.isManualSelectionActive)
                ) {
                    val isDecryptSuggested = uiState.suggestedOperation == CryptoOperation.DECRYPT || uiState.suggestedOperation == CryptoOperation.DECODE
                    val canToggle = !listOf(
                        CryptoAlgorithm.MD5, CryptoAlgorithm.SHA1, CryptoAlgorithm.SHA256, CryptoAlgorithm.SHA512
                    ).contains(uiState.selectedAlgorithm)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ActionButton(
                            text = if (isDecryptSuggested) stringResource(R.string.st_SmartEncrypterScreen_w9x0) else stringResource(R.string.st_SmartEncrypterScreen_a1b3),
                            icon = if (isDecryptSuggested) Icons.Rounded.NoEncryption else Icons.Rounded.EnhancedEncryption,
                            onClick = { if (isDecryptSuggested) viewModel.decrypt() else viewModel.encrypt() },
                            isLoading = uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )

                        if (canToggle) {
                            ActionButton(
                                text = if (isDecryptSuggested) stringResource(R.string.st_SmartEncrypterScreen_a1b3) else stringResource(R.string.st_SmartEncrypterScreen_w9x0),
                                icon = if (isDecryptSuggested) Icons.Rounded.EnhancedEncryption else Icons.Rounded.NoEncryption,
                                onClick = { if (isDecryptSuggested) viewModel.encrypt() else viewModel.decrypt() },
                                isLoading = uiState.isLoading,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = !uiState.isFileMode && uiState.resultText.isNotBlank(),
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.94f)
                ) {
                    ResultPanel(
                        result = uiState.resultText,
                        qrCode = uiState.qrCode,
                        autoClearSeconds = uiState.autoClearSeconds,
                        onClear = viewModel::clearResult,
                        onGenerateQr = viewModel::generateQr,
                        onOpenQrFull = { showFullQr = true },
                        onCopy = { clipboardManager.setText(AnnotatedString(uiState.resultText)) },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, uiState.resultText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share result"))
                        }
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = uiState.isFileMode && uiState.isProcessingFile,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    CryptoPanel(
                        title = uiState.fileProcessingStatus,
                        icon = Icons.Rounded.CloudSync
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val isPreparing = uiState.fileProcessingStatus.contains("Preparing")
                            val infiniteTransition = rememberInfiniteTransition(label = "preparing_pulse")
                            val pulseAlpha by if (isPreparing) {
                                infiniteTransition.animateFloat(
                                    initialValue = 0.5f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
                                    label = "alpha"
                                )
                            } else {
                                remember { mutableStateOf(1f) }
                            }

                            Box(modifier = Modifier.alpha(pulseAlpha)) {
                                ToolzWavyLinearProgressIndicator(
                                    progress = { uiState.fileProcessingProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${(uiState.fileProcessingProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                ToolzOutlinedExpressiveButton(
                                    onClick = viewModel::cancelFileProcess,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Rounded.Cancel, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = uiState.isFileMode && !uiState.isProcessingFile && uiState.processedFile != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f)
                ) {
                    val context = LocalContext.current
                    val processedFile = uiState.processedFile
                    
                    ExpressiveCard(
                        onClick = {},
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                        shape = RoundedCornerShape(32.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle, 
                                        null, 
                                        tint = MaterialTheme.colorScheme.primary, 
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.st_SmartEncrypterScreen_i7j8), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        processedFile?.name ?: "File saved", 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ToolzExpressiveButton(
                                    onClick = {
                                        processedFile?.let { file ->
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try { context.startActivity(Intent.createChooser(intent, "Open with")) } catch (e: Exception) {}
                                        }
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.st_SmartEncrypterScreen_k9l0))
                                }
                                
                                ToolzTonalExpressiveIconButton(
                                    onClick = {
                                        processedFile?.let { file ->
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = context.contentResolver.getType(uri) ?: "*/*"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share via"))
                                        }
                                    },
                                    modifier = Modifier.size(60.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, stringResource(R.string.st_SmartEncrypterScreen_m1n2))
                                }

                                ToolzOutlinedExpressiveIconButton(
                                    onClick = viewModel::clearFileSelection,
                                    modifier = Modifier.size(60.dp),
                                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteSweep, null)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showHistory) {
        HistoryBottomSheet(
            history = history,
            onDismiss = { showHistory = false },
            onDelete = viewModel::deleteHistoryEntry,
            onClear = viewModel::clearHistory,
            onSelect = {
                viewModel.restoreHistoryEntry(it)
                showHistory = false
            }
        )
    }

    if (showFullQr) {
        FullScreenQrDialog(
            result = uiState.resultText,
            foreColor = uiState.qrForeColor,
            backColor = uiState.qrBackColor,
            dotStyle = uiState.qrStyle,
            noteText = uiState.qrNoteText,
            noteSize = uiState.qrNoteSize,
            notePosition = uiState.qrNotePosition,
            isNoteEnabled = uiState.isQrNoteEnabled,
            qrCode = uiState.qrCode,
            isLoading = uiState.isQrLoading,
            onDismiss = { showFullQr = false },
            onUpdateCustomization = viewModel::updateQrCustomization
        )
    }

    if (showClearConfirm) {
        ClearAllConfirmDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                viewModel.clearAll()
                showClearConfirm = false
            }
        )
    }

    if (showPermissionSheet) {
        PermissionBottomSheet(
            onDismiss = { showPermissionSheet = false },
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
                showPermissionSheet = false
            }
        )
    }

    if (showEncPicker) {
        EncFilePickerBottomSheet(
            uiState = uiState,
            onDismiss = { showEncPicker = false },
            onFileSelected = { file ->
                vibrationManager?.vibrateClick()
                viewModel.onEncFileSelected(file, context)
                showEncPicker = false
            },
            onAdvancedPicker = {
                showEncPicker = false
                encFilePickerLauncher.launch(arrayOf("*/*"))
            },
            formatFileSize = viewModel::formatFileSize
        )
    }

    if (uiState.isFileMode && uiState.fileOperationIntent == null) {
        FileOperationBottomSheet(
            onSelect = viewModel::setFileOperationIntent,
            onDismiss = { viewModel.toggleFileMode() } // Exit file mode if they cancel
        )
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { 
            vibrationManager?.vibrateError()
            snackbarHostState.showSnackbar(it) 
        }
    }

    LaunchedEffect(uiState.processedFile) {
        if (uiState.processedFile != null) {
            vibrationManager?.vibrateSuccess()
        }
    }
}

/**
 * Calm "Live" indicator — a small tonal dot that breathes gently, replacing the old
 * saturated-yellow "LIVE MODE ACTIVE" badge with a scale-pulsing icon.
 */
@Composable
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "live_dot")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "live_dot_alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = alpha))
        )
        Text(
            stringResource(R.string.st_SmartEncrypterScreen_c3d4),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FormatBadge(format: CryptoFormat) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(0.6f),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                when (format) {
                    CryptoFormat.BASE64 -> Icons.Rounded.Code
                    CryptoFormat.HEX -> Icons.Rounded.Hexagon
                    CryptoFormat.BINARY -> Icons.Rounded.Memory
                    else -> Icons.Rounded.TextFields
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "Detected ${format.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun FileModePanel(
    uiState: EncrypterUiState,
    viewModel: SmartEncrypterViewModel,
    onGrantClick: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onPickEncFiles: () -> Unit,
    onChangeOperation: () -> Unit,
    onToggleRenamer: () -> Unit,
    onCustomNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onClear: () -> Unit
) {
    val operation = uiState.fileOperationIntent
    val hasPermission = uiState.isFilePermissionGranted
    val isRenamerEnabled = uiState.isRenamerEnabled
    val customFileName = uiState.customFileName
    val selectedFileUri = uiState.selectedFileUri

    CryptoPanel(
        title = if (selectedFileUri == null) "File ${operation?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Encrypter"}" else "Ready to Process",
        icon = when {
            selectedFileUri != null -> Icons.Rounded.TaskAlt
            operation == CryptoOperation.DECRYPT -> Icons.Rounded.NoEncryption
            else -> Icons.Rounded.EnhancedEncryption
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!hasPermission) {
                ExpressiveCard(
                    onClick = onGrantClick,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.LockPerson, null, tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permission needed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text("Allow access to all files to enable encryption.", style = MaterialTheme.typography.bodySmall)
                        }
                        ToolzExpressiveButton(onClick = onGrantClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Grant")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = hasPermission && operation != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (selectedFileUri == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Select content to ${operation?.name?.lowercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onChangeOperation) {
                                Text("Change Action", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (operation == CryptoOperation.DECRYPT) {
                            ExpressiveCard(
                                onClick = onPickEncFiles,
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(32.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(20.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.LockOpen, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(28.dp))
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text("Attach .enc file", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Select the encrypted file to restore", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilePickerButton(label = "Photos", icon = Icons.Rounded.Collections, onClick = onPickPhotos, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.primaryContainer)
                                FilePickerButton(label = "Files", icon = Icons.Rounded.Folder, onClick = onPickFiles, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            }
                        }
                    } else {
                        // Ready Panel
                        ExpressiveCard(
                            onClick = {},
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(uiState.selectedFileName ?: "Unknown File", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Size: ${viewModel.formatFileSize(uiState.selectedFileSize)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = onClear) {
                                    Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Renamer Toggle
                    ExpressiveCard(
                        onClick = onToggleRenamer,
                        containerColor = if (isRenamerEnabled) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.DriveFileRenameOutline, null, modifier = Modifier.size(18.dp), tint = if (isRenamerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Custom output name", style = MaterialTheme.typography.labelLarge)
                                }
                                Switch(checked = isRenamerEnabled, onCheckedChange = { onToggleRenamer() }, modifier = Modifier.scale(0.7f))
                            }
                            
                            AnimatedVisibility(visible = isRenamerEnabled) {
                                OutlinedTextField(
                                    value = customFileName,
                                    onValueChange = onCustomNameChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Enter new file name") },
                                    shape = RoundedCornerShape(16.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    singleLine = true,
                                    isError = isRenamerEnabled && customFileName.isBlank()
                                )
                            }
                        }
                    }

                    if (selectedFileUri != null) {
                        val canContinue = !isRenamerEnabled || customFileName.isNotBlank()
                        ToolzExpressiveButton(
                            onClick = { if (canContinue) onContinue() },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canContinue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (canContinue) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                            )
                        ) {
                            Icon(if (operation == CryptoOperation.DECRYPT) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null)
                            Spacer(Modifier.width(12.dp))
                            Text("Confirm & ${operation?.name?.lowercase()?.replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.Bold)
                        }
                        
                        if (isRenamerEnabled && customFileName.isBlank()) {
                            Text("Output name cannot be empty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                    
                    Text(
                        if (operation == CryptoOperation.ENCRYPT) "Encrypted files are saved to Downloads/Toolz with .enc extension."
                        else "Restored files are saved to Downloads/Toolz without .enc extension.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilePickerButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        containerColor = containerColor,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionBottomSheet(
    onDismiss: () -> Unit,
    onGrant: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Secure File Access",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "To encrypt your photos and documents, Toolz needs permission to access all files. Your files never leave this device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            
            ToolzExpressiveButton(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Allow Access")
            }
            
            ToolzExpressiveTextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not Now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncFilePickerBottomSheet(
    uiState: EncrypterUiState,
    onDismiss: () -> Unit,
    onFileSelected: (File) -> Unit,
    onAdvancedPicker: () -> Unit,
    formatFileSize: (Long) -> String
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .animateContentSize()
        ) {
            Text(
                "Select .enc file",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Box(modifier = Modifier.weight(1f, fill = false)) {
                if (uiState.isSearchingEncFiles) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.foundEncFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("No .enc files found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                            Text("Check your Downloads or Documents folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.foundEncFiles) { file ->
                            val date = SimpleDateFormat("MMM d, yyyy HH:mm").format(Date(file.lastModified()))
                            ListItem(
                                headlineContent = { Text(file.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                supportingContent = { Text("${formatFileSize(file.length())} • $date") },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onFileSelected(file) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            ToolzOutlinedExpressiveButton(
                onClick = onAdvancedPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Rounded.AutoFixHigh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Advanced (System Picker)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileOperationBottomSheet(
    onSelect: (CryptoOperation) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "File Action",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "What would you like to do with your files?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExpressiveCard(
                    onClick = { onSelect(CryptoOperation.ENCRYPT) },
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.EnhancedEncryption, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text("Encrypt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                
                ExpressiveCard(
                    onClick = { onSelect(CryptoOperation.DECRYPT) },
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.NoEncryption, null, tint = MaterialTheme.colorScheme.onSecondary)
                        }
                        Text("Decrypt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            ToolzExpressiveTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SmartAutoToggle(isEnabled: Boolean, onToggle: () -> Unit) {
    val bg by animateColorAsState(
        if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "smart_auto_bg"
    )
    val content by animateColorAsState(
        if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "smart_auto_content"
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .bouncyClick { onToggle() }
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (isEnabled) Icons.Rounded.AutoAwesome else Icons.Rounded.RadioButtonUnchecked,
            null,
            modifier = Modifier.size(16.dp),
            tint = content
        )
        Text(
            if (isEnabled) "Smart auto" else "Manual",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

@Composable
fun CryptoPanel(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(22.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        icon,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

@Composable
fun AlgorithmSelector(
    selected: CryptoAlgorithm,
    onSelected: (CryptoAlgorithm) -> Unit,
    isFileMode: Boolean = false
) {
    val groups = if (isFileMode) {
        listOf("Supported for Files" to listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20))
    } else {
        listOf(
            "Standard" to listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20),
            "Encoding" to listOf(CryptoAlgorithm.BASE64, CryptoAlgorithm.HEX, CryptoAlgorithm.BINARY, CryptoAlgorithm.BASE32, CryptoAlgorithm.URL),
            "Hashing" to listOf(CryptoAlgorithm.SHA256, CryptoAlgorithm.SHA512, CryptoAlgorithm.SHA1, CryptoAlgorithm.MD5),
            "Fun" to listOf(CryptoAlgorithm.ROT13, CryptoAlgorithm.MORSE)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groups.forEach { (name, algos) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalFadingEdges(left = 16.dp, right = 16.dp)
                ) {
                    items(algos) { algo ->
                        FilterChip(
                            selected = selected == algo,
                            onClick = { onSelected(algo) },
                            label = { Text(algo.name) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordStrengthIndicator(strength: Float) {
    val color = when {
        strength < 0.4f -> MaterialTheme.colorScheme.error
        strength < 0.7f -> Color(0xFFB07A00)
        else -> Color(0xFF2E7D32)
    }
    val text = when {
        strength < 0.4f -> "Weak"
        strength < 0.7f -> "Medium"
        else -> "Strong"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Strength", style = MaterialTheme.typography.labelSmall)
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        }
        ExpressiveLinearProgressIndicator(
            progress = { strength },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(60.dp)
            .bouncyClick { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = contentColor, strokeWidth = 2.dp)
        } else {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ResultPanel(
    result: String,
    qrCode: Bitmap?,
    autoClearSeconds: Int,
    onClear: () -> Unit,
    onGenerateQr: () -> Unit,
    onOpenQrFull: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(6.dp).size(14.dp))
                    }
                    Text("Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(visible = autoClearSeconds > 0) {
                        AutoClearBadge(secondsRemaining = autoClearSeconds)
                    }

                    ToolzExpressiveIconButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp),
                        shape = SmallExpressiveShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.1f))
            ) {
                AnimatedContent(
                    targetState = result,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220, delayMillis = 90)))
                            .togetherWith(fadeOut(tween(90)))
                    },
                    label = "result_animation"
                ) { targetResult ->
                    Text(
                        targetResult,
                        modifier = Modifier.padding(18.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(visible = qrCode != null) {
                qrCode?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .clickable(onClick = onOpenQrFull)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                Icons.Rounded.Fullscreen,
                                null,
                                modifier = Modifier.padding(8.dp).size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }

                if (qrCode == null) {
                    FilledTonalButton(onClick = onGenerateQr, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.QrCode, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("QR code")
                    }
                } else {
                    FilledTonalButton(
                        onClick = { saveAndShareQr(context, qrCode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

/**
 * Small circular countdown + seconds label — replaces the old flat error-colored
 * pill. Gives a real sense of time passing without borrowing "danger" red for a
 * routine, expected countdown.
 */
@Composable
private fun AutoClearBadge(secondsRemaining: Int) {
    val progress = (secondsRemaining / 30f).coerceIn(0f, 1f)
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(12.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                strokeWidth = 2.dp,
                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.25f)
            )
            Text(
                "Clears in ${secondsRemaining}s",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ClearAllConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
        title = { Text("Reset everything?") },
        text = { Text("This clears your input, result, and password on this screen. Your saved history stays untouched.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Reset", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    history: List<CryptoHistoryEntry>,
    onDismiss: () -> Unit,
    onDelete: (CryptoHistoryEntry) -> Unit,
    onClear: () -> Unit,
    onSelect: (CryptoHistoryEntry) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear all", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No history yet", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().fadingEdges(top = 16.dp, bottom = 16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history) { entry ->
                        HistoryItem(entry = entry, onClick = { onSelect(entry) }, onDelete = { onDelete(entry) })
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    entry: CryptoHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val time = remember(entry.timestamp, configuration) {
        SimpleDateFormat("MMM d, HH:mm", configuration.locales[0]).format(Date(entry.timestamp))
    }
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            when (entry.type) {
                                "ENCRYPT", "DECRYPT" -> Icons.Rounded.Lock
                                "HASH" -> Icons.Rounded.Fingerprint
                                "ENCODE", "DECODE" -> Icons.Rounded.Code
                                else -> Icons.Rounded.Code
                            },
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.algorithm, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Surface(color = MaterialTheme.colorScheme.secondary.copy(0.1f), shape = CircleShape) {
                            Text(
                                entry.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(entry.result, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(x = 100.dp, y = 0.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("Copy result") },
                onClick = { clipboardManager.setText(AnnotatedString(entry.result)); showMenu = false },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Share result") },
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, entry.result)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share result"))
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { onDelete(); showMenu = false },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenQrDialog(
    result: String,
    foreColor: Int,
    backColor: Int,
    dotStyle: String,
    noteText: String,
    noteSize: Float,
    notePosition: String,
    isNoteEnabled: Boolean,
    qrCode: Bitmap?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onUpdateCustomization: (Int, Int, String, String, Float, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
                    Text("QR customizer", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { qrCode?.let { saveAndShareQr(context, it) } }) {
                        Icon(Icons.Rounded.Share, null)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(backColor))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qrCode?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR code",
                            modifier = Modifier.fillMaxSize().alpha(if (isLoading) 0.5f else 1f),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = Color(foreColor), strokeWidth = 4.dp)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Style & colors", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Dot style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilterChip(
                                        selected = dotStyle == "SQUARE",
                                        onClick = { onUpdateCustomization(foreColor, backColor, "SQUARE", noteText, noteSize, notePosition, isNoteEnabled) },
                                        label = { Text("Square") },
                                        leadingIcon = { if (dotStyle == "SQUARE") Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp)) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    FilterChip(
                                        selected = dotStyle == "ROUNDED",
                                        onClick = { onUpdateCustomization(foreColor, backColor, "ROUNDED", noteText, noteSize, notePosition, isNoteEnabled) },
                                        label = { Text("Rounded") },
                                        leadingIcon = { if (dotStyle == "ROUNDED") Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp)) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick colors (foreground)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                val presets = listOf(
                                    Color.Black, Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFF44336),
                                    Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF009688), Color.White
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
                                    items(presets) { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                .clickable { onUpdateCustomization(color.toArgb(), backColor, dotStyle, noteText, noteSize, notePosition, isNoteEnabled) }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick colors (background)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                val presets = listOf(
                                    Color.White, Color.Black, Color(0xFFF5F5F5), Color(0xFFE3F2FD),
                                    Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFF3E5F5)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
                                    items(presets) { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                .clickable { onUpdateCustomization(foreColor, color.toArgb(), dotStyle, noteText, noteSize, notePosition, isNoteEnabled) }
                                        )
                                    }
                                }
                            }

                            var hexInput by remember { mutableStateOf(String.format("#%06X", (0xFFFFFF and foreColor))) }
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = {
                                    hexInput = it
                                    try {
                                        val color = Color(android.graphics.Color.parseColor(it))
                                        onUpdateCustomization(color.toArgb(), backColor, dotStyle, noteText, noteSize, notePosition, isNoteEnabled)
                                    } catch (e: Exception) { /* invalid hex while typing — ignore until valid */ }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Custom foreground hex") },
                                shape = RoundedCornerShape(16.dp),
                                leadingIcon = { Icon(Icons.Rounded.Palette, null, tint = Color(foreColor)) },
                                singleLine = true
                            )
                        }
                    }

                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.StickyNote2, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Include note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Switch(
                                    checked = isNoteEnabled,
                                    onCheckedChange = { onUpdateCustomization(foreColor, backColor, dotStyle, noteText.ifEmpty { "Your note" }, noteSize, notePosition, it) }
                                )
                            }

                            AnimatedVisibility(visible = isNoteEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedTextField(
                                        value = noteText,
                                        onValueChange = { onUpdateCustomization(foreColor, backColor, dotStyle, it, noteSize, notePosition, isNoteEnabled) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Enter note, link, or text") },
                                        shape = RoundedCornerShape(16.dp)
                                    )

                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Font size", style = MaterialTheme.typography.labelMedium)
                                            Text("${noteSize.toInt()}px", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                        Slider(
                                            value = noteSize,
                                            onValueChange = { onUpdateCustomization(foreColor, backColor, dotStyle, noteText, it, notePosition, isNoteEnabled) },
                                            valueRange = 10f..40f
                                        )
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FilterChip(
                                            selected = notePosition == "TOP",
                                            onClick = { onUpdateCustomization(foreColor, backColor, dotStyle, noteText, noteSize, "TOP", isNoteEnabled) },
                                            label = { Text("Top") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilterChip(
                                            selected = notePosition == "BOTTOM",
                                            onClick = { onUpdateCustomization(foreColor, backColor, dotStyle, noteText, noteSize, "BOTTOM", isNoteEnabled) },
                                            label = { Text("Bottom") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(result)) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy QR content")
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { qrCode?.let { saveToGallery(context, it) } },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = qrCode != null && !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save to gallery", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

fun saveToGallery(context: android.content.Context, bitmap: Bitmap) {
    try {
        val filename = "QR_${System.currentTimeMillis()}.png"
        val outputStream: FileOutputStream
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
            }
            val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = resolver.openOutputStream(imageUri!!) as FileOutputStream
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            outputStream = FileOutputStream(image)
        }
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
        android.widget.Toast.makeText(context, "Saved to gallery", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Failed to save: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun saveAndShareQr(context: android.content.Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "qr_code_${System.currentTimeMillis()}.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = "image/png"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share QR code"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Sharing failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

