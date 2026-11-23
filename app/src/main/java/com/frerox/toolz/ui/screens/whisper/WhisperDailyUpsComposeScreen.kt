/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.data.whisper.WhisperStoryAudience
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
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

/**
 * The whole "new Daily Up" flow as an in-place overlay owned by the hub: step 1
 * takes or picks a photo, step 2 chooses the audience and opens the settings
 * sheet. Keeping it here (instead of a second navigation destination) means the
 * hub's reconcile, quota and viewer state survive the round trip, and there is
 * only one Daily Ups surface to learn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperDailyUpsComposer(
    onDismiss: () -> Unit,
    onPublished: () -> Unit,
    viewModel: WhisperDailyUpsComposeViewModel,
) {
    if (!WhisperDailyUpsConfig.ENABLED) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    SecureWindow()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showAudiencePicker by rememberSaveable { mutableStateOf(false) }
    var showCloseFriendsSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.published) {
        if (state.published) onPublished()
    }

    // A composer reopened after a publish starts clean: no photo means step 1.
    LaunchedEffect(state.photo == null) {
        if (state.photo == null) step = 0
    }

    val requestExit: () -> Unit = {
        if (state.hasDraft) showDiscardDialog = true else onDismiss()
    }

    BackHandler {
        when {
            step == 1 -> step = 0
            state.hasDraft -> showDiscardDialog = true
            else -> onDismiss()
        }
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

    val openPicker: () -> Unit = {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "daily-up-composer-step",
        ) { currentStep ->
            if (currentStep == 0) {
                PhotoStep(
                    state = state,
                    onClose = requestExit,
                    onPick = openPicker,
                    onCaptured = viewModel::setPhoto,
                    onRetake = viewModel::clearPhoto,
                    onNext = { step = 1 },
                    snackbar = snackbar,
                )
            } else {
                AudienceStep(
                    state = state,
                    onBack = { step = 0 },
                    onClose = requestExit,
                    onKind = viewModel::setAudienceKind,
                    onManageCloseFriends = { showCloseFriendsSheet = true },
                    onPickCustom = { showAudiencePicker = true },
                    onSettings = { showSettingsSheet = true },
                    onShare = viewModel::publish,
                    snackbar = snackbar,
                )
            }
        }
    }

    if (showAudiencePicker) {
        CustomAudienceSheet(
            state = state,
            onToggle = viewModel::toggleAudience,
            onSelectAll = viewModel::selectAllAudience,
            onRetryFriends = viewModel::retryFriends,
            onDismiss = { showAudiencePicker = false },
        )
    }

    if (showCloseFriendsSheet) {
        CloseFriendsSheet(
            state = state,
            onToggle = viewModel::setCloseFriend,
            onRetryFriends = viewModel::retryFriends,
            onDismiss = { showCloseFriendsSheet = false },
        )
    }

    if (showSettingsSheet) {
        ComposerSettingsSheet(
            receiptsOn = state.receiptsOn,
            saveToDevice = state.saveToDevice,
            onReceipts = viewModel::setReceipts,
            onSaveToDevice = viewModel::setSaveToDevice,
            onManageCloseFriends = {
                showSettingsSheet = false
                showCloseFriendsSheet = true
            },
            onDismiss = { showSettingsSheet = false },
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
                        onDismiss()
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
// Step 1 — camera or gallery
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoStep(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onClose: () -> Unit,
    onPick: () -> Unit,
    onCaptured: (ByteArray, String) -> Unit,
    onRetake: () -> Unit,
    onNext: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val photo = state.photo
    if (photo != null) {
        PhotoReview(
            state = state,
            onClose = onClose,
            onRetake = onRetake,
            onPick = onPick,
            onNext = onNext,
            snackbar = snackbar,
        )
    } else {
        LiveCameraStep(
            onClose = onClose,
            onPick = onPick,
            onCaptured = onCaptured,
            snackbar = snackbar,
        )
    }
}

@Composable
private fun PhotoReview(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onClose: () -> Unit,
    onRetake: () -> Unit,
    onPick: () -> Unit,
    onNext: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val photo = state.photo ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Image(
            bitmap = photo.preview,
            contentDescription = stringResource(R.string.st_Whisper_DailyUp_PhotoPreviewCd),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // Readability scrims for the controls over an arbitrary photo.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                    ),
                )
                .align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                )
                .align(Alignment.BottomCenter),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = GroupCd.close(),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            StepPill(step = 1)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolzExpressiveButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.Cached, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_Whisper_DailyUp_Retake))
                }
                ToolzExpressiveButton(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_NextStep), fontWeight = FontWeight.SemiBold)
                }
            }
            TextButton(onClick = onPick) {
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_PickDifferent),
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        GroupSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
        )
    }
}

@Composable
private fun LiveCameraStep(
    onClose: () -> Unit,
    onPick: () -> Unit,
    onCaptured: (ByteArray, String) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraFailed by remember { mutableStateOf(false) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var capturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted && !cameraFailed) {
            CameraPreview(
                lensFacing = lensFacing,
                onReady = { capture ->
                    imageCapture = capture
                    capture.flashMode = if (flashOn) {
                        ImageCapture.FLASH_MODE_ON
                    } else {
                        ImageCapture.FLASH_MODE_OFF
                    }
                },
                onFailed = { cameraFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CameraUnavailable(
                permissionGranted = permissionGranted,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onPick = onPick,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Scrims keep the controls readable over any camera frame.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.64f), Color.Transparent),
                    ),
                )
                .align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .align(Alignment.BottomCenter),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = GroupCd.close(),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            StepPill(step = 1)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { flashOn = !flashOn },
                enabled = permissionGranted && !cameraFailed,
            ) {
                Icon(
                    if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = stringResource(
                        if (flashOn) {
                            R.string.st_Whisper_DailyUp_FlashOffCd
                        } else {
                            R.string.st_Whisper_DailyUp_FlashOnCd
                        },
                    ),
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gallery shortcut on the left, shutter centred, flip on the right.
                FilledTonalIconButton(
                    onClick = onPick,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.st_Whisper_DailyUp_PickPhotoCd),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                ShutterButton(
                    capturing = capturing,
                    enabled = permissionGranted && !cameraFailed,
                    onClick = {
                        val capture = imageCapture ?: return@ShutterButton
                        capturing = true
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bytes = image.planes.firstOrNull()
                                        ?.buffer
                                        ?.toByteArray()
                                    image.close()
                                    capturing = false
                                    if (bytes == null || bytes.isEmpty()) {
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                context.getString(R.string.st_Whisper_DailyUp_CaptureFailed),
                                            )
                                        }
                                    } else {
                                        onCaptured(bytes, "image/jpeg")
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            context.getString(R.string.st_Whisper_DailyUp_CaptureFailed),
                                        )
                                    }
                                }
                            },
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    enabled = permissionGranted && !cameraFailed,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        Icons.Rounded.Cached,
                        contentDescription = stringResource(R.string.st_Whisper_DailyUp_FlipCameraCd),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_CameraHint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        if (capturing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        GroupSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
        )
    }
}

@Composable
private fun CameraUnavailable(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(
                if (permissionGranted) {
                    R.string.st_Whisper_DailyUp_CameraUnavailable
                } else {
                    R.string.st_Whisper_DailyUp_CameraPermissionTitle
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (permissionGranted) {
                    R.string.st_Whisper_DailyUp_CameraUnavailableDesc
                } else {
                    R.string.st_Whisper_DailyUp_CameraPermissionDesc
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (!permissionGranted) {
            ToolzExpressiveButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.st_Whisper_DailyUp_AllowCamera))
            }
            Spacer(Modifier.height(8.dp))
        }
        ToolzExpressiveButton(
            onClick = onPick,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_Whisper_DailyUp_ChooseFromGallery))
        }
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    onReady: (ImageCapture) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnFailed by rememberUpdatedState(onFailed)

    DisposableEffect(lensFacing) {
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        future.addListener(
            {
                if (disposed) return@addListener
                runCatching {
                    provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .build()
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    provider?.unbindAll()
                    provider?.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    currentOnReady(capture)
                }.onFailure {
                    currentOnFailed()
                }
            },
            executor,
        )

        onDispose {
            disposed = true
            runCatching { provider?.unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

@Composable
private fun ShutterButton(
    capturing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled && !capturing,
        modifier = Modifier.size(78.dp),
        shape = CircleShape,
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.4f),
        ),
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .border(3.dp, Color.Black.copy(alpha = 0.18f), CircleShape),
        )
    }
}

@Composable
private fun StepPill(step: Int) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
    ) {
        Text(
            text = stringResource(R.string.st_Whisper_DailyUp_StepOf, step, 2),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Step 2 — audience + settings
// ────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudienceStep(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onKind: (WhisperStoryAudience) -> Unit,
    onManageCloseFriends: () -> Unit,
    onPickCustom: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { GroupSnackbarHost(state = snackbar) },
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.st_Whisper_DailyUp_ComposeTitle),
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = {
                    Text(stringResource(R.string.st_Whisper_DailyUp_StepTwoSubtitle))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = GroupCd.back(),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.st_Whisper_DailyUp_SettingsCd),
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = GroupCd.close(),
                        )
                    }
                },
            )
        },
        bottomBar = {
            ComposerShareBar(state = state, onShare = onShare)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoStrip(state = state, onManageCloseFriends = onManageCloseFriends)

            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_WhoCanSee),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )

            AudienceOptionCard(
                selected = state.audienceKind == WhisperStoryAudience.EVERYONE,
                icon = Icons.Rounded.Public,
                title = stringResource(R.string.st_Whisper_DailyUp_AudienceEveryoneTitle),
                description = if (state.friendsLoading) {
                    stringResource(R.string.st_Whisper_DailyUp_LoadingFriends)
                } else {
                    stringResource(
                        R.string.st_Whisper_DailyUp_AudienceEveryoneDesc,
                        state.friends.size,
                    )
                },
                onClick = { onKind(WhisperStoryAudience.EVERYONE) },
            )

            AudienceOptionCard(
                selected = state.audienceKind == WhisperStoryAudience.CLOSE_FRIENDS,
                icon = Icons.Rounded.Favorite,
                accent = closeFriendsRingColor(),
                title = stringResource(R.string.st_Whisper_DailyUp_CloseFriends),
                description = if (state.closeAudienceSize == 0) {
                    stringResource(R.string.st_Whisper_DailyUp_CloseFriendsEmpty)
                } else {
                    stringResource(
                        R.string.st_Whisper_DailyUp_AudiencePeople,
                        state.closeAudienceSize,
                    )
                },
                actionLabel = stringResource(R.string.st_Whisper_DailyUp_Manage),
                onAction = onManageCloseFriends,
                onClick = { onKind(WhisperStoryAudience.CLOSE_FRIENDS) },
            )

            AudienceOptionCard(
                selected = state.audienceKind == WhisperStoryAudience.CUSTOM,
                icon = Icons.Rounded.Groups,
                title = stringResource(R.string.st_Whisper_DailyUp_AudienceCustomTitle),
                description = when {
                    state.audienceKind != WhisperStoryAudience.CUSTOM ->
                        stringResource(R.string.st_Whisper_DailyUp_AudienceCustomDesc)
                    state.audienceSize == 0 ->
                        stringResource(R.string.st_Whisper_DailyUp_AudienceCustomEmpty)
                    else -> stringResource(
                        R.string.st_Whisper_DailyUp_AudiencePeople,
                        state.audienceSize,
                    )
                },
                actionLabel = stringResource(R.string.st_Whisper_DailyUp_ChoosePeople),
                onAction = {
                    onKind(WhisperStoryAudience.CUSTOM)
                    onPickCustom()
                },
                onClick = {
                    onKind(WhisperStoryAudience.CUSTOM)
                    onPickCustom()
                },
            )

            if (state.friendsFailed) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.st_Whisper_DailyUp_FriendsFailed),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (state.audienceCapped && !state.friendsLoading) {
                Text(
                    text = stringResource(
                        R.string.st_Whisper_DailyUp_AudienceCapped,
                        state.friends.size,
                        WhisperDailyUpsConfig.MAX_RECIPIENTS,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Settings are visible as a compact summary, not a duplicate surface:
            // the gear opens the editable sheet.
            ExpressiveCard(
                onClick = onSettings,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.st_Whisper_DailyUp_SettingsTitle),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(
                                R.string.st_Whisper_DailyUp_SettingsSummary,
                                if (state.receiptsOn) {
                                    stringResource(R.string.st_Whisper_DailyUp_On)
                                } else {
                                    stringResource(R.string.st_Whisper_DailyUp_Off)
                                },
                                if (state.saveToDevice) {
                                    stringResource(R.string.st_Whisper_DailyUp_On)
                                } else {
                                    stringResource(R.string.st_Whisper_DailyUp_Off)
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoStrip(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onManageCloseFriends: () -> Unit,
) {
    val photo = state.photo ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .width(76.dp)
                .aspectRatio(DAILY_UP_PREVIEW_ASPECT),
        ) {
            Image(
                bitmap = photo.preview,
                contentDescription = stringResource(R.string.st_Whisper_DailyUp_PhotoPreviewCd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_ReadyToShare),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_DisappearsIn24h),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.closeAudienceSize == 0) {
                TextButton(
                    onClick = onManageCloseFriends,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                ) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_AddCloseFriends))
                }
            }
        }
    }
}

@Composable
private fun AudienceOptionCard(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    ExpressiveCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) accent.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ComposerShareBar(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onShare: () -> Unit,
) {
    val block = state.shareBlock
    val reason: String? = when (block) {
        ShareBlock.NoPhoto -> stringResource(R.string.st_Whisper_DailyUp_NeedPhoto)
        ShareBlock.NoQuota -> stringResource(R.string.st_Whisper_DailyUp_NoUpsLeft)
        ShareBlock.TooManyPeople -> stringResource(R.string.st_Whisper_DailyUp_ErrAudience)
        ShareBlock.NoAudience -> when {
            state.friendsLoading || state.friendsFailed -> null
            state.friends.isEmpty() -> stringResource(R.string.st_Whisper_DailyUp_NoFriends)
            state.audienceKind == WhisperStoryAudience.CLOSE_FRIENDS ->
                stringResource(R.string.st_Whisper_DailyUp_CloseFriendsEmpty)
            state.audienceKind == WhisperStoryAudience.CUSTOM ->
                stringResource(R.string.st_Whisper_DailyUp_AudienceCustomEmpty)
            else -> stringResource(R.string.st_Whisper_DailyUp_AudienceWarning)
        }
        else -> null
    }
    val enabled = state.photo != null &&
        state.remaining > 0 &&
        state.audienceSize > 0 &&
        !state.audienceOverLimit &&
        !state.publishing &&
        !state.published

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
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
                        color = if (block == ShareBlock.NoAudience && state.friends.isNotEmpty()) {
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
// Sheets
// ────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSettingsSheet(
    receiptsOn: Boolean,
    saveToDevice: Boolean,
    onReceipts: (Boolean) -> Unit,
    onSaveToDevice: (Boolean) -> Unit,
    onManageCloseFriends: () -> Unit,
    onDismiss: () -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_SettingsTitle),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            SettingsToggleRow(
                title = stringResource(R.string.st_Whisper_DailyUp_ReceiptsLabel),
                description = stringResource(R.string.st_Whisper_DailyUp_ReceiptsDesc),
                checked = receiptsOn,
                onCheckedChange = onReceipts,
            )
            SettingsToggleRow(
                title = stringResource(R.string.st_Whisper_DailyUp_SaveToDeviceLabel),
                description = stringResource(R.string.st_Whisper_DailyUp_SaveToDeviceDesc),
                checked = saveToDevice,
                onCheckedChange = onSaveToDevice,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onManageCloseFriends)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = closeFriendsRingColor(),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_ManageCloseFriends),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_Manage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpressiveSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomAudienceSheet(
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
                        text = stringResource(R.string.st_Whisper_DailyUp_AudienceCustomTitle),
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
                            enabled = state.customSelected.size < state.friends.size,
                        ) {
                            Text(
                                if (state.audienceCapped) {
                                    stringResource(
                                        R.string.st_Whisper_DailyUp_AudienceSelectMax,
                                        WhisperDailyUpsConfig.MAX_RECIPIENTS,
                                    )
                                } else {
                                    stringResource(R.string.st_Whisper_DailyUp_AudienceSelectAll)
                                },
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp),
                    ) {
                        items(filtered, key = { it.id }) { friend ->
                            val included = friend.id in state.customSelected
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseFriendsSheet(
    state: WhisperDailyUpsComposeViewModel.ComposeUiState,
    onToggle: (String, Boolean) -> Unit,
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
                .heightIn(max = 660.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.st_Whisper_DailyUp_CloseFriends),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            R.string.st_Whisper_DailyUp_CloseFriendsCount,
                            state.closeAudienceSize,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.st_Whisper_DailyUp_AudienceDone))
                }
            }
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_CloseFriendsHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 440.dp),
                    ) {
                        items(filtered, key = { it.id }) { friend ->
                            val close = friend.id in state.closeFriends
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .toggleable(
                                        value = close,
                                        role = Role.Checkbox,
                                        onValueChange = { onToggle(friend.id, it) },
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
                                if (close) {
                                    Icon(
                                        Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = closeFriendsRingColor(),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Checkbox(checked = close, onCheckedChange = null)
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

private fun ByteBuffer.toByteArray(): ByteArray {
    val copy = duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return bytes
}
