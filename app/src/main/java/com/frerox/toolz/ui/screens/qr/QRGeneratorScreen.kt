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

package com.frerox.toolz.ui.screens.qr

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveSlider
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.QREngine
import kotlinx.coroutines.launch

@Composable
fun QRGeneratorScreen(
    viewModel: QRViewModel,
    onBack: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ViewModel States
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val noteText by viewModel.noteText.collectAsStateWithLifecycle()
    val noteSize by viewModel.noteSize.collectAsStateWithLifecycle()
    val notePosition by viewModel.notePosition.collectAsStateWithLifecycle()
    val isNoteEnabled by viewModel.isNoteEnabled.collectAsStateWithLifecycle()
    
    val foregroundColor by viewModel.foregroundColor.collectAsStateWithLifecycle()
    val backgroundColor by viewModel.backgroundColor.collectAsStateWithLifecycle()
    val dotShape by viewModel.dotShape.collectAsStateWithLifecycle()
    val eyeShape by viewModel.eyeShape.collectAsStateWithLifecycle()
    val quietZone by viewModel.quietZone.collectAsStateWithLifecycle()
    val logoClearance by viewModel.logoClearance.collectAsStateWithLifecycle()
    val logoBitmap by viewModel.logoBitmap.collectAsStateWithLifecycle()
    val isInheritingTheme by viewModel.isInheritingTheme.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val clipboardSuggestion by viewModel.clipboardSuggestion.collectAsStateWithLifecycle()
    val isContrastSafe by viewModel.isContrastSafe.collectAsStateWithLifecycle()

    var isFullScreen by remember { mutableStateOf(false) }
    var originalBrightness by remember { mutableFloatStateOf(-1f) }

    // Resolve app theme colors
    val appPrimaryColor = MaterialTheme.colorScheme.primary
    val appSecondaryColor = MaterialTheme.colorScheme.secondary
    val appSurfaceColor = MaterialTheme.colorScheme.surface
    LaunchedEffect(appPrimaryColor, appSecondaryColor, appSurfaceColor) {
        viewModel.updateThemeColors(
            appPrimaryColor.toArgb(),
            appSecondaryColor.toArgb(),
            appSurfaceColor.toArgb()
        )
    }

    LaunchedEffect(Unit) {
        viewModel.checkClipboard(context)
    }

    // Brightness Control
    fun setBrightness(brightness: Float) {
        val activity = context as? Activity ?: return
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = brightness
        activity.window.attributes = layoutParams
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            val activity = context as? Activity
            originalBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
            setBrightness(1.0f)
            viewModel.toggleCaffeinate(context, true)
        } else {
            setBrightness(originalBrightness)
            viewModel.toggleCaffeinate(context, false)
        }
    }

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vibrationManager?.vibrateClick()
            viewModel.setLogoUri(uri, context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullScreen) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "QR GENERATOR",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                onBack()
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                onNavigateToScanner()
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            // QR PREVIEW AREA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isFullScreen) 1f else 0.45f)
                    .padding(if (isFullScreen) 0.dp else 24.dp),
                contentAlignment = Alignment.Center
            ) {
                val cardShape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(40.dp)
                ExpressiveCard(
                    onClick = {
                        if (qrBitmap != null) {
                            isFullScreen = !isFullScreen
                        } else {
                            vibrationManager?.vibrateClick()
                        }
                    },
                    modifier = Modifier
                        .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.size(320.dp)),
                    shape = cardShape,
                    containerColor = if (isFullScreen) Color.White else MaterialTheme.colorScheme.surface,
                    elevation = if (isFullScreen) 0.dp else 12.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isFullScreen) 40.dp else 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = qrBitmap,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(600, easing = EaseOutBack)) + scaleIn(initialScale = 0.85f, animationSpec = tween(600, easing = EaseOutBack)))
                                    .togetherWith(fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.85f, animationSpec = tween(400)))
                            },
                            label = "qr_main_anim"
                        ) { bitmap ->
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                QRPlaceholder()
                            }
                        }
                    }
                }

            }

            if (!isFullScreen) {
                // CONTROLS SHEET
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .fadingEdges(top = 16.dp, bottom = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // SMART CLIPBOARD CHIP
                    AnimatedVisibility(visible = clipboardSuggestion != null) {
                        clipboardSuggestion?.let { (text, type) ->
                            Surface(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.updateInputText(text)
                                    viewModel.clearClipboardSuggestion()
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Fill from Clipboard", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text(if (type == QRInputType.ENCRYPTED) "Encrypted text found!" else "Detected: ${type.displayName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                                    }
                                }
                            }
                        }
                    }

                    // CONTRAST ALARM
                    AnimatedVisibility(visible = !isContrastSafe) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Low contrast alert! QR may be unreadable.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    // MAIN INPUT
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("PAYLOAD", Icons.AutoMirrored.Rounded.Article)
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { viewModel.updateInputText(it) },
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            placeholder = { Text("https://toolz.app or Wi-Fi info...") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 5,
                            trailingIcon = {
                                if (inputText.isNotEmpty()) {
                                    ToolzExpressiveIconButton(
                                        onClick = {
                                            vibrationManager?.vibrateClick()
                                            viewModel.updateInputText("")
                                        },
                                        modifier = Modifier.size(28.dp),
                                        shape = SmallExpressiveShape,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // STYLE & COLORS SECTION
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("STYLE & COLORS", Icons.Rounded.Palette)
                            
                            // App Theme Toggle
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Use App Theme", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ExpressiveSwitch(
                                    checked = isInheritingTheme,
                                    onCheckedChange = { viewModel.toggleInheritTheme(it) }
                                )
                            }
                        }
                        
                        AnimatedVisibility(
                            visible = !isInheritingTheme,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Foreground
                                Text("Foreground Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                ColorSelector(
                                    selectedColor = foregroundColor,
                                    onColorSelected = { viewModel.updateForegroundColor(it) },
                                    presets = listOf(
                                        Color.Black, Color(0xFF2563EB), Color(0xFF059669), Color(0xFFDC2626),
                                        Color(0xFFFF9800), Color(0xFF7C3AED), Color(0xFFEC4899), Color(0xFF06B6D4)
                                    ),
                                    gradients = listOf(
                                        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
                                        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                                        listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
                                        listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
                                        listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
                                        listOf(Color(0xFF5EEAD4), Color(0xFF2563EB))
                                    ),
                                    label = "Foreground HEX"
                                )
                                
                                Spacer(Modifier.height(8.dp))
                                
                                // Background
                                Text("Background Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                ColorSelector(
                                    selectedColor = backgroundColor,
                                    onColorSelected = { viewModel.updateBackgroundColor(it) },
                                    presets = listOf(
                                        Color.White, Color.Black, Color(0xFFF1F5F9), Color(0xFFE2E8F0),
                                        Color(0xFFFEF2F2), Color(0xFFF0FDF4), Color(0xFFEFF6FF), Color(0xFFFAF5FF)
                                    ),
                                    gradients = emptyList(),
                                    label = "Background HEX"
                                )
                            }
                        }
                    }

                    // NOTE FEATURE
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("LEAVE A NOTE", Icons.AutoMirrored.Rounded.StickyNote2)
                            ExpressiveSwitch(
                                checked = isNoteEnabled,
                                onCheckedChange = { viewModel.toggleNoteEnabled(it) }
                            )
                        }
                        
                        AnimatedVisibility(visible = isNoteEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = noteText,
                                    onValueChange = { viewModel.updateNoteText(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Optional text label...") },
                                    shape = RoundedCornerShape(20.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilterChip(
                                        selected = notePosition == "TOP",
                                        onClick = { viewModel.updateNotePosition("TOP") },
                                        label = { Text("Top") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    FilterChip(
                                        selected = notePosition == "BOTTOM",
                                        onClick = { viewModel.updateNotePosition("BOTTOM") },
                                        label = { Text("Bottom") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Font Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${noteSize.toInt()}px", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    ExpressiveSlider(
                                        value = noteSize,
                                        onValueChange = { viewModel.updateNoteSize(it) },
                                        valueRange = 10f..40f
                                    )
                                }
                            }
                        }
                    }

                    // DOT & EYE STYLING
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader("GEOMETRIC STYLING", Icons.Rounded.BlurOn)
                        
                        Text("Dot Shape", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val dots = listOf(
                                Triple(QREngine.DotShape.SQUARE, "Classic", Icons.Rounded.Square),
                                Triple(QREngine.DotShape.ROUND, "Circle", Icons.Rounded.Circle),
                                Triple(QREngine.DotShape.LIQUID, "Liquid", Icons.Rounded.BlurCircular),
                                Triple(QREngine.DotShape.HEART, "Heart", Icons.Rounded.Favorite),
                                Triple(QREngine.DotShape.STAR, "Star", Icons.Rounded.Star)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().horizontalFadingEdges(right = 16.dp)) {
                                items(dots) { (shape, label, icon) ->
                                    ChoiceChip(selected = dotShape == shape, label = label, icon = icon) {
                                        vibrationManager?.vibrateClick()
                                        viewModel.updateDotShape(shape)
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text("Eye Shape", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val eyes = listOf(
                                Triple(QREngine.EyeShape.SQUARE, "Classic", Icons.Rounded.CropSquare),
                                Triple(QREngine.EyeShape.ROUND, "Circle", Icons.Rounded.RadioButtonUnchecked),
                                Triple(QREngine.EyeShape.SQUIRCLE, "Squircle", Icons.Rounded.Hexagon),
                                Triple(QREngine.EyeShape.LEAF, "Leaf", Icons.Rounded.Spa)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().horizontalFadingEdges(right = 16.dp)) {
                                items(eyes) { (shape, label, icon) ->
                                    ChoiceChip(selected = eyeShape == shape, label = label, icon = icon) {
                                        vibrationManager?.vibrateClick()
                                        viewModel.updateEyeShape(shape)
                                    }
                                }
                            }
                        }
                    }

                    // SLIDERS
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader("PRECISION TOOLS", Icons.Rounded.Tune)
                        
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Quiet Zone (Padding)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$quietZone", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            ExpressiveSlider(
                                value = quietZone.toFloat(), 
                                onValueChange = { viewModel.updateQuietZone(it.toInt()) }, 
                                valueRange = 0f..10f
                            )
                        }
                        
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Logo Clearance Buffer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${(logoClearance * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            ExpressiveSlider(
                                value = logoClearance, 
                                onValueChange = { viewModel.updateLogoClearance(it) }, 
                                valueRange = 0.1f..0.45f
                            )
                        }
                    }

                    // LOGO PICKER
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("BRANDING", Icons.Rounded.AutoAwesome)
                        Button(
                            onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null)
                            Spacer(Modifier.width(10.dp))
                            Text("PICK CENTER LOGO", fontWeight = FontWeight.Black)
                        }
                        
                        AnimatedVisibility(visible = logoBitmap != null) {
                            OutlinedButton(
                                onClick = { viewModel.setLogoUri(null, context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Rounded.Delete, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Remove Logo")
                            }
                        }
                    }

                    // EXPORT CENTER
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                val b = qrBitmap ?: return@Button
                                viewModel.saveQrCode(context, b, { 
                                    vibrationManager?.vibrateSuccess()
                                    scope.launch { snackbarHostState.showSnackbar("Saved to Gallery!") }
                                }, {})
                            },
                            modifier = Modifier.weight(1.2f).height(68.dp),
                            shape = RoundedCornerShape(24.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("SAVE PNG", fontWeight = FontWeight.Black)
                        }
                        
                        IconButton(
                            onClick = {
                                viewModel.exportSvg(context, inputText, { uri ->
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/svg+xml"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share SVG"))
                                }, {})
                            },
                            modifier = Modifier.size(68.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(24.dp))
                        ) {
                            Icon(Icons.Rounded.ChangeHistory, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primary.copy(0.1f), shape = RoundedCornerShape(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp).size(18.dp))
        }
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, letterSpacing = 1.sp)
    }
}

