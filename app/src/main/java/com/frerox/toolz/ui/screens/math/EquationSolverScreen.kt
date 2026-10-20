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

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.frerox.toolz.ui.screens.math

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.expressivePressScale
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/* ───────────────────────────── Entry point ───────────────────────────── */

class SolverActions(
    val onBack: () -> Unit,
    val onMode: (SolverMode) -> Unit,
    val onDegree: (Int) -> Unit,
    val onSize: (Int) -> Unit,
    val onRadius: (Double?) -> Unit,
    val onSelect: (String?) -> Unit,
    val onKey: (String) -> Unit,
    val onNext: () -> Unit,
    val onHideKeypad: () -> Unit,
    val onSolve: () -> Unit,
    val onClearAll: () -> Unit,
    val onExample: (SolverExample) -> Unit,
    val onShowHistory: () -> Unit,
    val onShowSteps: () -> Unit,
    val onCopy: (String) -> Unit,
    val onShare: (String) -> Unit
)

@Composable
fun EquationSolverScreen(
    viewModel: EquationSolverViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val form by viewModel.form.collectAsState()
    val computed by viewModel.computed.collectAsState()
    val history by viewModel.history.collectAsState()
    val tick by viewModel.solveTick.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }
    var manualPending by remember { mutableStateOf(false) }
    var scrollTick by remember { mutableIntStateOf(0) }

    // The screen never solves on its own. Results come from the view model's
    // debounced flow; a result only counts as fresh for the exact current problem.
    val currentProblem = form.toProblem()
    val sameShape = computed?.problem?.let {
        it.mode == form.mode && it.degree == form.degree && it.size == form.size
    } == true
    val report = if (!form.isEmpty && sameShape) computed?.report else null
    val fresh = report != null && computed?.problem == currentProblem
    val solving = !form.isEmpty && !fresh

    // Manual Solve only confirms: wait for the live result, then scroll once.
    LaunchedEffect(tick, fresh) {
        if (manualPending && fresh) {
            manualPending = false
            scrollTick++
        }
    }
    LaunchedEffect(form.isEmpty) {
        if (form.isEmpty) manualPending = false
    }

    val actions = SolverActions(
        onBack = onBack,
        onMode = viewModel::setMode,
        onDegree = viewModel::setDegree,
        onSize = viewModel::setSize,
        onRadius = viewModel::setSearchRadius,
        onSelect = viewModel::select,
        onKey = viewModel::onKey,
        onNext = viewModel::next,
        onHideKeypad = viewModel::hideKeypad,
        onSolve = {
            manualPending = true
            viewModel.hideKeypad()
            viewModel.solve()
        },
        onClearAll = viewModel::clearAll,
        onExample = viewModel::loadExample,
        onShowHistory = { showHistory = true },
        onShowSteps = { showSteps = true },
        onCopy = { copyToClipboard(context, it) },
        onShare = { shareText(context, it) }
    )

    EquationSolverScreenContent(
        form = form,
        report = report,
        fresh = fresh,
        solving = solving,
        solveTick = scrollTick,
        actions = actions
    )

    if (showHistory) {
        HistoryBottomSheet(
            history = history,
            onDismiss = { showHistory = false },
            onSelect = { viewModel.restore(it); showHistory = false },
            onDelete = viewModel::deleteRecord,
            onClear = viewModel::clearHistory
        )
    }

    if (showSteps && report != null) {
        StepsBottomSheet(
            equation = report.equation,
            steps = report.steps,
            onDismiss = { showSteps = false }
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Equation Solver", text))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/* ───────────────────────────── Screen layout ───────────────────────────── */

private enum class SlotState { ACTIVE, ERROR, FILLED, EMPTY }

private val DegreeNames = listOf("Linear", "Quadratic", "Cubic", "Quartic", "Quintic", "Sextic")

/** Every field the user has to fill for the current shape. */
private fun requiredKeys(form: SolverForm): List<String> = when (form.mode) {
    SolverMode.POLYNOMIAL -> (0..form.degree).map { "p$it" }
    SolverMode.SYSTEM -> (0 until form.size).flatMap { r -> (0..form.size).map { c -> "s$r$c" } }
    SolverMode.CUSTOM -> listOf("lhs")
}

private fun filledCount(form: SolverForm): Int =
    requiredKeys(form).count { !form.inputs[it].isNullOrEmpty() }

private fun shapeTitle(form: SolverForm): String = when (form.mode) {
    SolverMode.POLYNOMIAL -> "${DegreeNames.getOrElse(form.degree - 1) { "Polynomial" }} equation"
    SolverMode.SYSTEM -> "${form.size} × ${form.size} linear system"
    SolverMode.CUSTOM -> "Custom equation"
}

@Composable
fun EquationSolverScreenContent(
    form: SolverForm,
    report: SolveReport?,
    fresh: Boolean,
    solving: Boolean,
    solveTick: Int,
    actions: SolverActions
) {
    val resultRequester = remember { BringIntoViewRequester() }
    val scroll = rememberScrollState()
    val invalidSlot = if (fresh) (report?.outcome as? Outcome.Invalid)?.slot else null

    BackHandler(enabled = form.active != null) { actions.onHideKeypad() }

    LaunchedEffect(solveTick) {
        if (solveTick > 0) {
            delay(60)
            resultRequester.bringIntoView()
        }
    }

    Box(modifier = Modifier.fillMaxSize().toolzBackground()) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    title = stringResource(R.string.st_EquationSolverScreen_f1a2),
                    navigationIcon = {
                        ToolzExpressiveIconButton(
                            onClick = actions.onBack,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = MediumExpressiveShape
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.st_EquationSolverScreen_9e2c)
                            )
                        }
                    },
                    actions = {
                        ToolzExpressiveIconButton(
                            onClick = actions.onShowSteps,
                            enabled = fresh && report?.steps?.isNotEmpty() == true,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = MediumExpressiveShape
                        ) {
                            Icon(Icons.Rounded.Lightbulb, contentDescription = "Steps")
                        }
                        Spacer(Modifier.width(6.dp))
                        ToolzExpressiveIconButton(
                            onClick = actions.onShowHistory,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = MediumExpressiveShape
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = stringResource(R.string.st_EquationSolverScreen_1a2b)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxSize()
                ) {
                    SolverSelectors(form, actions)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fadingEdges(top = 6.dp, bottom = 6.dp)
                            .verticalScroll(scroll)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EquationCard(form, invalidSlot, actions)

                        if (form.isEmpty) {
                            ExamplesSection(form.mode, actions.onExample)
                        }

                        Box(Modifier.bringIntoViewRequester(resultRequester)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (report != null) {
                                    ResultCard(report, fresh, actions)
                                    report.graph?.let { GraphCard(it, fresh) }
                                } else if (solving) {
                                    Text(
                                        "Solving…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                    }

                    if (form.active != null) {
                        SolverKeypad(
                            mode = form.mode,
                            slotTitle = slotTitle(form),
                            showStatus = !form.isEmpty,
                            solving = solving,
                            onKey = actions.onKey,
                            onNext = actions.onNext,
                            onSolve = actions.onSolve,
                            onHide = actions.onHideKeypad
                        )
                    } else if (!form.isEmpty) {
                        SolverStatusBar(
                            form = form,
                            fresh = fresh,
                            solving = solving,
                            onSolve = actions.onSolve
                        )
                    } else {
                        Spacer(Modifier.navigationBarsPadding().height(8.dp))
                    }
                }
            }
        }
    }
}

/* ───────────────────────────── Selectors ───────────────────────────── */

@Composable
private fun SolverSelectors(form: SolverForm, actions: SolverActions) {
    val modes = listOf("Polynomial", "System", "Custom")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ToolzConnectedButtonGroup(
            selectedIndex = SolverMode.entries.indexOf(form.mode),
            options = modes,
            onOptionSelected = { actions.onMode(SolverMode.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        SubSelector(form, actions)
    }
}

@Composable
private fun SubSelector(form: SolverForm, actions: SolverActions) {
    val haptic = rememberToolzHapticFeedback()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
            .horizontalFadingEdges(left = 8.dp, right = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        when (form.mode) {
            SolverMode.POLYNOMIAL -> items(
                (Slots.MIN_DEGREE..Slots.MAX_DEGREE).toList(),
                key = { it }
            ) { degree ->
                ModeChip(DegreeNames[degree - 1], form.degree == degree) {
                    haptic.tick(); actions.onDegree(degree)
                }
            }
            SolverMode.SYSTEM -> items(listOf(2, 3), key = { it }) { size ->
                ModeChip("$size × $size", form.size == size) {
                    haptic.tick(); actions.onSize(size)
                }
            }
            SolverMode.CUSTOM -> items(
                listOf<Double?>(null, 10.0, 100.0, 1000.0),
                key = { it?.toString() ?: "auto" }
            ) { radius ->
                val label = if (radius == null) "Auto range" else "±${radius.toInt()}"
                ModeChip(label, form.searchRadius == radius) {
                    haptic.tick(); actions.onRadius(radius)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    ExpressiveFilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        shape = SmallExpressiveShape
    )
}

/* ───────────────────────────── Equation card & slots ───────────────────────────── */

private fun slotState(key: String, form: SolverForm, invalid: String?): SlotState = when {
    form.active == key -> SlotState.ACTIVE
    invalid == key -> SlotState.ERROR
    form.inputs[key].isNullOrEmpty() -> SlotState.EMPTY
    else -> SlotState.FILLED
}

/** Raw → pretty: keeps what's stored ASCII-safe for the parser but shows real math symbols. */
private fun prettyInput(raw: String): String =
    raw.replace('-', '−').replace('*', '×').replace('/', '÷').replace("sqrt(", "√(")

@Composable
private fun EquationCard(form: SolverForm, invalidSlot: String?, actions: SolverActions) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        color = cs.surfaceContainerHigh.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    shapeTitle(form),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    modifier = Modifier.weight(1f)
                )
                if (!form.isEmpty) {
                    TextButton(
                        onClick = actions.onClearAll,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (form.mode) {
                    SolverMode.POLYNOMIAL -> PolynomialSlots(form.degree, form, invalidSlot, actions.onSelect)
                    SolverMode.SYSTEM -> SystemSlots(form.size, form, invalidSlot, actions.onSelect)
                    SolverMode.CUSTOM -> CustomSlots(form, invalidSlot, actions.onSelect)
                }
            }
        }
    }
}

@Composable
private fun EqText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PolynomialSlots(degree: Int, form: SolverForm, invalid: String?, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (i in 0..degree) {
            val key = "p$i"
            val power = degree - i
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoefficientSlot(
                    value = form.inputs[key].orEmpty(),
                    placeholder = Slots.polyLetter(i).toString(),
                    state = slotState(key, form, invalid),
                    onClick = { onSelect(key) }
                )
                if (power >= 1) EqText(if (power == 1) "x" else "x" + Fmt.sup(power))
                EqText(if (i < degree) " +" else " = 0")
            }
        }
    }
}

@Composable
private fun SystemSlots(size: Int, form: SolverForm, invalid: String?, onSelect: (String) -> Unit) {
    for (r in 0 until size) {
        SystemRow(r, size, form, invalid, onSelect)
    }
}

@Composable
private fun SystemRow(
    r: Int,
    size: Int,
    form: SolverForm,
    invalid: String?,
    onSelect: (String) -> Unit
) {
    val vars = listOf("x", "y", "z")
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalFadingEdges(left = 6.dp, right = 6.dp)
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
    ) {
        for (c in 0 until size) {
            val key = "s$r$c"
            if (c > 0) EqText(" +")
            CoefficientSlot(
                value = form.inputs[key].orEmpty(),
                placeholder = ('a' + c).toString(),
                state = slotState(key, form, invalid),
                onClick = { onSelect(key) },
                minWidth = 44.dp
            )
            EqText(vars[c])
        }
        val rhs = "s$r$size"
        EqText(" =")
        CoefficientSlot(
            value = form.inputs[rhs].orEmpty(),
            placeholder = "k",
            state = slotState(rhs, form, invalid),
            onClick = { onSelect(rhs) },
            minWidth = 44.dp
        )
    }
}

