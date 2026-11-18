@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.liquidglass.LocalLiquidGlassEnabled
import com.frerox.toolz.ui.liquidglass.liquidGlassEffect
import com.frerox.toolz.ui.liquidglass.liquidGlassTile
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// ROOT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showHistorySheet by remember { mutableStateOf(false) }
    val vibrationManager = LocalVibrationManager.current
    val hapticEnabled = LocalHapticEnabled.current

    LaunchedEffect(state.error) {
        if (state.error != null && hapticEnabled) vibrationManager?.vibrateError()
    }

    Box(modifier = Modifier.fillMaxSize().toolzBackground()) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    title = stringResource(R.string.st_CalculatorScreen_f1a2),
                    navigationIcon = {
                        ToolzExpressiveIconButton(
                            onClick = onBack,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            shape = MediumExpressiveShape,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.st_CalculatorScreen_3d5b),
                            )
                        }
                    },
                    actions = {
                        ToolzExpressiveIconButton(
                            onClick = { viewModel.onSheet(CalcSheet.SETUP) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            shape = MediumExpressiveShape,
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Setup")
                        }
                        Spacer(Modifier.width(6.dp))
                        ToolzExpressiveIconButton(
                            onClick = { showHistorySheet = true },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            shape = MediumExpressiveShape,
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = stringResource(R.string.st_CalculatorScreen_9e2c),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    },
                    modifier = Modifier.statusBarsPadding(),
                )
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentAlignment = Alignment.TopCenter,
            ) {
                CalculatorMain(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                )
            }
        }
    }

    if (showHistorySheet) {
        val glass = LocalLiquidGlassEnabled.current
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = if (glass) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    width = 48.dp,
                    height = 4.dp,
                    color = if (glass) {
                        Color.White.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                )
            },
        ) {
            CalculatorHistoryContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .navigationBarsPadding(),
                onPicked = { showHistorySheet = false },
            )
        }
    }

    when (state.sheet) {
        CalcSheet.NONE -> Unit
        CalcSheet.SETUP -> ToolSheet(
            onDismiss = viewModel::onDismissSheet,
            scrollable = true,
        ) { SetupSheetContent(state, viewModel) }
        CalcSheet.CATALOG -> ToolSheet(onDismiss = viewModel::onDismissSheet) {
            CatalogSheetContent(viewModel)
        }
        CalcSheet.CONST -> ToolSheet(onDismiss = viewModel::onDismissSheet) {
            ConstantsSheetContent(state, viewModel)
        }
        CalcSheet.STORE -> ToolSheet(
            onDismiss = viewModel::onDismissSheet,
            scrollable = true,
        ) { VariablesSheetContent(state, store = true, viewModel = viewModel) }
        CalcSheet.RECALL -> ToolSheet(
            onDismiss = viewModel::onDismissSheet,
            scrollable = true,
        ) { VariablesSheetContent(state, store = false, viewModel = viewModel) }
    }
}