@Composable
fun ColorSelector(
    selectedColor: QREngine.QrColor,
    onColorSelected: (QREngine.QrColor) -> Unit,
    presets: List<Color>,
    gradients: List<List<Color>>,
    label: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().horizontalFadingEdges(left = 12.dp, right = 12.dp)
        ) {
            items(presets) { color ->
                ColorCircle(selected = (selectedColor as? QREngine.QrColor.Solid)?.color == color.toArgb(), color = color) {
                    onColorSelected(QREngine.QrColor.Solid(color.toArgb()))
                }
            }
            items(gradients) { colors ->
                GradientCircle(selected = (selectedColor as? QREngine.QrColor.LinearGradient)?.colors == colors.map { it.toArgb() }, colors = colors) {
                    onColorSelected(QREngine.QrColor.LinearGradient(colors.map { it.toArgb() }))
                }
            }
        }
        
        var hexInput by remember { mutableStateOf(String.format("#%06X", (0xFFFFFF and selectedColor.getPrimaryColor()))) }
        OutlinedTextField(
            value = hexInput,
            onValueChange = { 
                hexInput = it
                try {
                    val color = Color(android.graphics.Color.parseColor(it))
                    onColorSelected(QREngine.QrColor.Solid(color.toArgb()))
                } catch (e: Exception) {}
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.Colorize, null, tint = Color(selectedColor.getPrimaryColor())) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
fun ColorCircle(selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = if (color == Color.White) Color.Black else Color.White)
    }
}

@Composable
fun GradientCircle(selected: Boolean, colors: List<Color>, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors))
            .border(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
    }
}

@Composable
fun ChoiceChip(selected: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(68.dp).width(90.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QRPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "scan_offset"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        // Center Pulsing Icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    // Scanning line within the icon box
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * scanOffset
                        drawLine(
                            brush = Brush.verticalGradient(listOf(Color.Transparent, primaryColor, Color.Transparent)),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "AWAITING PAYLOAD",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
        }
    }
}
