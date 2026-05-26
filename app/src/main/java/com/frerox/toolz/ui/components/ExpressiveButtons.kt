package com.frerox.toolz.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
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
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !performanceMode) 0.94f else 1f,
        animationSpec = if (performanceMode) tween(durationMillis = 90) else spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "toolzExpressiveButtonScale",
    )

    Button(
        onClick = {
            vibrationManager?.vibrateClick()
            currentOnClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
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
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !performanceMode) 0.94f else 1f,
        animationSpec = if (performanceMode) tween(durationMillis = 90) else spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "toolzOutlinedExpressiveButtonScale",
    )

    OutlinedButton(
        onClick = {
            vibrationManager?.vibrateClick()
            currentOnClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
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
fun ToolzExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    shape: Shape? = null,
    content: @Composable () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    FilledIconButton(
        onClick = {
            vibrationManager?.vibrateClick()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        shapes = if (shape != null) IconButtonShapes(shape, shape) else shapes,
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzLargeExtendedFloatingActionButton(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    val vibrationManager = LocalVibrationManager.current
    LargeExtendedFloatingActionButton(
        onClick = {
            vibrationManager?.vibrateClick()
            onClick()
        },
        modifier = modifier,
        icon = icon,
        text = text,
        expanded = expanded,
    )
}

@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(22.dp),
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val currentOnClick by rememberUpdatedState(onClick)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            performanceMode -> 1f
            isPressed -> 0.94f
            selected -> 1.05f
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
            vibrationManager?.vibrateTick()
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

/**
 * Premium Split Button using official Material 3 Expressive APIs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSplitButton(
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val vibrationManager = LocalVibrationManager.current
    var expanded by remember { mutableStateOf(false) }

    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onClick()
                },
                enabled = enabled,
            ) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black)) {
                    label()
                }
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = expanded,
                onCheckedChange = {
                    vibrationManager?.vibrateTick()
                    expanded = it
                    onMenuClick()
                },
                enabled = enabled,
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "Trailing Icon Rotation",
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Expand",
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        rotationZ = rotation
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun ExpressiveButtonsPreview() {
    ToolzTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                text = { Text("Large FAB") }
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveFilterChip(selected = true, onClick = {}, label = { Text("Selected") })
                ExpressiveFilterChip(selected = false, onClick = {}, label = { Text("Unselected") })
            }
            
            ExpressiveSplitButton(
                onClick = {},
                onMenuClick = {},
                label = { Text("SPLIT BUTTON") }
            )
        }
    }
}
