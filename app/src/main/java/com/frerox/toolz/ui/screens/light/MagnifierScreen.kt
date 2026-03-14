package com.frerox.toolz.ui.screens.light

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MagnifierScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val scope = rememberCoroutineScope()
    
    var zoomLevel by remember { mutableFloatStateOf(0f) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun playShutterSound() {
        scope.launch {
            if (settingsRepository.shutterSoundEnabled.first()) {
                val customUri = settingsRepository.shutterSoundUri.first()
                val mediaPlayer = if (!customUri.isNullOrEmpty()) {
                    try {
                        MediaPlayer.create(context, Uri.parse(customUri))
                    } catch (e: Exception) {
                        MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                    }
                } else {
                    MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                }
                mediaPlayer?.start()
                mediaPlayer?.setOnCompletionListener { it.release() }
            }
        }
    }

    fun captureImage() {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Toolz-Magnifier")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        playShutterSound()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MAGNIFIER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(surfaceVariantColor.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (cameraPermissionState.status.isGranted) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(Color.Black)
                        .border(BorderStroke(2.dp, primaryColor.copy(alpha = 0.2f)), RoundedCornerShape(40.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                
                                try {
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                    cameraControl = camera.cameraControl
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Center Crosshair
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
                    }

                    // Capture Button
                    FloatingActionButton(
                        onClick = { captureImage() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Rounded.Camera, contentDescription = "Capture", modifier = Modifier.size(28.dp))
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = surfaceVariantColor.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ZoomIn, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "OPTICAL ZOOM", 
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.Black,
                                color = primaryColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${String.format(Locale.US, "%.1f", 1.0 + zoomLevel * 9)}x", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = zoomLevel,
                            onValueChange = {
                                zoomLevel = it
                                cameraControl?.setLinearZoom(it)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor,
                                inactiveTrackColor = primaryColor.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = RoundedCornerShape(40.dp),
                            color = primaryContainerColor.copy(alpha = 0.3f),
                            border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Rounded.ZoomIn, null, modifier = Modifier.padding(32.dp), tint = primaryColor.copy(alpha = 0.5f))
                        }
                        Spacer(Modifier.height(32.dp))
                        Text("CAMERA ACCESS REQUIRED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            "Magnifier needs camera access to provide high-resolution zoom capabilities.", 
                            textAlign = TextAlign.Center,
                            color = onSurfaceVariantColor.copy(alpha = 0.7f),
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
