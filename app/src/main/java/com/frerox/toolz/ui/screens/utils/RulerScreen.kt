package com.frerox.toolz.ui.screens.utils

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

private enum class UnitMode(val label: String, val fullName: String) {
    MM("mm", "Millimeters"),
    CM("cm", "Centimeters"),
    IN("in", "Inches"),
    FT("ft", "Feet")
}

private data class Measurement(val cm: Float, val inches: Float, val unit: UnitMode)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RulerScreen(
    onBack: () -> Unit
) {
    // --- Core measuring state ---
    var lineY by remember { mutableFloatStateOf(0f) }
    var anchorY by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var isFlipped by remember { mutableStateOf(false) }
    var twoPointMode by remember { mutableStateOf(false) }
    var snapToGrid by remember { mutableStateOf(true) }

    // --- UI state ---
    var unitMode by remember { mutableStateOf(UnitMode.CM) }
    var showHistory by remember { mutableStateOf(false) }
    var measurementHistory by remember { mutableStateOf(emptyList<Measurement>()) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val haptic = rememberToolzHapticFeedback()
    val clipboard = LocalClipboardManager.current

    // Physical scale.
    //
    // `Configuration.densityDpi` (used previously) is NOT physical DPI — it's
    // Android's bucketed logical density (120/160/240/320/420/480/560...) used
    // purely for dp scaling. Two devices with different physical screen sizes
    // can report the same bucket, so a ruler built on it is guaranteed to be
    // physically wrong by whatever ratio separates true PPI from the density
    // bucket — this is exactly the ~11cm-measures-as-~13cm case (a fixed
    // ~1.18x mismatch, not noise or drift).
    //
    // The field that actually carries physical truth is `DisplayMetrics.xdpi`
    // / `ydpi`, computed by the OS from the panel's real physical size. It can
    // occasionally be missing/garbage on some OEM builds, so it's
    // sanity-clamped and averaged across both axes (panels are very slightly
    // non-square) with a fallback to the density bucket only if both reported
    // values are clearly invalid.
    val displayMetrics = context.resources.displayMetrics
    val physicalDpi = remember(displayMetrics) {
        val x = displayMetrics.xdpi
        val y = displayMetrics.ydpi
        val validX = x.isFinite() && x > 72f && x < 1000f
        val validY = y.isFinite() && y > 72f && y < 1000f
        when {
            validX && validY -> (x + y) / 2f
            validX -> x
            validY -> y
            else -> configuration.densityDpi.toFloat() // last-resort fallback only
        }
    }
    val mmPx = physicalDpi / 25.4f
    val inchPx = physicalDpi

    val colorScheme = MaterialTheme.colorScheme
    val onSurface = colorScheme.onSurface
    val primary = colorScheme.primary
    val secondary = colorScheme.secondary
    val tertiary = colorScheme.tertiary

    // Snap increment matches the selected unit's smallest readable division,
    // so "snap to grid" always feels like it's snapping to *this* ruler's
    // marks rather than a fixed mm grid regardless of what's displayed.
    val snapIncrementPx = when (unitMode) {
        UnitMode.MM -> mmPx
        UnitMode.CM -> mmPx
        UnitMode.IN -> inchPx / 8f
        UnitMode.FT -> inchPx / 8f
    }

    fun snap(y: Float): Float {
        if (!snapToGrid) return y
        return (y / snapIncrementPx).roundToInt() * snapIncrementPx
    }

    // `justReset` flags a dismiss/reset so the line/anchor animatables know to
    // snap instantly to 0 instead of spring-animating there. Without this,
    // clearing the measurement (lineY = 0f) triggered the normal spring path,
    // which takes a few hundred ms to settle — during that same window
    // `AnimatedVisibility` is running its own scaleOut()/fadeOut() on the
    // readout box. Two independently-timed animations were fighting over the
    // same screen region (the readout box exiting while a "phantom" line was
    // still visibly springing down to 0 underneath it), which is what read as
    // flicker/bugging out when the center box closed.
    var justReset by remember { mutableStateOf(false) }

    // Springy follow for the primary line — immediate while dragging or just
    // reset, springs gently into place only for a normal (non-reset) release.
    val lineAnim = remember { Animatable(0f) }
    LaunchedEffect(lineY, isDragging, justReset) {
        if (isDragging || performanceMode || justReset) {
            lineAnim.snapTo(lineY)
        } else {
            lineAnim.animateTo(
                targetValue = lineY,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val anchorAnim = remember { Animatable(0f) }
    LaunchedEffect(anchorY, justReset) {
        if (justReset) {
            anchorAnim.snapTo(0f)
            return@LaunchedEffect
        }
        val target = anchorY ?: return@LaunchedEffect
        if (performanceMode) {
            anchorAnim.snapTo(target)
        } else {
            anchorAnim.animateTo(
                target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val hasActiveLine = lineY > 0f
    val hasAnchor = anchorY != null

    val cmValue = lineY / mmPx / 10f
    val inValue = lineY / inchPx
    val distanceCm = if (hasAnchor) abs(lineY - (anchorY ?: 0f)) / mmPx / 10f else cmValue
    val distanceIn = if (hasAnchor) abs(lineY - (anchorY ?: 0f)) / inchPx else inValue

    fun resetMeasurement() {
        justReset = true
        lineY = 0f
        anchorY = null
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Ruler",
                subtitle = when {
                    twoPointMode && !hasAnchor -> "Tap a start point"
                    twoPointMode -> "Two-point measure"
                    else -> "Drag to measure"
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.click()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = {
                            haptic.tick()
                            twoPointMode = !twoPointMode
                            resetMeasurement()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (twoPointMode) colorScheme.secondaryContainer
                            else colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                        ),
                        shape = SmallExpressiveShape,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = "Two-point measure")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                AnimatedVisibility(
                    visible = measurementHistory.isNotEmpty() && showHistory,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut()
                ) {
                    HistoryCarousel(
                        history = measurementHistory,
                        onClear = {
                            haptic.error()
                            measurementHistory = emptyList()
                        }
                    )
                }

                ToolzHorizontalFloatingToolbar(
                    expanded = true,
                    content = {
                        ToolzExpressiveIconButton(
                            onClick = {
                                haptic.click()
                                isFlipped = !isFlipped
                            },
                            modifier = Modifier.size(48.dp),
                            shape = SmallExpressiveShape
                        ) {
                            Icon(Icons.Rounded.Flip, contentDescription = "Flip orientation")
                        }

                        ToolzExpressiveIconButton(
                            onClick = {
                                haptic.tick()
                                snapToGrid = !snapToGrid
                            },
                            modifier = Modifier.size(48.dp),
                            shape = SmallExpressiveShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (snapToGrid) colorScheme.primaryContainer
                                else colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Rounded.GridOn, contentDescription = "Snap to gridline")
                        }

                        ToolzExpressiveIconButton(
                            onClick = {
                                haptic.tick()
                                showHistory = !showHistory
                            },
                            modifier = Modifier.size(48.dp),
                            shape = SmallExpressiveShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showHistory) colorScheme.secondaryContainer
                                else colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "History")
                        }
                    },
                    trailingContent = {
                        clickableItem(
                            onClick = {
                                haptic.click()
                                resetMeasurement()
                            },
                            icon = { Icon(Icons.Rounded.Refresh, null) },
                            label = "Reset"
                        )
                    }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
                // Single pointer input block: tap and drag are detected off the
                // same shared gesture stream, so there is no competition between
                // two separate detectors for pointer ownership.
                //
                // IMPORTANT: this block is keyed on `snapToGrid` and
                // `snapIncrementPx` in addition to `twoPointMode`. Compose only
                // restarts a `pointerInput` coroutine (and therefore only
                // recreates the closures inside it) when one of its keys
                // changes. Previously this block was keyed on `twoPointMode`
                // alone, so toggling "snap to grid" updated the button's look
                // but the running gesture coroutine kept using whatever
                // `snap()` behavior was captured when the coroutine last
                // started — the toggle silently did nothing. Keying on the
                // values `snap()` actually depends on forces a fresh coroutine
                // (and fresh closure) every time the toggle changes.
                .pointerInput(twoPointMode, snapToGrid, snapIncrementPx) {
                    if (twoPointMode) {
                        detectTapGestures(
                            onTap = { offset ->
                                haptic.tick()
                                justReset = false
                                when {
                                    anchorY == null -> anchorY = snap(offset.y)
                                    else -> lineY = snap(offset.y)
                                }
                            }
                        )
                    } else {
                        detectDragGestures(
                            onDragStart = { offset ->
                                haptic.tick()
                                justReset = false
                                isDragging = true
                                lineY = snap(offset.y)
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, _ ->
                                change.consume()
                                val oldStep = (lineY / snapIncrementPx).roundToInt()
                                val newY = snap(change.position.y).coerceIn(0f, size.height.toFloat())
                                val newStep = (newY / snapIncrementPx).roundToInt()
                                if (oldStep != newStep && !performanceMode) {
                                    haptic.tick()
                                }
                                lineY = newY
                            }
                        )
                    }
                }
                .pointerInput(twoPointMode, hasAnchor, snapToGrid, snapIncrementPx) {
                    // In two-point mode, once an anchor is dropped, dragging
                    // moves the second (measuring) point — a distinct gesture
                    // registered only once an anchor exists, so it never
                    // competes with the tap detector above for the first tap.
                    // Same key-on-snap-state fix as above applies here.
                    if (twoPointMode && hasAnchor) {
                        detectDragGestures(
                            onDragStart = {
                                haptic.tick()
                                justReset = false
                                isDragging = true
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, _ ->
                                change.consume()
                                val oldStep = (lineY / snapIncrementPx).roundToInt()
                                val newY = snap(change.position.y).coerceIn(0f, size.height.toFloat())
                                val newStep = (newY / snapIncrementPx).roundToInt()
                                if (oldStep != newStep && !performanceMode) {
                                    haptic.tick()
                                }
                                lineY = newY
                            }
                        )
                    }
                }
        ) {
            RulerCanvas(
                density = density,
                performanceMode = performanceMode,
                isFlipped = isFlipped,
                unitMode = unitMode,
                mmPx = mmPx,
                inchPx = inchPx,
                lineY = lineAnim.value,
                anchorYPx = if (twoPointMode && hasAnchor) anchorAnim.value else null,
                snapToGrid = snapToGrid,
                snapIncrementPx = snapIncrementPx,
                onSurface = onSurface,
                primary = primary,
                modifier = Modifier.fillMaxSize()
            )

            // --- Floating measurement readout ---
            AnimatedVisibility(
                visible = hasActiveLine,
                enter = fadeIn() + scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                MeasurementReadout(
                    unitMode = unitMode,
                    onUnitModeChange = {
                        haptic.tick()
                        unitMode = it
                    },
                    cm = distanceCm,
                    inches = distanceIn,
                    twoPointMode = twoPointMode,
                    hasAnchor = hasAnchor,
                    primary = primary,
                    secondary = secondary,
                    colorScheme = colorScheme,
                    onSurface = onSurface,
                    onSave = {
                        haptic.success()
                        measurementHistory =
                            (measurementHistory + Measurement(distanceCm, distanceIn, unitMode)).takeLast(10)
                    },
                    onCopy = {
                        haptic.click()
                        val text = when (unitMode) {
                            UnitMode.MM -> String.format(Locale.ROOT, "%.1f mm", distanceCm * 10f)
                            UnitMode.CM -> String.format(Locale.ROOT, "%.2f cm", distanceCm)
                            UnitMode.IN -> String.format(Locale.ROOT, "%.3f in", distanceIn)
                            UnitMode.FT -> String.format(Locale.ROOT, "%.3f ft", distanceIn / 12f)
                        }
                        clipboard.setText(AnnotatedString(text))
                    },
                    onDismiss = {
                        haptic.click()
                        resetMeasurement()
                    }
                )
            }

            // --- Idle hint ---
            if (!hasActiveLine) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 160.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    StaggeredEntrance(index = 0) {
                        Surface(
                            color = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            shape = BouncyShape,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Straighten, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (twoPointMode) "Tap a start point, then drag"
                                    else "Drag anywhere to measure",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The ruler tick canvas. Draws a single scale in the currently selected unit —
 * not two overlapping scales — with ticks near the active line/anchor growing
 * and brightening as a lightweight, real "magnifier" reaction.
 */
@Composable
private fun RulerCanvas(
    density: androidx.compose.ui.unit.Density,
    performanceMode: Boolean,
    isFlipped: Boolean,
    unitMode: UnitMode,
    mmPx: Float,
    inchPx: Float,
    lineY: Float,
    anchorYPx: Float?,
    snapToGrid: Boolean,
    snapIncrementPx: Float,
    onSurface: Color,
    primary: Color,
    modifier: Modifier = Modifier
) {
    val strokeWidth = with(density) { 1.5.dp.toPx() }
    val majorLen = with(density) { 56.dp.toPx() }
    val midLen = with(density) { 38.dp.toPx() }
    val minorLen = with(density) { 22.dp.toPx() }
    val labelTextSize = with(density) { 16.sp.toPx() }
    val labelOffset = with(density) { 16.dp.toPx() }
    val reactRadius = with(density) { 60.dp.toPx() }

    fun reactionAt(y: Float, ref: Float): Float {
        if (performanceMode || ref <= 0f) return 0f
        val d = abs(y - ref)
        return if (d < reactRadius) (1f - d / reactRadius).coerceIn(0f, 1f) else 0f
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val startX = if (isFlipped) width else 0f

        // Determine the unit's major/minor tick spacing so the ruler always
        // reads cleanly at any unit rather than always showing millimeters.
        //   MM: major = 10mm (1cm), minor = 1mm
        //   CM: major = 10cm,       minor = 1cm, half = 0.5cm
        //   IN: major = 1in,        minor = 1/8in, half = 1/2in, quarter = 1/4in
        //   FT: major = 1ft,        minor = 1in
        when (unitMode) {
            UnitMode.MM, UnitMode.CM -> {
                val minorStep = mmPx
                val perMajor = 10
                var i = 0
                var y = 0f
                while (y < height) {
                    val isMajor = i % perMajor == 0
                    val isMid = i % 5 == 0
                    val reaction = reactionAt(y, lineY)
                    val len = when {
                        isMajor -> majorLen * (1f + reaction * 0.3f)
                        isMid -> midLen * (1f + reaction * 0.2f)
                        else -> minorLen
                    }
                    val endX = if (isFlipped) width - len else len
                    drawLine(
                        color = if (isMajor) onSurface.copy(alpha = 0.95f)
                        else onSurface.copy(alpha = 0.2f + reaction * 0.5f),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = if (isMajor) strokeWidth * 2.2f else strokeWidth,
                        cap = StrokeCap.Round
                    )
                    if (isMajor) {
                        val label = when (unitMode) {
                            UnitMode.MM -> (i).toString()
                            else -> (i / perMajor).toString()
                        }
                        val textX = if (isFlipped) width - len - labelOffset else len + labelOffset
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            textX,
                            y + 5.dp.toPx(),
                            Paint().apply {
                                color = onSurface.toArgb()
                                textSize = labelTextSize * (1f + reaction * 0.2f)
                                textAlign = if (isFlipped) Paint.Align.RIGHT else Paint.Align.LEFT
                                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                alpha = ((0.6f + reaction * 0.4f).coerceIn(0f, 1f) * 255).toInt()
                            }
                        )
                    }
                    y += minorStep
                    i++
                }
            }
            UnitMode.IN, UnitMode.FT -> {
                val eighth = inchPx / 8f
                var i = 0
                var y = 0f
                while (y < height) {
                    val isMajor = i % 8 == 0 // whole inch
                    val isHalf = i % 4 == 0
                    val isQuarter = i % 2 == 0
                    val reaction = reactionAt(y, lineY)
                    val len = when {
                        isMajor -> majorLen * (1f + reaction * 0.3f)
                        isHalf -> midLen * (1f + reaction * 0.2f)
                        isQuarter -> minorLen * 1.2f
                        else -> minorLen * 0.7f
                    }
                    val endX = if (isFlipped) width - len else len
                    drawLine(
                        color = if (isMajor) onSurface.copy(alpha = 0.95f)
                        else onSurface.copy(alpha = 0.2f + reaction * 0.5f),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = if (isMajor) strokeWidth * 2.2f else strokeWidth,
                        cap = StrokeCap.Round
                    )
                    if (isMajor) {
                        val wholeInches = i / 8
                        val label = when (unitMode) {
                            UnitMode.FT -> String.format(Locale.ROOT, "%.1f", wholeInches / 12f)
                            else -> wholeInches.toString()
                        }
                        val textX = if (isFlipped) width - len - labelOffset else len + labelOffset
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            textX,
                            y + 5.dp.toPx(),
                            Paint().apply {
                                color = onSurface.toArgb()
                                textSize = labelTextSize * (1f + reaction * 0.2f)
                                textAlign = if (isFlipped) Paint.Align.RIGHT else Paint.Align.LEFT
                                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                alpha = ((0.6f + reaction * 0.4f).coerceIn(0f, 1f) * 255).toInt()
                            }
                        )
                    }
                    y += eighth
                    i++
                }
            }
        }

        // --- Snap grid overlay ---
        // Faint horizontal lines spanning the full width at each snap
        // increment, drawn only when snapToGrid is on. Without this, toggling
        // snap had no visible effect at all beyond the button's own fill
        // color — it looked broken even once the underlying behavior worked,
        // since there was nothing on screen to confirm what "snap" meant.
        if (snapToGrid && snapIncrementPx > 0f) {
            var gridY = 0f
            var gridIndex = 0
            while (gridY < height) {
                // Skip drawing over positions already marked by a tick line
                // (every snap increment coincides with a minor tick), so this
                // only adds a visible full-width guide rather than doubling
                // the existing marks.
                drawLine(
                    color = primary.copy(alpha = 0.10f),
                    start = Offset(0f, gridY),
                    end = Offset(width, gridY),
                    strokeWidth = 1.dp.toPx()
                )
                gridY += snapIncrementPx
                gridIndex++
            }
        }

        // --- Anchor point (two-point mode) ---
        if (anchorYPx != null) {
            drawLine(
                color = primary.copy(alpha = 0.55f),
                start = Offset(0f, anchorYPx),
                end = Offset(width, anchorYPx),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
            drawCircle(
                color = primary.copy(alpha = 0.55f),
                radius = 5.dp.toPx(),
                center = Offset(if (isFlipped) width - 14.dp.toPx() else 14.dp.toPx(), anchorYPx)
            )
        }

        // --- Active measuring line ---
        if (lineY > 0f) {
            // Fill between anchor and line in two-point mode, drawn first so
            // the line and endpoint sit on top of it.
            if (anchorYPx != null) {
                drawRect(
                    color = primary.copy(alpha = 0.08f),
                    topLeft = Offset(0f, minOf(anchorYPx, lineY)),
                    size = Size(width, abs(lineY - anchorYPx))
                )
            }

            if (!performanceMode) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, primary.copy(alpha = 0.14f), Color.Transparent)
                    ),
                    topLeft = Offset(0f, lineY - 22.dp.toPx()),
                    size = Size(width, 44.dp.toPx())
                )
            }

            drawLine(
                color = primary,
                start = Offset(0f, lineY),
                end = Offset(width, lineY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(
                color = primary,
                radius = 6.dp.toPx(),
                center = Offset(if (isFlipped) width - 14.dp.toPx() else 14.dp.toPx(), lineY)
            )
        }
    }
}

@Composable
private fun MeasurementReadout(
    unitMode: UnitMode,
    onUnitModeChange: (UnitMode) -> Unit,
    cm: Float,
    inches: Float,
    twoPointMode: Boolean,
    hasAnchor: Boolean,
    primary: Color,
    secondary: Color,
    colorScheme: ColorScheme,
    onSurface: Color,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val (valueText, unitLabel) = when (unitMode) {
        UnitMode.MM -> String.format(Locale.ROOT, "%.1f", cm * 10f) to "mm"
        UnitMode.CM -> String.format(Locale.ROOT, "%.2f", cm) to "cm"
        UnitMode.IN -> String.format(Locale.ROOT, "%.3f", inches) to "in"
        UnitMode.FT -> String.format(Locale.ROOT, "%.3f", inches / 12f) to "ft"
    }

    Surface(
        modifier = Modifier
            .padding(24.dp)
            .shadow(
                elevation = 12.dp,
                shape = ExtraLargeExpressiveShape,
                spotColor = primary.copy(alpha = 0.25f)
            ),
        shape = ExtraLargeExpressiveShape,
        color = colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (twoPointMode) {
                ExpressiveStatePill(
                    text = if (hasAnchor) "Distance" else "Drop a start point",
                    icon = Icons.Rounded.SwapVert,
                    color = secondary
                )
                Spacer(Modifier.height(20.dp))
            }

            // Unit switcher — a real M3 segmented control, not decorative chips.
            SingleChoiceSegmentedButtonRow {
                UnitMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = unitMode == mode,
                        onClick = { onUnitModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, UnitMode.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = primary.copy(alpha = 0.15f),
                            activeContentColor = primary
                        )
                    ) {
                        Text(mode.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = onSurface
                )
                Text(
                    unitLabel,
                    modifier = Modifier.padding(bottom = 12.dp, start = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(
                    onClick = onCopy,
                    shape = MediumExpressiveShape,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceContainerHighest
                    )
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                }

                FilledIconButton(
                    onClick = onSave,
                    shape = MediumExpressiveShape,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = primary)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Save measurement")
                }

                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = MediumExpressiveShape,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceContainerHighest
                    )
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}

@Composable
private fun HistoryCarousel(
    history: List<Measurement>,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        shape = LargeExpressiveShape,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent measurements",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history.reversed()) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MediumExpressiveShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            val primaryText = when (item.unit) {
                                UnitMode.MM -> String.format(Locale.ROOT, "%.1f mm", item.cm * 10f)
                                UnitMode.CM -> String.format(Locale.ROOT, "%.2f cm", item.cm)
                                UnitMode.IN -> String.format(Locale.ROOT, "%.3f in", item.inches)
                                UnitMode.FT -> String.format(Locale.ROOT, "%.3f ft", item.inches / 12f)
                            }
                            val secondaryText = if (item.unit == UnitMode.MM || item.unit == UnitMode.CM)
                                String.format(Locale.ROOT, "%.3f in", item.inches)
                            else
                                String.format(Locale.ROOT, "%.2f cm", item.cm)

                            Text(primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                secondaryText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}
