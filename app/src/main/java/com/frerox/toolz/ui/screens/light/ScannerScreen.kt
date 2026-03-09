package com.frerox.toolz.ui.screens.light

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val clipboardManager = LocalClipboardManager.current
    
    var scanResult by remember { mutableStateOf("") }
    var isFlashOn by remember { mutableStateOf(false) }
    
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                MlKitAnalyzer(
                    listOf(scanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(context)
                ) { result: MlKitAnalyzer.Result ->
                    val barcodes = result.getValue(scanner)
                    if (!barcodes.isNullOrEmpty()) {
                        val firstBarcode = barcodes[0].rawValue ?: ""
                        if (firstBarcode.isNotEmpty() && firstBarcode != scanResult) {
                            scanResult = firstBarcode
                        }
                    }
                }
            )
        }
    }

    // Bind the controller to the lifecycle to fix blank camera issue
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scanner", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isFlashOn = !isFlashOn
                        cameraController.enableTorch(isFlashOn)
                    }) {
                        Icon(
                            if (isFlashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = "Flash"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (cameraPermissionState.status.isGranted) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            controller = cameraController
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                ScannerOverlay()
                
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
                        }
                    )
                }
            } else {
                PermissionRequestView(
                    onRequest = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
fun ScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLinePos by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "line"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val boxSize = width * 0.75f
        val left = (width - boxSize) / 2
        val top = (height - boxSize) / 2
        val cornerRadius = 32.dp.toPx()

        // Draw darkened background with transparent hole
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(width, 0f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
            
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + boxSize, top + boxSize),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            )
            fillType = PathFillType.EvenOdd
        }
        drawPath(path, Color.Black.copy(alpha = 0.7f))
        
        // Draw corners
        val lineLength = 40.dp.toPx()
        val strokeWidth = 5.dp.toPx()
        val cornerColor = Color.Cyan
        
        // Top Left
        drawLine(cornerColor, Offset(left, top + lineLength), Offset(left, top + cornerRadius), strokeWidth)
        drawArc(
            color = cornerColor,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
        )
        drawLine(cornerColor, Offset(left + cornerRadius, top), Offset(left + lineLength, top), strokeWidth)
        
        // Top Right
        drawLine(cornerColor, Offset(left + boxSize - lineLength, top), Offset(left + boxSize - cornerRadius, top), strokeWidth)
        drawArc(
            color = cornerColor,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left + boxSize - cornerRadius * 2, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
        )
        drawLine(cornerColor, Offset(left + boxSize, top + cornerRadius), Offset(left + boxSize, top + lineLength), strokeWidth)
        
        // Bottom Left
        drawLine(cornerColor, Offset(left, top + boxSize - lineLength), Offset(left, top + boxSize - cornerRadius), strokeWidth)
        drawArc(
            color = cornerColor,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, top + boxSize - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
        )
        drawLine(cornerColor, Offset(left + cornerRadius, top + boxSize), Offset(left + lineLength, top + boxSize), strokeWidth)
        
        // Bottom Right
        drawLine(cornerColor, Offset(left + boxSize - lineLength, top + boxSize), Offset(left + boxSize - cornerRadius, top + boxSize), strokeWidth)
        drawArc(
            color = cornerColor,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left + boxSize - cornerRadius * 2, top + boxSize - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
        )
        drawLine(cornerColor, Offset(left + boxSize, top + boxSize - cornerRadius), Offset(left + boxSize, top + boxSize - lineLength), strokeWidth)

        // Animated scan line
        val lineY = top + (boxSize * scanLinePos)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Cyan.copy(alpha = 0f), Color.Cyan, Color.Cyan.copy(alpha = 0f)),
                startY = lineY - 15.dp.toPx(),
                endY = lineY + 15.dp.toPx()
            ),
            topLeft = Offset(left + 10.dp.toPx(), lineY - 1.dp.toPx()),
            size = Size(boxSize - 20.dp.toPx(), 2.dp.toPx())
        )
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
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Scan Result",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    maxLines = 5,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                
                if (result.startsWith("http") || result.startsWith("www")) {
                    val finalUrl = if (!result.startsWith("http")) "https://$result" else result
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Handle error
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequestView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Camera,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The scanner needs camera access to read QR codes and barcodes. Please grant permission to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Grant Permission", style = MaterialTheme.typography.labelLarge)
        }
    }
}
