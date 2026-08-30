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

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.screens.light.ScannerOverlay
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.util.QREngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// V3-FIX: single source of truth for the QR verification payload so the encoder below and
// any future decoder can never drift apart.
private const val VERIFY_PAYLOAD_PREFIX = "whisper-verify:"
private const val FINGERPRINT_HEX_LENGTH = 64

/**
 * Builds "whisper-verify:<username>:<fingerprint-hex-without-dashes>" from a fingerprint in
 * the canonical dashed form produced by [com.frerox.toolz.data.whisper.WhisperCrypto.computeFingerprint].
 */
internal fun whisperVerifyPayload(username: String, fingerprint: String): String =
    VERIFY_PAYLOAD_PREFIX + username + ":" + fingerprint.replace("-", "")

/**
 * Parses and validates a scanned verification payload.
 * Returns (username, canonical dashed uppercase fingerprint) or null when the payload is not
 * a well-formed Whisper verification code (wrong prefix, blank username, or fingerprint that
 * is not exactly 64 hex chars). Never trusts partial/garbled input — fail closed.
 */
internal fun parseWhisperVerifyPayload(raw: String): Pair<String, String>? {
    if (!raw.startsWith(VERIFY_PAYLOAD_PREFIX)) return null
    val parts = raw.removePrefix(VERIFY_PAYLOAD_PREFIX).split(":")
    if (parts.size != 2) return null
    val username = parts[0].trim()
    val hex = parts[1].trim().replace("-", "").uppercase()
    if (username.isEmpty() || hex.length != FINGERPRINT_HEX_LENGTH) return null
    if (hex.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
    // Re-dash into the same shape WhisperCrypto.computeFingerprint produces so comparison
    // against the locally computed fingerprint is a plain string equals.
    return username to hex.chunked(4).joinToString("-")
}

/**
 * V3-FIX: shows MY verification QR so the partner can scan it instead of comparing eight
 * hex groups by eye. The bitmap is generated OFF the main thread with the same zxing-based
 * engine the QR Generator tool uses ([QREngine]); nothing but the payload string goes into
 * the code — no keys, no identifiers beyond username+fingerprint.
 */
@Composable
internal fun WhisperVerifyQrDialog(
    username: String,
    fingerprint: String,
    onDismiss: () -> Unit,
) {
    val payload = remember(username, fingerprint) { whisperVerifyPayload(username, fingerprint) }
    // V3-FIX: zxing encode + Bitmap drawing must never run during composition — produce it
    // on Dispatchers.Default keyed on the immutable payload.
    val qrBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = payload) {
        value = withContext(Dispatchers.Default) {
            runCatching { QREngine.generate(payload, size = 768) }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Rounded.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(stringResource(R.string.st_Whisper_QrDialogTitle), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.st_Whisper_QrDialogDesc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                ) {
                    Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                        val bmp = qrBitmap
                        if (bmp == null) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        } else {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_Whisper_VerifyQr),
                                modifier = Modifier.size(240.dp),
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = fingerprint,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            ToolzTonalExpressiveButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_Whisper_Close), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * V3-FIX: full-screen scanner for the partner's verification QR. Reuses the exact CameraX +
 * ML Kit pipeline of the existing Scanner tool ([ScannerScreen]'s LifecycleCameraController +
 * MlKitAnalyzer pattern and its ScannerOverlay cutout), narrowed to QR_CODE only.
 *
 * Trust model: the scanned fingerprint is compared against the LOCALLY computed fingerprint
 * of the partner's stored public key ([expectedPartnerFingerprint]) — never against anything
 * displayed by the server inside the QR itself.
 */
@Composable
internal fun WhisperQrScanDialog(
    partnerName: String,
    expectedPartnerFingerprint: String?,
    toastState: WhisperToastState,
    haptic: com.frerox.toolz.ui.components.ToolzHapticFeedback,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val performanceMode = LocalPerformanceMode.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // V3-FIX: one-shot guard shared across analyzer frames — without it a single scan fires
    // dozens of callbacks before the dialog closes, spamming toasts/haptics.
    val scanHandled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    // V3-FIX: analyzer closure is created once (remembered controller); every per-composition
    // value it needs is read through refs/states so no stale captures are possible.
    val expectedRef = rememberUpdatedState(expectedPartnerFingerprint)
    val verifiedMsgRef = rememberUpdatedState(context.getString(R.string.st_Whisper_QrVerifiedToast, partnerName))
    val invalidMsgRef = rememberUpdatedState(context.getString(R.string.st_Whisper_Error_QrInvalid))
    val onVerifiedRef = rememberUpdatedState(onVerified)
    val showMismatchWarning: MutableState<Boolean> = remember { mutableStateOf(false) }

    val handleScan: (String) -> Unit = { raw ->
        if (!scanHandled.get()) {
            val parsed = parseWhisperVerifyPayload(raw)
            val expected = expectedRef.value
            when {
                parsed == null || expected == null ->
                    toastState.show(invalidMsgRef.value, WhisperToastType.ERROR)
                parsed.second == expected -> {
                    // Match: verified state refreshes automatically via getKeyTrustInfo once
                    // the caller runs its verifyKey() path.
                    scanHandled.set(true)
                    haptic.success()
                    toastState.show(verifiedMsgRef.value, WhisperToastType.SUCCESS)
                    onVerifiedRef.value.invoke()
                }
                else -> {
                    // Mismatch: never auto-accept; force explicit acknowledgment.
                    scanHandled.set(true)
                    showMismatchWarning.value = true
                }
            }
        }
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                ZxingQrCodeAnalyzer { raw ->
                    if (!scanHandled.get()) {
                        if (raw.isNotEmpty()) handleScan(raw)
                    }
                }
            )
        }
    }

    // V3-FIX: bind only while this dialog exists AND permission is granted; unbind releases
    // the camera (and stops frame analysis) the moment the dialog leaves composition.
    // Frames are consumed by ML Kit in-memory only — no image is ever cached or retained.
    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            runCatching { cameraController.bindToLifecycle(lifecycleOwner) }
        }
        onDispose {
            runCatching { cameraController.unbind() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply { controller = cameraController }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    ScannerOverlay(performanceMode)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp, start = 32.dp, end = 32.dp),
                    ) {
                        Text(
                            stringResource(R.string.st_Whisper_QrScanHint, partnerName),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                } else {
                    // Graceful closed state: rationale + retry, never a dead black screen.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.st_Whisper_ScanDialogTitle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.st_Whisper_QrCameraRationale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        ToolzTonalExpressiveButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text(stringResource(R.string.st_Whisper_QrGrantCamera), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.st_Whisper_Close),
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.st_Whisper_ScanDialogTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }

    if (showMismatchWarning.value) {
        AlertDialog(
            // Sticky like KeyVerifyDialog: an impersonation alarm must be acknowledged
            // explicitly via Close, not dismissed by an accidental outside tap.
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            icon = {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
            },
            title = {
                Text(
                    stringResource(R.string.st_Whisper_QrMismatchTitle),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            text = {
                Text(
                    stringResource(R.string.st_Whisper_QrMismatchDesc, partnerName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showMismatchWarning.value = false
                        onDismiss()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.st_Whisper_Close), fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

private class ZxingQrCodeAnalyzer(
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val data: ByteArray
            if (rowStride == width && pixelStride == 1) {
                data = ByteArray(buffer.remaining())
                buffer.get(data)
            } else {
                data = ByteArray(width * height)
                val rowBuffer = ByteArray(rowStride)
                for (row in 0 until height) {
                    buffer.position(row * rowStride)
                    buffer.get(rowBuffer, 0, minOf(rowStride, buffer.remaining()))
                    for (col in 0 until width) {
                        data[row * width + col] = rowBuffer[col * pixelStride]
                    }
                }
            }

            val rotationDegrees = image.imageInfo.rotationDegrees
            val rotatedData = if (rotationDegrees == 90 || rotationDegrees == 270) {
                val rotated = ByteArray(data.size)
                if (rotationDegrees == 90) {
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            rotated[x * height + (height - y - 1)] = data[x + y * width]
                        }
                    }
                } else {
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            rotated[(width - x - 1) * height + y] = data[x + y * width]
                        }
                    }
                }
                rotated
            } else if (rotationDegrees == 180) {
                val rotated = ByteArray(data.size)
                for (i in data.indices) {
                    rotated[data.size - 1 - i] = data[i]
                }
                rotated
            } else {
                data
            }

            val finalWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
            val finalHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

            val source = PlanarYUVLuminanceSource(
                rotatedData, finalWidth, finalHeight, 0, 0, finalWidth, finalHeight, false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val result = reader.decodeWithState(binaryBitmap)
            result?.text?.let { onQrDetected(it) }
        } catch (_: Exception) {
            // No QR detected in this frame
        } finally {
            reader.reset()
            image.close()
        }
    }
}
