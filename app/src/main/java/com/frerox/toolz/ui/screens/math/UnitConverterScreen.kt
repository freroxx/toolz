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

package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * M3 Expressive redesign v2 of the unit converter.
 *
 * Signature element: the **Dial** — a circular conversion gauge that sits at the waist of the
 * ribbon in place of a plain swap button. The dial's filled arc communicates the *scale* of the
 * current conversion (a huge multiplier like Byte -> Bit fills the ring; a near-1:1 conversion
 * like Meter -> Yard barely fills it), so the shape itself becomes a piece of information, not
 * just a control. Tapping the dial still swaps units, with the arc animating to its new value
 * and the whole ring doing a spring-driven flip.
 *
 * Secondary additions:
 *  - A quick-scrub slider under the input field for fast scanning through values.
 *  - A star/favorite affordance for units (separate from the existing long-press pin), shown as
 *    a small starred row above the category chips for one-tap access to favorite conversions.
 *  - A compact/expanded density toggle in the top bar so the tool works well both for a single
 *    quick lookup and for leaving on screen while cooking/building/traveling.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UnitConverterScreen(
    viewModel: UnitConverterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val units = state.availableUnits
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var compact by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Unit Converter",
                subtitle = if (compact) "Quick mode" else "Scientific conversions",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            compact = !compact
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = if (compact) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                            contentDescription = if (compact) "Switch to full view" else "Switch to quick view"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 24.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                CategoryChipRow(
                    selected = state.type,
                    onSelect = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onTypeChange(it)
                    }
                )

                Spacer(Modifier.height(24.dp))

                DialRibbon(
                    state = state,
                    units = units,
                    compact = compact,
                    onInputChange = viewModel::onInputValueChange,
                    onFromUnitChange = viewModel::onFromUnitChange,
                    onToUnitChange = viewModel::onToUnitChange,
                    onSwap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.swapUnits()
                    },
                    onTogglePin = { unit -> viewModel.togglePinned(state.type, unit) },
                    isFavorite = viewModel.isCurrentFavorite(),
                    onToggleFavorite = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleFavorite()
                    }
                )

                if (!compact) {
                    Spacer(Modifier.height(14.dp))
                    FormulaHintRow(state = state)

                    Spacer(Modifier.height(18.dp))
                    ResultCopyBar(
                        state = state,
                        onCopy = {
                            clipboard.setText(AnnotatedString("${state.inputValue} ${state.fromUnit} = ${state.outputValue} ${state.toUnit}"))
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )

                    val favoritesForType = state.favorites[state.type].orEmpty()
                    if (favoritesForType.isNotEmpty() || state.history.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        ConversionShelf(
                            favorites = favoritesForType,
                            history = state.history,
                            onSelect = { entry ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.applyHistory(entry)
                            }
                        )
                    }

                    if (state.type == ConversionType.CURRENCY) {
                        Spacer(Modifier.height(18.dp))
                        NoticeBanner(
                            text = "Exchange rates are fixed reference values for demonstration, not live market rates.",
                            icon = Icons.Rounded.Info
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Category picker: shape-morphing chips (rounded -> squircle on selection), horizontal scroll.
// ---------------------------------------------------------------------------------------

@Composable
private fun CategoryChipRow(
    selected: ConversionType,
    onSelect: (ConversionType) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(ConversionType.entries) { type ->
            CategoryChip(
                type = type,
                isSelected = type == selected,
                onClick = { onSelect(type) }
            )
        }
    }
}

@Composable
private fun CategoryChip(
    type: ConversionType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (isSelected) 18.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "chipScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chipColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipContentColor"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(50.dp),
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(getIconForType(type), contentDescription = null, modifier = Modifier.size(19.dp))
            AnimatedVisibility(
                visible = isSelected,
                enter = expandHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                Text(
                    text = type.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// The Dial Ribbon: signature element. One continuous surface with input on top, output on
// bottom, and a circular gauge at the waist that both swaps units and visualizes scale.
// ---------------------------------------------------------------------------------------

@Composable
private fun DialRibbon(
    state: UnitConverterState,
    units: List<String>,
    compact: Boolean,
    onInputChange: (String) -> Unit,
    onFromUnitChange: (String) -> Unit,
    onToUnitChange: (String) -> Unit,
    onSwap: () -> Unit,
    onTogglePin: (String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 22.dp,
                vertical = if (compact) 18.dp else 26.dp
            )
        ) {
            if (!compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FavoriteStar(isFavorite = isFavorite, onClick = onToggleFavorite)
                }
                Spacer(Modifier.height(4.dp))
            }

            RibbonField(
                label = "From",
                isInput = true,
                value = state.inputValue,
                unit = state.fromUnit,
                units = units,
                compact = compact,
                onValueChange = onInputChange,
                onUnitChange = onFromUnitChange,
                onTogglePin = onTogglePin,
                pinnedUnits = state.pinnedUnits[state.type].orEmpty().toSet()
            )

            if (!compact) {
                Spacer(Modifier.height(10.dp))
                ScrubSlider(value = state.inputValue, onValueChange = onInputChange)
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 10.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ConversionDial(
                    sweepFraction = state.dialSweep,
                    compact = compact,
                    onClick = onSwap
                )
            }

            RibbonField(
                label = "To",
                isInput = false,
                value = state.outputValue,
                unit = state.toUnit,
                units = units,
                compact = compact,
                onValueChange = {},
                onUnitChange = onToUnitChange,
                onTogglePin = onTogglePin,
                pinnedUnits = state.pinnedUnits[state.type].orEmpty().toSet()
            )
        }
    }
}

/**
 * The dial: a circular gauge whose filled arc length encodes how large the effective
 * multiplier from -> to is on a log scale (clamped). Doubles as the swap button — tapping it
 * flips fromUnit/toUnit and the arc animates from its old sweep to its new one, plus a 180°
 * flip of the whole dial so the direction of the swap reads visually, not just numerically.
 */
@Composable
private fun ConversionDial(
    sweepFraction: Float,
    compact: Boolean,
    onClick: () -> Unit
) {
    val size = if (compact) 48.dp else 60.dp

    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dialRotation"
    )
    val animatedSweep by animateFloatAsState(
        targetValue = sweepFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "dialSweep"
    )

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val arcColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        onClick = {
            flipped = !flipped
            onClick()
        },
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                val stroke = 4.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedSweep,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = "Swap units",
                tint = iconColor,
                modifier = Modifier
                    .size(if (compact) 20.dp else 24.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

/**
 * Small star toggle for marking the current from/to pair as a favorite. Separate from the
 * existing long-press-to-pin-a-unit gesture: pinning affects where a unit sits in its picker
 * list, while favoriting bookmarks a specific from -> to pair for instant recall later.
 */
@Composable
private fun FavoriteStar(isFavorite: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "favoriteScale"
    )
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        )
    }
}

/**
 * Horizontal drag strip beneath the input field for fast scrubbing through values without
 * the keyboard. Center is neutral; dragging right/left nudges the numeric value up/down with
 * velocity proportional to drag distance from center, springing back to center on release.
 */
@Composable
private fun ScrubSlider(value: String, onValueChange: (String) -> Unit) {
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scrubOffset"
    )
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = { dragOffsetPx = 0f },
                    onDragCancel = { dragOffsetPx = 0f }
                ) { change, delta ->
                    change.consume()
                    accumulated += delta
                    dragOffsetPx = accumulated.coerceIn(-120f, 120f)
                    val current = value.toDoubleOrNull() ?: 0.0
                    val step = if (abs(current) < 10) 0.1 else if (abs(current) < 1000) 1.0 else current * 0.01
                    val direction = if (delta > 0) 1 else -1
                    if (abs(delta) > 2f) {
                        val next = (current + direction * step).let {
                            if (it < 0) 0.0 else it
                        }
                        val formatted = if (next % 1.0 == 0.0) next.toLong().toString()
                        else String.format("%.3f", next).trimEnd('0').trimEnd('.')
                        onValueChange(formatted)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2
            val trackWidth = size.width * 0.9f
            val startX = (size.width - trackWidth) / 2
            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(startX, midY),
                end = Offset(startX + trackWidth, midY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            val handleX = (size.width / 2) + animatedOffset
            drawCircle(
                color = Color.Gray.copy(alpha = 0.55f),
                radius = 5.dp.toPx(),
                center = Offset(handleX.coerceIn(startX, startX + trackWidth), midY)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RibbonField(
    label: String,
    isInput: Boolean,
    value: String,
    unit: String,
    units: List<String>,
    compact: Boolean,
    onValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    pinnedUnits: Set<String> = emptySet()
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!compact) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isInput) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = (if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall)
                        .copy(fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (compact) 48.dp else 64.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "outputValue"
                    ) { animatedValue ->
                        Text(
                            text = animatedValue,
                            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            UnitSelector(unit, units, compact, onUnitChange, onTogglePin, pinnedUnits)
        }
    }
}

/**
 * Unit picker trigger: a compact pill that, on tap, raises a full M3 Expressive modal bottom
 * sheet containing a scrollable grid of unit tiles (replacing the old horizontal wheel/dropdown).
 * Pinned units get their own row up top; tapping a tile selects it with a spring pop, long-press
 * still toggles the pin. The trigger pill itself does a small squash/stretch on open/close so it
 * reads as the thing that "opens into" the sheet, not an unrelated control.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun UnitSelector(
    selectedUnit: String,
    units: List<String>,
    compact: Boolean,
    onUnitChange: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    pinnedUnits: Set<String> = emptySet()
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val height = if (compact) 48.dp else 64.dp

    val pillScale by animateFloatAsState(
        targetValue = if (sheetOpen) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "unitPillScale"
    )

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            sheetOpen = true
        },
        modifier = Modifier
            .height(height)
            .widthIn(min = 104.dp)
            .graphicsLayer { scaleX = pillScale; scaleY = pillScale },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                selectedUnit,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(19.dp)
            )
        }
    }

    if (sheetOpen) {
        UnitPickerSheet(
            selectedUnit = selectedUnit,
            units = units,
            pinnedUnits = pinnedUnits,
            onDismiss = { sheetOpen = false },
            onUnitChange = {
                onUnitChange(it)
                sheetOpen = false
            },
            onTogglePin = onTogglePin
        )
    }
}

/**
 * The grid itself. Pinned units (if any) render first in their own labeled section so frequent
 * conversions stay one tap away; everything else follows in a 3-column adaptive grid. Each tile
 * is a self-contained toggle: selected state gets a filled primary tile with a spring pop and a
 * check mark, unselected tiles are quiet outlined surfaces. Long-press pins/unpins.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun UnitPickerSheet(
    selectedUnit: String,
    units: List<String>,
    pinnedUnits: Set<String>,
    onDismiss: () -> Unit,
    onUnitChange: (String) -> Unit,
    onTogglePin: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val pinned = units.filter { it in pinnedUnits }
    val rest = units.filterNot { it in pinnedUnits }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Choose a unit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Tap to select \u00b7 hold to pin to the top",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                if (pinned.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                "Pinned",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    items(pinned, key = { "pinned-$it" }) { unit ->
                        UnitTile(
                            unit = unit,
                            isSelected = unit == selectedUnit,
                            isPinned = true,
                            onClick = { onUnitChange(unit) },
                            onLongClick = { onTogglePin(unit) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(6.dp))
                    }
                }
                items(rest, key = { it }) { unit ->
                    UnitTile(
                        unit = unit,
                        isSelected = unit == selectedUnit,
                        isPinned = false,
                        onClick = { onUnitChange(unit) },
                        onLongClick = { onTogglePin(unit) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnitTile(
    unit: String,
    isSelected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "unitTileScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "unitTileColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "unitTileContentColor"
    )
    val corner by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 16.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "unitTileCorner"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(corner),
        color = containerColor,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            if (isPinned && !isSelected) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(
                text = unit,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Formula hint: small, quiet line showing the active multiplier/formula.
// ---------------------------------------------------------------------------------------

@Composable
private fun FormulaHintRow(state: UnitConverterState) {
    AnimatedVisibility(
        visible = state.formulaHint.isNotBlank(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Functions,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = state.formulaHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Result summary with copy action.
// ---------------------------------------------------------------------------------------

@Composable
private fun ResultCopyBar(state: UnitConverterState, onCopy: () -> Unit) {
    var justCopied by remember { mutableStateOf(false) }
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1600)
            justCopied = false
        }
    }

    AnimatedVisibility(
        visible = state.inputValue.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${state.inputValue} ${state.fromUnit} = ${state.outputValue} ${state.toUnit}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.isApproximate) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Approximate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { onCopy(); justCopied = true }) {
                    Crossfade(targetState = justCopied, label = "copyIcon") { copied ->
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copy result",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Conversion shelf: unifies Favorites + Recent behind one M3 Expressive segmented switcher
// instead of two separately-styled rows. A pill-shaped SingleChoiceSegmentedButtonRow flips
// between the two lists with a crossfade + slide; each entry is a proper elevated card with
// clear from/to hierarchy (not a flat text chip), and favorites get a small filled star badge
// so the two states stay visually distinguishable even after the switch.
// ---------------------------------------------------------------------------------------

private enum class ShelfTab { FAVORITES, RECENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversionShelf(
    favorites: List<ConversionHistoryEntry>,
    history: List<ConversionHistoryEntry>,
    onSelect: (ConversionHistoryEntry) -> Unit
) {
    val hasFavorites = favorites.isNotEmpty()
    val hasHistory = history.isNotEmpty()
    var tab by remember(hasFavorites, hasHistory) {
        mutableStateOf(if (hasFavorites) ShelfTab.FAVORITES else ShelfTab.RECENT)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (hasFavorites && hasHistory) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tab == ShelfTab.FAVORITES,
                        onClick = { tab = ShelfTab.FAVORITES },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {},
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Favorites", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    SegmentedButton(
                        selected = tab == ShelfTab.RECENT,
                        onClick = { tab = ShelfTab.RECENT },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {},
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Recent", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                ) {
                    Icon(
                        imageVector = if (hasFavorites) Icons.Rounded.Star else Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (hasFavorites) "Favorites" else "Recent",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMedium)) + slideInHorizontally(spring(stiffness = Spring.StiffnessMedium)) { it / 6 }) togetherWith
                        (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + slideOutHorizontally(spring(stiffness = Spring.StiffnessMedium)) { -it / 6 })
                },
                label = "shelfTab"
            ) { selectedTab ->
                val entries = if (selectedTab == ShelfTab.FAVORITES) favorites else history
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(entries, key = { "${selectedTab}-${it.fromUnit}-${it.toUnit}" }) { entry ->
                        ShelfEntryCard(
                            entry = entry,
                            isFavorite = selectedTab == ShelfTab.FAVORITES,
                            onClick = { onSelect(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfEntryCard(
    entry: ConversionHistoryEntry,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "shelfCardScale"
    )

    Surface(
        onClick = {
            pressed = true
            onClick()
        },
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .widthIn(min = 132.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isFavorite) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (!isFavorite) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)) else null
    ) {
        LaunchedEffect(pressed) {
            if (pressed) {
                delay(120)
                pressed = false
            }
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    entry.fromUnit,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isFavorite) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isFavorite) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp).padding(start = 6.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    entry.toUnit,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isFavorite) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Generic quiet notice banner (used for the currency disclaimer).
// ---------------------------------------------------------------------------------------

@Composable
private fun NoticeBanner(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ConversionType.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")

private fun getIconForType(type: ConversionType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        ConversionType.LENGTH -> Icons.Rounded.Straighten
        ConversionType.WEIGHT -> Icons.Rounded.MonitorWeight
        ConversionType.TEMPERATURE -> Icons.Rounded.Thermostat
        ConversionType.VOLUME -> Icons.Rounded.Opacity
        ConversionType.AREA -> Icons.Rounded.Layers
        ConversionType.SPEED -> Icons.Rounded.Speed
        ConversionType.TIME -> Icons.Rounded.Schedule
        ConversionType.DIGITAL_STORAGE -> Icons.Rounded.SdCard
        ConversionType.ENERGY -> Icons.Rounded.Bolt
        ConversionType.FORCE -> Icons.Rounded.FitnessCenter
        ConversionType.PRESSURE -> Icons.Rounded.TireRepair
        ConversionType.POWER -> Icons.Rounded.ElectricBolt
        ConversionType.CURRENCY -> Icons.Rounded.Paid
    }
}
