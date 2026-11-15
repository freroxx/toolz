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
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import androidx.compose.material3.FilledTonalIconButton
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
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ExtraLargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
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
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    width = 48.dp,
                    height = 4.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 48.dp,
                height = 4.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
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

    Column(modifier = modifier.fillMaxSize()) {
        StatusStrip(state, viewModel)
        CalculatorDisplay(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 132.dp)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
        CalculatorKeypad(
            state = state,
            onKey = onKey,
            modifier = Modifier
                .weight(padWeight)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            interactionSource = interaction,
            shape = CircleShape,
            color = bg,
            contentColor = fg,
            border = border,
            content = label,
        )
    } else {
        Surface(
            shape = CircleShape,
            color = bg,
            contentColor = fg,
            border = border,
            content = label,
        )
    }
}

@Composable
private fun StatusBadge(text: String, container: Color, content: Color) {
    Surface(
        shape = CircleShape,
        color = container,
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
            .animateContentSize(),
        interactionSource = interaction,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = BorderStroke(
            1.dp,
            if (scientific) fg.copy(alpha = 0.2f) else scheme.outlineVariant.copy(alpha = 0.18f),
        ),
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
            .padding(horizontal = 14.dp, vertical = 4.dp),
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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.22f)),
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = 0.10f),
                                scheme.primary.copy(alpha = 0.035f),
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
                                scheme.primary.copy(alpha = 0.18f),
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

    val caretTransition = rememberInfiniteTransition(label = "caret")
    val blink by caretTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretBlink",
    )
    val caretAlpha = if (performanceMode) 1f else blink

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .horizontalFadingEdges(left = 16.dp, right = 18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        val viewport = with(density) { maxWidth.toPx() }
        val rightInset = with(density) { 18.dp.toPx() }
        val textWidth = layout?.size?.width?.toFloat() ?: 0f
        val caretX = layout?.getHorizontalPosition(cur, usePrimaryDirection = true) ?: 0f
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
                        .background(
                            color = scheme.primary.copy(alpha = 0.9f * caretAlpha),
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
            CopyAction(copied = copied, onClick = onCopy)
        }
        Spacer(Modifier.width(if (hasResult) 10.dp else 0.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
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

    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.18f)),
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
        modifier = modifier.offset { IntOffset(offsetX.roundToInt(), 0) },
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
    align: Alignment.Horizontal = Alignment.End,
) {
    val len = if (line.num != null) maxOf(line.num.length, line.den?.length ?: 0) + 2 else line.text.length
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
    if (line.num != null && line.den != null) {
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
                        style = style.copy(fontSize = style.fontSize * 0.8f),
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
                        style = style.copy(fontSize = style.fontSize * 0.8f),
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalFadingEdges(left = 12.dp, right = 12.dp)
                .horizontalScroll(rememberScrollState(), reverseScrolling = true),
            contentAlignment = when (align) {
                Alignment.Start -> Alignment.CenterStart
                Alignment.CenterHorizontally -> Alignment.Center
                else -> Alignment.CenterEnd
            },
        ) {
            Text(
                line.text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
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
                .clip(RoundedCornerShape(30.dp))
                .background(layerAccent)
                .border(BorderStroke(1.dp, layerBorder), RoundedCornerShape(30.dp))
                .padding(2.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (scientific) {
                    CalcKeys.fullFunctions.forEach { keys ->
                        KeyRow(keys, weight = 0.82f, state = state, hints = true, big = false, onKey = onKey)
                    }
                    CalcKeys.numbers.forEach { keys ->
                        KeyRow(keys, weight = 1f, state = state, hints = false, big = false, onKey = onKey)
                    }
                } else {
                    CalcKeys.basic.forEach { keys ->
                        KeyRow(keys, weight = 1f, state = state, hints = false, big = true, onKey = onKey)
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
    val shape = when (def.kind) {
        KeyKind.DIGIT -> RoundedCornerShape(22.dp)
        KeyKind.OPERATOR -> RoundedCornerShape(24.dp)
        KeyKind.EQUALS -> RoundedCornerShape(30.dp)
        KeyKind.FUNCTION -> RoundedCornerShape(18.dp)
        KeyKind.MODIFIER -> RoundedCornerShape(18.dp)
        KeyKind.NAV -> RoundedCornerShape(18.dp)
        KeyKind.CLEAR, KeyKind.DELETE -> RoundedCornerShape(20.dp)
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
    val borderColor by animateColorAsState(
        targetValue = if (pressed) baseFg.copy(alpha = 0.22f) else borderBase,
        animationSpec = tween(80),
        label = "keyBorder",
    )
    val scale = 1f - 0.05f * pressProgress
    val onKeyState by rememberUpdatedState(onKey)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), shape)
            .drawWithContent {
                drawContent()
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
            }
            .pointerInput(def, onKeyState) {
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
                                    onKeyState(def)
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
            .semantics {
                role = Role.Button
                contentDescription = label
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
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.tertiary.copy(alpha = 0.95f),
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
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.secondary.copy(alpha = 0.95f),
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
                "left" -> Icons.Rounded.KeyboardArrowLeft
                "right" -> Icons.Rounded.KeyboardArrowRight
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
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )
}

@Composable
private fun SetupSheetContent(state: CalculatorState, viewModel: CalculatorViewModel) {
    SheetTitle("Setup")

    SectionLabel("Angle unit")
    val angles = AngleUnit.values().toList()
    ToolzConnectedButtonGroup(
        selectedIndex = angles.indexOf(state.angle),
        options = angles.map { it.label },
        onOptionSelected = { viewModel.onSetAngle(angles[it]) },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(14.dp))
    SectionLabel("Number format")
    val kinds = FormatKind.values().toList()
    ToolzConnectedButtonGroup(
        selectedIndex = kinds.indexOf(state.fmtKind),
        options = kinds.map { it.label },
        onOptionSelected = { viewModel.onSetFormat(kinds[it], state.fmtDigits) },
        modifier = Modifier.fillMaxWidth(),
    )
    AnimatedVisibility(visible = state.fmtKind != FormatKind.NORM) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.fmtKind == FormatKind.FIX) "Decimal places" else "Significant digits − 1",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.onSetFormat(state.fmtKind, state.fmtDigits - 1) }) {
                Icon(Icons.Rounded.Remove, contentDescription = "Fewer digits")
            }
            Text(
                state.fmtDigits.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { viewModel.onSetFormat(state.fmtKind, state.fmtDigits + 1) }) {
                Icon(Icons.Rounded.Add, contentDescription = "More digits")
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionLabel("Exact results")
    ToolzConnectedButtonGroup(
        selectedIndex = if (state.showFraction) 0 else 1,
        options = listOf("Fractions", "Decimals"),
        onOptionSelected = { viewModel.onSetShowFraction(it == 0) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Fractions stay exact. S⇔D or the n/d status chip flips the result at any time.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun CatalogSheetContent(viewModel: CalculatorViewModel) {
    val vibrationManager = LocalVibrationManager.current
    val scheme = MaterialTheme.colorScheme
    SheetTitle("Catalog")
    Column(
        modifier = Modifier
            .heightIn(max = 560.dp)
            .fadingEdges(top = 4.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        CalcCatalog.sections.forEach { (title, items) ->
            SectionLabel(title)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    Surface(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.onInsertText(item.insert)
                        },
                        shape = MediumExpressiveShape,
                        color = scheme.surfaceContainerHigh,
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
private fun ConstantsSheetContent(state: CalculatorState, viewModel: CalculatorViewModel) {
    val scheme = MaterialTheme.colorScheme
    val vibrationManager = LocalVibrationManager.current
    var query by remember { mutableStateOf("") }
    val filtered = CalcConstants.all.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.symbol.contains(query, ignoreCase = true)
    }
    val prev = state.activeField.let { field ->
        if (field.cursor > 0 && field.cursor <= field.text.length) field.text[field.cursor - 1] else null
    }
    val needsTimes = prev != null && (prev in '0'..'9' || prev == '.' || prev == ')')

    SheetTitle(stringResource(R.string.st_CalculatorScreen_1b2c))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search constants") },
        shape = RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(10.dp))
    LazyColumn(
        modifier = Modifier
            .heightIn(max = 480.dp)
            .fadingEdges(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(filtered, key = { it.symbol + it.name }) { constant ->
            Surface(
                onClick = {
                    vibrationManager?.vibrateClick()
                    viewModel.onInsertText((if (needsTimes) "×" else "") + constant.value)
                },
                shape = MediumExpressiveShape,
                color = scheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        constant.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        modifier = Modifier.width(56.dp),
                    )
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
                        )
                    }
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
    val vibrationManager = LocalVibrationManager.current
    var storeMode by remember(store) { mutableStateOf(store) }

    SheetTitle("Variables")
    ToolzConnectedButtonGroup(
        selectedIndex = if (storeMode) 0 else 1,
        options = listOf("Store", "Recall"),
        onOptionSelected = {
            vibrationManager?.vibrateTick()
            storeMode = it == 0
        },
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
                Surface(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        if (storeMode) viewModel.onStoreVar(name) else viewModel.onRecallVar(name)
                    },
                    modifier = Modifier.weight(1f),
                    shape = MediumExpressiveShape,
                    color = scheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                        )
                        Text(
                            state.variables[name] ?: "0",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
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

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (state.history.isNotEmpty()) {
                    Text(
                        text = "${state.history.size} calculations",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = state.history.isNotEmpty(),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(100)),
            ) {
                ToolzExpressiveIconButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        viewModel.clearHistory()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = scheme.error,
                    ),
                    shape = SmallExpressiveShape,
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.st_CalculatorScreen_5f6e),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (state.history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.Calculate,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = scheme.primary.copy(alpha = 0.35f),
                    )
                    Text(
                        stringResource(R.string.st_CalculatorScreen_2b8a),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.st_CalculatorScreen_4d9c),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
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
                    Surface(
                        shape = MediumExpressiveShape,
                        color = scheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
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
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "= $result",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        viewModel.onUseHistoryExpression(expression)
                                        onPicked()
                                    },
                                ) { Text("Edit") }
                                TextButton(
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        viewModel.onUseHistoryResult(result)
                                        onPicked()
                                    },
                                ) { Text("Use result") }
                            }
                        }
                    }
                }
            }
        }
    }
}
