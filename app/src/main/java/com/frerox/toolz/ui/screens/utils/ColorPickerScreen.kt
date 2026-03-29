package com.frerox.toolz.ui.screens.utils

import android.Manifest
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.Help
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
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
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
    val performanceMode = LocalPerformanceMode.current
    
    var pickedColor by remember { mutableStateOf(Color.White) }
    var hexCode by remember { mutableStateOf("#FFFFFF") }
    val colorHistory = remember { mutableStateListOf<Color>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CHROMA PICKER", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { /* Help */ },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Help, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (cameraPermissionState.status.isGranted) {
                    // Camera Preview Area
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(Color.Black)
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), RoundedCornerShape(40.dp)),
                        color = Color.Black
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

                            // Reticle
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(32.dp).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
                                Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                    
                    // Color Info Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .shadow(8.dp, CircleShape)
                                        .bouncyClick {
                                            if (!colorHistory.contains(pickedColor)) {
                                                colorHistory.add(0, pickedColor)
                                                if (colorHistory.size > 15) colorHistory.removeAt(colorHistory.lastIndex)
                                            }
                                        },
                                    shape = CircleShape,
                                    color = pickedColor,
                                    border = BorderStroke(4.dp, Color.White),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Palette, null, tint = if (pickedColor.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(28.dp).alpha(0.2f))
                                    }
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hexCode, 
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 32.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    @Suppress("DEPRECATION")
                                    Text(
                                        text = "RGB: ${(pickedColor.red * 255).toInt()}, ${(pickedColor.green * 255).toInt()}, ${(pickedColor.blue * 255).toInt()}", 
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                }
                                
                                IconButton(
                                    onClick = { 
                                        clipboardManager.setText(AnnotatedString(hexCode))
                                        if (!colorHistory.contains(pickedColor)) {
                                            colorHistory.add(0, pickedColor)
                                        }
                                    },
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
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
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                @Suppress("DEPRECATION")
                                Text(
                                    "CAPTURE LOG", 
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Black, 
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(colorHistory) { color ->
                                Surface(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .bouncyClick {
                                            pickedColor = color
                                            hexCode = String.format(Locale.US, "#%06X", (0xFFFFFF and color.toArgb()))
                                        },
                                    shape = CircleShape,
                                    color = color,
                                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                                    shadowElevation = 4.dp
                                ) {}
                            }
                            if (colorHistory.isEmpty()) {
                                item {
                                    Text(
                                        "Tap the large color circle to save results", 
                                        style = MaterialTheme.typography.labelMedium, 
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                            Surface(
                                modifier = Modifier.size(120.dp),
                                shape = RoundedCornerShape(40.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(64.dp).alpha(0.5f), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                            Text("IMAGERY ACCESS REQUIRED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            Text(
                                "To extract color data from your physical environment, Toolz needs access to the camera module. All processing is local.", 
                                textAlign = TextAlign.Center, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
                            )
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                            ) {
                                Text("GRANT PERMISSION", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
