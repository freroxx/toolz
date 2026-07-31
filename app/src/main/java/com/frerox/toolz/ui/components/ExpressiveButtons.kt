package com.frerox.toolz.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier.expressivePressScale(interactionSource, enabled),
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) ButtonShapes(shape, shape) else shapes,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzOutlinedExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    shape: Shape? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }

    OutlinedButton(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier.expressivePressScale(interactionSource, enabled),
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) ButtonShapes(shape, shape) else shapes,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzElevatedExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }

    ElevatedButton(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier.expressivePressScale(interactionSource, enabled),
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) ButtonShapes(shape, shape) else shapes,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzLargeExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }

    LargeExtendedFloatingActionButton(
        onClick = {
            if (enabled) {
                haptic.click()
                currentOnClick()
            }
        },
        modifier = modifier.expressivePressScale(interactionSource, enabled),
        icon = icon,
        text = text,
        interactionSource = interactionSource,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzElevatedExpressiveToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ToggleButtonColors = ToggleButtonDefaults.elevatedToggleButtonColors(),
    shapes: ToggleButtonShapes = ToggleButtonShapes(ButtonDefaults.shape, ButtonDefaults.shape, ButtonDefaults.shape),
    shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }

    ElevatedToggleButton(
        checked = checked,
        onCheckedChange = {
            haptic.tick()
            currentOnCheckedChange(it)
        },
        modifier = modifier.expressivePressScale(interactionSource, enabled),
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) ToggleButtonShapes(shape, shape, shape) else shapes,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzTonalExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }

    FilledTonalButton(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier.expressivePressScale(interactionSource, enabled),
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) ButtonShapes(shape, shape) else shapes,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzTonalExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    shape: Shape? = null,
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)

    FilledTonalIconButton(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) IconButtonShapes(shape, shape) else shapes,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzTonalExpressiveIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = IconButtonDefaults.filledTonalIconToggleButtonColors(),
    shapes: IconToggleButtonShapes = IconButtonDefaults.toggleableShapes(),
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = {
            haptic.tick()
            currentOnCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        shapes = shapes,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    shape: Shape? = null,
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)

    FilledIconButton(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) IconButtonShapes(shape, shape) else shapes,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzExpressiveIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = IconButtonDefaults.filledIconToggleButtonColors(
        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    shapes: IconToggleButtonShapes = IconButtonDefaults.toggleableShapes(),
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = {
            haptic.tick()
            currentOnCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        shapes = shapes,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = SmallExpressiveShape,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val performanceMode = LocalPerformanceMode.current
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            performanceMode -> 1f
            isPressed -> 0.94f
            selected -> 1.03f
            else -> 1f
        },
        animationSpec = if (performanceMode) tween(durationMillis = 90) else spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "expressiveChipScale",
    )

    FilterChip(
        selected = selected,
        onClick = {
            haptic.tick()
            currentOnClick()
        },
        label = label,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = resolvedInteractionSource,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSplitButton(
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContentDescription: String? = "Expand",
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnMenuClick by rememberUpdatedState(onMenuClick)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    var internalChecked by remember { mutableStateOf(false) }
    val expanded = checked ?: internalChecked

    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = {
                    haptic.click()
                    currentOnClick()
                },
                enabled = enabled,
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                }
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                    ),
                ) {
                    label()
                }
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = expanded,
                onCheckedChange = { next ->
                    haptic.tick()
                    if (checked == null) internalChecked = next
                    currentOnCheckedChange?.invoke(next)
                    currentOnMenuClick()
                },
                enabled = enabled,
                modifier = Modifier.semantics {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "splitButtonTrailingIconRotation",
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = trailingContentDescription,
                    modifier = Modifier
                        .size(SplitButtonDefaults.TrailingIconSize)
                        .graphicsLayer { rotationZ = rotation },
                )
            }
        },
    )
}

@Composable
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val performanceMode = LocalPerformanceMode.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !performanceMode) 0.94f else 1f,
        animationSpec = if (performanceMode) tween(durationMillis = 90) else spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "expressivePressScale",
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpressiveButtonsPreview() {
    ToolzTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToolzExpressiveButton(onClick = {}) {
                Text("Expressive Button")
            }

            ToolzOutlinedExpressiveButton(onClick = {}) {
                Text("Outlined Expressive")
            }

            ToolzLargeExtendedFloatingActionButton(
                onClick = {},
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Large FAB") },
            )

            var checked by remember { mutableStateOf(true) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolzExpressiveIconButton(onClick = {}) {
                    Icon(Icons.Rounded.Save, null)
                }
                ToolzExpressiveIconToggleButton(
                    checked = checked,
                    onCheckedChange = { checked = it },
                ) {
                    Icon(if (checked) Icons.Rounded.Check else Icons.Rounded.Lock, null)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveFilterChip(selected = true, onClick = {}, label = { Text("Selected") })
                ExpressiveFilterChip(selected = false, onClick = {}, label = { Text("Unselected") })
            }

            ExpressiveSplitButton(
                onClick = {},
                onMenuClick = {},
                leadingIcon = { Icon(Icons.Rounded.Save, null, Modifier.size(SplitButtonDefaults.LeadingIconSize)) },
                label = { Text("SPLIT BUTTON") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzExpressiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    shape: Shape? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }

    TextButton(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier.expressivePressScale(interactionSource, enabled),
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) ButtonShapes(shape, shape) else shapes,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzOutlinedExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    shape: Shape? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)

    OutlinedIconButton(
        onClick = {
            haptic.click()
            currentOnClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        border = border,
        shapes = if (shape != null) IconButtonShapes(shape, shape) else shapes,
        content = content,
    )
}

@Composable
fun ToolzOutlinedExpressiveIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = IconButtonDefaults.outlinedIconToggleButtonColors(),
    shapes: IconToggleButtonShapes = IconButtonDefaults.toggleableShapes(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    OutlinedIconToggleButton(
        checked = checked,
        onCheckedChange = {
            haptic.tick()
            currentOnCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        shapes = shapes,
        border = border,
        content = content,
    )
}
