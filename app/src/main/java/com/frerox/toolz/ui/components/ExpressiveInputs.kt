package com.frerox.toolz.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme

/**
 * Custom switch with expressive animations and physics.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = rememberToolzHapticFeedback()
    val performanceMode = LocalPerformanceMode.current
    val interactionSource = remember { MutableInteractionSource() }
    
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isActivelyInteracting = isPressed || isDragged

    // Symmetrical offset calculation: inner width is 44.dp (52 - 8), thumb is 24.dp.
    // Travel distance = 44 - 24 = 20.dp.
    val targetOffset = if (checked) 20.dp else 0.dp
    
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = if (performanceMode) {
            tween(120)
        } else {
            spring(
                dampingRatio = 0.6f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "ExpressiveSwitchThumbOffset"
    )

    // Dynamic width stretching when interacting (M3/iOS style physics)
    val thumbWidthMultiplier = if (isActivelyInteracting) 1.25f else 1.0f
    val thumbWidth by animateDpAsState(
        targetValue = 24.dp * thumbWidthMultiplier,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "ExpressiveSwitchThumbWidth"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 200),
        label = "ExpressiveSwitchContainerColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            checked -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "ExpressiveSwitchThumbColor"
    )

    Surface(
        checked = checked,
        onCheckedChange = {
            haptic.tick()
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier
            .size(52.dp, 32.dp),
        color = containerColor,
        shape = CircleShape,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(width = thumbWidth, height = 24.dp)
                    .background(thumbColor, CircleShape)
            )
        }
    }
}

/**
 * Expressive slider with physics-based thumb and active track.
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = false,
) {
    ExpressiveSlider(
        value = { value },
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        isPlaying = isPlaying,
    )
}

@Composable
fun ExpressiveSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        thumbColor = MaterialTheme.colorScheme.primary,
    ),
    isPlaying: Boolean = false,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) {
        if (isDragged) haptic.tick()
    }
    
    Slider(
        value = value(),
        onValueChange = currentOnValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = {
            haptic.click()
            currentOnValueChangeFinished?.invoke()
        },
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 1.4f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                label = "slider_thumb_scale"
            )
            
            Surface(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = CircleShape,
                color = colors.thumbColor,
                shadowElevation = 4.dp,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f))
            ) {}
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    onSearch: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOnSearch by rememberUpdatedState(onSearch)
    val haptic = rememberToolzHapticFeedback()

    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = {
            expanded = false
            haptic.click()
            currentOnSearch(it)
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
        enabled = enabled,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = colors,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpressiveInputsPreview() {
    ToolzTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            var checked by remember { mutableStateOf(true) }
            ExpressiveSwitch(checked = checked, onCheckedChange = { checked = it })
            
            var sliderValue by remember { mutableFloatStateOf(0.5f) }
            ExpressiveSlider(value = sliderValue, onValueChange = { sliderValue = it })
            
            ExpressiveSearchField(
                query = "",
                onQueryChange = {},
                placeholder = { Text("Search...") }
            )
        }
    }
}
