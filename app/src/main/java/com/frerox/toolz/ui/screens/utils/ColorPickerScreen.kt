package com.frerox.toolz.ui.screens.utils

import android.Manifest
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ColorPickerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val clipboardManager = LocalClipboardManager.current
    
    var pickedColor by remember { mutableStateOf(Color.White) }
    var hexCode by remember { mutableStateOf("#FFFFFF") }
    val colorHistory = remember { mutableStateListOf<Color>() }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("CHROMA PICKER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Help */ }) {
                            Icon(Icons.Rounded.Help, null)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (cameraPermissionState.status.isGranted) {
                // Camera Preview with Rounded Corners
                Surface(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxWidth()
                        .padding(24.dp)
                        .shadow(24.dp, RoundedCornerShape(44.dp)),
                    shape = RoundedCornerShape(44.dp),
                    color = Color.Black,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                        .build()
                                    
                                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                        val bitmap = imageProxy.toBitmap()
                                        val centerX = bitmap.width / 2
                                        val centerY = bitmap.height / 2
                                        if (centerX in 0 until bitmap.width && centerY in 0 until bitmap.height) {
                                            val pixel = bitmap.getPixel(centerX, centerY)
                                            pickedColor = Color(pixel)
                                            hexCode = String.format(Locale.US, "#%06X", (0xFFFFFF and pixel))
                                        }
                                        imageProxy.close()
                                    }
                                    
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        Log.e("ColorPicker", "Binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Target Reticle (Premium)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(32.dp).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
                            Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
                            
                            // Crosshair lines
                            Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.White.copy(alpha = 0.5f)).align(Alignment.TopCenter))
                            Box(modifier = Modifier.width(1.5.dp).height(12.dp).background(Color.White.copy(alpha = 0.5f)).align(Alignment.BottomCenter))
                            Box(modifier = Modifier.width(12.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.5f)).align(Alignment.CenterStart))
                            Box(modifier = Modifier.width(12.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.5f)).align(Alignment.CenterEnd))
                        }
                    }
                }
                
                // Color Info Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(28.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(90.dp)
                                    .shadow(16.dp, CircleShape)
                                    .bouncyClick {
                                        if (!colorHistory.contains(pickedColor)) {
                                            colorHistory.add(0, pickedColor)
                                            if (colorHistory.size > 12) colorHistory.removeAt(colorHistory.lastIndex)
                                        }
                                    },
                                shape = CircleShape,
                                color = pickedColor,
                                border = androidx.compose.foundation.BorderStroke(4.dp, Color.White),
                                tonalElevation = 8.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Palette, null, tint = if (pickedColor.red > 0.5f) Color.Black else Color.White, modifier = Modifier.size(32.dp).alpha(0.2f))
                                }
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hexCode, 
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = (-2).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "RGB: ${(pickedColor.red * 255).toInt()}, ${(pickedColor.green * 255).toInt()}, ${(pickedColor.blue * 255).toInt()}", 
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            FilledTonalIconButton(
                                onClick = { 
                                    clipboardManager.setText(AnnotatedString(hexCode))
                                    if (!colorHistory.contains(pickedColor)) {
                                        colorHistory.add(0, pickedColor)
                                        if (colorHistory.size > 12) colorHistory.removeAt(colorHistory.lastIndex)
                                    }
                                },
                                modifier = Modifier.size(64.dp).bouncyClick {},
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }

                // History Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.History, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "COLOR HISTORY", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fadingEdge(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.05f to Color.Black,
                                    0.95f to Color.Black,
                                    1f to Color.Transparent
                                ),
                                length = 32.dp
                            )
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(colorHistory) { color ->
                                Surface(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .bouncyClick {
                                            pickedColor = color
                                            hexCode = String.format(Locale.US, "#%06X", (0xFFFFFF and color.toArgb()))
                                        },
                                    shape = CircleShape,
                                    color = color,
                                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    tonalElevation = 4.dp
                                ) {}
                            }
                            if (colorHistory.isEmpty()) {
                                item {
                                    Text(
                                        "Saved colors will appear here", 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(64.dp).alpha(0.2f), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        Text("CAMERA REQUIRED", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "To pick colors from your environment, please enable camera access in permissions.", 
                            textAlign = TextAlign.Center, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(40.dp))
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            Text("GRANT ACCESS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}
