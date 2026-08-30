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

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground

// ═══════════════════════════════════════════════════════════════════════════════
// BUTTON TYPE — drives M3 surface color role selection
// ═══════════════════════════════════════════════════════════════════════════════

enum class CalcKeyType {
    DIGIT,      // surfaceContainerHigh + onSurface
    OPERATOR,   // secondaryContainer + onSecondaryContainer
    EQUALS,     // primary + onPrimary
    CLEAR,      // errorContainer + error
    FUNCTION,   // surfaceContainerHighest + onSurfaceVariant
    SPECIAL,    // tertiaryContainer + onTertiary
}

// ═══════════════════════════════════════════════════════════════════════════════
// ROOT SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val vibrationManager = LocalVibrationManager.current
    var showHistorySheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground(),
    ) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    title = stringResource(R.string.st_CalculatorScreen_f1a2),
                    subtitle = if (state.isScientific) stringResource(R.string.st_CalculatorScreen_7e8f) + " mode" else stringResource(R.string.st_CalculatorScreen_5d6e) + " mode",
                    navigationIcon = {
                        ToolzExpressiveIconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                onBack()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            shape = MediumExpressiveShape,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_CalculatorScreen_3d5b))
                        }
                    },
                    actions = {
                        ToolzExpressiveIconButton(
                            onClick = {
                                vibrationManager?.vibrateTick()
                                showHistorySheet = true
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            shape = MediumExpressiveShape,
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.st_CalculatorScreen_9e2c))
                        }
                        Spacer(Modifier.width(4.dp))
                    },
                    modifier = Modifier.statusBarsPadding(),
                )
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            ExpressiveSupportingPaneScaffold(
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                mainPane = {
                    CalculatorMainContent(state = state, viewModel = viewModel)
                },
                supportingPane = {
                    CalculatorHistoryContent(
                        state = state,
                        viewModel = viewModel,
                    )
                },
            )
        }
    }

    // History bottom sheet (compact screens)
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
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
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN CONTENT — display + mode toggle + keypad
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalculatorMainContent(state: CalculatorState, viewModel: CalculatorViewModel) {
    val vibrationManager = LocalVibrationManager.current

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Display Panel ─────────────────────────────────────────────────────
        CalculatorDisplay(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.25f),
            onCopyResult = {
                vibrationManager?.vibrateLongClick()
                viewModel.onCopyResult()
            },
        )

        // ── Standard / Scientific mode toggle ─────────────────────────────────
        ToolzConnectedButtonGroup(
            selectedIndex = if (state.isScientific) 1 else 0,
            options = listOf(stringResource(R.string.st_CalculatorScreen_5d6e), stringResource(R.string.st_CalculatorScreen_7e8f)),
            onOptionSelected = { idx ->
                val wantScientific = idx == 1
                if (wantScientific != state.isScientific) {
                    vibrationManager?.vibrateTick()
                    viewModel.onToggleMode()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── Keypad — cross-fades between Standard and Scientific ──────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (state.isScientific) 4f else 3.2f),
        ) {
            AnimatedContent(
                targetState = state.isScientific,
                transitionSpec = {
                    val enter = fadeIn(tween(380)) + scaleIn(
                        initialScale = 0.93f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                    val exit = fadeOut(tween(280)) + scaleOut(targetScale = 0.96f)
                    enter togetherWith exit
                },
                label = "keypad_mode_switch",
            ) { isScientific ->
                if (isScientific) {
                    ScientificKeypad(viewModel = viewModel, state = state)
                } else {
                    StandardKeypad(viewModel = viewModel)
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DISPLAY PANEL
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalculatorDisplay(
    state: CalculatorState,
    modifier: Modifier = Modifier,
    onCopyResult: () -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .bouncyClick { onCopyResult() },
        shape = ExtraLargeExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {

            // ── Formula (previous expression) ─────────────────────────────────
            val formulaScrollState = rememberScrollState()
            LaunchedEffect(state.formula) {
                formulaScrollState.animateScrollTo(formulaScrollState.maxValue)
            }
            AnimatedContent(
                targetState = state.formula,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "formula_text",
                modifier = Modifier.fillMaxWidth(),
            ) { formula ->
                val formulaFontSp = when {
                    formula.length > 35 -> 11.sp
                    formula.length > 25 -> 13.sp
                    formula.length > 18 -> 14.sp
                    else                -> 16.sp
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(formulaScrollState),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = formula.ifEmpty { " " },
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = formulaFontSp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Main display number — spring-physics slide on change with dynamic zoom-out ───────────
            val displayLength = state.display.length
            val displayFontSp = when {
                displayLength > 36 -> 18.sp
                displayLength > 28 -> 22.sp
                displayLength > 22 -> 28.sp
                displayLength > 17 -> 34.sp
                displayLength > 13 -> 44.sp
                displayLength > 10 -> 54.sp
                displayLength > 7  -> 64.sp
                else               -> 76.sp
            }
            val letterSpacingSp = when {
                displayLength > 22 -> 0.sp
                displayLength > 13 -> (-1).sp
                else               -> (-2).sp
            }
            val displayScrollState = rememberScrollState()
            LaunchedEffect(state.display) {
                displayScrollState.animateScrollTo(displayScrollState.maxValue)
            }

            AnimatedContent(
                targetState = state.display,
                transitionSpec = {
                    val enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { it / 4 } + fadeIn(tween(180))
                    val exit = slideOutVertically(tween(140)) { -it / 4 } + fadeOut(tween(140))
                    enter togetherWith exit
                },
                label = "display_value",
                modifier = Modifier.fillMaxWidth(),
            ) { display ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(displayScrollState),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = display,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = displayFontSp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = letterSpacingSp,
                        ),
                        color = when {
                            state.error != null -> MaterialTheme.colorScheme.error
                            else               -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }

            // ── Live "preview" result ─────────────────────────────────────────
            AnimatedVisibility(
                visible = state.liveResult != null &&
                        state.liveResult != state.display &&
                        state.error == null,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(140)) + fadeOut(tween(100)),
            ) {
                Text(
                    text = "= ${state.liveResult ?: ""}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            // ── Error toast ───────────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.error != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier.padding(top = 8.dp),
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            // ── Tap-to-copy hint ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.st_CalculatorScreen_1a2b),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STANDARD KEYPAD  (5 rows × 4 columns)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StandardKeypad(viewModel: CalculatorViewModel) {
    val vibrationManager = LocalVibrationManager.current
    val hapticEnabled = LocalHapticEnabled.current

    // Row-by-row definition: label → CalcKeyType
    val rows: List<List<Pair<String, CalcKeyType>>> = listOf(
        listOf("C" to CalcKeyType.CLEAR, "÷" to CalcKeyType.OPERATOR, "×" to CalcKeyType.OPERATOR, "DEL" to CalcKeyType.FUNCTION),
        listOf("7" to CalcKeyType.DIGIT,  "8" to CalcKeyType.DIGIT,   "9" to CalcKeyType.DIGIT,   "-" to CalcKeyType.OPERATOR),
        listOf("4" to CalcKeyType.DIGIT,  "5" to CalcKeyType.DIGIT,   "6" to CalcKeyType.DIGIT,   "+" to CalcKeyType.OPERATOR),
        listOf("1" to CalcKeyType.DIGIT,  "2" to CalcKeyType.DIGIT,   "3" to CalcKeyType.DIGIT,   "=" to CalcKeyType.EQUALS),
        listOf("0" to CalcKeyType.DIGIT,  "00" to CalcKeyType.DIGIT,  "." to CalcKeyType.DIGIT,   "%" to CalcKeyType.OPERATOR),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { (label, type) ->
                    CalcKey(
                        label = label,
                        keyType = type,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 5.dp),
                        onClick = {
                            if (hapticEnabled) {
                                when (type) {
                                    CalcKeyType.EQUALS, CalcKeyType.CLEAR -> vibrationManager?.vibrateLongClick()
                                    else -> vibrationManager?.vibrateClick()
                                }
                            }
                            dispatchCalcAction(label, viewModel)
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCIENTIFIC KEYPAD  (function strip + 5 rows × 4 columns)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ScientificKeypad(viewModel: CalculatorViewModel, state: CalculatorState) {
    val vibrationManager = LocalVibrationManager.current
    val hapticEnabled = LocalHapticEnabled.current
    var showConstants by remember { mutableStateOf(false) }

    // Scientific function strip — 3 rows × 5 cols
    val functionRows = listOf(
        listOf("sin", "cos", "tan", "log", "ln"),
        listOf("√", "xⁿ", "π", "e", "("),
        listOf(")", "DEG/RAD", "inv", "abs", "CONST"),
    )

    // Main number grid
    val mainRows: List<List<Pair<String, CalcKeyType>>> = listOf(
        listOf("AC" to CalcKeyType.CLEAR, "DEL" to CalcKeyType.FUNCTION, "%" to CalcKeyType.OPERATOR, "÷" to CalcKeyType.OPERATOR),
        listOf("7" to CalcKeyType.DIGIT,  "8" to CalcKeyType.DIGIT,      "9" to CalcKeyType.DIGIT,   "×" to CalcKeyType.OPERATOR),
        listOf("4" to CalcKeyType.DIGIT,  "5" to CalcKeyType.DIGIT,      "6" to CalcKeyType.DIGIT,   "-" to CalcKeyType.OPERATOR),
        listOf("1" to CalcKeyType.DIGIT,  "2" to CalcKeyType.DIGIT,      "3" to CalcKeyType.DIGIT,   "+" to CalcKeyType.OPERATOR),
        listOf("0" to CalcKeyType.DIGIT,  "." to CalcKeyType.DIGIT,      "!" to CalcKeyType.SPECIAL, "=" to CalcKeyType.EQUALS),
    )

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Scientific function strip ──────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                functionRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { func ->
                            val displayLabel = if (func == "DEG/RAD") {
                                if (state.isDegreeMode) "DEG" else "RAD"
                            } else {
                                func
                            }
                            val isAngleMode = func == "DEG/RAD"

                            SciFunctionKey(
                                label = displayLabel,
                                isAngleModeActive = isAngleMode,
                                isDeg = state.isDegreeMode,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (hapticEnabled) vibrationManager?.vibrateTick()
                                    when (func) {
                                        "DEG/RAD" -> viewModel.onToggleAngleMode()
                                        "xⁿ"     -> viewModel.onOperator("^")
                                        "π"      -> viewModel.onDigit("π")
                                        "e"      -> viewModel.onDigit("e")
                                        "(", ")" -> viewModel.onOperator(func)
                                        "CONST"  -> showConstants = true
                                        else     -> viewModel.onFunction(func)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
        )

        // ── Main number grid ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            mainRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (label, type) ->
                        CalcKey(
                            label = label,
                            keyType = type,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            onClick = {
                                if (hapticEnabled) {
                                    when (type) {
                                        CalcKeyType.EQUALS, CalcKeyType.CLEAR -> vibrationManager?.vibrateLongClick()
                                        else -> vibrationManager?.vibrateClick()
                                    }
                                }
                                dispatchCalcAction(label, viewModel)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showConstants) {
        ConstantsDialog(
            onDismiss = { showConstants = false },
            onSelect = { value ->
                viewModel.onDigit(value)
                showConstants = false
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CALC KEY — unified button composable for all grid buttons
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CalcKey(
    label: String,
    keyType: CalcKeyType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when (keyType) {
        CalcKeyType.EQUALS   -> MaterialTheme.colorScheme.primary
        CalcKeyType.CLEAR    -> MaterialTheme.colorScheme.errorContainer
        CalcKeyType.OPERATOR -> MaterialTheme.colorScheme.secondaryContainer
        CalcKeyType.FUNCTION -> MaterialTheme.colorScheme.surfaceContainerHighest
        CalcKeyType.SPECIAL  -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
        CalcKeyType.DIGIT    -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (keyType) {
        CalcKeyType.EQUALS   -> MaterialTheme.colorScheme.onPrimary
        CalcKeyType.CLEAR    -> MaterialTheme.colorScheme.error
        CalcKeyType.OPERATOR -> MaterialTheme.colorScheme.onSecondaryContainer
        CalcKeyType.FUNCTION -> MaterialTheme.colorScheme.onSurfaceVariant
        CalcKeyType.SPECIAL  -> MaterialTheme.colorScheme.onTertiaryContainer
        CalcKeyType.DIGIT    -> MaterialTheme.colorScheme.onSurface
    }
    val keyElevation = when (keyType) {
        CalcKeyType.EQUALS -> 2.dp
        else               -> 0.dp
    }

    // ExpressiveCard already uses bouncyClick internally from ExpressiveCards.kt
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier,
        shape = BouncyShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = keyElevation,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (label == "DEL") {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "Backspace",
                    tint = contentColor,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                val fontSizeSp = when {
                    label == "=" || keyType == CalcKeyType.EQUALS -> 30.sp
                    label.length >= 3                             -> 18.sp
                    else                                          -> 26.sp
                }
                val fontWeight = when (keyType) {
                    CalcKeyType.EQUALS -> FontWeight.Black
                    CalcKeyType.DIGIT  -> FontWeight.Bold
                    else               -> FontWeight.SemiBold
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = fontSizeSp,
                        fontWeight = fontWeight,
                    ),
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCI FUNCTION KEY — compact button for the scientific function strip
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SciFunctionKey(
    label: String,
    modifier: Modifier = Modifier,
    isAngleModeActive: Boolean = false,
    isDeg: Boolean = true,
    onClick: () -> Unit,
) {
    val isAngle = label == "DEG" || label == "RAD"
    val containerColor = when {
        isAngle -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        label == "CONST" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    }
    val textColor = when {
        isAngle  -> MaterialTheme.colorScheme.onTertiaryContainer
        label in listOf("π", "e") -> MaterialTheme.colorScheme.primary
        label == "CONST" -> MaterialTheme.colorScheme.onSecondaryContainer
        else     -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = SmallExpressiveShape,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isAngle) FontWeight.Black else FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HISTORY PANE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CalculatorHistoryContent(
    state: CalculatorState,
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.st_CalculatorScreen_7c4d),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(
                visible = state.history.isNotEmpty(),
                enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ToolzExpressiveIconButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        viewModel.clearHistory()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.error,
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

        // ── Empty state ────────────────────────────────────────────────────────
        if (state.history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = ExtraLargeExpressiveShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(80.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Calculate,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.st_CalculatorScreen_2b8a),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.st_CalculatorScreen_4d9c),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // ── History list ───────────────────────────────────────────────────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                itemsIndexed(
                    items = state.history,
                    key = { idx, item -> "$idx-${item.first}" },
                ) { index, (expression, result) ->
                    StaggeredEntrance(index = index) {
                        ExpressiveCard(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                viewModel.onDigit(result)
                            },
                            shape = MediumExpressiveShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            elevation = 0.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    text = expression,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "= $result",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End,
                                )
                                // Use as input hint
                                Text(
                                    text = stringResource(R.string.st_CalculatorScreen_6a1b),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS DIALOG
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ConstantsDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current

    val constants = listOf(
        Triple("π", stringResource(R.string.st_CalculatorScreen_9f0a),          "3.14159265"),
        Triple("e", stringResource(R.string.st_CalculatorScreen_a1b2),     "2.71828182"),
        Triple("φ", stringResource(R.string.st_CalculatorScreen_c3d4),"1.61803398"),
        Triple("c", stringResource(R.string.st_CalculatorScreen_e5f6), "299792458"),
        Triple("G", stringResource(R.string.st_CalculatorScreen_g7h8), "6.6743e-11"),
        Triple("h", stringResource(R.string.st_CalculatorScreen_i9j0),      "6.62607e-34"),
        Triple("k", stringResource(R.string.st_CalculatorScreen_k1l2),   "1.38064e-23"),
        Triple("Nₐ", stringResource(R.string.st_CalculatorScreen_m3n4),  "6.02214e23"),
        Triple("R", stringResource(R.string.st_CalculatorScreen_o5p6),  "8.31446"),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.st_CalculatorScreen_1b2c),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                constants.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { (symbol, name, value) ->
                            ExpressiveCard(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    onSelect(value)
                                },
                                modifier = Modifier.weight(1f),
                                shape = MediumExpressiveShape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                elevation = 0.dp,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = symbol,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = value.take(7),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        // Pad incomplete last row
                        repeat(3 - row.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_CalculatorScreen_3c4d), fontWeight = FontWeight.Bold)
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DISPATCH HELPER — maps a button label to ViewModel action
// ═══════════════════════════════════════════════════════════════════════════════

private fun dispatchCalcAction(label: String, viewModel: CalculatorViewModel) {
    when (label) {
        "C", "AC"             -> viewModel.onClear()
        "DEL"                 -> viewModel.onBackspace()
        "="                   -> viewModel.onEquals()
        "+", "-", "×", "÷"   -> viewModel.onOperator(label)
        "%"                   -> viewModel.onOperator("/100")
        "!"                   -> viewModel.onFunction("!")
        else                  -> viewModel.onDigit(label)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PREVIEWS  (Light + Dark)
// ═══════════════════════════════════════════════════════════════════════════════

@Preview(name = "Display Panel — Light", showBackground = true)
@Composable
private fun DisplayLightPreview() {
    ToolzTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            CalculatorDisplay(
                state = CalculatorState(
                    display = "1234567",
                    formula = "100 × 12345 =",
                    liveResult = "1234567",
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                onCopyResult = {},
            )
        }
    }
}

@Preview(name = "Display Panel — Dark", showBackground = true)
@Composable
private fun DisplayDarkPreview() {
    ToolzTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            CalculatorDisplay(
                state = CalculatorState(
                    display = "3.14159265",
                    formula = "sin(30) + π =",
                    liveResult = "3.64159",
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                onCopyResult = {},
            )
        }
    }
}

@Preview(name = "Display — Error state", showBackground = true)
@Composable
private fun DisplayErrorPreview() {
    ToolzTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            CalculatorDisplay(
                state = CalculatorState(
                    display = "Error",
                    error = "Invalid Expression",
                ),
                modifier = Modifier.fillMaxSize(),
                onCopyResult = {},
            )
        }
    }
}

@Preview(name = "Calc Key Variants — Light", showBackground = true)
@Composable
private fun CalcKeyVariantsLightPreview() {
    ToolzTheme(darkTheme = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalcKey("7",   CalcKeyType.DIGIT,    Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("+",   CalcKeyType.OPERATOR, Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("=",   CalcKeyType.EQUALS,   Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("C",   CalcKeyType.CLEAR,    Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("DEL", CalcKeyType.FUNCTION, Modifier.weight(1f).fillMaxHeight(), onClick = {})
        }
    }
}

@Preview(name = "Calc Key Variants — Dark", showBackground = true)
@Composable
private fun CalcKeyVariantsDarkPreview() {
    ToolzTheme(darkTheme = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalcKey("7",   CalcKeyType.DIGIT,    Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("+",   CalcKeyType.OPERATOR, Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("=",   CalcKeyType.EQUALS,   Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("C",   CalcKeyType.CLEAR,    Modifier.weight(1f).fillMaxHeight(), onClick = {})
            CalcKey("DEL", CalcKeyType.FUNCTION, Modifier.weight(1f).fillMaxHeight(), onClick = {})
        }
    }
}

@Preview(name = "History — Empty, Light", showBackground = true, heightDp = 400)
@Composable
private fun HistoryEmptyLightPreview() {
    ToolzTheme(darkTheme = false) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            CalculatorHistoryContent(
                state = CalculatorState(),
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            )
        }
    }
}

@Preview(name = "History — With items, Dark", showBackground = true, heightDp = 500)
@Composable
private fun HistoryFilledDarkPreview() {
    ToolzTheme(darkTheme = true) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            CalculatorHistoryContent(
                state = CalculatorState(
                    history = listOf(
                        "sin(30) + π" to "3.64159265",
                        "100 × 1234" to "123400",
                        "√1764" to "42",
                        "log(1000)" to "3",
                    ),
                ),
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            )
        }
    }
}

@Preview(name = "Sci Function Keys — Light", showBackground = true)
@Composable
private fun SciFunctionKeysPreview() {
    ToolzTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                listOf("sin", "cos", "tan", "log", "ln"),
                listOf("√", "xⁿ", "π", "e", "("),
                listOf(")", "DEG", "inv", "abs", "CONST"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { label ->
                        SciFunctionKey(label = label, modifier = Modifier.weight(1f), onClick = {})
                    }
                }
            }
        }
    }
}

@Preview(name = "Constants Dialog — Light", showBackground = true)
@Composable
private fun ConstantsDialogLightPreview() {
    ToolzTheme(darkTheme = false) {
        ConstantsDialog(onDismiss = {}, onSelect = {})
    }
}

@Preview(name = "Constants Dialog — Dark", showBackground = true)
@Composable
private fun ConstantsDialogDarkPreview() {
    ToolzTheme(darkTheme = true) {
        ConstantsDialog(onDismiss = {}, onSelect = {})
    }
}