@Composable
private fun ToolSheet(
    onDismiss: () -> Unit,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalLiquidGlassEnabled.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = if (glass) {
            // Same frosted-sheet convention as the app's Quick Access sheet.
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 48.dp,
                height = 4.dp,
                color = if (glass) {
                    Color.White.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
            )
        },
    ) {
        if (scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .fadingEdges(top = 4.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                content = content,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                content = content,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN CONTENT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CalculatorMain(
    state: CalculatorState,
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current
    val hapticEnabled = LocalHapticEnabled.current
    val padWeight by animateFloatAsState(
        targetValue = if (state.scientific) 2.35f else 1.62f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "padWeight",
    )

    val onKey: (KeyDef) -> Unit = { def ->
        if (hapticEnabled) {
            when (def.kind) {
                KeyKind.EQUALS, KeyKind.CLEAR -> vibrationManager?.vibrateLongClick()
                KeyKind.MODIFIER -> vibrationManager?.vibrateTick()
                else -> vibrationManager?.vibrateClick()
            }
        }
        viewModel.onKey(def)
    }
    val onRepeatKey: (KeyDef) -> Unit = { viewModel.onKey(it) }

    Column(modifier = modifier.fillMaxSize()) {
        StatusStrip(state, viewModel)
        CalculatorDisplay(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 132.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        CalculatorKeypad(
            state = state,
            onKey = onKey,
            onRepeatKey = onRepeatKey,
            modifier = Modifier
                .weight(padWeight)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STATUS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatusChip(
    text: String,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "chipScale",
    )
    val bg by animateColorAsState(container, tween(180), label = "chipContainer")
    val fg by animateColorAsState(content, tween(180), label = "chipContent")
    val glass = LocalLiquidGlassEnabled.current
    val glassDark = isSystemInDarkTheme()
    // Mostly clear glass; only the selected chip carries real colour, and the
    // animated background keeps press feedback visible through the material.
    val glassTint = bg.copy(alpha = if (selected) 0.46f else 0.20f)
    val glassRim = if (selected) {
        fg.copy(alpha = 0.34f)
    } else {
        Color.White.copy(alpha = if (glassDark) 0.20f else 0.55f)
    }
    val border = BorderStroke(
        width = 1.dp,
        color = if (selected) fg.copy(alpha = 0.24f) else scheme.outlineVariant.copy(alpha = 0.18f),
    )
    val label: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(start = if (icon != null) 10.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = fg.copy(alpha = if (selected) 1f else 0.72f),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
                maxLines = 1,
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .liquidGlassTile(
                    shape = CircleShape,
                    tint = glassTint,
                    rimColor = glassRim,
                    shadowRadius = 3.dp,
                )
                .semantics {
                    role = Role.Button
                    if (selected) stateDescription = "Selected"
                },
            interactionSource = interaction,
            shape = CircleShape,
            color = if (glass) Color.Transparent else bg,
            contentColor = fg,
            border = if (glass) null else border,
            content = label,
        )
    } else {
        Surface(
            modifier = Modifier.liquidGlassTile(
                shape = CircleShape,
                tint = glassTint,
                rimColor = glassRim,
                shadowRadius = 3.dp,
            ),
            shape = CircleShape,
            color = if (glass) Color.Transparent else bg,
            contentColor = fg,
            border = if (glass) null else border,
            content = label,
        )
    }
}

@Composable
private fun StatusBadge(text: String, container: Color, content: Color) {
    val glass = LocalLiquidGlassEnabled.current
    Surface(
        modifier = Modifier.liquidGlassTile(
            shape = CircleShape,
            tint = container.copy(alpha = 0.72f),
            rimColor = content.copy(alpha = 0.30f),
            shadowRadius = 2.dp,
        ),
        shape = CircleShape,
        color = if (glass) Color.Transparent else container,
        contentColor = content,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun KeypadModeButton(scientific: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val vibrationManager = LocalVibrationManager.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "modeScale",
    )
    val bg by animateColorAsState(
        targetValue = if (scientific) scheme.primary else scheme.surfaceContainerHigh,
        animationSpec = tween(200),
        label = "modeContainer",
    )
    val fg by animateColorAsState(
        targetValue = if (scientific) scheme.onPrimary else scheme.onSurface,
        animationSpec = tween(200),
        label = "modeContent",
    )
    val glass = LocalLiquidGlassEnabled.current
    val glassDark = isSystemInDarkTheme()
    val glassTint = if (scientific) {
        scheme.primary.copy(alpha = 0.66f)
    } else {
        scheme.surfaceContainerHigh.copy(alpha = 0.22f)
    }
    val glassRim = if (scientific) {
        Color.White.copy(alpha = 0.40f)
    } else {
        Color.White.copy(alpha = if (glassDark) 0.20f else 0.55f)
    }

    Surface(
        onClick = {
            vibrationManager?.vibrateTick()
            onClick()
        },
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .animateContentSize()
            .liquidGlassTile(
                shape = CircleShape,
                tint = glassTint,
                rimColor = glassRim,
                shadowRadius = 3.dp,
            )
            .semantics { role = Role.Button },
        interactionSource = interaction,
        shape = CircleShape,
        color = if (glass) Color.Transparent else bg,
        contentColor = fg,
        border = if (glass) {
            null
        } else {
            BorderStroke(
                1.dp,
                if (scientific) fg.copy(alpha = 0.2f) else scheme.outlineVariant.copy(alpha = 0.18f),
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = scientific,
                transitionSpec = {
                    (fadeIn(tween(140)) + scaleIn(initialScale = 0.7f))
                        .togetherWith(fadeOut(tween(90)) + scaleOut(targetScale = 0.7f))
                },
                label = "modeIcon",
            ) { sci ->
                Icon(
                    imageVector = if (sci) Icons.Rounded.Functions else Icons.Rounded.Calculate,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = if (scientific) "Scientific" else "Basic",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusStrip(state: CalculatorState, viewModel: CalculatorViewModel) {
    val scheme = MaterialTheme.colorScheme
    val vibrationManager = LocalVibrationManager.current
    val scroll = rememberScrollState()
    val format = when (state.fmtKind) {
        FormatKind.NORM -> "NORM"
        FormatKind.FIX -> "FIX ${state.fmtDigits}"
        FormatKind.SCI -> "SCI ${state.fmtDigits}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalFadingEdges(left = 4.dp, right = 18.dp)
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(
                text = state.angle.label,
                icon = Icons.Rounded.Functions,
                onClick = {
                    vibrationManager?.vibrateTick()
                    viewModel.onCycleAngle()
                },
            )
            StatusChip(
                text = format,
                icon = Icons.Rounded.Numbers,
                onClick = {
                    vibrationManager?.vibrateTick()
                    viewModel.onSheet(CalcSheet.SETUP)
                },
            )
            StatusChip(
                text = if (state.showFraction) "n/d" else "dec",
                icon = Icons.Rounded.SwapHoriz,
                onClick = {
                    vibrationManager?.vibrateTick()
                    viewModel.onSetShowFraction(!state.showFraction)
                },
                container = if (state.showFraction) scheme.secondaryContainer else scheme.surfaceContainer,
                content = if (state.showFraction) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                selected = state.showFraction,
            )
            AnimatedVisibility(
                visible = state.layer == KeyLayer.SHIFT,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.82f),
                exit = fadeOut(tween(80)) + scaleOut(targetScale = 0.86f),
            ) {
                StatusBadge("SHIFT", scheme.tertiary, scheme.onTertiary)
            }
            AnimatedVisibility(
                visible = state.layer == KeyLayer.ALPHA,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.82f),
                exit = fadeOut(tween(80)) + scaleOut(targetScale = 0.86f),
            ) {
                StatusBadge("ALPHA", scheme.secondary, scheme.onSecondary)
            }
            AnimatedVisibility(
                visible = state.hyp,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.82f),
                exit = fadeOut(tween(80)) + scaleOut(targetScale = 0.86f),
            ) {
                StatusBadge("HYP", scheme.primary, scheme.onPrimary)
            }
        }

        Spacer(Modifier.width(8.dp))
        KeypadModeButton(
            scientific = state.scientific,
            onClick = viewModel::onToggleScientific,
        )
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// DISPLAY
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CalculatorDisplay(
    state: CalculatorState,
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val vibrationManager = LocalVibrationManager.current
    val field = state.fields.firstOrNull() ?: EditField()
    val glass = LocalLiquidGlassEnabled.current
    val glassDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(36.dp)

    Surface(
        modifier = modifier.then(
            if (glass) {
                Modifier.liquidGlassEffect(
                    shape = shape,
                    tint = scheme.surfaceContainerLowest.copy(alpha = 0.12f),
                    refractionFactor = 0.9f,
                    shadowRadius = 10.dp,
                )
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = if (glass) Color.Transparent else scheme.surfaceContainerLowest,
        // Surface would fall back to Color.Unspecified for a transparent container,
        // which would drop the inherited content colour and render text unpainted.
        contentColor = scheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (glass) Color.White.copy(alpha = if (glassDark) 0.16f else 0.45f)
            else scheme.outlineVariant.copy(alpha = 0.22f),
        ),
        tonalElevation = if (glass) 0.dp else 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = if (glass) 0.06f else 0.10f),
                                scheme.primary.copy(alpha = if (glass) 0.02f else 0.035f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.width * 0.85f,
                        ),
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                scheme.primary.copy(alpha = if (glass) 0.12f else 0.18f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(size.width * 0.16f, 0f),
                        end = Offset(size.width * 0.84f, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                ScrollingExpression(
                    text = field.text,
                    cursor = field.cursor,
                    onCursor = { viewModel.onCursor(0, it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.22f),
                )

                ResultArea(
                    state = state,
                    onCopy = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.onCopyResult()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .padding(top = 10.dp),
                )
            }

            ToastPill(
                text = state.toast,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
            )
        }
    }
}


@Composable
private fun ScrollingExpression(
    text: String,
    cursor: Int,
    onCursor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val performanceMode = LocalPerformanceMode.current
    val cur = cursor.coerceIn(0, text.length)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val fontSizeTarget = when {
        text.length > 48 -> 24f
        text.length > 28 -> 28f
        else -> 34f
    }
    val fontSize by animateFloatAsState(
        targetValue = fontSizeTarget,
        animationSpec = tween(180),
        label = "expressionSize",
    )
    val style = MaterialTheme.typography.headlineLarge.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.18f).sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum",
    )

    // The caret only needs to repaint a 2dp rectangle. Keeping the blink on the
    // draw path means the infinite transition no longer recomposes this whole
    // text cluster 60 times a second, and it is skipped entirely in performance
    // mode.
    val caretAlpha = if (performanceMode) {
        null
    } else {
        rememberInfiniteTransition(label = "caret").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "caretBlink",
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .horizontalFadingEdges(left = 16.dp, right = 18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        val viewport = with(density) { maxWidth.toPx() }
        val rightInset = with(density) { 18.dp.toPx() }
        val textWidth = layout?.size?.width?.toFloat() ?: 0f
        val caretX = layout?.let { current ->
            val safe = cur.coerceIn(0, current.layoutInput.text.length)
            current.getHorizontalPosition(safe, usePrimaryDirection = true)
        } ?: 0f
        val targetShift = when {
            layout == null -> 0f
            textWidth <= viewport - rightInset -> viewport - textWidth - rightInset
            else -> (viewport - rightInset - caretX)
                .coerceIn(viewport - textWidth - rightInset, 0f)
        }
        val shift by animateFloatAsState(
            targetValue = targetShift,
            animationSpec = tween(160),
            label = "expressionShift",
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(shift.roundToInt(), 0) }
                .wrapContentWidth(unbounded = true),
        ) {
            Text(
                text = if (text.isEmpty()) "0" else text,
                style = style,
                color = if (text.isEmpty()) {
                    scheme.onSurfaceVariant.copy(alpha = 0.32f)
                } else {
                    scheme.onSurface
                },
                maxLines = 1,
                softWrap = false,
                onTextLayout = { layout = it },
                modifier = Modifier.pointerInput(text, cur) {
                    detectTapGestures { pos ->
                        val current = layout
                        if (current == null || text.isEmpty()) {
                            onCursor(0)
                        } else {
                            onCursor(
                                current.getOffsetForPosition(pos).coerceIn(0, text.length),
                            )
                        }
                    }
                },
            )

            if (text.isNotEmpty()) {
                val caretHeight = with(density) {
                    ((layout?.size?.height ?: 0).toFloat()).toDp()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(caretX.roundToInt() - 1, 0) }
                        .width(2.dp)
                        .height(caretHeight)
                        .graphicsLayer { alpha = caretAlpha?.value ?: 1f }
                        .background(
                            color = scheme.primary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}


private data class ResultSnapshot(
    val error: String?,
    val line: OutLine?,
    val note: String?,
)

@Composable
private fun ResultArea(
    state: CalculatorState,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val out = state.outputs.firstOrNull()
    val hasResult = out != null && state.error == null
    val copied = state.toast == "Copied"
    val resultSize by animateFloatAsState(
        targetValue = if (state.evaluated) 48f else 30f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "resultSize",
    )
    val resultInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = hasResult,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.88f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CopyAction(copied = copied, onClick = onCopy)
                Spacer(Modifier.width(10.dp))
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    enabled = hasResult,
                    interactionSource = resultInteraction,
                    indication = null,
                    onClickLabel = stringResource(R.string.st_CalculatorScreen_1a2b),
                    onClick = onCopy,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AnimatedContent(
                targetState = ResultSnapshot(state.error, out, state.note),
                transitionSpec = {
                    (
                        fadeIn(tween(180)) +
                            slideInVertically(tween(220)) { it / 5 } +
                            scaleIn(initialScale = 0.94f)
                        ).togetherWith(
                        fadeOut(tween(110)) +
                            slideOutVertically(tween(110)) { -it / 5 },
                    )
                },
                contentAlignment = Alignment.CenterEnd,
                label = "resultValue",
            ) { snapshot ->
                when {
                    snapshot.error != null -> ErrorChip(snapshot.error)
                    snapshot.line != null -> ResultText(
                        line = snapshot.line,
                        sizeSp = resultSize,
                        color = if (state.evaluated) {
                            scheme.primary
                        } else {
                            scheme.onSurfaceVariant.copy(alpha = 0.78f)
                        },
                        weight = if (state.evaluated) FontWeight.Bold else FontWeight.Medium,
                    )
                    snapshot.note != null -> Text(
                        text = snapshot.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyAction(copied: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (copied) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = tween(160),
        label = "copyContainer",
    )
    val content by animateColorAsState(
        targetValue = if (copied) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "copyContent",
    )

    val glass = LocalLiquidGlassEnabled.current

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .liquidGlassTile(
                shape = CircleShape,
                tint = container.copy(alpha = 0.34f),
                rimColor = content.copy(alpha = 0.30f),
                shadowRadius = 2.dp,
            ),
        shape = CircleShape,
        color = if (glass) Color.Transparent else container,
        contentColor = content,
        border = if (glass) {
            null
        } else {
            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.18f))
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = copied,
                transitionSpec = {
                    (fadeIn(tween(120)) + scaleIn(initialScale = 0.7f))
                        .togetherWith(fadeOut(tween(80)) + scaleOut(targetScale = 0.7f))
                },
                label = "copyIcon",
            ) { done ->
                Icon(
                    imageVector = if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = if (done) "Copied" else stringResource(R.string.st_CalculatorScreen_1a2b),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorChip(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val performanceMode = LocalPerformanceMode.current
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }

    LaunchedEffect(text, performanceMode) {
        if (performanceMode) {
            shake.snapTo(1f)
            return@LaunchedEffect
        }
        shake.snapTo(0f)
        shake.animateTo(1f, tween(400, easing = LinearEasing))
    }
    val offsetX = if (performanceMode) {
        0f
    } else {
        sin(shake.value * PI.toFloat() * 6f) *
            (1f - shake.value) *
            with(density) { 9.dp.toPx() }
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = SmallExpressiveShape,
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultText(
    line: OutLine,
    sizeSp: Float,
    color: Color,
    weight: FontWeight = FontWeight.SemiBold,
) {
    if (line.num != null && line.den != null) {
        val len = maxOf(line.num.length, line.den.length) + 2
        val factor = when {
            len > 22 -> 0.56f
            len > 16 -> 0.72f
            len > 11 -> 0.86f
            else -> 1f
        }
        val style = MaterialTheme.typography.displaySmall.copy(
            fontSize = (sizeSp * factor).sp,
            fontWeight = weight,
            lineHeight = (sizeSp * factor * 1.15f).sp,
            letterSpacing = (-0.6).sp,
            fontFeatureSettings = "tnum",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalFadingEdges(left = 12.dp, right = 12.dp)
                .horizontalScroll(rememberScrollState(), reverseScrolling = true),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line.negative) Text("−", style = style, color = color)
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        line.num,
                        style = style.copy(
                            fontSize = style.fontSize * 0.72f,
                            lineHeight = style.lineHeight * 0.72f,
                        ),
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        thickness = 2.dp,
                        color = color.copy(alpha = 0.75f),
                    )
                    Text(
                        line.den,
                        style = style.copy(
                            fontSize = style.fontSize * 0.72f,
                            lineHeight = style.lineHeight * 0.72f,
                        ),
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    } else {
        val needsAutoSize = line.text.length > 7
        Text(
            text = line.text,
            style = if (needsAutoSize) {
                TextStyle(
                    fontFamily = MaterialTheme.typography.displaySmall.fontFamily,
                    fontWeight = weight,
                    letterSpacing = (-0.6).sp,
                    fontFeatureSettings = "tnum",
                )
            } else {
                MaterialTheme.typography.displaySmall.copy(
                    fontSize = sizeSp.sp,
                    fontWeight = weight,
                    lineHeight = (sizeSp * 1.15f).sp,
                    letterSpacing = (-0.6).sp,
                    fontFeatureSettings = "tnum",
                )
            },
            color = color,
            maxLines = 1,
            softWrap = false,
            autoSize = if (needsAutoSize) {
                TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = sizeSp.sp,
                    stepSize = 1.sp,
                )
            } else {
                null
            },
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToastPill(text: String?, modifier: Modifier = Modifier) {
    val icon = when {
        text == null -> Icons.Rounded.Check
        text.startsWith("Nothing") -> Icons.Rounded.ErrorOutline
        text == "DEG" || text == "RAD" || text == "GRA" -> Icons.Rounded.Functions
        else -> Icons.Rounded.Check
    }

    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(140)) +
            scaleIn(initialScale = 0.9f) +
            slideInVertically(tween(180)) { -it / 2 },
        exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.94f),
    ) {
        Surface(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 11.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(
                    text ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// KEYPAD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CalculatorKeypad(
    state: CalculatorState,
    onKey: (KeyDef) -> Unit,
    onRepeatKey: (KeyDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val layerAccent by animateColorAsState(
        targetValue = when (state.layer) {
            KeyLayer.SHIFT -> scheme.tertiary.copy(alpha = 0.055f)
            KeyLayer.ALPHA -> scheme.secondary.copy(alpha = 0.055f)
            KeyLayer.NONE -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "layerAccent",
    )
    val layerBorder by animateColorAsState(
        targetValue = when (state.layer) {
            KeyLayer.SHIFT -> scheme.tertiary.copy(alpha = 0.16f)
            KeyLayer.ALPHA -> scheme.secondary.copy(alpha = 0.16f)
            KeyLayer.NONE -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "layerBorder",
    )
    val glass = LocalLiquidGlassEnabled.current
    val padShape = RoundedCornerShape(30.dp)

    AnimatedContent(
        targetState = state.scientific,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(220)) +
                scaleIn(initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 1f)))
                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.98f))
        },
        contentAlignment = Alignment.BottomCenter,
        label = "keypadDensity",
    ) { scientific ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The pad itself is deliberately not a glass slab. Every key in
                // it is already glass, so a second full-screen blur stacked ~4
                // offscreen layers underneath 30 others and only darkened the
                // material. A flat scrim still groups the pad without costing a
                // single extra layer, and the layer wash / rim below is all the
                // shift-alpha slab ever actually communicated.
                .clip(padShape)
                .background(
                    color = if (glass) scheme.surfaceContainerLow.copy(alpha = 0.12f) else Color.Transparent,
                    shape = padShape,
                )
                .background(layerAccent)
                .then(
                    if (state.layer != KeyLayer.NONE) {
                        Modifier.border(BorderStroke(1.dp, layerBorder), padShape)
                    } else {
                        Modifier
                    },
                )
                .padding(2.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (scientific) {
                    CalcKeys.fullFunctions.forEach { keys ->
                        KeyRow(keys, weight = 0.82f, state = state, hints = true, big = false, onKey = onKey, onRepeatKey = onRepeatKey)
                    }
                    CalcKeys.numbers.forEach { keys ->
                        KeyRow(keys, weight = 1f, state = state, hints = false, big = false, onKey = onKey, onRepeatKey = onRepeatKey)
                    }
                } else {
                    CalcKeys.basic.forEach { keys ->
                        KeyRow(keys, weight = 1f, state = state, hints = false, big = true, onKey = onKey, onRepeatKey = onRepeatKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(
    keys: List<KeyDef>,
    weight: Float,
    state: CalculatorState,
    hints: Boolean,
    big: Boolean,
    onKey: (KeyDef) -> Unit,
    onRepeatKey: (KeyDef) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(weight),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { def ->
            CalcKey(
                def = def,
                state = state,
                hints = hints,
                big = big,
                onKey = onKey,
                onRepeatKey = onRepeatKey,
                modifier = Modifier
                    .weight(def.wide)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun CalcKey(
    def: KeyDef,
    state: CalculatorState,
    hints: Boolean,
    big: Boolean,
    onKey: (KeyDef) -> Unit,
    onRepeatKey: (KeyDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val layer = state.layer
    val active = when (def.id) {
        "shift" -> layer == KeyLayer.SHIFT
        "alpha" -> layer == KeyLayer.ALPHA
        "hyp" -> state.hyp
        else -> false
    }

    val (baseBg, baseFg) = when {
        active && def.id == "shift" -> scheme.tertiary to scheme.onTertiary
        active && def.id == "alpha" -> scheme.secondary to scheme.onSecondary
        active -> scheme.primary to scheme.onPrimary
        else -> when (def.kind) {
            KeyKind.DIGIT -> scheme.surfaceContainerHigh to scheme.onSurface
            KeyKind.OPERATOR -> scheme.secondaryContainer to scheme.onSecondaryContainer
            KeyKind.EQUALS -> scheme.primary to scheme.onPrimary
            KeyKind.CLEAR -> scheme.errorContainer to scheme.onErrorContainer
            KeyKind.DELETE -> scheme.surfaceContainerHighest to scheme.onSurface
            KeyKind.FUNCTION -> scheme.surfaceContainer to scheme.onSurfaceVariant
            KeyKind.MODIFIER -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            KeyKind.NAV -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        }
    }

    val label = when (layer) {
        KeyLayer.SHIFT -> def.shiftLabel ?: def.label
        KeyLayer.ALPHA -> def.alphaLabel ?: def.label
        KeyLayer.NONE -> def.label
    }
    val labelColor = when {
        layer == KeyLayer.SHIFT && def.shiftLabel != null && !active -> scheme.tertiary
        layer == KeyLayer.ALPHA && def.alphaLabel != null && !active -> scheme.secondary
        else -> baseFg
    }
    val showIcon = def.id == "left" || def.id == "right" || def.label == "DEL"
    // One corner language for the whole pad. Per-kind radii turned the keypad
    // into a patchwork; only the equals key is allowed to be a hero shape.
    val keyShape = if (def.kind == KeyKind.EQUALS) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(20.dp)
    }
    val borderBase = when {
        active -> baseFg.copy(alpha = 0.28f)
        def.kind == KeyKind.EQUALS -> baseFg.copy(alpha = 0.12f)
        def.kind == KeyKind.DIGIT || def.kind == KeyKind.FUNCTION ->
            scheme.outlineVariant.copy(alpha = 0.20f)
        def.kind == KeyKind.DELETE || def.kind == KeyKind.NAV ->
            scheme.outlineVariant.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    var pressed by remember(def.id) { mutableStateOf(false) }
    var pressPoint by remember(def.id) { mutableStateOf(Offset.Unspecified) }
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "keyPress",
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) lerp(baseBg, baseFg, 0.16f) else baseBg,
        animationSpec = tween(80),
        label = "keyBackground",
    )
    val glass = LocalLiquidGlassEnabled.current
    val glassDark = isSystemInDarkTheme()
    // Neutral glass carries the keypad; colour is reserved for the keys that
    // actually mean something (equals, modifiers, clears) so the pad reads as
    // one material instead of a tray of painted tiles.
    val glassKeyTint = bg.copy(
        alpha = when {
            def.kind == KeyKind.EQUALS -> 0.72f
            active -> 0.60f
            def.kind == KeyKind.OPERATOR -> 0.44f
            def.kind == KeyKind.CLEAR -> 0.42f
            def.kind == KeyKind.DIGIT -> 0.26f
            def.kind == KeyKind.MODIFIER -> 0.26f
            def.kind == KeyKind.DELETE -> 0.26f
            def.kind == KeyKind.NAV -> 0.22f
            else -> 0.24f
        },
    )
    val glassRimBase = when {
        def.kind == KeyKind.EQUALS -> Color.White.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = if (glassDark) 0.18f else 0.55f)
    }
    val keyBorder by animateColorAsState(
        targetValue = when {
            glass && pressed -> baseFg.copy(alpha = 0.34f)
            glass -> glassRimBase
            pressed -> baseFg.copy(alpha = 0.22f)
            else -> borderBase
        },
        animationSpec = tween(80),
        label = "keyBorder",
    )
    val scale = 1f - 0.05f * pressProgress
    val onKeyState by rememberUpdatedState(onKey)
    val onRepeatKeyState by rememberUpdatedState(onRepeatKey)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (glass) {
                    // Cast the key's depth from the transform layer we already own
                    // instead of paying for a second shadow layer on every key.
                    shadowElevation = 2.dp.toPx()
                    shape = keyShape
                }
            }
            .liquidGlassTile(
                shape = keyShape,
                tint = glassKeyTint,
                rimColor = keyBorder,
                // Depth comes from the layer above.
                shadowRadius = null,
            )
            .clip(keyShape)
            .background(if (glass) Color.Transparent else bg)
            .then(
                if (glass) {
                    Modifier
                } else {
                    Modifier.border(BorderStroke(1.dp, keyBorder), keyShape)
                },
            )
            .drawWithContent {
                if (pressProgress > 0.01f && pressPoint.isSpecified) {
                    val radius = size.maxDimension * (0.35f + 0.9f * pressProgress)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                baseFg.copy(alpha = 0.20f * pressProgress),
                                baseFg.copy(alpha = 0.05f * pressProgress),
                                Color.Transparent,
                            ),
                            center = pressPoint,
                            radius = radius,
                        ),
                        radius = radius,
                        center = pressPoint,
                    )
                }
                drawContent()
            }
            .pointerInput(def.id) {
                detectTapGestures(
                    onPress = { offset ->
                        pressPoint = offset
                        pressed = true
                        onKeyState(def)
                        var repeatJob: Job? = null
                        if (def.repeat) {
                            repeatJob = scope.launch {
                                delay(400)
                                while (true) {
                                    onRepeatKeyState(def)
                                    delay(55)
                                }
                            }
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            repeatJob?.cancel()
                            pressed = false
                        }
                    },
                )
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = when (def.id) {
                    "left" -> "Move cursor left"
                    "right" -> "Move cursor right"
                    "del", "b_del" -> "Delete"
                    else -> label
                }
                if (def.kind == KeyKind.MODIFIER) {
                    stateDescription = if (active) "On" else "Off"
                }
                onClick {
                    onKeyState(def)
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (hints && layer == KeyLayer.NONE) {
            if (def.shiftLabel != null) {
                Text(
                    def.shiftLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 7.dp, top = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            if (def.alphaLabel != null) {
                Text(
                    def.alphaLabel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 7.dp, top = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
        }

        val topPad = if (hints && layer == KeyLayer.NONE && (def.shiftLabel != null || def.alphaLabel != null)) {
            7.dp
        } else {
            0.dp
        }
        if (showIcon) {
            val icon = when (def.id) {
                "left" -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
                "right" -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
                else -> Icons.AutoMirrored.Rounded.Backspace
            }
            Icon(
                icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(if (def.label == "DEL") 22.dp else 26.dp),
            )
        } else {
            val size = when {
                def.kind == KeyKind.DIGIT -> if (big) 32.sp else 20.sp
                def.kind == KeyKind.OPERATOR -> if (big) 32.sp else 22.sp
                def.kind == KeyKind.EQUALS -> if (big) 34.sp else 24.sp
                def.kind == KeyKind.MODIFIER -> if (big) 11.sp else 9.sp
                label.length <= 1 -> if (big) 26.sp else 17.sp
                label.length == 2 -> if (big) 22.sp else 14.5.sp
                label.length == 3 -> if (big) 18.sp else 12.sp
                else -> if (big) 15.sp else 10.5.sp
            }
            Text(
                text = label,
                modifier = Modifier.padding(top = topPad),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = size,
                    lineHeight = (size.value * 1.1f).sp,
                    fontWeight = when (def.kind) {
                        KeyKind.DIGIT -> FontWeight.SemiBold
                        KeyKind.EQUALS -> FontWeight.Bold
                        KeyKind.MODIFIER -> FontWeight.SemiBold
                        else -> FontWeight.Medium
                    },
                    letterSpacing = (-0.2).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// SHEETS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SheetHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current
    val iconShape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (glass) Color.Transparent else scheme.primaryContainer.copy(alpha = 0.55f),
                    shape = iconShape,
                )
                .liquidGlassTile(
                    shape = iconShape,
                    tint = scheme.primaryContainer.copy(alpha = 0.32f),
                    rimColor = scheme.primary.copy(alpha = 0.30f),
                    shadowRadius = 2.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = scheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SectionLabel(text: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SheetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current
    val searchShape = RoundedCornerShape(18.dp)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassTile(
                shape = searchShape,
                tint = scheme.surfaceContainerHigh.copy(alpha = 0.26f),
                shadowRadius = 2.dp,
                // Keep the field calm so the query stays the loudest thing in it.
                sheenAlpha = if (isSystemInDarkTheme()) 0.06f else 0.14f,
            ),
        singleLine = true,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = scheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.7f),
                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.7f),
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (glass) {
                // Focus still needs a colour cue; the unfocused rim comes from the tile.
                scheme.primary.copy(alpha = 0.70f)
            } else {
                scheme.primary.copy(alpha = 0.55f)
            },
            unfocusedBorderColor = if (glass) {
                Color.Transparent
            } else {
                scheme.outlineVariant.copy(alpha = 0.45f)
            },
            focusedContainerColor = if (glass) Color.Transparent else scheme.surfaceContainerHigh.copy(alpha = 0.55f),
            unfocusedContainerColor = if (glass) Color.Transparent else scheme.surfaceContainerHigh.copy(alpha = 0.55f),
            cursorColor = scheme.primary,
        ),
    )
}

@Composable
private fun SheetEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = scheme.primary.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DigitStepper(
    label: String,
    caption: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current
    val stepperShape = RoundedCornerShape(22.dp)
    Surface(
        shape = stepperShape,
        color = if (glass) Color.Transparent else scheme.surfaceContainerHigh.copy(alpha = 0.65f),
        // A transparent Surface resolves its content colour to Unspecified,
        // which would leave the default-coloured label text unpainted.
        contentColor = scheme.onSurface,
        border = if (glass) {
            null
        } else {
            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.3f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassTile(
                shape = stepperShape,
                tint = scheme.surfaceContainerHigh.copy(alpha = 0.28f),
                shadowRadius = 2.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            ToolzTonalExpressiveIconButton(
                onClick = { onValueChange(value - 1) },
                enabled = value > range.first,
                modifier = Modifier.size(36.dp),
                shape = SmallExpressiveShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = scheme.surfaceContainerHighest,
                    contentColor = scheme.primary,
                ),
            ) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = "Fewer digits",
                    modifier = Modifier.size(17.dp),
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(width = 42.dp, height = 36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.primaryContainer.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        (fadeIn(tween(130)) + scaleIn(initialScale = 0.8f)) togetherWith
                            (fadeOut(tween(90)) + scaleOut(targetScale = 0.8f))
                    },
                    label = "digitValue",
                ) { digits ->
                    Text(
                        digits.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
            ToolzTonalExpressiveIconButton(
                onClick = { onValueChange(value + 1) },
                enabled = value < range.last,
                modifier = Modifier.size(36.dp),
                shape = SmallExpressiveShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = scheme.surfaceContainerHighest,
                    contentColor = scheme.primary,
                ),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "More digits",
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupSheetContent(state: CalculatorState, viewModel: CalculatorViewModel) {
    val scheme = MaterialTheme.colorScheme

    SheetHeader(
        icon = Icons.Rounded.Tune,
        title = "Setup",
        subtitle = "Angle, number format, and exact results",
    )

    SectionLabel("Angle unit", Icons.Rounded.Functions)
    val angles = AngleUnit.values().toList()
    ToolzConnectedButtonGroup(
        selectedIndex = angles.indexOf(state.angle),
        options = angles.map { it.label },
        onOptionSelected = { viewModel.onSetAngle(angles[it]) },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))
    SectionLabel("Number format", Icons.Rounded.Numbers)
    val kinds = FormatKind.values().toList()
    ToolzConnectedButtonGroup(
        selectedIndex = kinds.indexOf(state.fmtKind),
        options = kinds.map { it.label },
        onOptionSelected = { viewModel.onSetFormat(kinds[it], state.fmtDigits) },
        modifier = Modifier.fillMaxWidth(),
    )
    AnimatedVisibility(
        visible = state.fmtKind != FormatKind.NORM,
        enter = fadeIn(tween(140)) + expandVertically(tween(180)),
        exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
    ) {
        Column {
            Spacer(Modifier.height(10.dp))
            DigitStepper(
                label = if (state.fmtKind == FormatKind.FIX) "Decimal places" else "Significant digits − 1",
                caption = "Between 0 and 9 digits",
                value = state.fmtDigits,
                range = 0..9,
                onValueChange = { viewModel.onSetFormat(state.fmtKind, it) },
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel("Exact results", Icons.Rounded.SwapHoriz)
    ToolzConnectedButtonGroup(
        selectedIndex = if (state.showFraction) 0 else 1,
        options = listOf("Fractions", "Decimals"),
        onOptionSelected = { viewModel.onSetShowFraction(it == 0) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Fractions stay exact. S⇔D or the n/d status chip flips the result at any time.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun ColumnScope.CatalogSheetContent(viewModel: CalculatorViewModel) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current
    var query by remember { mutableStateOf("") }
    val needle = query.trim()
    val sections = remember(needle) {
        CalcCatalog.sections.mapNotNull { (title, items) ->
            val matches = if (needle.isBlank()) {
                items
            } else {
                items.filter {
                    it.label.contains(needle, ignoreCase = true) ||
                        it.insert.contains(needle, ignoreCase = true) ||
                        it.hint.contains(needle, ignoreCase = true)
                }
            }
            if (matches.isEmpty()) null else title to matches
        }
    }

    SheetHeader(
        icon = Icons.Rounded.GridView,
        title = "Catalog",
        subtitle = "Insert a function at the cursor",
    )
    SheetSearchField(
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search functions",
    )
    Spacer(Modifier.height(12.dp))

    if (sections.isEmpty()) {
        SheetEmptyState(
            icon = Icons.Rounded.Search,
            title = "No functions found",
            subtitle = "Try another name, like root, log, or sin.",
            modifier = Modifier.padding(vertical = 40.dp),
        )
        return
    }

    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .fadingEdges(top = 4.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        sections.forEach { (title, items) ->
            SectionLabel(title)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    ExpressiveCard(
                        onClick = { viewModel.onInsertText(item.insert) },
                        shape = MediumExpressiveShape,
                        containerColor = scheme.surfaceContainerHigh,
                        border = if (glass) {
                            null
                        } else {
                            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.2f))
                        },
                        compactGlass = true,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (item.hint.isNotEmpty()) {
                                Text(
                                    item.hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ConstantsSheetContent(state: CalculatorState, viewModel: CalculatorViewModel) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current
    var query by remember { mutableStateOf("") }
    val needle = query.trim()
    val filtered = CalcConstants.all.filter {
        needle.isBlank() ||
            it.name.contains(needle, ignoreCase = true) ||
            it.symbol.contains(needle, ignoreCase = true) ||
            it.unit.contains(needle, ignoreCase = true) ||
            it.value.replace("ᴇ", "E").contains(needle, ignoreCase = true)
    }
    val prev = state.activeField.let { field ->
        if (field.cursor > 0 && field.cursor <= field.text.length) field.text[field.cursor - 1] else null
    }
    val needsTimes = prev != null && (prev in '0'..'9' || prev == '.' || prev == ')')

    SheetHeader(
        icon = Icons.Rounded.Science,
        title = stringResource(R.string.st_CalculatorScreen_1b2c),
        subtitle = "Physical and mathematical constants",
    )
    SheetSearchField(
        query = query,
        onQueryChange = { query = it },
        placeholder = "Search constants",
    )
    Spacer(Modifier.height(12.dp))

    if (filtered.isEmpty()) {
        SheetEmptyState(
            icon = Icons.Rounded.Search,
            title = "No constants found",
            subtitle = "Try a name or symbol, like Planck or π.",
            modifier = Modifier.padding(vertical = 36.dp),
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .weight(1f, fill = false)
            .fadingEdges(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(filtered, key = { it.symbol + it.name }) { constant ->
            ExpressiveCard(
                onClick = {
                    viewModel.onInsertText((if (needsTimes) "×" else "") + constant.value)
                },
                shape = MediumExpressiveShape,
                containerColor = scheme.surfaceContainerHigh,
                border = if (glass) {
                    null
                } else {
                    BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.2f))
                },
                compactGlass = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primaryContainer.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            constant.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            constant.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            constant.value.replace("ᴇ", "E") +
                                if (constant.unit.isNotEmpty()) "  ${constant.unit}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = scheme.primary.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VariablesSheetContent(
    state: CalculatorState,
    store: Boolean,
    viewModel: CalculatorViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current
    var storeMode by remember(store) { mutableStateOf(store) }

    SheetHeader(
        icon = Icons.Rounded.Memory,
        title = "Variables",
        subtitle = if (storeMode) "Store the current value" else "Insert a stored value",
    )
    ToolzConnectedButtonGroup(
        selectedIndex = if (storeMode) 0 else 1,
        options = listOf("Store", "Recall"),
        // The group fires its own tick haptic.
        onOptionSelected = { storeMode = it == 0 },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(14.dp))

    listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M").chunked(3).forEach { rowNames ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowNames.forEach { name ->
                val value = state.variables[name] ?: "0"
                val filled = value != "0"
                ExpressiveCard(
                    onClick = {
                        if (storeMode) viewModel.onStoreVar(name) else viewModel.onRecallVar(name)
                    },
                    modifier = Modifier.weight(1f),
                    shape = MediumExpressiveShape,
                    containerColor = if (filled) {
                        scheme.primaryContainer.copy(alpha = 0.38f)
                    } else {
                        scheme.surfaceContainerHigh
                    },
                    border = when {
                        // In glass mode the fill is only a light tint, so a filled
                        // variable leans on the primary rim and ink to stay legible.
                        filled -> BorderStroke(1.dp, scheme.primary.copy(alpha = 0.42f))
                        glass -> null
                        else -> BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.2f))
                    },
                    compactGlass = true,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (filled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            repeat(3 - rowNames.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HISTORY
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CalculatorHistoryContent(
    state: CalculatorState,
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier,
    onPicked: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val vibrationManager = LocalVibrationManager.current
    val glass = LocalLiquidGlassEnabled.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        SheetHeader(
            icon = Icons.Rounded.History,
            title = "History",
            subtitle = if (state.history.isEmpty()) {
                "Your calculations will show up here"
            } else {
                "${state.history.size} calculation${if (state.history.size == 1) "" else "s"}"
            },
            trailing = if (state.history.isEmpty()) {
                null
            } else {
                {
                    Surface(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            confirmClear = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .liquidGlassTile(
                                shape = SmallExpressiveShape,
                                tint = scheme.errorContainer.copy(alpha = 0.38f),
                                rimColor = scheme.error.copy(alpha = 0.40f),
                                shadowRadius = 2.dp,
                            ),
                        shape = SmallExpressiveShape,
                        // Destructive actions keep their colour even on glass.
                        color = if (glass) Color.Transparent else scheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = scheme.error,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.st_CalculatorScreen_5f6e),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
        )

        if (state.history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                SheetEmptyState(
                    icon = Icons.Rounded.Calculate,
                    title = stringResource(R.string.st_CalculatorScreen_2b8a),
                    subtitle = stringResource(R.string.st_CalculatorScreen_4d9c),
                    modifier = Modifier.padding(bottom = 48.dp),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .fadingEdges(top = 4.dp, bottom = 24.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(
                    items = state.history,
                    key = { index, item -> "$index-${item.first}" },
                ) { _, (expression, result) ->
                    ExpressiveCard(
                        shape = MediumExpressiveShape,
                        containerColor = scheme.surfaceContainerHigh,
                        border = if (glass) {
                            null
                        } else {
                            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.2f))
                        },
                        compactGlass = true,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = expression,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "= $result",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 10.dp),
                                color = scheme.outlineVariant.copy(alpha = 0.18f),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        viewModel.onUseHistoryExpression(expression)
                                        onPicked()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("Edit")
                                }
                                TextButton(
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        viewModel.onUseHistoryResult(result)
                                        onPicked()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("Use result")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = {
                Text(
                    if (state.history.size == 1) {
                        "1 calculation will be permanently removed from this device."
                    } else {
                        "${state.history.size} calculations will be permanently removed from this device."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.clearHistory()
                        confirmClear = false
                    },
                ) {
                    Text(
                        "Clear",
                        color = scheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = scheme.surfaceContainerHigh,
        )
    }
}
