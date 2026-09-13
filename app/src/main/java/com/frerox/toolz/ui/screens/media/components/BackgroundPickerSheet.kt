/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.screens.media.BgScaleMode
import com.frerox.toolz.ui.screens.media.PreviewBackground
import com.frerox.toolz.ui.theme.SquircleShape
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Curated backdrop palette — 12 tones across the full spectrum. */
private val SwatchPalette = listOf(
    Color(0xFF1C1B1F), // Ink black
    Color(0xFF263238), // Dark slate
    Color(0xFF1A237E), // Deep indigo
    Color(0xFF37474F), // Blue grey
    Color(0xFF4A148C), // Deep purple
    Color(0xFF880E4F), // Deep pink
    Color(0xFFB71C1C), // Deep red
    Color(0xFFE65100), // Deep orange
    Color(0xFF33691E), // Dark green
    Color(0xFF006064), // Dark teal
    Color(0xFFE8E0D5), // Warm paper
    Color(0xFFFFFFFF), // Pure white
)

/**
 * Full-featured background picker sheet.
 *
 * Sections:
 *  1. Curated swatch palette
 *  2. Custom HSV color wheel + hex input
 *  3. Image from gallery
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackgroundPickerSheet(
    current: PreviewBackground,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onSelect: (PreviewBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // ── HSV state — initialised from the current colour if one is active ──────
    val initArgb = (current as? PreviewBackground.Color)?.color
        ?: android.graphics.Color.HSVToColor(floatArrayOf(260f, 0.8f, 1f))
    val initHsv = FloatArray(3).also { android.graphics.Color.colorToHSV(initArgb, it) }

    var hue by rememberSaveable { mutableStateOf(initHsv[0]) }
    var sat by rememberSaveable { mutableStateOf(initHsv[1]) }
    var value by rememberSaveable { mutableStateOf(initHsv[2]) }

    val pickedArgb = remember(hue, sat, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    }
    val pickedColor = Color(pickedArgb)

    var hexInput by rememberSaveable { mutableStateOf(argbToHex(pickedArgb)) }
    var hexError by rememberSaveable { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            } ?: return@rememberLauncherForActivityResult
            onSelect(PreviewBackground.CustomImage(scaleBitmap(bmp, 2048)))
            onDismiss()
        } catch (_: Exception) { }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                stringResource(R.string.st_BackgroundRemover_BgPickerTitle),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )

            // ── Section 1: Swatches ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.st_BackgroundRemover_BgSwatches))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (swatch in SwatchPalette) {
                        val isSelected = current is PreviewBackground.Color &&
                            Color(current.color) == swatch
                        SwatchDot(
                            color = swatch,
                            selected = isSelected,
                            onClick = {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(swatch.toArgb(), hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                hexInput = argbToHex(swatch.toArgb())
                                hexError = false
                                onSelect(PreviewBackground.Color(swatch.toArgb()))
                            },
                        )
                    }
                }
            }

            // ── Section 2: Custom color ──────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel(stringResource(R.string.st_BackgroundRemover_BgColorHex))

                // Preview + hex field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(SquircleShape)
                            .background(pickedColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape)
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            val clean = raw
                                .removePrefix("#")
                                .filter { it.isLetterOrDigit() }
                                .uppercase()
                                .take(6)
                            hexInput = clean
                            val parsed = parseHex(clean)
                            if (parsed != null) {
                                hexError = false
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed, hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                onSelect(PreviewBackground.Color(parsed))
                            } else {
                                hexError = clean.isNotEmpty() && clean.length < 6
                            }
                        },
                        prefix = { Text("#") },
                        label = { Text("RRGGBB", style = MaterialTheme.typography.labelSmall) },
                        isError = hexError,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                        shape = SquircleShape,
                    )
                }

                // HSV hue+sat wheel
                HsvWheel(
                    hue = hue,
                    sat = sat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    onChange = { h, s ->
                        hue = h; sat = s
                        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, value))
                        hexInput = argbToHex(argb)
                        hexError = false
                        onSelect(PreviewBackground.Color(argb))
                    },
                )

                // Brightness slider
                Column {
                    Text(
                        "Brightness",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black,
                                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f))),
                                    )
                                )
                            )
                    )
                    Slider(
                        value = value,
                        onValueChange = { v ->
                            value = v
                            val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, v))
                            hexInput = argbToHex(argb)
                            hexError = false
                            onSelect(PreviewBackground.Color(argb))
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Section 3: Image background ──────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(stringResource(R.string.st_BackgroundRemover_BgImage))

                if (current is PreviewBackground.CustomImage) {
                    Surface(
                        shape = SquircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Image(
                                    bitmap = current.bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(SquircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Custom image active",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "${current.bitmap.width} × ${current.bitmap.height}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = SquircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Change", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Fit mode selector
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Scale mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ToolzConnectedButtonGroup(
                                    selectedIndex = if (current.scaleMode == BgScaleMode.COVER) 0 else 1,
                                    options = listOf("Fill (Cover)", "Fit inside"),
                                    unCheckedIcons = listOf(Icons.Rounded.Crop, Icons.Rounded.AspectRatio),
                                    checkedIcons = listOf(Icons.Rounded.Crop, Icons.Rounded.AspectRatio),
                                    onOptionSelected = { idx ->
                                        val newMode = if (idx == 0) BgScaleMode.COVER else BgScaleMode.FIT
                                        onSelect(current.copy(scaleMode = newMode))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // Blur slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Blur",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${(current.blurRadius * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Slider(
                                    value = current.blurRadius,
                                    onValueChange = { onSelect(current.copy(blurRadius = it)) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // Dim slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Dim background",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${(current.dimAmount * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Slider(
                                    value = current.dimAmount,
                                    onValueChange = { onSelect(current.copy(dimAmount = it)) },
                                    valueRange = 0f..0.8f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = SquircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.st_BackgroundRemover_BgChooseImage),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwatchDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (selected) 42.dp else 38.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            val checkTint = if (color.luminance() > 0.4f) Color.Black else Color.White
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp), tint = checkTint)
        }
    }
}

/**
 * HSV hue + saturation wheel — pure Canvas, no third-party library.
 * Tap or drag to change hue (angle) and saturation (distance from centre).
 */