@Composable
private fun CustomSlots(form: SolverForm, invalid: String?, onSelect: (String) -> Unit) {
    Text(
        "Left side",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp)
    )
    CoefficientSlot(
        value = form.inputs["lhs"].orEmpty(),
        placeholder = "expression in x",
        state = slotState("lhs", form, invalid),
        onClick = { onSelect("lhs") },
        wide = true
    )
    EqText("=")
    Text(
        "Right side",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp)
    )
    CoefficientSlot(
        value = form.inputs["rhs"].orEmpty(),
        placeholder = "0",
        state = slotState("rhs", form, invalid),
        onClick = { onSelect("rhs") },
        wide = true
    )
}

@Composable
private fun CoefficientSlot(
    value: String,
    placeholder: String,
    state: SlotState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 46.dp,
    wide: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberToolzHapticFeedback()
    val interaction = remember { MutableInteractionSource() }
    val scroll = rememberScrollState()
    LaunchedEffect(value, wide, state) {
        if (wide && state == SlotState.ACTIVE) scroll.scrollTo(scroll.maxValue)
    }

    val container = when (state) {
        SlotState.ACTIVE -> cs.primaryContainer
        SlotState.ERROR -> cs.errorContainer.copy(alpha = 0.6f)
        SlotState.FILLED -> cs.surfaceContainerHighest
        SlotState.EMPTY -> cs.surfaceContainer
    }
    val border = when (state) {
        SlotState.ACTIVE -> cs.primary
        SlotState.ERROR -> cs.error
        SlotState.EMPTY -> cs.outlineVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val textColor = when (state) {
        SlotState.ACTIVE -> cs.onPrimaryContainer
        SlotState.ERROR -> cs.onErrorContainer
        else -> cs.onSurface
    }

    Surface(
        onClick = { haptic.tick(); onClick() },
        interactionSource = interaction,
        modifier = modifier
            .expressivePressScale(interaction)
            .then(if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(min = minWidth))
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(if (state == SlotState.ACTIVE) 2.dp else 1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .then(if (wide) Modifier.horizontalFadingEdges(left = 6.dp, right = 6.dp) else Modifier)
                .then(if (wide) Modifier.horizontalScroll(scroll) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (wide) Arrangement.Start else Arrangement.Center
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = (if (state == SlotState.ACTIVE) cs.onPrimaryContainer else cs.onSurfaceVariant)
                        .copy(alpha = 0.45f),
                    maxLines = 1
                )
            } else {
                Text(
                    text = prettyInput(value),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (state == SlotState.ACTIVE) {
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(width = 2.dp, height = 18.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(cs.primary)
                )
            }
        }
    }
}

/* ───────────────────────────── Examples ───────────────────────────── */

@Composable
private fun ExamplesSection(mode: SolverMode, onExample: (SolverExample) -> Unit) {
    val examples = SolverExamples[mode].orEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Examples",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            examples.forEach { example ->
                ExpressiveFilterChip(
                    selected = false,
                    onClick = { onExample(example) },
                    label = {
                        Text(example.label, style = MaterialTheme.typography.labelMedium)
                    },
                    shape = SmallExpressiveShape
                )
            }
        }
    }
}

