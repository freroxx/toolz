package com.frerox.toolz.ui.screens.media

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.util.ConversionEngine
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileConverterScreen(
    viewModel: FileConverterViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    
    var highQuality by remember { mutableStateOf(true) }
    var showAllFormatsSheet by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    "COM_FREROX_TOOLZ_CONVERSION_PROGRESS" -> {
                        val progress = intent.getIntExtra("progress", 0)
                        viewModel.onConversionProgress(progress)
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_SUCCESS" -> {
                        val path = intent.getStringExtra("output_path")
                        viewModel.onConversionFinished(true, path, null)
                        vibrationManager?.vibrateSuccess()
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_ERROR" -> {
                        val error = intent.getStringExtra("error_message")
                        viewModel.onConversionFinished(false, null, error)
                        vibrationManager?.vibrateError()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("COM_FREROX_TOOLZ_CONVERSION_PROGRESS")
            addAction("COM_FREROX_TOOLZ_CONVERSION_SUCCESS")
            addAction("COM_FREROX_TOOLZ_CONVERSION_ERROR")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    var showTypePicker by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingUri = it
            showTypePicker = true
        }
    }

    if (showTypePicker && pendingUri != null) {
        ConversionTypeSheet(
            uri = pendingUri!!,
            onDismiss = { showTypePicker = false },
            onTypeSelected = { type ->
                viewModel.selectFile(pendingUri!!, type, highQuality)
                showTypePicker = false
            }
        )
    }

    if (showAllFormatsSheet) {
        AllFormatsSheet(onDismiss = { showAllFormatsSheet = false })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "FILE CONVERTER",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            color = Color.White
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quality Settings Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (highQuality) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (highQuality) Icons.Rounded.HighQuality else Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Elite Quality", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                if (highQuality) "Max bitrate & Lanczos" else "Fast & optimized",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Switch(
                        checked = highQuality,
                        onCheckedChange = { 
                            highQuality = it
                            vibrationManager?.vibrateTick()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = uiState.conversionSuccess to uiState.isConverting,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(500, easing = LinearOutSlowInEasing)) + 
                     scaleIn(initialScale = 0.92f, animationSpec = tween(500, easing = LinearOutSlowInEasing)))
                        .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.05f))
                },
                label = "conversion_content"
            ) { (success, converting) ->
                if (success) {
                    SuccessView(
                        outputPath = uiState.outputPath ?: "",
                        category = uiState.conversionType?.category ?: "Downloads",
                        onReset = { viewModel.reset() }
                    )
                } else {
                    ConversionView(
                        isConverting = converting,
                        progress = uiState.progress,
                        onSelectFile = { launcher.launch("*/*") },
                        onShowAllFormats = { showAllFormatsSheet = true }
                    )
                }
            }
            
            if (uiState.error != null) {
                Spacer(Modifier.height(20.dp))
                AnimatedVisibility(
                    visible = uiState.error != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun ConversionView(isConverting: Boolean, progress: Int, onSelectFile: () -> Unit, onShowAllFormats: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConverting) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat() / 100f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .scale(scale)
                .bouncyClick(enabled = !isConverting) { onSelectFile() },
            shape = RoundedCornerShape(48.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(
                2.dp,
                if (isConverting) 
                    Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary))
                else 
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f)))
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(40.dp)
                ) {
                    if (isConverting) {
                        Box(contentAlignment = Alignment.Center) {
                            if (progress >= 0) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(120.dp),
                                    strokeWidth = 10.dp,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                                Text(
                                    "$progress%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(100.dp),
                                    strokeWidth = 8.dp,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Rounded.Autorenew,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                        Text(
                            "PROCESSING",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            color = Color.White
                        )
                        Text(
                            "Optimizing your media...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    } else {
                        Icon(
                            Icons.Rounded.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "SELECT MEDIA",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                        Text(
                            "Tap to transform any file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        val featureItems = listOf(
            Triple(Icons.Rounded.Movie, "VIDEO", Color(0xFFFF5722)),
            Triple(Icons.Rounded.MusicNote, "AUDIO", Color(0xFF2196F3)),
            Triple(Icons.Rounded.Image, "IMAGE", Color(0xFF4CAF50)),
            Triple(Icons.Rounded.Description, "DOCS", Color(0xFFFFC107)),
            Triple(Icons.Rounded.Animation, "GIF", Color(0xFFE91E63)),
            Triple(Icons.Rounded.MoreHoriz, "MORE", Color.White)
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                featureItems.take(3).forEach { (icon, label, color) ->
                    ConversionFeatureChip(icon = icon, label = label, color = color)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                featureItems.drop(3).forEach { (icon, label, color) ->
                    ConversionFeatureChip(icon = icon, label = label, color = color)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // See all formats card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClick { onShowAllFormats() },
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.GridOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "SEE ALL SUPPORTED FORMATS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun RowScope.ConversionFeatureChip(icon: ImageVector, label: String, color: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(28.dp), tint = color)
            Spacer(Modifier.height(8.dp))
            Text(
                label, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}

@Composable
fun SuccessView(outputPath: String, category: String, onReset: () -> Unit) {
    val context = LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "success_scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse_scale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse_alpha"
            )
            
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .background(Color(0xFF4CAF50).copy(alpha = pulseAlpha), CircleShape)
            )

            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                border = BorderStroke(2.dp, Color(0xFF4CAF50))
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(24.dp),
                    tint = Color(0xFF4CAF50)
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            "SUCCESS!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            color = Color.White
        )
        
        Text(
            "Saved in Toolz/$category",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            onClick = {
                 try {
                    val file = File(outputPath)
                    val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open File"))
                } catch (e: Exception) {}
            }
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val fileName = outputPath.substringAfterLast("/")
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        "Tap to preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("CONVERT ANOTHER", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionTypeSheet(
    uri: Uri,
    onDismiss: () -> Unit,
    onTypeSelected: (ConversionEngine.ConversionType) -> Unit
) {
    val context = LocalContext.current
    val mimeType = context.contentResolver.getType(uri) ?: ""
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val availableTypes = remember(mimeType) {
        ConversionEngine.ConversionType.entries.filter { type ->
            when {
                mimeType.startsWith("video") -> type.name.startsWith("VIDEO_TO_")
                mimeType.startsWith("audio") -> type.name.startsWith("AUDIO_TO_")
                mimeType.startsWith("image") -> type.name.startsWith("IMAGE_TO_")
                mimeType.startsWith("application/pdf") -> type.name.startsWith("PDF_TO_")
                else -> false
            }
        }
    }

    val filteredTypes = remember(searchQuery, availableTypes) {
        val filtered = if (searchQuery.isBlank()) availableTypes
        else availableTypes.filter { it.extension.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true) }
        
        // Sort by popularity
        filtered.sortedWith(compareByDescending<ConversionEngine.ConversionType> { it.isPopular }.thenBy { it.extension })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "CHOOSE OUTPUT",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search formats (e.g. mp4, wav)", color = Color.White.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(16.dp))

            if (filteredTypes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No formats found", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.05f to Color.Black,
                                        0.95f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                    ) {
                        val popularTypes = filteredTypes.filter { it.isPopular && searchQuery.isBlank() }
                        val remainingTypes = filteredTypes.filter { !it.isPopular || searchQuery.isNotBlank() }

                        if (popularTypes.isNotEmpty()) {
                            item {
                                Text(
                                    "POPULAR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp)
                                )
                            }
                            items(popularTypes) { type ->
                                TypeOptionItem(
                                    type = type,
                                    onClick = { onTypeSelected(type) }
                                )
                            }
                        }

                        val grouped = remainingTypes.groupBy { it.category }
                        grouped.forEach { (category, types) ->
                            item {
                                Text(
                                    category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp)
                                )
                            }
                            items(types) { type ->
                                TypeOptionItem(
                                    type = type,
                                    onClick = { onTypeSelected(type) }
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
fun AllFormatsSheet(onDismiss: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val allTypes = remember { ConversionEngine.ConversionType.entries }
    val filteredTypes = remember(searchQuery) {
        if (searchQuery.isBlank()) allTypes
        else allTypes.filter { it.extension.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "SUPPORTED FORMATS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search all formats...", color = Color.White.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.05f to Color.Black,
                                    0.95f to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                ) {
                    val grouped = filteredTypes.groupBy { it.category }
                    grouped.forEach { (category, types) ->
                        item {
                            Text(
                                category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp)
                            )
                        }
                        items(types.sortedWith(compareByDescending<ConversionEngine.ConversionType> { it.isPopular }.thenBy { it.extension })) { type ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(
                                            type.extension.uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        if (type.isPopular) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Icons.Rounded.Star, 
                                                null, 
                                                tint = Color(0xFFFFD700), 
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            type.name.replace("_", " ").lowercase(Locale.getDefault())
                                                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypeOptionItem(type: ConversionEngine.ConversionType, onClick: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    Surface(
        onClick = {
            vibrationManager?.vibrateClick()
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        when(type.category) {
                            "Videos" -> Color(0xFFFF5722).copy(alpha = 0.1f)
                            "Audio" -> Color(0xFF2196F3).copy(alpha = 0.1f)
                            "Images" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                            "Documents" -> Color(0xFFFFC107).copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        }, 
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when(type.category) {
                        "Audio" -> Icons.Rounded.MusicNote
                        "Animations" -> Icons.Rounded.Animation
                        "Images" -> Icons.Rounded.Image
                        "Documents" -> Icons.Rounded.Description
                        else -> Icons.Rounded.Movie
                    },
                    null, 
                    tint = when(type.category) {
                        "Videos" -> Color(0xFFFF5722)
                        "Audio" -> Color(0xFF2196F3)
                        "Images" -> Color(0xFF4CAF50)
                        "Documents" -> Color(0xFFFFC107)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "To ${type.extension.uppercase()}", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    if (type.isPopular) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.Star, 
                            null, 
                            tint = Color(0xFFFFD700), 
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    ".${type.extension} format", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.2f))
        }
    }
}
