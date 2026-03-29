@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.light

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SCANNER", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            isFlashOn = !isFlashOn
                            cameraController.enableTorch(isFlashOn)
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            if (isFlashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = "Flash",
                            tint = if (isFlashOn) Color.Yellow else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
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
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
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

@Composable
fun ScannerOverlay(performanceMode: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLinePos by if (performanceMode) remember { mutableFloatStateOf(0.5f) } else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "line"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val boxSize = width * 0.7f
        val left = (width - boxSize) / 2
        val top = (height - boxSize) / 2
        val cornerRadius = 32.dp.toPx()

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
        drawPath(path, Color.Black.copy(alpha = 0.65f))
        
        val lineLength = 40.dp.toPx()
        val strokeWidth = 6.dp.toPx()
        val cornerColor = Color.White
        
        // Corners
        drawLine(cornerColor, Offset(left, top + lineLength), Offset(left, top + cornerRadius), strokeWidth, cap = StrokeCap.Round)
        drawArc(
            color = cornerColor,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(cornerColor, Offset(left + cornerRadius, top), Offset(left + lineLength, top), strokeWidth, cap = StrokeCap.Round)
        
        drawLine(cornerColor, Offset(left + boxSize - lineLength, top), Offset(left + boxSize - cornerRadius, top), strokeWidth, cap = StrokeCap.Round)
        drawArc(
            color = cornerColor,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left + boxSize - cornerRadius * 2, top),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(cornerColor, Offset(left + boxSize, top + cornerRadius), Offset(left + boxSize, top + lineLength), strokeWidth, cap = StrokeCap.Round)
        
        drawLine(cornerColor, Offset(left, top + boxSize - lineLength), Offset(left, top + boxSize - cornerRadius), strokeWidth, cap = StrokeCap.Round)
        drawArc(
            color = cornerColor,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left, top + boxSize - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(cornerColor, Offset(left + cornerRadius, top + boxSize), Offset(left + lineLength, top + boxSize), strokeWidth, cap = StrokeCap.Round)
        
        drawLine(cornerColor, Offset(left + boxSize - lineLength, top + boxSize), Offset(left + boxSize - cornerRadius, top + boxSize), strokeWidth, cap = StrokeCap.Round)
        drawArc(
            color = cornerColor,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left + boxSize - cornerRadius * 2, top + boxSize - cornerRadius * 2),
            size = Size(cornerRadius * 2, cornerRadius * 2),
            style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(cornerColor, Offset(left + boxSize, top + boxSize - cornerRadius), Offset(left + boxSize, top + boxSize - lineLength), strokeWidth, cap = StrokeCap.Round)

        if (!performanceMode) {
            val lineY = top + (boxSize * scanLinePos)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0f)),
                    startY = lineY - 20.dp.toPx(),
                    endY = lineY + 20.dp.toPx()
                ),
                topLeft = Offset(left + 4.dp.toPx(), lineY - 1.dp.toPx()),
                size = Size(boxSize - 8.dp.toPx(), 2.dp.toPx())
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
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        @Suppress("DEPRECATION")
                        Text(
                            "SCAN RESULT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("COPY", fontWeight = FontWeight.Black)
                }
                
                if (result.startsWith("http") || result.startsWith("www")) {
                    val finalUrl = if (!result.startsWith("http")) "https://$result" else result
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("OPEN", fontWeight = FontWeight.Black)
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
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
            "CAMERA ACCESS",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The scanner needs camera access to read QR codes and barcodes. All processing happens locally on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            @Suppress("DEPRECATION")
            Text("GRANT PERMISSION", fontWeight = FontWeight.Black)
        }
    }
}