@Composable
private fun HsvWheel(
    hue: Float,
    sat: Float,
    modifier: Modifier = Modifier,
    onChange: (hue: Float, sat: Float) -> Unit,
) {
    // Cache the wheel bitmap — only regenerated when the composable size changes
    var wheelBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastSize by remember { mutableStateOf(Pair(0, 0)) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .drawWithCache {
                val w = size.width.toInt()
                val h = size.height.toInt()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(cx, cy) - 4.dp.toPx()

                // Re-rasterise only on size change
                if (wheelBitmap == null || lastSize != Pair(w, h)) {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val pixels = IntArray(w * h)
                    for (py in 0 until h) {
                        for (px in 0 until w) {
                            val dx = px - cx; val dy = py - cy
                            val dist = hypot(dx, dy)
                            if (dist > radius) {
                                pixels[py * w + px] = 0
                            } else {
                                val angle = (atan2(dy, dx) * 180.0 / PI).toFloat()
                                val wh = (angle + 360f) % 360f
                                val ws = (dist / radius).coerceIn(0f, 1f)
                                pixels[py * w + px] =
                                    android.graphics.Color.HSVToColor(floatArrayOf(wh, ws, 1f))
                            }
                        }
                    }
                    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                    wheelBitmap = bmp
                    lastSize = Pair(w, h)
                }

                onDrawBehind {
                    wheelBitmap?.let { drawImage(it.asImageBitmap()) }
                    // Thumb
                    val thumbAngleRad = hue * PI.toFloat() / 180f
                    val thumbR = sat * radius
                    val tx = cx + thumbR * cos(thumbAngleRad)
                    val ty = cy + thumbR * sin(thumbAngleRad)
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(tx, ty))
                    drawCircle(
                        Color.Black.copy(alpha = 0.3f), radius = 11.dp.toPx(),
                        center = Offset(tx, ty), style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> updateFromPos(pos, size, onChange) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromPos(change.position, size, onChange)
                }
            },
    ) {}
}

private fun updateFromPos(
    pos: Offset,
    size: androidx.compose.ui.unit.IntSize,
    onChange: (Float, Float) -> Unit,
) {
    val cx = size.width / 2f; val cy = size.height / 2f
    val radius = min(cx, cy) - 4f   // matches the draw radius (ignore dp conversion — close enough)
    val dx = pos.x - cx; val dy = pos.y - cy
    val dist = hypot(dx, dy)
    val angle = (atan2(dy, dx) * 180.0 / PI).toFloat()
    val h = (angle + 360f) % 360f
    val s = (dist / radius).coerceIn(0f, 1f)
    onChange(h, s)
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun argbToHex(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

private fun parseHex(hex: String): Int? {
    if (hex.length != 6) return null
    return try { android.graphics.Color.parseColor("#$hex") } catch (_: Exception) { null }
}

private fun Color.luminance(): Float =
    (0.2126 * red + 0.7152 * green + 0.0722 * blue).toFloat()

private fun scaleBitmap(bmp: Bitmap, maxEdge: Int): Bitmap {
    val w = bmp.width; val h = bmp.height
    if (w <= maxEdge && h <= maxEdge) return bmp
    val scale = maxEdge.toFloat() / maxOf(w, h)
    return Bitmap.createScaledBitmap(bmp, (w * scale).toInt(), (h * scale).toInt(), true)
}
