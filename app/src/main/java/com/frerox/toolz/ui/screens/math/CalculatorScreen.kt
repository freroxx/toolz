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
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    width = 56.dp,
                    height = 4.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            },
        ) {
            CalculatorHistoryContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
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
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 56.dp,
                height = 4.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
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
        targetValue = if (state.scientific) 2.4f else 1.7f,
        animationSpec = tween(220),
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
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
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
) {
    val shape = CircleShape
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    } else {
        null
    }
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = container,
            contentColor = content,
            border = border,
            content = label,
        )
    } else {
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            border = border,
            content = label,
        )
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
            .padding(horizontal = 14.dp, vertical = 3.dp),
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
                onClick = {
                    vibrationManager?.vibrateTick()
                    viewModel.onCycleAngle()
                },
            )
            StatusChip(
                text = format,
                onClick = {
                    vibrationManager?.vibrateTick()
                    viewModel.onSheet(CalcSheet.SETUP)
                },
            )
            StatusChip(
                text = if (state.showFraction) "n/d" else "dec",
                onClick = {
                    vibrationManager?.vibrateTick()
                    viewModel.onSetShowFraction(!state.showFraction)
                },
                container = if (state.showFraction) scheme.secondaryContainer else scheme.surfaceContainer,
                content = if (state.showFraction) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                selected = state.showFraction,
            )
            if (state.layer == KeyLayer.SHIFT) {
                StatusChip(
                    text = "SHIFT",
                    container = scheme.tertiary,
                    content = scheme.onTertiary,
                )
            }
            if (state.layer == KeyLayer.ALPHA) {
                StatusChip(
                    text = "ALPHA",
                    container = scheme.secondary,
                    content = scheme.onSecondary,
                )
            }
            if (state.hyp) {
                StatusChip(
                    text = "HYP",
                    container = scheme.primary,
                    content = scheme.onPrimary,
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        StatusChip(
            text = if (state.scientific) "Scientific" else "Basic",
            onClick = {
                vibrationManager?.vibrateTick()
                viewModel.onToggleScientific()
            },
            container = if (state.scientific) scheme.primaryContainer else scheme.surfaceContainerHigh,
            content = if (state.scientific) scheme.onPrimaryContainer else scheme.onSurface,
            selected = state.scientific,
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
        shape = ExtraLargeExpressiveShape,
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.18f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
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
                    modifier = Modifier.padding(top = 8.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.28f),
                )

                ResultArea(
                    state = state,
                    onCopy = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.onCopyResult()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 66.dp)
                        .padding(top = 8.dp),
                )
            }

            ToastPill(
                text = state.toast,
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
    var textWidth by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .horizontalFadingEdges(left = 18.dp, right = 18.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        val viewport = with(density) { maxWidth.toPx() }
        val overflow = (textWidth - viewport).coerceAtLeast(0f)
        val cursorFraction = if (text.isEmpty()) 1f else {
            (cursor.toFloat() / text.length).coerceIn(0f, 1f)
        }
        val targetShift = if (textWidth <= 0f) {
            0f
        } else if (overflow > 0f) {
            -overflow * cursorFraction
        } else {
            viewport - textWidth
        }
        val shift by animateFloatAsState(
            targetValue = targetShift,
            animationSpec = tween(140),
            label = "expressionShift",
        )
        val fontSize = when {
            text.length > 48 -> 23.sp
            text.length > 28 -> 27.sp
            else -> 33.sp
        }

        CaretText(
            text = text,
            cursor = cursor,
            focused = true,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.2f,
                fontWeight = FontWeight.Medium,
            ),
            color = scheme.onSurface,
            modifier = Modifier
                .offset { IntOffset(shift.roundToInt(), 0) }
                .wrapContentWidth(unbounded = true),
            onTapOffset = onCursor,
            onTextWidth = { textWidth = it },
        )
    }
}

@Composable
private fun CaretText(
    text: String,
    cursor: Int,
    focused: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onTapOffset: (Int) -> Unit,
    onTextWidth: (Float) -> Unit,
) {
    val cur = cursor.coerceIn(0, text.length)
    val caretColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append(text.substring(0, cur))
        if (focused) {
            withStyle(SpanStyle(color = caretColor, fontWeight = FontWeight.Medium)) {
                append("▏")
            }
        }
        append(text.substring(cur))
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.pointerInput(text, cur, focused) {
            detectTapGestures { pos ->
                val l = layout
                if (l == null || text.isEmpty()) {
                    onTapOffset(0)
                } else {
                    val raw = l.getOffsetForPosition(pos)
                    val mapped = if (focused && raw > cur) raw - 1 else raw
                    onTapOffset(mapped.coerceIn(0, text.length))
                }
            }
        },
        onTextLayout = {
            layout = it
            onTextWidth(it.size.width.toFloat())
        },
    )
}