private fun slotTitle(form: SolverForm): String {
    val key = form.active ?: return ""
    return when (form.mode) {
        SolverMode.POLYNOMIAL -> {
            val i = key.removePrefix("p").toIntOrNull() ?: 0
            val power = form.degree - i
            val term = when (power) {
                0 -> "constant term"
                1 -> "x"
                else -> "x" + Fmt.sup(power)
            }
            "Coefficient ${Slots.polyLetter(i)} · $term"
        }
        SolverMode.SYSTEM -> {
            val r = key.getOrNull(1)?.digitToIntOrNull() ?: 0
            val c = key.getOrNull(2)?.digitToIntOrNull() ?: 0
            val what = if (c == form.size) "right-hand side" else "coefficient of ${listOf("x", "y", "z")[c]}"
            "Equation ${r + 1} · $what"
        }
        SolverMode.CUSTOM -> if (key == "lhs") "Left side" else "Right side"
    }
}

/* ───────────────────────────── Results ───────────────────────────── */

@Composable
private fun ResultCard(report: SolveReport, fresh: Boolean, actions: SolverActions) {
    val cs = MaterialTheme.colorScheme
    val outcome = report.outcome
    val error = outcome is Outcome.Invalid
    val container = when {
        error -> cs.errorContainer.copy(alpha = 0.42f)
        outcome is Outcome.Infinite -> cs.tertiaryContainer.copy(alpha = 0.32f)
        else -> cs.surfaceContainerHigh.copy(alpha = 0.42f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (fresh) 1f else 0.55f),
        shape = MediumExpressiveShape,
        color = container,
        border = BorderStroke(
            1.dp,
            if (error) cs.error.copy(alpha = 0.25f) else cs.outlineVariant.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (outcome) {
                is Outcome.Incomplete -> {
                    SectionHead(Icons.Rounded.Info, "Finish the equation", error = false)
                    Text(
                        outcome.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }

                is Outcome.Invalid -> {
                    SectionHead(Icons.Rounded.Warning, "Check your input", error = true)
                    Text(
                        outcome.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onErrorContainer
                    )
                    val slot = outcome.slot
                    if (slot != null) {
                        TextButton(
                            onClick = { actions.onSelect(slot) },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Edit field", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                is Outcome.Roots -> {
                    SectionHead(Icons.Rounded.Check, outcome.headline, error = false)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        outcome.rows.forEach { row -> RootRowItem(row, actions.onCopy) }
                    }
                    if (outcome.exact != null || outcome.factored != null || outcome.note != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cs.surfaceContainerHighest.copy(alpha = 0.45f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                outcome.exact?.let { LabeledLine("Exact form", it) }
                                outcome.factored?.let { LabeledLine("Factored form", it) }
                                outcome.note?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                is Outcome.Values -> {
                    SectionHead(Icons.Rounded.Check, outcome.headline, error = false)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        outcome.rows.forEach { row ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = cs.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        row.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cs.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        row.text,
                                        style = if (row.text.length > 8) {
                                            MaterialTheme.typography.titleSmall
                                        } else {
                                            MaterialTheme.typography.titleLarge
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                is Outcome.Infinite -> {
                    SectionHead(Icons.Rounded.Info, outcome.headline, error = false)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        outcome.lines.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                color = cs.onTertiaryContainer
                            )
                        }
                    }
                }

                is Outcome.NoSolution -> {
                    SectionHead(Icons.Rounded.Info, outcome.headline, error = false)
                    Text(
                        outcome.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            if (report.steps.isNotEmpty() || report.copyText.isNotEmpty()) {
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.25f))
                ResultActions(report, actions)
            }
        }
    }
}

@Composable
private fun SectionHead(icon: ImageVector, text: String, error: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RootRowItem(row: RootRow, onCopy: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberToolzHapticFeedback()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1100)
            copied = false
        }
    }
    val container = if (row.isComplex) cs.tertiaryContainer.copy(alpha = 0.55f) else cs.primaryContainer.copy(alpha = 0.55f)
    val onContainer = if (row.isComplex) cs.onTertiaryContainer else cs.onPrimaryContainer

    Surface(
        onClick = {
            haptic.longClick()
            onCopy(row.text.replace('−', '-'))
            copied = true
        },
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(14.dp),
        color = container
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                row.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = onContainer,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(8.dp))
            Text(
                row.text,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
                modifier = Modifier.weight(1f)
            )
            if (row.isComplex) {
                Text(
                    "complex",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.65f)
                )
                Spacer(Modifier.width(8.dp))
            }
            if (row.mult > 1) {
                Surface(shape = CircleShape, color = onContainer.copy(alpha = 0.12f)) {
                    Text(
                        "×${row.mult}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = onContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy value",
                tint = onContainer.copy(alpha = if (copied) 1f else 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ResultActions(report: SolveReport, actions: SolverActions) {
    val haptic = rememberToolzHapticFeedback()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }
    val canCopy = report.copyText.isNotEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (report.steps.isNotEmpty()) {
            FilledTonalButton(
                onClick = { haptic.tick(); actions.onShowSteps() },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = SmallExpressiveShape,
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Steps",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (canCopy) {
            FilledTonalIconButton(
                onClick = {
                    haptic.longClick()
                    actions.onCopy(report.copyText)
                    copied = true
                },
                modifier = Modifier.size(40.dp),
                shape = SmallExpressiveShape
            ) {
                Icon(
                    if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = if (copied) "Copied" else "Copy all",
                    modifier = Modifier.size(18.dp)
                )
            }
            FilledTonalIconButton(
                onClick = { haptic.tick(); actions.onShare(report.copyText) },
                modifier = Modifier.size(40.dp),
                shape = SmallExpressiveShape
            ) {
                Icon(Icons.Rounded.Share, "Share", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/* ───────────────────────────── Graph ───────────────────────────── */

@Composable
private fun GraphCard(graph: GraphData, fresh: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = MediumExpressiveShape,
        color = cs.surfaceContainerHigh.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (fresh) 1f else 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Graph",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary,
                    modifier = Modifier.weight(1f)
                )
                if (graph.traceable) {
                    Text(
                        "Drag to trace",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            SolverGraph(graph)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                graph.series.forEach { series ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (series.colorIndex % 2 == 0) cs.primary else cs.tertiary)
                        )
                        Text(
                            series.label.replace('-', '−'),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun niceTicks(min: Double, max: Double, target: Int): List<Double> {
    val range = max - min
    if (range <= 0 || !range.isFinite()) return emptyList()
    val raw = range / target
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val step = mag * when {
        norm < 1.5 -> 1.0
        norm < 3.5 -> 2.0
        norm < 7.5 -> 5.0
        else -> 10.0
    }
    val out = ArrayList<Double>()
    var t = kotlin.math.ceil(min / step) * step
    var guard = 0
    while (t <= max + step * 1e-9 && guard++ < 40) {
        out += if (abs(t) < step * 1e-9) 0.0 else t
        t += step
    }
    return out
}

private fun interpolate(s: GraphSeries, x: Double): Double? {
    for (i in 0 until s.xs.size - 1) {
        val y0 = s.ys[i]
        val y1 = s.ys[i + 1]
        if (y0.isNaN() || y1.isNaN()) continue
        val x0 = s.xs[i]
        val x1 = s.xs[i + 1]
        if (x1 > x0 && x >= x0 && x <= x1) return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }
    return null
}

@Composable
fun SolverGraph(graph: GraphData, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.copy(
        color = cs.onSurfaceVariant.copy(alpha = 0.85f),
        fontSize = 10.sp
    )
    val markerStyle = MaterialTheme.typography.labelSmall.copy(
        color = cs.onSurface,
        fontWeight = FontWeight.SemiBold
    )
    val readoutStyle = MaterialTheme.typography.labelMedium.copy(
        color = cs.onSurface,
        fontFamily = FontFamily.Monospace
    )
    var traceX by remember(graph) { mutableStateOf<Float?>(null) }

    val seriesColors = listOf(cs.primary, cs.tertiary)
    val gridColor = cs.outlineVariant.copy(alpha = 0.45f)
    val axisColor = cs.onSurfaceVariant.copy(alpha = 0.7f)
    val surface = cs.surface
    val readoutBg = cs.surfaceContainerHighest
    val secondary = cs.secondary

    val traceModifier = if (graph.traceable) {
        Modifier.pointerInput(graph) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                traceX = down.position.x
                var locked = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.isConsumed && !locked) break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!locked && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) locked = true
                    if (locked) change.consume()
                    traceX = change.position.x
                }
                traceX = null
            }
        }
    } else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceContainerLowest)
            .then(traceModifier)
    ) {
        val w = size.width
        val h = size.height
        val xSpan = graph.xMax - graph.xMin
        val ySpan = graph.yMax - graph.yMin
        fun px(x: Double): Float = ((x - graph.xMin) / xSpan * w).toFloat()
        fun py(y: Double): Float = (h - (y - graph.yMin) / ySpan * h).toFloat()

        val xTicks = niceTicks(graph.xMin, graph.xMax, 6)
        val yTicks = niceTicks(graph.yMin, graph.yMax, 4)
        for (t in xTicks) drawLine(gridColor, Offset(px(t), 0f), Offset(px(t), h), strokeWidth = 1f)
        for (t in yTicks) drawLine(gridColor, Offset(0f, py(t)), Offset(w, py(t)), strokeWidth = 1f)

        val xAxisVisible = 0.0 >= graph.yMin && 0.0 <= graph.yMax
        val yAxisVisible = 0.0 >= graph.xMin && 0.0 <= graph.xMax
        if (xAxisVisible) drawLine(axisColor, Offset(0f, py(0.0)), Offset(w, py(0.0)), strokeWidth = 1.5.dp.toPx())
        if (yAxisVisible) drawLine(axisColor, Offset(px(0.0), 0f), Offset(px(0.0), h), strokeWidth = 1.5.dp.toPx())

        val pad = 4.dp.toPx()
        var lastRight = -1e9f
        for (t in xTicks) {
            if (t == 0.0 && yAxisVisible) continue
            val layout = measurer.measure(Fmt.shown(t, 4), tickStyle)
            val x = (px(t) - layout.size.width / 2f).coerceIn(pad, w - layout.size.width - pad)
            if (x < lastRight + 6.dp.toPx()) continue
            val top = ((if (xAxisVisible) py(0.0) + pad else h) - (if (xAxisVisible) 0f else layout.size.height + pad))
                .coerceIn(pad, h - layout.size.height - pad)
            drawText(layout, topLeft = Offset(x, top))
            lastRight = x + layout.size.width
        }
        for (t in yTicks) {
            if (t == 0.0 && xAxisVisible) continue
            val layout = measurer.measure(Fmt.shown(t, 4), tickStyle)
            val left = (if (yAxisVisible) px(0.0) + pad else pad).coerceIn(pad, w - layout.size.width - pad)
            val top = (py(t) - layout.size.height / 2f).coerceIn(pad, h - layout.size.height - pad)
            drawText(layout, topLeft = Offset(left, top))
        }

        graph.series.forEach { series ->
            val path = Path()
            var pen = false
            for (i in series.xs.indices) {
                val y = series.ys[i]
                if (y.isNaN()) {
                    pen = false
                    continue
                }
                val x = px(series.xs[i])
                val yy = py(y).coerceIn(-1e6f, 1e6f)
                if (!pen) {
                    path.moveTo(x, yy)
                    pen = true
                } else {
                    path.lineTo(x, yy)
                }
            }
            drawPath(
                path = path,
                color = seriesColors[series.colorIndex % 2],
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        val drawn = ArrayList<Rect>()
        graph.markers.sortedBy { it.x }.forEach { marker ->
            val cx = px(marker.x)
            val cy = py(marker.y)
            if (cx < 0f || cx > w || cy < 0f || cy > h) return@forEach
            if (marker.kind == 0) {
                drawCircle(surface, radius = 8.dp.toPx(), center = Offset(cx, cy))
                drawCircle(seriesColors[0], radius = 5.dp.toPx(), center = Offset(cx, cy))
                if (marker.label.isNotEmpty()) {
                    val layout = measurer.measure(marker.label, markerStyle)
                    val lx = (cx - layout.size.width / 2f).coerceIn(pad, w - layout.size.width - pad)
                    var ly = cy - 12.dp.toPx() - layout.size.height
                    if (ly < pad) ly = cy + 12.dp.toPx()
                    val rect = Rect(lx - 2f, ly - 2f, lx + layout.size.width + 2f, ly + layout.size.height + 2f)
                    if (drawn.none { it.overlaps(rect) }) {
                        drawText(layout, topLeft = Offset(lx, ly))
                        drawn += rect
                    }
                }
            } else {
                drawCircle(surface, radius = 5.dp.toPx(), center = Offset(cx, cy))
                drawCircle(secondary, radius = 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
            }
        }

        val tx = traceX
        if (graph.traceable && tx != null) {
            val cx = tx.coerceIn(0f, w)
            val dx = graph.xMin + cx / w * xSpan
            val dy = interpolate(graph.series[0], dx)
            drawLine(axisColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.dp.toPx())
            if (dy != null) {
                val cy = py(dy).coerceIn(0f, h)
                drawCircle(surface, radius = 8.dp.toPx(), center = Offset(cx, cy))
                drawCircle(seriesColors[0], radius = 5.dp.toPx(), center = Offset(cx, cy))
                val layout = measurer.measure(
                    "x = ${Fmt.shown(dx, 4)}\ny = ${Fmt.shown(dy, 4)}",
                    readoutStyle
                )
                val boxW = layout.size.width + 16.dp.toPx()
                val boxH = layout.size.height + 12.dp.toPx()
                val bx = if (cx < w / 2f) w - boxW - 8.dp.toPx() else 8.dp.toPx()
                drawRoundRect(
                    readoutBg,
                    topLeft = Offset(bx, 8.dp.toPx()),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
                drawText(layout, topLeft = Offset(bx + 8.dp.toPx(), 8.dp.toPx() + 6.dp.toPx()))
            }
        }
    }
}

/* ───────────────────────────── Status bar ───────────────────────────── */

@Composable
private fun SolverStatusBar(
    form: SolverForm,
    fresh: Boolean,
    solving: Boolean,
    onSolve: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberToolzHapticFeedback()
    val total = requiredKeys(form).size
    val filled = filledCount(form)
    val status = when {
        solving -> "Solving…"
        fresh -> "Up to date"
        filled >= total -> "Ready"
        else -> "$filled of $total filled"
    }

    Surface(
        color = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            FilledTonalButton(
                onClick = { haptic.longClick(); onSolve() },
                modifier = Modifier.height(40.dp),
                shape = SmallExpressiveShape,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Solve",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/* ───────────────────────────── Keypad ───────────────────────────── */

private data class Token(val label: String, val insert: String, val sub: String? = null)

private fun tokensFor(mode: SolverMode): List<Token> {
    val ops = listOf(
        Token("+", "+"), Token("−", "-"), Token("×", "*"), Token("÷", "/"), Token("^", "^"),
        Token("(", "("), Token(")", ")"), Token("√", "sqrt("), Token("π", "π"), Token("e", "e")
    )
    val fns = listOf(
        Token("x", "x"), Token("sin", "sin("), Token("cos", "cos("), Token("tan", "tan("),
        Token("ln", "log("), Token("log", "log10("), Token("eˣ", "exp("), Token("|x|", "abs(")
    )
    val constants = SolverConstants.map { Token(it.symbol, it.insert, it.name) }
    return (if (mode == SolverMode.CUSTOM) fns else emptyList()) + ops + constants
}

@Composable
private fun SolverKeypad(
    mode: SolverMode,
    slotTitle: String,
    showStatus: Boolean,
    solving: Boolean,
    onKey: (String) -> Unit,
    onNext: () -> Unit,
    onSolve: () -> Unit,
    onHide: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = remember(mode) { tokensFor(mode) }
    val rows = listOf(
        listOf("7", "8", "9", "BS"),
        listOf("4", "5", "6", "NEG"),
        listOf("1", "2", "3", "NEXT"),
        listOf("0", ".", "C", "SOLVE")
    )

    Surface(
        color = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    slotTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (showStatus) {
                    Text(
                        if (solving) "Solving…" else "Up to date",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onHide, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Hide keypad",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalFadingEdges(left = 2.dp, right = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(tokens, key = { it.label + it.insert }) { token ->
                    TokenChip(token) { onKey(token.insert) }
                }
            }

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        val modifier = Modifier.weight(1f)
                        when (key) {
                            "BS" -> PadKey(
                                modifier = modifier,
                                onClick = { onKey("BS") },
                                container = cs.secondaryContainer,
                                content = cs.onSecondaryContainer,
                                onLongClick = { onKey("C") }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Backspace (hold to clear)",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            "NEG" -> PadKey(
                                modifier = modifier,
                                onClick = { onKey("NEG") },
                                container = cs.secondaryContainer,
                                content = cs.onSecondaryContainer
                            ) { PadText("±") }

                            "NEXT" -> PadKey(
                                modifier = modifier,
                                onClick = onNext,
                                container = cs.tertiaryContainer,
                                content = cs.onTertiaryContainer
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Next field",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            "C" -> PadKey(
                                modifier = modifier,
                                onClick = { onKey("C") },
                                container = cs.errorContainer.copy(alpha = 0.8f),
                                content = cs.onErrorContainer
                            ) { PadText("C") }

                            "SOLVE" -> PadKey(
                                modifier = modifier,
                                onClick = onSolve,
                                container = cs.primary,
                                content = cs.onPrimary
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Solve",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            else -> PadKey(
                                modifier = modifier,
                                onClick = { onKey(key) },
                                container = cs.surfaceContainerHighest,
                                content = cs.onSurface
                            ) { PadText(key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadText(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PadKey(
    modifier: Modifier,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    onLongClick: (() -> Unit)? = null,
    label: @Composable () -> Unit
) {
    val haptic = rememberToolzHapticFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) lerp(container, content, 0.16f) else container,
        animationSpec = tween(70),
        label = "solverKeyBackground"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessHigh
        ),
        label = "solverKeyScale"
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = { haptic.tick(); onClick() },
                onLongClick = onLongClick?.let { longClick ->
                    { haptic.longClick(); longClick() }
                }
            )
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { label() }
    }
}

@Composable
private fun TokenChip(token: Token, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .height(if (token.sub != null) 48.dp else 44.dp)
            .expressivePressScale(interaction),
        shape = RoundedCornerShape(12.dp),
        color = if (token.sub != null) {
            cs.secondaryContainer.copy(alpha = 0.6f)
        } else {
            cs.surfaceContainerHigh
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 44.dp)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                token.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            token.sub?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/* ───────────────────────────── Sheets ───────────────────────────── */

@Composable
fun StepsBottomSheet(equation: String, steps: List<Step>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 56.dp,
                height = 4.dp,
                color = cs.outlineVariant
            )
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.78f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Steps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    equation,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .horizontalFadingEdges(left = 4.dp, right = 12.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            val lineColor = cs.outlineVariant.copy(alpha = 0.6f)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fadingEdges(top = 4.dp, bottom = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(steps, key = { index, _ -> index }) { index, step ->
                    val last = index == steps.lastIndex
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.drawBehind {
                            if (!last) {
                                val x = 12.dp.toPx()
                                drawLine(
                                    color = lineColor,
                                    start = Offset(x, 28.dp.toPx()),
                                    end = Offset(x, size.height + 12.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    ) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = if (last) cs.primary else cs.primary.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (last) cs.onPrimary else cs.primary
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                step.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            if (step.body.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = cs.surfaceContainerHigh.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        step.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalFadingEdges(left = 4.dp, right = 10.dp)
                                            .horizontalScroll(rememberScrollState())
                                            .padding(10.dp)
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

@Composable
fun HistoryBottomSheet(
    history: List<SolveRecord>,
    onDismiss: () -> Unit,
    onSelect: (SolveRecord) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 56.dp,
                height = 4.dp,
                color = cs.outlineVariant
            )
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.7f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear all") }
                }
            }

            if (history.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No saved solutions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Press Solve to save one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        Surface(
                            onClick = { onSelect(item) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = cs.surfaceVariant.copy(alpha = 0.28f)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = 14.dp,
                                    top = 10.dp,
                                    bottom = 10.dp,
                                    end = 4.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        item.equation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.summary,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = cs.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(item.time).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
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

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(showBackground = true)
@Composable
fun EquationSolverPreview() {
    ToolzTheme {
        val problem = Problem(
            SolverMode.POLYNOMIAL,
            degree = 3,
            inputs = mapOf("p0" to "1", "p1" to "0", "p2" to "-3", "p3" to "2")
        )
        val form = SolverForm(
            mode = problem.mode,
            degree = 3,
            inputs = problem.inputs,
            active = "p1"
        )
        EquationSolverScreenContent(
            form = form,
            report = EquationEngine.solve(problem),
            fresh = true,
            solving = false,
            solveTick = 0,
            actions = SolverActions(
                onBack = {},
                onMode = {},
                onDegree = {},
                onSize = {},
                onRadius = {},
                onSelect = {},
                onKey = {},
                onNext = {},
                onHideKeypad = {},
                onSolve = {},
                onClearAll = {},
                onExample = {},
                onShowHistory = {},
                onShowSteps = {},
                onCopy = {},
                onShare = {}
            )
        )
    }
}
