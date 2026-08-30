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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.light

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    initialImageUri: String? = null,
    onNavigateToGenerator: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val performanceMode = LocalPerformanceMode.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var scanResult by remember { mutableStateOf("") }
    var isFlashOn by remember { mutableStateOf(false) }
    var isScanningInitialImage by remember { mutableStateOf(false) }

    LaunchedEffect(initialImageUri) {
        if (!initialImageUri.isNullOrEmpty() && initialImageUri != "{initialImageUri}") {
            isScanningInitialImage = true
            try {
                val uri = Uri.parse(initialImageUri)
                val detected = decodeBarcodeFromUri(context, uri)
                if (!detected.isNullOrEmpty()) {
                    scanResult = detected
                } else {
                    Toast.makeText(context, "No QR / barcode detected in image", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to scan image: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isScanningInitialImage = false
            }
        }
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                ZxingBarcodeAnalyzer { result ->
                    if (result.isNotEmpty() && result != scanResult) {
                        scanResult = result
                    }
                }
            )
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_ScannerScreen_f1a2))
                    }
                },
                actions = {
                    if (onNavigateToGenerator != null) {
                        FilledTonalIconButton(onClick = onNavigateToGenerator) {
                            Icon(Icons.Rounded.QrCode, contentDescription = stringResource(R.string.st_ScannerScreen_3d5b))
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            isFlashOn = !isFlashOn
                            cameraController.enableTorch(isFlashOn)
                        }
                    ) {
                        Icon(
                            if (isFlashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = stringResource(R.string.st_ScannerScreen_9e2c)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            controller = cameraController
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                ScannerOverlay(performanceMode)

                if (isScanningInitialImage) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                AnimatedVisibility(
                    visible = scanResult.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ResultCard(
                        result = scanResult,
                        onClose = { scanResult = "" },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(scanResult))
                            Toast.makeText(context, context.getString(R.string.st_ScannerScreen_3c4d), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else {
                PermissionRequestView(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }
}

private class ZxingBarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.EAN_8,
                BarcodeFormat.EAN_13,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.ITF,
                BarcodeFormat.CODABAR,
                BarcodeFormat.PDF_417,
                BarcodeFormat.AZTEC
            ),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
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
            result?.text?.let { onBarcodeDetected(it) }
        } catch (_: Exception) {
            // No barcode found in this frame
        } finally {
            reader.reset()
            image.close()
        }
    }
}

private fun decodeBarcodeFromUri(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result = reader.decode(binaryBitmap)
        result.text
    } catch (_: Exception) {
        null
    }
}

@Composable
fun ScannerOverlay(performanceMode: Boolean) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLinePos by if (performanceMode) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scanLine"
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cutoutSize = w * 0.7f
        val left = (w - cutoutSize) / 2
        val top = (h - cutoutSize) / 2
        val cr = 24.dp.toPx()

        // Dim overlay with rounded cutout
        val dimPath = Path().apply {
            addRect(Rect(0f, 0f, w, h))
            addRoundRect(RoundRect(Rect(left, top, left + cutoutSize, top + cutoutSize), CornerRadius(cr)))
            fillType = PathFillType.EvenOdd
        }
        drawPath(dimPath, Color.Black.copy(alpha = 0.55f))

        // Corner brackets
        val bracketLen = 28.dp.toPx()
        val stroke = 3.dp.toPx()

        // Top-left
        drawArc(primaryColor, 180f, 90f, false, Offset(left, top), Size(cr * 2, cr * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(primaryColor, Offset(left, top + cr), Offset(left, top + cr + bracketLen), stroke, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(left + cr, top), Offset(left + cr + bracketLen, top), stroke, cap = StrokeCap.Round)

        // Top-right
        drawArc(primaryColor, 270f, 90f, false, Offset(left + cutoutSize - cr * 2, top), Size(cr * 2, cr * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(primaryColor, Offset(left + cutoutSize, top + cr), Offset(left + cutoutSize, top + cr + bracketLen), stroke, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(left + cutoutSize - cr - bracketLen, top), Offset(left + cutoutSize - cr, top), stroke, cap = StrokeCap.Round)

        // Bottom-left
        drawArc(primaryColor, 90f, 90f, false, Offset(left, top + cutoutSize - cr * 2), Size(cr * 2, cr * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(primaryColor, Offset(left, top + cutoutSize - cr - bracketLen), Offset(left, top + cutoutSize - cr), stroke, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(left + cr, top + cutoutSize), Offset(left + cr + bracketLen, top + cutoutSize), stroke, cap = StrokeCap.Round)

        // Bottom-right
        drawArc(primaryColor, 0f, 90f, false, Offset(left + cutoutSize - cr * 2, top + cutoutSize - cr * 2), Size(cr * 2, cr * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(primaryColor, Offset(left + cutoutSize, top + cutoutSize - cr - bracketLen), Offset(left + cutoutSize, top + cutoutSize - cr), stroke, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(left + cutoutSize - cr - bracketLen, top + cutoutSize), Offset(left + cutoutSize - cr, top + cutoutSize), stroke, cap = StrokeCap.Round)

        // Scan line
        if (!performanceMode) {
            val lineY = top + cr + ((cutoutSize - cr * 2) * scanLinePos)
            val inset = 12.dp.toPx()
            drawLine(
                color = primaryColor.copy(alpha = 0.6f),
                start = Offset(left + inset, lineY),
                end = Offset(left + cutoutSize - inset, lineY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun ResultCard(
    result: String,
    onClose: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.st_ScannerScreen_1a2b),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.st_ScannerScreen_7c4d),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.st_ScannerScreen_5f6e))
                }

                if (result.startsWith("http") || result.startsWith("www")) {
                    val finalUrl = if (!result.startsWith("http")) "https://$result" else result
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.st_ScannerScreen_2b8a))
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequestView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.st_ScannerScreen_4d9c),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.st_ScannerScreen_6a1b),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.st_ScannerScreen_1b2c))
        }
    }
}