@Composable
private fun ResultArea(
    state: CalculatorState,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val out = state.outputs.firstOrNull()
    val resultSize by animateFloatAsState(
        targetValue = if (state.evaluated) 46f else 30f,
        animationSpec = tween(180),
        label = "resultSize",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = out != null && state.error == null) { onCopy() },
        contentAlignment = Alignment.CenterEnd,
    ) {
        when {
            state.error != null -> {
                Surface(
                    shape = SmallExpressiveShape,
                    color = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                ) {
                    Text(
                        text = state.error,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            out != null -> {
                Crossfade(
                    targetState = out,
                    animationSpec = tween(160),
                    label = "resultValue",
                ) { line ->
                    ResultText(
                        line = line,
                        sizeSp = resultSize,
                        color = if (state.evaluated) {
                            scheme.primary
                        } else {
                            scheme.onSurfaceVariant.copy(alpha = 0.72f)
                        },
                        weight = if (state.evaluated) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }

            state.note != null -> {
                Text(
                    text = state.note.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(line.num, style = style.copy(fontSize = style.fontSize * 0.82f), color = color)
                    HorizontalDivider(thickness = 2.dp, color = color.copy(alpha = 0.7f))
                    Text(line.den, style = style.copy(fontSize = style.fontSize * 0.82f), color = color)
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
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.94f),
        exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.96f),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(
                text ?: "",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
    AnimatedContent(
        targetState = state.scientific,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(170)) + expandVertically(tween(170), expandFrom = Alignment.Bottom))
                .togetherWith(fadeOut(tween(100)) + shrinkVertically(tween(100), shrinkTowards = Alignment.Top))
        },
        contentAlignment = Alignment.BottomCenter,
        label = "keypadDensity",
    ) { scientific ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (scientific) {
                CalcKeys.fullFunctions.forEach { keys ->
                    KeyRow(keys, weight = 0.84f, state = state, hints = true, big = false, onKey = onKey)
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
            KeyKind.NAV -> scheme.surfaceContainerHighest to scheme.primary
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
    val showIcon = layer == KeyLayer.NONE && (def.id == "left" || def.id == "right" || def.label == "DEL")
    val shape = when (def.kind) {
        KeyKind.DIGIT -> RoundedCornerShape(20.dp)
        KeyKind.OPERATOR -> RoundedCornerShape(24.dp)
        KeyKind.EQUALS -> RoundedCornerShape(28.dp)
        KeyKind.FUNCTION -> RoundedCornerShape(18.dp)
        KeyKind.MODIFIER -> RoundedCornerShape(18.dp)
        KeyKind.NAV -> RoundedCornerShape(18.dp)
        KeyKind.CLEAR, KeyKind.DELETE -> RoundedCornerShape(20.dp)
    }
    val borderColor = when (def.kind) {
        KeyKind.DIGIT, KeyKind.FUNCTION -> scheme.outlineVariant.copy(alpha = 0.22f)
        KeyKind.DELETE, KeyKind.NAV -> scheme.outlineVariant.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (pressed) lerp(baseBg, baseFg, 0.18f) else baseBg,
        animationSpec = tween(70),
        label = "keyBackground",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "keyScale",
    )
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
            .pointerInput(def, onKeyState) {
                detectTapGestures(
                    onPress = {
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
                        .padding(start = 6.dp, top = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = scheme.tertiary,
                    maxLines = 1,
                )
            }
            if (def.alphaLabel != null) {
                Text(
                    def.alphaLabel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 6.dp, top = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = scheme.secondary,
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
                contentDescription = def.label,
                tint = baseFg,
                modifier = Modifier.size(if (def.label == "DEL") 22.dp else 26.dp),
            )
        } else {
            val size = when {
                def.kind == KeyKind.DIGIT -> if (big) 30.sp else 21.sp
                def.kind == KeyKind.OPERATOR -> if (big) 30.sp else 23.sp
                def.kind == KeyKind.EQUALS -> if (big) 32.sp else 25.sp
                def.kind == KeyKind.MODIFIER -> 10.sp
                label.length <= 2 -> if (big) 22.sp else 16.sp
                label.length == 3 -> if (big) 18.sp else 13.sp
                else -> if (big) 15.sp else 11.sp
            }
            Text(
                text = label,
                modifier = Modifier.padding(top = topPad),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = size,
                    fontWeight = when (def.kind) {
                        KeyKind.DIGIT -> FontWeight.SemiBold
                        KeyKind.EQUALS -> FontWeight.Bold
                        else -> FontWeight.Medium
                    },
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
